"""Parse uiautomator XML dumps into Nodes; screen signatures; denylist."""
import hashlib
import re
import xml.etree.ElementTree as ET

from model import Node

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

# Controls the crawler must never activate (irreversible / account / payment).
DENYLIST = (
    "delete", "remove", "sign out", "log out", "logout",
    "buy", "purchase", "unsubscribe",
)


def _parse_bounds(s):
    m = _BOUNDS.search(s or "")
    if not m:
        return (0, 0, 0, 0)
    return tuple(int(g) for g in m.groups())


def parse_nodes(xml):
    """Flatten every <node> in the dump (any depth) into a list of Node."""
    root = ET.fromstring(xml)
    out = []
    for el in root.iter("node"):
        a = el.attrib
        out.append(Node(
            resource_id=a.get("resource-id", ""),
            cls=a.get("class", ""),
            text=a.get("text", ""),
            desc=a.get("content-desc", ""),
            clickable=a.get("clickable") == "true",
            focusable=a.get("focusable") == "true",
            focused=a.get("focused") == "true",
            bounds=_parse_bounds(a.get("bounds", "")),
        ))
    return out


def actionable(nodes):
    """Nodes worth interacting with: clickable or focusable, with real bounds."""
    res = []
    for n in nodes:
        if not (n.clickable or n.focusable):
            continue
        x1, y1, x2, y2 = n.bounds
        if x2 <= x1 or y2 <= y1:
            continue
        res.append(n)
    return res


def screen_signature(nodes):
    """Stable identity of a screen. Excludes bounds so position jitter doesn't
    fork one screen into many."""
    key = "\n".join(sorted(f"{n.resource_id}|{n.cls}|{n.text}" for n in nodes))
    return hashlib.sha1(key.encode("utf-8")).hexdigest()


def is_denylisted(node):
    hay = f"{node.text} {node.desc}".lower()
    return any(term in hay for term in DENYLIST)
