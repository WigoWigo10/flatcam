"""Tests for report metrics/visualization; not substitutes for the actual legacy comparison."""
import unittest
from pathlib import Path

from shapely.geometry import GeometryCollection, LineString, MultiLineString, Polygon, box
from compare_cam_python import compile_rectangular_handler, dependency_metadata, leaves, lines, metrics, overlay, sampled_distance, sampled_witness


class ComparisonReportTest(unittest.TestCase):
    def test_metadata_identifies_loaded_geos_and_shapely(self):
        import shapely
        metadata = dependency_metadata()
        self.assertEqual(shapely.__version__, metadata["shapely"])
        self.assertRegex(metadata["python"], r"^\d+\.\d+\.\d+")
        self.assertRegex(metadata["geos"], r"^\d+\.\d+\.\d+")

    def test_witness_identifies_actual_point_and_nearest_counterpart(self):
        a = LineString([(0, 0), (10, 0)])
        b = LineString([(0, 1), (10, 1)])
        witness = sampled_witness(a, b)
        self.assertEqual("FX", witness["side"])
        self.assertEqual([0, 0], witness["point"])
        self.assertEqual([0, 1], witness["nearest"])
        self.assertEqual(1, witness["distance"])

    def test_actual_nested_handler_keeps_live_bindings_and_uses_original_helpers(self):
        root = Path(__file__).resolve().parents[2]
        if not (root / "appTools" / "ToolCutOut.py").is_file():
            self.skipTest("Legacy checkout unavailable")
        namespace = compile_rectangular_handler(root)
        namespace.update(margin=0, gaps="None")
        handler = namespace["cutout_rect_handler"]
        source = box(0, 0, 20, 10)
        self.assertEqual(60, sum(line.length for line in lines(handler(source, 1.5, 0, 0, 20, 10))))
        namespace["gaps"] = "LR"
        self.assertEqual(54, sum(line.length for line in lines(handler(source, 1.5, 0, 0, 20, 10))))

    def test_identical_reversed_paths_match(self):
        a = LineString([(0, 0), (10, 0)])
        b = LineString([(10, 0), (0, 0)])
        result = metrics(a, b, .003, 1)
        self.assertTrue(result["matchesSampledCriteria"])
        self.assertEqual(1, result["footprintIntersectionOverUnion"])
        self.assertEqual(0, result["sampledDistance"])

    def test_length_bounds_and_distance_reject_shifted_paths(self):
        a = LineString([(0, 0), (10, 0)])
        b = LineString([(0, 1), (10, 1)])
        result = metrics(a, b, .003, 1)
        self.assertFalse(result["matchesSampledCriteria"])
        self.assertEqual(1, result["sampledDistance"])
        self.assertEqual(1, result["boundsMaxDelta"])

    def test_empty_output_is_not_a_match(self):
        with self.assertRaises(ValueError):
            metrics(GeometryCollection(), LineString([(0, 0), (1, 1)]), .003)

    def test_holes_and_multipart_lines_are_not_discarded(self):
        polygon = Polygon([(0, 0), (10, 0), (10, 10), (0, 10)],
                          [[(2, 2), (2, 8), (8, 8), (8, 2)]])
        self.assertEqual(2, len(lines(polygon)))
        multi = MultiLineString([[(0, 0), (1, 0)], [(0, 2), (1, 2)]])
        self.assertEqual(3, len(leaves([polygon, multi])))
        self.assertEqual(0, sampled_distance(multi, multi))

    def test_svg_contains_both_legends_and_inverts_y_only_for_display(self):
        a = LineString([(0, 0), (10, 0)])
        b = LineString([(0, 1), (10, 1)])
        svg = overlay(a, b)
        self.assertIn("#087caa", svg)
        self.assertIn("#e17200", svg)
        self.assertIn("scale(1 -1)", svg)
        self.assertEqual([(0, 0), (10, 0)], list(a.coords))


if __name__ == "__main__":
    unittest.main()
