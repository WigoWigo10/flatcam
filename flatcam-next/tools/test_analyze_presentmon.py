import unittest
from analyze_presentmon import analyze, distribution, check_capture_log


class PresentedFramesTest(unittest.TestCase):
    def test_loss_or_overflow_rejects_even_otherwise_usable_csv(self):
        for message in ("warning: 42 ETW events were lost.", "warning: 1 ETW buffers were lost.",
                        "warning: 8 overflowed present events detected."):
            with self.assertRaises(ValueError):
                check_capture_log(message)
        check_capture_log("warning: PresentMon requires elevated privilege to query other accounts")

    def report(self):
        return {"processId": 42, "qpcFrequency": 1_000_000, "qpcMinusNanoTimeNanos": 1_000_000_000,
                "qpcCalibrationUncertaintyNanos": 100,
                "phases": [{"name": "zoom-in-out", "repeat": 1, "startNanoTime": 0, "endNanoTime": 100_000_000}]}

    def row(self, micros, **changes):
        return {"ProcessID": "42", "CPUStartQPC": str(1_000_000 + micros), "SwapChainAddress": "main",
                "DisplayedTime": "10", "DisplayLatency": "5", **changes}

    def test_uses_display_timestamps_not_ui_pulses_or_cpu_present_intervals(self):
        result = analyze(self.report(), [self.row(0), self.row(10_000), self.row(20_000, DisplayLatency="15")])
        p = result["phases"][0]
        self.assertEqual("MEASURED", result["status"])
        self.assertAlmostEqual(1000 / 15, p["averageFps"])
        self.assertEqual(50, p["onePercentLowFps"])

    def test_dropped_foreign_pid_and_outside_phase_frames_do_not_create_fps(self):
        result = analyze(self.report(), [self.row(0), self.row(10_000, DisplayedTime="NA"),
                                       self.row(20_000, ProcessID="99"), self.row(200_000)])
        self.assertEqual("INCOMPLETE_CAPTURE", result["status"])
        self.assertIsNone(result["phases"][0]["averageFps"])

    def test_swapchains_are_not_added_together_and_duplicates_do_not_inflate_fps(self):
        rows = [self.row(0), self.row(0), self.row(20_000), self.row(0, SwapChainAddress="other")]
        result = analyze(self.report(), rows)
        self.assertEqual(2, len(result["swapChains"]))
        self.assertEqual(50, result["phases"][0]["averageFps"])

    def test_missing_clock_calibration_is_not_replaced_with_an_assumption(self):
        report = self.report(); report["qpcFrequency"] = 0
        with self.assertRaises(ValueError):
            analyze(report, [])

    def test_failed_benchmark_cannot_be_reported_as_successful_capture(self):
        report = self.report(); report["status"] = "FAILED"
        with self.assertRaises(ValueError):
            analyze(report, [self.row(0), self.row(20_000)])

    def test_invalid_display_values_are_excluded_and_empty_is_not_zero_fps(self):
        for value in ("NA", "NaN", "inf", "-1", "0"):
            self.assertEqual("INCOMPLETE_CAPTURE", analyze(self.report(), [self.row(0, DisplayedTime=value)])["status"])
        self.assertIsNone(distribution([])["averageFps"])

    def test_low_one_percent_uses_mean_of_slowest_tail_not_inverse_fps_average(self):
        result = distribution([10] * 198 + [50, 100])
        self.assertAlmostEqual(1000 / 75, result["onePercentLowFps"])
        self.assertEqual(100, result["worstFrameTimeMs"])


if __name__ == "__main__":
    unittest.main()
