import unittest

from crawler import crawl
from model import Node


class FakeDriver:
    """In-memory screen graph. Each screen maps to (label, target_sig) edges."""
    def __init__(self, screens):
        self.screens = screens
        self.cur = "root"
        self.stack = []

    def to_root(self):
        self.cur = "root"
        self.stack = []

    def dump(self):
        ns = [Node(text=lbl, cls="Button", clickable=True, bounds=(0, 0, 10, 10))
              for lbl, _ in self.screens[self.cur]]
        ns.append(Node(text=self.cur, cls="Title", bounds=(0, 0, 5, 5)))  # non-actionable label
        return ns

    def screenshot(self, path):
        pass

    def read_logcat(self):
        return ""

    def enter(self, node):
        for lbl, tgt in self.screens[self.cur]:
            if lbl == node.text:
                self.stack.append(self.cur)
                self.cur = tgt
                return
        raise Exception("unreachable")

    def back(self):
        if self.stack:
            self.cur = self.stack.pop()


class TestCrawler(unittest.TestCase):
    def test_visits_all_and_skips_denylist(self):
        d = FakeDriver({
            "root": [("Open A", "A"), ("Sign out", "root")],
            "A": [],
        })
        run = crawl(d, "pkg", max_steps=50, max_depth=5)
        self.assertEqual({s.label for s in run.screens}, {"root", "A"})

    def test_respects_max_steps(self):
        d = FakeDriver({
            "root": [(f"c{i}", f"C{i}") for i in range(10)],
            **{f"C{i}": [] for i in range(10)},
        })
        run = crawl(d, "pkg", max_steps=3, max_depth=9)
        self.assertLessEqual(run.steps, 3)


if __name__ == "__main__":
    unittest.main()
