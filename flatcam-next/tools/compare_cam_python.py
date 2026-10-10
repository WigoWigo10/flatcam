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
from types import MethodType, SimpleNamespace


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


def panelize_legacy_source(root, source, reference, layout):
    """Execute the ORIGINAL Gerber solid-geometry initializer, preserving its list container.

    Aperture copying/export is stubbed because subsequent CAM uses solid_geometry only.
    This does not validate Python aperture editing, Excellon panelization or GUI gestures.
    Reference bounds are independently decoded, never trusted from the FX export.
    """
    from copy import deepcopy
    import numpy as np
    from shapely import affinity
    from shapely.ops import unary_union
    columns, rows = layout["columns"], layout["rows"]
    if (type(columns) is not int or type(rows) is not int or columns < 1 or rows < 1
            or columns * rows > 10000):
        raise ValueError("Invalid panel layout")
    sx, sy = layout["spacingX"], layout["spacingY"]
    if any(not math.isfinite(v) or v < 0 for v in (sx, sy)):
        raise ValueError("Invalid panel spacing")
    bounds = unary_union(leaves(reference)).bounds
    if len(bounds) != 4:
        raise ValueError("Empty panel reference")
    path = root / "appTools/ToolPanelize.py"
    tree = ast.parse(path.read_text(encoding="utf-8-sig"))
    initializers = [n for n in ast.walk(tree) if isinstance(n, ast.FunctionDef) and n.name == "job_init_geometry"]
    if len(initializers) != 1:
        raise ValueError("Legacy panel initializer layout changed")
    noop = lambda *args, **kwargs: None
    app = SimpleNamespace(abort_flag=False, inform=SimpleNamespace(emit=noop),
                          proc_container=SimpleNamespace(update_view_text=noop),
                          f_handlers=SimpleNamespace(export_gerber=noop, export_dxf=noop))
    namespace = {"panel_source_obj": SimpleNamespace(kind="gerber", solid_geometry=source, apertures={}),
                 "copied_apertures": {}, "rows": rows, "columns": columns,
                 "lenghtx": bounds[2] - bounds[0] + sx, "lenghty": bounds[3] - bounds[1] + sy,
                 "panel_type": "gerber", "to_optimize": False, "np": np, "affinity": affinity,
                 "deepcopy": deepcopy, "_": lambda text: text, "grace": RuntimeError,
                 "self": SimpleNamespace(app=app, outname="headless-panel"),
                 "log": logging.getLogger("flatcam.comparison")}
    exec(compile(ast.Module(body=initializers, type_ignores=[]), str(path), "exec"), namespace)
    result = SimpleNamespace()
    namespace["job_init_geometry"](result, app)
    return result.solid_geometry


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


def compile_rectangular_handler(root, freeform=False):
    from copy import deepcopy
    from shapely.geometry import Polygon, LineString, LinearRing, box
    from shapely.ops import unary_union
    tree = ast.parse((root / "appTools" / "ToolCutOut.py").read_text(encoding="utf-8-sig"))
    handler = next(node for node in ast.walk(tree)
                   if isinstance(node, ast.FunctionDef) and node.name == ("cutout_handler" if freeform else "cutout_rect_handler"))
    # Globals replace the nested lexical bindings, not any algorithm or operation.
    # Importing the entire plugin pulls in legacy command-line/UI initialization. Compile only
    # the ORIGINAL required helpers; their bodies, including legacy multipart limitations, stay intact.
    cls = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == "CutOut")
    names = ("flatten", "subtract_poly_from_geo", "recursive_bounds", "intersect_geo") if freeform else ("flatten", "subtract_poly_from_geo")
    helpers = [node for node in cls.body if isinstance(node, ast.FunctionDef) and node.name in names]
    if len(helpers) != len(names):
        raise ValueError("Legacy Cutout helper layout changed")
    for node in helpers:
        node.decorator_list = []  # compiled as standalone functions, then bound as static helpers
    binding = SimpleNamespace()
    namespace = {"CutOut": binding, "Polygon": Polygon, "LineString": LineString,
                 "LinearRing": LinearRing, "unary_union": unary_union, "box": box, "Inf": float("inf"), "deepcopy": deepcopy,
                 "log": logging.getLogger("flatcam.comparison")}
    exec(compile(ast.Module(body=helpers + [handler], type_ignores=[]), str(root / "appTools" / "ToolCutOut.py"),
                 "exec"), namespace)
    for name in names:
        setattr(binding,name,namespace[name])
    namespace["self"] = binding
    return namespace


def compile_plugin_methods(root, filename, class_name, names):
    """Compile original method bodies with explicit headless bindings, never patch algorithms."""
    from shapely.geometry import Polygon, MultiPolygon, LineString, LinearRing, MultiLineString, base
    from shapely.ops import unary_union
    source = root / "appTools" / filename
    tree = ast.parse(source.read_text(encoding="utf-8-sig"))
    cls = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == class_name)
    methods = [node for node in cls.body if isinstance(node, ast.FunctionDef) and node.name in names]
    if {node.name for node in methods} != set(names):
        raise ValueError("Legacy method layout changed: " + filename)
    for node in methods:
        node.decorator_list = []  # Bodies remain unchanged; static helpers are bound below.
    namespace = dict(Polygon=Polygon, MultiPolygon=MultiPolygon, LineString=LineString,
                     LinearRing=LinearRing, MultiLineString=MultiLineString, base=base,
                     unary_union=unary_union, log=logging.getLogger("flatcam.comparison"),
                     _=lambda text: text)
    exec(compile(ast.Module(body=methods, type_ignores=[]), str(source), "exec"), namespace)
    return namespace


def compile_ncc_gui(root):
    """Original GUI initializers, not the different clear_copper_tcl Rest loop."""
    from copy import deepcopy
    from collections.abc import Iterable
    import numpy as np
    from PyQt5 import QtWidgets
    if str(root.resolve()) not in sys.path:
        sys.path.insert(0,str(root.resolve()))
    from appCommon.Common import GracefulException
    source = root / "appTools/ToolNCC.py"
    tree = ast.parse(source.read_text(encoding="utf-8-sig"))
    cls = next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name == "NonCopperClear")
    outer = next(n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name == "clear_copper")
    initializers = [n for n in outer.body if isinstance(n,ast.FunctionDef)
                    and n.name in ("gen_clear_area","gen_clear_area_rest")]
    if len(initializers) != 2:
        raise ValueError("Legacy GUI NCC initializer layout changed")
    namespace = compile_plugin_methods(root,"ToolNCC.py","NonCopperClear",
            {"calculate_bounding_box","apply_margin_to_bounding_box","get_tool_empty_area",
             "get_ncc_empty_area","clear_polygon_worker","geometry_parts","poly2rings"})
    namespace.update(deepcopy=deepcopy,Iterable=Iterable,np=np,QtWidgets=QtWidgets,
                     grace=GracefulException,BaseGeometry=namespace["base"].BaseGeometry)
    exec(compile(ast.Module(body=initializers,type_ignores=[]),str(source),"exec"),namespace)
    return namespace


def legacy_ncc_tools(case, engine, root, cache):
    """Run original GUI multi-tool/Rest bodies with only headless UI bindings."""
    from copy import deepcopy
    from shapely import wkt
    from shapely.ops import unary_union
    if "ncc_gui" not in cache:
        cache["ncc_gui"] = compile_ncc_gui(root)
    # Nested functions have mutable globals; use a fresh namespace per execution.
    template = cache["ncc_gui"]
    namespace = template.copy()
    from types import FunctionType
    for name, value in template.items():
        if isinstance(value,FunctionType) and value.__globals__ is template:
            namespace[name] = FunctionType(value.__code__,namespace,name,value.__defaults__,value.__closure__)
    params = case["parameters"]
    rest = params["restMachining"]
    tools = params["tools"]
    settings = {i + 1:{"tooldia":tool["diameter"],"solid_geometry":[],"data":{
        "tools_ncc_overlap":tool["overlap"] * 100,
        "tools_ncc_method":["STANDARD","SEED","LINES","COMBO"].index(tool["method"]),
        "tools_ncc_connect":tool["connect"],"tools_ncc_contour":tool["contour"],
        "tools_ncc_offset_choice":tool["copperOffset"] != 0,"tools_ncc_offset_value":tool["copperOffset"]}}
        for i,tool in enumerate(tools)}
    field = lambda value: SimpleNamespace(get_value=lambda:value)
    binding = SimpleNamespace(app=engine.app,circle_steps=64,decimals=engine.app.decimals,
        ncc_tools=deepcopy(settings),sel_rect=[],
        ui=SimpleNamespace(ncc_margin_entry=field(params["margin"]),
            rest_ncc_connect_cb=field(params["connect"]),rest_ncc_contour_cb=field(params["contour"]),
            rest_ncc_choice_offset_cb=field(params["copperOffset"] != 0),
            rest_ncc_offset_spinner=field(params["copperOffset"])))
    for name in ("calculate_bounding_box","apply_margin_to_bounding_box","get_tool_empty_area",
                 "get_ncc_empty_area","clear_polygon_worker"):
        setattr(binding,name,MethodType(namespace[name],binding))
    binding.geometry_parts = namespace["geometry_parts"]
    binding.poly2rings = namespace["poly2rings"]
    failures = 0
    attempted_order = []
    worker_active = False
    # Observers keep actual clearing functions unchanged and count their None outputs.
    def observed(method):
        def call(*args,**kwargs):
            nonlocal failures
            diameter = args[1] if len(args) > 1 else kwargs["tooldia"]
            if diameter not in attempted_order:
                attempted_order.append(diameter)
            result = method(*args,**kwargs)
            if result is None and not worker_active:
                failures += 1
            return result
        return call
    for name in ("clear_polygon","clear_polygon2","clear_polygon3"):
        setattr(binding,name,observed(getattr(engine,name)))
    original_worker = binding.clear_polygon_worker
    def capture_worker(*args,**kwargs):
        nonlocal worker_active, failures
        worker_active = True
        try:
            result = original_worker(*args,**kwargs)
        finally:
            worker_active = False
        # Combo's failed preliminary attempts are not failed polygons when
        # a later fallback succeeds. Count the original worker's final result.
        if result is None:
            failures += 1
        return result
    binding.clear_polygon_worker = capture_worker
    areas = []
    original_area = binding.get_tool_empty_area
    def capture_area(*args,**kwargs):
        result = original_area(*args,**kwargs)
        if isinstance(result,tuple):
            areas.append(result[0])
        return result
    binding.get_tool_empty_area = capture_area
    variant = params.get("boundary","itself")
    selection = {"itself":0,"area":1,"reference-gerber":2,"reference-geometry":2}[variant]
    reference = wkt.loads(params["referenceWkt"]) if selection else None
    if selection == 1:
        binding.sel_rect = leaves(reference)
    reference_obj = SimpleNamespace(kind="gerber" if variant == "reference-gerber" else "geometry",
                                   solid_geometry=reference) if selection == 2 else None
    app = engine.app
    # Runtime/UI state only; no user preferences or project files are modified.
    app.defaults = dict(app.defaults,gerber_buffering="full",tools_ncc_plotting="normal")
    app.dec_format = lambda value,decimals: round(value,decimals)
    app.inform_shell = app.inform
    obj = SimpleNamespace(kind="geometry",tools={},options={},solid_geometry=[])
    namespace.update(self=binding,run_threaded=True,order={"NONE":"no","FORWARD":"fwd","REVERSE":"rev"}[params["order"]],
        sorted_clear_tools=[tool["diameter"] for tool in tools],ncc_select=selection,
        ncc_obj=SimpleNamespace(kind="gerber",solid_geometry=engine.solid_geometry),sel_obj=reference_obj,
        isotooldia=[],name="headless-ncc",units=case["units"],prog_plot=False,tools_storage=deepcopy(settings))
    outcome = namespace["gen_clear_area_rest" if rest else "gen_clear_area"](obj,app)
    if outcome == "fail" or not obj.solid_geometry or not areas:
        raise ValueError("Original GUI NCC produced no output")
    tool_paths = {entry["tooldia"]:unary_union(lines(entry["solid_geometry"])) for entry in obj.tools.values()}
    return unary_union(lines(obj.solid_geometry)), {
        "oracle":"original ToolNCC.clear_copper GUI initializer and helpers (not Tcl)",
        "pythonFailedPolygons":failures,"_toolPaths":tool_paths,
        # Normal GUI output dict keeps insertion order even when processing
        # was reversed. Observe actual clearing calls, not that storage order.
        "pythonNonemptyToolOrder":[diameter for diameter in attempted_order
                                    if diameter in tool_paths and not tool_paths[diameter].is_empty],
        "clearingAreaSymmetricDifference":unary_union(areas).symmetric_difference(wkt.loads(params["fxClearingAreaWkt"])).area,
        "clearingAreaMatches":unary_union(areas).symmetric_difference(wkt.loads(params["fxClearingAreaWkt"])).area
            <= max(unary_union(areas).area,1e-20) * 1e-8}


def isolation_exception_paths(paths, mask, root, cache):
    if "isolation" not in cache:
        cache["isolation"] = compile_plugin_methods(root, "ToolIsolation.py", "ToolIsolation",
                                                    {"area_subtraction", "poly2rings"})
    namespace = cache["isolation"]
    binding = SimpleNamespace(poly2rings=namespace["poly2rings"])
    # The plugin receives a list of pass paths and polygon exception shapes.
    return namespace["area_subtraction"](binding, paths, subtraction_geo=leaves(mask))


def ncc_boundary(case, copper, engine, root, cache):
    from shapely import wkt
    if "ncc" not in cache:
        cache["ncc"] = compile_plugin_methods(root, "ToolNCC.py", "NonCopperClear",
                                             {"calculate_bounding_box", "apply_margin_to_bounding_box"})
    params = case["parameters"]
    variant = params.get("boundary", "itself")
    selection = {"itself": 0, "connect": 0, "no-contour": 0, "area": 1,
                 "reference-gerber": 2, "reference-geometry": 2}[variant]
    reference = wkt.loads(params["referenceWkt"]) if selection else None
    binding = SimpleNamespace(app=engine.app, sel_rect=leaves(reference))
    box = SimpleNamespace(kind="gerber" if variant == "reference-gerber" else "geometry",
                          solid_geometry=reference) if selection == 2 else None
    namespace = cache["ncc"]
    calculated = namespace["calculate_bounding_box"](binding, SimpleNamespace(solid_geometry=engine.solid_geometry), selection, box)
    if not isinstance(calculated, tuple) or calculated[0] is None:
        raise ValueError("Legacy NCC boundary unavailable")
    result = namespace["apply_margin_to_bounding_box"](binding, calculated[0], calculated[1], selection, params["margin"])
    if isinstance(result, str) or result is None or result.is_empty:
        raise ValueError("Legacy NCC margin failed")
    return result


def public_panelized_source(export, root):
    """Decode explicit ORIGINAL synthetic inputs, never the FX clearing area.

    WKT cannot encode a Python list. Invoke the original ToolPanelize handler
    to get the actual legacy container/order, as for independently read projects.
    """
    from shapely import wkt
    inputs = export["publicPanelizationInputs"]
    if not export.get("panelization"):
        raise ValueError("Public panel inputs require an explicit layout")
    return panelize_legacy_source(root,wkt.loads(inputs["sourceWkt"]),
                                 wkt.loads(inputs["referenceWkt"]),export["panelization"])


def gcode_metrics(source, parsed, tolerance):
    """Validate travel length, not the union's unstable length after XY rounding.

    Almost coincident passes can collapse to one line when exported to the
    controller's coordinate precision. Both passes still occur in the program.
    Keep the original spatial/length thresholds and unique-length diagnostics;
    compare travelled length with multiplicity so an omitted/repeated pass fails.
    CAM-vs-Python metrics intentionally retain their existing union semantics.
    """
    from shapely.ops import unary_union
    source_lines, parsed_lines = lines(source), lines(parsed)
    result = metrics(unary_union(source_lines), unary_union(parsed_lines), tolerance)
    source_length = sum(line.length for line in source_lines)
    parsed_length = sum(line.length for line in parsed_lines)
    delta = abs(source_length - parsed_length) / max(source_length, parsed_length)
    result.update(lengthCriterion="travel-with-multiplicity",
                  fxTravelLength=source_length, pythonParsedTravelLength=parsed_length,
                  travelRelativeLengthDelta=delta,
                  matchesSampledCriteria=delta <= result["relativeLengthTolerance"]
                      and result["boundsMaxDelta"] <= tolerance
                      and result["sampledDistance"] <= tolerance)
    return result


def classify_result(result, fx_failed_polygons=0):
    """Keep CAM path differences distinct from interpreted G-code differences."""
    result["camMatchesSampledCriteria"] = result["matchesSampledCriteria"] and result.get("clearingAreaMatches", True) and result.get("toolPathsMatch",True)
    result["gcodeMatchesSampledCriteria"] = result["pythonParsedFxCutComparison"]["matchesSampledCriteria"] and result.get("toolGcodeMatches",True)
    result["matchesSampledCriteria"] = result["camMatchesSampledCriteria"] and result["gcodeMatchesSampledCriteria"]
    if result.get("pythonFailedPolygons", 0) != fx_failed_polygons or not result.get("toolOrderMatches",True):
        result["matchesSampledCriteria"] = False
        return "PARTIAL_DIFFERENCE"
    if not result["camMatchesSampledCriteria"]:
        return "DIFFERENT"
    return "MATCH_SAMPLED" if result["gcodeMatchesSampledCriteria"] else "GCODE_DIFFERENT"


def compare_tool_outputs(fx_tools, python_tools, python_order, tolerance, parse_cut):
    """Check assignments individually: combined union must not hide a missing/swapped tool."""
    from shapely import wkt
    from shapely.geometry import GeometryCollection
    from shapely.ops import unary_union
    details = []
    for tool in fx_tools:
        source = wkt.loads(tool["fxWkt"])
        fx = unary_union(lines(source))
        python = python_tools.get(tool["diameter"],GeometryCollection())
        if fx.is_empty or python.is_empty:
            cam = {"fxEmpty":fx.is_empty,"pythonEmpty":python.is_empty,
                   "matchesSampledCriteria":fx.is_empty and python.is_empty}
        else:
            cam = metrics(fx,python,tolerance,tool["diameter"])
        gcode = ({"matchesSampledCriteria":True,"emptyTool":True} if fx.is_empty
                 else gcode_metrics(source,parse_cut(tool["gcode"]),tolerance))
        details.append({"diameter":tool["diameter"],"cam":cam,"gcode":gcode})
    fx_order = [tool["diameter"] for tool in fx_tools if not wkt.loads(tool["fxWkt"]).is_empty]
    # Extra Python output not assigned to an FX tool also fails.
    extra = [diameter for diameter,paths in python_tools.items()
             if not paths.is_empty and diameter not in {tool["diameter"] for tool in fx_tools}]
    return {"tools":details,"fxNonemptyToolOrder":fx_order,"pythonNonemptyToolOrder":python_order,
            "toolOrderMatches":fx_order == python_order,
            "toolPathsMatch":not extra and all(tool["cam"]["matchesSampledCriteria"] for tool in details),
            "toolGcodeMatches":all(tool["gcode"]["matchesSampledCriteria"] for tool in details),
            "extraPythonTools":extra}


def legacy_paths(case, copper, engine, root, handler_cache):
    from shapely import wkt
    from shapely.geometry import box
    from shapely.ops import unary_union
    params = case["parameters"]
    diameter = case["diameter"]
    operation = case["operation"]
    if operation == "ncc" and "tools" in params:
        return legacy_ncc_tools(case,engine,root,handler_cache)
    if operation == "isolation":
        results = []
        if params.get("follow"):
            results.extend(lines(engine.isolation_geometry(0, geometry=wkt.loads(case["inputWkt"]), follow=True)))
        for i in range(0 if params.get("follow") else params["passes"]):
            # ToolIsolation.py's normal (non-Rest) UI branch, including its epsilon.
            offset = diameter * ((2 * i + 1) / 2.0000001) - i * params["overlap"] * diameter
            results.extend(lines(engine.isolation_geometry(offset, geometry=copper, iso_type=params.get("isoType", 2))))
        if "exceptionWkt" in params:
            results = isolation_exception_paths(results, wkt.loads(params["exceptionWkt"]), root, handler_cache)
        return unary_union(results), {"oracle": "Geometry.isolation_geometry; original ToolIsolation.area_subtraction when requested"}
    if operation == "cutout":
        if params.get("shape") == "FREEFORM":
            if params.get("kind") != "PANEL" or params["margin"] < 0:
                raise ValueError("Free-form oracle currently covers Panel with nonnegative margin only")
            if "freeform" not in handler_cache:
                handler_cache["freeform"] = compile_rectangular_handler(root,freeform=True)
            namespace = handler_cache["freeform"]
            namespace.update(margin=params["margin"],gaps=params["gaps"])
            # Explicitly shared filled outline, not reconstructed from copper or a bounding box.
            source = wkt.loads(case["inputWkt"])
            parts = leaves(source) if params.get("kind") == "PANEL" else [source]
            output = []
            for part in parts:
                distance = params["margin"] + abs(diameter / 2) if params["margin"] >= 0 else params["margin"] - abs(diameter / 2)
                outline = part.buffer(distance).exterior
                cut, _ = namespace["cutout_handler"](outline,(params["gapSize"] + diameter) / 2)
                output.extend(lines(cut))
            return unary_union(output), {"oracle":"original nested free-form cutout_handler/helpers on explicitly shared filled board areas; positive/zero margin Gerber compensation"}
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
        # Actual ToolNCC selection/margin methods; no ISO, Rest or copper offset in these cases.
        area = ncc_boundary(case, copper, engine, root, handler_cache).difference(copper).buffer(0)
        fx_area = wkt.loads(params["fxClearingAreaWkt"])
        extra = {"clearingAreaSymmetricDifference": area.symmetric_difference(fx_area).area,
                 "clearingAreaMatches": area.symmetric_difference(fx_area).area <= max(area.area, fx_area.area) * 1e-8,
                 "oracle": "original NCC boundary/margin methods and clearing routine"}
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
    legacy_source = fx_copper
    source_delta = 0
    if export.get("panelization") and not args.project and not export.get("publicPanelizationInputs"):
        raise ValueError("Panelized comparison requires independent --project decoding")
    if export.get("publicPanelizationInputs") and args.project:
        raise ValueError("Do not mix public synthetic inputs and a private project")
    if export.get("publicPanelizationInputs"):
        legacy_source = public_panelized_source(export,root)
        copper = unary_union(leaves(legacy_source))
        source_delta = copper.symmetric_difference(fx_copper).area
        if source_delta > max(copper.area,1) * 1e-8:
            raise ValueError(f"Public panelized source differs before CAM: area delta {source_delta}")
    if args.project:
        raw = args.project.read_bytes()
        if raw.startswith(b"\xfd7zXZ"):
            raw = lzma.decompress(raw)
        project = json.loads(raw.decode("utf-8-sig"), object_hook=camlib.dict2obj)
        entry = next(obj for obj in project["objs"]
                     if obj["kind"] == "gerber" and obj["options"]["name"] == export["sourceName"])
        if entry["units"] != export["units"]:
            raise ValueError("Source units differ")
        legacy_source = entry["solid_geometry"]
        if export.get("panelization"):
            reference = next(obj for obj in project["objs"]
                             if obj["options"]["name"] == export["panelization"]["referenceName"])
            if reference["units"] != export["units"]:
                raise ValueError("Panel reference units differ")
            legacy_source = panelize_legacy_source(root, legacy_source, reference["solid_geometry"], export["panelization"])
        copper = unary_union(leaves(legacy_source))
        source_delta = copper.symmetric_difference(fx_copper).area
        if source_delta > max(copper.area, 1) * 1e-8:
            raise ValueError(f"Imported source differs before CAM: area delta {source_delta}")
    # Keep the actual object-layer container: Python's Itself branch behaves
    # differently for Polygon vs a one-item list of Polygon. Flattening here
    # would change the oracle instead of comparing the original operation.
    engine.solid_geometry = legacy_source
    tolerance = .003 / 25.4 if export["units"] in ("IN", "INCH") else .003
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"schema": 1, **dependency_metadata(),
              "source": export["sourceName"], "units": export["units"],
              "sourceAreaDelta": source_delta, "independentProjectDecode": bool(args.project),
              "legacySourceRepresentation": type(legacy_source).__name__,
              "legacySourceSha256": {str(path): hashlib.sha256((root / path).read_bytes()).hexdigest()
                                     for path in (Path("camlib.py"), Path("appTools/ToolCutOut.py"),
                                                  Path("appTools/ToolIsolation.py"), Path("appTools/ToolNCC.py"))},
              "scope": "Headless numerical sampling, not full UI/physical validation; no oracle patches",
              "selectedCases": [case["id"] for case in cases],
              "cases": []}
    if export.get("diagnosticControl"):
        report["diagnosticControl"] = export["diagnosticControl"]
        report["scope"] += "; DIAGNOSTIC CANDIDATE ONLY: production behavior is unchanged"
    if export.get("panelization"):
        report["panelization"] = export["panelization"]
        report["independentPanelization"] = True
        report["panelizationInputs"] = ("explicit original synthetic source/reference"
                                       if export.get("publicPanelizationInputs") else "independently decoded project")
        report["panelizationOracle"] = "original ToolPanelize.job_init_geometry solid-geometry branch; aperture copying/export stubbed"
        report["legacySourceSha256"]["appTools/ToolPanelize.py"] = hashlib.sha256((root / "appTools/ToolPanelize.py").read_bytes()).hexdigest()
    if export.get("comparisonScope"):
        report["scope"] += "; " + export["comparisonScope"]
    cards = []
    handler_cache = {}
    for case in cases:
        result = {"id": case["id"], "operation": case["operation"],
                  "fxDetailedPreviewAvailable": case["fxDetailedPreviewAvailable"],
                  "fxPreviewWarning": case["fxPreviewWarning"]}
        (args.output / (case["id"] + ".nc")).write_text(case["gcode"], encoding="utf-8")
        try:
            def parse_cut(program):
                cnc = camlib.CNCjob()
                # Metadata normally set by FlatCAMCNCJob, not the CAM base constructor.
                cnc.origin_kind = "geometry"
                cnc.gcode = program
                parsed = cnc.gcode_parse()
                if parsed == "fail" or not parsed:
                    raise ValueError("Legacy CNC parser rejected FX G-code")
                from shapely.geometry import GeometryCollection
                # Preserve repeated/overlapping passes for export fidelity.
                # gcode_metrics performs a separate union for spatial coverage.
                cut = GeometryCollection([step["geom"] for step in parsed if step["kind"][0] == "C"])
                if cut.is_empty or cut.length <= 0:
                    raise ValueError("Legacy CNC parser found no cutting motion")
                return cut
            cut = parse_cut(case["gcode"])
            result["pythonParsedFxGcode"] = True
            result["pythonParsedCutLength"] = cut.length
            python, extra = legacy_paths(case, copper, engine, root, handler_cache)
            python_tools = extra.pop("_toolPaths",None)
            if python_tools is not None:
                extra.update(compare_tool_outputs(case["parameters"]["fxToolResults"],python_tools,
                    extra["pythonNonemptyToolOrder"],tolerance,parse_cut))
            fx = unary_union(lines(wkt.loads(case["fxWkt"])))
            if not python.is_valid or not fx.is_valid:
                raise ValueError("Invalid output")
            result.update(extra)
            (args.output / (case["id"] + ".python.wkt")).write_text(python.wkt, encoding="utf-8")
            (args.output / (case["id"] + ".fx.wkt")).write_text(fx.wkt, encoding="utf-8")
            result.update(metrics(fx, python, tolerance, case["diameter"]))
            if "fxToolResults" in case["parameters"]:
                from shapely.geometry import GeometryCollection
                source_paths = GeometryCollection([wkt.loads(tool["fxWkt"])
                                for tool in case["parameters"]["fxToolResults"]])
            else:
                source_paths = wkt.loads(case["fxWkt"])
            result["pythonParsedFxCutComparison"] = gcode_metrics(source_paths, cut, tolerance)
            result["status"] = classify_result(result, case["parameters"].get("fxFailedPolygons", 0))
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
              for status in ("MATCH_SAMPLED", "DIFFERENT", "GCODE_DIFFERENT", "PARTIAL_DIFFERENCE", "ORACLE_ERROR")}
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
                + (f'<p><strong>CONTROLE DIAGNÓSTICO — não é resultado da implementação em produção:</strong> '
                   f'{html.escape(str(export["diagnosticControl"]))}</p>' if export.get("diagnosticControl") else '')
                +
                f'<p>{html.escape(report["scope"])}</p><p>{html.escape(json.dumps(counts))}</p>' + "".join(cards) + '</html>')
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
