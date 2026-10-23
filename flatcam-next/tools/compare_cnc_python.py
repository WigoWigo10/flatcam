"""Compare executed XYZ/F/S/T state with the ORIGINAL Python CNC generators.

This is a deliberately narrow, profile-aware XYZ milling reader, not a machine simulator.
Unknown commands, swallowed preprocessor errors and missing cuts fail closed.
The oracle's generators/optimizers/preprocessor/exclusion routing are not patched.
"""
import argparse
import ast
import hashlib
import html
import json
import logging
import math
import re
import sys
from pathlib import Path
from types import SimpleNamespace, MethodType


WORD = re.compile(r"([A-Z])\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))")
SUPPORTED_PROFILES = ("GRBL_11_no_M6", "grbl_11", "default", "Default_no_M6", "Marlin", "line_xyz", "ISEL_CNC")


def read_program(program, profile="GRBL_11_no_M6", *, marlin_rapid_mode="shared", marlin_fixed_rapid_feed=None):
    """Profile-scoped modal subset; retain cuts, pauses, synchronization and feeds."""
    if profile not in SUPPORTED_PROFILES:
        raise ValueError("Profile outside the XYZ comparison reader")
    if marlin_rapid_mode not in ("shared", "variable", "fixed"):
        raise ValueError("Unknown Marlin G0 feed configuration")
    if marlin_rapid_mode == "fixed" and (marlin_fixed_rapid_feed is None
            or not math.isfinite(marlin_fixed_rapid_feed) or marlin_fixed_rapid_feed <= 0):
        raise ValueError("Fixed Marlin G0 requires a positive feed in source units")
    if marlin_rapid_mode != "fixed" and marlin_fixed_rapid_feed is not None:
        raise ValueError("Fixed G0 feed is only valid in fixed mode")
    xyz = [0.0, 0.0, 0.0]
    feed = None
    rapid_feed = None  # VARIABLE_G0_FEEDRATE initial firmware default is deliberately unknown.
    rpm = 0.0
    spindle = 0
    tool = 0
    motion = None
    units = None
    absolute = False
    # Marlin's emitted dialect has native units/min and no G94 in its original
    # header. This is an explicit profile assumption, not an inserted command.
    feed_per_minute = profile == "Marlin"
    events, cuts = [], []
    terminated = False
    spindle_stopped = False
    for number, raw in enumerate(program.splitlines(), 1):
        line = re.sub(r"\([^()]*\)", "", raw.upper()).split(";", 1)[0].strip()
        if not line:
            continue
        if terminated:
            raise ValueError("Commands after program termination")
        if WORD.sub("", line).strip():
            raise ValueError(f"Unsupported syntax on line {number}: {raw}")
        words = WORD.findall(line)
        if any(letter not in "GMXYZFSTP" for letter, _ in words):
            raise ValueError(f"Unsupported word on line {number}")
        if any(sum(letter == item[0] for item in words) > 1 for letter in "XYZFSTP"):
            raise ValueError(f"Duplicate word on line {number}")
        values = {key: float(value) for key, value in words if key not in "GM"}
        if any(not math.isfinite(value) for value in values.values()):
            raise ValueError("Nonfinite G-code value")
        previous_feed = feed
        if "F" in values:
            feed = values["F"]
            if feed <= 0:
                raise ValueError("Nonpositive feed")
        if "S" in values:
            rpm = values["S"]
            if rpm < 0:
                raise ValueError("Negative spindle speed")
        dwell = False
        for letter, value in words:
            if letter != "G":
                continue
            code = float(value)
            if code in (0, 1):
                motion = int(code)
            elif code == 4:
                dwell = True
            elif code in (20, 21) or code == 71 and profile == "ISEL_CNC":
                if profile == "ISEL_CNC" and code == 20:
                    raise ValueError("ISEL_CNC comparison supports MM only")
                selected = "IN" if code == 20 else "MM"
                if units is not None and selected != units:
                    raise ValueError("Unit change inside the program is outside this reader's scope")
                if units is None:
                    units = selected
                    events.append({"kind": "units", "value": units})
            elif code == 90:
                absolute = True
            elif code == 94:
                if profile == "Marlin":
                    raise ValueError("G94 is outside the modeled Marlin command set")
                feed_per_minute = True
            elif code != 17:
                raise ValueError(f"Unsupported G{value} on line {number}")
        movement_feed = feed
        if profile == "Marlin" and motion == 0:
            if marlin_rapid_mode == "variable":
                if "F" in values: rapid_feed = values["F"]
                movement_feed = rapid_feed
                feed = previous_feed  # G0 saves/restores G1 modal feed in this firmware configuration.
            elif marlin_rapid_mode == "fixed":
                movement_feed = marlin_fixed_rapid_feed
                # Fixed mode restores the NEW modal feed (including this block's F), not the old one.
        if "T" in values:
            selected = values["T"]
            if selected != int(selected) or selected < 1:
                raise ValueError("Invalid tool number")
            if tool != int(selected):
                tool = int(selected)
                events.append({"kind": "tool", "tool": tool})
        for letter, value in words:
            if letter != "M":
                continue
            code = float(value)
            if code in (3, 4, 5):
                selected = 0 if code == 5 else int(code)
                spindle_stopped = code == 5
                if selected != spindle or selected and "S" in values:
                    spindle = selected
                    events.append({"kind": "spindle", "tool": tool, "direction": spindle,
                                   "rpm": rpm if spindle else 0})
            elif code in (0, 6):
                events.append({"kind": "pause" if code == 0 else "change", "tool": tool, "at": xyz.copy(),
                               "spindleStopped": spindle_stopped})
            elif code == 1 and profile == "ISEL_CNC":
                if len(words) != 1:
                    raise ValueError("M01 must be a standalone optional stop")
                events.append({"kind":"optionalPause", "tool":tool, "at":xyz.copy(), "spindleStopped":spindle_stopped})
            elif code in (2, 30):
                if any(k in values for k in "XYZFP"):
                    raise ValueError("Motion/dwell on a termination block")
                terminated = True
            elif code == 400 and profile == "Marlin":
                if len(words) != 1:
                    raise ValueError("M400 must be a standalone synchronization block")
                events.append({"kind":"synchronize", "tool":tool, "at":xyz.copy()})
            else:
                raise ValueError(f"Unsupported M{value} on line {number}")
        if dwell:
            if "P" not in values or values["P"] < 0 or any(k in values for k in "XYZ"):
                raise ValueError("Invalid/unsupported dwell")
            events.append({"kind": "dwell", "tool": tool, "seconds": values["P"]})
        elif "P" in values:
            raise ValueError("P without dwell")
        if any(k in values for k in "XYZ"):
            if units is None or motion is None or not absolute:
                raise ValueError("Motion without explicit units/mode")
            end = [values.get(k, xyz[i]) for i, k in enumerate("XYZ")]
            if end != xyz:
                if motion == 1 and (feed is None or not feed_per_minute):
                    raise ValueError("Linear motion without feed/G94")
                event = {"kind": "rapid" if motion == 0 else "linear", "tool": tool,
                         "from": xyz.copy(), "to": end, "feed": movement_feed if motion == 1 or profile == "Marlin" else None,
                         "spindle": spindle, "rpm": rpm if spindle else 0}
                events.append(event)
                if motion == 1 and min(xyz[2], end[2]) < 0:
                    cuts.append(event)
            xyz = end
    if units is None or not cuts:
        raise ValueError("Program lacks explicit units or cutting motion")
    parsed = {"events": events, "cuts": cuts, "end": xyz, "units": units, "explicitTermination": terminated}
    if profile == "Marlin":
        parsed["marlinControls"] = {
            "synchronizationPoints":[e["at"] for e in events if e["kind"]=="synchronize"],
            "finalMovementBarrier":bool(events and events[-1]["kind"]=="synchronize"),
            "toolChanges":[e for e in events if e["kind"] in ("tool","change","pause")],
            "finalSpindleCommand":spindle,
            "rapidMode":marlin_rapid_mode,
            "limits":"M400 drains modeled planner moves, not spindle coast-down. Tn/M6 compatibility is a precondition, not simulated firmware support."}
    return parsed


def equivalent(a, b, tolerance=0.000051):
    """Rounding bound of Python's four coordinate decimals, in source units."""
    if isinstance(a, bool) or isinstance(b, bool):
        return type(a) is type(b) and a == b
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return abs(a-b) <= tolerance
    if isinstance(a, dict) and isinstance(b, dict):
        return a.keys() == b.keys() and all(equivalent(a[k], b[k], tolerance) for k in a)
    if isinstance(a, list) and isinstance(b, list):
        return len(a) == len(b) and all(equivalent(x,y,tolerance) for x,y in zip(a,b))
    return a == b


def machining_signature(parsed):
    """Not full execution equivalence: plunge starts may have different clearance.

    Keep multiplicity, direction, tool, depth, feed and running spindle; no union,
    sorting or removal of repeated holes/passes. Full events remain compared too.
    """
    result = []
    for event in parsed["cuts"]:
        start, end = event["from"], event["to"]
        if event["kind"] == "linear" and end[2] < 0:
            signature = {k: event[k] for k in ("tool", "to", "feed", "spindle", "rpm")}
            signature["from"] = start if start[:2] != end[:2] else start[:2]
            result.append(signature)
    result.extend(event for event in parsed["events"] if event["kind"] in ("dwell", "tool", "spindle"))
    return result


def cut_difference_by_tool(fx, python):
    """Diagnosis only: NEVER changes strict/ordered equivalence or permits missing/reversed cuts."""
    a=[event for event in machining_signature(fx) if "to" in event]
    b=[event for event in machining_signature(python) if "to" in event]
    result={}
    for tool in sorted({event["tool"] for event in a+b}):
        left=[event for event in a if event["tool"]==tool]
        right=[event for event in b if event["tool"]==tool]
        if equivalent(left,right): category="ORDERED_CUTS_MATCH"
        else:
            remaining=list(right)
            for event in left:
                index=next((i for i,other in enumerate(remaining) if equivalent(event,other)),None)
                if index is None: break
                remaining.pop(index)
            else:
                if not remaining:
                    result[str(tool)]="SAME_CUTS_DIFFERENT_ORDER"; continue
            category="CUTS_OR_PARAMETERS_DIFFER"
        result[str(tool)]=category
    return result


def first_difference(a, b):
    for index, (left, right) in enumerate(zip(a,b)):
        if not equivalent(left,right):
            return {"index": index, "fx": left, "python": right}
    if len(a) != len(b):
        index = min(len(a),len(b))
        return {"index": index, "fx": a[index] if index < len(a) else None,
                "python": b[index] if index < len(b) else None}
    return None


def unmatched_cuts(left, right):
    """Multiplicity-aware evidence, not a relaxed equivalence rule."""
    remaining = list(right)
    unmatched = []
    for event in left:
        index = next((i for i, other in enumerate(remaining) if equivalent(event, other)), None)
        if index is None:
            unmatched.append(event)
        else:
            remaining.pop(index)
    return unmatched, remaining


def toolpath_review(case, fx, python, fx_issues, python_issues):
    """Expose actual emitted differences per tool without attributing intent or safety.

    Reverse matching is diagnostic ONLY, limited to full XYZ lateral cuts. A
    reversed edge is never an execution/cutting match. Plunge origins deliberately
    have only XY in machining_signature and cannot be reversed as lateral cuts.
    """
    signatures = [[e for e in machining_signature(p) if "to" in e] for p in (fx, python)]
    categories = cut_difference_by_tool(fx, python)
    reviews = {}
    for tool in case["tools"]:
        identifier = tool["id"]
        cuts = [[e for e in signature if e["tool"] == identifier] for signature in signatures]
        left, right = unmatched_cuts(*cuts)
        reversed_remaining = list(right)
        reversed_count = 0
        for event in left:
            if len(event["from"]) != 3 or event["from"][:2] == event["to"][:2]:
                continue
            reversed_event = {**event, "from": event["to"], "to": event["from"]}
            index = next((i for i, other in enumerate(reversed_remaining)
                          if equivalent(reversed_event, other)), None)
            if index is not None:
                reversed_count += 1
                reversed_remaining.pop(index)
        metrics = {}
        for name, parsed, selected in zip(("fx", "python"), (fx, python), cuts):
            rapid = [e for e in parsed["events"] if e["kind"] == "rapid" and e["tool"] == identifier]
            lateral = [e for e in rapid if e["from"][:2] != e["to"][:2]]
            metrics[name] = {
                "cutCount": len(selected),
                "deepestZ": min((e["to"][2] for e in selected), default=None),
                "feeds": sorted({e["feed"] for e in selected}),
                "spindleStates": [list(state) for state in sorted({(e["spindle"], e["rpm"]) for e in selected})],
                **rapid_metrics({"events": rapid}),
                "lowestRapidXY_Z": min((min(e["from"][2], e["to"][2]) for e in lateral), default=None),
                "declaredRapidFeeds": sorted({e["feed"] for e in rapid if e["feed"] is not None}),
            }
        reviews[str(identifier)] = {
            "cutComparison": categories.get(str(identifier), "NO_MODELED_CUTS"),
            "metrics": metrics,
            "unmatchedDirectedCuts": {"fx": len(left), "python": len(right)},
            "oppositeDirectionPairs": reversed_count,
            "unmatchedSamples": {"fx": left[:3], "python": right[:3]},
            "requested": tool["parameters"],
            "parameterIssues": {name: [issue for issue in issues if issue.startswith(f"T{identifier}:")]
                                for name, issues in (("fx", fx_issues), ("python", python_issues))},
        }
    return {"tools": reviews,
            "jobIssues": {name: [issue for issue in issues if not re.match(r"T\d+:", issue)]
                          for name, issues in (("fx", fx_issues), ("python", python_issues))},
            "limits": "Source-unit distances, not time. Samples limited to three cuts per side; counts retain all repeated passes. "
                      "Opposite direction, same cuts in another order and unsupported commands are NOT parity or physical safety."}


def rapid_metrics(parsed):
    """Executed source-unit distances, not an estimated time or a safety score."""
    rapid = [event for event in parsed["events"] if event["kind"] == "rapid"]
    return {"xyDistance":sum(math.dist(event["from"][:2],event["to"][:2]) for event in rapid),
            "zDistance":sum(abs(event["to"][2]-event["from"][2]) for event in rapid),
            "xyMoves":sum(event["from"][:2] != event["to"][:2] for event in rapid)}


def geometry_source(tool):
    from shapely import wkt
    from shapely.geometry import LinearRing
    shape=wkt.loads(tool["wkt"])
    kind=tool.get("geometryType",shape.geom_type)
    if kind=="LinearRing":
        if shape.geom_type!="LineString" or not shape.is_ring:
            raise ValueError("LinearRing fixture must be a closed simple LineString")
        return LinearRing(shape.coords)
    if kind!=shape.geom_type:
        raise ValueError("Geometry type does not match fixture WKT")
    return shape


def geometry_paths(shape):
    if shape.geom_type=="Polygon":
        return [shape.exterior,*shape.interiors]
    if shape.geom_type in ("LineString","LinearRing","Point"):
        return [shape]
    if shape.geom_type in ("MultiLineString","MultiPolygon","GeometryCollection"):
        return [path for part in shape.geoms for path in geometry_paths(part)]
    raise ValueError("Unsupported CNC fixture geometry")


def nearest_points(points,start):
    remaining=list(points); result=[]
    while remaining:
        index=min(range(len(remaining)),key=lambda i:math.dist(start,remaining[i]))
        start=remaining.pop(index); result.append(start)
    return result


def audit_segments(expected,events,issues,identifier):
    # Keep multiplicity and depth: traversing the same edge twice is not one cut.
    remaining=list(expected)
    for event in events:
        a,b=event["from"],event["to"]
        match=next((i for i,(start,end,z) in enumerate(remaining) if equivalent(a[2],z) and equivalent(b[2],z)
                    and ((equivalent(a[:2],start) and equivalent(b[:2],end))
                         or (equivalent(a[:2],end) and equivalent(b[:2],start)))),None)
        if match is None:
            issues.append(f"T{identifier}: XY cut segment/depth differs from the source paths")
        else:
            remaining.pop(match)
    if remaining:
        issues.append(f"T{identifier}: missing source cut segments/depths")


def audit_parameters(case, parsed, *, implementation=None):
    """Check the requested settings independently; two equally wrong outputs are not a match."""
    issues = []
    if parsed["units"] != case["units"]:
        issues.append("Units differ from the source")
    job = case["job"]
    fx_clearance = job.get("fxTravelClearance","JOB_MAXIMUM")
    if implementation == "fx" and fx_clearance not in ("JOB_MAXIMUM","PER_TOOL"):
        raise ValueError("Unknown FX Travel Z policy")
    tools = case["tools"]
    selected = {tool["id"] for tool in tools}
    if any(event["tool"] not in selected for event in parsed["cuts"]):
        issues.append("Cut with an unselected tool")
    last_point=[0,0]
    for tool in tools:
        p = tool["parameters"]
        linear_paths = None
        if case["operation"] == "geometry" and "wkt" in tool:
            linear_paths = geometry_paths(geometry_source(tool))
        depth = p["depth"] + p.get("offsetZ",0)
        step = p["depthPerPass"] if p["multiDepth"] else depth
        count = math.ceil(depth / step - 1e-12)
        expected_depths = [-min(i*step,depth) for i in range(1,count+1)]
        plunges = [event for event in parsed["cuts"] if event["tool"] == tool["id"]
                   and event["from"][:2] == event["to"][:2] and event["to"][2] < event["from"][2]]
        ordered=tool.get("points",[])
        if case["operation"]=="drilling":
            if job.get("changeX") is not None: last_point=[job["changeX"],job["changeY"]]
            if implementation=="fx" and job.get("fxDrillOrder")=="NEAREST": ordered=nearest_points(ordered,last_point)
            visits=list(ordered)+[slot[0] for slot in tool.get("slots",[])]
            if tool.get("slots"): last_point=tool["slots"][-1][1]
            elif ordered: last_point=ordered[-1]
            expected=expected_depths*len(visits)
        elif linear_paths is not None:
            expected=[z for path in linear_paths for z in
                      ([expected_depths[-1]] if path.geom_type=="Point" and (implementation=="python" or
                       p.get("fxPassStrategy","RETRACT_SAME_DIRECTION")!="RETRACT_SAME_DIRECTION") else expected_depths)]
        else: expected=expected_depths
        if not equivalent([event["to"][2] for event in plunges],expected):
            issues.append(f"T{tool['id']}: depths/count differ from the requested passes")
        for event in parsed["cuts"]:
            if event["tool"] != tool["id"] or event["to"][2] >= 0:
                continue
            plunge = event["from"][:2] == event["to"][:2]
            wanted_feed = p["feedZ"] if plunge else p["feed"]
            if not equivalent(event["feed"],wanted_feed):
                issues.append(f"T{tool['id']}: executed feed differs from the requested feed")
            if p["rpm"] > 0 and (event["spindle"] != 3 or event["rpm"] != p["rpm"]):
                issues.append(f"T{tool['id']}: requested spindle is not running at the requested RPM")
        if implementation == "fx" and p["rpm"] == 0 and any(event["kind"] == "spindle"
                and event["tool"] == tool["id"] and event["direction"] != 0 for event in parsed["events"]):
            issues.append(f"T{tool['id']}: RPM zero must not command a spindle start in FX")
        expected_dwells = [p["dwellSeconds"]] if p["dwell"] else []
        dwells = [event["seconds"] for event in parsed["events"] if event["kind"] == "dwell" and event["tool"] == tool["id"]]
        if not equivalent(dwells,expected_dwells):
            issues.append(f"T{tool['id']}: dwell differs from the requested duration/count")
        if case["operation"] == "drilling":
            expected_points = [point for point in visits for _ in expected_depths]
            actual_points=[event["to"][:2] for event in plunges]
            if implementation=="python":
                # Python B chooses its own order. Audit every requested visit; full
                # execution/machining comparison STILL requires the same ordered events.
                remaining=list(expected_points)
                for point in actual_points:
                    index=next((i for i,pnt in enumerate(remaining) if equivalent(point,pnt)),None)
                    if index is None: remaining.append(None); break
                    remaining.pop(index)
                correct=not remaining and len(actual_points)==len(expected_points)
            else: correct=equivalent(actual_points,expected_points)
            if not correct:
                issues.append(f"T{tool['id']}: drilled points/count differ from the selected holes")
            expected_edges=[(slot[0],slot[1],z) for slot in tool.get("slots",[]) for z in expected_depths]
            xy_cuts=[event for event in parsed["cuts"] if event["tool"]==tool["id"] and event["from"][:2]!=event["to"][:2]]
            audit_segments(expected_edges,xy_cuts,issues,tool["id"])
        elif linear_paths is not None:
            # These public fixtures are uncompensated linear paths. Count XY cutting
            # traversals as well as plunges: an extra pass at the SAME rounded Z can
            # have no Z movement and otherwise escape the depth-only audit.
            xy_cuts = [event for event in parsed["cuts"] if event["tool"] == tool["id"]
                       and event["from"][:2] != event["to"][:2] and event["to"][2] < 0]
            if len(xy_cuts) != sum(len(path.coords)-1 for path in linear_paths if path.geom_type!="Point") * count:
                issues.append(f"T{tool['id']}: XY cutting traversal count differs from the requested passes")
            expected_edges=[(list(a[:2]),list(b[:2]),z) for path in linear_paths if path.geom_type!="Point"
                            for a,b in zip(list(path.coords),list(path.coords)[1:]) for z in expected_depths]
            audit_segments(expected_edges,xy_cuts,issues,tool["id"])
    pause_kind = "optionalPause" if case.get("profile") == "ISEL_CNC" else "pause"
    pauses = [event for event in parsed["events"] if event["kind"] == pause_kind]
    if len(pauses) != (len(tools) if job["toolChange"] else 0):
        issues.append("Tool-change pause count differs from the requested setting")
    if any(not event["spindleStopped"] for event in pauses):
        issues.append("Tool-change pause without an explicit preceding spindle stop")
    if any(not equivalent(event["at"][2],job["toolChangeZ"]) for event in pauses):
        issues.append("Tool-change pause Z differs from the requested height")
    if job.get("changeX") is not None and any(not equivalent(event["at"][:2],[job["changeX"],job["changeY"]]) for event in pauses):
        issues.append("Tool-change pause XY differs from the requested position")
    for event in parsed["events"]:
        if case.get("profile") == "Marlin" and event["kind"] == "rapid" and not equivalent(event["feed"],job["rapidFeed"]):
            issues.append("Marlin G0 feed differs from the requested Feed rapids (or is undeclared)")
        if event["kind"] == "rapid" and event["from"][:2] != event["to"][:2]:
            p = next((tool["parameters"] for tool in tools if tool["id"] == event["tool"]),tools[0]["parameters"])
            if min(event["from"][2],event["to"][2]) < p["safeZ"]-.000051:
                issues.append("Rapid XY below the selected tool's requested Travel Z")
            if implementation == "fx" and case["operation"] == "geometry" and fx_clearance == "JOB_MAXIMUM":
                clearance = max(tool["parameters"]["safeZ"] for tool in tools)
                if min(event["from"][2],event["to"][2]) < clearance-.000051:
                    issues.append("Rapid XY below FX job-wide maximum Travel Z")
    if not equivalent(parsed["end"][2],job["endZ"]):
        issues.append("End Z differs from the requested height")
    if job["endX"] is not None and not equivalent(parsed["end"][:2],[job["endX"],job["endY"]]):
        issues.append("End XY differs from the requested position")
    if job["startZ"] is not None and not any(event["kind"] == "rapid" and equivalent(event["to"][2],job["startZ"])
                                                for event in parsed["events"]):
        issues.append("Start Z was not emitted")
    if job.get("exclusions"):
        from shapely import wkt
        from shapely.geometry import Point, LineString
        for event in parsed["events"]:
            if event["kind"] not in ("rapid","linear"):
                continue
            diameter = next((tool["diameter"] for tool in tools if tool["id"] == event["tool"]),tools[0]["diameter"])
            start, end = event["from"], event["to"]
            path = Point(*start[:2]) if start[:2] == end[:2] else LineString([start[:2],end[:2]])
            for area in job["exclusions"]:
                # Tool-radius collision, not certification of fixture clearance. Inset
                # only by coordinate rounding; it is much smaller than the routing margin.
                protected = wkt.loads(area["wkt"]).buffer(diameter/2,join_style=2).buffer(-.000051)
                if not path.intersects(protected):
                    continue
                if event["kind"] == "linear":
                    issues.append("Cut intersects an exclusion's tool-radius footprint")
                elif area["strategy"] == "around":
                    issues.append("Around travel intersects an exclusion's tool-radius footprint")
                elif min(start[2],end[2]) < area["overZ"]-.000051:
                    issues.append("Over travel crosses an exclusion below its requested height")
    return sorted(set(issues))


def exclusion_binding(root, app, areas):
    from copy import deepcopy
    import numpy as np
    from shapely.geometry import LineString, Point
    from shapely import wkt
    source = root / "appCommon/Common.py"
    tree = ast.parse(source.read_text(encoding="utf-8-sig"))
    cls = next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name == "ExclusionAreas")
    methods = [n for n in cls.body if isinstance(n,ast.FunctionDef) and n.name == "travel_coordinates"]
    methods += [n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ("nearest_point","farthest_point")]
    if len(methods) != 3:
        raise ValueError("Original exclusion router layout changed")
    namespace = dict(deepcopy=deepcopy, np=np, LineString=LineString, Point=Point)
    exec(compile(ast.Module(body=methods,type_ignores=[]),str(source),"exec"),namespace)
    binding = SimpleNamespace(app=app, exclusion_areas_storage=[dict(shape=wkt.loads(area["wkt"]),
                              strategy=area["strategy"],overz=area["overZ"]) for area in areas])
    binding.travel_coordinates = MethodType(namespace["travel_coordinates"],binding)
    return binding


class ErrorCapture(logging.Handler):
    def __init__(self):
        super().__init__(logging.ERROR)
        self.messages = []
    def emit(self, record):
        self.messages.append(record.getMessage())


def python_program(case, root, app, camlib):
    from shapely.geometry import Point
    from shapely import wkt
    job = case["job"]
    rapid_feed = job.get("rapidFeed",1500)  # compatibility with historical four-profile exports
    app.defaults["units"] = case["units"]
    app.defaults["geometry_f_plunge"] = True
    app.dec_format = lambda value, decimals: float(f"{value:.{decimals}f}")
    app.exc_areas = exclusion_binding(root, app, job["exclusions"])
    errors = ErrorCapture()
    app.log = logging.getLogger("flatcam.cnc.oracle")
    app.log.addHandler(errors)
    profile = case.get("profile","GRBL_11_no_M6")
    try:
        cnc = camlib.CNCjob(units=case["units"],pp_geometry_name=profile,pp_excellon_name=profile)
        cnc.coords_decimals = 4
        # Match the FX's fixed four feed decimals through a real Python setting,
        # not by changing the preprocessor or tolerating different feed rates.
        cnc.fr_decimals = 4
        cnc.options = {"type": "Excellon" if case["operation"] == "drilling" else "Geometry",
                       "xmin":0,"ymin":0,"xmax":30,"ymax":10,"tool_dia":case["tools"][0]["diameter"],
                       "Tools_in_use":[[str(t["id"]),t["diameter"],len(t.get("points",[]))] for t in case["tools"]]}
        if case["operation"] == "drilling":
            tools = {}
            for tool in case["tools"]:
                p = tool["parameters"]
                data = dict(tools_drill_cutz=-p["depth"],tools_drill_multidepth=p["multiDepth"],
                    tools_drill_depthperpass=p["depthPerPass"],tools_drill_travelz=p["safeZ"],
                    tools_drill_f_plunge=True,tools_drill_f_retract=True,tools_drill_feedrate_z=p["feedZ"],
                    tools_drill_feedrate_rapid=rapid_feed,tools_drill_spindlespeed=p["rpm"],tools_drill_dwell=p["dwell"],
                    tools_drill_dwelltime=p["dwellSeconds"],tools_drill_spindledir="CW",tools_drill_offset=p["offsetZ"],
                    tools_drill_toolchangez=job["toolChangeZ"],tools_drill_toolchangexy=xy(job,"change"),
                    tools_drill_startz=job["startZ"],tools_drill_endz=job["endZ"],tools_drill_endxy=xy(job,"end"),
                    tools_drill_z_pdepth=-1,tools_drill_feedrate_probe=50)
                tools[tool["id"]] = {"tooldia":tool["diameter"],"drills":[Point(*pt) for pt in tool["points"]],"data":data}
            result, start, first = "", "", (0,0)
            for index, tool in enumerate(case["tools"]):
                generated = cnc.excellon_tool_gcode_gen(tool["id"],tools[tool["id"]]["drills"],tools,first,
                        is_first=index == 0,is_last=index == len(case["tools"])-1,opt_type="B",toolchange=job["toolChange"])
                if generated == "fail":
                    raise ValueError("Original drill generator returned fail")
                code, first, header = generated
                result += code
                start += header
            result = start + result
        elif len(case["tools"]) > 1:
            # The GUI's multigeo routine, not a concatenation of finished single-tool programs.
            tools = {}
            for tool in case["tools"]:
                p = tool["parameters"]
                tools[tool["id"]] = dict(tooldia=tool["diameter"], offset="Path", offset_value=0,
                    solid_geometry=[geometry_source(tool)], data=dict(
                        optimization_type="B", search_time="R", ppname_g=profile,
                        cutz=-p["depth"], multidepth=p["multiDepth"], depthperpass=p["depthPerPass"],
                        travelz=p["safeZ"], feedrate=p["feed"], feedrate_z=p["feedZ"], feedrate_rapid=rapid_feed,
                        spindlespeed=p["rpm"], spindledir="CW", dwell=p["dwell"], dwelltime=p["dwellSeconds"],
                        startz=job["startZ"], endz=job["endZ"], endxy=xy(job,"end"),
                        toolchangez=job["toolChangeZ"], toolchangexy=xy(job,"change"), extracut=False, extracut_length=0))
            result, start = "", ""
            for index, tool in enumerate(case["tools"]):
                generated = cnc.geometry_tool_gcode_gen(tool["id"], tools, first_pt=(0,0), tolerance=0,
                    is_first=index == 0, is_last=index == len(case["tools"])-1, toolchange=job["toolChange"])
                if generated == "fail":
                    raise ValueError("Original multigeo generator returned fail")
                code, header = generated
                result += code
                start += header
            result = start + result
        else:
            tool = case["tools"][0]
            p = tool["parameters"]
            geo = camlib.Geometry()
            geo.solid_geometry = [geometry_source(tool)]
            generated = cnc.generate_from_geometry_2(geo,append=False,tooldia=tool["diameter"],
                z_cut=-p["depth"],z_move=p["safeZ"],feedrate=p["feed"],feedrate_z=p["feedZ"],feedrate_rapid=rapid_feed,
                spindlespeed=p["rpm"],dwell=p["dwell"],dwelltime=p["dwellSeconds"],multidepth=p["multiDepth"],
                depthpercut=p["depthPerPass"],toolchange=job["toolChange"],toolchangez=job["toolChangeZ"],
                toolchangexy=xy(job,"change"),startz=job["startZ"],endz=job["endZ"],endxy=xy(job,"end"),
                pp_geometry_name=profile,tool_no=tool["id"],is_first=True)
            if generated == "fail":
                raise ValueError("Original geometry generator returned fail")
            code, header = generated
            result = header + code
        if errors.messages:
            raise ValueError("Original preprocessor errors: " + " | ".join(errors.messages))
        return result
    finally:
        app.log.removeHandler(errors)


def xy(job, prefix):
    return "" if job[prefix+"X"] is None else f"{job[prefix+'X']}, {job[prefix+'Y']}"


def slot_oracle_case(case,root,*,enabled_only=False):
    """Original Python Drill slots conversion, not a fake mill or copied algorithm."""
    if not any(tool.get("slots") for tool in case["tools"]): return case
    from copy import deepcopy
    from shapely.geometry import Point,LineString
    source=root/"appTools/ToolDrilling.py"
    tree=ast.parse(source.read_text(encoding="utf-8"))
    cls=next(node for node in tree.body if isinstance(node,ast.ClassDef) and node.name=="ToolDrilling")
    method=next(node for node in cls.body if isinstance(node,ast.FunctionDef) and node.name=="process_slot_as_drills")
    method.decorator_list=[] # Static method invoked as a standalone original body.
    namespace={"LineString":LineString}
    exec(compile(ast.Module(body=[method],type_ignores=[]),str(source),"exec"),namespace)
    copied=deepcopy(case)
    for tool in copied["tools"]:
        p=tool.get("parameters",{})
        if enabled_only and not p.get("fxSlotDrilling",False): continue
        # Old milling fixtures intentionally compare different operations; new explicit chains
        # use their per-tool settings. The expected positions come from the ORIGINAL Python body.
        overlap=p.get("slotOverlap",0) if p.get("fxSlotDrilling",False) else case["job"].get("pythonSlotOverlap",0)
        last=p.get("slotLastHole",True) if p.get("fxSlotDrilling",False) else case["job"].get("pythonSlotLastHole",True)
        if not 0<=overlap<100: raise ValueError("Invalid slot drilling overlap")
        spacing=tool["diameter"]*(1-overlap/100)
        if not math.isfinite(spacing) or spacing<=0: raise ValueError("Invalid slot drilling diameter/spacing")
        for a,b in tool.get("slots",[]):
            points=namespace["process_slot_as_drills"]((Point(*a),Point(*b)),spacing,last)
            tool["points"].extend([[point.x,point.y] for point in points])
        tool["slots"]=[]
    return copied


def run(args):
    root = args.legacy_root.resolve()
    sys.path[:0] = [str(root),str(root / "tests")]
    if args.dependency_path:
        sys.path.insert(0,str(args.dependency_path.resolve()))
    import camlib
    from parser_baseline import install_stub_app
    from preprocessors.default import default
    from compare_cam_python import dependency_metadata
    app = install_stub_app()
    camlib.log.setLevel(logging.WARNING)
    export = json.loads(args.fx_export.read_text(encoding="utf-8-sig"))
    if export["schema"] != 1 or export["profile"] not in SUPPORTED_PROFILES:
        raise ValueError("Unsupported CNC corpus")
    import importlib
    profile=export["profile"]
    rapid_mode=args.marlin_rapid_mode
    fixed_rapid_mm=args.marlin_fixed_rapid_mm
    if profile != "Marlin" and (rapid_mode != "shared" or fixed_rapid_mm is not None):
        raise ValueError("Marlin reader configuration requires the Marlin profile")
    if rapid_mode == "fixed" and (fixed_rapid_mm is None or not math.isfinite(fixed_rapid_mm) or fixed_rapid_mm <= 0):
        raise ValueError("Fixed Marlin G0 requires --marlin-fixed-rapid-mm positive and finite")
    if rapid_mode != "fixed" and fixed_rapid_mm is not None:
        raise ValueError("--marlin-fixed-rapid-mm is only accepted with fixed mode")
    selected=getattr(importlib.import_module("preprocessors."+profile),profile)()
    app.preprocessors={"default":default(),profile:selected}
    for case in export["cases"]: case["profile"]=profile
    ids = [case["id"] for case in export["cases"]]
    if not ids or len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z0-9-]{1,80}",identifier) for identifier in ids):
        raise ValueError("Missing/invalid/duplicate cases")
    if args.output.exists():
        raise ValueError("Use a fresh report directory")
    args.output.mkdir(parents=True)
    sources = ("camlib.py","appCommon/Common.py","preprocessors/"+profile+".py","appTools/ToolDrilling.py")
    hashes = {name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
    report = {"schema":1,**dependency_metadata(),"profile":export["profile"],"cases":[],
              "legacySourceSha256":hashes,"oracle":"Original CNCjob generators, OR-Tools Basic optimizer, selected preprocessor and exclusion routing",
              "coordinateToleranceInSourceUnits":.000051,"pythonFeedDecimals":4,"pythonCoordinateDecimals":4,
              "excludedUnitCases":export.get("excludedUnitCases",[]),
              "marlinConfiguration":None if profile!="Marlin" else {
                  "rapidMode":rapid_mode, "fixedRapidMillimetersPerMinute":fixed_rapid_mm,
                  "assumptions":"CNC-configured Cartesian firmware, spindle S in RPM, compatible Tn/M6 and M0, INCH_MODE_SUPPORT for IN. No firmware/controller or physical simulation. Variable G0 initial feed is unknown until declared."},
              "scope":"Public synthetic cases; initial XYZ assumed zero; explicit XYZ/F/S/T G0/G1/M0/M6 subset for the allowlisted milling profiles. Marlin assumes native units/min, records M400 barriers and G0 feeds; other profiles still require G94. ISEL G71 means MM; M01 is a distinct OPTIONAL stop event, not proof the machine will pause. M6 internals are NOT simulated. No GUI/physical certification. Strict requires full modeled execution, not machining-only agreement."}
    for case in export["cases"]:
        result = {"id":case["id"],"operation":case["operation"],"units":case["units"]}
        (args.output/(case["id"]+".fx.nc")).write_text(case["gcode"],encoding="utf-8")
        try:
            if case["units"] not in ("MM","IN") or case["operation"] not in ("drilling","geometry"):
                raise ValueError("Unsupported units/operation")
            reader_options = {} if profile!="Marlin" else {"marlin_rapid_mode":rapid_mode,
                "marlin_fixed_rapid_feed":None if fixed_rapid_mm is None else fixed_rapid_mm/(25.4 if case["units"]=="IN" else 1)}
            fx = read_program(case["gcode"],profile,**reader_options)
            if profile=="Marlin": result["fxMarlinControls"]=fx["marlinControls"]
            result["fxParameterIssues"] = audit_parameters(slot_oracle_case(case,root,enabled_only=True),fx,implementation="fx")
            if case["operation"] == "geometry":
                result["travelPolicies"] = {"fx":"per-tool Travel Z" if case["job"].get("fxTravelClearance")=="PER_TOOL"
                                            else "job-wide maximum Travel Z", "python":"per-tool Travel Z"}
        except Exception as error:
            result.update(status="FX_ERROR",error=str(error))
        else:
            try:
                oracle_case=slot_oracle_case(case,root)
                if any(tool.get("slots") and not tool.get("parameters",{}).get("fxSlotDrilling",False) for tool in case["tools"]):
                    result["operationDifference"]="FX mills slots end-to-end; Python Drill slots drills overlapping point chains. Not equivalent operations."
                    result["pythonSlotPoints"]={str(tool["id"]):tool["points"] for tool in oracle_case["tools"]}
                program = python_program(oracle_case,root,app,camlib)
                (args.output/(case["id"]+".python.nc")).write_text(program,encoding="utf-8")
                python = read_program(program,profile,**reader_options)
                if profile=="Marlin": result["pythonMarlinControls"]=python["marlinControls"]
                result["pythonParameterIssues"] = audit_parameters(oracle_case,python,implementation="python")
            except Exception as error:
                result.update(status="ORACLE_ERROR",error=str(error))
            else:
                full = (equivalent(fx["events"],python["events"]) and equivalent(fx["end"],python["end"])
                        and fx["units"] == python["units"] and not result["fxParameterIssues"] and not result["pythonParameterIssues"])
                machining = equivalent(machining_signature(fx),machining_signature(python))
                result.update(status="MATCH_EXECUTION" if full else "ROUTING_DIFFERENT" if machining else "MACHINING_DIFFERENT",
                    machiningMatches=machining,executionMatches=full,fxEventCount=len(fx["events"]),pythonEventCount=len(python["events"]),
                    fxCutCount=len(fx["cuts"]),pythonCutCount=len(python["cuts"]),
                    fxExplicitTermination=fx["explicitTermination"],pythonExplicitTermination=python["explicitTermination"],
                    firstExecutionDifference=first_difference(fx["events"],python["events"]),
                    firstMachiningDifference=first_difference(machining_signature(fx),machining_signature(python)),
                    rapidMetrics={"fx":rapid_metrics(fx),"python":rapid_metrics(python)})
                result["cutDifferenceByTool"] = cut_difference_by_tool(fx,python)
                result["toolpathReview"] = toolpath_review(case,fx,python,result["fxParameterIssues"],result["pythonParameterIssues"])
                if not machining and case["operation"]=="drilling" and not any(tool.get("slots") and not tool.get("parameters",{}).get("fxSlotDrilling",False) for tool in case["tools"]) \
                        and any(len(tool.get("points",[]))>1 for tool in oracle_case["tools"]) \
                        and not result["fxParameterIssues"] and not result["pythonParameterIssues"]:
                    result["orderingDifference"]="Both pass requested hole multiplicity, depths and feeds; ordered cutting events differ. Strict comparison is unchanged."
                (args.output/(case["id"]+".trace.json")).write_text(json.dumps({"fx":fx,"python":python},indent=2),encoding="utf-8")
        report["cases"].append(result)
    if hashes != {name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}:
        raise ValueError("Oracle sources changed during the run")
    report["counts"] = {status:sum(c["status"] == status for c in report["cases"])
                        for status in ("MATCH_EXECUTION","ROUTING_DIFFERENT","MACHINING_DIFFERENT","ORACLE_ERROR","FX_ERROR")}
    (args.output/"report.json").write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
    rows = ""
    for case in report["cases"]:
        identifier = case["id"]
        links = " ".join(f'<a href="{identifier}.{suffix}">{label}</a>' for suffix,label in
                         (("fx.nc","FX"),("python.nc","Python"),("trace.json","XYZ trace"))
                         if (args.output/(identifier+"."+suffix)).is_file())
        rows += "<tr><td>"+html.escape(identifier)+"<br>"+links+"</td><td>"+case["status"]+"</td><td><pre>"+html.escape(json.dumps(case,indent=2))+"</pre></td></tr>"
    (args.output/"index.html").write_text('<!doctype html><meta charset="utf-8"><title>CNC XYZ comparison</title>'
        '<h1>CNC XYZ/F/S/T — FX × Python</h1><p>'+html.escape(report["scope"])+"</p><table>"+rows+"</table>",encoding="utf-8")
    print(json.dumps({"counts":report["counts"],"report":str(args.output/"index.html")}))
    return int(args.strict and any(c["status"] != "MATCH_EXECUTION" for c in report["cases"]))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root",type=Path,required=True)
    parser.add_argument("--fx-export",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--dependency-path",type=Path)
    parser.add_argument("--strict",action="store_true")
    parser.add_argument("--marlin-rapid-mode",choices=("shared","variable","fixed"),default="shared")
    parser.add_argument("--marlin-fixed-rapid-mm",type=float)
    sys.exit(run(parser.parse_args()))
