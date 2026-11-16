import pathlib
import unittest

from logscan import scan

FX = pathlib.Path(__file__).parent / "fixtures"


class TestLogscan(unittest.TestCase):
    def test_finds_crash_and_anr(self):
        f = scan((FX / "logcat_crash.txt").read_text(encoding="utf-8"),
                 "com.neurokaraoke", "sig1")
        joined = " ".join(x.detail for x in f)
        self.assertIn("FATAL", joined)
        self.assertTrue(any("ANR" in x.detail for x in f))
        self.assertTrue(all(x.kind == "BUG" and x.screen_sig == "sig1" for x in f))

    def test_clean_log_no_findings(self):
        self.assertEqual(scan("I/foo just fine\n", "com.neurokaraoke", "s"), [])


if __name__ == "__main__":
    unittest.main()
