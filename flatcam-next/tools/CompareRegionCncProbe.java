import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.geometry.*;
import org.flatcam.cam.gcode.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.index.strtree.STRtree;

/** Actual GRBL export comparison: ordinary Lines/NEAREST versus regional/SOURCE. No machine timing claim. */
public class CompareRegionCncProbe {
    static final Pattern RETRACT=Pattern.compile("(?m)^G0?0\\s+Z");
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: CompareRegionCncProbe.java BASELINE_RAW_DIR REGION_RAW_DIR OUTPUT_DIR");
        Path out=Path.of(args[2]);Files.createDirectories(out);var reader=new WKTReader();int failures=0;var cases=new JSONArray();
        try(var files=Files.list(Path.of(args[1]))) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var region=new JSONObject(Files.readString(file));String id=region.getString("id");
                if(!id.matches(System.getProperty("flatcam.audit.caseFilter",".*")))continue;
                Path destination=out.resolve(id+".json");if(Files.exists(destination)){var existing=new JSONObject(Files.readString(destination));cases.put(existing);if(!existing.getString("status").equals("MEASURED"))failures++;continue;}
                var row=new JSONObject().put("id",id).put("board",region.getString("board"));long start=System.nanoTime();
                CancellationToken token=()->System.nanoTime()-start>300e9;
                try {
                    if(!region.getString("status").equals("GENERATED"))throw new IllegalStateException(region.optString("error"));
                    var old=new JSONObject(Files.readString(Path.of(args[0]).resolve(file.getFileName())));
                    Geometry before=reader.read(old.getString("rawPathsWkt")),after=reader.read(region.getString("rawPathsWkt"));
                    double d=region.getDouble("diameter");String units=region.getString("units");double scale=units.equals("IN")?1/25.4:1;
                    // Compare required cuts, without requiring a connector's old shortcuts to be repeated.
                    var raw=new JSONObject(Files.readString(Path.of(args[0]).resolve(file.getFileName().toString().replace("-c1-","-c0-"))));
                    Geometry required=reader.read(raw.getString("rawPathsWkt"));
                    double missing=deviation(required,after,token);
                    if(missing>1e-8*scale)throw new AssertionError("Missing/displaced source cuts: "+missing);
                    double uncovered=uncoveredLength(required,after,1e-8*scale,token);
                    if(uncovered>1e-8*scale)throw new AssertionError("Uncovered source intervals: "+uncovered);
                    var common=new GeometryGCodeParameters(3*scale,.1*scale,false,0,300*scale,10000,false);
                    var source=common.withJobOptions(GeometryJobOptions.AUTOMATIC.withRouting(new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0)));
                    var previous=program(before,d,units,common,token);var fresh=program(after,d,units,source,token);
                    row.put("status","MEASURED").put("requiredCutDeviation",missing).put("uncoveredRequiredLength",uncovered).put("baseline",previous).put("regional",fresh)
                            .put("estimatedMinuteDelta",fresh.getDouble("estimatedMinutes")-previous.getDouble("estimatedMinutes"))
                            .put("retractDelta",fresh.getInt("retractCommands")-previous.getInt("retractCommands"))
                            .put("travelDelta",fresh.getDouble("travelXY")-previous.getDouble("travelXY"));
                }catch(Exception|AssertionError failure){row.put("status","ERROR_OR_BUDGET").put("error",failure.toString());failures++;}
                row.put("elapsedMs",(System.nanoTime()-start)/1e6);Files.writeString(destination,row.toString(2));cases.put(row);
                System.out.println(id+": "+row.getString("status"));System.out.flush();
            }
        }
        int expected=Integer.parseInt(System.getProperty("flatcam.audit.expectedCases","16"));boolean complete=expected>0&&cases.length()==expected;
        var summary=new JSONObject().put("cases",cases).put("errors",failures).put("expectedCases",expected).put("matrixComplete",complete).put("policy","GRBL_11_no_M6. Ordinary NEAREST vs regional SOURCE/preserved direction; safe Z 3 mm, cut Z -0.1 mm, XY 300 mm/min, default Z feed, rapid estimate 1500 mm/min. Parser estimate excludes acceleration, dwell and real controller behavior. Required source segments checked by continuous interval coverage within 1e-8 mm transverse/longitudinal tolerance; export fidelity samples vertices/midpoints within coordinate rounding. No physical CNC certification.");
        Files.writeString(out.resolve("comparison.json"),summary.toString(2));if(failures>0||!complete)System.exit(1);
    }
    static JSONObject program(Geometry paths,double d,String units,GeometryGCodeParameters parameters,CancellationToken token) {
        String code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(d,paths)),parameters,Map.of(),token,GCodePreprocessor.GRBL_11_NO_M6).gcode();
        var parsed=GCodeToolpathParser.parse(code,token,ProgressCallback.none());if(!parsed.plotAvailable())throw new IllegalStateException(parsed.warning());
        double scale=units.equals("IN")?1/25.4:1,quantum=Math.sqrt(2)*.5*(units.equals("IN")?1e-6:1e-4);
        double error=Math.max(deviation(paths,parsed.cutCenterlines(),token),deviation(parsed.cutCenterlines(),paths,token));
        if(error>quantum*1.001+1e-10*scale)throw new AssertionError("Exported cut deviation: "+error);
        return new JSONObject().put("estimatedMinutes",parsed.stats().estimatedMinutes()).put("travelXY",parsed.travelCenterlines().getLength())
                .put("cutLength",parsed.cutCenterlines().getLength()).put("sourcePaths",paths.getNumGeometries()).put("retractCommands",RETRACT.matcher(code).results().count())
                .put("cutDeviation",error).put("roundingBound",quantum);
    }
    static double deviation(Geometry source,Geometry destination,CancellationToken token){
        if(source.isEmpty())return 0;if(destination.isEmpty())return Double.POSITIVE_INFINITY;
        return sample(source,new IndexedFacetDistance(destination),token);
    }
    static double sample(Geometry g,IndexedFacetDistance index,CancellationToken token){
        token.throwIfCancellationRequested();double max=0;
        if(g instanceof GeometryCollection){for(int i=0;i<g.getNumGeometries();i++)max=Math.max(max,sample(g.getGeometryN(i),index,token));}
        else {Coordinate[] p=g.getCoordinates();for(int i=0;i<p.length;i++){
            if((i&255)==0)token.throwIfCancellationRequested();max=Math.max(max,index.distance(g.getFactory().createPoint(p[i])));
            if(i>0)max=Math.max(max,index.distance(g.getFactory().createPoint(new Coordinate((p[i-1].x+p[i].x)/2,(p[i-1].y+p[i].y)/2))));
        }}return max;
    }
    private record Segment(Coordinate a,Coordinate b) { }
    static double uncoveredLength(Geometry required,Geometry actual,double tolerance,CancellationToken token) {
        var index=new STRtree();var candidates=new ArrayList<Segment>();segments(actual,candidates);
        for(var s:candidates)index.insert(new Envelope(s.a(),s.b()),s);index.build();var needed=new ArrayList<Segment>();segments(required,needed);double missing=0;
        for(var s:needed) {
            token.throwIfCancellationRequested();double dx=s.b().x-s.a().x,dy=s.b().y-s.a().y,length=Math.hypot(dx,dy);if(length==0)continue;
            double ux=dx/length,uy=dy/length;var query=new Envelope(s.a(),s.b());query.expandBy(tolerance);var intervals=new ArrayList<double[]>();
            for(Object item:index.query(query)) {
                var c=(Segment)item;double ax=c.a().x-s.a().x,ay=c.a().y-s.a().y,bx=c.b().x-s.a().x,by=c.b().y-s.a().y;
                if(Math.abs(ax*uy-ay*ux)>tolerance||Math.abs(bx*uy-by*ux)>tolerance)continue;
                double a=ax*ux+ay*uy,b=bx*ux+by*uy,low=Math.max(0,Math.min(a,b)-tolerance),high=Math.min(length,Math.max(a,b)+tolerance);
                if(high>low)intervals.add(new double[]{low,high});
            }
            intervals.sort(Comparator.comparingDouble(a->a[0]));double end=0;
            for(var interval:intervals){if(interval[0]>end)missing+=interval[0]-end;end=Math.max(end,interval[1]);}
            missing+=length-end;
        }
        return missing;
    }
    private static void segments(Geometry g,List<Segment> out){
        if(g instanceof GeometryCollection){for(int i=0;i<g.getNumGeometries();i++)segments(g.getGeometryN(i),out);}
        else {var p=g.getCoordinates();for(int i=1;i<p.length;i++)if(!p[i-1].equals2D(p[i]))out.add(new Segment(p[i-1],p[i]));}
    }
}
