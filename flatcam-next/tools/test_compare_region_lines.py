import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from compare_region_lines import main


class RegionComparisonTest(unittest.TestCase):
    def compare(self, regional, connect=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ("old", "new"):
                (root / name).mkdir()
            case = {"id": "board-paint-c0-e1", "status": "GENERATED", "connect": connect,
                    "operation": "paint", "board": "board", "copperWkt": "POLYGON ((0 0,4 0,4 4,0 4,0 0))",
                    "rawPathsWkt": "MULTILINESTRING ((0 0,1 0),(1 1,0 1))"}
            filename = "board-paint-c1-e1.json" if connect else "board-paint-c0-e1.json"
            for f in {filename, "board-paint-c0-e1.json"}:
                (root / "old" / f).write_text(json.dumps(case), encoding="utf-8")
            (root / "new" / filename).write_text(json.dumps(dict(case, rawPathsWkt=regional)), encoding="utf-8")
            with patch.object(sys, "argv", ["compare", str(root / "old"), str(root / "new"), str(root / "out"), "--expected-cases", "1"]), contextlib.redirect_stdout(io.StringIO()):
                status = main()
            return status, json.loads((root / "out/comparison.json").read_text())["cases"][0]

    def test_reversal_and_safe_bridge_preserve_source_edges(self):
        status, row = self.compare("LINESTRING (0 1,1 1,1 0,0 0)", connect=True)
        self.assertFalse(status)
        self.assertEqual(0, row["missingEdges"])
        self.assertEqual(1, row["addedEdges"])

    def test_dropped_pass_is_not_accepted_as_shorter_travel(self):
        status, row = self.compare("LINESTRING (0 0,1 0)")
        self.assertTrue(status)
        self.assertEqual(1, row["missingEdges"])

    def test_duplicate_pass_is_rejected_without_connect(self):
        status, row = self.compare("MULTILINESTRING ((0 0,1 0),(1 1,0 1),(0 0,1 0))")
        self.assertTrue(status)
        self.assertEqual(1, row["addedEdges"])


if __name__ == "__main__":
    unittest.main()
