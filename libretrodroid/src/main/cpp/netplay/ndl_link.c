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

/* The automatic route, as the 3DS link's. */
#define NDL_SEARCH_WAIT_MS 300
#define NDL_SEARCH_AGAIN_MS 700
#define NDL_ALONE_SEARCH_MS 2000
/* A game drops and restarts its radio between its own menus: the room outlives those gaps. */
#define NDL_LINGER_MS 10000
/* The holder just answered, so a few unanswered JOINs mean it went away: search again rather than knock for 30 s. */
#define NDL_AUTO_JOIN_TRIES 3
/* A room that refused this phone is asked again only after a pause. */
#define NDL_REFUSED_WAIT_MS 5000
#define NDL_OPEN_RETRY_MS 1000
#define NDL_ANSWER_SIZE 8

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
	int sfd; /* holder: bound to the search port, answers ASK */
	ndl_mode mode;
	ndl_role role;
	ndl_radio radio;

	/* AUTO. */
	uint64_t radio_off_ms; /* when the radio went off, 0 while it is on */
	bool parked;		   /* left at once: no search until the radio comes on again */
	uint64_t search_until; /* a search is waiting for answers until then, 0 = none */
	uint64_t next_search_ms;
	uint64_t next_alone_ms;
	bool found;
	ndl_room_info found_info;
	struct sockaddr_in found_addr;
#ifdef NDL_TESTING
	uint32_t test_sent;
#endif

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
	ndl_drop_counts drops;
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

static void send_on(ndl_link* l, int fd, const struct sockaddr_in* to, uint8_t type, uint16_t sender, uint16_t dest,
					const void* payload, size_t n) {
	if (fd < 0)
		return;
	uint8_t buf[NDL_DGRAM_MAX];
	size_t len = encode(buf, type, sender, dest, payload, n);
	ssize_t r;
	do {
		r = sendto(fd, buf, len, 0, (const struct sockaddr*)to, sizeof(*to));
	} while (r < 0 && errno == EINTR);
#ifdef NDL_TESTING
	l->test_sent++;
#else
	(void)l;
#endif
}

static void send_to(ndl_link* l, const struct sockaddr_in* to, uint8_t type, uint16_t sender, uint16_t dest,
					const void* payload, size_t n) {
	send_on(l, l->fd, to, type, sender, dest, payload, n);
}

static bool same_addr(const struct sockaddr_in* a, const struct sockaddr_in* b) {
	return a->sin_addr.s_addr == b->sin_addr.s_addr && a->sin_port == b->sin_port;
}

/* ---- Socket ---- */

static void close_search_socket(ndl_link* l) {
	if (l->sfd >= 0) {
		close(l->sfd);
		l->sfd = -1;
	}
}

/* Both: a link without its room socket has nothing to answer searches for. */
static void close_socket(ndl_link* l) {
	close_search_socket(l);
	if (l->fd >= 0) {
		close(l->fd);
		l->fd = -1;
	}
}

/* UDP, non-blocking, bound to port on every address (0: any free port). The room socket takes no SO_REUSEADDR: on
 * Linux it would let a second socket share the room port and take half its datagrams. The search socket does (reuse),
 * since asks are broadcast and reach every socket on the port, and a holder that is closing must not block the next. */
static int open_socket(const ndl_config* cfg, uint16_t port, bool reuse) {
	int fd = socket(AF_INET, SOCK_DGRAM, 0);
	if (fd < 0)
		return -1;
	if (cfg->bind_socket)
		cfg->bind_socket(fd, cfg->bind_ctx);
	if (reuse) {
		int on = 1;
		setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
	}
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
		l->drops.queue++;
		return;
	}
	ndl_slot* s = &l->q[(l->q_head + l->q_len) % NDL_QUEUE];
	s->sender = sender;
	s->len = (uint16_t)n;
	memcpy(s->buf, buf, n);
	l->q_len++;
	if ((uint32_t)l->q_len > l->drops.queue_hwm)
		l->drops.queue_hwm = (uint32_t)l->q_len;
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

/* Opens the room on the room port, answering searches on the search port. Lock held, no socket open. */
static bool open_room(ndl_link* l) {
	l->fd = open_socket(&l->cfg, l->cfg.room_port, false);
	if (l->fd < 0) {
		NP_LOG("DS link: cannot open the room on port %u (errno %d)", l->cfg.room_port, errno);
		return false;
	}
	if (l->cfg.search_port) {
		l->sfd = open_socket(&l->cfg, l->cfg.search_port, true);
		if (l->sfd < 0)
			NP_LOG("DS link: the search port %u is taken (errno %d), the room will not be found", l->cfg.search_port,
				   errno);
	}
	l->role = R_HOLDER;
	l->my_id = NDL_HOLDER_ID;
	l->next_alone_ms = now_ms(l) + NDL_ALONE_SEARCH_MS;
	NP_LOG("DS link: room open on port %u (up to %d)", l->cfg.room_port, l->max_members);
	return true;
}

/* Starts knocking on the room at holder from the room socket, opening it (any port) when there is none. */
static bool start_join(ndl_link* l, const struct sockaddr_in* holder) {
	l->holder = *holder;
	if (l->fd < 0)
		l->fd = open_socket(&l->cfg, 0, false);
	if (l->fd < 0) {
		NP_LOG("DS link: cannot open a socket (errno %d)", errno);
		return false;
	}
	l->role = R_JOINING;
	l->join_tries = 0;
	l->next_join_ms = 0;
	return true;
}

static void auto_reset(ndl_link* l) {
	l->search_until = 0;
	l->next_search_ms = 0;
	l->found = false;
	l->parked = false;
}

/* Leaves, closes the socket and starts mode. Lock held. */
static void apply_mode(ndl_link* l, ndl_mode m) {
	leave_room(l);
	close_socket(l);
	auto_reset(l);
	l->mode = m;
	if (m == NDL_HOST) {
		open_room(l);
	} else if (m == NDL_JOIN) {
		struct sockaddr_in h;
		memset(&h, 0, sizeof(h));
		h.sin_family = AF_INET;
		h.sin_port = htons(l->cfg.room_port);
		if (inet_pton(AF_INET, l->cfg.host_ip, &h.sin_addr) != 1) {
			NP_LOG("DS link: bad room address '%s'", l->cfg.host_ip);
			return;
		}
		start_join(l, &h);
	}
	/* AUTO opens nothing until the game's radio comes on (tick). */
}

/* ---- Search (lock held) ---- */

static bool holder_settled(ndl_link* l) {
	return l->mode == NDL_HOST || holder_count(l) > 1;
}

/* Asks the network for a room from the room socket (the answers come back to it), opening one when there is none. */
static void start_search(ndl_link* l) {
	if (l->fd < 0)
		l->fd = open_socket(&l->cfg, 0, false);
	if (l->fd < 0) {
		NP_LOG("DS link: cannot open a socket (errno %d)", errno);
		l->next_search_ms = now_ms(l) + NDL_OPEN_RETRY_MS;
		return;
	}
	int on = 1;
	setsockopt(l->fd, SOL_SOCKET, SO_BROADCAST, &on, sizeof(on));
	struct sockaddr_in to;
	memset(&to, 0, sizeof(to));
	to.sin_family = AF_INET;
	to.sin_port = htons(l->cfg.search_port);
	/* The network's own broadcast address leaves through that network whatever the default route is (a Wi-Fi without
	 * internet next to mobile data); the limited one covers a wrong or missing address. */
	if (l->cfg.broadcast[0] && inet_pton(AF_INET, l->cfg.broadcast, &to.sin_addr) == 1)
		send_to(l, &to, T_ASK, 0, NDL_BROADCAST, NULL, 0);
	to.sin_addr.s_addr = htonl(INADDR_BROADCAST);
	send_to(l, &to, T_ASK, 0, NDL_BROADCAST, NULL, 0);
	l->found = false;
	l->search_until = now_ms(l) + NDL_SEARCH_WAIT_MS;
}

static void search_answer(ndl_link* l, const struct sockaddr_in* from, const uint8_t* p, size_t n) {
	if (n < NDL_ANSWER_SIZE)
		return;
	ndl_room_info r;
	r.room_id = ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | (uint32_t)p[3];
	r.settled = p[4] != 0;
	uint16_t port = (uint16_t)((p[6] << 8) | p[7]);
	/* This phone's own room, heard through its own search port. */
	if (r.room_id == l->room_id || port == 0)
		return;
	if (l->found && !ndl_prefer(r, l->found_info))
		return;
	l->found = true;
	l->found_info = r;
	l->found_addr = *from;
	l->found_addr.sin_port = htons(port);
}

/* An ASK on the search socket: a holder tells of its room. */
static void search_ask(ndl_link* l, const struct sockaddr_in* from, const uint8_t* buf, size_t n) {
	if (l->role != R_HOLDER || n < NDL_HDR_SIZE || buf[0] != 'N' || buf[1] != 'D' || buf[2] != NDL_VERSION ||
		buf[3] != T_ASK)
		return;
	uint8_t a[NDL_ANSWER_SIZE];
	a[0] = (uint8_t)(l->room_id >> 24);
	a[1] = (uint8_t)(l->room_id >> 16);
	a[2] = (uint8_t)(l->room_id >> 8);
	a[3] = (uint8_t)l->room_id;
	a[4] = holder_settled(l);
	a[5] = (uint8_t)holder_count(l);
	a[6] = (uint8_t)(l->cfg.room_port >> 8);
	a[7] = (uint8_t)l->cfg.room_port;
	send_on(l, l->sfd, from, T_ANSWER, NDL_HOLDER_ID, 0, a, sizeof(a));
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
	} else {
		l->drops.dest++;
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
	if (!id) {
		l->drops.unknown++;
		return;
	}
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
	if (!same_addr(from, &l->holder)) {
		l->drops.wrong_src++;
		return;
	}
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
			l->next_search_ms = now + NDL_REFUSED_WAIT_MS;
			NP_LOG("DS link: the room is full");
		}
		return;
	}
	l->heard_ms = now;
	switch (type) {
	case T_DATA:
		if (sender == l->my_id)
			l->drops.echo++;
		else if (dest == NDL_BROADCAST || dest == l->my_id)
			queue_push(l, sender, payload, n);
		else
			l->drops.dest++;
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
	if (n < NDL_HDR_SIZE || n > NDL_DGRAM_MAX || buf[0] != 'N' || buf[1] != 'D' || buf[2] != NDL_VERSION) {
		l->drops.bad++;
		return;
	}
	uint8_t type = buf[3];
	uint16_t sender = (uint16_t)((buf[4] << 8) | buf[5]);
	uint16_t dest = (uint16_t)((buf[6] << 8) | buf[7]);
	const uint8_t* payload = buf + NDL_HDR_SIZE;
	size_t pn = n - NDL_HDR_SIZE;
	if (type == T_DATA && pn == 0) {
		l->drops.bad++;
		return;
	}
	if (type == T_ANSWER) {
		if (l->search_until)
			search_answer(l, from, payload, pn);
		return;
	}
	if (type == T_ASK)
		return;
	if (l->role == R_HOLDER)
		holder_handle(l, from, type, dest, payload, pn);
	else if (l->role == R_JOINING || l->role == R_MEMBER)
		member_handle(l, from, type, sender, dest, payload, pn);
}

/* ---- Timers (network thread, lock held) ---- */

static bool auto_wanted(ndl_link* l, uint64_t now) {
	if (l->parked)
		return false;
	if (l->radio != NDL_RADIO_OFF)
		return true;
	/* Only a room already held or joined outlives the radio. */
	return l->role != R_IDLE && l->radio_off_ms && now - l->radio_off_ms < NDL_LINGER_MS;
}

/* The automatic route: in a room while the game's radio is on. A game that hosts opens the room when the network has
 * none; a game that only looks joins the room it finds. */
static void auto_tick(ndl_link* l, uint64_t now) {
	if (!auto_wanted(l, now)) {
		if (l->role != R_IDLE || l->fd >= 0) {
			if (l->role == R_HOLDER || l->role == R_MEMBER)
				NP_LOG("DS link: radio off, leaving the room");
			leave_room(l);
			close_socket(l);
		}
		l->search_until = 0;
		l->radio_off_ms = 0;
		return;
	}
	bool done = l->search_until && now >= l->search_until;
	if (done)
		l->search_until = 0;

	if (l->role == R_HOLDER) {
		if (holder_count(l) > 1)
			return;
		/* Two phones that hosted at once each hold a room: alone in this one, move to the room every phone picks. */
		if (done && l->found) {
			ndl_room_info mine = {l->room_id, false};
			if (ndl_prefer(l->found_info, mine)) {
				NP_LOG("DS link: another room is the one to be in, moving to it");
				leave_room(l);
				close_socket(l);
				start_join(l, &l->found_addr);
			}
			return;
		}
		if (!l->search_until && now >= l->next_alone_ms) {
			l->next_alone_ms = now + NDL_ALONE_SEARCH_MS;
			start_search(l);
		}
		return;
	}
	if (l->role != R_IDLE)
		return; /* joining or a member: the room's own timers run it */

	if (done) {
		if (l->found) {
			if (!start_join(l, &l->found_addr))
				l->next_search_ms = now + NDL_OPEN_RETRY_MS;
		} else if (l->radio == NDL_RADIO_HOSTING) {
			/* The asking socket is on a free port; the room needs its own. */
			close_socket(l);
			if (!open_room(l))
				l->next_search_ms = now + NDL_OPEN_RETRY_MS;
		} else {
			l->next_search_ms = now + NDL_SEARCH_AGAIN_MS;
		}
		return;
	}
	if (!l->search_until && now >= l->next_search_ms)
		start_search(l);
}

static void tick(ndl_link* l) {
	uint64_t now = now_ms(l);
	if (l->mode == NDL_AUTO)
		auto_tick(l, now);
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
		if (l->join_tries >= (l->mode == NDL_AUTO ? NDL_AUTO_JOIN_TRIES : NDL_JOIN_TRIES)) {
			l->role = R_IDLE;
			char ip[INET_ADDRSTRLEN] = "";
			inet_ntop(AF_INET, &l->holder.sin_addr, ip, sizeof(ip));
			NP_LOG("DS link: no answer from %s, gave up", ip);
			return;
		}
		send_to(l, &l->holder, T_JOIN, 0, NDL_HOLDER_ID, l->cfg.name, strnlen(l->cfg.name, 16));
		l->join_tries++;
		l->next_join_ms = now + NDL_JOIN_RETRY_MS;
	}
}

/* ---- Network thread ---- */

/* Reads the room socket (or the search socket) dry, taking the lock per datagram. */
static void read_socket(ndl_link* l, bool search, uint8_t* buf, size_t cap) {
	for (;;) {
		pthread_mutex_lock(&l->mu);
		int fd = search ? l->sfd : l->fd;
		if (fd < 0) {
			pthread_mutex_unlock(&l->mu);
			return;
		}
		struct sockaddr_in from;
		socklen_t fl = sizeof(from);
		ssize_t n = recvfrom(fd, buf, cap, 0, (struct sockaddr*)&from, &fl);
		if (n < 0) {
			pthread_mutex_unlock(&l->mu);
			if (errno == EINTR)
				continue;
			/* EAGAIN, or an ICMP error left on the socket by a peer that went away. */
			return;
		}
		if (fl >= sizeof(from) && from.sin_family == AF_INET) {
			if (search)
				search_ask(l, &from, buf, (size_t)n);
			else
				handle_datagram(l, &from, buf, (size_t)n);
		}
		pthread_mutex_unlock(&l->mu);
	}
}

static void* net_thread(void* arg) {
	ndl_link* l = arg;
	uint8_t buf[NDL_DGRAM_MAX + 1];
	while (!atomic_load(&l->stop)) {
		pthread_mutex_lock(&l->mu);
		int fd = l->fd;
		int sfd = l->sfd;
		pthread_mutex_unlock(&l->mu);

		struct pollfd p[3];
		int np = 0;
		p[np].fd = l->wake.rd;
		p[np].events = POLLIN;
		p[np].revents = 0;
		np++;
		if (!atomic_load(&l->frozen)) {
			if (fd >= 0) {
				p[np].fd = fd;
				p[np].events = POLLIN;
				p[np].revents = 0;
				np++;
			}
			if (sfd >= 0) {
				p[np].fd = sfd;
				p[np].events = POLLIN;
				p[np].revents = 0;
				np++;
			}
		}
		poll(p, (nfds_t)np, NDL_POLL_MS);
		if (p[0].revents & POLLIN)
			np_wake_drain(l->wake.rd);
		if (atomic_load(&l->stop))
			break;
		if (atomic_load(&l->frozen))
			continue;

		read_socket(l, false, buf, sizeof(buf));
		read_socket(l, true, buf, sizeof(buf));

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
	l->sfd = -1;
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
	if (r == NDL_RADIO_OFF) {
		if (l->radio != NDL_RADIO_OFF)
			l->radio_off_ms = now_ms(l);
	} else {
		l->radio_off_ms = 0;
		if (r != l->radio)
			l->parked = false;
	}
	l->radio = r;
	pthread_mutex_unlock(&l->mu);
	np_wake_poke(&l->wake);
}

void ndl_leave_now(ndl_link* l) {
	if (!l)
		return;
	pthread_mutex_lock(&l->mu);
	leave_room(l);
	/* Nobody may find a room that is gone. */
	close_search_socket(l);
	if (l->mode == NDL_AUTO) {
		close_socket(l);
		l->search_until = 0;
		l->radio_off_ms = 0;
		l->parked = true;
	}
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

void ndl_stats_drops(ndl_link* l, ndl_drop_counts* out) {
	ndl_drop_counts d;
	memset(&d, 0, sizeof(d));
	if (l) {
		pthread_mutex_lock(&l->mu);
		d = l->drops;
		memset(&l->drops, 0, sizeof(l->drops));
		l->drops.queue_hwm = (uint32_t)l->q_len;
		pthread_mutex_unlock(&l->mu);
	}
	if (out)
		*out = d;
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

bool ndl_prefer(ndl_room_info a, ndl_room_info b) {
	if (a.settled != b.settled)
		return a.settled;
	return a.room_id < b.room_id;
}

#ifdef NDL_TESTING
uint32_t ndl_test_sent(ndl_link* l) {
	pthread_mutex_lock(&l->mu);
	uint32_t n = l->test_sent;
	pthread_mutex_unlock(&l->mu);
	return n;
}

void ndl_test_freeze(ndl_link* l, bool frozen) {
	atomic_store(&l->frozen, frozen);
	np_wake_poke(&l->wake);
}
#endif
