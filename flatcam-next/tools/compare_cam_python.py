"""Compare exported FX paths with actual legacy CAM methods, without starting either UI.

Artifacts contain the user's board geometry: keep them in an ignored target directory.
No legacy algorithm is patched; exceptions and mismatches are reported, not hidden.
Cutout's nested handler is compiled directly from the checked-out legacy source.
"""
import argparse
import ast
import hashlib
import html
import json
import lzma
import logging
import math
import re
import sys
from pathlib import Path
from types import SimpleNamespace


def dependency_metadata():
    """Identify the loaded geometry engine, not only its Python wrapper."""
    import shapely
    version = getattr(shapely, "geos_version_string", None)
    if version is None:
        # Shapely 1.x exposes GEOS through this module; avoid its deprecated import in 2.x.
        from shapely.geos import geos_version_string
        version = geos_version_string
    return {"python": sys.version.split()[0], "shapely": shapely.__version__, "geos": version}


def leaves(geometry):
    if isinstance(geometry, (list, tuple)):
        return [leaf for child in geometry for leaf in leaves(child)]
    if hasattr(geometry, "geoms"):
        return [leaf for child in geometry.geoms for leaf in leaves(child)]
    return [] if geometry is None or geometry.is_empty else [geometry]


def lines(geometry):
    from shapely.geometry import Polygon
    result = []
    for leaf in leaves(geometry):
        if isinstance(leaf, Polygon):
            result.extend([leaf.exterior, *leaf.interiors])
        elif leaf.geom_type in ("LineString", "LinearRing"):
            result.append(leaf)
    return result


def sampled_witness(a, b, count=512):
    """Worst sampled point and nearest counterpart, for reproducible mismatch diagnosis."""
    from shapely.ops import nearest_points
    candidates = []
    for side, source, target in (("FX", a, b), ("Python", b, a)):
        points = (source.interpolate(i / count, normalized=True) for i in range(count + 1))
        point = max(points, key=lambda p: p.distance(target))
        other = nearest_points(point, target)[1]
        candidates.append({"side": side, "point": list(point.coords[0]),
                           "nearest": list(other.coords[0]), "distance": point.distance(other)})
    return max(candidates, key=lambda item: item["distance"])


def sampled_distance(a, b, count=512):
    """Symmetric SAMPLE distance, not exact Hausdorff and not a proof of safety."""
    return sampled_witness(a, b, count)["distance"]


def metrics(fx, python, tolerance, diameter=None):
    if fx.is_empty or python.is_empty:
        raise ValueError("Empty output cannot establish parity")
    length_delta = abs(fx.length - python.length) / max(fx.length, python.length)
    bounds_delta = max(abs(a - b) for a, b in zip(fx.bounds, python.bounds))
    witness = sampled_witness(fx, python)
    distance = witness["distance"]
    result = {"fxLength": fx.length, "pythonLength": python.length,
            "relativeLengthDelta": length_delta, "boundsMaxDelta": bounds_delta,
            "sampledDistance": distance, "distanceWitness": witness, "distanceSamplesPerDirection": 513,
            "tolerance": tolerance, "relativeLengthTolerance": 0.001,
            "matchesSampledCriteria": length_delta <= .001 and bounds_delta <= tolerance
                                       and distance <= tolerance}
    if diameter is not None:
        fx_cover = fx.buffer(diameter / 2, resolution=16)
        python_cover = python.buffer(diameter / 2, resolution=16)
        intersection = fx_cover.intersection(python_cover).area
        union = fx_cover.union(python_cover).area
        result.update(footprintIntersectionOverUnion=intersection / union,
                      footprintSymmetricDifferenceArea=union - intersection,
                      minimumFootprintIntersectionOverUnion=.995)
        result["matchesSampledCriteria"] &= intersection / union >= .995
    return result


def compile_rectangular_handler(root):
    from shapely.geometry import Polygon, LineString, LinearRing
    from shapely.ops import unary_union
    tree = ast.parse((root / "appTools" / "ToolCutOut.py").read_text(encoding="utf-8-sig"))
    handler = next(node for node in ast.walk(tree)
                   if isinstance(node, ast.FunctionDef) and node.name == "cutout_rect_handler")
    # Globals replace the nested lexical bindings, not any algorithm or operation.
    # Importing the entire plugin pulls in legacy command-line/UI initialization. Compile only
    # the ORIGINAL required helpers; their bodies, including legacy multipart limitations, stay intact.
    cls = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == "CutOut")
    helpers = [node for node in cls.body if isinstance(node, ast.FunctionDef)
               and node.name in ("flatten", "subtract_poly_from_geo")]
    if len(helpers) != 2:
        raise ValueError("Legacy Cutout helper layout changed")
    for node in helpers:
        node.decorator_list = []  # compiled as standalone functions, then bound as static helpers
    binding = SimpleNamespace()
    namespace = {"CutOut": binding, "Polygon": Polygon, "LineString": LineString,
                 "LinearRing": LinearRing, "unary_union": unary_union,
                 "log": logging.getLogger("flatcam.comparison")}
    exec(compile(ast.Module(body=helpers + [handler], type_ignores=[]), str(root / "appTools" / "ToolCutOut.py"),
                 "exec"), namespace)
    binding.flatten = namespace["flatten"]
    binding.subtract_poly_from_geo = namespace["subtract_poly_from_geo"]
    namespace["self"] = binding
    return namespace


def legacy_paths(case, copper, engine, root, handler_cache):
    from shapely import wkt
    from shapely.geometry import box
    from shapely.ops import unary_union
    params = case["parameters"]
    diameter = case["diameter"]
    operation = case["operation"]
    if operation == "isolation":
        results = []
        for i in range(params["passes"]):
            # ToolIsolation.py's normal (non-Rest) UI branch, including its epsilon.
            offset = diameter * ((2 * i + 1) / 2.0000001) - i * params["overlap"] * diameter
            results.extend(lines(engine.isolation_geometry(offset, geometry=copper, iso_type=2)))
        return unary_union(results), {}
    if operation == "cutout":
        if "namespace" not in handler_cache:
            handler_cache["namespace"] = compile_rectangular_handler(root)
        namespace = handler_cache["namespace"]
        namespace.update(margin=params["margin"], gaps=params["gaps"])
        xmin, ymin, xmax, ymax = copper.bounds
        outline = box(xmin, ymin, xmax, ymax).buffer(params["margin"] + abs(diameter / 2))
        result = namespace["cutout_rect_handler"](
            outline, params["gapSize"] / 2 + diameter / 2, xmin, ymin, xmax, ymax)
        return unary_union(lines(result)), {"oracle": "actual nested rectangular handler and CutOut helpers"}
    if operation == "ncc":
        # ToolNCC Itself + apply_margin_to_bounding_box, no ISO or copper offset.
        area = copper.convex_hull.buffer(params["margin"], join_style=2).difference(copper).buffer(0)
        fx_area = wkt.loads(params["fxClearingAreaWkt"])
        extra = {"clearingAreaSymmetricDifference": area.symmetric_difference(fx_area).area}
    elif operation == "paint":
        area = wkt.loads(case["inputWkt"])
        extra = {"oracle": "actual clearing routine on the same explicitly selected area"}
    else:
        raise ValueError("Unsupported operation: " + operation)
    methods = {"STANDARD": engine.clear_polygon, "SEED": engine.clear_polygon2,
               "LINES": engine.clear_polygon3}
    results = []
    failures = 0
    for polygon in leaves(area):
        result = methods[params["method"]](polygon, diameter, 64, overlap=params["overlap"],
                                           connect=params["connect"], contour=params["contour"])
        if result is None:
            failures += 1
        else:
            results.extend(lines(list(result.get_objects())))
    extra["pythonFailedPolygons"] = failures
    return unary_union(results), extra


def svg_paths(geometry, color, width):
    paths = []
    # SVG visual simplification only. Numerical comparisons always use the original geometry.
    for line in lines(geometry.simplify(width / 6, preserve_topology=True)):
        coordinates = " ".join(f"{x:.6g},{y:.6g}" for x, y, *_ in line.coords)
        paths.append(f'<polyline points="{coordinates}" fill="none" stroke="{color}" '
                     f'stroke-width="{width:.6g}"/>')
    return "".join(paths)


def overlay(fx, python):
    xmin, ymin, xmax, ymax = fx.union(python).bounds
    size = max(xmax - xmin, ymax - ymin)
    pad = size * .025
    width = max(size / 1400, 1e-7)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{xmin-pad} {ymin-pad} '
            f'{xmax-xmin+2*pad} {ymax-ymin+2*pad}"><rect x="{xmin-pad}" y="{ymin-pad}" '
            f'width="{xmax-xmin+2*pad}" height="{ymax-ymin+2*pad}" fill="#fff"/>'
            f'<g transform="translate(0 {ymin+ymax}) scale(1 -1)">'
            + svg_paths(python, "#e17200", width * 1.6) + svg_paths(fx, "#087caa", width)
            + '</g></svg>')


def run(args):
    root = args.legacy_root.resolve()
    sys.path.insert(0, str(root))
    sys.path.insert(0, str(root / "tests"))
    if args.dependency_path:
        sys.path.insert(0, str(args.dependency_path.resolve()))
    import camlib
    import shapely
    from parser_baseline import install_stub_app
    from shapely import wkt
    from shapely.ops import unary_union
    from preprocessors.default import default
    app = install_stub_app()
    app.preprocessors = {"default": default()}
    camlib.log.setLevel(logging.WARNING)
    engine = camlib.Geometry()
    engine.geo_steps_per_circle = 64
    export = json.loads(args.fx_export.read_text(encoding="utf-8"))
    if export["schema"] != 1:
        raise ValueError("Unsupported FX export schema")
    if export["units"] not in ("MM", "IN", "INCH"):
        raise ValueError("Unsupported units")
    identifiers = [case["id"] for case in export["cases"]]
    if len(set(identifiers)) != len(identifiers) or any(
            not re.fullmatch(r"[a-z0-9-]{1,64}", name) for name in identifiers):
        raise ValueError("Invalid or duplicate case identifiers")
    if any(not math.isfinite(case["diameter"]) or case["diameter"] <= 0 for case in export["cases"]):
        raise ValueError("Invalid tool diameter")
    cases = export["cases"]
    if args.cases:
        requested = set(args.cases.split(","))
        if not requested.issubset(identifiers):
            raise ValueError("Unknown requested case identifiers")
        cases = [case for case in cases if case["id"] in requested]
    fx_copper = wkt.loads(export["sourceWkt"])
    copper = fx_copper
    source_delta = 0
    if args.project:
        raw = args.project.read_bytes()
        if raw.startswith(b"\xfd7zXZ"):
            raw = lzma.decompress(raw)
        project = json.loads(raw.decode("utf-8-sig"), object_hook=camlib.dict2obj)
        entry = next(obj for obj in project["objs"]
                     if obj["kind"] == "gerber" and obj["options"]["name"] == export["sourceName"])
        if entry["units"] != export["units"]:
            raise ValueError("Source units differ")
        copper = unary_union(leaves(entry["solid_geometry"]))
        source_delta = copper.symmetric_difference(fx_copper).area
        if source_delta > max(copper.area, 1) * 1e-8:
            raise ValueError(f"Imported source differs before CAM: area delta {source_delta}")
    engine.solid_geometry = copper
    tolerance = .003 / 25.4 if export["units"] in ("IN", "INCH") else .003
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"schema": 1, **dependency_metadata(),
              "source": export["sourceName"], "units": export["units"],
              "sourceAreaDelta": source_delta, "independentProjectDecode": bool(args.project),
              "legacySourceSha256": {str(path): hashlib.sha256((root / path).read_bytes()).hexdigest()
                                     for path in (Path("camlib.py"), Path("appTools/ToolCutOut.py"),
                                                  Path("appTools/ToolIsolation.py"), Path("appTools/ToolNCC.py"))},
              "scope": "Headless numerical sampling, not full UI/physical validation; no oracle patches",
              "selectedCases": [case["id"] for case in cases],
              "cases": []}
    cards = []
    handler_cache = {}
    for case in cases:
        result = {"id": case["id"], "operation": case["operation"],
                  "fxDetailedPreviewAvailable": case["fxDetailedPreviewAvailable"],
                  "fxPreviewWarning": case["fxPreviewWarning"]}
        (args.output / (case["id"] + ".nc")).write_text(case["gcode"], encoding="utf-8")
        try:
            cnc = camlib.CNCjob()
            # Object-layer metadata normally set by FlatCAMCNCJob, not by the CAM base constructor.
            cnc.origin_kind = "geometry"
            cnc.gcode = case["gcode"]
            parsed = cnc.gcode_parse()
            if parsed == "fail" or not parsed:
                raise ValueError("Legacy CNC parser rejected FX G-code")
            cut = unary_union([step["geom"] for step in parsed if step["kind"][0] == "C"])
            if cut.is_empty or cut.length <= 0:
                raise ValueError("Legacy CNC parser found no cutting motion")
            result["pythonParsedFxGcode"] = True
            result["pythonParsedCutLength"] = cut.length
            python, extra = legacy_paths(case, copper, engine, root, handler_cache)
            fx = unary_union(lines(wkt.loads(case["fxWkt"])))
            if not python.is_valid or not fx.is_valid:
                raise ValueError("Invalid output")
            result.update(extra)
            (args.output / (case["id"] + ".python.wkt")).write_text(python.wkt, encoding="utf-8")
            (args.output / (case["id"] + ".fx.wkt")).write_text(fx.wkt, encoding="utf-8")
            result.update(metrics(fx, python, tolerance, case["diameter"]))
            result["status"] = "MATCH_SAMPLED" if result["matchesSampledCriteria"] else "DIFFERENT"
            # A partial legacy result is not a parity success, even when the surviving lines agree.
            if result.get("pythonFailedPolygons", 0) != case["parameters"].get("fxFailedPolygons", 0):
                result["status"] = "PARTIAL_DIFFERENCE"
            filename = case["id"] + ".svg"
            (args.output / filename).write_text(overlay(fx, python), encoding="utf-8")
            result["overlay"] = filename
            cards.append(f'<section><h2>{html.escape(case["id"])}</h2><p>{result["status"]}</p>'
                         f'<object data="{filename}" type="image/svg+xml"></object>'
                         f'<pre>{html.escape(json.dumps(result, indent=2))}</pre></section>')
        except Exception as error:
            result.update(status="ORACLE_ERROR", error=f"{type(error).__name__}: {error}")
            cards.append(f'<section><h2>{html.escape(case["id"])}</h2><pre>'
                         f'{html.escape(result["error"])}</pre></section>')
        report["cases"].append(result)
        print(json.dumps(result), flush=True)
    counts = {status: sum(c["status"] == status for c in report["cases"])
              for status in ("MATCH_SAMPLED", "DIFFERENT", "PARTIAL_DIFFERENCE", "ORACLE_ERROR")}
    report["counts"] = counts
    (args.output / "report.json").write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    document = ('<!doctype html><html lang="pt-BR"><meta charset="utf-8">'
                '<title>FlatCAM FX / Python — comparação CAM</title><style>'
                'body{font:16px system-ui;margin:2rem;background:#f1f6fa;color:#20303c}'
                'section{background:white;border:1px solid #ccdbe4;padding:1rem;margin:1rem 0}'
                'object{width:100%;height:650px}pre{white-space:pre-wrap}</style>'
                '<h1>Comparação CAM: FX × Python</h1><p>Azul: FX. Laranja: Python. '
                'Rotinas reais do legado, sem patches. Métricas por amostragem, não prova de paridade total '
                'ou segurança física. SVG simplificado apenas para exibição.</p>'
                f'<p>{html.escape(json.dumps(counts))}</p>' + "".join(cards) + '</html>')
    (args.output / "index.html").write_text(document, encoding="utf-8")
    print(json.dumps({"counts": counts, "report": str(args.output / "index.html")}))
    return 1 if args.strict and counts["MATCH_SAMPLED"] != len(cases) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("--fx-export", type=Path, required=True)
    parser.add_argument("--project", type=Path, help="Independently decode the untouched legacy project")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dependency-path", type=Path,
                        help="Optional isolated dependency directory; does not alter the active environment")
    parser.add_argument("--cases", help="Optional comma-separated case IDs for focused diagnosis")
    parser.add_argument("--strict", action="store_true", help="Fail on every mismatch or legacy error")
    args = parser.parse_args()
    raise SystemExit(run(args))


if __name__ == "__main__":
    main()
