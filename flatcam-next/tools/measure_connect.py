"""Current Connect on/off pairs plus a separate instrumented/JFR diagnostic pass.
Run after building CAM and copying dependencies. No production classes are edited.
"""
import argparse, hashlib, json, os, shutil, statistics, subprocess, time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CASES=[
    dict(id='horizon-single-lines',board='horizon-choc',method='LINES',rest=False,diametersMm=[.4]),
    dict(id='horizon-panel-2x2-lines',board='horizon-choc',method='LINES',rest=False,diametersMm=[.4],columns=2,rows=2),
    dict(id='audio-three-clear-rest-lines',board='audio-watermark',method='LINES',rest=True,diametersMm=[1.2,.8,.4],iso=True),
    dict(id='breadboard-panel-three-clear-rest-standard',board='breadboard-power-supply',method='STANDARD',rest=True,diametersMm=[1.2,.8,.4],iso=True,columns=2,rows=2),
]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--pairs',type=int,default=3)
    parser.add_argument('--case-filter',default='')
    parser.add_argument('--skip-profile',action='store_true')
    args=parser.parse_args()
    if not 1<=args.pairs<=10:parser.error('pairs must be 1..10')
    chosen=[c for c in CASES if not args.case_filter or args.case_filter in c['id']]
    if not chosen:parser.error('case-filter matched no cases')
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=False)
    classes=output/'classes';classes.mkdir()
    home=Path(os.environ['JAVA_HOME']);java=home/'bin/java.exe';javac=home/'bin/javac.exe'
    snapshot=output/'cam-snapshot.jar'
    cp=os.pathsep.join([str(classes),str(snapshot),str(ROOT/'flatcam-fx/target/dependency/*')])
    sources=[ROOT/'tools'/f for f in ['ConnectProfileAgent.java','ConnectMeasurementProbe.java','SummarizeConnectJfr.java','SummarizeClearingPhases.java']]
    environment=os.environ.copy()
    for key in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH']:environment.pop(key,None)
    def run(command,log,timeout=240):
        start=time.perf_counter()
        try:r=subprocess.run(list(map(str,command)),cwd=ROOT,env=environment,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            log.write_bytes(failure.stdout or b'');raise
        log.write_bytes(r.stdout)
        if r.returncode:raise RuntimeError(f'Exit {r.returncode}: {log}')
        return (time.perf_counter()-start)*1000
    run([home/'bin/jar.exe','--create','--file',snapshot,'-C',ROOT/'flatcam-cam/target/classes','.'],output/'snapshot.log')
    run([javac,'-encoding','UTF-8','-cp',cp,'-d',classes,*sources],output/'compile.log')
    manifest=output/'agent.mf';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: ConnectProfileAgent\n\n',encoding='utf-8')
    agent=output/'connect-agent.jar'
    run([home/'bin/jar.exe','--create','--file',agent,'--manifest',manifest,'-C',classes,'.'],output/'jar.log')
    report={'schema':1,'baseRevision':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'java':str(java),'heap':'4g','warmup':'3 identical small Lines/Connect runs then explicit GC before timing; fresh JVM per sample; large-case JIT included',
            'cpuMetric':'Current generation thread CPU; allocations cumulative on that thread, not peak RAM. JFR only covers generation.',
            'scope':'NCC public copper bounding rectangle, overlap 40%, Contour on, 3 mm gaps between copies. Diagnostic setup, not a physical CNC recipe. Single and 2x2 translated Gerber copies; sources unchanged.',
            'expectedCases':len(chosen),'complete':False,'runs':[],'cases':[]}
    tracked_sources=[*sources,Path(__file__).resolve(),ROOT/'flatcam-cam/src/main/java/org/flatcam/cam/ncc/NccGenerator.java']
    report['sourceFilesSha256']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in tracked_sources}
    report['camSnapshotSha256']=hashlib.sha256(snapshot.read_bytes()).hexdigest()
    (output/'sources').mkdir()
    for source in tracked_sources:shutil.copy2(source,output/'sources'/source.name)
    def save():(output/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    inventory=ROOT/'samples/community/validation/fx-import.json'
    report['inventorySha256']=hashlib.sha256(inventory.read_bytes()).hexdigest();save()
    for config in chosen:
        case_dir=output/config['id'];case_dir.mkdir();config_path=case_dir/'case.json'
        config_path.write_text(json.dumps(config),encoding='utf-8');references={}
        for pair in range(args.pairs):
            for connect in ([False,True] if pair%2==0 else [True,False]):
                label=f'pair-{pair+1}-'+('on' if connect else 'off');path=case_dir/(label+'.json')
                command=[java,'-Xmx4g','-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL','-cp',cp,'ConnectMeasurementProbe',inventory,config_path,str(connect).lower(),path]
                process_ms=run(command,case_dir/(label+'.log'));result=json.loads(path.read_text(encoding='utf-8'))
                fingerprints={k:result[k] for k in ['geometrySha256','clearingAreaSha256','sourceSha256','tools','failedPolygons']}
                if connect in references and references[connect]!=fingerprints:raise AssertionError(f'Non-deterministic output: {config["id"]} {label}')
                references[connect]=fingerprints;result.update(pair=pair+1,processMs=process_ms)
                report['runs'].append(result);save()
                print(f'{config["id"]} {label}: {result["generationMs"]:.1f} ms',flush=True)
        runs=[r for r in report['runs'] if r['case']['id']==config['id']]
        summary={'id':config['id'],'medianGenerationMs':{str(c):statistics.median(r['generationMs'] for r in runs if r['connect']==c) for c in [False,True]},'deterministicPerVariant':True}
        if not args.skip_profile:
            path=case_dir/'profile.json';jfr=case_dir/'profile.jfr'
            command=[java,'-Xmx4g','-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL','-XX:FlightRecorderOptions=stackdepth=128',f'-javaagent:{agent}',f'-Dflatcam.connect.jfr={jfr}',
                     '-cp',cp,'ConnectMeasurementProbe',inventory,config_path,'true',path]
            run(command,case_dir/'profile.log');profile=json.loads(path.read_text(encoding='utf-8'))
            if {k:profile[k] for k in references[True]}!=references[True]:raise AssertionError(f'Instrumentation changed output: {config["id"]}')
            run([java,'-cp',cp,'SummarizeConnectJfr',jfr,case_dir/'jfr-summary.json',ROOT/'flatcam-cam/src/main/java/org/flatcam/cam/ncc/NccGenerator.java'],case_dir/'jfr-summary.log')
            summary['diagnosticProfile']=profile;summary['jfrSummary']=json.loads((case_dir/'jfr-summary.json').read_text(encoding='utf-8'))
            run([java,'-cp',cp,'SummarizeClearingPhases',jfr,case_dir/'clearing-phases.json',ROOT/'flatcam-cam/src/main/java/org/flatcam/cam/ncc/NccGenerator.java'],case_dir/'clearing-phases.log')
            summary['clearingPhases']=json.loads((case_dir/'clearing-phases.json').read_text(encoding='utf-8'))
        report['cases'].append(summary);save()
    report['complete']=True;save()
    print('Report:',output/'report.json',flush=True)

if __name__=='__main__':main()
