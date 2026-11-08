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
 *     Ported from NX Redux workspace/all/netplay/network_common.h @ 19d3a5e1 (GPL-3.0).
 */

/*
 * NXRedux Network Common Module
 * Shared networking utilities for netplay and gbalink
 */

#ifndef NP_NET_H
#define NP_NET_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

/* Logging: logcat on Android, stderr on the host test build. */
#ifdef __ANDROID__
#include <android/log.h>
#include "../logflag.h"
/* Info only, so the Debug log setting gates it. */
#define NP_LOG(...) ((void) (nx_log_enabled() && __android_log_print(ANDROID_LOG_INFO, "nx-netplay", __VA_ARGS__)))
#else
#include <stdio.h>
#define NP_LOG(...)                       \
	do {                                  \
		fprintf(stderr, "nx-netplay: ");  \
		fprintf(stderr, __VA_ARGS__);     \
		fprintf(stderr, "\n");            \
	} while (0)
#endif

#ifdef __cplusplus
extern "C" {
#endif

/* Engine packet framing (identical to the handheld's PacketHeader: u8 cmd, u32 frame BE, u16 size BE). */
#define NP_HDR_SIZE 7
#define NP_MAX_PAYLOAD 4096

/* Monotonic milliseconds (CLOCK_MONOTONIC). */
uint64_t np_net_now_ms(void);

/* TCP_NODELAY, 64 KiB send/receive buffers, SO_NOSIGPIPE on Apple. */
void np_net_configure_tcp(int fd);

/* O_NONBLOCK + FD_CLOEXEC (no SOCK_CLOEXEC/accept4 on macOS). 0 ok. */
int np_net_set_nonblocking(int fd);

/* SO_REUSEADDR, bind INADDR_ANY:port, listen(1), non-blocking. fd or -1. */
int np_net_listen(uint16_t port);

/* Non-blocking connect to ip:port, waiting up to timeout_ms. Returns a connected, configured, non-blocking fd;
 * -1 on failure or timeout; -2 when wake_fd (may be -1) became readable first. */
int np_net_connect(const char* ip, uint16_t port, int timeout_ms, int wake_fd);

/* One non-blocking send with MSG_NOSIGNAL (EINTR retried). Bytes written, 0 when the socket buffer is full,
 * -1 on error (errno set). The engine loops on it until every queued byte is written. */
ssize_t np_net_send_some(int fd, const void* buf, size_t len);

/* Header encode: 7 bytes, big-endian, exactly the handheld's packed PacketHeader. */
void np_encode_header(uint8_t out[NP_HDR_SIZE], uint8_t cmd, uint32_t frame, uint16_t size);

/* ---- Accumulating reader ----------------------------------------------------------------------------------
 * Unlike the handheld's recv_packet (one recv for the header, one for the payload), framing survives a short
 * read. Bytes are fed in as they arrive; the reader returns one event at a time:
 * - NP_RD_PACKET: a full header plus `size` payload bytes (size <= 4096). After a STATE_HDR the caller switches
 *   the reader to raw mode with np_reader_expect_raw (the state stream after STATE_HDR has no framing).
 * - NP_RD_RAW_DONE: the raw buffer given to np_reader_expect_raw is full.
 * - NP_RD_OVERSIZE: a header announced size > 4096. The stream cannot be trusted any more: end the session.
 * - NP_RD_MORE: all input consumed, nothing complete yet.
 */
typedef struct {
	uint8_t cmd;
	uint32_t frame;
	uint16_t size;
	const uint8_t* payload; /* valid until the next feed */
} np_packet;

typedef enum {
	NP_RD_MORE = 0,
	NP_RD_PACKET,
	NP_RD_RAW_DONE,
	NP_RD_OVERSIZE
} np_rd_result;

typedef struct {
	int mode; /* 0 header, 1 payload, 2 raw */
	uint8_t hdr[NP_HDR_SIZE];
	size_t hdr_have;
	uint8_t cmd;
	uint32_t frame;
	uint16_t size;
	uint8_t payload[NP_MAX_PAYLOAD];
	size_t payload_have;
	uint8_t* raw;
	size_t raw_len;
	size_t raw_have;
} np_reader;

void np_reader_reset(np_reader* r);
/* The next len bytes of the stream are raw: copy them into dst (owned by the caller). len > 0. */
void np_reader_expect_raw(np_reader* r, uint8_t* dst, size_t len);
/* Consume up to n bytes (*used tells how many) and stop at the first complete event. */
np_rd_result np_reader_feed(np_reader* r, const uint8_t* in, size_t n, size_t* used, np_packet* out);

#ifdef __cplusplus
}
#endif

#endif /* NP_NET_H */
