"""Probe/report regressions; these stand-ins do not constitute a Python CAM comparison."""
import unittest
from types import SimpleNamespace as NS
from shapely.geometry import MultiPolygon, box
from open_in_python_app import probe_ncc_offset_loading


class NccLoadingProbeTest(unittest.TestCase):
    def object(self, name):
        return NS(kind="gerber", options={"name": name}, units="MM",
                  solid_geometry=[MultiPolygon([box(1, 1, 2, 2)])], bounds=lambda: (0, 0, 4, 4))

    def test_representation_is_not_repaired_and_both_offset_controls_reach_the_handler(self):
        obj = self.object("public_F_Cu")
        calls = []
        def handler(**kwargs):
            calls.append(kwargs)
            self.assertIs(kwargs["ncc_obj"], obj)
            self.assertIs(kwargs["ncc_obj"].solid_geometry, obj.solid_geometry)
            return box(0, 0, 4, 4), 0
        fc = NS(defaults={"gerber_buffering": "full"}, ncclear_tool=NS(get_tool_empty_area=handler))
        result = probe_ncc_offset_loading(fc, [obj])
        self.assertEqual([False, True], [c["has_offset"] for c in calls])
        self.assertEqual(["PREPARED"] * 2, [c["status"] for c in result["cases"]])
        self.assertEqual({"type": "list", "count": 1, "memberTypes": {"MultiPolygon": 1}},
                         result["cases"][0]["representation"])

    def test_offset_failure_is_kept_as_error_without_skipping_other_faces(self):
        def handler(**kwargs):
            if kwargs["has_offset"]:
                raise ValueError("original handler failure")
            return box(0, 0, 4, 4), 0
        fc = NS(defaults={"gerber_buffering": "full"}, ncclear_tool=NS(get_tool_empty_area=handler))
        cases = probe_ncc_offset_loading(fc, [self.object("public_F_Cu"), self.object("public_B_Cu")])["cases"]
        self.assertEqual(["PREPARED", "ORACLE_ERROR"] * 2, [c["status"] for c in cases])
        self.assertIn("ValueError: original handler failure", cases[1]["error"])
        self.assertIn("handler", cases[1]["trace"])

    def test_missing_copper_faces_cannot_produce_an_empty_success(self):
        with self.assertRaisesRegex(RuntimeError, "No F_Cu/B_Cu"):
            probe_ncc_offset_loading(NS(), [NS(kind="geometry")])

    def test_cli_cannot_save_a_project_during_a_loading_probe(self):
        from unittest.mock import patch
        from contextlib import redirect_stderr
        from io import StringIO
        import open_in_python_app
        args = ["probe", "input.FlatPrj", "--probe-ncc-offset", "--resave", "copy.FlatPrj", "--report", "report.json"]
        with patch("sys.argv", args), redirect_stderr(StringIO()), self.assertRaises(SystemExit) as failure:
            open_in_python_app.main()
        self.assertEqual(2, failure.exception.code)


if __name__ == "__main__":
    unittest.main()
