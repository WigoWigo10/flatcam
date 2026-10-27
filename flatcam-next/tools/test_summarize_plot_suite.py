import json
import unittest
from unittest.mock import patch
from summarize_plot_suite import collect


class PlotSuiteSummaryTest(unittest.TestCase):
    def test_failed_run_never_uses_partial_or_stale_reports(self):
        manifest = {"Results": [{"Name": "failed", "Status": "FAILED", "Report": "private/report.json", "Error": "missing ETW"}]}
        with patch("pathlib.Path.read_text", side_effect=AssertionError("must not read rejected evidence")):
            rows, failures = collect(manifest)
        self.assertEqual([], rows)
        self.assertEqual("failed", failures[0]["name"])

    def test_incomplete_capture_does_not_become_a_successful_summary(self):
        manifest = {"Results": [{"Name": "full", "Status": "COMPLETED", "Report": "report.json"}]}
        with patch("pathlib.Path.read_text", side_effect=[json.dumps({"status": "COMPLETED", "sourceUnchanged": True}),
                                                         json.dumps({"status": "INCOMPLETE_CAPTURE"})]):
            rows, failures = collect(manifest)
        self.assertEqual([], rows)
        self.assertEqual(1, len(failures))

    def test_presented_fps_and_ui_hz_remain_distinct_and_repeats_are_not_averaged(self):
        phase = {"name": "pan", "repeat": 2, "rasterBefore": {"drawOmissionsCumulative": 5},
                 "rasterAfter": {"drawOmissionsCumulative": 9}, "uiPulseCadence": {"averageHz": 60},
                 "uiQueueMs": {"p95": 12}, "requestToReadyCommandsMs": {"p95": None},
                 "cncPasses": {"PASS_LINES": {"commandEmissionMs": 2}}}
        report = {"status": "COMPLETED", "sourceUnchanged": True, "visualValidation": "NOT_VALIDATED_DIAGNOSTIC_OMISSIONS",
                  "graphics": {"adapter": "GPU"}, "phases": [phase]}
        fps = {"status": "MEASURED", "phases": [{"name": "pan", "repeat": 2, "status": "MEASURED", "averageFps": 30}]}
        manifest = {"Results": [{"Name": "omit", "Status": "COMPLETED", "Report": "report.json"}]}
        with patch("pathlib.Path.read_text", side_effect=[json.dumps(report), json.dumps(fps)]):
            rows, failures = collect(manifest)
        self.assertEqual([], failures)
        self.assertEqual(30, rows[0]["averageFps"])
        self.assertEqual(60, rows[0]["uiPulseHz"])
        self.assertEqual(2, rows[0]["repeat"])
        self.assertEqual(4, rows[0]["imageDrawOmissions"])


if __name__ == "__main__":
    unittest.main()
