# Autonomous App Checker (hybrid) — design

Date: 2026-08-02
Branch: feat/android-tv

## Goal

A local, on-demand tool that autonomously walks every reachable interaction in the
app across its three surfaces (TV, Phone, Car) and reports two things per screen:
**BUG** (deterministic: crashes, ANRs, dead-ends, missing/expected controls, focus
traps) and **IMPROVE** (LLM UX notes: unclear labels, hard-to-reach targets,
confusing empty states).

Build order: **TV first** (vertical slice), then Phone, then Car — same core, per-surface driver.

## Engine: hybrid

- **Deterministic crawler** (free, repeatable): generic DFS over the live UI graph via
  `adb` + `uiautomator dump`. Finds crashes/ANRs/dead-ends/missing controls without
  hardcoding flows.
- **LLM judge** (cheap, gated by `--judge`): one screenshot per *unique* screen reviewed
  for UX improvements.

## Harness

Off-device Python 3 in `Android/tools/app-checker/`, driving raw `adb`. Stdlib only —
no pip deps, no venv.

```bash
python checker.py --surface tv --device emulator-5554 [--judge] [--max-steps N] [--max-judge N] [--out report/]
```

### Pipeline per run
1. `adb -s <dev> logcat -c`; relaunch app to root activity.
2. Crawl loop (DFS over UI graph), bounded by `--max-steps` + depth cap:
   - `screencap` -> png; `uiautomator dump` -> parse XML -> actionable nodes.
   - screen **signature** = normalized hash of hierarchy (resource-id / class / text,
     bounds excluded) -> dedupe visited screens.
   - `logcat -d` scan for `FATAL EXCEPTION`, `ANR in <pkg>`, `E AndroidRuntime` since
     last step -> BUG.
   - for each unvisited actionable node: act -> capture -> new signature? recurse;
     then Back. If Back doesn't return to a known signature -> relaunch to root and re-path.
3. LLM judge (if `--judge`): for each unique screen, shell out to the `claude` CLI
   (subscription auth, no API key):
   ```
   claude -p --model claude-haiku-4-5 --allowed-tools Read --output-format text \
     "Review UX of the <surface> app screenshot at <abs-path>. List concrete bugs and
      improvements you can see. Terse, one per line, prefix each with BUG: or IMPROVE:."
   ```
   Capped at `--max-judge` screens; prints the count before spending.
4. Report: `report/<surface>-<timestamp>/report.md` + screenshots. Sections: summary
   (screens visited, crashes, ANRs, dead-ends), per-screen (shot + deterministic
   findings + judge notes), crash-log excerpts. Every finding tagged BUG or IMPROVE.

## Surface driver interface

One abstraction, three implementations:
`actionable_nodes() -> [Node]`, `do(node)`, `back()`, `to_root()`, `focus_signature()`.

- **TV (leanback, slice 1):** no tap. Focus-cycle crawl — press D-pad to move focus
  through focusable elements (`focused=true` in the dump is the current focus),
  `DPAD_CENTER` to activate; detect focus-wrap (focus returns to a seen element) to know
  a screen is exhausted. Track visited-per-screen by focus signature. Trickiest driver,
  which is why TV is first.
- **Phone:** tap node-center from `bounds`. Standard.
- **Car:** last. Android Auto (`car/`) = Car App Library templates via DHU; AAOS (`aaos/`)
  = automotive emulator. Driver TBD when reached (own slice).

## Reset / robustness

- Depth + step caps bound runtime and judge cost.
- `to_root()` = relaunch the launcher activity (`am start`) for the surface.
- If a screen dump fails or focus is lost, `to_root()` and continue from unvisited set.
- Crawler is read-mostly: it activates controls but performs no sign-in, purchase, or
  destructive confirm — nodes whose text matches a denylist (delete/remove/sign out/buy)
  are recorded but not activated.

## Non-goals (v1)

- Not in CI (local on-demand only for now).
- No form text entry / login automation.
- No cross-run regression diffing (future: compare two reports).
- Phone and Car drivers ship in later slices; v1 = harness core + TV driver + report + judge.

## Slice 1 deliverable (TV)

Harness skeleton (crawler, capture, report, judge modules) + TV driver, proven against
`emulator-5554`, producing a real `report/tv-<ts>/report.md` with at least one screenshot
per unique screen and any crashes/ANRs surfaced.
