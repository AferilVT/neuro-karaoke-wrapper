"""Render a RunResult into a markdown report."""


def render(run):
    lines = []
    lines.append(f"# App Checker Report - {run.surface}")
    lines.append("")
    lines.append(f"- Device: {run.device}")
    lines.append(f"- Started: {run.started}")
    lines.append(f"- Steps: {run.steps}")
    lines.append("")

    bug = sum(1 for s in run.screens for f in s.findings if f.kind == "BUG")
    imp = sum(1 for s in run.screens for f in s.findings if f.kind == "IMPROVE")

    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Screens visited: {len(run.screens)}")
    lines.append(f"- BUG findings: {bug}")
    lines.append(f"- IMPROVE findings: {imp}")
    lines.append("")

    for i, s in enumerate(run.screens):
        lines.append(f"## Screen {i} - {s.label or s.sig[:8]}")
        lines.append("")
        if s.shot_path:
            lines.append(f"![]({s.shot_path})")
            lines.append("")
        if not s.findings:
            lines.append("_No findings._")
        else:
            for f in s.findings:
                lines.append(f"- **{f.kind}**: {f.detail}")
        lines.append("")

    return "\n".join(lines)
