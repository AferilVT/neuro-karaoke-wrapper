import unittest

from model import Finding, RunResult, ScreenRecord
from report import render


def sample_run():
    s0 = ScreenRecord(sig="a", shot_path="shot0.png",
                      findings=[Finding("BUG", "a", "boom")], label="Home")
    s1 = ScreenRecord(sig="b", shot_path="shot1.png",
                      findings=[Finding("IMPROVE", "b", "tighten spacing")], label="Lib")
    return RunResult(surface="tv", device="emu", screens=[s0, s1],
                     started="now", steps=5)


class TestReport(unittest.TestCase):
    def test_render_contains_summary_and_findings(self):
        md = render(sample_run())
        self.assertIn("Screens visited: 2", md)
        self.assertIn("**BUG**", md)
        self.assertIn("**IMPROVE**", md)
        self.assertIn("![](shot0.png)", md)
        self.assertIn("boom", md)
        self.assertIn("tighten spacing", md)


if __name__ == "__main__":
    unittest.main()
