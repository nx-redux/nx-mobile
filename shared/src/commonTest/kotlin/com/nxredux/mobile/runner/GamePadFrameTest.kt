package com.nxredux.mobile.runner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class GamePadFrameTest {
    private fun near(a: Vec3, b: Vec3) = abs(a.x - b.x) < 1e-4f && abs(a.y - b.y) < 1e-4f && abs(a.z - b.z) < 1e-4f
    private fun assertNear(expected: Vec3, actual: Vec3) = assertTrue(near(expected, actual), "expected $expected, got $actual")

    @Test fun the_natural_orientation_passes_the_axes_through() {
        // A handheld whose natural orientation is landscape (the Nova) reads as a GamePad held in front of you.
        val v = Vec3(1f, 2f, 3f)
        assertNear(v, GamePadFrame.accel(v, 0))
        assertNear(v, GamePadFrame.gyro(v, 0))
    }

    @Test fun a_phone_held_in_landscape_reads_gravity_up_the_screen() {
        // ROTATION_90: the phone turned a quarter counter-clockwise, its right edge up; ROTATION_270 the other way.
        assertNear(Vec3(0f, 1f, 0f), GamePadFrame.accel(Vec3(1f, 0f, 0f), 1))
        assertNear(Vec3(0f, 1f, 0f), GamePadFrame.accel(Vec3(-1f, 0f, 0f), 3))
        assertNear(Vec3(0f, 1f, 0f), GamePadFrame.accel(Vec3(0f, -1f, 0f), 2))
    }

    @Test fun turning_like_a_steering_wheel_reads_the_same_in_every_rotation() {
        for (r in 0..3) assertNear(Vec3(0f, 0f, 1.5f), GamePadFrame.gyro(Vec3(0f, 0f, 1.5f), r))
    }

    @Test fun the_screen_axes_match_the_3ds_transform_from_azahars_android_frontend() {
        // N3dsFrame is (-right, out, up) of the screen held; the GamePad's is (right, up, out).
        val v = Vec3(1f, 2f, 3f)
        for (r in 0..3) {
            val n = N3dsFrame.gyro(v, r)
            assertNear(Vec3(-n.x, n.z, n.y), GamePadFrame.gyro(v, r))
        }
    }
}
