"""Audit actual binary union operands independently, without normalizing rings."""
import argparse
import json
from pathlib import Path
from shapely import wkt
from shapely.ops import unary_union
from compare_cam_python import dependency_metadata, leaves


def signature(geometry):
    return [{"bounds": list(part.bounds), "start": list(part.exterior.coords[0]),
             "holes": [list(ring.coords[0]) for ring in part.interiors]}
            for part in leaves(geometry)]


def audit(document):
    # binary union may shortcut disjoint bounds, whereas unary union may sort
    # and recursively union again. Neither column is the actual internal GEOS
    # binary operand call. These controls must not be interpreted as parity.
    results = []
    for case in document["cases"]:
        source = wkt.loads(case["inputWkt"])
        copper = unary_union([source])
        operations = []
        for operation in case["unionOperations"]:
            left, right = wkt.loads(operation["left"]), wkt.loads(operation["right"])
            actual = wkt.loads(operation["result"])
            expected = left.union(right)
            operations.append({"range": operation["range"],
                "areaDelta": actual.symmetric_difference(expected).area,
                "fx": signature(actual), "pythonBinary": signature(expected),
                "pythonUnary": signature(unary_union([left,right]))})
        results.append({"id": case["id"], "fxCopper": signature(wkt.loads(case["candidateCopperWkt"])),
                        "pythonCopper": signature(copper), "operations": operations})
    return {"schema": 1, **dependency_metadata(),
            "scope": "Diagnostic operands only, not production parity approval", "cases": results}


def public_oracle(document):
    """Public golden: part IDs and exact input vertex used to begin each union ring."""
    fixtures=[]
    for case in document["cases"]:
        source=wkt.loads(case["sourceWkt"])
        parts=leaves(source)
        order=[]
        vertices=[]
        for polygon in leaves(unary_union([source])):
            cx,cy=polygon.centroid.coords[0]
            index=round(cx/6)+7*round(cy/5)
            original=next(part for part in parts if round(part.centroid.x/6)+7*round(part.centroid.y/5)==index)
            start=polygon.exterior.coords[0]
            vertex=min(range(len(original.exterior.coords)-1),
                       key=lambda i: (original.exterior.coords[i][0]-start[0])**2+(original.exterior.coords[i][1]-start[1])**2)
            order.append(index)
            vertices.append(vertex)
        fixtures.append({"count":case["count"],"order":order,"vertices":vertices})
    return {"schema":1,**dependency_metadata(),"scope":"Public GEOS Windows union ring representation", "cases":fixtures}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--trace", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--public-oracle", action="store_true")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a new output file")
    document=json.loads(args.trace.read_text(encoding="utf-8"))
    report = public_oracle(document) if args.public_oracle else audit(document)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    for case in [] if args.public_oracle else report["cases"]:
        differences = [operation["range"] for operation in case["operations"]
                       if operation["fx"] != operation["pythonUnary"]]
        print(json.dumps({"id": case["id"], "differentRepresentations": differences}))
