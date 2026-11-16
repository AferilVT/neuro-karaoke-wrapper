"""TV-specific crawl: a focus-walk.

Compose-on-TV exposes clickable elements as anonymous `View` nodes (no text/id),
so the generic node-identity crawler can't target them. Instead we move D-pad
focus and let the screen tell us what happened: focusing a nav tab selects it
(new screen signature), and activating a focused card opens a detail we can Back
out of. Screens are deduped by signature; screenshots / logcat / judge are
identity-agnostic and work unchanged.
"""
import os
import time

from drivers.tv import (BACK, DPAD_CENTER, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
                        DPAD_UP)
from logscan import scan
from model import RunResult, ScreenRecord
from uidump import screen_signature


def _focused(nodes):
    for n in nodes:
        if n.focused:
            return n
    return None


def _label(nodes):
    # Labels are separate text nodes in Compose. The nav bar's short chips ("Home")
    # appear on every screen, so pick the longest text — titles/song names win.
    texts = [n.text.strip() for n in nodes if n.text.strip()]
    return max(texts, key=len) if texts else ""


def crawl_tv(driver, pkg, max_steps, out_dir, device, tabs=8, content_per_tab=3):
    adb = driver.adb
    run = RunResult(surface="tv", device=device,
                    started=time.strftime("%Y-%m-%d %H:%M:%S"))
    visited = {}
    counter = [0]
    steps = [0]

    def ensure_app():
        """If the walk escaped into the launcher / a system dialog, relaunch."""
        if not adb.foreground_has(pkg):
            driver.to_root()

    def snap():
        """Record the current screen if it's in-app and its signature is new."""
        if not adb.foreground_has(pkg):
            return None, None, False
        nodes = driver.dump()
        sig = screen_signature(nodes)
        if sig in visited:
            return sig, nodes, False
        base = ""
        shot = os.path.join(out_dir, f"shot{counter[0]}.png")
        counter[0] += 1
        try:
            driver.screenshot(shot)
            base = os.path.basename(shot)
        except Exception:
            base = ""
        try:
            findings = scan(adb.logcat_dump(), pkg, sig)
        except Exception:
            findings = []
        rec = ScreenRecord(sig=sig, shot_path=base, findings=findings,
                           label=_label(nodes))
        visited[sig] = rec
        run.screens.append(rec)
        return sig, nodes, True

    driver.to_root()
    adb.keyevent(DPAD_UP)          # reach the nav row
    time.sleep(0.3)
    for _ in range(tabs):          # go to the leftmost tab
        adb.keyevent(DPAD_LEFT)
        time.sleep(0.15)
    snap()

    for _ in range(tabs):
        if steps[0] >= max_steps:
            break
        # Sample this tab's content: drop into the first row, activate a few items.
        adb.keyevent(DPAD_DOWN)
        time.sleep(0.6)
        seen_focus = set()
        for _ in range(content_per_tab):
            if steps[0] >= max_steps:
                break
            f = _focused(driver.dump())
            key = f.bounds if f else None
            if key in seen_focus:
                adb.keyevent(DPAD_DOWN)      # try the next row
                time.sleep(0.4)
                f2 = _focused(driver.dump())
                if (f2.bounds if f2 else None) == key:
                    break                     # focus stuck -> row exhausted
                continue
            seen_focus.add(key)
            adb.keyevent(DPAD_CENTER)         # activate
            time.sleep(1.2)
            steps[0] += 1
            snap()
            adb.keyevent(BACK)                # return from any detail
            time.sleep(0.8)
            ensure_app()                      # BACK on a root tab can exit the app
            adb.keyevent(DPAD_RIGHT)          # next item
            time.sleep(0.3)
        # Back to the nav row, advance to the next tab (focus selects it).
        ensure_app()
        adb.keyevent(DPAD_UP)
        time.sleep(0.5)
        adb.keyevent(DPAD_RIGHT)
        time.sleep(0.5)
        steps[0] += 1
        snap()

    run.steps = steps[0]
    return run
