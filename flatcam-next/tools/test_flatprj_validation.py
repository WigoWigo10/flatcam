"""No Qt/dependencies needed: strict round-trip and save-validation regressions."""
import copy
import json
import tempfile
import unittest
import sys
from unittest.mock import patch
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO
from pathlib import Path
from types import SimpleNamespace as NS

import compare_flatprj_roundtrip as comparison
import open_in_python_app as live


def project(*names):
    return {"version": 8.994, "options": {"units": "MM"},
            "objs": [{"kind": "geometry", "options": {"name": name}, "tools": {}} for name in names]}


class StrictComparisonTest(unittest.TestCase):
    def test_metadata_ignored_recursively_but_extra_objects_fail(self):
        before = project("a")
        after = copy.deepcopy(before)
        after["_java"] = {"ignored": True}
        after["objs"][0]["_java"] = {"ignored": True}
        self.assertEqual(0, comparison.compare(before, after)["unexpectedDifferences"])
        after["objs"].append(project("extra")["objs"][0])
        self.assertGreater(comparison.compare(before, after)["unexpectedDifferences"], 0)
        self.assertEqual(0, comparison.compare(before, after, added=["extra"])["unexpectedDifferences"])

    def test_additions_at_root_options_and_nested_tool_data_fail(self):
        for change in (lambda p: p.update(extra=True),
                       lambda p: p["options"].update(extra=True),
                       lambda p: p["objs"][0]["tools"].update({"1": {"data": {"extra": True}}})):
            before = project("a")
            after = copy.deepcopy(before)
            change(after)
            self.assertGreater(comparison.compare(before, after)["unexpectedDifferences"], 0)

    def test_expected_path_does_not_accept_similar_prefix(self):
        self.assertTrue(comparison.expected_path("excellon.tools.1.data", ["excellon.tools.1"]))
        self.assertFalse(comparison.expected_path("excellon.tools.11.data", ["excellon.tools.1"]))

    def test_deletion_duplicate_names_order_and_false_expectations(self):
        before = project("a", "b")
        self.assertGreater(comparison.compare(before, project("a"))["unexpectedDifferences"], 0)
        self.assertEqual(0, comparison.compare(before, project("a"), removed=["b"])["unexpectedDifferences"])
        self.assertGreater(comparison.compare(before, project("a", "a", "b"))["unexpectedDifferences"], 0)
        self.assertFalse(comparison.compare(before, project("b", "a"))["orderKept"])
        self.assertGreater(comparison.compare(before, before, removed=["b"])["unexpectedDifferences"], 0)
        self.assertGreater(comparison.compare(before, before, added=["nonexistent"])["unexpectedDifferences"], 0)

    def test_strict_cli_returns_failure_for_unexpected_additions(self):
        for expected, code in (([], 1), (["--added", "extra"], 0)):
            args = ["compare", "before", "after", "--strict"] + expected
            with patch.object(sys, "argv", args), patch.object(comparison, "load", side_effect=[project("a"), project("a", "extra")]), redirect_stdout(StringIO()):
                self.assertEqual(code, comparison.main())


class LiveSaveValidationTest(unittest.TestCase):
    def application(self, save):
        return NS(f_handlers=NS(save_project=save),
                  collection=NS(get_list=lambda: [NS(kind="geometry", options={"name": "a"})]))

    def test_cli_rejects_source_overwrite_before_importing_qt(self):
        for extra in (("--resave", "source.FlatPrj", "--report", "report.json"),
                      ("--report", "source.FlatPrj"),
                      ("--resave", "copy.FlatPrj", "--report", "copy.FlatPrj")):
            with patch.object(sys, "argv", ["open", "--legacy-root", "..", "source.FlatPrj", *extra]), redirect_stderr(StringIO()):
                with self.assertRaises(SystemExit) as failure:
                    live.main()
                self.assertEqual(2, failure.exception.code)

    def test_existing_target_cannot_turn_a_failed_save_into_a_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "previous.FlatPrj"
            target.write_bytes(b"previous contents")
            messages = []
            fc = self.application(lambda *a, **k: messages.append("[ERROR_NOTCL] Save failed"))
            with self.assertRaises(RuntimeError):
                live.save_and_validate(fc, target, messages)
            self.assertEqual(b"previous contents", target.read_bytes())
            self.assertEqual([target], list(Path(folder).iterdir()))

    def test_no_output_partial_output_and_wrong_inventory_preserve_destination(self):
        for contents in (None, "{", json.dumps(project("wrong"))):
            with self.subTest(contents=contents), tempfile.TemporaryDirectory() as folder:
                target = Path(folder) / "previous.FlatPrj"
                target.write_bytes(b"previous contents")
                def save(path, **kwargs):
                    if contents is not None:
                        Path(path).write_text(contents, encoding="utf-8")
                with self.assertRaises((ValueError, RuntimeError)):
                    live.save_and_validate(self.application(save), target, [])
                self.assertEqual(b"previous contents", target.read_bytes())
                self.assertEqual([target], list(Path(folder).iterdir()))

    def test_valid_save_is_parsed_and_published_with_no_stage_left(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "previous.FlatPrj"
            target.write_bytes(b"previous contents")
            fc = self.application(lambda path, **kw: Path(path).write_text(json.dumps(project("a")), encoding="utf-8"))
            self.assertEqual(target.name, live.save_and_validate(fc, target, []))
            self.assertEqual(project("a"), live.read_project(target))
            self.assertEqual([target], list(Path(folder).iterdir()))


if __name__ == "__main__":
    unittest.main()
