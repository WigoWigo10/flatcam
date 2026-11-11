import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.geometry.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.ncc.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

/** Same nominal cutter/target for all methods; estimated program time is not physical machine time. */
public class SpiralCommunityProbe {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: SpiralCommunityProbe.java FX_IMPORT_JSON OUTPUT_DIR");
        Path out=Path.of(args[1]);Files.createDirectories(out);var cases=new JSONArray();int errors=0,boards=0;
        var inventory=new JSONObject(Files.readString(Path.of(args[0])));
        boolean connect=Boolean.parseBoolean(System.getProperty("flatcam.spiral.connect","true"));
        for(Object entry:inventory.getJSONArray("boards")) {
            var board=(JSONObject)entry;String id=board.getString("id");
            if(!id.matches(System.getProperty("flatcam.spiral.boards","breadboard-power-supply|modxo-adapter|single-sided-5v")))continue;
            boards++;
            Geometry copper=new WKTReader().read(board.getJSONObject("preview").getString("wkt"));
            if(!board.getJSONObject("preview").getString("units").equals("MM"))throw new IllegalArgumentException("Probe requires MM input");
            Geometry area=copper.getFactory().toGeometry(copper.getEnvelopeInternal()).difference(copper);String source=copper.toText();
            for(var method:List.of(NccMethod.LINES,NccMethod.SEED,NccMethod.SPIRAL)) {
                String caseId=id+"-"+method.name();Path file=out.resolve(caseId+".json");
                if(Files.exists(file)){var saved=new JSONObject(Files.readString(file));cases.put(saved);if(!saved.getString("status").equals("MEASURED"))errors++;continue;}
                var row=new JSONObject().put("id",caseId).put("method",method.name()).put("board",id).put("connect",connect);
                long start=System.nanoTime();CancellationToken token=()->System.nanoTime()-start>240e9;
                try {
                    long generation=System.nanoTime();
                    var result=NccGenerator.paint("MM",area,new PaintParameters(List.of(.4),.4,0,method,connect,true,NccOrder.NONE,false),token,ProgressCallback.none());
                    row.put("generationMs",(System.nanoTime()-generation)/1e6);
                    Files.writeString(out.resolve(caseId+".wkt"),result.geometry().toText());
                    var tools=result.toolResults().stream().filter(t->!t.isEmpty()).map(t->new ToolGeometry(t.toolDiameter(),t.geometry())).toList();
                    if(tools.isEmpty())throw new AssertionError("No toolpaths");
                    // Legacy failure count includes regions too small for this cutter; retain it alongside reachability.
                    row.put("failedPolygons",result.totalFailedPolygonCount());
                    var params=new GeometryGCodeParameters(3,.1,false,0,300,10000,false);
                    if(method.prefersSourceOrder())params=params.withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
                    var job=GCodeGenerator.generateGeometryCncJob("MM",tools,params,Map.of(),token,GCodePreprocessor.GRBL_11_NO_M6);
                    Files.writeString(out.resolve(caseId+".nc"),job.gcode());
                    var parsed=GCodeToolpathParser.parse(job.gcode(),token,ProgressCallback.none());
                    if(!parsed.plotAvailable())throw new AssertionError(parsed.warning());
                    double deviation=Math.max(deviation(result.geometry(),parsed.cutCenterlines(),token),deviation(parsed.cutCenterlines(),result.geometry(),token));
                    if(deviation>Math.sqrt(2)*.00005*1.001+1e-10)throw new AssertionError("Export coordinate deviation "+deviation);
                    double clearance=Math.min(result.geometry().distance(copper),result.geometry().distance(area.getEnvelope().getBoundary()));
                    if(clearance+.002<.2)throw new AssertionError("Cutter clearance "+clearance);
                    var exported=List.of(new ToolGeometry(.4,parsed.cutCenterlines()));
                    var target=new MachiningTarget("PAINT","MM",area,Map.of(0,area),MachiningTarget.signature(exported,token));
                    var coverage=MachiningCoverage.calculate(target,"MM",exported,params,Map.of(),token,ProgressCallback.none());
                    var access=MachiningReachability.calculate(area,.4,token);
                    // Q16 footprint/chord/export fringe is declared separately; raw residual is never discarded.
                    double tolerance=.2*(1-Math.cos(Math.PI/64))+.0004+Math.sqrt(2)*.00005;
                    var residual=MachiningReachability.classify(coverage.finalRemaining(),access,tolerance,token);
                    if(!source.equals(copper.toText()))throw new AssertionError("Source changed");
                    row.put("status","MEASURED").put("targetArea",area.getArea()).put("reachableArea",access.reachable().getArea())
                        .put("rawReachableUncutArea",residual.reachableUncut().getArea()).put("stableUncutArea",residual.stableReachableUncut().getArea())
                        .put("stableResidualCoreArea",residual.stableCore().getArea()).put("precisionSensitiveUncutArea",residual.precisionSensitiveUncut().getArea())
                        .put("analysisTolerance",tolerance).put("exportDeviation",deviation).put("minimumClearance",clearance)
                        .put("sourceParts",result.geometry().getNumGeometries()).put("sourcePoints",result.geometry().getNumPoints())
                        .put("retracts",Pattern.compile("(?m)^G0?0\\s+Z").matcher(job.gcode()).results().count())
                        .put("estimatedMinutes",parsed.stats().estimatedMinutes()).put("travelXY",parsed.travelCenterlines().getLength())
                        .put("cutLength",parsed.cutCenterlines().getLength());
                }catch(Exception|AssertionError failure){row.put("status","ERROR_OR_BUDGET").put("error",failure.toString());errors++;}
                row.put("elapsedMs",(System.nanoTime()-start)/1e6);Files.writeString(file,row.toString(2));cases.put(row);
                System.out.println(caseId+": "+row.getString("status")+" "+row.getDouble("elapsedMs")+" ms");System.out.flush();
            }
        }
        boolean complete=boards>0&&cases.length()==3*boards;
        Files.writeString(out.resolve("comparison.json"),new JSONObject().put("schema",1).put("cases",cases).put("errors",errors).put("matrixComplete",complete)
            .put("policy","Three public boards, same MM diagnostic copper bounding box minus copper, .4 mm cutter, 40% overlap, Connect recorded per case, Contour on, no Rest. Paint kernel. GRBL_11_no_M6 export/parse. Lines/Seed default NEAREST vs spiral SOURCE/preserved direction. Cold sequential generation timings are observations, not an isolated benchmark. Program estimate excludes acceleration and controller behavior; boundary is not a physical Outline. Nominal Q16 swept area and Q64 reachability; raw and stable-core residuals retained. Cooperative case budget 240 seconds.").toString(2));
        if(errors>0||!complete)System.exit(1);
    }
    static double deviation(Geometry source,Geometry target,CancellationToken token){return sample(source,new IndexedFacetDistance(target),token);}
    static double sample(Geometry g,IndexedFacetDistance index,CancellationToken token){
        token.throwIfCancellationRequested();double max=0;
        if(g instanceof GeometryCollection){for(int i=0;i<g.getNumGeometries();i++)max=Math.max(max,sample(g.getGeometryN(i),index,token));}
        else {var p=g.getCoordinates();for(int i=0;i<p.length;i++){
            if((i&255)==0)token.throwIfCancellationRequested();max=Math.max(max,index.distance(g.getFactory().createPoint(p[i])));
            if(i>0)max=Math.max(max,index.distance(g.getFactory().createPoint(new Coordinate((p[i-1].x+p[i].x)/2,(p[i-1].y+p[i].y)/2))));
        }}return max;
    }
}
