# App Checker — TV Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the off-device Python app-checker harness core plus the TV (leanback) driver, producing a real crawl report against the TV emulator.

**Architecture:** A stdlib-only Python 3 tool in `Android/tools/app-checker/` drives raw `adb`. Pure logic (UI-dump parsing, screen signatures, logcat scanning, report + judge-output rendering, DFS crawl) is unit-tested with `unittest`; integration pieces (adb wrappers, TV focus driver, end-to-end run) are smoke-verified against `emulator-5554`. The LLM judge shells out to the `claude` CLI (subscription auth) — no Anthropic API key.

**Tech Stack:** Python 3 (stdlib only: `subprocess`, `xml.etree.ElementTree`, `hashlib`, `argparse`, `dataclasses`, `unittest`), Android `adb`, `claude` CLI.

## Global Constraints

- Stdlib only — no pip installs, no venv. Tests run via `python -m unittest`.
- Package under test: applicationId `com.neurokaraoke`; launcher activity `com.soul.neurokaraoke.MainActivity` (LEANBACK_LAUNCHER on TV).
- adb path on this machine: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`; pass device via `-s`. TV emulator = `emulator-5554`.
- Judge uses: `claude -p --model claude-haiku-4-5 --allowed-tools Read --output-format text "<prompt>"`. No `ANTHROPIC_API_KEY`.
- Crawler is read-mostly: nodes whose text/desc match the destructive denylist (`delete`, `remove`, `sign out`, `log out`, `buy`, `purchase`, `unsubscribe`) are recorded but never activated.
- Every finding is tagged `BUG` (deterministic) or `IMPROVE` (judge).

---

## File Structure

```
Android/tools/app-checker/
  checker.py          # CLI entry + orchestration
  model.py            # dataclasses: Node, Finding, ScreenRecord, RunResult
  uidump.py           # parse uiautomator XML -> nodes; actionable filter; signature; denylist
  logscan.py          # logcat text -> crash/ANR Findings
  report.py           # RunResult -> markdown
  judge.py            # build prompt, run claude CLI (injectable runner), parse findings
  adb.py              # thin adb wrappers (shell/screencap/dump/logcat/keyevent/tap/am)
  crawler.py          # generic DFS over a Driver
  drivers/
    __init__.py
    base.py           # Driver protocol
    tv.py             # TV leanback focus-cycle driver
  tests/
    __init__.py
    fixtures/         # sample dump XML + logcat text
    test_uidump.py
    test_logscan.py
    test_report.py
    test_judge.py
    test_crawler.py
```

---

### Task 1: Model + UI-dump parsing

**Files:**
- Create: `Android/tools/app-checker/model.py`
- Create: `Android/tools/app-checker/uidump.py`
- Create: `Android/tools/app-checker/tests/fixtures/dump_home.xml`
- Test: `Android/tools/app-checker/tests/test_uidump.py`

**Interfaces:**
- Produces:
  - `Node(resource_id:str, cls:str, text:str, desc:str, clickable:bool, focusable:bool, focused:bool, bounds:tuple[int,int,int,int])` with `center() -> tuple[int,int]`.
  - `uidump.parse_nodes(xml:str) -> list[Node]`
  - `uidump.actionable(nodes:list[Node]) -> list[Node]`  (clickable or focusable, on-screen, non-empty bounds)

- [ ] **Step 1: Write fixture** `dump_home.xml` — a minimal uiautomator dump with 3 nodes: one `clickable="true"` with `resource-id="com.neurokaraoke:id/home"` text "Home", one `focusable="true" focused="true"` text "Search", one non-actionable `TextView` text "label", each with a `bounds="[x1,y1][x2,y2]"`.

- [ ] **Step 2: Write failing tests** in `test_uidump.py`:

```python
import unittest, pathlib
from uidump import parse_nodes, actionable

FX = pathlib.Path(__file__).parent / "fixtures"

class TestParse(unittest.TestCase):
    def setUp(self):
        self.nodes = parse_nodes((FX / "dump_home.xml").read_text(encoding="utf-8"))
    def test_parses_all(self):
        self.assertEqual(len(self.nodes), 3)
    def test_center(self):
        n = next(n for n in self.nodes if n.text == "Home")
        self.assertEqual(n.center(), ( (n.bounds[0]+n.bounds[2])//2, (n.bounds[1]+n.bounds[3])//2 ))
    def test_actionable_filters_labels(self):
        act = actionable(self.nodes)
        self.assertEqual({n.text for n in act}, {"Home", "Search"})
```

- [ ] **Step 3: Run, verify fail** — `cd Android/tools/app-checker && python -m unittest tests.test_uidump -v` → FAIL (no module).

- [ ] **Step 4: Implement** `model.py` (`Node` dataclass with `center()`) and `uidump.parse_nodes` (walk XML with `xml.etree.ElementTree`, read attrs `resource-id/class/text/content-desc/clickable/focusable/focused/bounds`, parse `bounds` `[x1,y1][x2,y2]` via regex) and `uidump.actionable` (keep `clickable or focusable`, drop zero-area bounds).

- [ ] **Step 5: Run, verify pass.**

- [ ] **Step 6: Commit** `git add Android/tools/app-checker && git commit -m "feat(checker): model + uiautomator dump parsing"`

---

### Task 2: Screen signature + denylist

**Files:**
- Modify: `Android/tools/app-checker/uidump.py`
- Test: `Android/tools/app-checker/tests/test_uidump.py`

**Interfaces:**
- Produces:
  - `uidump.screen_signature(nodes:list[Node]) -> str`  (sha1 of sorted `resource_id|cls|text` tuples; bounds excluded so position jitter doesn't fork screens)
  - `uidump.is_denylisted(node:Node) -> bool`  (case-insensitive match of text/desc against DENYLIST)
  - `uidump.DENYLIST: tuple[str,...]`

- [ ] **Step 1: Add failing tests:**

```python
from uidump import screen_signature, is_denylisted, parse_nodes
def test_signature_stable_across_bounds(self):
    a = parse_nodes(XML_A); b = parse_nodes(XML_A_SHIFTED_BOUNDS)
    self.assertEqual(screen_signature(a), screen_signature(b))
def test_signature_differs_on_content(self):
    self.assertNotEqual(screen_signature(parse_nodes(XML_A)), screen_signature(parse_nodes(XML_B)))
def test_denylist(self):
    n = parse_nodes('<hierarchy><node text="Sign out" class="x" bounds="[0,0][1,1]"/></hierarchy>')[0]
    self.assertTrue(is_denylisted(n))
```
(Define `XML_A`, `XML_A_SHIFTED_BOUNDS`, `XML_B` inline as small hierarchy strings.)

- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement** `screen_signature` (build `"\n".join(sorted(f"{n.resource_id}|{n.cls}|{n.text}" for n in nodes))`, `hashlib.sha1(...).hexdigest()`), `DENYLIST`, `is_denylisted`.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(checker): screen signature + destructive-node denylist`

---

### Task 3: Logcat crash/ANR scanner

**Files:**
- Create: `Android/tools/app-checker/logscan.py`
- Create: `Android/tools/app-checker/tests/fixtures/logcat_crash.txt`
- Test: `Android/tools/app-checker/tests/test_logscan.py`

**Interfaces:**
- Consumes: `Finding` (defined here in `model.py`) — add `Finding(kind:str, screen_sig:str, detail:str)` where `kind in {"BUG","IMPROVE"}`.
- Produces: `logscan.scan(logcat_text:str, pkg:str, screen_sig:str) -> list[Finding]`

- [ ] **Step 1: Fixture** `logcat_crash.txt` — a few benign lines plus a `FATAL EXCEPTION: main` block naming `com.neurokaraoke` and an `ANR in com.neurokaraoke` line.

- [ ] **Step 2: Failing test:**

```python
import unittest, pathlib
from logscan import scan
FX = pathlib.Path(__file__).parent / "fixtures"
class T(unittest.TestCase):
    def test_finds_crash_and_anr(self):
        f = scan((FX/"logcat_crash.txt").read_text(), "com.neurokaraoke", "sig1")
        kinds = [x.detail.split(":")[0] for x in f]
        self.assertIn("FATAL", " ".join(x.detail for x in f))
        self.assertTrue(any("ANR" in x.detail for x in f))
        self.assertTrue(all(x.kind == "BUG" and x.screen_sig == "sig1" for x in f))
    def test_clean_log_no_findings(self):
        self.assertEqual(scan("I/foo just fine\n", "com.neurokaraoke", "s"), [])
```

- [ ] **Step 3: Run, verify fail.**
- [ ] **Step 4: Add `Finding` to `model.py`; implement `logscan.scan`** — for each line, flag `FATAL EXCEPTION`, `E AndroidRuntime`, or `ANR in <pkg>` that references `pkg`; return `Finding("BUG", screen_sig, "<matched line trimmed>")`. Dedupe identical details.
- [ ] **Step 5: Run, verify pass.**
- [ ] **Step 6: Commit** `feat(checker): logcat crash/ANR scanner`

---

### Task 4: Report renderer

**Files:**
- Create: `Android/tools/app-checker/report.py`
- Test: `Android/tools/app-checker/tests/test_report.py`

**Interfaces:**
- Consumes: `ScreenRecord`, `RunResult` (add to `model.py`):
  - `ScreenRecord(sig:str, shot_path:str, findings:list[Finding], label:str)`
  - `RunResult(surface:str, device:str, screens:list[ScreenRecord], started:str, steps:int)`
- Produces: `report.render(run:RunResult) -> str` (markdown)

- [ ] **Step 1: Failing test** — build a `RunResult` with 2 screens (one with a BUG finding, one with an IMPROVE), assert the markdown contains a summary line with the screen count, both findings' details, tags `BUG`/`IMPROVE`, and each `shot_path` as a `![](...)` image link.

```python
def test_render_contains_summary_and_findings(self):
    md = render(sample_run())
    self.assertIn("Screens visited: 2", md)
    self.assertIn("**BUG**", md); self.assertIn("**IMPROVE**", md)
    self.assertIn("![](shot0.png)", md)
```

- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement `report.render`** — header (surface/device/time/steps), `## Summary` (screens visited, total BUG count, total IMPROVE count, crash count), then `## Screen N — <label>` per screen with the image and a bulleted findings list `- **BUG**: detail`.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(checker): markdown report renderer`

---

### Task 5: Judge (prompt + parse; injectable runner)

**Files:**
- Create: `Android/tools/app-checker/judge.py`
- Test: `Android/tools/app-checker/tests/test_judge.py`

**Interfaces:**
- Produces:
  - `judge.build_prompt(surface:str, abs_path:str) -> str`
  - `judge.parse_findings(stdout:str, screen_sig:str) -> list[Finding]`  (lines starting `BUG:`/`IMPROVE:` → Finding; ignore others)
  - `judge.judge_screen(surface, abs_path, screen_sig, runner=_default_runner) -> list[Finding]` where `runner(prompt, abs_path) -> str` is injectable (default shells the `claude` CLI).

- [ ] **Step 1: Failing test** (no CLI call — inject a fake runner):

```python
from judge import build_prompt, parse_findings, judge_screen
def test_prompt_mentions_surface_and_path(self):
    p = build_prompt("tv", "/x/y.png")
    self.assertIn("tv", p); self.assertIn("/x/y.png", p); self.assertIn("IMPROVE:", p)
def test_parse(self):
    out = "BUG: button off screen\nnoise\nIMPROVE: label unclear\n"
    fs = parse_findings(out, "sig")
    self.assertEqual([(f.kind,f.detail) for f in fs],
                     [("BUG","button off screen"),("IMPROVE","label unclear")])
def test_judge_uses_runner(self):
    fs = judge_screen("tv","/x.png","sig", runner=lambda p,a: "IMPROVE: tighten spacing")
    self.assertEqual(fs[0].kind, "IMPROVE")
```

- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** `build_prompt` returns the spec's prompt string (asks for one finding per line, each prefixed `BUG:` or `IMPROVE:`). `parse_findings` splits lines, maps prefix→kind. `_default_runner(prompt, abs_path)` runs `subprocess.run(["claude","-p","--model","claude-haiku-4-5","--allowed-tools","Read","--output-format","text", prompt], capture_output=True, text=True, timeout=120).stdout`.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(checker): LLM judge via claude CLI (prompt + parse)`

---

### Task 6: Driver protocol + generic DFS crawler

**Files:**
- Create: `Android/tools/app-checker/drivers/__init__.py`
- Create: `Android/tools/app-checker/drivers/base.py`
- Create: `Android/tools/app-checker/crawler.py`
- Test: `Android/tools/app-checker/tests/test_crawler.py`

**Interfaces:**
- Produces:
  - `base.Driver` protocol: `to_root()`, `dump() -> list[Node]`, `screenshot(path)`, `read_logcat() -> str`, `enter(node:Node) -> None`, `back() -> None`.
  - `crawler.crawl(driver:Driver, pkg:str, max_steps:int, max_depth:int) -> RunResult`
- Consumes: `uidump.actionable/screen_signature/is_denylisted`, `logscan.scan`.

- [ ] **Step 1: Failing test with a FakeDriver** — an in-memory tree of screens (dict sig → list of child nodes → target sig). `crawl` should visit every reachable screen exactly once, never enter denylisted nodes, and stop at `max_steps`.

```python
class FakeDriver:
    # screens: {"root":[("Open A","A"),("Sign out","root")], "A":[]}
    ...
def test_visits_all_and_skips_denylist(self):
    run = crawl(FakeDriver(), "pkg", max_steps=50, max_depth=5)
    self.assertEqual({s.label for s in run.screens}, {"root","A"})   # B unreachable; denylist not entered
def test_respects_max_steps(self):
    run = crawl(BigFakeDriver(), "pkg", max_steps=3, max_depth=9)
    self.assertLessEqual(run.steps, 3)
```

- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement `crawl`** — DFS: `to_root()`; recurse(depth): dump→sig; if seen return; record ScreenRecord (screenshot + `logscan.scan`); for each `actionable` non-denylisted node not yet tried on this sig: `enter(node)`, recurse, `back()` (then re-dump; if sig changed unexpectedly, `to_root()` and replay path is out of scope for v1 — instead abort this screen's remaining children and return). Respect `max_steps`/`max_depth`. Screenshot path via a counter under the run's out dir.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(checker): driver protocol + generic DFS crawler`

---

### Task 7: adb wrappers

**Files:**
- Create: `Android/tools/app-checker/adb.py`

**Interfaces:**
- Produces: `Adb(device:str, adb_path:str)` with `shell(*args)->str`, `screencap(local_path)`, `dump()->str` (pull `/sdcard/window_dump.xml`), `logcat_dump()->str`, `logcat_clear()`, `keyevent(code)`, `tap(x,y)`, `start_activity(component)`.

- [ ] **Step 1: Implement `adb.py`** — wrap `subprocess.run([adb_path,"-s",device,...])`. `screencap`: `shell exec-out screencap -p` written as **binary** to `local_path` (use `subprocess.run(..., capture_output=True)` and write `.stdout` bytes — avoids the PowerShell BOM corruption). `dump`: `shell uiautomator dump /sdcard/window_dump.xml` then `exec-out cat /sdcard/window_dump.xml`.

- [ ] **Step 2: Integration check** — with `emulator-5554` running:
  `python -c "from adb import Adb; a=Adb('emulator-5554', r'%LOCALAPPDATA%\\Android\\Sdk\\platform-tools\\adb.exe'); a.screencap('t.png'); print('dump len', len(a.dump()))"`
  Expected: `t.png` is a valid PNG (>0 bytes) and dump len > 0.

- [ ] **Step 3: Commit** `feat(checker): adb wrappers (binary-safe screencap + dump)`

---

### Task 8: TV leanback driver

**Files:**
- Create: `Android/tools/app-checker/drivers/tv.py`

**Interfaces:**
- Produces: `TvDriver(adb:Adb, pkg:str, launch_component:str)` implementing `base.Driver`.
- TV specifics: `enter(node)` = move focus to `node` then `DPAD_CENTER`; `to_root()` = `start_activity(launch_component)`.

- [ ] **Step 1: Implement `TvDriver`.**
  - `dump()` → `uidump.parse_nodes(adb.dump())`.
  - `screenshot/read_logcat/back()` (KEYCODE_BACK=4) → adb.
  - `enter(node)`: focus-seek — loop up to N times: dump, find `focused=true` node; if its signature == target `node` (match by `resource_id|cls|text`) → `keyevent(DPAD_CENTER=23)` and return; else press `DPAD_RIGHT=22` (and every M steps `DPAD_DOWN=20`) to advance focus; if focus stops changing (wrap detected) → give up (raise `FocusUnreachable`). Crawler treats `FocusUnreachable` as "skip node".
  - Keycodes: BACK=4, DPAD_UP=19, DOWN=20, LEFT=21, RIGHT=22, CENTER=23.

- [ ] **Step 2: Integration smoke** — with app on TV:
  `python -c "from adb import Adb; from drivers.tv import TvDriver; ..."` launch to root, dump, assert ≥3 focusable nodes (Home/Search/Library…). Confirm `enter()` on the Library nav node lands the Library screen (dump then shows the segment bar text "Setlists").

- [ ] **Step 3: Commit** `feat(checker): TV leanback focus driver`

---

### Task 9: CLI entry + end-to-end TV run

**Files:**
- Create: `Android/tools/app-checker/checker.py`
- Create: `Android/tools/app-checker/README.md`

**Interfaces:**
- CLI: `--surface {tv}`, `--device`, `--adb` (default from `%LOCALAPPDATA%`), `--judge`, `--max-steps` (default 60), `--max-depth` (default 6), `--max-judge` (default 30), `--out` (default `report/`).

- [ ] **Step 1: Implement `checker.py`** — parse args; build `Adb` + `TvDriver`; `adb.logcat_clear()`; `run = crawler.crawl(...)`; if `--judge`: for up to `--max-judge` unique screens call `judge.judge_screen(surface, abs(shot), sig)` and extend findings (print `"Judging N screens…"` first); write `report.render(run)` to `out/tv-<ts>/report.md`; print the path.

- [ ] **Step 2: End-to-end (no judge)** — `python checker.py --surface tv --device emulator-5554 --max-steps 40`. Expected: `report/tv-<ts>/report.md` exists, lists multiple unique screens with screenshots, no crash in the harness itself.

- [ ] **Step 3: End-to-end (judge)** — rerun with `--judge --max-judge 6`. Expected: report now has `IMPROVE`/`BUG` judge notes on the first 6 screens.

- [ ] **Step 4: Write `README.md`** — usage, examples, what BUG vs IMPROVE mean, that judge needs the `claude` CLI logged in, and that Phone/Car drivers are future slices.

- [ ] **Step 5: Commit** `feat(checker): CLI + end-to-end TV crawl with report`

---

## Self-Review

- **Spec coverage:** harness/pipeline (Tasks 6–9), deterministic crash/ANR (Task 3), dead-end/missing-control via crawl records (Task 6), focus traps (Task 8 `FocusUnreachable`), signature dedupe (Task 2), denylist (Tasks 2,6), judge via CLI (Task 5,9), report BUG/IMPROVE tags (Tasks 3,4,5), TV-first slice (Tasks 7–9). Phone/Car explicitly deferred (spec non-goals). Covered.
- **Placeholder scan:** none — every code step has concrete signatures/tests.
- **Type consistency:** `Finding(kind,screen_sig,detail)`, `Node`, `ScreenRecord`, `RunResult` used consistently across tasks; `Driver` methods (`to_root/dump/screenshot/read_logcat/enter/back`) match between base (Task 6), FakeDriver (Task 6 test), and TvDriver (Task 8).
