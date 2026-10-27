#!/usr/bin/env python3
"""Compare FX public implicit-decimal controls with the ORIGINAL Python parse_number method; no Qt/settings."""
import argparse
import ast
import csv
import hashlib
import json
import logging
import re
from pathlib import Path
from types import SimpleNamespace


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--legacy-root", type=Path, default=Path(".."))
    p.add_argument("--csv", type=Path, default=Path("flatcam-cam/target/excellon-zero-comparison.csv"))
    p.add_argument("--report", type=Path, default=Path("target/excellon-import-python.json"))
    args = p.parse_args()
    source = args.legacy_root / "appParsers/ParseExcellon.py"
    module = ast.parse(source.read_text(encoding="utf-8-sig"))
    cls = next(n for n in module.body if isinstance(n, ast.ClassDef) and n.name == "Excellon")
    fn = next(n for n in cls.body if isinstance(n, ast.FunctionDef) and n.name == "parse_number")
    ns = dict(log=logging.getLogger("legacy"))
    exec(compile(ast.Module(body=[fn], type_ignores=[]), str(source), "exec"), ns)
    count, maximum, failures = 0, 0.0, []
    with args.csv.open(newline="") as f:
        for row in csv.DictReader(f):
            obj = SimpleNamespace(units=row["units"], zeros=row["zeros"],
                excellon_format_upper_in=int(row["upper"]), excellon_format_upper_mm=int(row["upper"]),
                excellon_format_lower_in=int(row["lower"]), excellon_format_lower_mm=int(row["lower"]),
                leadingzeros_re=re.compile(r"^[-\+]?(0*)(\d*)"))
            expected = ns["parse_number"](obj, row["token"])
            actual = float(row["value"])
            delta = abs(expected-actual)
            maximum = max(maximum, delta); count += 1
            if delta > 1e-12*max(1, abs(expected)): failures.append(dict(row=row, expected=expected))
    report = dict(controls=count, failures=failures, maxDifference=maximum,
                  sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  scope="Original implicit-number decoding; Java reader/header precedence tested separately, not live GUI")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
