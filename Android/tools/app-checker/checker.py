"""App checker CLI. Slice 1: TV surface."""
import argparse
import os
import time

import crawler
import judge
import report
from adb import Adb
from drivers.tv import TvDriver

PKG = "com.neurokaraoke"
LAUNCH = "com.neurokaraoke/com.soul.neurokaraoke.MainActivity"


def default_adb():
    return os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")


def main():
    ap = argparse.ArgumentParser(description="Autonomous app checker (TV slice)")
    ap.add_argument("--surface", choices=["tv"], required=True)
    ap.add_argument("--device", required=True)
    ap.add_argument("--adb", default=default_adb())
    ap.add_argument("--judge", action="store_true", help="run the LLM UX judge (claude CLI)")
    ap.add_argument("--max-steps", type=int, default=60)
    ap.add_argument("--max-depth", type=int, default=6)
    ap.add_argument("--max-judge", type=int, default=30)
    ap.add_argument("--out", default="report")
    a = ap.parse_args()

    adb = Adb(a.device, a.adb)
    driver = TvDriver(adb, PKG, LAUNCH)

    ts = time.strftime("%Y%m%d-%H%M%S")
    run_dir = os.path.join(a.out, f"{a.surface}-{ts}")
    os.makedirs(run_dir, exist_ok=True)

    adb.logcat_clear()
    print(f"Crawling {a.surface} on {a.device} (max_steps={a.max_steps})...")
    if a.surface == "tv":
        # TV needs the focus-walk (Compose exposes anonymous clickable nodes).
        from tvwalk import crawl_tv
        run = crawl_tv(driver, PKG, a.max_steps, run_dir, a.device)
    else:
        run = crawler.crawl(driver, PKG, a.max_steps, a.max_depth,
                            out_dir=run_dir, surface=a.surface, device=a.device)
    print(f"Visited {len(run.screens)} unique screens in {run.steps} steps.")

    if a.judge:
        n = min(a.max_judge, len(run.screens))
        print(f"Judging {n} screens via claude CLI (subscription)...")
        for s in run.screens[:n]:
            if not s.shot_path:
                continue
            abs_path = os.path.abspath(os.path.join(run_dir, s.shot_path))
            try:
                s.findings.extend(judge.judge_screen(a.surface, abs_path, s.sig))
            except Exception as e:
                print(f"  judge failed on {s.shot_path}: {e}")

    out_md = os.path.join(run_dir, "report.md")
    with open(out_md, "w", encoding="utf-8") as f:
        f.write(report.render(run))
    print("Report:", out_md)


if __name__ == "__main__":
    main()
