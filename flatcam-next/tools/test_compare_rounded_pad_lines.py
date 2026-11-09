import unittest
from shapely.geometry import LineString, GeometryCollection
from compare_rounded_pad_lines import coverage_paths
from compare_cam_python import metrics


class RoundedPadComparisonTest(unittest.TestCase):
    def test_spatial_comparison_treats_repeated_paths_symmetrically_and_keeps_raw_data(self):
        # One overlapping pass is present twice in the raw FX collection; the
        # legacy helper has already returned its union. This is not a motion comparison.
        path = LineString([(0, 0), (2, 0)])
        raw = GeometryCollection([path, path])
        before = raw.wkb
        self.assertEqual(4, raw.length)
        self.assertTrue(metrics(coverage_paths(raw), coverage_paths(path), .001)["matchesSampledCriteria"])
        self.assertEqual(before, raw.wkb)
        self.assertEqual(4, raw.length)

    def test_connector_difference_remains_a_difference_after_spatial_union(self):
        fx = GeometryCollection([LineString([(0, 0), (2, 0)]), LineString([(0, 0), (0, 1)])])
        python = LineString([(0, 0), (2, 0)])
        self.assertFalse(metrics(coverage_paths(fx), coverage_paths(python), .001)["matchesSampledCriteria"])


if __name__ == "__main__":
    unittest.main()
