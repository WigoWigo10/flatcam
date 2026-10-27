#!/usr/bin/env python3
"""Compare public reader controls with the ORIGINAL legacy parser, without starting the GUI.

Differences/unsupported curves are reported, never counted as parity. Uses the
existing parser_baseline App stub only for factory defaults/signals; no parsing
logic is replaced and no project or preference files are opened or saved.
"""
import argparse
import csv
import hashlib
import json
import logging
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, default=Path(".."))
    parser.add_argument("--controls", type=Path, default=Path("flatcam-cam/target/excellon-routing-controls"))
    parser.add_argument("--report", type=Path, default=Path("target/excellon-routing-python.json"))
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    root = args.legacy_root.resolve()
    sys.path.insert(0, str(root))
    sys.path.insert(0, str(root / "tests"))
    from parser_baseline import install_stub_app
    from appParsers.ParseExcellon import Excellon
    logging.getLogger("base").setLevel(logging.ERROR)
    app = install_stub_app()
    app.defaults["units"] = "MM"
    rows = []
    with (args.controls / "fx.csv").open(newline="", encoding="utf-8") as stream:
        for fx in csv.DictReader(stream):
            obj = Excellon()
            obj.default_data = {}
            case = dict(name=fx["name"], fxDrills=[[float(value) for value in p.split(":")] for p in fx["drills"].split("|") if p],
                        fxSlots=int(fx["slots"]), fxCurvedSlots=int(fx["curved"]))
            try:
                result = obj.parse_lines((args.controls / (fx["name"] + ".drl")).read_text(encoding="ascii").splitlines())
                drills = [[point.x, point.y] for tool in obj.tools.values() for point in tool.get("drills", [])]
                slots = [[[a.x, a.y], [b.x, b.y]] for tool in obj.tools.values() for a, b in tool.get("slots", [])]
                case.update(pythonDrills=drills, pythonSlots=slots, parserReturn=result)
                if result == "fail":
                    case["status"] = "ORACLE_ERROR"
                elif case["fxCurvedSlots"]:
                    case["status"] = "LEGACY_CURVE_UNSUPPORTED"
                elif len(drills) == len(case["fxDrills"]) and len(slots) == case["fxSlots"] and all(
                        abs(a - b) <= 1e-12 for pa, pb in zip(drills, case["fxDrills"]) for a, b in zip(pa, pb)):
                    case["status"] = "MATCH_LINEAR"
                else:
                    case["status"] = "DIFFERENT"
            except Exception as error:
                case.update(status="ORACLE_ERROR", error=f"{type(error).__name__}: {error}")
            rows.append(case)
    counts = {status: sum(row["status"] == status for row in rows) for status in sorted({row["status"] for row in rows})}
    report = dict(counts=counts, cases=rows,
                  sources={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in
                           [root / "appParsers/ParseExcellon.py", root / "camlib.py", root / "tests/parser_baseline.py"]},
                  scope="Original reader with factory App stub; not a Qt session or a machine/controller test. Curves are not certified by legacy endpoint-only data.")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(dict(counts=counts, report=str(args.report))))
    return bool(args.strict and any(row["status"] != "MATCH_LINEAR" for row in rows))


if __name__ == "__main__":
    raise SystemExit(main())
