"""Audit RAW Lines paths before union: repeated centerline travel, interior ends and swept coverage.

Area overlap from the configured stepover is expected. This report distinguishes it
from traversing the same centerline again. It is a geometric diagnostic, not CNC simulation.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from shapely import wkt
from shapely.geometry import Point, LineString
from shapely.ops import unary_union
from compare_cam_python import lines
from compare_rounded_pad_lines import footprint_metrics


def audit(raw, area, copper, diameter, compute_coverage=True):
    paths=lines(raw)
    spatial=unary_union(paths)
    duplicate=max(0.0,raw.length-spatial.length)
    safe=area.buffer(-diameter/1.99999999,resolution=64)
    tolerance=diameter*.01
    interior=[];open_ends=0
    if not safe.is_empty:
        boundary=safe.boundary
        # Long PCB boundaries make a full distance scan per endpoint quadratic.
        # Index bounded pieces, retaining all coordinates and exact distances.
        from shapely.strtree import STRtree
        pieces=[]
        for ring in lines(boundary):
            coordinates=list(ring.coords)
            pieces.extend(LineString(coordinates[i:i+33]) for i in range(0,len(coordinates)-1,32))
        boundary_index=STRtree(pieces)
        for i,path in enumerate(paths):
            if path.is_closed:continue
            for coordinate in (path.coords[0],path.coords[-1]):
                open_ends+=1
                point=Point(coordinate)
                nearest=boundary_index.nearest(point)
                distance=point.distance(nearest if nearest is not None else boundary)
                if distance>tolerance:
                    interior.append({'path':i,'point':list(coordinate),'distanceFromSafeBoundary':distance})
    result={'rawPathCount':len(paths),'rawLength':raw.length,'spatialLength':spatial.length,
            'repeatedCenterlineLength':duplicate,
            'repeatedCenterlinePercent':duplicate/raw.length*100 if raw.length else 0,
            'openEndpointCount':open_ends,'interiorEndpointCount':len(interior),
            'endpointTolerance':tolerance,'interiorEndpointExamples':interior[:8]}
    # Independent GEOS check of omitted components, not the FX diagnostic counters.
    # This is intentionally NOT a swept-material coverage measurement.
    from shapely.strtree import STRtree
    path_index=STRtree(paths) if paths else None
    unserved=[];exact_unserved=[];near_boundary=[]
    magnitude=max(1.0,*(abs(value) for value in area.bounds)) if not area.is_empty else 1.0
    region_tolerance=max(diameter*1e-10,32*math.ulp(magnitude))
    minimum_near_span=max(16*region_tolerance,diameter*1e-6)
    for component in polygons(safe):
        if path_index is not None and any(component.intersection(path).length>0 for path in path_index.query(component)):
            continue
        exact_unserved.append(component)
        # GEOS and the Java buffer can differ by a few ULP at the same boundary.
        # Keep that exact miss visible, and require a positive span far longer
        # than the tolerance so an endpoint touch does not become a pass.
        expanded=component.buffer(region_tolerance)
        nearby=[] if path_index is None else path_index.query(expanded)
        span=max((expanded.intersection(path).length for path in nearby),default=0)
        if span>minimum_near_span:
            near_boundary.append({'bounds':list(component.bounds),'cutterCenterArea':component.area,
                                  'distance':min(component.distance(path) for path in nearby),
                                  'intersectionLengthWithinTolerance':span})
        else:unserved.append(component)
    result['regions']={'reachableComponentCount':len(polygons(safe)),
                      'exactUnservedReachableComponentCount':len(exact_unserved),
                      'nearBoundaryComponentCount':len(near_boundary),
                      'numericalTolerance':region_tolerance,
                      'minimumNearBoundarySpan':minimum_near_span,
                      'nearBoundaryExamples':near_boundary[:8],
                      'unservedReachableComponentCount':len(unserved),
                      'unservedCutterCenterArea':sum(part.area for part in unserved)}
    if not compute_coverage:
        result['coverage']=None
        result['coverageStatus']='NOT_MEASURED'
    elif not spatial.is_empty:
        result['coverage']=footprint_metrics(spatial,area,copper,diameter)
    else:result['coverage']=None
    return result


def polygons(geometry):
    if geometry.is_empty:return []
    if geometry.geom_type=='Polygon':return [geometry]
    return [polygon for part in getattr(geometry,'geoms',[]) for polygon in polygons(part)]


def overlap_example(raw):
    """Return an exact positive-length intersection between two distinct raw paths."""
    from shapely.strtree import STRtree
    paths=lines(raw);tree=STRtree(paths);indexes={id(g):i for i,g in enumerate(paths)}
    best=None
    for i,path in enumerate(paths):
        for candidate in tree.query(path):
            j=indexes[id(candidate)]
            if j<=i:continue
            intersection=path.intersection(candidate)
            if intersection.length>1e-8 and (best is None or intersection.length>best['length']):
                best={'paths':[i,j],'length':intersection.length,'wkt':intersection.wkt}
    return best


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--rounded',type=Path)
    p.add_argument('--community',type=Path)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--skip-coverage',action='store_true',help='Raw overlap/endpoints only; coverage remains explicitly unmeasured')
    args=p.parse_args();records=[]
    if args.rounded:
        original=json.loads(args.rounded.read_text(encoding='utf-8'))
        for case in original['cases']:
            raw=wkt.loads(case['fxWkt']);copper=wkt.loads(case['copperWkt'])
            area=wkt.loads(case['boardWkt']).difference(copper)
            record={'id':case['id'],'source':'rounded-pads','units':case['units'],'operation':case['operation'],
                    'connect':case['parameters']['connect'],'contour':case['parameters']['contour'],
                    'failedPolygons':case['fxFailedPolygons'],'diameter':case['diameter'],
                    **audit(raw,area,copper,case['diameter'],not args.skip_coverage)}
            if case['id']=='ncc-mm-c0-e1':record['exactOverlapExample']=overlap_example(raw)
            records.append(record);print(record['id'],record['repeatedCenterlineLength'],flush=True)
    if args.community:
        cache=args.output.parent/'community-metrics-cache';cache.mkdir(parents=True,exist_ok=True)
        for file in sorted(args.community.glob('*.json')):
            content=file.read_bytes();digest=hashlib.sha256(content).hexdigest()
            cached=cache/(file.stem+('-raw.json' if args.skip_coverage else '-coverage.json'))
            if cached.exists():
                saved=json.loads(cached.read_text(encoding='utf-8'))
                if saved.get('inputSha256')==digest and saved.get('auditVersion')==4:
                    records.append(saved['result']);print(file.stem,'cached',flush=True);continue
            case=json.loads(content)
            if case['status']!='GENERATED':
                records.append({k:case.get(k) for k in ['id','board','status','error']});continue
            raw=wkt.loads(case['rawPathsWkt']);area=wkt.loads(case['clearingAreaWkt']);copper=wkt.loads(case['copperWkt'])
            record={k:case[k] for k in ['id','board','units','operation','connect','contour','failedPolygons','diameter']}
            record.update(audit(raw,area,copper,case['diameter'],not args.skip_coverage));records.append(record)
            cached.write_text(json.dumps({'inputSha256':digest,'auditVersion':4,'result':record},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
            print(record['id'],record['repeatedCenterlineLength'],record['interiorEndpointCount'],flush=True)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps({'schema':1,'scope':'Raw FX path audit; no machine or Python execution/order claim',
        'centerlineUnionRemovesMultiplicityForComparisonOnly':True,'cases':records},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':main()
