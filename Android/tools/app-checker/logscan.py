"""Scan a logcat dump for crashes / ANRs referencing the app under test."""
from model import Finding


def scan(logcat_text, pkg, screen_sig):
    findings = []
    seen = set()
    for line in logcat_text.splitlines():
        s = line.strip()
        hit = None
        if "FATAL EXCEPTION" in s:
            hit = "FATAL: " + s
        elif f"ANR in {pkg}" in s:
            hit = "ANR: " + s
        elif "AndroidRuntime" in s and pkg in s and " E " in s:
            hit = "AndroidRuntime: " + s
        if hit and hit not in seen:
            seen.add(hit)
            findings.append(Finding("BUG", screen_sig, hit))
    return findings
