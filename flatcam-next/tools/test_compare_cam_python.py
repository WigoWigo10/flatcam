"""Tests for report metrics/visualization; not substitutes for the actual legacy comparison."""
import unittest
from pathlib import Path
from types import SimpleNamespace

from shapely.geometry import GeometryCollection, LineString, MultiLineString, Polygon, box
from compare_cam_python import (classify_result, compile_rectangular_handler, compile_plugin_methods, dependency_metadata,
                               compile_ncc_gui, compare_tool_outputs,
                               isolation_exception_paths, ncc_boundary, leaves, lines, metrics, overlay,
                               sampled_distance, sampled_witness, gcode_metrics)
from compare_cam_python import panelize_legacy_source, public_panelized_source


class ComparisonReportTest(unittest.TestCase):
    def test_public_panel_oracle_uses_original_inputs_not_the_fx_collection_or_clearing_area(self):
        from shapely.ops import unary_union
        from shapely.geometry import Point
        copper=box(10,20,16,28).difference(Point(12,23).buffer(.5))
        export={"panelization":dict(columns=2,rows=2,spacingX=3,spacingY=5),
                "publicPanelizationInputs":{"sourceWkt":copper.wkt,"referenceWkt":box(9,19,17,29).wkt},
                # Deliberately unrelated: this field must NOT generate the oracle source.
                "sourceWkt":box(-10,-10,-9,-9).wkt,"cases":[]}
        result=public_panelized_source(export,self.legacy_root())
        self.assertIsInstance(result,list)
        self.assertEqual(4,len(result))
        self.assertEqual((10,20,27,43),unary_union(result).bounds)
        self.assertAlmostEqual(4*copper.area,unary_union(result).area)
        for dx,dy in ((0,0),(11,0),(0,15),(11,15)):
            self.assertFalse(unary_union(result).covers(Point(12+dx,23+dy)))

    def test_public_panel_oracle_rejects_inputs_without_layout(self):
        with self.assertRaisesRegex(ValueError,"explicit layout"):
            public_panelized_source({"publicPanelizationInputs":{}},self.legacy_root())

    def test_original_gui_offset_rejects_a_list_containing_multipart_copper(self):
        import shapely
        from shapely.geometry import MultiPolygon
        if shapely.__version__ != "1.8.5.post1":
            self.skipTest("Reproduction targets the compatible unmodified legacy reference")
        namespace = compile_ncc_gui(self.legacy_root())
        source = SimpleNamespace(kind="gerber",solid_geometry=[MultiPolygon([box(0,0,2,2),box(4,0,6,2)])])
        binding = SimpleNamespace(app=SimpleNamespace(defaults={"gerber_buffering":"full"},
                inform=SimpleNamespace(emit=lambda *args:None)))
        with self.assertRaisesRegex(ValueError,"Sequences of multi-polygons are not valid arguments"):
            namespace["get_tool_empty_area"](binding,name="public",ncc_obj=source,geo_obj=None,
                    isotooldia=None,has_offset=True,ncc_offset=.1,ncc_margin=1,
                    bounding_box=box(-1,-1,7,3),tools_storage={})

    def test_export_length_preserves_near_coincident_passes_after_xy_rounding(self):
        for scale in (1, 1 / 25.4):
            with self.subTest(scale=scale):
                original = MultiLineString([[(0,0),(30*scale,0)],
                                           [(0,1e-9*scale),(30*scale,1e-9*scale)]])
                rounded = MultiLineString([[(0,0),(30*scale,0)],[(0,0),(30*scale,0)]])
                report = gcode_metrics(original,rounded,.003*scale)
                self.assertTrue(report["matchesSampledCriteria"])
                self.assertAlmostEqual(60*scale,report["pythonParsedTravelLength"])
                self.assertAlmostEqual(.5,report["relativeLengthDelta"])
                self.assertEqual(0,report["travelRelativeLengthDelta"])

    def test_export_fidelity_rejects_missing_repeated_added_and_shifted_passes(self):
        lower = LineString([(0,0),(30,0)])
        upper = LineString([(0,1e-9),(30,1e-9)])
        source = MultiLineString([lower,upper])
        for parsed in (lower, MultiLineString([lower,lower,lower]),
                       MultiLineString([[(0,.1),(30,.1)],[(0,.1),(30,.1)]]),
                       MultiLineString([[(0,0),(29,0)],[(0,0),(29,0)]])):
            with self.subTest(parsed=parsed.wkt):
                self.assertFalse(gcode_metrics(source,parsed,.003)["matchesSampledCriteria"])

    def test_original_panel_initializer_preserves_holes_positions_and_list_container(self):
        from shapely.geometry import Point
        from shapely.ops import unary_union
        copper = box(110,205,155,235).difference(Point(115,210).buffer(1))
        original = copper.wkt
        reference = box(100,200,160,240)
        for source in (copper, [copper]):
            result = panelize_legacy_source(self.legacy_root(),source,reference,
                dict(columns=2,rows=2,spacingX=5,spacingY=3))
            self.assertIsInstance(result,list)
            self.assertEqual(4,len(result))
            self.assertAlmostEqual(4*copper.area,unary_union(result).area)
            self.assertEqual((110,205,220,278),unary_union(result).bounds)
            for dx,dy in ((0,0),(65,0),(0,43),(65,43)):
                self.assertFalse(unary_union(result).covers(Point(115+dx,210+dy)))
        self.assertEqual(original,copper.wkt)

    def test_original_panel_initializer_rejects_invalid_or_unbounded_layout(self):
        for overrides in (dict(columns=0),dict(rows=1.5),dict(columns=10001),dict(spacingX=float('nan')),dict(spacingY=-1)):
            layout=dict(columns=2,rows=2,spacingX=5,spacingY=3); layout.update(overrides)
            with self.assertRaises(ValueError):
                panelize_legacy_source(self.legacy_root(),box(0,0,10,10),box(0,0,10,10),layout)

    def test_original_freeform_handler_keeps_four_bridges_per_translated_board(self):
        import shapely
        if int(shapely.__version__.split('.')[0]) >= 2:
            self.skipTest("Original GUI multipart handler requires the compatible Shapely 1.8 reference; do not patch it")
        from shapely.ops import linemerge
        namespace = compile_rectangular_handler(self.legacy_root(),freeform=True)
        namespace.update(margin=0,gaps='4')
        cuts=[]
        for dx,dy in ((0,0),(25,0),(0,21),(25,21)):
            ring=box(10+dx,20+dy,30+dx,36+dy).buffer(.4).exterior
            cut,rest=namespace['cutout_handler'](ring,1.4)
            self.assertEqual(4,len(lines(linemerge(cut))))
            self.assertEqual(4,len(lines(rest)))
            cuts.extend(lines(cut))
        self.assertEqual(16,len(lines(linemerge(cuts))))

    def test_per_tool_comparison_rejects_swapped_assignment_even_when_union_matches(self):
        lower = LineString([(0,0),(10,0)])
        upper = LineString([(0,2),(10,2)])
        tools = [{"diameter":1,"fxWkt":lower.wkt,"gcode":"lower"},
                 {"diameter":.5,"fxWkt":upper.wkt,"gcode":"upper"}]
        parse = lambda code: {"lower":lower,"upper":upper}[code]
        report = compare_tool_outputs(tools,{1:upper,.5:lower},[1,.5],.003,parse)
        self.assertFalse(report["toolPathsMatch"])
        self.assertTrue(report["toolGcodeMatches"])
        self.assertTrue(report["toolOrderMatches"])

    def test_per_tool_comparison_preserves_empty_tools_and_rejects_extra_output_or_bad_order(self):
        path = LineString([(0,0),(10,0)])
        tools = [{"diameter":1,"fxWkt":GeometryCollection().wkt,"gcode":""},
                 {"diameter":.5,"fxWkt":path.wkt,"gcode":"cut"}]
        report = compare_tool_outputs(tools,{.5:path},[.5],.003,lambda _:path)
        self.assertTrue(report["toolPathsMatch"])
        self.assertTrue(report["tools"][0]["cam"]["fxEmpty"])
        report = compare_tool_outputs(tools,{.5:path,.25:path},[.25,.5],.003,lambda _:path)
        self.assertFalse(report["toolPathsMatch"])
        self.assertFalse(report["toolOrderMatches"])
        self.assertEqual([.25],report["extraPythonTools"])

    def test_original_gui_initializers_are_selected_instead_of_tcl_namesakes(self):
        import ast
        root = self.legacy_root()
        tree = ast.parse((root / "appTools/ToolNCC.py").read_text(encoding="utf-8-sig"))
        cls = next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name == "NonCopperClear")
        outer = next(n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name == "clear_copper")
        expected = {n.name:n.lineno for n in outer.body if isinstance(n,ast.FunctionDef)}
        namespace = compile_ncc_gui(root)
        for name in ("gen_clear_area","gen_clear_area_rest"):
            self.assertEqual(expected[name],namespace[name].__code__.co_firstlineno)

    def test_tool_mismatch_fails_cam_and_bad_order_is_partial(self):
        for extra,status in [(dict(toolPathsMatch=False),"DIFFERENT"),
                             (dict(toolGcodeMatches=False),"GCODE_DIFFERENT"),
                             (dict(toolOrderMatches=False),"PARTIAL_DIFFERENCE")]:
            result = dict(matchesSampledCriteria=True,
                          pythonParsedFxCutComparison={"matchesSampledCriteria":True},**extra)
            self.assertEqual(status,classify_result(result))
            self.assertFalse(result["matchesSampledCriteria"])

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
