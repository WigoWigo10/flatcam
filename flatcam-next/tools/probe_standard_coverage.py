"""Independent point-distance witnesses for the Standard coverage defect.

Runs the actual checked-out Python clear_polygon, without starting either UI.
Inputs and large output geometries stay in the caller's ignored target folder.
"""
import argparse
import hashlib
import json
import logging
import sys
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-root",type=Path,required=True)
    parser.add_argument("--before",type=Path,required=True)
    parser.add_argument("--after",type=Path,required=True)
    parser.add_argument("--inventory",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    root=args.legacy_root.resolve();sys.path[:0]=[str(root),str(root/"tests")]
    import camlib
    import shapely
    from shapely import wkt
    from shapely.geometry import box,Point
    from shapely.ops import unary_union
    from parser_baseline import install_stub_app
    install_stub_app();camlib.log.setLevel(logging.WARNING);engine=camlib.Geometry()
    def parts(g):return list(g.geoms) if hasattr(g,"geoms") else [g]
    def legacy_paths(area):
        output=[]
        for polygon in parts(area):
            result=engine.clear_polygon(polygon,.4,64,overlap=.4,connect=False,contour=True)
            if result is not None:output.extend(result.get_objects())
        return unary_union(output)
    minimal=box(0,0,4,.84);point=Point(2,.42)
    repeats=[legacy_paths(minimal).distance(point) for _ in range(10)]
    assert len(set(repeats))==1 and repeats[0]>.2
    boards={r["id"]:r for r in json.loads(args.inventory.read_text(encoding="utf-8"))["boards"]}
    rows=[]
    for name in ("breadboard-power-supply","modxo-adapter","single-sided-5v"):
        filename=name+"-STANDARD"
        core=wkt.loads((args.before/(filename+"-stable-core.wkt")).read_text(encoding="utf-8"))
        witness=max(parts(core),key=lambda p:p.area).representative_point()
        before=wkt.loads((args.before/(filename+".wkt")).read_text(encoding="utf-8"))
        after=wkt.loads((args.after/(filename+".wkt")).read_text(encoding="utf-8"))
        copper=wkt.loads(boards[name]["preview"]["wkt"]);area=box(*copper.bounds).difference(copper)
        python=legacy_paths(area)
        row={"board":name,"witness":list(witness.coords)[0],"diameter":.4,
             "beforeDistance":before.distance(witness),"afterDistance":after.distance(witness),
             "pythonUnconnectedDistance":python.distance(witness),"witnessClearanceToAllowedBoundary":area.boundary.distance(witness),
             "beforeStableCoreArea":core.area}
        assert row["beforeDistance"]>.2 and row["pythonUnconnectedDistance"]>.2
        assert row["afterDistance"]<=.2
        rows.append(row)
    from shapely.geos import geos_version_string
    report={"schema":1,"scope":"Point witnesses independently checked by Shapely distance; actual legacy clear_polygon without Connect. Not a full Python app or machine validation.",
            "python":sys.version,"shapely":shapely.__version__,"geos":geos_version_string,
            "legacySourceSha256":hashlib.sha256((root/"camlib.py").read_bytes()).hexdigest(),
            "inputSha256":hashlib.sha256(args.inventory.read_bytes()).hexdigest(),
            "minimal":{"areaWkt":minimal.wkt,"witness":[2,.42],"diameter":.4,"overlap":.4,"repeatedPythonDistances":repeats},"cases":rows}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"boards":len(rows),"minimalRepeated":len(repeats),"output":str(args.output)}))


if __name__=="__main__":main()
