import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import jdk.jfr.consumer.*;
import org.json.*;

/** Deeper, read-only attribution of existing generation-only JFR recordings.
 * Inclusive APIs overlap; allocation weights are estimates, never resident-memory peaks. */
public final class SummarizeGeometryHotspots {
    private static final class Count {long events,weight;void add(long value){events++;weight+=value;}}
    private static void add(Map<String,Count> map,String key,long weight){map.computeIfAbsent(key,k->new Count()).add(weight);}
    private static String method(RecordedFrame frame){var m=frame.getMethod();return m.getType().getName()+"."+m.getName();}
    private static String site(RecordedFrame frame){return method(frame)+":"+frame.getLineNumber();}
    private static boolean application(RecordedFrame frame){
        String type=frame.getMethod().getType().getName();
        return type.startsWith("org.flatcam.cam.")&&!type.contains(".geosbuffer.");
    }
    private static JSONArray table(Map<String,Count> map,int limit){
        var rows=new JSONArray();map.entrySet().stream().sorted(Comparator.<Map.Entry<String,Count>>comparingLong(e->e.getValue().weight).reversed().thenComparing(Map.Entry::getKey))
                .limit(limit).forEach(e->rows.put(new JSONObject().put("site",e.getKey()).put("events",e.getValue().events).put("weight",e.getValue().weight)));
        return rows;
    }
    private static final class Metrics {
        long events,weight,noStack,noStackWeight,truncated,generatorEvents,generatorWeight;
        final Map<String,Count> apis=new HashMap<>(),leaf=new HashMap<>(),callers=new HashMap<>(),copyStacks=new HashMap<>(),copyOwners=new HashMap<>(),classes=new HashMap<>(),threads=new HashMap<>(),stacks=new HashMap<>();
        void accept(RecordedEvent event,boolean allocation){
            long value=allocation?event.getLong("weight"):1;events++;weight+=value;
            RecordedThread thread=allocation?event.getThread():event.getThread("sampledThread");
            add(threads,thread==null?"unknown":thread.getJavaName(),value);
            if(allocation)add(classes,event.getClass("objectClass").getName(),value);
            var trace=event.getStackTrace();if(trace==null){noStack++;noStackWeight+=value;return;}if(trace.isTruncated())truncated++;
            var frames=trace.getFrames();boolean buffer=false,intersection=false,overlay=false,array=false,active=false;
            var chain=new ArrayList<String>();var full=new ArrayList<String>();
            for(var frame:frames){
                var m=frame.getMethod();String type=m.getType().getName(),name=m.getName();
                if(full.size()<14)full.add(site(frame));
                if(application(frame)&&chain.size()<4)chain.add(site(frame));
                buffer|=(type.endsWith("GeosBufferOp")&&name.equals("bufferOp"))||type.equals("org.locationtech.jts.operation.buffer.BufferOp")||type.equals("org.locationtech.jts.geom.Geometry")&&name.equals("buffer");
                intersection|=type.equals("org.locationtech.jts.geom.Geometry")&&name.equals("intersection");
                overlay|=type.contains(".operation.overlay")||type.contains(".operation.union.");
                array|=type.equals("java.util.Arrays")&&(name.startsWith("copyOf"))||type.equals("java.lang.System")&&name.equals("arraycopy");
                active|=type.equals("org.flatcam.cam.ncc.NccGenerator")&&name.equals("generate");
            }
            if(active){generatorEvents++;generatorWeight+=value;}
            if(buffer)add(apis,"buffer",value);if(intersection)add(apis,"geometryIntersection",value);if(overlay)add(apis,"overlayOrUnion",value);if(array)add(apis,"arrayCopy",value);
            if(!frames.isEmpty())add(leaf,site(frames.getFirst()),value);
            add(callers,chain.isEmpty()?"outsideApplication":String.join(" <- ",chain),value);
            add(stacks,String.join(" <- ",full),value);
            if(array){
                add(copyStacks,String.join(" <- ",full),value);
                String owner=frames.stream().filter(f->{String t=f.getMethod().getType().getName();return !t.startsWith("java.")&&!t.startsWith("jdk.")&&!t.startsWith("sun.");})
                        .map(SummarizeGeometryHotspots::method).findFirst().orElse("jdkOnly");
                add(copyOwners,owner,value);
            }
        }
        JSONObject json(){return new JSONObject().put("events",events).put("weight",weight).put("eventsWithoutStack",noStack).put("weightWithoutStack",noStackWeight).put("truncatedStacks",truncated)
                .put("generatorActiveEvents",generatorEvents).put("generatorActiveWeight",generatorWeight).put("inclusiveApis",table(apis,20)).put("threads",table(threads,10))
                .put("leafMethods",table(leaf,25)).put("applicationCallsites",table(callers,Integer.MAX_VALUE)).put("arrayCopyStacks",table(copyStacks,12)).put("arrayCopyOwners",table(copyOwners,20))
                .put("allocationClasses",table(classes,15)).put("topStacks",table(stacks,12));}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("INPUT_JFR OUTPUT_JSON");
        var cpu=new Metrics();var allocation=new Metrics();
        try(var input=new RecordingFile(Path.of(args[0]))){while(input.hasMoreEvents()){
            var event=input.readEvent();switch(event.getEventType().getName()){
                case "jdk.ExecutionSample"->cpu.accept(event,false);
                case "jdk.ObjectAllocationSample"->allocation.accept(event,true);
            }
        }}
        if(cpu.generatorEvents==0)throw new IllegalArgumentException("No NCC generation CPU samples in recording");
        var report=new JSONObject().put("schema",1).put("recordingSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[0])))))
                .put("cpu",cpu.json()).put("allocation",allocation.json())
                .put("notes",List.of("CPU weights are sampled counts, not elapsed milliseconds.","Allocation weights estimate cumulative allocated bytes, not peak RAM. Inclusive APIs and call stacks can overlap.","Callsite keys include source lines and up to four application callers; geosbuffer implementation is treated as the geometry library.","All recorded threads retained; thread attribution is explicit. No algorithm or recording is modified."));
        Files.writeString(Path.of(args[1]),report.toString(2));
    }
}
