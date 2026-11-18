import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;
import org.json.*;

/** Streams JFR events without expanding recording metadata into hundreds of MB of JSON. */
public class SummarizeConnectJfr {
    private static int searchStart=-1,searchEnd=-1;
    private static final class Metrics {
        long events,totalWeight,connectWeight;
        final Map<String,Long> groups=new TreeMap<>(),top=new HashMap<>();
        void add(RecordedEvent event) {
            events++;long weight=event.hasField("weight")?event.getLong("weight"):1;totalWeight+=weight;
            var trace=event.getStackTrace();if(trace==null)return;
            var frames=trace.getFrames();boolean connect=false,safety=false,buffer=false,coordinates=false,search=false;
            for(var frame:frames) {
                var method=frame.getMethod();String type=method.getType().getName(),name=method.getName();
                boolean connector=type.equals("org.flatcam.cam.ncc.NccGenerator")&&(name.equals("connectSafePaths")||name.equals("connectOrderedPaths"));
                connect|=connector;
                search|=connector&&searchStart>0&&frame.getLineNumber()>=searchStart&&frame.getLineNumber()<=searchEnd;
                safety|=name.equals("within") || (type.startsWith("org.locationtech.jts.geom.prep.") && name.equals("contains"));
                buffer|=type.startsWith("org.locationtech.jts.operation.buffer.") || name.equals("buffer");
                coordinates|=name.equals("getCoordinates");
            }
            if(!connect)return;connectWeight+=weight;
            if(safety)groups.merge("safetyPredicate",weight,Long::sum);
            if(buffer)groups.merge("sweptBuffer",weight,Long::sum);
            if(coordinates)groups.merge("coordinateArrayAccess",weight,Long::sum);
            if(search)groups.merge("nearestEndpointSearch",weight,Long::sum);
            if(!frames.isEmpty()) {
                var method=frames.getFirst().getMethod();top.merge(method.getType().getName()+"."+method.getName(),weight,Long::sum);
            }
        }
        JSONObject json() {
            var sorted=new JSONArray();top.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                    .limit(12).forEach(e->sorted.put(new JSONObject().put("method",e.getKey()).put("weight",e.getValue())));
            return new JSONObject().put("events",events).put("totalWeight",totalWeight).put("connectWeight",connectWeight)
                    .put("connectGroups",new JSONObject(groups)).put("connectTop",sorted);
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length<2||args.length>3)throw new IllegalArgumentException("Usage: SummarizeConnectJfr.java INPUT_JFR OUTPUT_JSON [NCC_GENERATOR_SOURCE]");
        if(args.length==3){
            var lines=Files.readAllLines(Path.of(args[2]));
            for(int i=0;i<lines.size();i++){
                if(lines.get(i).contains("for (int i = 0; i < remaining.size(); i++)"))searchStart=i+1;
                if(searchStart>0&&searchEnd<0&&lines.get(i).contains("Coordinate[] coordinates = remaining.remove(bestIndex)"))searchEnd=i;
            }
            if(searchStart<0||searchEnd<searchStart)throw new IllegalArgumentException("Cannot identify current endpoint search loop");
        }
        var samples=new Metrics();var allocations=new Metrics();
        try(var recording=new RecordingFile(Path.of(args[0]))) {
            while(recording.hasMoreEvents()) {
                var event=recording.readEvent();switch(event.getEventType().getName()) {
                    case "jdk.ExecutionSample"->samples.add(event);
                    case "jdk.ObjectAllocationSample"->allocations.add(event);
                }
            }
        }
        var report=new JSONObject().put("schema",1).put("recording",args[0]).put("executionSamples",samples.json())
                .put("allocationSamples",allocations.json()).put("nearestSearchSourceLines",List.of(searchStart,searchEnd))
                .put("allocationNote","Sampled allocation weights are estimates, not peak heap or resident RAM. Stack categories may overlap; execution counts are sampled CPU attribution, not exact wall time.");
        var path=Path.of(args[1]);if(path.getParent()!=null)Files.createDirectories(path.getParent());Files.writeString(path,report.toString(2));
        System.out.println("JFR summary: "+path);
    }
}
