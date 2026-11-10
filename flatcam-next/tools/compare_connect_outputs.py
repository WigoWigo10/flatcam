"""Compare raw Connect outputs exactly, retaining order, direction and repeated vertices.

This is a behavior-preservation check for optimization, not an area-coverage or
machine-time measurement. Timing values may come from unrelated runs.
"""
import argparse
import hashlib
import json
from pathlib import Path

FIELDS=('id','board','units','operation','diameter','overlap','connect','contour',
        'boundaryPolicy','rawPathsWkt','clearingAreaWkt','copperWkt','failedPolygons','regionDiagnostics')

def compare(before,after):
    first={path.name:path for path in before.glob('*.json')}
    second={path.name:path for path in after.glob('*.json')}
    records=[]
    for name in sorted(first.keys()|second.keys()):
        entry={'file':name}
        try:
            if name not in first or name not in second:raise ValueError('Case missing from one directory')
            a=json.loads(first[name].read_text(encoding='utf-8'));b=json.loads(second[name].read_text(encoding='utf-8'))
            if a.get('status')!='GENERATED' or b.get('status')!='GENERATED':raise ValueError('Both cases must be GENERATED')
            absent=[key for key in FIELDS if key not in a or key not in b]
            if absent:raise ValueError('Missing fields: '+','.join(absent))
            differences=[key for key in FIELDS if a[key]!=b[key]]
            entry.update(status='DIFFERENT' if differences else 'EXACT_MATCH',differingFields=differences,
                         rawPathsBeforeSha256=hashlib.sha256(a['rawPathsWkt'].encode()).hexdigest(),
                         rawPathsAfterSha256=hashlib.sha256(b['rawPathsWkt'].encode()).hexdigest())
        except (ValueError,KeyError,OSError) as error:entry.update(status='UNVERIFIED',error=str(error))
        records.append(entry)
    if not records:raise ValueError('No cases found')
    counts={status:sum(entry['status']==status for entry in records) for status in ('EXACT_MATCH','DIFFERENT','UNVERIFIED')}
    return {'schema':1,'scope':__doc__.strip(),'before':str(before),'after':str(after),'counts':counts,'cases':records}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before',type=Path,required=True);parser.add_argument('--after',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    report=compare(args.before,args.after);args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'counts':report['counts'],'report':str(args.output)}))
    return 0 if report['counts']['DIFFERENT']==report['counts']['UNVERIFIED']==0 else 1

if __name__=='__main__':raise SystemExit(main())
