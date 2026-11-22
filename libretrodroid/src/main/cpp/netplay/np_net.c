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
 *
 *     Ported from NX Redux workspace/all/netplay/network_common.c @ 19d3a5e1 (GPL-3.0).
 *     Kept: NET_configureTCPSocket, NET_createListenSocket, the client connect from netplay.c and a monotonic
 *     clock. Dropped: LAN/IP lookup, UDP discovery and broadcast, hotspot SSID (the app does discovery in Kotlin).
 *     New: the accumulating packet reader and non-blocking connect/send.
 */

/*
 * NXRedux Network Common Module
 * Shared networking utilities for netplay and gbalink
 */

#define _POSIX_C_SOURCE 200809L
#if defined(__APPLE__)
#define _DARWIN_C_SOURCE
#endif

#include "np_net.h"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0 /* macOS: SO_NOSIGPIPE is set on every socket instead */
#endif

// Default TCP configuration (handheld DEFAULT_TCP_CONFIG: 64 KiB buffers, no timeout, no keepalive)
#define NP_TCP_BUFFER_SIZE 65536

uint64_t np_net_now_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000u + (uint64_t)ts.tv_nsec / 1000000u;
}

//////////////////////////////////////////////////////////////////////////////
// TCP Socket Configuration
//////////////////////////////////////////////////////////////////////////////

void np_net_configure_tcp(int fd) {
	if (fd < 0)
		return;

	int opt = 1;
	setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));

	int size = NP_TCP_BUFFER_SIZE;
	setsockopt(fd, SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));
	setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));

#if defined(__APPLE__) && defined(SO_NOSIGPIPE)
	setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &opt, sizeof(opt));
#endif
}

int np_net_set_nonblocking(int fd) {
	int fl = fcntl(fd, F_GETFL, 0);
	if (fl < 0 || fcntl(fd, F_SETFL, fl | O_NONBLOCK) < 0)
		return -1;
	int fdfl = fcntl(fd, F_GETFD, 0);
	if (fdfl >= 0)
		fcntl(fd, F_SETFD, fdfl | FD_CLOEXEC);
	return 0;
}

//////////////////////////////////////////////////////////////////////////////
// Server Socket Creation
//////////////////////////////////////////////////////////////////////////////

int np_net_listen(uint16_t port) {
	int fd = socket(AF_INET, SOCK_STREAM, 0);
	if (fd < 0) {
		NP_LOG("Socket creation failed");
		return -1;
	}

	int opt = 1;
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

	struct sockaddr_in addr;
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = htonl(INADDR_ANY);
	addr.sin_port = htons(port);

	if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
		NP_LOG("Bind failed on port %d (errno %d)", port, errno);
		close(fd);
		return -1;
	}

	if (listen(fd, 1) < 0) {
		NP_LOG("Listen failed (errno %d)", errno);
		close(fd);
		return -1;
	}

	if (np_net_set_nonblocking(fd) != 0) {
		close(fd);
		return -1;
	}
	return fd;
}

//////////////////////////////////////////////////////////////////////////////
// Client connect (netplay.c Netplay_connectToHost, made non-blocking so it can be interrupted)
//////////////////////////////////////////////////////////////////////////////

int np_net_connect(const char* ip, uint16_t port, int timeout_ms, int wake_fd) {
	struct sockaddr_in addr;
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_port = htons(port);
	if (!ip || inet_pton(AF_INET, ip, &addr.sin_addr) <= 0)
		return -1;

	int fd = socket(AF_INET, SOCK_STREAM, 0);
	if (fd < 0)
		return -1;
	np_net_configure_tcp(fd);
	if (np_net_set_nonblocking(fd) != 0) {
		close(fd);
		return -1;
	}

	if (connect(fd, (struct sockaddr*)&addr, sizeof(addr)) == 0)
		return fd;
	if (errno != EINPROGRESS && errno != EINTR) {
		close(fd);
		return -1;
	}

	uint64_t start = np_net_now_ms();
	for (;;) {
		uint64_t waited = np_net_now_ms() - start;
		if (waited >= (uint64_t)timeout_ms) {
			close(fd);
			return -1;
		}
		struct pollfd p[2] = {{fd, POLLOUT, 0}, {wake_fd, POLLIN, 0}};
		int r = poll(p, wake_fd >= 0 ? 2 : 1, (int)((uint64_t)timeout_ms - waited));
		if (r < 0) {
			if (errno == EINTR)
				continue;
			close(fd);
			return -1;
		}
		if (wake_fd >= 0 && (p[1].revents & POLLIN)) {
			close(fd);
			return -2;
		}
		if (p[0].revents) {
			int err = 0;
			socklen_t len = sizeof(err);
			if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len) < 0 || err != 0) {
				close(fd);
				return -1;
			}
			return fd;
		}
	}
}

ssize_t np_net_send_some(int fd, const void* buf, size_t len) {
	for (;;) {
		ssize_t n = send(fd, buf, len, MSG_NOSIGNAL);
		if (n >= 0)
			return n;
		if (errno == EINTR)
			continue;
		if (errno == EAGAIN || errno == EWOULDBLOCK)
			return 0;
		return -1;
	}
}

//////////////////////////////////////////////////////////////////////////////
// Packet framing
//////////////////////////////////////////////////////////////////////////////

void np_encode_header(uint8_t out[NP_HDR_SIZE], uint8_t cmd, uint32_t frame, uint16_t size) {
	out[0] = cmd;
	out[1] = (uint8_t)(frame >> 24);
	out[2] = (uint8_t)(frame >> 16);
	out[3] = (uint8_t)(frame >> 8);
	out[4] = (uint8_t)frame;
	out[5] = (uint8_t)(size >> 8);
	out[6] = (uint8_t)size;
}

void np_reader_reset(np_reader* r) {
	r->mode = 0;
	r->hdr_have = 0;
	r->payload_have = 0;
	r->raw = NULL;
	r->raw_len = 0;
	r->raw_have = 0;
}

void np_reader_expect_raw(np_reader* r, uint8_t* dst, size_t len) {
	r->mode = 2;
	r->raw = dst;
	r->raw_len = len;
	r->raw_have = 0;
}

np_rd_result np_reader_feed(np_reader* r, const uint8_t* in, size_t n, size_t* used, np_packet* out) {
	size_t i = 0;
	np_rd_result res = NP_RD_MORE;
	while (i < n && res == NP_RD_MORE) {
		if (r->mode == 2) {
			size_t take = r->raw_len - r->raw_have;
			if (take > n - i)
				take = n - i;
			memcpy(r->raw + r->raw_have, in + i, take);
			r->raw_have += take;
			i += take;
			if (r->raw_have == r->raw_len) {
				r->mode = 0;
				r->hdr_have = 0;
				res = NP_RD_RAW_DONE;
			}
		} else if (r->mode == 0) {
			size_t take = NP_HDR_SIZE - r->hdr_have;
			if (take > n - i)
				take = n - i;
			memcpy(r->hdr + r->hdr_have, in + i, take);
			r->hdr_have += take;
			i += take;
			if (r->hdr_have < NP_HDR_SIZE)
				continue;
			r->cmd = r->hdr[0];
			r->frame = ((uint32_t)r->hdr[1] << 24) | ((uint32_t)r->hdr[2] << 16) | ((uint32_t)r->hdr[3] << 8) |
					   (uint32_t)r->hdr[4];
			r->size = (uint16_t)(((uint16_t)r->hdr[5] << 8) | r->hdr[6]);
			r->hdr_have = 0;
			if (r->size > NP_MAX_PAYLOAD) {
				res = NP_RD_OVERSIZE;
			} else if (r->size == 0) {
				res = NP_RD_PACKET;
			} else {
				r->mode = 1;
				r->payload_have = 0;
			}
		} else {
			size_t take = (size_t)r->size - r->payload_have;
			if (take > n - i)
				take = n - i;
			memcpy(r->payload + r->payload_have, in + i, take);
			r->payload_have += take;
			i += take;
			if (r->payload_have == r->size) {
				r->mode = 0;
				res = NP_RD_PACKET;
			}
		}
	}
	if (res == NP_RD_PACKET && out) {
		out->cmd = r->cmd;
		out->frame = r->frame;
		out->size = r->size;
		out->payload = r->payload;
	}
	*used = i;
	return res;
}
