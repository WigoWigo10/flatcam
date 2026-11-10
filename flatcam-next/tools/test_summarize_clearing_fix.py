import unittest
from summarize_clearing_fix import summarize


def case(units="MM", core=1):
    return {"status": "MEASURED", "units": units, "rest": True,"targetArea": 100,"stableReachableArea": 99,"tools": [{"diameter": .4}],
            "geometry": {"reachableUncutArea": core + 2, "stableReachableUncutArea": core,
                         "stableCoreArea": core, "precisionSensitiveUncutArea": 2},
            "gcode": {"stableCoreArea": core}}


class ClearingReportGuards(unittest.TestCase):
    def test_shrinking_the_target_is_not_a_coverage_fix(self):
        after = case(core=0)
        after["targetArea"] = 50
        report = summarize({"x": case()}, {"x": after}, {}, expected=1, expected_cnc=0)
        self.assertTrue(any(f["reason"] == "target area changed" for f in report["failures"]))

    def test_changing_the_cutter_is_not_a_coverage_fix(self):
        after = case(core=0)
        after["tools"] = [{"diameter": .399}]
        report = summarize({"x": case()}, {"x": after}, {}, expected=1, expected_cnc=0)
        self.assertTrue(any(f["reason"] == "cutter diameters changed" for f in report["failures"]))
    def test_empty_matrices_are_not_complete(self):
        self.assertFalse(summarize({}, {}, {})["matrixComplete"])

    def test_missing_counterpart_is_not_a_successful_reduction(self):
        report = summarize({"x": case()}, {}, {}, expected=1, expected_cnc=0)
        self.assertTrue(report["failures"])

    def test_gcode_regression_is_detected_even_when_geometry_improves(self):
        after = case(core=0)
        after["gcode"]["stableCoreArea"] = 2
        report = summarize({"x": case()}, {"x": after}, {}, expected=1, expected_cnc=0)
        self.assertTrue(any(f["reason"] == "exported stable coverage core increased" for f in report["failures"]))

    def test_inch_areas_are_normalized_and_precision_sensitive_stock_is_retained(self):
        before, after = case("IN", 1 / 25.4 ** 2), case("IN", 0)
        report = summarize({"x": before}, {"x": after}, {}, expected=1, expected_cnc=0)
        self.assertAlmostEqual(1, report["coverageCases"][0]["beforeMM2"]["stableCore"])
        self.assertAlmostEqual(2 * 25.4 ** 2, report["coverageCases"][0]["afterMM2"]["precisionSensitiveUncut"])


if __name__ == "__main__":
    unittest.main()
