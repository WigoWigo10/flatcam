"""Tests for report metrics/visualization; not substitutes for the actual legacy comparison."""
import unittest
from pathlib import Path
from types import SimpleNamespace

from shapely.geometry import GeometryCollection, LineString, MultiLineString, Polygon, box
from compare_cam_python import (classify_result, compile_rectangular_handler, compile_plugin_methods, dependency_metadata,
                               isolation_exception_paths, ncc_boundary, leaves, lines, metrics, overlay,
                               sampled_distance, sampled_witness)


class ComparisonReportTest(unittest.TestCase):
    def legacy_root(self):
        root = Path(__file__).resolve().parents[2]
        if not (root / "appTools" / "ToolNCC.py").is_file():
            self.skipTest("Legacy checkout unavailable")
        return root

    def engine(self, geometry):
        app = SimpleNamespace(abort_flag=False, inform=SimpleNamespace(emit=lambda *args: None))
        return SimpleNamespace(app=app, solid_geometry=geometry)

    def test_original_exception_helper_clips_each_line_without_dropping_disjoint_paths(self):
        source = [LineString([(0,0),(10,0)]), LineString([(0,2),(10,2)])]
        result = isolation_exception_paths(source,box(4,-1,6,3),self.legacy_root(),{})
        self.assertAlmostEqual(16,sum(line.length for line in lines(result)))
        self.assertTrue(all(line.intersection(box(4.1,-.5,5.9,2.5)).is_empty for line in lines(result)))

    def test_actual_ncc_itself_preserves_polygon_vs_list_legacy_semantics(self):
        copper = box(0,0,10,10).difference(box(2,2,8,8))
        case = {"parameters":{"margin":0,"boundary":"itself"}}
        direct = ncc_boundary(case,copper,self.engine(copper),self.legacy_root(),{})
        listed = ncc_boundary(case,copper,self.engine([copper]),self.legacy_root(),{})
        self.assertEqual(100,direct.area)
        self.assertEqual(64,listed.area,"Do not flatten the source container and silently change the oracle")

    def test_actual_ncc_area_and_two_reference_kinds_use_explicit_shapes(self):
        copper = box(0,0,10,10)
        reference = box(-2,4,12,12)
        expected = {"area":reference.buffer(1,join_style=2),
                    "reference-geometry":reference.buffer(1,join_style=2),
                    "reference-gerber":copper.intersection(reference).buffer(1,join_style=2)}
        cache = {}
        for variant,target in expected.items():
            with self.subTest(variant=variant):
                case = {"parameters":{"margin":1,"boundary":variant,"referenceWkt":reference.wkt}}
                actual = ncc_boundary(case,copper,self.engine(copper),self.legacy_root(),cache)
                self.assertEqual(0,actual.symmetric_difference(target).area)

    def test_missing_original_methods_fail_loudly(self):
        with self.assertRaises(ValueError):
            compile_plugin_methods(self.legacy_root(),"ToolNCC.py","NonCopperClear",{"not_a_method"})

    def test_status_distinguishes_cam_gcode_boundary_and_partial_output_failures(self):
        for cam,gcode,boundary,failed,status in [
                (True,True,True,0,"MATCH_SAMPLED"),
                (False,True,True,0,"DIFFERENT"),
                (True,False,True,0,"GCODE_DIFFERENT"),
                (True,True,False,0,"DIFFERENT"),
                (True,True,True,1,"PARTIAL_DIFFERENCE")]:
            with self.subTest(status=status):
                result = dict(matchesSampledCriteria=cam, clearingAreaMatches=boundary,
                              pythonFailedPolygons=failed, pythonParsedFxCutComparison={"matchesSampledCriteria":gcode})
                self.assertEqual(status,classify_result(result))
                self.assertEqual(status == "MATCH_SAMPLED", result["matchesSampledCriteria"])

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
