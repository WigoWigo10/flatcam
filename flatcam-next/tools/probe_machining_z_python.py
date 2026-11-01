"""Probe real legacy Geometry Z policies without opening the Python UI or writing preferences.

Run from the worktree with the Python environment that can import camlib:
  python flatcam-next/tools/probe_machining_z_python.py
Output: flatcam-next/target/machining_z_python_probe.json
This intentionally covers one GRBL tool, one pass and MM, not all legacy generators.
"""
import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(root), str(root / "tests"), str(root / "flatcam-next/tools")]
import camlib
from parser_baseline import install_stub_app
from compare_cnc_python import python_program
from preprocessors.default import default
from preprocessors.GRBL_11_no_M6 import GRBL_11_no_M6


def main():
    app = install_stub_app()
    app.preprocessors = {"default": default(), "GRBL_11_no_M6": GRBL_11_no_M6()}
    records = []
    for mode in (0, 1):
        camlib.CNCjob.machinist_setting = mode  # Process-local; no QSettings writes.
        for cut in (-0.2, 0.2, 0):
            for travel in (3, -3):
                messages = []
                app.inform = type("Signal", (), {
                    "emit": lambda self, *args: messages.append(" ".join(map(str, args)))
                })()
                parameters = dict(depth=-cut, safeZ=travel, multiDepth=False, depthPerPass=0.1,
                                  feed=100, feedZ=50, rpm=0, dwell=False, dwellSeconds=0, offsetZ=0)
                case = dict(operation="geometry", profile="GRBL_11_no_M6", units="MM",
                            tools=[dict(id=1, diameter=1, wkt="LINESTRING (1 0,2 0)", parameters=parameters)],
                            job=dict(rapidFeed=1200, exclusions=[], startZ=None, endZ=3, endX=None,
                                     endY=None, toolChange=False, toolChangeZ=15, changeX=None, changeY=None))
                row = dict(machinist=bool(mode), cutZ=cut, travelZ=travel)
                try:
                    code = python_program(case, root, app, camlib)
                    row.update(status="GENERATED", zFeeds=[float(value) for value in
                               re.findall(r"G0?1\s+Z([+-]?[0-9.]+)", code)], warnings=messages)
                except Exception as error:
                    row.update(status="REJECTED", error=str(error), warnings=messages)
                records.append(row)
    output = root / "flatcam-next/target/machining_z_python_probe.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(records, indent=2), encoding="utf-8")
    for row in records:
        if not row["machinist"] and row["cutZ"] == 0:
            assert row["status"] == "REJECTED", row
        else:
            target = row["cutZ"] if row["machinist"] else -abs(row["cutZ"])
            assert row["status"] == "GENERATED" and row["zFeeds"] == [target], row
    print(json.dumps({"cases": len(records), "status": "PASS", "report": str(output)}))


if __name__ == "__main__":
    main()
