"""Unit tests for the comparison helpers of compare_tools_python.py (run with the oracle interpreter)."""
import math
import unittest
from pathlib import Path

from shapely.geometry import box

import compare_tools_python as tools


class HelpersTest(unittest.TestCase):
    def test_point_metrics_need_a_partner_within_the_tolerance_on_both_sides(self):
        same = tools.point_metrics([(0, 0), (1, 1)], [(1, 1.001), (0, 0), (0, 0)], 0.005)
        self.assertTrue(same["matches"])
        self.assertEqual((2, 2), (same["fxPoints"], same["pythonPoints"]))
        self.assertFalse(tools.point_metrics([(0, 0)], [(0, 0), (5, 5)], 0.005)["matches"])
        missing = tools.point_metrics([(0, 0)], [], 0.005)
        self.assertFalse(missing["matches"])
        self.assertIsNone(missing["maxPointDistance"])
        self.assertTrue(tools.point_metrics([], [], 0.005)["matches"])

    def test_violations_are_named_by_the_pieces_not_by_the_coordinates(self):
        pieces = [box(0, 0, 1, 1), box(1.1, 0, 2, 1), box(10, 0, 11, 1)]
        # Two witnesses of the same gap between parallel edges, and one of another pair.
        self.assertEqual({(0, 1)}, tools.witness_pairs([(1.05, 0.1), (1.05, 0.9)], [pieces]))
        self.assertEqual({(1, 2)}, tools.witness_pairs([(6, 0.5)], [pieces]))
        holes = [box(0.4, 0.4, 0.6, 0.6), box(10.4, 0.4, 10.6, 0.6)]
        self.assertEqual({(0, 0), (2, 1)}, tools.witness_pairs([(0.5, 0.3), (10.5, 0.7)], [pieces, holes]))

    def test_area_metrics_compare_the_unions(self):
        overlapping = [box(0, 0, 2, 1), box(1, 0, 3, 1)]
        merged = [box(0, 0, 3, 1)]
        self.assertTrue(tools.area_metrics(overlapping, merged)["matches"])
        different = tools.area_metrics(merged, [box(0, 0, 3, 1.1)])
        self.assertFalse(different["matches"])
        self.assertTrue(math.isclose(0.3 / 3.3, different["relativeSymmetricDifference"]))

    def test_leaves_flatten_the_legacy_containers(self):
        from shapely.geometry import MultiPolygon
        nested = [MultiPolygon([box(0, 0, 1, 1), box(2, 0, 3, 1)]), [box(5, 5, 6, 6)], None]
        self.assertEqual(3, len(tools.leaves(nested)))

    def test_the_original_methods_are_still_where_the_harness_expects_them(self):
        root = Path(__file__).resolve().parents[2]
        for filename, cls, names in (
                ("ToolRulesCheck.py", "RulesCheck", tools.RULE_FUNCTIONS),
                ("ToolCopperThieving.py", "ToolCopperThieving",
                 ("copper_thieving", "flatten", "on_add_robber_bar_click", "on_new_pattern_plating_object")),
                ("ToolCalibration.py", "ToolCalibration",
                 ("calculate_factors", "gcode_header", "generate_verification_gcode"))):
            source = (root / "appTools" / filename).read_text(encoding="utf-8-sig")
            for name in names:
                self.assertIn("def %s(" % name, source, "%s.%s" % (cls, name))


if __name__ == "__main__":
    unittest.main()
