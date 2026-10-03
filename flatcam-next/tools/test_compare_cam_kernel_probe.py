"""Probe reporting controls; these do not establish cross-engine equivalence."""
import unittest

from shapely.geometry import GeometryCollection, Polygon, box
from shapely.wkt import dumps, loads
from compare_cam_kernel_probe import compare, geometry_difference


class KernelProbeTest(unittest.TestCase):
    def test_empty_mismatch_does_not_report_zero_distance(self):
        result = geometry_difference(GeometryCollection(), box(0, 0, 2, 2))
        self.assertTrue(result["pythonEmpty"])
        self.assertFalse(result["fxEmpty"])
        self.assertEqual(4, result["areaDelta"])
        self.assertIsNone(result["boundaryHausdorff"])

    def test_unsupported_schema_is_refused(self):
        with self.assertRaisesRegex(ValueError, "schema"):
            compare({"schema": 2})

    def test_invalid_step_is_refused(self):
        for value in (0, -1, float("inf"), float("nan")):
            with self.subTest(step=value), self.assertRaisesRegex(ValueError, "positive"):
                compare({"schema": 1, "cases": [{"id": "synthetic-standard", "diameter": .5, "step": value}]})

    def test_private_fixture_ids_are_not_silently_accepted(self):
        with self.assertRaisesRegex(ValueError, "public synthetic"):
            compare({"schema": 1, "cases": [{"id": "ncc-standard", "diameter": .5, "step": .3}]})

    def test_identical_geometry_control(self):
        area = box(0, 0, 2, 2)
        result = geometry_difference(area, area)
        self.assertEqual(0, result["boundaryHausdorff"])
        self.assertEqual(0, result["areaDelta"])

    def test_scanline_reports_geometry_and_seed_differences_separately(self):
        reference = Polygon([(0, 0), (2, 2), (0, 4), (-2, 2)])
        perturbed = Polygon([(0, 0), (2, 2 + 1e-12), (0, 4), (-2, 2 + 1e-12)])
        document = {"schema": 1, "java": "test", "jts": "test", "cases": [{
            "id": "synthetic-scanline", "reference": {"wkt": reference.wkt, "seed": [0, 3]},
            "perturbed": {"wkt": perturbed.wkt, "seed": [0, 1 + 5e-13]}}]}
        result = compare(document)["cases"][0]
        self.assertEqual(reference.representative_point().distance(perturbed.representative_point()),
                         result["pythonSeedDelta"])
        self.assertEqual(0, result["sameReferenceSeedDelta"])
        self.assertLess(result["boundaryHausdorff"], 1e-11)
        self.assertGreater(result["javaSeedDelta"], 1)

    def test_standard_traces_all_vertices_and_pass_index(self):
        source = box(0, 0, 4, 4)
        inset = source.buffer(-.5 / 1.999999, resolution=64)
        document = {"schema": 1, "java": "test", "jts": "test", "cases": [{
            "id": "synthetic-standard", "diameter": .5, "step": .3, "parts": [{
                "index": 0, "source": {"wkt": source.wkt}, "passes": [{"wkt": dumps(inset, rounding_precision=-1), "points": 5}]}]}]}
        result = compare(document)["cases"][0]["parts"][0]["passes"][0]
        # The GEOS WKT round-trip can change the final bit: compare the serialized fixture.
        serialized = loads(document["cases"][0]["parts"][0]["passes"][0]["wkt"])
        self.assertEqual(inset.boundary.hausdorff_distance(serialized.boundary), result["boundaryHausdorff"])
        self.assertEqual(0, result["passIndex"])
        self.assertEqual(5, result["pythonPoints"])

    def test_seed_keeps_same_geometry_control(self):
        source = box(0, 0, 4, 4)
        safe = source.buffer(-.25, resolution=64)
        document = {"schema": 1, "java": "test", "jts": "test", "cases": [{
            "id": "synthetic-seed", "diameter": .5, "step": .3, "parts": [{
                "index": 0, "source": {"wkt": source.wkt}, "safeArea": {
                    "wkt": safe.wkt, "seed": [2, 2], "reversedSeed": [2, 2]}}]}]}
        result = compare(document)["cases"][0]["parts"][0]
        self.assertEqual(0, result["seedDelta"])
        self.assertEqual(0, result["sameSafeSeedDelta"])


if __name__ == "__main__":
    unittest.main()
