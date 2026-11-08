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

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_tcp.h"
#include "np_net.h"

#include <arpa/inet.h>
#include <errno.h>
#include <poll.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

//////////////////////////////////////////////////////////////////////////////
// Wake pipe
//////////////////////////////////////////////////////////////////////////////

void np_wake_init(np_wake* w) {
	w->rd = -1;
	w->wr = -1;
}

int np_wake_open(np_wake* w) {
	int p[2];
	if (pipe(p) != 0) {
		np_wake_init(w);
		return -1;
	}
	np_net_set_nonblocking(p[0]);
	np_net_set_nonblocking(p[1]);
	w->rd = p[0];
	w->wr = p[1];
	return 0;
}

void np_wake_poke(const np_wake* w) {
	if (w->wr >= 0) {
		uint8_t b = 1;
		ssize_t r = write(w->wr, &b, 1); // non-blocking; a full pipe already means "awake"
		(void)r;
	}
}

void np_wake_drain(int rd) {
	uint8_t b[64];
	while (read(rd, b, sizeof(b)) > 0) {
	}
}

void np_wake_close(np_wake* w) {
	if (w->rd >= 0)
		close(w->rd);
	if (w->wr >= 0)
		close(w->wr);
	np_wake_init(w);
}

//////////////////////////////////////////////////////////////////////////////
// Send queue
//////////////////////////////////////////////////////////////////////////////

bool np_outq_push(np_outq* q, const void* data, size_t n, size_t max) {
	if (q->off == q->len)
		q->off = q->len = 0;
	size_t pending = q->len - q->off;
	if (max && pending + n > max)
		return false;
	if (q->len + n > q->cap && q->off > 0) {
		memmove(q->buf, q->buf + q->off, pending);
		q->len = pending;
		q->off = 0;
	}
	if (q->len + n > q->cap) {
		size_t cap = q->cap ? q->cap : 4096;
		while (cap < q->len + n)
			cap *= 2;
		uint8_t* p = (uint8_t*)realloc(q->buf, cap);
		if (!p)
			return false;
		q->buf = p;
		q->cap = cap;
	}
	memcpy(q->buf + q->len, data, n);
	q->len += n;
	return true;
}

void np_outq_free(np_outq* q) {
	free(q->buf);
	memset(q, 0, sizeof(*q));
}

np_outq_result np_outq_flush(np_outq* q, int fd, bool* wrote) {
	*wrote = false;
	while (q->off < q->len) {
		ssize_t s = np_net_send_some(fd, q->buf + q->off, q->len - q->off);
		if (s > 0) {
			q->off += (size_t)s;
			*wrote = true;
		} else if (s == 0) {
			return NP_OUTQ_FULL;
		} else {
			return NP_OUTQ_ERROR;
		}
	}
	return NP_OUTQ_DRAINED;
}

//////////////////////////////////////////////////////////////////////////////
// Thread loops
//////////////////////////////////////////////////////////////////////////////

int np_tcp_accept_from(int listen_fd, int wake_fd, struct in_addr peer, np_tcp_quit_fn quit, const char* log_prefix) {
	for (;;) {
		if (quit())
			return -1;
		struct pollfd p[2] = {{listen_fd, POLLIN, 0}, {wake_fd, POLLIN, 0}};
		int r = poll(p, 2, 100);
		if (r < 0 && errno != EINTR)
			return -1;
		if (p[1].revents & POLLIN)
			np_wake_drain(wake_fd);
		if (p[0].revents & POLLIN) {
			struct sockaddr_in from;
			socklen_t from_len = sizeof(from);
			memset(&from, 0, sizeof(from));
			int fd = accept(listen_fd, (struct sockaddr*)&from, &from_len);
			if (fd < 0)
				continue;
			if (from.sin_family != AF_INET || from.sin_addr.s_addr != peer.s_addr) {
				char got[INET_ADDRSTRLEN] = "?";
				inet_ntop(AF_INET, &from.sin_addr, got, sizeof(got));
				NP_LOG("%srefused a connection from %s (not the agreed peer)", log_prefix, got);
				close(fd);
				continue;
			}
			np_net_configure_tcp(fd);
			if (np_net_set_nonblocking(fd) != 0) {
				close(fd);
				continue;
			}
			return fd;
		}
	}
}

int np_tcp_connect_retry(const char* ip, uint16_t port, int wake_fd, int attempts, int timeout_ms, int retry_ms,
						 np_tcp_quit_fn quit) {
	for (int attempt = 0; attempt < attempts; attempt++) {
		if (quit())
			return NP_TCP_CONNECT_QUIT;
		int fd = np_net_connect(ip, port, timeout_ms, wake_fd);
		if (fd >= 0)
			return fd;
		if (fd == -2) {
			np_wake_drain(wake_fd);
			continue;
		}
		struct pollfd p = {wake_fd, POLLIN, 0};
		if (poll(&p, 1, retry_ms) > 0)
			np_wake_drain(wake_fd);
	}
	return NP_TCP_CONNECT_GAVE_UP;
}

void np_tcp_final_flush(int fd, np_tcp_flush_fn flush, int flush_ms, int poll_ms, int linger_ms, uint8_t* scratch,
						size_t scratch_len) {
	uint64_t t0 = np_net_now_ms();
	for (;;) {
		bool pending = flush(fd);
		if (!pending || np_net_now_ms() - t0 >= (uint64_t)flush_ms)
			break;
		struct pollfd p = {fd, POLLOUT, 0};
		poll(&p, 1, poll_ms);
	}
	shutdown(fd, SHUT_WR);
	if (linger_ms <= 0)
		return;
	t0 = np_net_now_ms();
	for (;;) {
		uint64_t waited = np_net_now_ms() - t0;
		if (waited >= (uint64_t)linger_ms)
			break;
		struct pollfd p = {fd, POLLIN, 0};
		if (poll(&p, 1, (int)((uint64_t)linger_ms - waited)) <= 0)
			break;
		ssize_t n = recv(fd, scratch, scratch_len, 0);
		if (n == 0 || (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR))
			break;
	}
}

//////////////////////////////////////////////////////////////////////////////
// Start / shutdown
//////////////////////////////////////////////////////////////////////////////

bool np_tcp_parse_peer(const char* ip, size_t cap, struct in_addr* addr) {
	return ip && strlen(ip) < cap && inet_pton(AF_INET, ip, addr) == 1;
}

int np_tcp_start(bool host, uint16_t port, int* listen_fd, np_wake* wake, pthread_t* thread, void* (*fn)(void*)) {
	if (host) {
		*listen_fd = np_net_listen(port);
		if (*listen_fd < 0)
			return NP_TCP_START_LISTEN;
	}
	if (np_wake_open(wake) != 0 || pthread_create(thread, NULL, fn, NULL) != 0) {
		if (*listen_fd >= 0)
			close(*listen_fd);
		*listen_fd = -1;
		np_wake_close(wake);
		return NP_TCP_START_SETUP;
	}
	return 0;
}

void np_tcp_release(int* sock, int* listen_fd, np_wake* wake, np_outq* q) {
	if (*sock >= 0)
		close(*sock);
	*sock = -1;
	if (*listen_fd >= 0)
		close(*listen_fd);
	*listen_fd = -1;
	np_wake_close(wake);
	np_outq_free(q);
}
