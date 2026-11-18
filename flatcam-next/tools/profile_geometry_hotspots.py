"""Re-analyse generation-only JFRs without changing or rerunning the CAM algorithm.
Requires the complete measure_connect campaign; optional confirmations have no Java agent.
"""
import argparse, hashlib, json, os, re, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def read(path):return json.loads(path.read_text(encoding='utf-8'))

def source_markers(path):
    lines=path.read_text(encoding='utf-8').splitlines()
    def find(text):
        result=[i+1 for i,line in enumerate(lines) if text in line]
        if not result:raise ValueError('Missing source marker: '+text)
        return result
    return {'maskCore':find('Geometry core=GeosBufferOp.bufferOp(stock'),
            'maskDilation':find('needed=core.isEmpty()'),
            'maskClip':find('collectLines(OverlayNGRobust.overlay(path,needed'),
            'footprintUnion':find('footprint = footprints.isEmpty() ?'),
            'rasterIntersection':find('collectLines(line.intersection(safeArea)'),
            'standardOffsets':find('GeosBufferOp.bufferOp(polygon, -radius')+find('GeosBufferOp.bufferOp(current, -step')}

def category(site,markers):
    first=site.split(' <- ')[0]
    m=re.fullmatch(r'org\.flatcam\.cam\.ncc\.NccGenerator\.(\w+):(\d+)',first)
    if m:
        method,line=m.group(1),int(m.group(2))
        if method=='clearAreaImpl':
            for key in ['maskCore','maskDilation','maskClip','footprintUnion']:
                if line in markers[key]:return key
        if method=='linePaths' and line in markers['rasterIntersection']:return 'rasterIntersection'
        if method=='preciseRoundBuffer' and 'NccGenerator.linePaths:' in site:return 'rasterSafeArea'
        if method=='standardPaths' and line in markers['standardOffsets']:return 'standardOffsets'
        if method in ['connectSafePaths','connectOrderedPaths']:return 'connect'
    if first.startswith('org.flatcam.cam.ncc.StandardCoverageRefinement.'):return 'standardRefinement'
    if first.startswith('org.flatcam.cam.geometry.CutterSweep.'):return 'cutterSweep'
    return 'other'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--recordings',type=Path,required=True)
    p.add_argument('--confirmation',type=Path)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args();recordings=args.recordings.resolve();reference=read(recordings/'report.json')
    if len(reference['cases'])!=reference.get('expectedCases',4):p.error('Incomplete reference campaign')
    markers=source_markers(recordings/'sources/NccGenerator.java')
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=False);classes=output/'classes';classes.mkdir()
    home=Path(os.environ['JAVA_HOME']);cp=str(classes)+os.pathsep+str(ROOT/'flatcam-fx/target/dependency/*')
    source=ROOT/'tools/SummarizeGeometryHotspots.java'
    def run(command,log):
        result=subprocess.run(list(map(str,command)),cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        log.write_bytes(result.stdout)
        if result.returncode:raise RuntimeError(f'Exit {result.returncode}: {log}')
    run([home/'bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,source],output/'compile.log')
    summary={'schema':1,'status':'PROFILED_NO_PRODUCTION_OPTIMIZATION','baseRevision':reference['baseRevision'],
             'analyzerSha256':digest(source),'driverSha256':digest(Path(__file__)),
             'generatorSourceSha256':digest(recordings/'sources/NccGenerator.java'),'sourceMarkers':markers,
             'profiles':[],'confirmations':[],'complete':False}
    def analyse(case_id,recording,destination,expected=None):
        before=digest(recording)
        run([home/'bin/java.exe','-cp',cp,'SummarizeGeometryHotspots',recording,destination],destination.with_suffix('.log'))
        raw=read(destination)
        assert before==digest(recording)==raw['recordingSha256'],'Recording modified'
        if expected:
            assert raw['cpu']['events']==expected['executionSamples']['events'],'CPU sample count differs'
            assert raw['allocation']['weight']==expected['allocationSamples']['totalWeight'],'Allocation weight differs'
        row={'id':case_id,'recordingSha256':before,'metrics':raw,'exclusiveWorkloads':{}}
        for kind in ['cpu','allocation']:
            metrics=raw[kind];totals={}
            for site in metrics['applicationCallsites']:
                key=category(site['site'],markers);totals[key]=totals.get(key,0)+site['weight']
            assert sum(totals.values())+metrics['weightWithoutStack']==metrics['weight'],'Callsite weights lost/duplicated'
            row['exclusiveWorkloads'][kind]=totals
        return row
    for case in reference['cases']:
        case_id=case['id'];row=analyse(case_id,recordings/case_id/'profile.jfr',output/(case_id+'.json'),case['jfrSummary'])
        row['instrumentedConnect']=True;summary['profiles'].append(row)
        print(case_id,'CPU',row['metrics']['cpu']['events'],'workloads',row['exclusiveWorkloads']['cpu'],flush=True)
    if args.confirmation:
        paths=sorted(args.confirmation.resolve().glob('*-result.json'))
        if not paths:raise ValueError('No confirmation results')
        for result_path in paths:
            result=read(result_path);case_id=result['case']['id'];assert not result['instrumented'],'Confirmation must not use the agent'
            original=next(v for v in reference['runs'] if v['case']['id']==case_id and v['connect'])
            for key in ['geometrySha256','clearingAreaSha256','tools','sourceSha256','failedPolygons']:
                assert result[key]==original[key],'Confirmation changed output: '+key
            row=analyse(case_id,result_path.parent/(case_id+'.jfr'),output/(case_id+'-confirmation.json'))
            row['instrumentedConnect']=False;row['generationMs']=result['generationMs'];row['outputMatchesReference']=True
            summary['confirmations'].append(row)
    summary['complete']=True
    (output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print('Summary:',output/'summary.json',flush=True)

if __name__=='__main__':main()
