import unittest
from pathlib import Path
from types import SimpleNamespace
from shapely.geometry import box, LineString, Polygon
from investigate_ncc_preparation import compare, geometry_delta, connect_same_area_control, ring_representation


class PreparationInvestigationTest(unittest.TestCase):
    def test_equal_area_does_not_hide_rotated_ring_start(self):
        original = Polygon([(0,0),(10,0),(10,10),(0,10),(0,0)])
        rotated = Polygon([(10,10),(0,10),(0,0),(10,0),(10,10)])
        self.assertTrue(original.equals(rotated))
        report = ring_representation(rotated,original)
        self.assertEqual(1,report["pairedRings"])
        self.assertEqual(1,report["differentStartCount"])
        self.assertEqual(0,report["differentVertexCount"])
        self.assertEqual(0,report["differentOrientationCount"])

    def test_same_area_connect_control_is_explicitly_diagnostic(self):
        path = LineString([(1,1),(9,1)])
        area = box(0,0,10,10)
        cases = [{"id":name,"diameter":.5,"fxWkt":path.wkt,"parameters":{
            "fxClearingAreaWkt":area.wkt,"overlap":.4,"contour":True}}
            for name in ("ncc-standard","ncc-connect")]
        calls = []
        def clear(part, diameter, resolution, **options):
            calls.append(options["connect"])
            self.assertTrue(part.equals(area))
            return SimpleNamespace(get_objects=lambda:[path])
        report = connect_same_area_control({"units":"MM","cases":cases},SimpleNamespace(clear_polygon=clear))
        self.assertEqual([False,True],calls)
        self.assertIn("not an independent parity",report["scope"])
        self.assertTrue(report["plain"]["matchesSampledCriteria"])
        self.assertTrue(report["connected"]["matchesSampledCriteria"])
        cases[0]["parameters"]["fxClearingAreaWkt"] = box(0,0,5,5).wkt
        with self.assertRaisesRegex(ValueError,"same area"):
            connect_same_area_control({"units":"MM","cases":cases},SimpleNamespace(clear_polygon=clear))

    def test_equal_geometry_reports_zero_distance_without_sampling(self):
        report = geometry_delta(box(0,0,10,10),box(0,0,10,10))
        self.assertTrue(report["equalsTopologically"])
        self.assertEqual(0,report["hausdorffDistance"])
        self.assertEqual(0,report["symmetricDifferenceArea"])

    def test_original_container_semantics_are_not_flattened_by_diagnostics(self):
        root = Path(__file__).resolve().parents[2]
        if not (root / "appTools/ToolNCC.py").exists():
            self.skipTest("Legacy checkout unavailable")
        source = box(0,0,10,10).difference(box(2,2,8,8))
        hull = source.convex_hull
        boundary = hull.buffer(1,join_style=2)
        empty = boundary.difference(source)
        stages = {"source":source.wkt,"cleanCopper":source.wkt,"rawHull":hull.wkt,"cleanHull":hull.wkt}
        stages.update({name:boundary.wkt for name in ("jtsBoundary","geosBoundary","rawJtsBoundary","rawGeosBoundary")})
        stages.update({name:empty.wkt for name in ("exportedClearingArea","jtsClearingArea","geosClearingArea")})
        trace = {"units":"MM","margin":1,"stages":stages}
        app = SimpleNamespace(abort_flag=False,inform=SimpleNamespace(emit=lambda *args:None))
        direct = compare(trace,source,root,app)
        listed = compare(trace,[source],root,app)
        self.assertTrue(direct["comparisons"]["rawHull"]["equalsTopologically"])
        self.assertFalse(listed["comparisons"]["rawHull"]["equalsTopologically"])
        self.assertEqual("list",listed["legacyContainer"])
        self.assertEqual(36,listed["comparisons"]["rawHull"]["symmetricDifferenceArea"])
        self.assertEqual(0,listed["comparisons"]["source"]["symmetricDifferenceArea"])


if __name__ == "__main__":
    unittest.main()
