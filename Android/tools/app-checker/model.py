"""Core data types for the app checker."""
from dataclasses import dataclass, field


@dataclass
class Node:
    """One element from a uiautomator hierarchy dump."""
    resource_id: str = ""
    cls: str = ""
    text: str = ""
    desc: str = ""
    clickable: bool = False
    focusable: bool = False
    focused: bool = False
    bounds: tuple = (0, 0, 0, 0)  # (x1, y1, x2, y2)

    def center(self):
        x1, y1, x2, y2 = self.bounds
        return ((x1 + x2) // 2, (y1 + y2) // 2)


@dataclass
class Finding:
    """A single reported issue. kind is 'BUG' (deterministic) or 'IMPROVE' (judge)."""
    kind: str
    screen_sig: str
    detail: str


@dataclass
class ScreenRecord:
    sig: str
    shot_path: str = ""       # basename, relative to the run dir
    findings: list = field(default_factory=list)
    label: str = ""


@dataclass
class RunResult:
    surface: str
    device: str
    screens: list = field(default_factory=list)
    started: str = ""
    steps: int = 0
