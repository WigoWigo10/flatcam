"""Compare public CamKernelProbe fixtures; never patches either geometry engine.

This traces buffer boundaries and seeds, not complete CAM/CNC or machine safety.
Use a separate dependency directory to reproduce a particular GEOS version.
"""
import argparse
import json
import math
import sys
from pathlib import Path

from compare_cam_python import dependency_metadata, leaves


def geometry_difference(python, fx):
    """Keep empty/topological differences explicit, instead of reporting distance zero."""
    result = {"areaDelta": python.symmetric_difference(fx).area,
              "pythonEmpty": python.is_empty, "fxEmpty": fx.is_empty}
    result["boundaryHausdorff"] = (None if python.is_empty or fx.is_empty else
                                   python.boundary.hausdorff_distance(fx.boundary))
    return result


def compare(document):
    from shapely import wkt
    from shapely.geometry import MultiPolygon, Polygon

    def reverse_polygon(polygon):
        return Polygon(list(polygon.exterior.coords)[::-1],
                       [list(ring.coords)[::-1] for ring in polygon.interiors])

    def reverse_area(area):
        return reverse_polygon(area) if isinstance(area, Polygon) else MultiPolygon(
            [reverse_polygon(polygon) for polygon in area.geoms])
    if document["schema"] != 1:
        raise ValueError("Unsupported kernel probe schema")
    results = []
    for case in document["cases"]:
        if case["id"] == "synthetic-scanline":
            reference, perturbed = (wkt.loads(case[name]["wkt"]) for name in ("reference", "perturbed"))
            results.append({"id": case["id"],
                            "boundaryHausdorff": reference.boundary.hausdorff_distance(perturbed.boundary),
                            "javaSeedDelta": math.dist(case["reference"]["seed"], case["perturbed"]["seed"]),
                            "pythonSeedDelta": reference.representative_point().distance(perturbed.representative_point()),
                            "sameReferenceSeedDelta": math.dist(case["reference"]["seed"], list(reference.representative_point().coords)[0]),
                            "samePerturbedSeedDelta": math.dist(case["perturbed"]["seed"], list(perturbed.representative_point().coords)[0])})
            continue
        diameter, step = case["diameter"], case["step"]
        if not all(math.isfinite(value) and value > 0 for value in (diameter, step)):
            raise ValueError("Diameter/step must be finite and positive")
        if case["id"] not in ("synthetic-standard", "synthetic-seed"):
            raise ValueError("Only the public synthetic fixtures are supported")
        parts = []
        for part in case["parts"]:
            source = wkt.loads(part["source"]["wkt"])
            result = {key: part[key] for key in ("index", "translation", "resolution", "epsilon") if key in part}
            if case["id"] == "synthetic-seed":
                safe = source.buffer(-diameter / 2, resolution=64)
                fx_safe = wkt.loads(part["safeArea"]["wkt"])
                result.update(geometry_difference(safe, fx_safe))
                if not safe.is_empty and not fx_safe.is_empty:
                    python_seed = list(safe.representative_point().coords)[0]
                    same_safe_seed = list(fx_safe.representative_point().coords)[0]
                    reversed_safe_seed = list(reverse_area(fx_safe).representative_point().coords)[0]
                    fx_seed = part["safeArea"]["seed"]
                    result.update(seedDelta=math.dist(fx_seed, python_seed),
                                  sameSafeSeedDelta=math.dist(fx_seed, same_safe_seed),
                                  pythonOrientationSeedDelta=math.dist(same_safe_seed, reversed_safe_seed),
                                  javaOrientationSeedDelta=math.dist(fx_seed, part["safeArea"]["reversedSeed"]),
                                  fxSeed=fx_seed, pythonSeed=python_seed, sameSafeSeed=same_safe_seed)
            else:
                current = source.buffer(-diameter / 1.999999, resolution=64)
                passes = []
                for index, trace in enumerate(part["passes"]):
                    fx = wkt.loads(trace["wkt"])
                    difference = geometry_difference(current, fx)
                    difference.update(passIndex=index, fxPoints=trace["points"],
                                      pythonPoints=sum(len(p.exterior.coords) + sum(len(r.coords) for r in p.interiors)
                                                       for p in leaves(current)))
                    passes.append(difference)
                    current = current.buffer(-step, resolution=64)
                result["passes"] = passes
            parts.append(result)
        results.append({"id": case["id"], "parts": parts})
    return {"schema": 1, **dependency_metadata(), "java": document["java"], "jts": document["jts"],
            "scope": "Public synthetic buffer/seed diagnosis, not full CAM parity or CNC validation",
            "cases": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dependency-path", type=Path)
    args = parser.parse_args()
    if args.dependency_path:
        sys.path.insert(0, str(args.dependency_path.resolve()))
    result = compare(json.loads(args.trace.read_text(encoding="utf-8")))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("java", "jts", "python", "shapely", "geos")}))
    for case in result["cases"]:
        if case["id"] == "synthetic-scanline":
            print(json.dumps(case))
        elif case["id"] == "synthetic-seed":
            nonempty = [part for part in case["parts"] if "seedDelta" in part]
            print(case["id"], "maximum seed delta:", max(part["seedDelta"] for part in nonempty),
                  "same-geometry control:", max(part["sameSafeSeedDelta"] for part in nonempty))
            print(case["id"], "orientation-only seed delta, Java:",
                  max(part["javaOrientationSeedDelta"] for part in nonempty), "Python:",
                  max(part["pythonOrientationSeedDelta"] for part in nonempty))
        else:
            for part in case["parts"]:
                distances = [p["boundaryHausdorff"] for p in part["passes"] if p["boundaryHausdorff"] is not None]
                print(case["id"], "resolution", part["index"], "maximum boundary distance:", max(distances))


if __name__ == "__main__":
    main()
