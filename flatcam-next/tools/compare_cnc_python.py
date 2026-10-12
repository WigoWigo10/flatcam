"""Compare executed XYZ/F/S/T state with the ORIGINAL Python CNC generators.

This is a deliberately narrow GRBL milling reader, not a machine simulator.
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


def read_program(program):
    """Modal GRBL subset; retain repeated cuts, dwell, tool and spindle state."""
    xyz = [0.0, 0.0, 0.0]
    feed = None
    rpm = 0.0
    spindle = 0
    tool = 0
    motion = None
    units = None
    absolute = False
    feed_per_minute = False
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
            elif code in (20, 21):
                selected = "IN" if code == 20 else "MM"
                if units is not None and selected != units:
                    raise ValueError("Unit change inside the program is outside this reader's scope")
                if units is None:
                    units = selected
                    events.append({"kind": "units", "value": units})
            elif code == 90:
                absolute = True
            elif code == 94:
                feed_per_minute = True
            elif code != 17:
                raise ValueError(f"Unsupported G{value} on line {number}")
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
            elif code in (2, 30):
                if any(k in values for k in "XYZFP"):
                    raise ValueError("Motion/dwell on a termination block")
                terminated = True
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
                         "from": xyz.copy(), "to": end, "feed": feed if motion == 1 else None,
                         "spindle": spindle, "rpm": rpm if spindle else 0}
                events.append(event)
                if motion == 1 and min(xyz[2], end[2]) < 0:
                    cuts.append(event)
            xyz = end
    if units is None or not cuts:
        raise ValueError("Program lacks explicit units or cutting motion")
    return {"events": events, "cuts": cuts, "end": xyz, "units": units, "explicitTermination": terminated}


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


def first_difference(a, b):
    for index, (left, right) in enumerate(zip(a,b)):
        if not equivalent(left,right):
            return {"index": index, "fx": left, "python": right}
    if len(a) != len(b):
        index = min(len(a),len(b))
        return {"index": index, "fx": a[index] if index < len(a) else None,
                "python": b[index] if index < len(b) else None}
    return None


def audit_parameters(case, parsed):
    """Check the requested settings independently; two equally wrong outputs are not a match."""
    issues = []
    if parsed["units"] != case["units"]:
        issues.append("Units differ from the source")
    job = case["job"]
    tools = case["tools"]
    selected = {tool["id"] for tool in tools}
    if any(event["tool"] not in selected for event in parsed["cuts"]):
        issues.append("Cut with an unselected tool")
    for tool in tools:
        p = tool["parameters"]
        depth = p["depth"] + p.get("offsetZ",0)
        step = p["depthPerPass"] if p["multiDepth"] else depth
        count = math.ceil(depth / step - 1e-12)
        expected_depths = [-min(i*step,depth) for i in range(1,count+1)]
        plunges = [event for event in parsed["cuts"] if event["tool"] == tool["id"]
                   and event["from"][:2] == event["to"][:2] and event["to"][2] < event["from"][2]]
        expected = expected_depths * (len(tool["points"]) if case["operation"] == "drilling" else 1)
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
        expected_dwells = [p["dwellSeconds"]] if p["dwell"] else []
        dwells = [event["seconds"] for event in parsed["events"] if event["kind"] == "dwell" and event["tool"] == tool["id"]]
        if not equivalent(dwells,expected_dwells):
            issues.append(f"T{tool['id']}: dwell differs from the requested duration/count")
        if case["operation"] == "drilling":
            expected_points = [point for point in tool["points"] for _ in expected_depths]
            if not equivalent([event["to"][:2] for event in plunges],expected_points):
                issues.append(f"T{tool['id']}: drilled points/count differ from the selected holes")
    pauses = [event for event in parsed["events"] if event["kind"] == "pause"]
    if len(pauses) != (len(tools) if job["toolChange"] else 0):
        issues.append("Tool-change pause count differs from the requested setting")
    if any(not event["spindleStopped"] for event in pauses):
        issues.append("Tool-change pause without an explicit preceding spindle stop")
    if any(not equivalent(event["at"][2],job["toolChangeZ"]) for event in pauses):
        issues.append("Tool-change pause Z differs from the requested height")
    if job.get("changeX") is not None and any(not equivalent(event["at"][:2],[job["changeX"],job["changeY"]]) for event in pauses):
        issues.append("Tool-change pause XY differs from the requested position")
    for event in parsed["events"]:
        if event["kind"] == "rapid" and event["from"][:2] != event["to"][:2]:
            p = next((tool["parameters"] for tool in tools if tool["id"] == event["tool"]),tools[0]["parameters"])
            if min(event["from"][2],event["to"][2]) < p["safeZ"]-.000051:
                issues.append("Rapid XY below the selected tool's requested Travel Z")
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
    app.defaults["units"] = case["units"]
    app.defaults["geometry_f_plunge"] = True
    app.dec_format = lambda value, decimals: float(f"{value:.{decimals}f}")
    app.exc_areas = exclusion_binding(root, app, job["exclusions"])
    errors = ErrorCapture()
    app.log = logging.getLogger("flatcam.cnc.oracle")
    app.log.addHandler(errors)
    profile = "GRBL_11_no_M6"
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
                    tools_drill_feedrate_rapid=1500,tools_drill_spindlespeed=p["rpm"],tools_drill_dwell=p["dwell"],
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
        else:
            if len(case["tools"]) != 1:
                raise ValueError("Geometry oracle currently covers one tool per job")
            tool = case["tools"][0]
            p = tool["parameters"]
            geo = camlib.Geometry()
            geo.solid_geometry = [wkt.loads(tool["wkt"])]
            generated = cnc.generate_from_geometry_2(geo,append=False,tooldia=tool["diameter"],
                z_cut=-p["depth"],z_move=p["safeZ"],feedrate=p["feed"],feedrate_z=p["feedZ"],feedrate_rapid=1500,
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


def run(args):
    root = args.legacy_root.resolve()
    sys.path[:0] = [str(root),str(root / "tests")]
    if args.dependency_path:
        sys.path.insert(0,str(args.dependency_path.resolve()))
    import camlib
    from parser_baseline import install_stub_app
    from preprocessors.default import default
    from preprocessors.GRBL_11_no_M6 import GRBL_11_no_M6
    from compare_cam_python import dependency_metadata
    app = install_stub_app()
    camlib.log.setLevel(logging.WARNING)
    app.preprocessors = {"default":default(),"GRBL_11_no_M6":GRBL_11_no_M6()}
    export = json.loads(args.fx_export.read_text(encoding="utf-8-sig"))
    if export["schema"] != 1 or export["profile"] != "GRBL_11_no_M6":
        raise ValueError("Unsupported CNC corpus")
    ids = [case["id"] for case in export["cases"]]
    if not ids or len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z0-9-]{1,80}",identifier) for identifier in ids):
        raise ValueError("Missing/invalid/duplicate cases")
    if args.output.exists():
        raise ValueError("Use a fresh report directory")
    args.output.mkdir(parents=True)
    sources = ("camlib.py","appCommon/Common.py","preprocessors/GRBL_11_no_M6.py")
    hashes = {name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources}
    report = {"schema":1,**dependency_metadata(),"profile":export["profile"],"cases":[],
              "legacySourceSha256":hashes,"oracle":"Original CNCjob generators, OR-Tools Basic optimizer, GRBL preprocessor and exclusion routing",
              "coordinateToleranceInSourceUnits":.000051,"pythonFeedDecimals":4,"pythonCoordinateDecimals":4,
              "scope":"Public synthetic cases; initial XYZ assumed zero; only the explicit GRBL G0/G1 subset. No GUI/physical certification. Strict requires full execution, not machining-only agreement."}
    for case in export["cases"]:
        result = {"id":case["id"],"operation":case["operation"],"units":case["units"]}
        (args.output/(case["id"]+".fx.nc")).write_text(case["gcode"],encoding="utf-8")
        try:
            if case["units"] not in ("MM","IN") or case["operation"] not in ("drilling","geometry"):
                raise ValueError("Unsupported units/operation")
            fx = read_program(case["gcode"])
            result["fxParameterIssues"] = audit_parameters(case,fx)
        except Exception as error:
            result.update(status="FX_ERROR",error=str(error))
        else:
            try:
                program = python_program(case,root,app,camlib)
                (args.output/(case["id"]+".python.nc")).write_text(program,encoding="utf-8")
                python = read_program(program)
                result["pythonParameterIssues"] = audit_parameters(case,python)
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
                    firstMachiningDifference=first_difference(machining_signature(fx),machining_signature(python)))
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
    sys.exit(run(parser.parse_args()))
