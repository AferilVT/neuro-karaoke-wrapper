"""Generic depth-first crawl over any Driver."""
import os
import time

from logscan import scan
from model import RunResult, ScreenRecord
from uidump import actionable, is_denylisted, screen_signature


def _label(nodes):
    for n in nodes:
        if n.focused and n.text:
            return n.text
    for n in nodes:
        if not (n.clickable or n.focusable) and n.text:
            return n.text
    for n in nodes:
        if n.text:
            return n.text
    return ""


def crawl(driver, pkg, max_steps, max_depth, out_dir=".", surface="tv", device=""):
    run = RunResult(surface=surface, device=device,
                    started=time.strftime("%Y-%m-%d %H:%M:%S"))
    visited = set()
    counter = [0]
    state = {"steps": 0}

    driver.to_root()

    def visit(depth):
        if state["steps"] >= max_steps:
            return
        try:
            nodes = driver.dump()
        except Exception:
            return
        sig = screen_signature(nodes)
        if sig in visited:
            return
        visited.add(sig)

        shot_base = ""
        shot = os.path.join(out_dir, f"shot{counter[0]}.png")
        counter[0] += 1
        try:
            driver.screenshot(shot)
            shot_base = os.path.basename(shot)
        except Exception:
            shot_base = ""
        try:
            findings = scan(driver.read_logcat(), pkg, sig)
        except Exception:
            findings = []

        run.screens.append(ScreenRecord(
            sig=sig, shot_path=shot_base, findings=findings, label=_label(nodes)))

        if depth >= max_depth:
            return

        targets = [n for n in actionable(nodes) if not is_denylisted(n)]
        for n in targets:
            if state["steps"] >= max_steps:
                return
            state["steps"] += 1
            try:
                driver.enter(n)
            except Exception:
                continue
            visit(depth + 1)

            # Return to this screen. If we can't (e.g. Back exited a TV tab), reset
            # to root: at the top level keep covering sibling tabs; deeper down, the
            # path is lost, so abort this subtree (re-pathing is a future slice).
            reset_needed = False
            try:
                driver.back()
            except Exception:
                reset_needed = True
            if not reset_needed:
                try:
                    if screen_signature(driver.dump()) != sig:
                        reset_needed = True
                except Exception:
                    reset_needed = True
            if reset_needed:
                driver.to_root()
                if depth == 0:
                    continue
                return

    visit(0)
    run.steps = state["steps"]
    return run
