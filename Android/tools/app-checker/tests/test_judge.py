import unittest

from judge import build_prompt, judge_screen, parse_findings


class TestJudge(unittest.TestCase):
    def test_prompt_mentions_surface_and_path(self):
        p = build_prompt("tv", "/x/y.png")
        self.assertIn("tv", p)
        self.assertIn("/x/y.png", p)
        self.assertIn("IMPROVE:", p)

    def test_parse(self):
        out = "BUG: button off screen\nnoise line\nIMPROVE: label unclear\n"
        fs = parse_findings(out, "sig")
        self.assertEqual([(f.kind, f.detail) for f in fs],
                         [("BUG", "button off screen"), ("IMPROVE", "label unclear")])

    def test_parse_tolerates_bullets(self):
        fs = parse_findings("- BUG: x\n* IMPROVE: y\n", "s")
        self.assertEqual([f.kind for f in fs], ["BUG", "IMPROVE"])

    def test_judge_uses_injected_runner(self):
        fs = judge_screen("tv", "/x.png", "sig",
                          runner=lambda p, a: "IMPROVE: tighten spacing")
        self.assertEqual(fs[0].kind, "IMPROVE")
        self.assertEqual(fs[0].detail, "tighten spacing")


if __name__ == "__main__":
    unittest.main()
