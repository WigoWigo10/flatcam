"""Static Mach3 probe contract only. G31 contact and G92 physical frame are deliberately NOT simulated."""
import math
import re
from laser_execution import command_text

WORD=re.compile(r"([A-Z])\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))")


def read_probe(program):
    events=[]; units=None; tool=0; feed=None; stopped=False
    for raw in program.splitlines():
        line,nested=command_text(raw)
        if not line: continue
        if nested or WORD.sub("",line).strip(): raise ValueError("Probe syntax outside the static contract")
        words=WORD.findall(line)
        if any(k not in "GMXYZFSTP" for k,v in words): raise ValueError("Unknown probe-contract word")
        if any(sum(k==letter for k,v in words)>1 for letter in "XYZFSTP"): raise ValueError("Duplicate probe-contract word")
        values={k:float(v) for k,v in words if k not in "GM"}
        if any(not math.isfinite(v) for v in values.values()): raise ValueError("Nonfinite probe value")
        codes=[k+str(float(v)) for k,v in words if k in "GM"]
        if any(float(v) not in (0,1,4,17,20,21,31,90,92,94) for k,v in words if k=="G"):
            raise ValueError("G-code outside the static probe subset")
        if any(float(v) not in (0,3,4,5,6,30) for k,v in words if k=="M"):
            raise ValueError("M-code outside the static probe subset")
        for code in codes:
            if code in ("G20.0","G21.0"):
                selected="MM" if code=="G21.0" else "IN"
                if units is not None and selected != units: raise ValueError("Unit change outside static probe contract")
                units=selected
        if "T" in values:
            if values["T"] < 1 or values["T"] != int(values["T"]): raise ValueError("Invalid probe tool")
            tool=int(values["T"])
        if "F" in values:
            feed=values["F"]
            if feed <= 0: raise ValueError("Nonpositive probe feed")
        if "M5.0" in codes: stopped=True
        if "M6.0" in codes:
            stopped=None  # Macro internals can change spindle/modal state; do not assume preservation.
            feed=None
        if "M3.0" in codes or "M4.0" in codes: stopped=False
        events.append(dict(kind="probeContractCommand",tool=tool,codes=codes,words=values,
                           modalFeed=feed,spindleStopped=stopped))
    if units is None or not any("G31.0" in e["codes"] for e in events): raise ValueError("Missing units/probe commands")
    return dict(events=events,cuts=[],end=[None,None,None],units=units,explicitTermination=False,
        probeControls=dict(executionComparable=False,
            limits="Static command contract ONLY. No contact coordinates, physical G92 offsets, macro M6 motion, operator actions or material removal are invented. Not execution parity or a usable preview."))


def audit_probe(case,parsed,equivalent):
    p=case["job"]["probe"]; issues=[]; events=parsed["events"]
    if parsed["units"] != case["units"]: issues.append("Probe contract units differ")
    for tool in case["tools"]:
        identifier=tool["id"]; cycle=[(i,e) for i,e in enumerate(events) if e["tool"]==identifier]
        probes=[(i,e) for i,e in cycle if "G31.0" in e["codes"]]
        origins=[(i,e) for i,e in cycle if "G92.0" in e["codes"]]
        if len(probes)!=2 or len(origins)!=2: issues.append(f"T{identifier}: expected two probe passes and two Z-reference settings")
        for index,(i,e) in enumerate(probes):
            if not e["spindleStopped"]: issues.append(f"T{identifier}: probe without explicit stopped spindle")
            if any(k in e["words"] for k in "XY") or not equivalent(e["words"].get("Z"),p["depth"]):
                issues.append(f"T{identifier}: wrong probe axis/limit")
            if not equivalent(e["modalFeed"],p["feed"]/(2 if index else 1)):
                issues.append(f"T{identifier}: wrong fast/slow probe feed")
            if i+2 >= len(events) or events[i+1]["codes"] != ["M0.0"] or "G92.0" not in events[i+2]["codes"]:
                issues.append(f"T{identifier}: G92 lacks an explicit contact-confirmation pause")
        for i,e in origins:
            if set(e["words"]) != {"Z"} or not equivalent(e["words"]["Z"],p["contactZ"]):
                issues.append(f"T{identifier}: wrong G92 contact height")
        changes=[i for i,e in cycle if "M6.0" in e["codes"]]
        if len(changes)!=1: issues.append(f"T{identifier}: expected one opaque M6 cycle")
        elif probes:
            restored=[c for e in events[changes[0]+1:probes[0][0]] for c in e["codes"]]
            if "G90.0" not in restored or ("G21.0" if case["units"]=="MM" else "G20.0") not in restored:
                issues.append(f"T{identifier}: units/absolute mode not explicitly restored after M6")
        starts=[i for i,e in cycle if "M3.0" in e["codes"] or "M4.0" in e["codes"]]
        if not starts or not probes or any(i<probes[-1][0] for i in starts):
            issues.append(f"T{identifier}: spindle start before completed probe command sequence")
        elif not any(e["codes"]==["M0.0"] for e in events[probes[-1][0]+3:starts[0]]):
            issues.append(f"T{identifier}: missing probe-removal confirmation before spindle start")
    return sorted(set(issues))
