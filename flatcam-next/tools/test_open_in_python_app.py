"""The diagnostic must not accept a partial program merely because an object exists."""
import logging
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

from open_in_python_app import probe_cnc_profile


class ProfileProbeTest(unittest.TestCase):
    def app(self, fail=False):
        logger = logging.getLogger("profile-probe-test")
        geometry = SimpleNamespace(options={})
        def generate(**kwargs):
            if fail:
                logger.error("Exception occurred within a preprocessor: missing end_xy")
        geometry.generatecncjob = generate
        app = SimpleNamespace(log=logger, app_obj=SimpleNamespace(new_object=lambda *a, **k: geometry),
                              collection=SimpleNamespace(get_by_name=lambda name: SimpleNamespace(source_file="G90\nG0 Z3\n")),
                              probeGeometry=geometry)
        return app

    def test_swallowed_preprocessor_errors_fail_even_with_nonempty_programs(self):
        with tempfile.TemporaryDirectory() as folder:
            app = self.app(fail=True)
            handlers = list(app.log.handlers)
            cases = probe_cnc_profile(app, Mock(), "line_xyz", Path(folder) / "programs", [])
            self.assertEqual(4, len(cases))
            self.assertTrue(all(c["status"] == "ERROR" and c["objectCreated"] and c["programBytes"] > 0 for c in cases))
            self.assertEqual(handlers, app.log.handlers)

    def test_generated_is_not_a_claim_of_machine_validation(self):
        with tempfile.TemporaryDirectory() as folder:
            cases = probe_cnc_profile(self.app(), Mock(), "default", Path(folder) / "programs", [])
            self.assertEqual(["GENERATED"] * 4, [c["status"] for c in cases])

    def test_existing_directory_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            marker = Path(folder) / "existing.nc"
            marker.write_text("original", encoding="utf-8")
            with self.assertRaises(FileExistsError):
                probe_cnc_profile(self.app(), Mock(), "line_xyz", Path(folder), [])
            self.assertEqual("original", marker.read_text(encoding="utf-8"))

    def test_native_icp_artifacts_are_not_labeled_as_conventional_gcode(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "programs"
            probe_cnc_profile(self.app(), Mock(), "ISEL_ICP_CNC", output, [])
            self.assertEqual(4, len(list(output.glob("*.imf"))))
            self.assertFalse(list(output.glob("*.nc")))

    def test_empty_xy_uses_the_real_blank_field_representation_not_none(self):
        with tempfile.TemporaryDirectory() as folder:
            app = self.app()
            app.probeGeometry.generatecncjob = Mock()
            probe_cnc_profile(app, Mock(), "Toolchange_Custom", Path(folder) / "programs", [])
            calls = app.probeGeometry.generatecncjob.call_args_list
            self.assertEqual("", calls[2].kwargs["toolchangexy"])
            self.assertEqual([11,17], calls[1].kwargs["toolchangexy"])


if __name__ == "__main__":
    unittest.main()
