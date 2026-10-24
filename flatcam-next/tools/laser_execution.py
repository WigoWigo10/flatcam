"""Command-state laser reader. No beam/material/planner timing or machine safety simulation."""
import math
import re

LASER_PROFILES = ("GRBL_laser", "Z_laser", "Marlin_laser_FAN_pin", "Marlin_laser_Spindle_pin")
MARLIN_LASERS = LASER_PROFILES[2:]
WORD = re.compile(r"([A-Z])\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))")


def command_text(raw):
    """Separate header text for diagnosis, retaining nonstandard nested comments as a limitation."""
    result, depth, nested = [], 0, False
    for char in raw.upper():
        if char == ";" and depth == 0: break
        if char == "(":
            depth += 1
            nested |= depth > 1
        elif char == ")":
            if depth == 0: raise ValueError("Unmatched laser comment delimiter")
            depth -= 1
        elif depth == 0: result.append(char)
    if depth: raise ValueError("Unterminated laser comment")
    return "".join(result).strip(), nested


def read_laser(program, profile, *, rapid_mode="shared", fixed_rapid_feed=None):
    if profile not in LASER_PROFILES: raise ValueError("Unknown laser profile")
    marlin = profile in MARLIN_LASERS
    fan = profile == "Marlin_laser_FAN_pin"
    if rapid_mode not in ("shared", "variable", "fixed") or not marlin and rapid_mode != "shared":
        raise ValueError("Unsupported laser rapid configuration")
    if rapid_mode == "fixed":
        if fixed_rapid_feed is None or not math.isfinite(fixed_rapid_feed) or fixed_rapid_feed <= 0:
            raise ValueError("Fixed Marlin G0 requires a positive feed")
    elif fixed_rapid_feed is not None: raise ValueError("Fixed rapid feed requires fixed mode")
    xyz = [0., 0., 0.]
    units = None
    absolute = False
    per_minute = marlin
    feed = rapid_feed = motion = power = mode = None
    events, cuts = [], []
    for number,raw in enumerate(program.splitlines(),1):
        line, nested = command_text(raw)
        if nested: events.append(dict(kind="nonstandardComment",line=number,text=raw))
        if not line: continue
        if WORD.sub("",line).strip(): raise ValueError(f"Unsupported laser syntax at {number}: {raw}")
        words = WORD.findall(line)
        if any(k not in "GMXYZFS" for k,v in words): raise ValueError("Word outside the laser subset")
        if any(sum(k == letter for k,v in words) > 1 for letter in "XYZFS"):
            raise ValueError("Duplicate laser word")
        values = {k:float(v) for k,v in words if k not in "GM"}
        if any(not math.isfinite(v) for v in values.values()): raise ValueError("Nonfinite laser value")
        previous_feed = feed
        if "F" in values:
            feed = values["F"]
            if feed <= 0: raise ValueError("Nonpositive laser feed")
        gwords = [float(v) for k,v in words if k == "G"]
        mwords = [float(v) for k,v in words if k == "M"]
        if mwords and (gwords or len(mwords) != 1 or any(k in values for k in "XYZF")):
            raise ValueError("Compound laser control block outside the model")
        for code in gwords:
            if code in (20,21):
                chosen = "MM" if code == 21 else "IN"
                if units is not None and chosen != units: raise ValueError("Laser unit change outside the model")
                if units is None: events.append(dict(kind="units",value=chosen))
                units = chosen
            elif code == 90: absolute = True
            elif code == 94 and not marlin: per_minute = True
            elif code == 17 and not marlin: pass
            elif code in (0,1): motion = int(code)
            else: raise ValueError(f"Unsupported laser G{code:g}")
        if sum(code in (0,1) for code in gwords) > 1: raise ValueError("Ambiguous laser motion block")
        if "S" in values and not mwords: raise ValueError("Inline/modally changed laser power is outside this contract")
        for code in mwords:
            if code == 400 and marlin and not values:
                events.append(dict(kind="synchronize",tool=1,at=xyz.copy()))
                continue
            if fan and code in (106,107):
                selected = values.get("S",255) if code == 106 else 0
                if code == 107 and values or selected != int(selected) or not 0 <= selected <= 255:
                    raise ValueError("Invalid laser FAN duty")
                mode = "FAN"
            elif not fan and code in (3,4,5):
                if code == 5:
                    if values: raise ValueError("Unsupported laser stop arguments")
                    selected = 0
                else:
                    selected = values.get("S")
                    if selected is None or selected < 0: raise ValueError("Laser power must be explicit and nonnegative")
                mode = "M" + str(int(code)) if code != 5 else None
            else: raise ValueError(f"Unsupported laser M{code:g}")
            power = selected
            events.append(dict(kind="laserPower",tool=1,command="M"+str(int(code)),power=power,mode=mode))
        movement_feed = feed
        if marlin and motion == 0:
            if rapid_mode == "variable":
                if "F" in values: rapid_feed = values["F"]
                movement_feed = rapid_feed
                feed = previous_feed
            elif rapid_mode == "fixed": movement_feed = fixed_rapid_feed
        if any(k in values for k in "XYZ"):
            if units is None or not absolute or motion is None: raise ValueError("Laser motion without explicit units/mode")
            end = [values.get(k,xyz[i]) for i,k in enumerate("XYZ")]
            if end != xyz:
                if motion == 1 and (feed is None or not per_minute): raise ValueError("Laser linear motion without feed/mode")
                event = dict(kind="rapid" if motion == 0 else "linear",tool=1,from_=xyz.copy(),to=end,
                    feed=movement_feed if motion == 1 or marlin else None,spindle=None,rpm=None,
                    laserPower=power,powerMode=mode)
                event["from"] = event.pop("from_")
                events.append(event)
                if motion == 1 and power is not None and power > 0: cuts.append(event)
            xyz = end
    if units is None or not cuts: raise ValueError("Laser program has no explicit units or powered path")
    return dict(events=events,cuts=cuts,end=xyz,units=units,explicitTermination=False,
        laserControls=dict(profile=profile,initialCommandedXyz=[0,0,0],finalPower=power,
            rapidMode=rapid_mode,finalMovementBarrier=bool(events and events[-1]["kind"] == "synchronize"),
            powerMeaning="FAN_DUTY_0_255" if fan else "RAW_CONTROLLER_LASER_S_NOT_PHYSICAL_RPM",
            sourceGroup="Single source path group 1; NOT a selected hardware tool",
            limits="Commanded state only. No GRBL $30/$32, Marlin power mapping, inline mode, FAN electrical timing, beam/material or physical interlocks simulated. Marlin assumes standard cutter mode and INCH_MODE_SUPPORT for IN."))


def laser_signature(parsed):
    return [{k:e[k] for k in ("tool","from","to","feed","spindle","rpm","laserPower","powerMode")} for e in parsed["cuts"]]


def audit_laser(case, parsed, equivalent):
    from shapely import wkt
    issues = []
    if case["operation"] != "geometry" or len(case["tools"]) != 1:
        raise ValueError("Laser comparison requires one source path group, not milling tool selections")
    tool = case["tools"][0]
    if tool["id"] != 1: raise ValueError("Laser source group must be explicitly numbered 1, not a hardware tool ID")
    p, job = tool["parameters"], case["job"]
    if parsed["units"] != case["units"]: issues.append("Laser units differ from the source")
    expected = []
    def paths(shape):
        if shape.geom_type in ("LineString","LinearRing"): return [shape]
        if shape.geom_type == "Polygon": return [shape.exterior,*shape.interiors]
        if shape.geom_type in ("MultiLineString","MultiPolygon","GeometryCollection"):
            return [path for part in shape.geoms for path in paths(part)]
        raise ValueError("Laser source requires XY paths; point exposure is not modeled")
    for path in paths(wkt.loads(tool["wkt"])):
        expected.extend((list(a[:2]),list(b[:2])) for a,b in zip(path.coords,list(path.coords)[1:]))
    remaining = list(expected)
    focus = job["laserFocusZ"]
    for e in parsed["cuts"]:
        a,b = e["from"], e["to"]
        if a[2] != b[2]: issues.append("Powered Z motion is outside the XY laser contract")
        if not equivalent(e["feed"],p["feed"]): issues.append("Laser feed differs from requested feed")
        if not equivalent(e["laserPower"],p["rpm"]): issues.append("Laser S/duty differs from requested power (not physical RPM)")
        if focus is not None and (not equivalent(a[2],focus) or not equivalent(b[2],focus)):
            issues.append("Laser focus Z differs from the requested focus plane")
        match = next((i for i,(x,y) in enumerate(remaining) if equivalent(a[:2],x) and equivalent(b[:2],y)
                      or equivalent(a[:2],y) and equivalent(b[:2],x)),None)
        if match is None: issues.append("Extra/changed powered XY segment or repeated exposure")
        else: remaining.pop(match)
    if remaining: issues.append("Missing powered source XY segments")
    for e in parsed["events"]:
        if e["kind"] == "nonstandardComment":
            issues.append("Nested-parenthesis header is diagnostic text, not validated controller syntax")
        if e["kind"] == "rapid" and e["laserPower"] != 0:
            issues.append("Positioning without an explicit commanded laser stop; actual beam behavior depends on firmware")
        if case["profile"] in MARLIN_LASERS and e["kind"] == "rapid" and not equivalent(e["feed"],job["rapidFeed"]):
            issues.append("Laser Marlin G0 feed differs from requested Feed rapids (or is undeclared)")
    if not equivalent(parsed["end"][2],job["endZ"]): issues.append("Laser final Z differs from requested height")
    if parsed["laserControls"]["finalPower"] != 0: issues.append("Laser has no explicit final power-off command")
    if case["profile"] in MARLIN_LASERS and not parsed["laserControls"]["finalMovementBarrier"]:
        issues.append("Laser Marlin final parking is outside the final M400 barrier")
    return sorted(set(issues))
