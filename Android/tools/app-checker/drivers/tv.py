"""TV (leanback) driver: navigates by moving D-pad focus, not tapping."""
import time

from uidump import parse_nodes

BACK = 4
DPAD_UP = 19
DPAD_DOWN = 20
DPAD_LEFT = 21
DPAD_RIGHT = 22
DPAD_CENTER = 23


class FocusUnreachable(Exception):
    pass


def _key(n):
    return f"{n.resource_id}|{n.cls}|{n.text}"


class TvDriver:
    def __init__(self, adb, pkg, launch_component):
        self.adb = adb
        self.pkg = pkg
        self.launch = launch_component

    def to_root(self):
        self.adb.start_activity(self.launch)
        time.sleep(2.0)

    def dump(self):
        return parse_nodes(self.adb.dump())

    def screenshot(self, path):
        self.adb.screencap(path)

    def read_logcat(self):
        return self.adb.logcat_dump()

    def _focused(self, nodes):
        for n in nodes:
            if n.focused:
                return n
        return None

    def enter(self, node):
        """Move focus to `node` (matched by id|class|text) then press center.

        Cycles focus with RIGHT, dropping a row with DOWN when focus stalls.
        Raises FocusUnreachable if focus can't reach the target."""
        target = _key(node)
        last = None
        stalls = 0
        for _ in range(40):
            f = self._focused(self.dump())
            if f is not None and _key(f) == target:
                self.adb.keyevent(DPAD_CENTER)
                time.sleep(1.2)
                return
            cur = _key(f) if f else None
            if cur == last:
                stalls += 1
            else:
                stalls = 0
            last = cur
            if stalls >= 2:
                self.adb.keyevent(DPAD_DOWN)
                time.sleep(0.3)
                stalls = 0
                continue
            self.adb.keyevent(DPAD_RIGHT)
            time.sleep(0.3)
        raise FocusUnreachable(target)

    def back(self):
        self.adb.keyevent(BACK)
        time.sleep(0.8)
