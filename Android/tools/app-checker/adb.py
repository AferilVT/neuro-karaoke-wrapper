"""Thin, binary-safe wrappers over the adb CLI."""
import subprocess


class Adb:
    def __init__(self, device, adb_path="adb"):
        self.device = device
        self.adb = adb_path

    def _base(self):
        return [self.adb, "-s", self.device]

    def shell(self, *args):
        r = subprocess.run(self._base() + ["shell", *args],
                           capture_output=True, text=True, errors="replace")
        return r.stdout

    def screencap(self, local_path):
        # Read PNG bytes straight off stdout to avoid shell-redirect corruption.
        r = subprocess.run(self._base() + ["exec-out", "screencap", "-p"],
                           capture_output=True)
        with open(local_path, "wb") as f:
            f.write(r.stdout)

    def dump(self):
        subprocess.run(self._base() + ["shell", "uiautomator", "dump",
                                       "/sdcard/window_dump.xml"], capture_output=True)
        r = subprocess.run(self._base() + ["exec-out", "cat", "/sdcard/window_dump.xml"],
                           capture_output=True)
        return r.stdout.decode("utf-8", "replace")

    def logcat_dump(self):
        r = subprocess.run(self._base() + ["logcat", "-d"],
                           capture_output=True, text=True, errors="replace")
        return r.stdout

    def logcat_clear(self):
        subprocess.run(self._base() + ["logcat", "-c"], capture_output=True)

    def keyevent(self, code):
        subprocess.run(self._base() + ["shell", "input", "keyevent", str(code)],
                       capture_output=True)

    def tap(self, x, y):
        subprocess.run(self._base() + ["shell", "input", "tap", str(x), str(y)],
                       capture_output=True)

    def start_activity(self, component):
        subprocess.run(self._base() + ["shell", "am", "start", "-n", component],
                       capture_output=True)

    def foreground_has(self, pkg):
        """True if the app under test is the resumed/focused app (not the TV
        launcher or a system dialog)."""
        out = self.shell("dumpsys", "activity", "activities")
        for line in out.splitlines():
            if "mResumedActivity" in line:
                return pkg in line
        out = self.shell("dumpsys", "window")
        for line in out.splitlines():
            if "mCurrentFocus" in line:
                return pkg in line
        return True
