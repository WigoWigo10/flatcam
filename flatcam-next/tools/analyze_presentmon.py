"""Join PresentMon v2 QPC timestamps to native Plot phases. Standard-library only; never infer FPS from UI pulses."""
import argparse
import csv
import json
import math
import re
from collections import defaultdict
from pathlib import Path


def check_capture_log(text):
    if re.search(r"ETW (?:events|buffers) were lost|overflowed present events detected", text, re.IGNORECASE):
        raise ValueError("PresentMon reported lost/overflowed ETW data; FPS capture rejected")


def number(value):
    try:
        parsed = float(value)
        return parsed if math.isfinite(parsed) else None
    except (ValueError, TypeError):
        return None


def distribution(intervals):
    ordered = sorted(value for value in intervals if math.isfinite(value) and value > 0)
    if not ordered:
        return {"status": "INSUFFICIENT_DISPLAYED_FRAMES", "intervalSamples": 0,
                "averageFps": None, "onePercentLowFps": None}
    percentile = lambda q: ordered[max(0, math.ceil(len(ordered) * q) - 1)]
    slowest = max(1, math.ceil(len(ordered) * .01))
    return {"status": "MEASURED", "intervalSamples": len(ordered),
            "onePercentTailIntervals": slowest, "smallSampleWarning": len(ordered) < 100,
            "averageFps": 1000 / (sum(ordered) / len(ordered)),
            "onePercentLowFps": 1000 / (sum(ordered[-slowest:]) / slowest),
            "frameTimeMsP50": percentile(.5), "frameTimeMsP95": percentile(.95),
            "frameTimeMsP99": percentile(.99), "worstFrameTimeMs": ordered[-1],
            "gapsOver50Ms": sum(value >= 50 for value in ordered),
            "gapsOver100Ms": sum(value >= 100 for value in ordered)}


def analyze(report, rows):
    if report.get("status", "COMPLETED") != "COMPLETED":
        raise ValueError("A failed/incomplete benchmark must not acquire a successful FPS summary")
    frequency = int(report.get("qpcFrequency", 0))
    uncertainty = int(report.get("qpcCalibrationUncertaintyNanos", -1))
    if frequency <= 0 or uncertainty < 0:
        raise ValueError("Native QPC calibration is required; UI timing is not a substitute")
    pid = int(report["processId"])
    offset = int(report["qpcMinusNanoTimeNanos"])
    phases = report["phases"]
    groups = defaultdict(list)
    matching_rows = invalid_rows = 0
    for row in rows:
        if str(row.get("ProcessID")) != str(pid):
            continue
        matching_rows += 1
        try:
            qpc = int(row["CPUStartQPC"])
        except (KeyError, TypeError, ValueError):
            invalid_rows += 1
            continue
        display = number(row.get("DisplayedTime"))
        latency = number(row.get("DisplayLatency"))
        # NA means dropped/not displayed. Never substitute FrameTime or MsBetweenPresents.
        if display is None or display <= 0 or latency is None or latency < 0:
            invalid_rows += 1
            continue
        nano = qpc * 1_000_000_000 // frequency - offset
        display_time = nano / 1e6 + latency
        groups[row.get("SwapChainAddress", "unknown")].append((nano, display_time))
    chains = []
    for chain, frames in groups.items():
        per_phase = []
        for phase in phases:
            selected = sorted(set(display for nano, display in frames
                                  if phase["startNanoTime"] <= nano <= phase["endNanoTime"]))
            stats = distribution([b - a for a, b in zip(selected, selected[1:])])
            per_phase.append({"name": phase["name"], "repeat": phase["repeat"],
                              "displayedFrames": len(selected), **stats})
        chains.append({"swapChain": chain, "displayedFrames": len(set(display for _, display in frames)), "phases": per_phase})
    chains.sort(key=lambda chain: chain["displayedFrames"], reverse=True)
    primary = chains[0] if chains else None
    motion = [] if primary is None else [p for p in primary["phases"] if p["name"] in ("pan", "zoom-in-out")]
    complete = bool(motion) and all(p["status"] == "MEASURED" for p in motion)
    return {"schema": 1, "status": "MEASURED" if complete else "INCOMPLETE_CAPTURE", "processId": pid,
            "metric": "ETW_DISPLAYED_FRAME_CADENCE", "qpcFrequency": frequency,
            "qpcCalibrationUncertaintyNanos": uncertainty, "matchingCsvRows": matching_rows,
            "excludedNonDisplayedOrInvalidRows": invalid_rows, "swapChains": chains,
            "primarySwapChain": None if primary is None else primary["swapChain"],
            "phases": [] if primary is None else primary["phases"],
            "limits": ["Primary swapchain is selected by displayed-frame count; inspect alternatives if multiple exist",
                       "Frames are assigned by calibrated CPU frame-start time; display latency may cross a phase boundary",
                       "1% low is 1000 divided by the mean of the slowest ceil(1% of intervals)",
                       "Selection/delete are event-driven, not continuous animation FPS workloads",
                       "ETW display attribution is not an optical input-to-photon measurement",
                       "Diagnostic omissions are not visual parity or delivered optimizations"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--csv", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--capture-log", type=Path, help="Reject reported ETW loss/overflow before computing FPS")
    args = parser.parse_args()
    if args.output.resolve() in {args.report.resolve(), args.csv.resolve()}:
        parser.error("Analysis must not overwrite raw evidence")
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        if args.capture_log:
            check_capture_log(args.capture_log.read_text(encoding="utf-8-sig", errors="replace"))
        with args.csv.open(encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source)
            required = {"ProcessID", "CPUStartQPC", "SwapChainAddress", "DisplayLatency", "DisplayedTime"}
            if not required.issubset(reader.fieldnames or []):
                raise ValueError("PresentMon v2 --qpc_time with display tracking is required")
            result = analyze(report, reader)
            result["captureLogChecked"] = args.capture_log is not None
    except (OSError, ValueError, KeyError) as error:
        result = {"schema": 1, "status": "CAPTURE_UNAVAILABLE", "error": str(error)}
    with args.output.open("x", encoding="utf-8") as destination:
        json.dump(result, destination, indent=2)
    print(json.dumps({k: result[k] for k in ("status", "metric") if k in result}))
    return 0 if result["status"] == "MEASURED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
