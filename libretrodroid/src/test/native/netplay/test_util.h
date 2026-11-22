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
 * What the netplay host tests share: the CHECK a test body fails with, and the real-time helpers. Included after a
 * test's feature-test macros and system headers.
 */

#ifndef NP_TEST_UTIL_H
#define NP_TEST_UTIL_H

#include <stdint.h>
#include <stdio.h>
#include <time.h>

#define CHECK(cond)                                                                    \
	do {                                                                               \
		if (!(cond)) {                                                                 \
			fprintf(stderr, "  check failed %s:%d: %s\n", __FILE__, __LINE__, #cond); \
			return -1;                                                                 \
		}                                                                              \
	} while (0)

static inline uint64_t real_ms(void) {
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000u + (uint64_t)ts.tv_nsec / 1000000u;
}

static inline void nap_ms(int ms) {
	struct timespec ts = {ms / 1000, (long)(ms % 1000) * 1000000L};
	nanosleep(&ts, NULL);
}

#endif
