import pathlib
import unittest

from uidump import (actionable, is_denylisted, parse_nodes, screen_signature)

FX = pathlib.Path(__file__).parent / "fixtures"

XML_A = '<hierarchy><node class="A" text="x" bounds="[0,0][2,2]"/><node class="B" text="y" bounds="[0,0][2,2]"/></hierarchy>'
XML_A_SHIFTED = '<hierarchy><node class="A" text="x" bounds="[5,5][7,7]"/><node class="B" text="y" bounds="[9,9][11,11]"/></hierarchy>'
XML_B = '<hierarchy><node class="A" text="DIFFERENT" bounds="[0,0][2,2]"/></hierarchy>'


class TestParse(unittest.TestCase):
    def setUp(self):
        self.nodes = parse_nodes((FX / "dump_home.xml").read_text(encoding="utf-8"))

    def test_parses_all(self):
        self.assertEqual(len(self.nodes), 3)

    def test_center(self):
        n = next(n for n in self.nodes if n.text == "Home")
        self.assertEqual(n.center(), ((100 + 300) // 2, (50 + 120) // 2))

    def test_actionable_filters_labels(self):
        act = actionable(self.nodes)
        self.assertEqual({n.text for n in act}, {"Home", "Search"})


class TestSignatureDenylist(unittest.TestCase):
    def test_signature_stable_across_bounds(self):
        self.assertEqual(screen_signature(parse_nodes(XML_A)),
                         screen_signature(parse_nodes(XML_A_SHIFTED)))

    def test_signature_differs_on_content(self):
        self.assertNotEqual(screen_signature(parse_nodes(XML_A)),
                            screen_signature(parse_nodes(XML_B)))

    def test_denylist(self):
        n = parse_nodes('<hierarchy><node text="Sign out" class="x" bounds="[0,0][1,1]"/></hierarchy>')[0]
        self.assertTrue(is_denylisted(n))
        ok = parse_nodes('<hierarchy><node text="Play" class="x" bounds="[0,0][1,1]"/></hierarchy>')[0]
        self.assertFalse(is_denylisted(ok))


if __name__ == "__main__":
    unittest.main()
