"""Independent native command readers. No controller macros, machine homing or physical-tool simulation."""
import math
import re

NUMBER = re.compile(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)")


def number(text):
    if not NUMBER.fullmatch(text.strip()): raise ValueError("Invalid native number: " + text)
    result = float(text)
    if not math.isfinite(result) or not -(2**31) <= result < 2**31:
        raise ValueError("Native value out of modeled range")
    return result


def read_roland(program):
    text = program.lstrip("\ufeff")
    xyz, motor, feed = [0.,0.,0.], None, None
    initialized = absolute = False
    events, cuts = [], []
    cursor = 0
    while cursor < len(text):
        if text[cursor].isspace() or text[cursor] == ";": cursor += 1; continue
        if text.startswith("!MC",cursor):
            match = re.match(r"!MC([01])(?=;|\s|$)",text[cursor:])
            if not initialized or not match: raise ValueError("Invalid/uninitialized native motor command")
            motor = int(match[1]); cursor += len(match[0])
            events.append(dict(kind="nativeMotor",tool=1,on=bool(motor)))
            continue
        end = text.find(";",cursor)
        if end < 0: raise ValueError("Native command without semicolon")
        command = text[cursor:end].strip(); cursor = end+1
        if command == "^IN":
            if initialized: raise ValueError("Repeated native reset outside the model")
            initialized = True; events.append(dict(kind="units",value="MM")); continue
        if not initialized: raise ValueError("Native command before ^IN")
        if command == "^PA": absolute = True
        elif command.startswith("V"):
            velocity = number(command[1:])
            if not .1 <= velocity <= 15: raise ValueError("Native V outside 0.1..15 mm/s")
            feed = velocity*60
            events.append(dict(kind="nativeVelocity",feed=feed))
        elif command.startswith("Z"):
            if not absolute or feed is None: raise ValueError("Native Z requires ^PA and V")
            values = command[1:].split(",")
            if len(values) != 3: raise ValueError("Native Z requires all three axes")
            target = [number(v)/40 for v in values]
            if target != xyz:
                event = dict(kind="linear",tool=1,**{"from":xyz.copy(),"to":target},feed=feed,
                             spindle=3 if motor else 0 if motor == 0 else None,rpm=None,nativeMotor=motor)
                events.append(event)
                if target[2] < 0: cuts.append(event)
            xyz = target
        else: raise ValueError("Native command outside Roland subset: " + command)
    if not initialized or not cuts: raise ValueError("Native program lacks initialization or cutting motion")
    return dict(events=events,cuts=cuts,end=xyz,units="MM",explicitTermination=False,
        rolandControls=dict(finalMotor=motor,coordinateUnitMm=.025,velocityUnit="mm/s",rpmMeaning="binary motor, no RPM command",
            limits="One manually installed tool; initial commanded XYZ assumed zero. Native motion has no G0/G1 distinction. Text rounding is not hardware accuracy. No homing, offsets, mechanical limits or physical motor confirmation modeled."))


def audit_roland(case, parsed, equivalent, geometry_paths, geometry_source):
    if case["units"] != "MM" or len(case["tools"]) != 1 or case["tools"][0]["id"] != 1:
        raise ValueError("Roland corpus requires MM and one source tool group 1")
    tool = case["tools"][0]; p = tool["parameters"]; job = case["job"]
    issues = []
    depths = [-min(i*p["depthPerPass"],p["depth"]+p.get("offsetZ",0)) for i in range(1,
              math.ceil((p["depth"]+p.get("offsetZ",0))/p["depthPerPass"])+1)] if p["multiDepth"] else [-p["depth"]-p.get("offsetZ",0)]
    plunges = [e for e in parsed["cuts"] if e["from"][:2] == e["to"][:2] and e["to"][2] < e["from"][2]]
    paths = geometry_paths(geometry_source(tool)) if case["operation"] == "geometry" else []
    visits = [list(path.coords)[0][:2] for path in paths] if paths else tool["points"] + [slot[0] for slot in tool.get("slots",[])]
    if not equivalent([e["to"][2] for e in plunges],depths*len(visits)):
        issues.append("T1: native plunge depths/count differ from requested passes")
    expected = [(list(a[:2]),list(b[:2]),z) for path in paths for a,b in zip(path.coords,list(path.coords)[1:]) for z in depths]
    if not paths: expected = [(slot[0],slot[1],z) for slot in tool.get("slots",[]) for z in depths]
    for e in parsed["cuts"]:
        a,b = e["from"],e["to"]
        if e["nativeMotor"] != 1: issues.append("T1: native cut without explicit motor-on command")
        plunge = a[:2] == b[:2]
        if not equivalent(e["feed"],p["feedZ"] if plunge else p["feed"]):
            issues.append("T1: native feed differs from unrounded requested feed")
        if not plunge:
            match = next((i for i,(x,y,z) in enumerate(expected) if equivalent(a[2],z) and equivalent(b[2],z)
                          and (equivalent(a[:2],x) and equivalent(b[:2],y) or equivalent(a[:2],y) and equivalent(b[:2],x))),None)
            if match is None: issues.append("T1: native directed-path coverage/depth differs")
            else: expected.pop(match)
    if expected: issues.append("T1: missing native source segments/passes")
    for e in parsed["events"]:
        if e["kind"] == "linear" and e["to"][2] >= 0 and not equivalent(e["feed"],job["rapidFeed"]):
            issues.append("Native positioning velocity differs from requested rapid feed")
    if not paths:
        remaining = [list(v) for v in visits for _ in depths]
        for e in plunges:
            match = next((i for i,v in enumerate(remaining) if equivalent(e["to"][:2],v)),None)
            if match is None: issues.append("T1: native hole location/multiplicity differs")
            else: remaining.pop(match)
        if remaining: issues.append("T1: missing native holes")
    if parsed["rolandControls"]["finalMotor"] != 0: issues.append("Native motor is not explicitly stopped at EOF")
    if not equivalent(parsed["end"][2],job["endZ"]): issues.append("Native final Z differs from requested height")
    if job["endX"] is not None and not equivalent(parsed["end"][:2],[job["endX"],job["endY"]]): issues.append("Native final XY differs from requested position")
    return sorted(set(issues))


def read_hpgl(program, source_units="MM"):
    if source_units not in ("MM","IN"): raise ValueError("HPGL requires explicit source-unit context MM/IN")
    text=program.lstrip("\ufeff"); cursor=0; initialized=False; down=False; absolute=True; pen=None
    xy=[0.,0.]; events=[]; cuts=[]; scale=1016 if source_units == "IN" else 40
    while cursor < len(text):
        if text[cursor].isspace(): cursor+=1; continue
        command=text[cursor:cursor+2].upper(); cursor+=2
        if command == "CO":
            match=re.match(r'\s*"([^"]*)"\s*;',text[cursor:])
            if not match: raise ValueError("Invalid HPGL quoted comment")
            value=match[1]; cursor+=len(match[0])
            if value.startswith("FCFX HPGL UNITS ") and value[16:] != source_units:
                raise ValueError("HPGL source-unit metadata conflicts with explicit context")
            continue
        end=text.find(";",cursor)
        if end < 0: raise ValueError("HPGL command without semicolon")
        arg=text[cursor:end].strip(); cursor=end+1
        if command == "IN":
            if initialized or arg: raise ValueError("Unknown/repeated HPGL reset")
            initialized=True; events.append(dict(kind="units",value=source_units)); continue
        if not initialized: raise ValueError("HPGL command before IN")
        if command == "SP":
            if down or not re.fullmatch(r"\d+",arg): raise ValueError("HPGL pen selection requires PU and an explicit integer")
            pen=int(arg); events.append(dict(kind="pen",tool=pen)); continue
        if command not in ("PU","PD","PA","PR"): raise ValueError("HPGL command outside the linear subset: "+command)
        if command in ("PU","PD"):
            down=command == "PD"
            events.append(dict(kind="penState",tool=pen,down=down))
        if command in ("PA","PR"): absolute=command == "PA"
        if not arg: continue
        coords=arg.split(",")
        if len(coords)%2: raise ValueError("HPGL coordinates must be XY pairs")
        for i in range(0,len(coords),2):
            delta=[number(coords[i]),number(coords[i+1])]
            target=delta if absolute else [xy[j]+delta[j] for j in (0,1)]
            if any(v < -32767 or v > 32768 for v in target): raise ValueError("HPGL coordinate outside modeled range")
            if target != xy:
                e=dict(kind="linear" if down else "rapid",tool=pen if pen is not None else 0,**{"from":[xy[0]/scale,xy[1]/scale,0],
                    "to":[target[0]/scale,target[1]/scale,0]},feed=None,spindle=None,rpm=None,penDown=down)
                events.append(e)
                if down: cuts.append(e)
            xy=target
    if not initialized or not cuts: raise ValueError("HPGL lacks initialization or drawn paths")
    return dict(events=events,cuts=cuts,end=[xy[0]/scale,xy[1]/scale,0],units=source_units,explicitTermination=False,
        hpglControls=dict(finalPen=pen,finalPenDown=down,coordinateUnitMm=.025,sourceUnitContext=source_units,
            limits="Source-unit context is supplied explicitly, not a native IN/MM command. Initial XY assumed zero, initial pen unknown until SP. Pen selection does not prove a physically installed pen/width. No speed, Z or physical accuracy simulated."))


def audit_hpgl(case, parsed, equivalent, geometry_paths, geometry_source):
    if case["operation"] != "geometry": raise ValueError("HPGL requires source XY geometry")
    issues=[]
    if parsed["units"] != case["units"]: issues.append("HPGL source-unit context differs")
    for tool in case["tools"]:
        expected=[(list(a[:2]),list(b[:2])) for path in geometry_paths(geometry_source(tool))
                  for a,b in zip(path.coords,list(path.coords)[1:])]
        for e in parsed["cuts"]:
            if e["tool"] != tool["id"]: continue
            a,b=e["from"][:2],e["to"][:2]
            match=next((i for i,(x,y) in enumerate(expected) if equivalent(a,x) and equivalent(b,y)
                        or equivalent(a,y) and equivalent(b,x)),None)
            if match is None: issues.append(f"T{tool['id']}: HPGL extra/quantized/repeated source segment")
            else: expected.pop(match)
        if expected: issues.append(f"T{tool['id']}: HPGL missing/unrounded source segments")
    if any(e["tool"] not in [t["id"] for t in case["tools"]] for e in parsed["cuts"]):
        issues.append("HPGL draw without explicit selected source pen")
    if parsed["hpglControls"]["finalPenDown"]: issues.append("HPGL lacks final pen-up command")
    return sorted(set(issues))
