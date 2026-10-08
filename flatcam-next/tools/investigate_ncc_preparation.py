"""Find the first NCC preparation difference without changing the legacy oracle.

Outputs may contain private geometry; keep trace and report in ignored target.
Candidate results diagnose a stage, not approve CAM or CNC program parity.
"""
import argparse
import hashlib
import json
import logging
import lzma
import sys
from pathlib import Path
from types import MethodType, SimpleNamespace

from compare_cam_python import compile_ncc_gui, dependency_metadata, leaves, lines, metrics


def geometry_delta(actual, expected):
    equal = actual.equals(expected)
    return {"symmetricDifferenceArea": actual.symmetric_difference(expected).area,
            "areaDelta": abs(actual.area - expected.area),
            "hausdorffDistance": 0.0 if equal else actual.hausdorff_distance(expected),
            "equalsTopologically": equal}


def ring_representation(actual, expected):
    """Diagnostic ring pairing by rounded bounds; no geometry/parity normalization."""
    def rings(geometry):
        for part in leaves(geometry):
            if part.geom_type != "Polygon":
                raise ValueError("Preparation must contain polygons")
            yield "exterior", part.exterior
            for interior in part.interiors:
                yield "interior", interior
    def key(role, ring):
        return role, tuple(round(value,8) for value in ring.bounds)
    reference = {}
    for role, ring in rings(expected):
        reference.setdefault(key(role,ring),[]).append(ring)
    report = {"scope":"Ring representation diagnostic; rounded bounds only pair rings, not approve parity",
              "pairedRings":0,"unpairedOrAmbiguousRings":0,"differentStartCount":0,
              "differentVertexCount":0,"differentOrientationCount":0,"maxStartDistance":0.0}
    from math import dist
    for role, ring in rings(actual):
        candidates = reference.get(key(role,ring),[])
        if len(candidates) != 1:
            report["unpairedOrAmbiguousRings"] += 1
            continue
        other = candidates[0]
        report["pairedRings"] += 1
        distance = dist(ring.coords[0],other.coords[0])
        report["differentStartCount"] += distance > 1e-8
        report["differentVertexCount"] += len(ring.coords) != len(other.coords)
        report["differentOrientationCount"] += ring.is_ccw != other.is_ccw
        report["maxStartDistance"] = max(report["maxStartDistance"],distance)
    return report


def compare(trace, legacy_source, root, app):
    from shapely import wkt
    from shapely.ops import unary_union
    namespace = compile_ncc_gui(root)
    binding = SimpleNamespace(app=app)
    obj = SimpleNamespace(kind="gerber",solid_geometry=legacy_source)
    bbox, kind = namespace["calculate_bounding_box"](binding,obj,0)
    boundary = namespace["apply_margin_to_bounding_box"](binding,bbox,kind,0,trace["margin"])
    # get_ncc_empty_area is also an original helper; compile/bind it unchanged.
    from compare_cam_python import compile_plugin_methods
    empty_namespace = compile_plugin_methods(root,"ToolNCC.py","NonCopperClear",{"get_ncc_empty_area"})
    from shapely.geometry.base import BaseGeometry
    empty_namespace.update(BaseGeometry=BaseGeometry)
    binding.get_ncc_empty_area = MethodType(empty_namespace["get_ncc_empty_area"],binding)
    empty = binding.get_ncc_empty_area(legacy_source,boundary)
    if isinstance(empty,str):
        raise ValueError("Original NCC preparation failed")
    copper = unary_union(leaves(legacy_source))
    stages = {name:wkt.loads(value) for name,value in trace["stages"].items()}
    comparisons = {}
    for name in ("source","cleanCopper"):
        comparisons[name] = geometry_delta(stages[name],copper)
    copper_representation = {name:ring_representation(stages[name],copper)
                             for name in ("source","cleanCopper","classicUnionCopper","modernUnionCopper")
                             if name in stages}
    raw_parts = legacy_source if isinstance(legacy_source,(list,tuple)) else [legacy_source]
    if len(raw_parts) == 1:
        copper_representation["fxVsRawSource"] = ring_representation(stages["source"],raw_parts[0])
        copper_representation["legacyUnionVsRawSource"] = ring_representation(copper,raw_parts[0])
    for name in ("rawHull","cleanHull"):
        comparisons[name] = geometry_delta(stages[name],bbox)
    for name in ("jtsBoundary","geosBoundary","rawJtsBoundary","rawGeosBoundary"):
        comparisons[name] = geometry_delta(stages[name],boundary)
    for name in ("exportedClearingArea","jtsClearingArea","geosClearingArea"):
        comparisons[name] = geometry_delta(stages[name],empty)
    representation = {"geosClearingArea":ring_representation(stages["geosClearingArea"],empty)}
    for name in ("geosClearingWithoutFinalRepair","geosClearingRawTarget"):
        if name in stages:
            comparisons[name] = geometry_delta(stages[name],empty)
            representation[name] = ring_representation(stages[name],empty)
    geos_same_inputs = stages["geosBoundary"].difference(stages["cleanCopper"])
    return {"schema":1,**dependency_metadata(),"units":trace["units"],"margin":trace["margin"],
            "scope":"Diagnostic preparation only, not CAM/G-code parity",
            "legacyContainer":type(legacy_source).__name__,
            "legacyPartTypes":[part.geom_type for part in leaves(legacy_source)],
            "legacyCopperValid":copper.is_valid,"comparisons":comparisons,
            "copperRingRepresentation":copper_representation,
            "candidateClearingRingRepresentation":representation,
            "sameInputsOverlayControl":{
                "scope":"Diagnostic GEOS difference on the exact FX boundary/copper, not the original GUI invocation",
                "jtsVsGeosArea":geometry_delta(stages["geosClearingArea"],geos_same_inputs),
                "jtsVsGeosRings":ring_representation(stages["geosClearingArea"],geos_same_inputs),
                "geosVsOriginalRings":ring_representation(geos_same_inputs,empty)},
            "legacyStages":{"copper":copper.wkt,"bbox":bbox.wkt,"boundary":boundary.wkt,"empty":empty.wkt},
            "legacySourceSha256":hashlib.sha256((root / "appTools/ToolNCC.py").read_bytes()).hexdigest()}


def connect_same_area_control(export, engine):
    """Diagnostic only: feed FX's own area to Python to locate later differences."""
    from shapely import wkt
    from shapely.ops import unary_union
    plain = next(case for case in export["cases"] if case["id"] == "ncc-standard")
    connected = next(case for case in export["cases"] if case["id"] == "ncc-connect")
    params = connected["parameters"]
    area = wkt.loads(params["fxClearingAreaWkt"])
    if not area.equals(wkt.loads(plain["parameters"]["fxClearingAreaWkt"])):
        raise ValueError("Plain and connected controls must use the same area")
    diameter = connected["diameter"]
    tolerance = .003 / 25.4 if export["units"] in ("IN","INCH") else .003
    results = {"scope":"Same-area diagnostic only, not an independent parity approval"}
    for case, use_connect, name in ((plain,False,"plain"),(connected,True,"connected")):
        paths = []
        for part in leaves(area):
            storage = engine.clear_polygon(part,diameter,64,overlap=params["overlap"],
                    connect=use_connect,contour=params["contour"])
            if storage is not None:
                paths.extend(storage.get_objects())
        results[name] = metrics(unary_union(lines(wkt.loads(case["fxWkt"]))),
                                unary_union(paths),tolerance,diameter)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root",type=Path,required=True)
    parser.add_argument("--trace",type=Path,required=True)
    parser.add_argument("--project",type=Path)
    parser.add_argument("--connect-control",type=Path,help="Optional candidate CAM export for a same-area Connect control")
    parser.add_argument("--output",type=Path,required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a new output file")
    root = args.legacy_root.resolve()
    sys.path[:0] = [str(root),str(root / "tests")]
    import camlib
    from parser_baseline import install_stub_app
    from shapely import wkt
    app = install_stub_app()
    camlib.log.setLevel(logging.WARNING)
    trace = json.loads(args.trace.read_text(encoding="utf-8"))
    if trace["schema"] != 1:
        raise ValueError("Unsupported preparation trace")
    source = wkt.loads(trace["stages"]["source"])
    if args.project:
        raw = args.project.read_bytes()
        if raw.startswith(b"\xfd7zXZ"):
            raw = lzma.decompress(raw)
        project = json.loads(raw.decode("utf-8-sig"),object_hook=camlib.dict2obj)
        entry = next(obj for obj in project["objs"] if obj["kind"] == "gerber"
                     and obj["options"]["name"] == trace["sourceName"])
        if entry["units"] != trace["units"]:
            raise ValueError("Source units differ")
        source = entry["solid_geometry"]
    report = compare(trace,source,root,app)
    if args.connect_control:
        control = json.loads(args.connect_control.read_text(encoding="utf-8"))
        if control["units"] != trace["units"] or control["sourceName"] != trace["sourceName"]:
            raise ValueError("Connect control source differs")
        report["connectSameAreaControl"] = connect_same_area_control(control,camlib.Geometry())
    args.output.write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
    print(json.dumps({"comparisons":report["comparisons"],"report":str(args.output)}))


if __name__ == "__main__":
    main()
