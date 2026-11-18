import java.nio.file.*;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import java.util.*;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.*;

/** Full NCC on fixed public inputs, with optional diagnostic Connect-only counters.
 * No application preference, project, source board or production binary is modified. */
public final class ConnectMeasurementProbe {
    private static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("INVENTORY CASE_JSON CONNECT_BOOLEAN OUTPUT_JSON");
        if(!args[2].equals("true")&&!args[2].equals("false"))throw new IllegalArgumentException("Connect must be true or false");
        var config=new JSONObject(Files.readString(Path.of(args[1])));boolean connect=Boolean.parseBoolean(args[2]);
        var inventory=new JSONObject(Files.readString(Path.of(args[0])));JSONObject board=null;
        for(Object o:inventory.getJSONArray("boards"))if(((JSONObject)o).getString("id").equals(config.getString("board")))board=(JSONObject)o;
        if(board==null)throw new IllegalArgumentException("Unknown board");
        var preview=board.getJSONObject("preview");var writer=new WKTWriter();
        Geometry source=new WKTReader().read(preview.getString("wkt"));String sourceBefore=writer.write(source),units=preview.getString("units");
        double u=units.equals("IN")?1/25.4:1;var envelope=source.getEnvelopeInternal();
        int columns=config.optInt("columns",1),rows=config.optInt("rows",1);var copies=new ArrayList<Geometry>();
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)copies.add(AffineTransformation.translationInstance(x*(envelope.getWidth()+3*u),y*(envelope.getHeight()+3*u)).transform(source));
        Geometry copper=copies.size()==1?source:source.getFactory().createGeometryCollection(copies.toArray(Geometry[]::new));
        Geometry boundary=copper.getFactory().toGeometry(copper.getEnvelopeInternal());
        var tools=new ArrayList<Double>();for(Object d:config.getJSONArray("diametersMm"))tools.add(((Number)d).doubleValue()*u);
        var iso=config.optBoolean("iso",false)?List.of(.2*u):List.<Double>of();
        var method=NccMethod.valueOf(config.getString("method"));boolean rest=config.getBoolean("rest");
        var p=new NccParameters(tools,.4,0,method,connect,true,0,rest,NccOrder.NONE,new NccBoundary.Area(boundary),iso);
        // Equal small warmup in every fresh process; large-board warmup/JIT is included in reported case time.
        var f=new GeometryFactory();var warm=f.toGeometry(new Envelope(2,8,2,6));
        for(int i=0;i<3;i++)NccGenerator.generate("MM",warm,new NccParameters(List.of(.4),.4,1,NccMethod.LINES,true,true,0,false,NccOrder.NONE,new NccBoundary.Itself()));
        ConnectProfileAgent.reset();System.gc();
        var mx=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();
        if(!mx.isThreadAllocatedMemorySupported()||!mx.isCurrentThreadCpuTimeSupported())throw new IllegalStateException("Thread counters unavailable");
        mx.setThreadAllocatedMemoryEnabled(true);mx.setThreadCpuTimeEnabled(true);
        jdk.jfr.Recording recording=null;
        if(System.getProperty("flatcam.connect.jfr")!=null){
            recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
            recording.setDestination(Path.of(System.getProperty("flatcam.connect.jfr")));recording.start();
        }
        long beforeBytes=mx.getThreadAllocatedBytes(thread),beforeCpu=mx.getCurrentThreadCpuTime();
        long started=System.nanoTime();
        CancellationToken token=()->System.nanoTime()-started>180_000_000_000L;
        var result=NccGenerator.generate(units,copper,p,token,ProgressCallback.none());
        long wall=System.nanoTime()-started,cpu=mx.getCurrentThreadCpuTime()-beforeCpu,bytes=mx.getThreadAllocatedBytes(thread)-beforeBytes;
        if(recording!=null){recording.stop();recording.close();}
        var measured=ConnectProfileAgent.sample();boolean agent=Boolean.getBoolean("flatcam.connect.agent");
        if(measured.openCalls()!=0||(agent&&connect&&measured.calls()==0))throw new AssertionError("Connect instrumentation did not complete");
        if(!sourceBefore.equals(writer.write(source)))throw new AssertionError("Source mutated");
        var details=new JSONArray();long segments=0;
        for(var tool:result.toolResults()){
            String paths=writer.write(tool.geometry());segments+=Math.max(0,tool.geometry().getNumPoints()-tool.geometry().getNumGeometries());
            details.put(new JSONObject().put("diameterMm",tool.toolDiameter()/u).put("operation",tool.operation().name())
                    .put("pathsSha256",hash(paths)).put("parts",tool.geometry().getNumGeometries()).put("points",tool.geometry().getNumPoints())
                    .put("lengthMm",tool.geometry().getLength()/u).put("stockAreaMm2",tool.targetArea()==null?JSONObject.NULL:tool.targetArea().getArea()/(u*u)));
        }
        var report=new JSONObject().put("case",config).put("connect",connect).put("instrumented",agent).put("units",units)
                .put("sourceSha256",hash(sourceBefore)).put("sourcePoints",copper.getNumPoints()).put("outputPoints",result.geometry().getNumPoints()).put("outputSegments",segments)
                .put("geometrySha256",hash(writer.write(result.geometry()))).put("clearingAreaSha256",hash(writer.write(result.clearingArea())))
                .put("tools",details).put("failedPolygons",result.totalFailedPolygonCount())
                .put("generationMs",wall/1e6).put("generationThreadCpuMs",cpu/1e6).put("generationThreadAllocatedBytes",bytes)
                .put("connectCalls",measured.calls()).put("connectMs",measured.wallNanos()/1e6).put("connectThreadCpuMs",measured.cpuNanos()/1e6).put("connectThreadAllocatedBytes",measured.allocatedBytes())
                .put("javaVersion",System.getProperty("java.runtime.version")).put("camLocation",NccGenerator.class.getProtectionDomain().getCodeSource().getLocation().toString());
        Files.writeString(Path.of(args[3]),report.toString(2));
        System.out.printf(Locale.ROOT,"%s connect=%s agent=%s generation=%.1fms connect=%.1fms calls=%d%n",config.getString("id"),connect,agent,wall/1e6,measured.wallNanos()/1e6,measured.calls());
    }
}
