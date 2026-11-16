# App Checker

Autonomous, on-demand UI checker. It walks every reachable interaction in the app,
screenshots each unique screen, scans logcat for crashes/ANRs, and (optionally) has
an LLM judge each screen for UX improvements. Findings are tagged:

- **BUG** — deterministic: crashes, ANRs (free, no LLM).
- **IMPROVE** — LLM judge: unclear labels, hard-to-reach targets, confusing states.

Slice 1 covers the **TV** surface. Phone and Car drivers are future slices (the core
crawler, report, logcat scanner, and judge are surface-agnostic and already in place).

## Requirements

- Python 3 on PATH (stdlib only — no pip installs, no venv).
- `adb` (Android SDK platform-tools). A running emulator/device for the surface.
- For `--judge` only: the `claude` CLI, **logged in** (subscription auth, no API key).
  Run `claude` once and `/login` if you see "claude CLI not logged in".

## Usage

```bash
cd Android/tools/app-checker

# Deterministic crawl (free): screenshots + crash/ANR scan.
python checker.py --surface tv --device emulator-5554 --max-steps 24

# Add the LLM UX judge on the first N unique screens (uses your Claude subscription).
python checker.py --surface tv --device emulator-5554 --judge --max-judge 6
```

Output: `report/tv-<timestamp>/report.md` plus one `shotN.png` per unique screen.

### Flags

| flag | default | meaning |
|------|---------|---------|
| `--surface` | (required) | `tv` (Phone/Car later) |
| `--device` | (required) | adb serial, e.g. `emulator-5554` |
| `--adb` | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` | adb path |
| `--judge` | off | run the LLM UX judge |
| `--max-steps` | 60 | crawl budget (bounds runtime) |
| `--max-depth` | 6 | DFS depth (generic crawler; Phone/Car) |
| `--max-judge` | 30 | cap judged screens (bounds LLM cost) |
| `--out` | `report` | output root |

## How it works

- **Deterministic core** (`crawler.py`, `uidump.py`, `logscan.py`): DFS over the live
  UI graph via `adb` + `uiautomator dump`; screens deduped by a bounds-independent
  signature; logcat scanned for `FATAL EXCEPTION` / `ANR in <pkg>`.
- **TV driver** (`tvwalk.py`, `drivers/tv.py`): Compose-on-TV exposes clickable
  elements as **anonymous `View` nodes** (no text/id), so TV uses a *focus-walk* —
  move D-pad focus (which selects tabs / focuses cards), capture after each move,
  activate with CENTER, Back out. A foreground guard resets to root if a Back exits
  the app into the TV launcher.
- **Judge** (`judge.py`): shells `claude -p --model claude-haiku-4-5 --allowed-tools
  Read` on each screenshot; parses `BUG:` / `IMPROVE:` lines. Subscription auth.
- **Report** (`report.py`): markdown with a summary and per-screen shots + findings.

Safety: the crawler never activates nodes whose text matches a destructive denylist
(`delete`, `remove`, `sign out`, `buy`, ...) — they're recorded, not clicked.

## Tests

```bash
cd Android/tools/app-checker
python -m unittest discover -s tests -t . -v
```

Pure logic (parsing, signatures, denylist, logcat scan, report, judge parse, crawl
DFS via a fake driver) is unit-tested; adb/TV/e2e are verified against a live emulator.
