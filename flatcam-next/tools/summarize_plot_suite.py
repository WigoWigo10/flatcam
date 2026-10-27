"""Export per-repeat presented FPS and UI/CPU metrics without mixing the two clocks."""
import argparse
import csv
import json
from pathlib import Path


def thread_cpu_ms(phase, prefix):
    counters = phase.get("threadCpuTime", {})
    if counters.get("status") != "AVAILABLE":
        return None
    selected = [t["cpuMs"] for t in counters.get("threads", []) if t["name"].startswith(prefix)]
    return sum(selected) if selected else None


def collect(manifest):
    rows, failures = [], []
    for entry in manifest["Results"]:
        if entry["Status"] != "COMPLETED":
            failures.append({"name": entry["Name"], "error": entry.get("Error")})
            continue
        report_path = Path(entry["Report"])
        try:
            report = json.loads(report_path.read_text(encoding="utf-8-sig"))
            fps = json.loads(report_path.with_name("presentmon-analysis.json").read_text(encoding="utf-8-sig"))
            if report["status"] != "COMPLETED" or not report["sourceUnchanged"] or fps["status"] != "MEASURED":
                raise ValueError("Benchmark/capture is incomplete or source was modified")
            displayed = {(p["name"], p["repeat"]): p for p in fps["phases"]}
            for phase in report["phases"]:
                frames = displayed.get((phase["name"], phase["repeat"]), {})
                before, after = phase["rasterBefore"], phase["rasterAfter"]
                lines = phase["cncPasses"]["PASS_LINES"]
                clipped = lines.get("clippedCalls", 0) > 0
                rows.append({"run": entry["Name"], "phase": phase["name"], "repeat": phase["repeat"],
                             "cncLineRendering": report.get("cncLineRendering", "REFERENCE"),
                             "visualValidation": report["visualValidation"], "adapter": report["graphics"]["adapter"],
                             "fpsStatus": frames.get("status", "UNAVAILABLE"), "displayedFrames": frames.get("displayedFrames", 0),
                             "averageFps": frames.get("averageFps"), "onePercentLowFps": frames.get("onePercentLowFps"),
                             "frameTimeMsP95": frames.get("frameTimeMsP95"), "frameTimeMsP99": frames.get("frameTimeMsP99"),
                             "worstFrameTimeMs": frames.get("worstFrameTimeMs"),
                             "uiPulseHz": phase["uiPulseCadence"]["averageHz"],
                             "uiQueueMsP95": phase["uiQueueMs"]["p95"],
                             "readyCommandsMsP95": phase["requestToReadyCommandsMs"]["p95"],
                             "passLineCommandMsTotal": phase["cncPasses"]["PASS_LINES"].get("commandEmissionMs", 0),
                             "quantumRendererCpuMs": thread_cpu_ms(phase, "QuantumRenderer"),
                             "fxApplicationCpuMs": thread_cpu_ms(phase, "JavaFX Application Thread"),
                             "passSegmentsAccepted": lines.get("acceptedSegments") if clipped else None,
                             "passSegmentsEmitted": lines.get("emittedSegments") if clipped else None,
                             "passSegmentsCulled": lines.get("culledSegments") if clipped else None,
                             "imageDrawOmissions": after["drawOmissionsCumulative"] - before["drawOmissionsCumulative"],
                             "report": str(report_path)})
        except (OSError, ValueError, KeyError) as error:
            failures.append({"name": entry["Name"], "error": str(error)})
    return rows, failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.suite.read_text(encoding="utf-8-sig"))
    rows, failures = collect(manifest)
    directory = args.suite.parent
    with (directory / "summary.csv").open("x", encoding="utf-8", newline="") as output:
        if rows:
            writer = csv.DictWriter(output, fieldnames=list(rows[0]))
            writer.writeheader(); writer.writerows(rows)
    def fmt(value):
        return "unavailable" if value is None else f"{value:.2f}"
    lines = ["# Plot presentation benchmark", "",
             f"Suite status: {manifest['Status']}; accepted runs only below. Failures: {len(failures)}.", "",
             "FPS = ETW displayed-frame cadence. UI Hz is not presented FPS. OMIT runs intentionally change the image.",
             "Each repeat remains separate; repeat 1 can include cold/JIT work. No FPS for event-driven selection/editing is implied.", "",
             "| Run | Phase | Repeat | Frames | FPS | 1% low | Frame p95 ms | UI Hz |",
             "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for row in rows:
        if row["phase"] not in ("pan", "zoom-in-out"):
            continue
        lines.append(f"| {row['run']} | {row['phase']} | {row['repeat']} | {row['displayedFrames']} | "
                     f"{fmt(row['averageFps'])} | {fmt(row['onePercentLowFps'])} | {fmt(row['frameTimeMsP95'])} | {fmt(row['uiPulseHz'])} |")
    for failure in failures:
        lines.extend(["", f"FAILED: {failure['name']}: {failure['error']}"])
    with (directory / "summary.md").open("x", encoding="utf-8") as output:
        output.write("\n".join(lines) + "\n")
    print(directory / "summary.md")
    return 1 if failures or manifest["Status"] != "COMPLETED" else 0


if __name__ == "__main__":
    raise SystemExit(main())
