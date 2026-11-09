"""Compare a public rounded-pad fixture using the original Python NCC GUI body/Paint kernel.

No original algorithm or user project/preferences are modified. Plots are generated from
actual outputs in a common renderer; they are not screenshots of the two applications.
"""
import argparse
import hashlib
import json
import logging
import sys
from pathlib import Path

from compare_cam_python import dependency_metadata, legacy_paths, leaves, lines, metrics


def coverage_paths(paths):
    """Apply the existing comparator's spatial union policy symmetrically.

    The original legacy helper already unions its result. Keep raw FX paths separately;
    neither spatial union can establish execution order, lifts or repeated-cut equivalence.
    """
    from shapely.ops import unary_union
    return unary_union(lines(paths))


def footprint_metrics(paths, area, copper, diameter):
    swept = paths.buffer(diameter / 2, resolution=64)
    reachable = area.buffer(-diameter / 2, resolution=64).buffer(diameter / 2, resolution=64)
    residual = reachable.difference(swept).area
    # Same explicit numeric envelope as the Java coverage guard, not a physical tolerance.
    import math
    tolerance = diameter / 2 * (.01 + 1 - math.cos(math.pi / 256))
    return {
        "reachableArea": reachable.area,
        "uncoveredReachableArea": residual,
        "uncoveredReachablePercent": residual / reachable.area * 100 if reachable.area else 0,
        "sweptArea": swept.area,
        "padInteriorIntersectionArea": swept.intersection(copper.buffer(-tolerance, resolution=64)).area,
        "numericPadErosionTolerance": tolerance,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root", type=Path, required=True)
    parser.add_argument("--fx-export", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--strict", action="store_true", help="Exit nonzero on path differences as well as oracle errors")
    args = parser.parse_args()
    root = args.legacy_root.resolve()
    original_sources = [root/"camlib.py", root/"appTools/ToolNCC.py", root/"appTools/ToolPaint.py"]
    source_hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in original_sources}
    sys.path[:0] = [str(root), str(root / "tests")]
    import camlib
    from parser_baseline import install_stub_app
    from shapely import wkt
    from shapely.ops import unary_union
    install_stub_app()
    camlib.log.setLevel(logging.WARNING)
    engine = camlib.Geometry()
    engine.geo_steps_per_circle = 64
    document = json.loads(args.fx_export.read_text(encoding="utf-8"))
    if document["schema"] != 1 or document["fixture"] != "public-rounded-pads-v1":
        raise ValueError("Unexpected fixture schema")
    args.output.mkdir(parents=True, exist_ok=True)
    cache = {}
    records = []
    for case in document["cases"]:
        copper = wkt.loads(case["copperWkt"])
        board = wkt.loads(case["boardWkt"])
        source = wkt.loads(case["inputWkt"])
        before = source.wkb
        engine.solid_geometry = source
        raw_fx = wkt.loads(case["fxWkt"])
        fx = coverage_paths(raw_fx)
        try:
            python, extra = legacy_paths(case, copper, engine, root, cache)
            python = coverage_paths(python)
            if before != source.wkb:
                raise AssertionError("Legacy source mutated")
            extra.pop("_toolPaths", None)
            area = board.difference(copper)
            diameter = case["diameter"]
            match = metrics(fx, python, .003 * diameter / .5, diameter)
            fx_swept = fx.buffer(diameter / 2, resolution=64)
            py_swept = python.buffer(diameter / 2, resolution=64)
            record = dict(case, pythonWkt=python.wkt, comparison=match,
                          fxCoverage=footprint_metrics(fx, area, copper, diameter),
                          pythonCoverage=footprint_metrics(python, area, copper, diameter),
                          footprintSymmetricDifferenceArea=fx_swept.symmetric_difference(py_swept).area,
                          fxRawPathLength=raw_fx.length,
                          fxSpatialPathLength=fx.length, pythonSpatialPathLength=python.length,
                          fxSpatialSegmentCount=len(lines(fx)), pythonSpatialSegmentCount=len(lines(python)),
                          **extra)
            record["status"] = "MATCH_SAMPLED" if (match["matchesSampledCriteria"]
                    and not case["fxFailedPolygons"] and not extra.get("pythonFailedPolygons", 0)
                    and extra.get("clearingAreaMatches", True)) else "DIFFERENT"
        except Exception as error:
            record = dict(case, status="ORACLE_ERROR", error=repr(error))
        records.append(record)
    report = {"schema": 1, "fixture": document["fixture"], **dependency_metadata(),
              "scope": "Public shared pad polygons, automatic Lines only, single cutter, no Rest or machine simulation",
              "comparisonPolicy": "Both path sets are unioned for spatial comparison; raw FX paths retained for drawing. Python helper output is already unioned. Segment counts are NOT lift counts.",
              "sourceHashes": source_hashes,
              "cases": records}
    if source_hashes != {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in original_sources}:
        raise AssertionError("Original Python sources changed during comparison")
    (args.output / "comparison.json").write_text(json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False)+"\n", encoding="utf-8")
    good = {r["id"]: r for r in records if "pythonWkt" in r}
    if len(good) == len(records):
        render(good, args.output)
    counts = {status: sum(r["status"] == status for r in records)
              for status in ("MATCH_SAMPLED", "DIFFERENT", "ORACLE_ERROR")}
    print(json.dumps({"counts": counts, "report": str(args.output/"comparison.json")}, ensure_ascii=False))
    if counts["ORACLE_ERROR"] or (args.strict and counts["DIFFERENT"]):
        raise SystemExit(1)


def render(records, output):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.path import Path as MplPath
    from matplotlib.patches import PathPatch
    from shapely import wkt

    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10})
    def draw(ax, record, system, zoom=None):
        ax.set_facecolor("#10151b")
        for poly in leaves(wkt.loads(record["copperWkt"])):
            vertices, codes = [], []
            for ring in [poly.exterior, *poly.interiors]:
                points = list(ring.coords)
                vertices.extend(points)
                codes.extend([MplPath.MOVETO] + [MplPath.LINETO]*(len(points)-2) + [MplPath.CLOSEPOLY])
            ax.add_patch(PathPatch(MplPath(vertices, codes), facecolor="#93bf57", edgecolor="#b2d67c", linewidth=.55, zorder=3))
        paths = wkt.loads(record["fxWkt"] if system == "FX" else record["pythonWkt"])
        for line in lines(paths):
            x, y = line.xy
            ax.plot(x, y, color="#ff5a54", linewidth=.65, solid_capstyle="round", zorder=4)
        ax.set_aspect("equal")
        ax.set_xlim(*(zoom[:2] if zoom else (-.4,36.4)))
        ax.set_ylim(*(zoom[2:] if zoom else (-.4,24.4)))
        ax.grid(color="#29343e", linewidth=.55, zorder=0)
        ax.tick_params(colors="#5a6570", labelsize=9)
        ax.set_xlabel("X (mm)")
        ax.set_ylabel("Y (mm)")
        for spine in ax.spines.values(): spine.set_edgecolor("#cad2d9")

    fig, axes = plt.subplots(2, 2, figsize=(13,9), layout="constrained")
    fig.suptitle("Pads arredondados • Lines no FX e no Python", fontsize=19, fontweight="bold")
    for row, operation in enumerate(["ncc", "paint"]):
        record = records[f"{operation}-mm-c1-e1"]
        for col, system in enumerate(["FX", "Python"]):
            ax = axes[row,col]
            draw(ax, record, system)
            coverage = record["fxCoverage" if system == "FX" else "pythonCoverage"]
            residual = coverage['uncoveredReachablePercent']
            label = "< 0,0001%" if residual < .0001 else f"{residual:.4f}%".replace('.', ',')
            ax.set_title(f"{system} — {operation.upper() if operation == 'ncc' else 'Paint'}\n"
                         f"Área alcançável sem corte: {label}", loc="left", fontsize=11)
    fig.supxlabel("Verde: cobre / pads  •  Vermelho: centro da fresa\n"
                  "Fresa Ø 0,8 mm  •  Overlap 40%  •  Connect ligado  •  Contour ligado  •  Direção automática\n"
                  "Trajetos reais dos geradores, exibidos na mesma escala. Exemplo sintético; sem simulação da máquina.", fontsize=10)
    fig.savefig(output/"rounded-pads-fx-python.png", dpi=170, facecolor="white")
    plt.close(fig)

    # Controlled view of the reported symptom: only Contour changes between rows.
    fig, axes = plt.subplots(2, 2, figsize=(11,9), layout="constrained")
    fig.suptitle("Detalhe do pad circular e da pista • NCC Lines", fontsize=18, fontweight="bold")
    for row, contour in enumerate([False,True]):
        record = records[f"ncc-mm-c1-e{int(contour)}"]
        for col, system in enumerate(["FX", "Python"]):
            draw(axes[row,col], record, system, (5,14,12,21))
            axes[row,col].set_title(f"{system} — Contour {'ligado' if contour else 'desligado'}", loc="left", fontsize=12)
    fig.supxlabel("Verde: cobre  •  Vermelho: centro da fresa Ø 0,8 mm\n"
                  "A distância até o cobre corresponde ao raio da fresa; Contour acrescenta o acabamento periférico.", fontsize=10)
    fig.savefig(output/"rounded-pads-contour-detail.png", dpi=170, facecolor="white")
    plt.close(fig)

    fig, axes = plt.subplots(2, 2, figsize=(11,9), layout="constrained")
    fig.suptitle("Retângulo com cantos arredondados • NCC Lines", fontsize=18, fontweight="bold")
    for row, contour in enumerate([False,True]):
        record = records[f"ncc-mm-c1-e{int(contour)}"]
        for col, system in enumerate(["FX", "Python"]):
            draw(axes[row,col], record, system, (20.5,32,12,21))
            axes[row,col].set_title(f"{system} — Contour {'ligado' if contour else 'desligado'}", loc="left", fontsize=12)
    fig.supxlabel("Verde: cobre  •  Vermelho: centro da fresa Ø 0,8 mm\n"
                  "Mesmos parâmetros e mesma escala; apenas Contour muda entre as linhas.", fontsize=10)
    fig.savefig(output/"rounded-rectangle-contour-detail.png", dpi=170, facecolor="white")
    plt.close(fig)


if __name__ == "__main__":
    main()
