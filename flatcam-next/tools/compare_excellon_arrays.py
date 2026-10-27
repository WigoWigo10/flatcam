#!/usr/bin/env python3
"""Compare public FX array coordinates to ORIGINAL editor distribution methods, without starting Qt.

Only util_shape is a simple point/slot-centreline template: this validates placement/orientation,
not Python's polygon tessellation or its GUI/points_edit persistence. Slot endpoint order is irrelevant.
"""
import argparse
import ast
import csv
import hashlib
import json
import logging
import math
from pathlib import Path
from types import SimpleNamespace
from shapely import affinity
from shapely.geometry import Point, LineString


class Value:
    def __init__(self, value): self.value = value
    def get_value(self): return self.value


class Shape:
    def __init__(self, geo): self.geo = geo


def method(tree, cls, name):
    node = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == cls)
    fn = next(n for n in node.body if isinstance(n, ast.FunctionDef) and n.name == name)
    ns = dict(math=math, affinity=affinity, DrawToolShape=Shape, DrawToolUtilityShape=Shape,
              log=logging.getLogger("legacy"), _=lambda text: text)
    exec(compile(ast.Module(body=[fn], type_ignores=[]), "<original-editor-method>", "exec"), ns)
    return ns[name]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--legacy-root", type=Path, default=Path(".."))
    p.add_argument("--csv", type=Path, default=Path("flatcam-cam/target/excellon-array-comparison.csv"))
    p.add_argument("--report", type=Path, default=Path("target/excellon-array-python.json"))
    args = p.parse_args()
    source = args.legacy_root / "appEditors/appExcEditor.py"
    tree = ast.parse(source.read_text(encoding="utf-8-sig"))
    groups = {}
    with args.csv.open(newline="") as f:
        for row in csv.DictReader(f):
            key = tuple(row[k] for k in ("kind", "slots", "units", "clockwise", "angle"))
            groups.setdefault(key, []).append(row)
    controls, maximum, failures = 0, 0.0, []
    for key, rows in groups.items():
        kind, slot, units, cw, angle = key
        slots, clockwise, angle = slot == "true", cw == "true", float(angle)
        u = 1 if units == "MM" else 1/25.4
        direction = "CW" if clockwise else "CCW"
        vals = dict(drill_axis_radio="A", drill_array_dir_radio=direction, array_type_radio="linear",
                    drill_array_size_entry=4, drill_pitch_entry=2*u, linear_angle_spinner=angle, drill_angle_entry=90,
                    slot_array_axis_radio="A", slot_array_direction_radio=direction, slot_array_type_radio="linear",
                    slot_array_size_entry=4, slot_array_pitch_entry=2*u, slot_array_linear_angle_spinner=angle,
                    slot_array_angle_entry=90)
        ui = SimpleNamespace(**{k: Value(v) for k, v in vals.items()})
        template = (lambda pt: LineString([(pt[0]-1.1*u, pt[1]), (pt[0]+1.1*u, pt[1])])) if slots else (lambda pt: Point(pt))
        obj = SimpleNamespace(draw_app=SimpleNamespace(ui=ui), origin=(10*u, 20*u),
                              drill_array_size=4, slot_array_size=4, last_dx=0, last_dy=0, util_shape=template)
        cls = "SlotArray" if slots else "DrillArray"
        if kind == "CIRCULAR":
            geos = [s.geo for s in method(tree, cls, "circular_util_shape")(obj, 5*u, math.radians(angle))]
        else:
            geos = method(tree, cls, "utility_geometry")(obj, (10*u, 20*u), True).geo
        for row, legacy in zip(rows, geos, strict=True):
            x1, y1, x2, y2 = (float(row[k]) for k in ("x1", "y1", "x2", "y2"))
            fx = LineString([(x1, y1), (x2, y2)]) if slots else Point(x1, y1)
            delta = fx.hausdorff_distance(legacy) / u
            maximum = max(maximum, delta); controls += 1
            if delta > 1e-9: failures.append(dict(case=key, index=row["index"], distanceMm=delta))
    report = dict(controls=controls, failures=failures, maxDistanceMm=maximum,
                  legacySha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  scope="Original array distribution/orientation; independent point/slot-centreline templates, not GUI or polygon tessellation")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report))
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
