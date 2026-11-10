"""Alternate fresh JVMs on one saved community case and require identical raw outputs.

Run from any directory after compiling the candidate. A frozen baseline CAM jar is
required; keeping it outside Maven outputs prevents accidentally benchmarking the
candidate twice. This measures generation latency, not FPS or machine cycle time.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-jar',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True,help='New output directory; existing evidence is not overwritten')
    parser.add_argument('--pairs',type=int,default=3)
    parser.add_argument('--board',default='horizon-choc')
    parser.add_argument('--case',default='horizon-choc-ncc-c1-e0')
    parser.add_argument('--budget-seconds',type=int,default=300)
    args=parser.parse_args()
    if not 1<=args.pairs<=20:parser.error('--pairs must be in [1,20]')
    if not 1<=args.budget_seconds<=600:parser.error('--budget-seconds must be in [1,600]')
    baseline=args.baseline_jar.resolve()
    if not baseline.is_file():parser.error('Baseline jar not found')
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=False)
    java_home=os.environ.get('JAVA_HOME')
    java=Path(java_home)/'bin'/('java.exe' if os.name=='nt' else 'java') if java_home else shutil.which('java')
    if not java or not Path(java).is_file():parser.error('A JDK with java is required')
    dependencies=str(ROOT/'flatcam-fx/target/dependency/*')
    classpaths={'baseline':str(baseline)+os.pathsep+dependencies,
                'optimized':str(ROOT/'flatcam-cam/target/classes')+os.pathsep+dependencies}
    report={'baselineJar':str(baseline),'baselineJarSha256':hashlib.sha256(baseline.read_bytes()).hexdigest(),
            'case':args.case,'java':str(java),'heap':'4g','jfr':False,'runs':[]}
    reference=None
    for pair in range(args.pairs):
        for variant in (['baseline','optimized'] if pair%2==0 else ['optimized','baseline']):
            directory=output/f'{pair+1}-{variant}';directory.mkdir()
            command=[str(java),'-Xmx4g',f'-Dflatcam.audit.caseFilter={re.escape(args.case)}',
                     f'-Dflatcam.audit.caseBudgetSeconds={args.budget_seconds}','--class-path',classpaths[variant],
                     'tools/LinesCommunityProbe.java','samples/community/validation/fx-import.json',str(directory),args.board]
            start=time.perf_counter()
            try:
                run=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=args.budget_seconds+60)
            except subprocess.TimeoutExpired as error:
                (directory/'console.log').write_bytes(error.stdout or b'')
                raise RuntimeError(f'{variant}: process timeout; see {directory}') from error
            (directory/'console.log').write_bytes(run.stdout)
            if run.returncode:raise RuntimeError(f'{variant}: launcher exit {run.returncode}; see {directory}')
            result=json.loads((directory/(args.case+'.json')).read_text(encoding='utf-8'))
            if result['status']!='GENERATED':raise RuntimeError(f'{variant}: {result}')
            if reference is None:reference=result
            for key in ('rawPathsWkt','clearingAreaWkt','copperWkt','regionDiagnostics','failedPolygons'):
                if result[key]!=reference[key]:raise AssertionError((variant,pair,key,'output changed'))
            entry={'pair':pair+1,'variant':variant,'generationMs':result['elapsedMs'],
                   'processMs':(time.perf_counter()-start)*1000,
                   'rawPathsSha256':hashlib.sha256(result['rawPathsWkt'].encode()).hexdigest()}
            report['runs'].append(entry);print(json.dumps(entry),flush=True)
            (output/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    report['medianGenerationMs']={variant:statistics.median(r['generationMs'] for r in report['runs'] if r['variant']==variant) for variant in classpaths}
    report['medianRatio']=report['medianGenerationMs']['baseline']/report['medianGenerationMs']['optimized']
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'medianGenerationMs':report['medianGenerationMs'],'medianRatio':report['medianRatio']}))

if __name__=='__main__':main()
