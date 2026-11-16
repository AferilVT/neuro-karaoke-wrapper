"""LLM UX judge: shells out to the `claude` CLI (subscription auth, no API key)."""
import subprocess

from model import Finding


def build_prompt(surface, abs_path):
    return (
        f"Review the UX of this {surface} app screenshot at {abs_path}. "
        "Read the image, then list concrete bugs and improvements you can see. "
        "Be terse: one finding per line, each prefixed with 'BUG:' or 'IMPROVE:'. "
        "No preamble, no summary."
    )


def _default_runner(prompt, abs_path):
    r = subprocess.run(
        ["claude", "-p", "--model", "claude-haiku-4-5",
         "--allowed-tools", "Read", "--output-format", "text", prompt],
        capture_output=True, text=True, timeout=180,
    )
    combined = (r.stdout or "") + (r.stderr or "")
    if "Not logged in" in combined or "/login" in combined:
        raise RuntimeError(
            "claude CLI not logged in — run `claude` once and /login to enable --judge")
    return r.stdout or ""


def parse_findings(stdout, screen_sig):
    out = []
    for line in stdout.splitlines():
        s = line.strip().lstrip("-*").strip()
        if s[:4].upper() == "BUG:":
            out.append(Finding("BUG", screen_sig, s[4:].strip()))
        elif s[:8].upper() == "IMPROVE:":
            out.append(Finding("IMPROVE", screen_sig, s[8:].strip()))
    return out


def judge_screen(surface, abs_path, screen_sig, runner=_default_runner):
    return parse_findings(runner(build_prompt(surface, abs_path), abs_path), screen_sig)
