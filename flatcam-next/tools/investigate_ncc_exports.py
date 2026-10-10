"""Read-only G-code diagnostics: distinguish travelled and unique coverage length.

Uses the unchanged Python CNC parser. It does not approve CAM parity.
"""
import argparse
import json
import logging
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("--fx-export", type=Path, required=True)
    args = parser.parse_args()
    sys.path[:0] = [str(args.legacy_root.resolve()), str(args.legacy_root.resolve() / "tests")]
    import camlib
    from parser_baseline import install_stub_app
    from shapely import wkt
    from shapely.ops import unary_union
    from compare_cam_python import lines
    from preprocessors.default import default
    app = install_stub_app()
    app.preprocessors = {"default": default()}
    camlib.log.setLevel(logging.WARNING)
    document = json.loads(args.fx_export.read_text(encoding="utf-8"))
    for case in document["cases"]:
        if case["id"] not in ("ncc-lines", "ncc-multi-settings"):
            continue
        cnc = camlib.CNCjob()
        cnc.origin_kind = "geometry"
        cnc.gcode = case["gcode"]
        cuts = [step["geom"] for step in cnc.gcode_parse() if step["kind"][0] == "C"]
        inputs = (case["parameters"].get("fxToolResults") or [{"fxWkt":case["fxWkt"]}])
        source_paths = [line for tool in inputs for line in lines(wkt.loads(tool["fxWkt"]))]
        print(json.dumps({"id":case["id"], "sourceTravelLength":sum(p.length for p in source_paths),
                          "parsedTravelLength":sum(p.length for p in cuts),
                          "sourceUniqueLength":unary_union(source_paths).length,
                          "parsedUniqueLength":unary_union(cuts).length,
                          "sourcePaths":len(source_paths), "parsedPaths":len(cuts)}))


if __name__ == "__main__":
    main()
