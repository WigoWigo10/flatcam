#!/usr/bin/env python3
"""Compare FX Rules Check, Copper Thieving and Calibration results with the original FlatCAM Python routines.

Headless: the original method bodies are compiled from the legacy checkout's AST and run with explicit stand-ins for
the UI only. No algorithm is patched, no interface is started and no preference is written. Inputs come, in this
order of preference, from the legacy parsers (synthetic Gerber/Excellon text), from an independent decode of the
legacy project (--project), or from the geometry the FX exported (reported as "fx-shared").
"""
import argparse
import ast
import html
import json
import logging
import lzma
import math
import re
import sys
import time
import traceback
from pathlib import Path
from types import SimpleNamespace

RULE_FUNCTIONS = ("check_inside_gerber_clearance", "check_gerber_clearance", "check_holes_size",
                  "check_holes_clearance", "check_traces_size", "check_gerber_annular_ring")
FILL = {"SOLID": "solid", "DOT": "dot", "SQUARE": "square", "LINE": "line"}
REFERENCE = {"ITSELF": "itself", "AREA": "area", "BOX": "box"}
APERTURE_TYPE = {"CIRCLE": "C", "RECTANGLE": "R", "OBROUND": "O", "POLYGON": "P", "MACRO": "AM", "REGION": "REG"}


class Anything:
    """Stands in for UI/application objects whose calls only report progress or open windows."""

    def __getattr__(self, name):
        return Anything()

    def __call__(self, *args, **kwargs):
        return Anything()


class Value:
    """A UI entry: the routine reads or writes one value."""

    def __init__(self, value=None):
        self.value = value

    def get_value(self):
        return self.value

    def set_value(self, value):
        self.value = value


def compile_methods(root, filename, class_name, names, namespace):
    """Compile ORIGINAL method bodies as plain functions; `self` is bound by the caller."""
    source = root / "appTools" / filename
    tree = ast.parse(source.read_text(encoding="utf-8-sig"))
    cls = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == class_name)
    methods = [node for node in cls.body if isinstance(node, ast.FunctionDef) and node.name in names]
    if {node.name for node in methods} != set(names):
        raise ValueError("Legacy method layout changed: " + filename)
    for node in methods:
        node.decorator_list = []
    namespace = dict(namespace)
    namespace.setdefault("log", logging.getLogger("flatcam.comparison"))
    namespace.setdefault("_", lambda text: text)
    exec(compile(ast.Module(body=methods, type_ignores=[]), str(source), "exec"), namespace)
    return namespace


def leaves(geometry):
    if geometry is None:
        return []
    if isinstance(geometry, (list, tuple)):
        return [leaf for item in geometry for leaf in leaves(item)]
    if hasattr(geometry, "geoms"):
        return [leaf for item in geometry.geoms for leaf in leaves(item)]
    return [] if geometry.is_empty else [geometry]


def unary_pieces(tables):
    """The separate pieces of one or more aperture tables merged together, as the legacy rules merge them."""
    from shapely.ops import unary_union
    return leaves(unary_union([element["solid"] for apertures in tables for aperture in apertures.values()
                               for element in aperture.get("geometry", []) if element.get("solid") is not None]))


def area_metrics(fx, python, relative_tolerance=1e-3):
    from shapely.ops import unary_union
    fx_union = unary_union(leaves(fx))
    python_union = unary_union(leaves(python))
    reference = max(fx_union.area, python_union.area, 1e-12)
    delta = fx_union.symmetric_difference(python_union).area
    return dict(fxArea=fx_union.area, pythonArea=python_union.area, symmetricDifferenceArea=delta,
                relativeSymmetricDifference=delta / reference, relativeTolerance=relative_tolerance,
                matches=delta / reference <= relative_tolerance)


def point_metrics(fx, python, tolerance):
    """Unique points; every point of one side must have one of the other within the tolerance."""
    def unique(points):
        return sorted({(round(x, 6), round(y, 6)) for x, y in points})

    def reach(a, b):
        return max((min(math.hypot(x - u, y - v) for u, v in b) for x, y in a), default=0.0) if b else (
            math.inf if a else 0.0)

    fx, python = unique(fx), unique(python)
    distance = max(reach(fx, python), reach(python, fx))
    return dict(fxPoints=len(fx), pythonPoints=len(python), maxPointDistance=None if math.isinf(distance) else distance,
                pointTolerance=tolerance, matches=distance <= tolerance)


def witness_pairs(points, groups):
    """
    Which pair of pieces each violation point sits between: the two nearest pieces of one group, or the nearest piece
    of each of two groups. Two engines may report different points of the same gap (facing parallel edges have no
    single closest point), so violations are compared by the pieces involved, not by the coordinates.
    """
    from shapely.geometry import Point
    pairs = set()
    for x, y in points:
        point = Point(x, y)
        if len(groups) == 1:
            order = sorted(range(len(groups[0])), key=lambda i: groups[0][i].distance(point))
            pairs.add(tuple(sorted(order[:2])))
        else:
            pairs.add(tuple(min(range(len(group)), key=lambda i: group[i].distance(point)) for group in groups))
    return pairs


class Oracle:
    def __init__(self, args, export):
        self.root = args.legacy_root.resolve()
        sys.path.insert(0, str(self.root))
        sys.path.insert(0, str(self.root / "tests"))
        if args.dependency_path:
            sys.path.insert(0, str(args.dependency_path.resolve()))
        import camlib
        from parser_baseline import install_stub_app
        self.camlib = camlib
        self.app = install_stub_app()
        camlib.log.setLevel(logging.WARNING)
        self.export = export
        self.objects = {}
        self.project = None
        if args.project:
            raw = args.project.read_bytes()
            if raw.startswith(b"\xfd7zXZ"):
                raw = lzma.decompress(raw)
            self.project = json.loads(raw.decode("utf-8-sig"), object_hook=camlib.dict2obj)
        self.compiled = {}
        self.thieving = {}
        self.flatten_containers = False

    # --- inputs ------------------------------------------------------------------------------------------------

    def legacy_object(self, key):
        """(object as the legacy tools see it, where it came from)."""
        if key in self.objects:
            return self.objects[key]
        from shapely import wkt
        from shapely.geometry import LineString, Point
        spec = self.export["objects"][key]
        name = spec["name"]
        if "source" in spec:
            lines = spec["source"].split("\n")
            if spec["type"] == "gerber":
                from appParsers.ParseGerber import Gerber
                parsed = Gerber()
                parsed.parse_lines(lines)
                result = SimpleNamespace(kind="gerber", options={"name": name}, apertures=parsed.apertures,
                                         solid_geometry=parsed.solid_geometry, follow=False,
                                         follow_geometry=list(leaves(parsed.follow_geometry)))
            else:
                from appParsers.ParseExcellon import Excellon
                parsed = Excellon()
                # The application object supplies this attribute; a bare parser needs it to build hole polygons.
                parsed.default_data = {}
                parsed.parse_lines(lines)
                parsed.create_geometry()
                result = SimpleNamespace(kind="excellon", options={"name": name}, tools=parsed.tools)
            origin = "python-parser"
        elif self.project is not None:
            entry = next(obj for obj in self.project["objs"] if obj["options"]["name"] == name)
            if spec["type"] == "gerber":
                result = SimpleNamespace(kind="gerber", options={"name": name}, apertures=entry["apertures"],
                                         solid_geometry=entry["solid_geometry"], follow=False,
                                         follow_geometry=list(leaves(entry.get("follow_geometry"))))
            else:
                result = SimpleNamespace(kind="excellon", options={"name": name}, tools=entry["tools"])
            origin = "python-project"
        else:
            if spec["type"] == "gerber":
                apertures = {}
                for code, aperture in spec["apertures"].items():
                    table = {"type": APERTURE_TYPE[aperture["kind"]],
                             "geometry": [{k: wkt.loads(v) for k, v in element.items()}
                                          for element in aperture["geometry"]]}
                    if "size" in aperture:
                        table["size"] = aperture["size"]
                    apertures[code] = table
                result = SimpleNamespace(kind="gerber", options={"name": name}, apertures=apertures,
                                         solid_geometry=leaves(wkt.loads(spec["solidWkt"])), follow=False,
                                         follow_geometry=[])
            else:
                tools = {}
                for tool, data in spec["tools"].items():
                    radius = data["diameter"] / 2
                    solid = [Point(x, y).buffer(radius, 16) for x, y in data["drills"]]
                    solid += [LineString([(a, b), (c, d)]).buffer(radius, 16) for a, b, c, d in data["slots"]]
                    tools[tool] = {"tooldia": data["diameter"], "solid_geometry": solid}
                result = SimpleNamespace(kind="excellon", options={"name": name}, tools=tools)
            origin = "fx-shared"
        self.objects[key] = (result, origin)
        return self.objects[key]

    def namespace(self, key, builder):
        if key not in self.compiled:
            self.compiled[key] = builder()
        return self.compiled[key]

    # --- rules check -------------------------------------------------------------------------------------------

    def rules(self, case, tolerance):
        from copy import deepcopy
        from shapely.geometry import MultiPolygon, Polygon
        from shapely.ops import nearest_points
        namespace = self.namespace("rules", lambda: compile_methods(
            self.root, "ToolRulesCheck.py", "RulesCheck", RULE_FUNCTIONS,
            dict(MultiPolygon=MultiPolygon, Polygon=Polygon, nearest_points=nearest_points, deepcopy=deepcopy)))
        origins = []
        gerbers = []
        for key in case["gerbers"]:
            obj, origin = self.legacy_object(key)
            origins.append(origin)
            gerbers.append({"name": obj.options["name"], "apertures": deepcopy(obj.apertures)})
        excellons = []
        for key in case["excellons"]:
            obj, origin = self.legacy_object(key)
            origins.append(origin)
            excellons.append({"name": obj.options["name"], "tools": deepcopy(obj.tools)})
        function = namespace[case["function"]]
        limit = case["limit"]
        title = case["rule"]
        started = time.perf_counter()
        if case["function"] == "check_inside_gerber_clearance":
            answer = function(gerbers[0], limit, title)
        elif case["function"] == "check_gerber_clearance":
            answer = function(gerbers, limit, title)
        elif case["function"] == "check_gerber_annular_ring":
            answer = function(gerbers + excellons, limit, title)
        elif case["function"] == "check_traces_size":
            answer = function(gerbers, limit)
        else:
            answer = function(excellons, limit)
        elapsed = (time.perf_counter() - started) * 1000
        if isinstance(answer, str):
            raise ValueError("Legacy rule refused the inputs: " + answer)
        violations = answer[1]
        points, sizes, verdict = [], [], ""
        for violation in violations:
            for point in violation.get("points", []):
                if isinstance(point, str):
                    verdict = point
                elif hasattr(point, "x"):
                    points.append((point.x, point.y))
                else:
                    points.append((float(point[0]), float(point[1])))
            sizes += [float(value) for value in violation.get("size", [])]
            sizes += [float(value) for value in violation.get("dia", [])]
        fx_points = [tuple(p) for p in case["fxPoints"]]
        result = point_metrics(fx_points, points, tolerance)
        if case["function"] != "check_traces_size":
            # Classification only: the pieces as the legacy rule merges them, to name each violation's pair.
            solids = [unary_pieces([g["apertures"]]) for g in gerbers]
            holes = [geo for e in excellons for tool in e["tools"].values() for geo in tool.get("solid_geometry", [])]
            if case["function"] == "check_inside_gerber_clearance":
                groups = [solids[0]]
            elif case["function"] == "check_gerber_clearance":
                groups = [unary_pieces([g["apertures"] for g in gerbers[:-1]]), solids[-1]]
            elif case["function"] == "check_gerber_annular_ring":
                groups = [unary_pieces([g["apertures"] for g in gerbers]), holes]
            else:
                groups = [holes]
            fx_pairs, python_pairs = witness_pairs(fx_points, groups), witness_pairs(points, groups)
            result.update(fxViolations=len(fx_pairs), pythonViolations=len(python_pairs),
                          violationsOnlyInFx=len(fx_pairs - python_pairs),
                          violationsOnlyInPython=len(python_pairs - fx_pairs), matches=fx_pairs == python_pairs)
        fx_sizes = sorted({round(value, 4) for value in case["fxSizes"]})
        python_sizes = sorted({round(value, 4) for value in sizes})
        result.update(fxSizes=fx_sizes, pythonSizes=python_sizes, pythonVerdict=verdict, fxNote=case.get("fxNote", ""),
                      pythonMs=elapsed, inputs=sorted(set(origins)))
        result["matches"] = result["matches"] and fx_sizes == python_sizes and not verdict
        return result

    # --- copper thieving ---------------------------------------------------------------------------------------

    def thieving_namespace(self):
        def build():
            from collections.abc import Iterable
            from copy import deepcopy
            import numpy as np
            import shapely.affinity as affinity
            import shapely.geometry.base as base
            from shapely.geometry import LineString, MultiPolygon, Point, Polygon, box
            from shapely.ops import unary_union
            from camlib import grace
            return compile_methods(
                self.root, "ToolCopperThieving.py", "ToolCopperThieving",
                ("copper_thieving", "flatten", "on_add_robber_bar_click", "on_new_pattern_plating_object"),
                dict(QtWidgets=Anything(), np=np, deepcopy=deepcopy, Iterable=Iterable, MultiPolygon=MultiPolygon,
                     Polygon=Polygon, Point=Point, LineString=LineString, box=box, base=base, affinity=affinity,
                     unary_union=unary_union, grace=grace))
        return self.namespace("thieving", build)

    def thieving_binding(self, namespace, target, parameters=None, mask=None, **entries):
        app = Anything()
        app.abort_flag = False
        messages = []
        app.inform = SimpleNamespace(emit=lambda text, *a, **k: messages.append(str(text)))
        selected = {"grb": target, "sm": mask}
        index = SimpleNamespace()
        app.collection = SimpleNamespace(index=lambda row, column, parent: SimpleNamespace(
            internalPointer=lambda: SimpleNamespace(obj=selected[parent])))
        ui = Anything()
        ui.grb_object_combo = SimpleNamespace(currentIndex=lambda: 0, rootModelIndex=lambda: "grb",
                                              currentText=lambda: target.options["name"])
        ui.sm_object_combo = SimpleNamespace(currentIndex=lambda: 0, rootModelIndex=lambda: "sm")
        values = dict(reference_radio="itself", bbox_type_radio="rect", area_entry=0.0, fill_type_radio="solid",
                      dot_dia_entry=1.0, dot_spacing_entry=2.0, square_size_entry=1.0, squares_spacing_entry=2.0,
                      line_size_entry=0.25, lines_spacing_entry=2.0, rb_margin_entry=1.0, rb_thickness_entry=1.0,
                      clearance_ppm_entry=0.0, ppm_choice_radio="b", plated_area_entry=None)
        if parameters:
            values.update(reference_radio=REFERENCE[parameters["reference"]],
                          bbox_type_radio="min" if parameters["boxType"] == "MINIMAL" else "rect",
                          area_entry=parameters["minArea"], fill_type_radio=FILL[parameters["fill"]],
                          dot_dia_entry=parameters["dotDiameter"], dot_spacing_entry=parameters["dotSpacing"],
                          square_size_entry=parameters["squareSize"], squares_spacing_entry=parameters["squareSpacing"],
                          line_size_entry=parameters["lineSize"], lines_spacing_entry=parameters["lineSpacing"])
        values.update(entries)
        for name, value in values.items():
            setattr(ui, name, Value(value))
        binding = SimpleNamespace(app=app, ui=ui, grb_object=target, sm_object=None, ref_obj=None, sel_rect=[],
                                  flat_geometry=[], thief_solid_geometry=[], robber_geo=None, robber_line=None,
                                  rb_thickness=None, geo_steps_per_circle=64, on_exit=lambda *a, **k: None,
                                  messages=messages)
        binding.flatten = lambda geometry: namespace["flatten"](binding, geometry)
        del index
        return binding

    def fresh(self, obj):
        """The tools replace attributes of the object they work on: give each run its own copy."""
        from copy import deepcopy
        copy = SimpleNamespace(**{name: deepcopy(value) for name, value in vars(obj).items()})
        if self.flatten_containers and hasattr(copy, "solid_geometry"):
            copy.solid_geometry = leaves(copy.solid_geometry)
        return copy

    @staticmethod
    def legacy_errors(binding):
        """The legacy tools report a failure to the status bar and return; that is not a result."""
        errors = [text for text in binding.messages if "[ERROR" in text]
        if errors:
            raise ValueError("Legacy tool reported: " + "; ".join(errors))

    def with_legacy_container(self, run):
        """
        Runs with the object exactly as the legacy project stores it. A reopened project keeps a Gerber's geometry as a
        list holding one MultiPolygon, which these legacy tools cannot process; when that fails, the same geometry is
        given as a flat list of polygons (what the legacy application holds after parsing a file) and the report says so.
        """
        self.flatten_containers = False
        try:
            return run()
        except Exception as error:
            self.flatten_containers = True
            try:
                result = run()
            finally:
                self.flatten_containers = False
            result["inputs"] = [origin + "-flattened" if origin == "python-project" else origin
                                for origin in result["inputs"]]
            if not any(origin.endswith("-flattened") for origin in result["inputs"]):
                raise error
            result["legacyContainerError"] = "%s: %s" % (type(error).__name__, error)
            return result

    def run_thieving(self, case):
        from collections.abc import Iterable
        from shapely import wkt
        from shapely.ops import unary_union
        namespace = self.thieving_namespace()
        parameters = case["parameters"]
        source, origin = self.legacy_object(case["gerber"])
        target = self.fresh(source)
        binding = self.thieving_binding(namespace, target, parameters)
        binding.geo_steps_per_circle = parameters["circleSteps"]
        reference = None
        if parameters["reference"] == "AREA":
            # As on_mouse_release: the drawn rectangles are merged and kept as an iterable.
            reference = unary_union(leaves(wkt.loads(case["zonesWkt"])))
            if not isinstance(reference, Iterable):
                reference = [reference]
        elif parameters["reference"] == "BOX":
            reference = self.fresh(self.legacy_object(case["referenceGerber"])[0])
        started = time.perf_counter()
        namespace["copper_thieving"](binding, thieving_obj=target, ref_obj=reference, c_val=parameters["clearance"],
                                     margin=parameters["margin"], run_threaded=False)
        elapsed = (time.perf_counter() - started) * 1000
        self.legacy_errors(binding)
        return binding, origin, elapsed

    def thieving_case(self, case):
        from shapely import wkt
        binding, origin, elapsed = self.run_thieving(case)
        python = leaves(binding.thief_solid_geometry)
        self.thieving[case["id"]] = python
        result = area_metrics(wkt.loads(case["fxWkt"]), python)
        result.update(fxCount=case["fxCount"], pythonCount=len(python), pythonMs=elapsed, inputs=[origin])
        if case["parameters"]["fill"] in ("DOT", "SQUARE"):
            result["matches"] = result["matches"] and case["fxCount"] == len(python)
        return result

    def robber(self, case):
        from shapely import wkt
        namespace = self.thieving_namespace()
        source, origin = self.legacy_object(case["gerber"])
        binding = self.thieving_binding(namespace, self.fresh(source), rb_margin_entry=case["margin"],
                                        rb_thickness_entry=case["thickness"])
        started = time.perf_counter()
        namespace["on_add_robber_bar_click"](binding)
        elapsed = (time.perf_counter() - started) * 1000
        self.legacy_errors(binding)
        result = area_metrics(wkt.loads(case["fxWkt"]), binding.robber_geo)
        result.update(pythonMs=elapsed, inputs=[origin])
        return result

    def plating(self, case, by_id):
        from shapely import wkt
        namespace = self.thieving_namespace()
        thieving, origin, _ = self.run_thieving(by_id[case["thievingCase"]])
        mask, mask_origin = self.legacy_object(case["mask"])
        binding = self.thieving_binding(namespace, thieving.grb_object, mask=self.fresh(mask),
                                        rb_margin_entry=case["margin"], rb_thickness_entry=case["thickness"],
                                        clearance_ppm_entry=case["clearance"])
        binding.thief_solid_geometry = thieving.thief_solid_geometry
        namespace["on_add_robber_bar_click"](binding)
        captured = {}
        binding.app.app_obj = SimpleNamespace(new_object=lambda kind, name, init, **kw: captured.update(
            name=name, initializer=init))
        started = time.perf_counter()
        namespace["on_new_pattern_plating_object"](binding)
        elapsed = (time.perf_counter() - started) * 1000
        self.legacy_errors(binding)
        area = binding.ui.plated_area_entry.get_value()
        # The solid geometry is a local of the legacy routine captured by its object initializer.
        cells = dict(zip(captured["initializer"].__code__.co_freevars,
                         (cell.cell_contents for cell in captured["initializer"].__closure__)))
        result = area_metrics(wkt.loads(case["fxWkt"]), cells["new_solid_geometry"])
        relative = abs(area - case["fxArea"]) / max(abs(area), 1e-12)
        result.update(fxPlatedArea=case["fxArea"], pythonPlatedArea=area, relativePlatedAreaDelta=relative,
                      pythonMs=elapsed, inputs=sorted({origin, mask_origin}), pythonName=captured["name"])
        result["matches"] = result["matches"] and relative <= 1e-3
        return result

    # --- calibration -------------------------------------------------------------------------------------------

    def calibration_namespace(self):
        def build():
            import math as math_module
            from datetime import datetime
            from shapely.affinity import scale, skew
            from shapely.geometry import Point

            class Editor(Anything):
                last = None

                def __init__(self, *args, **kwargs):
                    Editor.last = self

                def load_text(self, text, **kwargs):
                    self.text = text

            namespace = compile_methods(self.root, "ToolCalibration.py", "ToolCalibration",
                                        ("calculate_factors", "gcode_header", "generate_verification_gcode"),
                                        dict(math=math_module, datetime=datetime, scale=scale, skew=skew, Point=Point,
                                             AppTextEditor=Editor))
            namespace["Editor"] = Editor
            return namespace
        return self.namespace("calibration", build)

    def factors(self, case):
        namespace = self.calibration_namespace()
        ui = Anything()
        deltas = dict(bottom_right_coordx_found=case["bottomRightDelta"][0],
                      bottom_right_coordy_found=case["bottomRightDelta"][1],
                      top_left_coordx_found=case["topLeftDelta"][0], top_left_coordy_found=case["topLeftDelta"][1])
        for name, value in deltas.items():
            # An empty table cell: the legacy entry gives None and the routine falls back to the target value.
            setattr(ui, name, Value(None if value == 0 else value))
        for name, value in dict(scalex_entry=1.0, scaley_entry=1.0, skewx_entry=0.0, skewy_entry=0.0).items():
            setattr(ui, name, Value(value))
        binding = SimpleNamespace(ui=ui, decimals=4, click_points=[[float(v) for v in point] for point in case["points"]])
        namespace["calculate_factors"](binding)
        python = [ui.scalex_entry.value, ui.scaley_entry.value, ui.skewx_entry.value, ui.skewy_entry.value]
        fx = [case["fxScaleX"], case["fxScaleY"], case["fxSkewX"], case["fxSkewY"]]
        delta = max(abs(a - b) for a, b in zip(fx, python))
        return dict(fxFactors=fx, pythonFactors=python, maxFactorDelta=delta, factorTolerance=1e-9,
                    matches=delta <= 1e-9, inputs=["case"])

    def gcode(self, case):
        namespace = self.calibration_namespace()
        ui = Anything()
        xy = case.get("toolChangeXY")
        entries = dict(second_point_radio="tl" if case["secondIsTopLeft"] else "br", travelz_entry=case["travelZ"],
                       toolchangez_entry=case["toolChangeZ"], verz_entry=case["verificationZ"],
                       toolchange_xy_entry="" if xy is None else "%r, %r" % (float(xy[0]), float(xy[1])),
                       zeroz_cb=case["zeroZ"])
        for name, value in entries.items():
            setattr(ui, name, Value(value))
        app = Anything()
        app.version = "comparison"
        app.version_date = "comparison"
        binding = SimpleNamespace(ui=ui, app=app, decimals=4, units="IN" if case["inches"] else "MM",
                                  click_points=[[float(v) for v in point] for point in case["points"]],
                                  close_tab=lambda: None)
        binding.gcode_header = lambda: namespace["gcode_header"](binding)
        namespace["generate_verification_gcode"](binding)

        def commands(text):
            return [line.strip() for line in text.splitlines() if line.strip() and not line.strip().startswith("(")]

        fx, python = commands(case["fxGcode"]), commands(namespace["Editor"].last.text)
        first = next((i for i, (a, b) in enumerate(zip(fx, python)) if a != b), None)
        if first is None and len(fx) != len(python):
            first = min(len(fx), len(python))
        return dict(fxCommands=len(fx), pythonCommands=len(python), matches=fx == python,
                    firstDifference=None if first is None else dict(
                        line=first, fx=fx[first] if first < len(fx) else None,
                        python=python[first] if first < len(python) else None), inputs=["case"])

    def calibrated_object(self, case):
        from shapely import wkt
        from appParsers.ParseGerber import Gerber
        source, origin = self.legacy_object(case["gerber"])
        # The two parsers already differ slightly (arc segments); the transform must not add to that.
        baseline = area_metrics(wkt.loads(self.export["objects"][case["gerber"]]["solidWkt"]), source.solid_geometry)
        target = Gerber()
        target.solid_geometry = list(leaves(source.solid_geometry))
        target.follow_geometry = []
        target.apertures = {}
        started = time.perf_counter()
        target.scale(case["scaleX"], case["scaleY"], point=tuple(case["origin"]))
        target.skew(case["skewX"], case["skewY"], point=tuple(case["origin"]))
        elapsed = (time.perf_counter() - started) * 1000
        result = area_metrics(wkt.loads(case["fxWkt"]), target.solid_geometry)
        added = result["relativeSymmetricDifference"] - baseline["relativeSymmetricDifference"]
        result.update(sourceRelativeSymmetricDifference=baseline["relativeSymmetricDifference"],
                      addedByTransform=added, pythonMs=elapsed, inputs=[origin])
        result["matches"] = result["matches"] and added <= 1e-6
        return result


def run(args):
    export = json.loads(args.fx_export.read_text(encoding="utf-8"))
    if export["schema"] != 1:
        raise ValueError("Unsupported FX export schema")
    cases = export["cases"]
    identifiers = [case["id"] for case in cases]
    if len(set(identifiers)) != len(identifiers) or any(
            not re.fullmatch(r"[a-z0-9-]{1,64}", name) for name in identifiers):
        raise ValueError("Invalid or duplicate case identifiers")
    by_id = {case["id"]: case for case in cases}
    if args.cases:
        requested = set(args.cases.split(","))
        if not requested.issubset(identifiers):
            raise ValueError("Unknown requested case identifiers")
        cases = [case for case in cases if case["id"] in requested]
    oracle = Oracle(args, export)
    import shapely
    from shapely.geos import geos_version_string
    tolerance = 0.005 if export["units"] == "MM" else 0.0002
    results = []
    for case in cases:
        result = {"id": case["id"], "tool": case["tool"]}
        try:
            if case["tool"] == "rules":
                result.update(rule=case["rule"], limit=case["limit"], function=case["function"])
                result.update(oracle.rules(case, tolerance))
            elif case["tool"] == "thieving":
                result.update(oracle.with_legacy_container(lambda: oracle.thieving_case(case)))
            elif case["tool"] == "robber":
                result.update(oracle.with_legacy_container(lambda: oracle.robber(case)))
            elif case["tool"] == "plating":
                result.update(oracle.with_legacy_container(lambda: oracle.plating(case, by_id)))
            elif case["tool"] == "calibration-factors":
                result.update(oracle.factors(case))
            elif case["tool"] == "calibration-gcode":
                result.update(oracle.gcode(case))
            elif case["tool"] == "calibration-object":
                result.update(oracle.calibrated_object(case))
            else:
                raise ValueError("Unknown tool: " + case["tool"])
            if result.pop("matches"):
                result["status"] = "MATCH"
            else:
                result["status"] = "DOCUMENTED_DIFFERENCE" if case.get("documentedDifference") else "DIFFERENT"
        except Exception as error:  # A legacy failure is reported, never counted as agreement.
            result.update(status="ORACLE_ERROR", error="%s: %s" % (type(error).__name__, error),
                          trace=traceback.format_exc(limit=6))
        results.append(result)
    counts = {status: sum(1 for r in results if r["status"] == status)
              for status in ("MATCH", "DIFFERENT", "DOCUMENTED_DIFFERENCE", "ORACLE_ERROR")}
    report = dict(schema=1, python=sys.version.split()[0], shapely=shapely.__version__, geos=geos_version_string,
                  units=export["units"], independentProjectDecode=oracle.project is not None,
                  scope="original Python routines compiled from the legacy checkout; UI stand-ins only",
                  counts=counts, cases=results)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "report.json").write_text(json.dumps(report, indent=1, default=str), encoding="utf-8")
    rows = "".join(
        "<tr class='%s'><td>%s</td><td>%s</td><td>%s</td><td><code>%s</code></td></tr>" % (
            r["status"], html.escape(r["id"]), html.escape(r["tool"]), r["status"],
            html.escape(json.dumps({k: v for k, v in r.items() if k not in ("id", "tool", "status", "trace")},
                                   default=str)))
        for r in results)
    (args.output / "index.html").write_text(
        "<!doctype html><meta charset='utf-8'><title>FX x Python: ferramentas</title>"
        "<style>body{font-family:sans-serif}td{border-bottom:1px solid #ccc;padding:4px;vertical-align:top}"
        ".DIFFERENT td,.ORACLE_ERROR td{background:#fde8e8}.DOCUMENTED_DIFFERENCE td{background:#fff6d6}"
        "code{font-size:11px;word-break:break-all}</style>"
        "<h1>FX x Python: Rules Check, Copper Thieving, Calibration</h1><p>%s</p><table>%s</table>" % (
            html.escape(json.dumps(counts)), rows), encoding="utf-8")
    print(json.dumps({"counts": counts, "report": str(args.output / "index.html")}))
    return 1 if args.strict and (counts["DIFFERENT"] or counts["ORACLE_ERROR"]) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("--fx-export", type=Path, required=True)
    parser.add_argument("--project", type=Path, help="Independently decode the untouched legacy project")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dependency-path", type=Path,
                        help="Optional isolated dependency directory; does not alter the active environment")
    parser.add_argument("--cases", help="Optional comma-separated case IDs for focused diagnosis")
    parser.add_argument("--strict", action="store_true",
                        help="Fail on every undocumented mismatch or legacy error")
    args = parser.parse_args()
    raise SystemExit(run(args))


if __name__ == "__main__":
    main()
