"""Locate Connect differences at preparation, erosion and connection stages.

The same-area control is diagnostic only: it is never counted as independent parity.
"""
import argparse
import json
import logging
import sys
from pathlib import Path
from types import MethodType, SimpleNamespace

from compare_cam_python import dependency_metadata, leaves, lines, metrics, ncc_boundary, compile_plugin_methods


def compare(document, engine, root):
    from shapely import wkt
    from shapely.geometry import MultiPolygon
    from shapely.geometry.base import BaseGeometry
    from shapely.ops import unary_union
    if document["schema"] != 1 or not document["cases"]:
        raise ValueError("Unsupported or empty Connect probe")
    results = []
    cache = {}
    helpers = compile_plugin_methods(root,"ToolNCC.py","NonCopperClear",{"get_ncc_empty_area"})
    helpers["BaseGeometry"] = BaseGeometry
    for case in document["cases"]:
        source = wkt.loads(case["inputWkt"])
        diameter = case["diameter"]
        container = case.get("sourceContainer","direct")
        if container not in ("direct","list-multipart"):
            raise ValueError("Unsupported source container")
        original = [MultiPolygon(leaves(source))] if container == "list-multipart" else source
        binding = SimpleNamespace(app=engine.app,solid_geometry=original)
        boundary = ncc_boundary({"parameters":{"margin":case["margin"],"boundary":"itself"}},
                                source,binding,root,cache)
        area = MethodType(helpers["get_ncc_empty_area"],binding)(original,boundary)
        if isinstance(area,str):
            raise ValueError("Original NCC preparation failed")
        fx_area = wkt.loads(case["clearingWkt"])
        tolerance = .003 * diameter / .5

        def paths(region, connect):
            return [line for part in leaves(region)
                    for line in engine.clear_polygon(part, diameter, 64, overlap=.4, connect=connect).get_objects()]

        python_plain, python_connected = paths(area, False), paths(area, True)
        same_area_plain, same_area_connected = paths(fx_area, False), paths(fx_area, True)
        fx_plain, fx_connected = wkt.loads(case["plainWkt"]), wkt.loads(case["connectedWkt"])
        result = {"id": case["id"], "sourceContainer":container,"clearingAreaDelta": area.symmetric_difference(fx_area).area,
                  "clearingAreaMatches":area.symmetric_difference(fx_area).area <= max(area.area,1e-20) * 1e-8,
                  "pythonAreaStarts": [list(line.coords[0]) for line in lines(area)],
                  "fxAreaStarts": [list(line.coords[0]) for line in lines(fx_area)],
                  "pythonPlainStarts": [list(line.coords[0]) for line in python_plain],
                  "fxPlainStarts": [list(line.coords[0]) for line in lines(fx_plain)],
                  "classicAreaStarts": [list(line.coords[0]) for line in lines(wkt.loads(case["classicWkt"]))],
                  "ngAreaStarts": [list(line.coords[0]) for line in lines(wkt.loads(case["ngWkt"]))],
                  "ngCleanAreaStarts": [list(line.coords[0]) for line in lines(wkt.loads(case["ngCleanWkt"]))],
                  "plain": metrics(unary_union(lines(fx_plain)),unary_union(python_plain),tolerance,diameter),
                  "connected": metrics(unary_union(lines(fx_connected)),unary_union(python_connected),tolerance,diameter),
                  "sameAreaPlain": metrics(unary_union(lines(fx_plain)),unary_union(same_area_plain),tolerance,diameter),
                  "sameAreaConnected": metrics(unary_union(lines(fx_connected)),unary_union(same_area_connected),tolerance,diameter)}
        results.append(result)
    return {"schema": 1, **dependency_metadata(),
            "scope": "Independent public fixtures; sameArea controls are not parity approvals",
            "cases": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root",type=Path,required=True)
    parser.add_argument("--trace",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args = parser.parse_args()
    sys.path[:0] = [str(args.legacy_root.resolve()),str(args.legacy_root.resolve() / "tests")]
    import camlib
    from parser_baseline import install_stub_app
    install_stub_app()
    camlib.log.setLevel(logging.WARNING)
    report = compare(json.loads(args.trace.read_text(encoding="utf-8")),camlib.Geometry(),args.legacy_root.resolve())
    args.output.write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
    print(json.dumps({"independentMatches": sum(c["clearingAreaMatches"] and c["plain"]["matchesSampledCriteria"]
                                                and c["connected"]["matchesSampledCriteria"] for c in report["cases"]),
                      "sameAreaMatches": sum(c["sameAreaConnected"]["matchesSampledCriteria"] for c in report["cases"]),
                      "cases":len(report["cases"]),"report":str(args.output)}))


if __name__ == "__main__":
    main()
