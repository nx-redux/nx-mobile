"""Local check of pacing.py's game teardown (no device): python3 -m unittest test_pacing, from this folder."""

import argparse
import unittest
from unittest import mock

import pacing

ROM = "/sdcard/NXRedux/Roms/Nintendo 64 (N64)/Super Mario 64.z64"


class FakeFold:
    def __init__(self, serial, verbose=False):
        self.calls = []
        self.stop_fails = False

    def sh(self, cmd, timeout=120):
        self.calls.append(("sh", cmd))
        return ""

    def require_present(self):
        pass

    def game(self, path, tag, slot=None, timeout=60):
        self.calls.append(("game", path))
        return {"state": "running"}

    def game_stop(self, timeout=20):
        self.calls.append(("game_stop",))
        return None if self.stop_fails else {"state": "stopped"}


def args(**kw):
    a = dict(serial="x", rom=ROM, tag="N64", core="mupen64plus_next", home="/sdcard/NXRedux", state=None, slot=0,
             warmup=0, seconds=0, runs=1, opt=[], ab=None, ab_prop=None, content_fps=60.0, work=False, verbose=False)
    a.update(kw)
    return argparse.Namespace(**a)


@mock.patch.object(pacing, "Fold", FakeFold)
@mock.patch.object(pacing.time, "sleep", lambda s: None)
class Teardown(unittest.TestCase):
    def ran(self, p):
        p.run("run #1")
        return p.dev.calls

    def index(self, calls, pred):
        return next(i for i, c in enumerate(calls) if pred(c))

    def test_a_run_quits_the_game_before_force_stopping_it(self):
        calls = self.ran(pacing.Pacing(args()))
        launched = self.index(calls, lambda c: c[0] == "game")
        stopped = self.index(calls, lambda c: c[0] == "game_stop")
        killed = self.index(calls[launched:], lambda c: c[0] == "sh" and "force-stop" in c[1]) + launched
        self.assertLess(stopped, killed)

    def test_a_quit_that_times_out_still_force_stops(self):
        p = pacing.Pacing(args())
        p.dev.stop_fails = True
        calls = self.ran(p)
        stopped = self.index(calls, lambda c: c[0] == "game_stop")
        self.assertTrue(any(c[0] == "sh" and "force-stop" in c[1] for c in calls[stopped:]))

    def test_the_games_auto_state_files(self):
        self.assertEqual([
            "/sdcard/NXRedux/.userdata/shared/N64-mupen64plus_next/Super Mario 64.state.auto",
            "/sdcard/NXRedux/.userdata/shared/N64-mupen64plus_next/Super Mario 64.z64.st9",
            "/sdcard/NXRedux/.userdata/shared/.minui/N64/Super Mario 64.z64.9.bmp",
            "/sdcard/NXRedux/.userdata/shared/.minui/N64/Super Mario 64.z64.txt",
        ], pacing.Pacing(args()).auto_files())

    def test_the_auto_state_is_set_aside_for_the_runs_and_put_back(self):
        p = pacing.Pacing(args())
        p.prepare()
        aside = " ".join(c[1] for c in p.dev.calls if c[0] == "sh")
        p.dev.calls.clear()
        p.restore()
        back = " ".join(c[1] for c in p.dev.calls if c[0] == "sh")
        for f in p.auto_files():
            self.assertIn("mv %s %s" % (pacing.q(f), pacing.q(f + ".nxpacing")), aside)
            self.assertIn("rm -f %s" % pacing.q(f), back)
            self.assertIn("mv %s %s" % (pacing.q(f + ".nxpacing"), pacing.q(f)), back)


if __name__ == "__main__":
    unittest.main()
