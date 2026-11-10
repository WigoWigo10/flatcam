import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.geometry.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.ncc.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.*;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

/** Offline nominal 2D stock audit. Does not modify community files or machine settings. */
public class ClearingCoverageProbe {
    static final WKTReader READER=new WKTReader();
    static final Map<String,MachiningReachability.Result> ACCESS=new HashMap<>();
    static final double BUDGET=Double.parseDouble(System.getProperty("flatcam.audit.caseBudgetSeconds","240"));
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: ClearingCoverageProbe.java RAW_CASE_DIR FX_IMPORT_JSON OUTPUT_DIR");
        if(!Double.isFinite(BUDGET)||BUDGET<=0||BUDGET>600)throw new IllegalArgumentException("Case budget must be in (0,600] seconds");
        Path out=Path.of(args[2]);Files.createDirectories(out);
        try(var files=Files.list(Path.of(args[0]))) {
            for(Path file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var input=new JSONObject(Files.readString(file));
                String id=input.getString("id");if(!selected(id))continue;
                if(Files.exists(out.resolve(id+".json")))continue;
                audit(out,id,input.getString("board"),input.getString("units"),input.getString("operation"),token->{
                    Geometry area=READER.read(input.getString("clearingAreaWkt"));
                    return new Case(area,List.of(new ToolGeometry(input.getDouble("diameter"),READER.read(input.getString("rawPathsWkt")))),List.of(area));
                },input);
            }
        }
        var inventory=new JSONObject(Files.readString(Path.of(args[1])));
        for(Object object:inventory.getJSONArray("boards")) {
            var board=(JSONObject)object;if(!board.has("preview"))continue;
            for(String units:List.of("MM","IN"))for(String op:List.of("ncc","paint")) {
                String id=board.getString("id")+"-"+op+"-rest-"+units.toLowerCase();
                if(!selected(id)||Files.exists(out.resolve(id+".json")))continue;
                double scale=units.equals("IN")?1/25.4:1;
                // Pairwise: both operations, units and two non-default angles, with two Rest cutters.
                double angle=(units.equals("MM")==op.equals("ncc"))?37.5:90;
                var metadata=new JSONObject().put("rest",true).put("angle",angle).put("connect",true).put("contour",true);
                audit(out,id,board.getString("id"),units,op,token->{
                    Geometry copper=AffineTransformation.scaleInstance(scale,scale).transform(READER.read(board.getJSONObject("preview").getString("wkt")));
                    String before=copper.toText();Geometry box=copper.getFactory().toGeometry(copper.getEnvelopeInternal());
                    List<Double> diameters=List.of(.8*scale,.4*scale);
                    NccResult result;
                    if(op.equals("ncc")) {
                        var settings=new HashMap<Double,NccToolSettings>();
                        for(double d:diameters)settings.put(d,new NccToolSettings(.4,NccMethod.LINES,true,true,0,NccSeedPolicy.STABLE,angle));
                        result=NccGenerator.generate(units,copper,new NccParameters(diameters,.4,0,NccMethod.LINES,true,true,0,true,NccOrder.NONE,new NccBoundary.Area(box),List.of(),settings),token,ProgressCallback.none());
                    } else {
                        var settings=new HashMap<Double,PaintToolSettings>();
                        for(double d:diameters)settings.put(d,new PaintToolSettings(.4,0,NccMethod.LINES,true,true,angle));
                        result=NccGenerator.paint(units,box.difference(copper),new PaintParameters(diameters,.4,0,NccMethod.LINES,true,true,NccOrder.NONE,true,settings),token,ProgressCallback.none());
                    }
                    if(!before.equals(copper.toText()))throw new AssertionError("Source changed");
                    return new Case(result.clearingArea(),result.toolResults().stream().map(t->new ToolGeometry(t.toolDiameter(),t.geometry())).toList(),result.toolResults().stream().map(t->t.targetArea()==null?result.clearingArea():t.targetArea()).toList());
                },metadata);
            }
        }
    }
    interface Generate { Case get(CancellationToken token) throws Exception; }
    record Case(Geometry area,List<ToolGeometry> tools,List<Geometry> targets) { }
    static boolean selected(String id){return id.matches(System.getProperty("flatcam.audit.caseFilter",".*"));}
    static void audit(Path out,String id,String board,String units,String operation,Generate generate,JSONObject metadata)throws Exception {
        long start=System.nanoTime();CancellationToken token=()->System.nanoTime()-start>BUDGET*1e9;
        var report=new JSONObject().put("schema",1).put("id",id).put("board",board).put("units",units).put("operation",operation)
                .put("rest",metadata.optBoolean("rest",false)).put("connect",metadata.optBoolean("connect",false)).put("contour",metadata.optBoolean("contour",false))
                .put("angle",metadata.opt("angle")).put("boundaryPolicy","copper bounding rectangle; diagnostic, not a CNC setup");
        try {
            Case c=generate.get(token);double scale=units.equals("IN")?1/25.4:1;
            double min=c.tools().stream().mapToDouble(ToolGeometry::toolDiameter).min().orElseThrow();
            String key=units+"/"+min+"/"+c.area().toText();
            // Never reuse access for a different allowed area, even when board/diameter match.
            var access=ACCESS.get(key);if(access==null){access=MachiningReachability.calculate(c.area(),min,token);ACCESS.put(key,access);}
            var params=new GeometryGCodeParameters(3*scale,.1*scale,false,0,300*scale,10000,c.tools().size()>1)
                    .withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
            var raw=coverage(c,c.tools(),units,operation,params,token);
            var parsedTools=new ArrayList<ToolGeometry>();var details=new JSONArray();double totalCut=0,totalTravel=0;int paths=0;
            for(int i=0;i<c.tools().size();i++) {
                token.throwIfCancellationRequested();var tool=c.tools().get(i);
                var job=GCodeGenerator.generateGeometryCncJob(units,List.of(tool),params,Map.of(),token,GCodePreprocessor.GRBL_11_NO_M6);
                var parsed=GCodeToolpathParser.parse(job.gcode(),token,ProgressCallback.none());
                if(!parsed.plotAvailable())throw new IllegalStateException(parsed.warning());
                Geometry cut=parsed.cutCenterlines();parsedTools.add(new ToolGeometry(tool.toolDiameter(),cut));
                double tolerance=Math.sqrt(2)*.5*(units.equals("IN")?1e-6:1e-4);
                double deviation=Math.max(sampleDeviation(tool.geometry(),cut,token),sampleDeviation(cut,tool.geometry(),token));
                if(deviation>tolerance*1.001+1e-10*scale)throw new AssertionError("G-code positional fidelity: "+deviation+" > "+tolerance);
                double lengthError=Math.abs(cut.getLength()-tool.geometry().getLength());
                totalCut+=cut.getLength();totalTravel+=parsed.travelCenterlines().getLength();paths+=cut.getNumGeometries();
                details.put(new JSONObject().put("index",i).put("diameter",tool.toolDiameter()).put("sourceLength",tool.geometry().getLength()).put("cutLength",cut.getLength())
                        .put("lengthError",lengthError).put("maxSampleDeviation",deviation).put("roundingBound",tolerance).put("paths",cut.getNumGeometries())
                        .put("ownRemainingArea",raw.steps().get(i).ownRemainingArea()).put("cumulativeRemainingArea",raw.steps().get(i).cumulativeRemainingArea()));
            }
            // Multi-tool export is also parsed, so tool changes and collective ordering are exercised.
            var combined=GCodeToolpathParser.parse(GCodeGenerator.generateGeometryCncJob(units,c.tools(),params,Map.of(),token,GCodePreprocessor.GRBL_11_NO_M6).gcode(),token,ProgressCallback.none());
            if(!combined.plotAvailable())throw new IllegalStateException(combined.warning());
            double combinedError=Math.abs(combined.cutCenterlines().getLength()-totalCut);
            if(combinedError>Math.max(totalCut*1e-10,1e-8*scale))throw new AssertionError("Combined tool export changed cutting length");
            var exported=coverage(c,parsedTools,units,operation,params,token);
            // Nominal chord approximation + coordinate quantization. Raw area always remains in the report.
            double max=c.tools().stream().mapToDouble(ToolGeometry::toolDiameter).max().orElseThrow();
            double approximation=MachiningReachability.chordError(max/2,16)+MachiningReachability.chordError(min/2,64)+1e-9*scale;
            double quantum=Math.sqrt(2)*.5*(units.equals("IN")?1e-6:1e-4);
            var stock=MachiningReachability.classify(raw.finalRemaining(),access,approximation,token);
            var stockGcode=MachiningReachability.classify(exported.finalRemaining(),access,approximation+quantum,token);
            report.put("status","MEASURED").put("profile","GRBL_11_no_M6").put("tools",details).put("targetArea",c.area().getArea())
                    .put("nominalUnreachableArea",access.unreachable().getArea()).put("geometry",metrics(raw,stock))
                    .put("stableReachableArea",access.stableReachable().getArea()).put("centerTolerance",access.centerTolerance())
                    .put("gcode",metrics(exported,stockGcode)).put("gcodeTravelLength",totalTravel).put("gcodeCutLength",totalCut)
                    .put("gcodeCutPaths",paths).put("combinedLengthError",combinedError);
        }catch(Exception|AssertionError error){report.put("status","ERROR_OR_BUDGET").put("error",error.toString());}
        report.put("elapsedMs",(System.nanoTime()-start)/1e6);Files.writeString(out.resolve(id+".json"),report.toString(2));
        System.out.println(id+": "+report.getString("status")+" "+report.getDouble("elapsedMs")+" ms");System.out.flush();
    }
    static JSONObject metrics(MachiningCoverage.Result result,MachiningReachability.Residual residual) {
        return new JSONObject().put("remainingArea",result.finalRemainingArea()).put("reachableUncutArea",residual.reachableUncut().getArea())
                .put("reachableCoreArea",residual.coreBeyondTolerance().getArea()).put("outsideReachableArea",residual.outsideReachable().getArea()).put("analysisTolerance",residual.tolerance())
                .put("stableReachableUncutArea",residual.stableReachableUncut().getArea()).put("stableCoreArea",residual.stableCore().getArea()).put("precisionSensitiveUncutArea",residual.precisionSensitiveUncut().getArea());
    }
    static MachiningCoverage.Result coverage(Case c,List<ToolGeometry> tools,String units,String op,GeometryGCodeParameters p,CancellationToken token){
        var own=new HashMap<Integer,Geometry>();for(int i=0;i<tools.size();i++)own.put(i,c.targets().get(i));
        var target=new MachiningTarget(op.toUpperCase(Locale.ROOT),units,c.area(),own,MachiningTarget.signature(tools,token));
        return MachiningCoverage.calculate(target,units,tools,p,Map.of(),token,ProgressCallback.none());
    }
    static double sampleDeviation(Geometry source,Geometry target,CancellationToken token) {
        if(source.isEmpty())return 0;if(target.isEmpty())return Double.POSITIVE_INFINITY;
        var index=new IndexedFacetDistance(target);return sample(source,index,token);
    }
    static double sample(Geometry g,IndexedFacetDistance index,CancellationToken token){
        token.throwIfCancellationRequested();double max=0;
        if(g instanceof GeometryCollection){for(int i=0;i<g.getNumGeometries();i++)max=Math.max(max,sample(g.getGeometryN(i),index,token));}
        else {Coordinate[] p=g.getCoordinates();for(int i=0;i<p.length;i++){
            if((i&255)==0)token.throwIfCancellationRequested();max=Math.max(max,index.distance(g.getFactory().createPoint(p[i])));
            if(i>0)max=Math.max(max,index.distance(g.getFactory().createPoint(new Coordinate((p[i-1].x+p[i].x)/2,(p[i-1].y+p[i].y)/2))));
        }}return max;
    }
}
