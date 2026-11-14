import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;

/** Generation-only fresh-JVM replay. Parse, G-code, coverage and file writes are outside the timed region. */
public class SpiralGenerationBenchmark {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: SpiralGenerationBenchmark.java INVENTORY OUTPUT_JSON TAG");
        var inventory=new JSONObject(Files.readString(Path.of(args[0])));var measurements=new JSONArray();
        var cpu=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        String boardFilter=System.getProperty("flatcam.spiral.boards","breadboard-power-supply|modxo-adapter|single-sided-5v");
        for(Object entry:inventory.getJSONArray("boards")) {
            var board=(JSONObject)entry;String id=board.getString("id");if(!id.matches(boardFilter))continue;
            Geometry copper=new WKTReader().read(board.getJSONObject("preview").getString("wkt"));
            Geometry area=copper.getFactory().toGeometry(copper.getEnvelopeInternal()).difference(copper);String before=area.toText();
            var params=new PaintParameters(List.of(.4),.4,0,NccMethod.SPIRAL,true,true,NccOrder.NONE,false);
            for(int repeat=0;repeat<3;repeat++) {
                long cpuStart=cpu.getProcessCpuTime(),start=System.nanoTime();
                var result=NccGenerator.paint("MM",area,params,CancellationToken.NONE,ProgressCallback.none());
                double ms=(System.nanoTime()-start)/1e6,cpuMs=(cpu.getProcessCpuTime()-cpuStart)/1e6;
                if(result.isEmpty()||!before.equals(area.toText()))throw new AssertionError("Empty result or changed source");
                measurements.put(new JSONObject().put("board",id).put("repeat",repeat).put("generationMs",ms).put("processCpuMs",cpuMs)
                        .put("radialFallback",result.toolResults().stream().anyMatch(t->t.regionDiagnostics()!=null&&t.regionDiagnostics().radialFallback()))
                        .put("paths",result.pathCount()).put("points",result.geometry().getNumPoints()).put("cutLength",result.totalLength()));
                System.out.println(args[2]+" "+id+" #"+repeat+" "+ms+" ms");System.out.flush();
            }
        }
        Files.writeString(Path.of(args[1]),new JSONObject().put("tag",args[2]).put("java",System.getProperty("java.version"))
                .put("processors",Runtime.getRuntime().availableProcessors()).put("maxHeap",Runtime.getRuntime().maxMemory())
                .put("boardFilter",boardFilter).put("measurements",measurements).put("scope","Generation only: selected MM Paint targets, .4 mm, 40% overlap, Connect/Contour on, no Rest. Three repeats per board; repeat 0 includes cold/JIT effects, later repeats warm. Process CPU includes concurrent JVM threads; not renderer FPS, physical CNC time or peak RAM.").toString(2));
    }
}
