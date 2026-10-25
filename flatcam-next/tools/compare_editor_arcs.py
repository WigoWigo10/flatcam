"""Export public FCArc.make() results without importing/starting the Qt application.

The math and sampling come from the unmodified Python sources, extracted by AST.
Only the UI notification/shape wrapper are stubbed. No project or preferences are read.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace as NS
import warnings

import numpy as np
from numpy.linalg import solve, norm as numpy_norm


def export(root):
    cam = root / "camlib.py"
    editor = root / "appEditors/appGeoEditor.py"
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", SyntaxWarning)
        cam_tree = ast.parse(cam.read_text(encoding="utf-8"))
        editor_tree = ast.parse(editor.read_text(encoding="utf-8"))
    helpers = [n for n in cam_tree.body if isinstance(n, ast.FunctionDef)
               and n.name in {"arc", "three_point_circle", "distance"}]
    cls = next(n for n in editor_tree.body if isinstance(n, ast.ClassDef) and n.name == "FCArc")
    make = next(n for n in cls.body if isinstance(n, ast.FunctionDef) and n.name == "make")
    namespace = dict(np=np, solve=solve, numpy_norm=numpy_norm, distance=lambda a, b: np.linalg.norm(np.array(a)-b),
                     LineString=lambda coords: NS(coords=coords), DrawToolShape=lambda shape: shape, _=lambda text: text)
    exec(compile(ast.Module(body=helpers + [make], type_ignores=[]), "original-python-arcs", "exec"), namespace)
    noop = lambda *args: None
    app = NS(jump_signal=NS(disconnect=noop), inform=NS(emit=noop))
    templates = [
        ("c12", [(0, 0), (5, 0), (0, 12)]),
        ("c12", [(0, 0), (5, 0), (5, 0)]),
        ("c12", [(2, -1), (5, 3), (-4, -7)]),
        ("12c", [(-5, 0), (5, 0), (2, 3)]),
        ("12c", [(-5, 0), (5, 0), (2, -3)]),
        ("12c", [(-5, 0), (5, 0), (2, 0)]),
        ("132", [(5, 0), (-5, 0), (0, 5)]),
        ("132", [(5, 0), (-5, 0), (0, -5)]),
        ("132", [(5, 0), (-5, 0), (0, 10)]),
        ("132", [(5, 0), (-5, 0), (-4, 3)]),
    ]
    cases = []
    modes = {"c12": "CENTER_START_END", "12c": "START_END_CENTER", "132": "START_END_THROUGH"}
    for units, scale in [("MM", 1), ("IN", 1 / 25.4)]:
        for mode, points in templates:
            for direction in ["cw", "ccw"]:
                coordinates = [(x*scale, y*scale) for x, y in points]
                tool = NS(points=coordinates, mode=mode, direction=direction, steps_per_circ=64,
                          draw_app=NS(app=app))
                namespace["make"](tool)
                cases.append(dict(mode=modes[mode], clockwise=direction == "cw", units=units,
                                  points=coordinates, expected=list(tool.geometry.coords)))
    points = [(5., 0.), (0., 5.), (-5., 0.)]
    tool = NS(points=points, mode="132", direction="cw", steps_per_circ=64, draw_app=NS(app=app))
    namespace["make"](tool)
    differences = [dict(mode="START_END_THROUGH", points=points, expectedPython=list(tool.geometry.coords),
                        policy="FX passes through the third point. Python T[0] == -0.0 chooses CCW and misses it.")]
    return dict(scope="Original FCArc.make math/sampling; public synthetic cases, no Qt gestures or physical CNC",
                sources={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in [cam, editor]},
                cases=cases, knownPythonDifferences=differences)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = export(Path(__file__).resolve().parents[2])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(result['cases'])} original Python arc controls to {args.output}")
