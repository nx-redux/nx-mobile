/*
 *     Copyright (C) 1  Filippo Scognamiglio
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

package com.swordfish.libretrodroid

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object KtUtils {
    fun runOnUIThread(runnable: () -> Unit) {
        if (isUIThread()) {
            runnable()
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    }

    private fun isUIThread(): Boolean = Looper.getMainLooper().isCurrentThread

    fun CountDownLatch.awaitUninterruptibly() {
        while (true) {
            try {
                await()
                return
            } catch (e: InterruptedException) {

            }
        }
    }

    /** Like [awaitUninterruptibly] but gives up after [timeout]; returns false if the latch did not reach zero. */
    fun CountDownLatch.awaitUninterruptibly(timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (true) {
            try {
                return await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
            } catch (e: InterruptedException) {

            }
        }
    }
}