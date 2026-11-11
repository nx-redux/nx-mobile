/*
 *     Copyright (C) 2026  nx-mobile contributors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * The DS room (ndl_link.h). One mutex per link guards everything, the socket included: only code holding it opens,
 * closes or reads the socket, so the network thread may poll a number the caller just closed (it wakes, finds the
 * field changed and moves on) but never reads from it. Sends happen on the caller's thread under the lock; a UDP
 * sendto on a non-blocking socket does not wait. The network thread takes the lock once per datagram, so the
 * emulation thread's ndl_take, which the core calls in a loop while it waits for replies, never waits behind a batch.
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "ndl_link.h"
#include "np_net.h"
#include "np_tcp.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define NDL_VERSION 1
#define NDL_KEEPALIVE_MS 500
#define NDL_SILENCE_MS 3000
#define NDL_JOIN_RETRY_MS 1000
#define NDL_JOIN_TRIES 30
#define NDL_POLL_MS 20
#define NDL_QUEUE 256
#define NDL_HOLDER_ID 1
#define NDL_DGRAM_MAX (NDL_HDR_SIZE + NDL_MAX_PAYLOAD)

enum {
	T_ASK = 1,
	T_ANSWER = 2,
	T_JOIN = 3,
	T_ACCEPT = 4,
	T_REFUSE = 5,
	T_DATA = 6,
	T_LEAVE = 7,
	T_KEEPALIVE = 8,
};

/* Where the link stands in its mode. */
typedef enum {
	R_IDLE = 0, /* no room: OFF, AUTO, parked after a leave, or a JOIN that ended */
	R_HOLDER,	/* holds the room */
	R_JOINING,	/* JOIN asked, no answer yet */
	R_MEMBER,	/* in someone else's room */
} ndl_role;

typedef struct {
	bool used;
	struct sockaddr_in addr;
	char name[17];
	uint64_t heard_ms;
	uint64_t sent_ms;
} ndl_member;

typedef struct {
	uint16_t sender;
	uint16_t len;
	uint8_t buf[NDL_MAX_PAYLOAD];
} ndl_slot;

struct ndl_link {
	ndl_config cfg;
	uint64_t (*clock)(void);
	int max_members;
	pthread_mutex_t mu;
	pthread_t thread;
	np_wake wake;
	atomic_bool stop;
	atomic_bool frozen;

	int fd;
	ndl_mode mode;
	ndl_role role;
	ndl_radio radio;

	/* Holder: members[id], id 2..max_members (members[1] is this phone, never used). */
	ndl_member members[NDL_MAX_MEMBERS + 1];
	uint32_t room_id;

	/* Member side. */
	struct sockaddr_in holder;
	int my_id;
	int count; /* members the holder last told us of */
	uint64_t heard_ms;
	uint64_t sent_ms;
	int join_tries;
	uint64_t next_join_ms;
	/* Round trips of the keepalive probe since the last ndl_stats_rtt. */
	int rtt_n;
	int rtt_min;
	int rtt_max;
	int64_t rtt_sum;

	/* Inbound queue, a ring. */
	ndl_slot* q;
	int q_head;
	int q_len;
	bool q_full_logged;
};

static uint64_t now_ms(ndl_link* l) {
	return l->clock();
}

/* ---- Wire ---- */

static size_t encode(uint8_t* out, uint8_t type, uint16_t sender, uint16_t dest, const void* payload, size_t n) {
	out[0] = 'N';
	out[1] = 'D';
	out[2] = NDL_VERSION;
	out[3] = type;
	out[4] = (uint8_t)(sender >> 8);
	out[5] = (uint8_t)sender;
	out[6] = (uint8_t)(dest >> 8);
	out[7] = (uint8_t)dest;
	if (n)
		memcpy(out + NDL_HDR_SIZE, payload, n);
	return NDL_HDR_SIZE + n;
}

static void send_to(ndl_link* l, const struct sockaddr_in* to, uint8_t type, uint16_t sender, uint16_t dest,
					const void* payload, size_t n) {
	if (l->fd < 0)
		return;
	uint8_t buf[NDL_DGRAM_MAX];
	size_t len = encode(buf, type, sender, dest, payload, n);
	ssize_t r;
	do {
		r = sendto(l->fd, buf, len, 0, (const struct sockaddr*)to, sizeof(*to));
	} while (r < 0 && errno == EINTR);
}

static bool same_addr(const struct sockaddr_in* a, const struct sockaddr_in* b) {
	return a->sin_addr.s_addr == b->sin_addr.s_addr && a->sin_port == b->sin_port;
}

/* ---- Socket ---- */

static void close_socket(ndl_link* l) {
	if (l->fd >= 0) {
		close(l->fd);
		l->fd = -1;
	}
}

/* UDP, non-blocking, bound to port on every address (0: any free port). No SO_REUSEADDR: on Linux it would let a
 * second socket share the room port and take half its datagrams. */
static int open_socket(const ndl_config* cfg, uint16_t port) {
	int fd = socket(AF_INET, SOCK_DGRAM, 0);
	if (fd < 0)
		return -1;
	if (cfg->bind_socket)
		cfg->bind_socket(fd, cfg->bind_ctx);
	int buf = 256 * 1024;
	setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &buf, sizeof(buf));
	setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &buf, sizeof(buf));
	struct sockaddr_in a;
	memset(&a, 0, sizeof(a));
	a.sin_family = AF_INET;
	a.sin_addr.s_addr = htonl(INADDR_ANY);
	a.sin_port = htons(port);
	if (bind(fd, (struct sockaddr*)&a, sizeof(a)) != 0 || np_net_set_nonblocking(fd) != 0) {
		close(fd);
		return -1;
	}
	return fd;
}

/* ---- Queue ---- */

static void queue_clear(ndl_link* l) {
	l->q_head = 0;
	l->q_len = 0;
	l->q_full_logged = false;
}

static void queue_push(ndl_link* l, uint16_t sender, const uint8_t* buf, size_t n) {
	if (l->q_len == NDL_QUEUE) {
		/* The core stopped taking (paused, or stuck in a long frame); old packets are useless to it anyway. */
		if (!l->q_full_logged) {
			NP_LOG("DS link: inbound queue full, dropping packets");
			l->q_full_logged = true;
		}
		return;
	}
	ndl_slot* s = &l->q[(l->q_head + l->q_len) % NDL_QUEUE];
	s->sender = sender;
	s->len = (uint16_t)n;
	memcpy(s->buf, buf, n);
	l->q_len++;
}

/* ---- Room state ---- */

static int holder_count(ndl_link* l) {
	int n = 1;
	for (int id = 2; id <= l->max_members; id++)
		n += l->members[id].used;
	return n;
}

/* Every member hears the new count at once, so a leave or a drop shows within a round trip. */
static void holder_tell_count(ndl_link* l) {
	uint8_t count = (uint8_t)holder_count(l);
	uint64_t now = now_ms(l);
	for (int id = 2; id <= l->max_members; id++) {
		if (!l->members[id].used)
			continue;
		send_to(l, &l->members[id].addr, T_KEEPALIVE, NDL_HOLDER_ID, (uint16_t)id, &count, 1);
		l->members[id].sent_ms = now;
	}
}

/* Sends LEAVE to whoever needs to know and drops the room state. The socket stays. */
static void leave_room(ndl_link* l) {
	if (l->role == R_HOLDER) {
		for (int id = 2; id <= l->max_members; id++) {
			if (l->members[id].used)
				send_to(l, &l->members[id].addr, T_LEAVE, NDL_HOLDER_ID, (uint16_t)id, NULL, 0);
		}
		NP_LOG("DS link: room closed");
	} else if (l->role == R_MEMBER) {
		send_to(l, &l->holder, T_LEAVE, (uint16_t)l->my_id, NDL_HOLDER_ID, NULL, 0);
		NP_LOG("DS link: left the room");
	}
	memset(l->members, 0, sizeof(l->members));
	l->role = R_IDLE;
	l->my_id = -1;
	l->count = 0;
	queue_clear(l);
}

static void member_lost_room(ndl_link* l, const char* why) {
	NP_LOG("DS link: out of the room (%s)", why);
	memset(l->members, 0, sizeof(l->members));
	l->role = R_IDLE;
	l->my_id = -1;
	l->count = 0;
	queue_clear(l);
}

/* Leaves, closes the socket and starts mode. Lock held. */
static void apply_mode(ndl_link* l, ndl_mode m) {
	leave_room(l);
	close_socket(l);
	l->mode = m;
	if (m == NDL_HOST) {
		l->fd = open_socket(&l->cfg, l->cfg.room_port);
		if (l->fd < 0) {
			NP_LOG("DS link: cannot open the room on port %u (errno %d)", l->cfg.room_port, errno);
			return;
		}
		l->role = R_HOLDER;
		l->my_id = NDL_HOLDER_ID;
		NP_LOG("DS link: room open on port %u (up to %d)", l->cfg.room_port, l->max_members);
	} else if (m == NDL_JOIN) {
		memset(&l->holder, 0, sizeof(l->holder));
		l->holder.sin_family = AF_INET;
		l->holder.sin_port = htons(l->cfg.room_port);
		if (inet_pton(AF_INET, l->cfg.host_ip, &l->holder.sin_addr) != 1) {
			NP_LOG("DS link: bad room address '%s'", l->cfg.host_ip);
			return;
		}
		l->fd = open_socket(&l->cfg, 0);
		if (l->fd < 0) {
			NP_LOG("DS link: cannot open a socket (errno %d)", errno);
			return;
		}
		l->role = R_JOINING;
		l->join_tries = 0;
		l->next_join_ms = 0;
	}
}

/* ---- Inbound (network thread, lock held) ---- */

static int member_at(ndl_link* l, const struct sockaddr_in* from) {
	for (int id = 2; id <= l->max_members; id++) {
		if (l->members[id].used && same_addr(&l->members[id].addr, from))
			return id;
	}
	return 0;
}

static void holder_relay(ndl_link* l, int from, uint16_t dest, const uint8_t* payload, size_t n) {
	if (dest == NDL_BROADCAST) {
		queue_push(l, (uint16_t)from, payload, n);
		for (int id = 2; id <= l->max_members; id++) {
			if (id != from && l->members[id].used)
				send_to(l, &l->members[id].addr, T_DATA, (uint16_t)from, NDL_BROADCAST, payload, n);
		}
	} else if (dest == NDL_HOLDER_ID) {
		queue_push(l, (uint16_t)from, payload, n);
	} else if (dest <= l->max_members && dest != from && l->members[dest].used) {
		send_to(l, &l->members[dest].addr, T_DATA, (uint16_t)from, dest, payload, n);
	}
}

static void holder_handle(ndl_link* l, const struct sockaddr_in* from, uint8_t type, uint16_t dest,
						  const uint8_t* payload, size_t n) {
	uint64_t now = now_ms(l);
	int id = member_at(l, from);
	if (type == T_JOIN) {
		if (!id) {
			for (int i = 2; i <= l->max_members; i++) {
				if (!l->members[i].used) {
					id = i;
					break;
				}
			}
			if (!id) {
				send_to(l, from, T_REFUSE, NDL_HOLDER_ID, NDL_BROADCAST, NULL, 0);
				NP_LOG("DS link: room full, a joiner turned away");
				return;
			}
			ndl_member* m = &l->members[id];
			m->used = true;
			m->addr = *from;
			size_t nl = n < 16 ? n : 16;
			memcpy(m->name, payload, nl);
			m->name[nl] = '\0';
			m->sent_ms = now;
			NP_LOG("DS link: '%s' joined as %d", m->name, id);
		}
		l->members[id].heard_ms = now;
		/* A repeated JOIN means our ACCEPT was lost: same id again. */
		uint8_t count = (uint8_t)holder_count(l);
		send_to(l, from, T_ACCEPT, NDL_HOLDER_ID, (uint16_t)id, &count, 1);
		holder_tell_count(l);
		return;
	}
	if (!id)
		return;
	l->members[id].heard_ms = now;
	if (type == T_DATA) {
		holder_relay(l, id, dest, payload, n);
	} else if (type == T_KEEPALIVE) {
		if (n >= 4) {
			uint8_t echo[5];
			echo[0] = (uint8_t)holder_count(l);
			memcpy(echo + 1, payload, 4);
			send_to(l, from, T_KEEPALIVE, NDL_HOLDER_ID, (uint16_t)id, echo, sizeof(echo));
		}
	} else if (type == T_LEAVE) {
		NP_LOG("DS link: member %d left", id);
		memset(&l->members[id], 0, sizeof(l->members[id]));
		holder_tell_count(l);
	}
}

static void member_handle(ndl_link* l, const struct sockaddr_in* from, uint8_t type, uint16_t sender, uint16_t dest,
						  const uint8_t* payload, size_t n) {
	if (!same_addr(from, &l->holder))
		return;
	uint64_t now = now_ms(l);
	if (l->role == R_JOINING) {
		if (type == T_ACCEPT && dest >= 2 && dest <= NDL_MAX_MEMBERS) {
			l->role = R_MEMBER;
			l->my_id = dest;
			l->count = n >= 1 ? payload[0] : 2;
			l->heard_ms = now;
			l->sent_ms = now;
			NP_LOG("DS link: joined the room as %d", dest);
		} else if (type == T_REFUSE) {
			l->role = R_IDLE;
			NP_LOG("DS link: the room is full");
		}
		return;
	}
	l->heard_ms = now;
	switch (type) {
	case T_DATA:
		if ((dest == NDL_BROADCAST || dest == l->my_id) && sender != l->my_id)
			queue_push(l, sender, payload, n);
		break;
	case T_KEEPALIVE:
		if (n >= 1)
			l->count = payload[0];
		if (n >= 5) {
			/* Our own send time, echoed: the difference is the round trip (32-bit wrap is harmless). */
			uint32_t sent = ((uint32_t)payload[1] << 24) | ((uint32_t)payload[2] << 16) | ((uint32_t)payload[3] << 8) |
							(uint32_t)payload[4];
			int rtt = (int)(uint32_t)((uint32_t)now - sent);
			if (rtt < 0)
				rtt = 0;
			if (l->rtt_n == 0 || rtt < l->rtt_min)
				l->rtt_min = rtt;
			if (l->rtt_n == 0 || rtt > l->rtt_max)
				l->rtt_max = rtt;
			l->rtt_sum += rtt;
			l->rtt_n++;
		}
		break;
	case T_LEAVE:
		member_lost_room(l, "the room closed");
		break;
	case T_ACCEPT:
		/* The answer to a JOIN retry that crossed the first ACCEPT. */
		if (n >= 1)
			l->count = payload[0];
		break;
	default:
		break;
	}
}

static void handle_datagram(ndl_link* l, const struct sockaddr_in* from, const uint8_t* buf, size_t n) {
	if (n < NDL_HDR_SIZE || n > NDL_DGRAM_MAX || buf[0] != 'N' || buf[1] != 'D' || buf[2] != NDL_VERSION)
		return;
	uint8_t type = buf[3];
	uint16_t sender = (uint16_t)((buf[4] << 8) | buf[5]);
	uint16_t dest = (uint16_t)((buf[6] << 8) | buf[7]);
	const uint8_t* payload = buf + NDL_HDR_SIZE;
	size_t pn = n - NDL_HDR_SIZE;
	if (type == T_DATA && pn == 0)
		return;
	if (l->role == R_HOLDER)
		holder_handle(l, from, type, dest, payload, pn);
	else if (l->role == R_JOINING || l->role == R_MEMBER)
		member_handle(l, from, type, sender, dest, payload, pn);
}

/* ---- Timers (network thread, lock held) ---- */

static void tick(ndl_link* l) {
	uint64_t now = now_ms(l);
	if (l->role == R_HOLDER) {
		bool dropped = false;
		for (int id = 2; id <= l->max_members; id++) {
			ndl_member* m = &l->members[id];
			if (!m->used)
				continue;
			if (now - m->heard_ms >= NDL_SILENCE_MS) {
				NP_LOG("DS link: member %d went silent, dropped", id);
				memset(m, 0, sizeof(*m));
				dropped = true;
				continue;
			}
			if (now - m->sent_ms >= NDL_KEEPALIVE_MS) {
				uint8_t count = (uint8_t)holder_count(l);
				send_to(l, &m->addr, T_KEEPALIVE, NDL_HOLDER_ID, (uint16_t)id, &count, 1);
				m->sent_ms = now;
			}
		}
		if (dropped)
			holder_tell_count(l);
	} else if (l->role == R_MEMBER) {
		if (now - l->heard_ms >= NDL_SILENCE_MS) {
			member_lost_room(l, "the room went silent");
			return;
		}
		if (now - l->sent_ms >= NDL_KEEPALIVE_MS) {
			uint32_t stamp = (uint32_t)now;
			uint8_t probe[4] = {(uint8_t)(stamp >> 24), (uint8_t)(stamp >> 16), (uint8_t)(stamp >> 8), (uint8_t)stamp};
			send_to(l, &l->holder, T_KEEPALIVE, (uint16_t)l->my_id, NDL_HOLDER_ID, probe, sizeof(probe));
			l->sent_ms = now;
		}
	} else if (l->role == R_JOINING) {
		if (now < l->next_join_ms)
			return;
		if (l->join_tries >= NDL_JOIN_TRIES) {
			l->role = R_IDLE;
			NP_LOG("DS link: no answer from %s, gave up", l->cfg.host_ip);
			return;
		}
		send_to(l, &l->holder, T_JOIN, 0, NDL_HOLDER_ID, l->cfg.name, strnlen(l->cfg.name, 16));
		l->join_tries++;
		l->next_join_ms = now + NDL_JOIN_RETRY_MS;
	}
}

/* ---- Network thread ---- */

static void* net_thread(void* arg) {
	ndl_link* l = arg;
	uint8_t buf[NDL_DGRAM_MAX + 1];
	while (!atomic_load(&l->stop)) {
		pthread_mutex_lock(&l->mu);
		int fd = l->fd;
		pthread_mutex_unlock(&l->mu);

		struct pollfd p[2];
		int np = 0;
		p[np].fd = l->wake.rd;
		p[np].events = POLLIN;
		p[np].revents = 0;
		np++;
		if (fd >= 0 && !atomic_load(&l->frozen)) {
			p[np].fd = fd;
			p[np].events = POLLIN;
			p[np].revents = 0;
			np++;
		}
		poll(p, (nfds_t)np, NDL_POLL_MS);
		if (p[0].revents & POLLIN)
			np_wake_drain(l->wake.rd);
		if (atomic_load(&l->stop))
			break;
		if (atomic_load(&l->frozen))
			continue;

		for (;;) {
			pthread_mutex_lock(&l->mu);
			if (l->fd < 0) {
				pthread_mutex_unlock(&l->mu);
				break;
			}
			struct sockaddr_in from;
			socklen_t fl = sizeof(from);
			ssize_t n = recvfrom(l->fd, buf, sizeof(buf), 0, (struct sockaddr*)&from, &fl);
			if (n < 0) {
				pthread_mutex_unlock(&l->mu);
				if (errno == EINTR)
					continue;
				/* EAGAIN, or an ICMP error left on the socket by a peer that went away. */
				break;
			}
			if (fl >= sizeof(from) && from.sin_family == AF_INET)
				handle_datagram(l, &from, buf, (size_t)n);
			pthread_mutex_unlock(&l->mu);
		}

		pthread_mutex_lock(&l->mu);
		tick(l);
		pthread_mutex_unlock(&l->mu);
	}
	return NULL;
}

/* ---- API ---- */

ndl_link* ndl_create(const ndl_config* cfg) {
	if (!cfg)
		return NULL;
	ndl_link* l = calloc(1, sizeof(*l));
	if (!l)
		return NULL;
	l->q = calloc(NDL_QUEUE, sizeof(ndl_slot));
	if (!l->q) {
		free(l);
		return NULL;
	}
	l->cfg = *cfg;
	l->cfg.name[sizeof(l->cfg.name) - 1] = '\0';
	l->cfg.host_ip[sizeof(l->cfg.host_ip) - 1] = '\0';
	l->cfg.broadcast[sizeof(l->cfg.broadcast) - 1] = '\0';
	l->clock = cfg->clock ? cfg->clock : np_net_now_ms;
	l->max_members =
		(cfg->max_members >= 2 && cfg->max_members <= NDL_MAX_MEMBERS) ? cfg->max_members : NDL_MAX_MEMBERS;
	l->fd = -1;
	l->my_id = -1;
	l->mode = NDL_OFF;
	l->radio = NDL_RADIO_OFF;
	l->room_id = (uint32_t)(np_net_now_ms() * 2654435761u) ^ (uint32_t)getpid() ^ (uint32_t)(uintptr_t)l;
	atomic_init(&l->stop, false);
	atomic_init(&l->frozen, false);
	np_wake_init(&l->wake);
	pthread_mutex_init(&l->mu, NULL);

	if (np_wake_open(&l->wake) != 0) {
		NP_LOG("DS link: cannot open the wake pipe");
		goto fail;
	}
	/* Before the thread starts, so a HOST's room is open when this returns. */
	apply_mode(l, cfg->mode);
	if (pthread_create(&l->thread, NULL, net_thread, l) != 0) {
		NP_LOG("DS link: cannot start the network thread");
		close_socket(l);
		goto fail;
	}
	return l;
fail:
	np_wake_close(&l->wake);
	pthread_mutex_destroy(&l->mu);
	free(l->q);
	free(l);
	return NULL;
}

void ndl_destroy(ndl_link* l) {
	if (!l)
		return;
	atomic_store(&l->stop, true);
	np_wake_poke(&l->wake);
	pthread_join(l->thread, NULL);
	pthread_mutex_lock(&l->mu);
	if (!atomic_load(&l->frozen))
		leave_room(l);
	close_socket(l);
	pthread_mutex_unlock(&l->mu);
	np_wake_close(&l->wake);
	pthread_mutex_destroy(&l->mu);
	free(l->q);
	free(l);
}

void ndl_set_mode(ndl_link* l, ndl_mode m) {
	if (!l)
		return;
	pthread_mutex_lock(&l->mu);
	apply_mode(l, m);
	pthread_mutex_unlock(&l->mu);
	np_wake_poke(&l->wake);
}

void ndl_radio_changed(ndl_link* l, ndl_radio r) {
	if (!l)
		return;
	pthread_mutex_lock(&l->mu);
	l->radio = r;
	pthread_mutex_unlock(&l->mu);
}

void ndl_leave_now(ndl_link* l) {
	if (!l)
		return;
	pthread_mutex_lock(&l->mu);
	leave_room(l);
	pthread_mutex_unlock(&l->mu);
}

bool ndl_in_room(ndl_link* l) {
	if (!l)
		return false;
	pthread_mutex_lock(&l->mu);
	bool in = l->role == R_HOLDER || l->role == R_MEMBER;
	pthread_mutex_unlock(&l->mu);
	return in;
}

int ndl_client_id(ndl_link* l) {
	if (!l)
		return -1;
	pthread_mutex_lock(&l->mu);
	int id = (l->role == R_HOLDER || l->role == R_MEMBER) ? l->my_id : -1;
	pthread_mutex_unlock(&l->mu);
	return id;
}

int ndl_member_count(ndl_link* l) {
	if (!l)
		return 0;
	pthread_mutex_lock(&l->mu);
	int n = l->role == R_HOLDER ? holder_count(l) : l->role == R_MEMBER ? l->count : 0;
	pthread_mutex_unlock(&l->mu);
	return n;
}

void ndl_stats_rtt(ndl_link* l, int* min_ms, int* avg_ms, int* max_ms) {
	int mn = -1, av = -1, mx = -1;
	if (l) {
		pthread_mutex_lock(&l->mu);
		if (l->rtt_n > 0) {
			mn = l->rtt_min;
			mx = l->rtt_max;
			av = (int)(l->rtt_sum / l->rtt_n);
		}
		l->rtt_n = 0;
		l->rtt_sum = 0;
		pthread_mutex_unlock(&l->mu);
	}
	if (min_ms)
		*min_ms = mn;
	if (avg_ms)
		*avg_ms = av;
	if (max_ms)
		*max_ms = mx;
}

int ndl_send(ndl_link* l, const void* buf, size_t len, uint16_t dest) {
	if (!l || !buf || len == 0 || len > NDL_MAX_PAYLOAD || dest == 0)
		return -1;
	int rc = 0;
	pthread_mutex_lock(&l->mu);
	if (l->role == R_HOLDER) {
		if (dest == NDL_HOLDER_ID) {
			rc = -1;
		} else if (dest == NDL_BROADCAST) {
			for (int id = 2; id <= l->max_members; id++) {
				if (l->members[id].used)
					send_to(l, &l->members[id].addr, T_DATA, NDL_HOLDER_ID, NDL_BROADCAST, buf, len);
			}
		} else if (dest <= l->max_members && l->members[dest].used) {
			send_to(l, &l->members[dest].addr, T_DATA, NDL_HOLDER_ID, dest, buf, len);
		}
		/* A member that just left: the packet is lost, as over the air. */
	} else if (l->role == R_MEMBER) {
		if (dest == l->my_id)
			rc = -1;
		else
			send_to(l, &l->holder, T_DATA, (uint16_t)l->my_id, dest, buf, len);
	} else {
		rc = -1;
	}
	pthread_mutex_unlock(&l->mu);
	return rc;
}

int ndl_take(ndl_link* l, ndl_deliver_fn fn, void* ctx) {
	if (!l || !fn)
		return 0;
	static _Thread_local uint8_t buf[NDL_MAX_PAYLOAD];
	pthread_mutex_lock(&l->mu);
	int pending = l->q_len;
	pthread_mutex_unlock(&l->mu);
	int taken = 0;
	/* Only what was queued on entry, so a fast sender cannot keep the core in here. */
	while (taken < pending) {
		pthread_mutex_lock(&l->mu);
		if (l->q_len == 0) {
			/* Cleared by a leave meanwhile. */
			pthread_mutex_unlock(&l->mu);
			break;
		}
		ndl_slot* s = &l->q[l->q_head];
		uint16_t sender = s->sender;
		size_t len = s->len;
		memcpy(buf, s->buf, len);
		l->q_head = (l->q_head + 1) % NDL_QUEUE;
		l->q_len--;
		if (l->q_len == 0)
			l->q_full_logged = false;
		pthread_mutex_unlock(&l->mu);
		fn(buf, len, sender, ctx);
		taken++;
	}
	return taken;
}

#ifdef NDL_TESTING
void ndl_test_freeze(ndl_link* l, bool frozen) {
	atomic_store(&l->frozen, frozen);
	np_wake_poke(&l->wake);
}
#endif
