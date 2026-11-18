import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;
import org.json.*;

/** Exclusive phase attribution for the same generation-only JFR recordings.
 * These are sampled stacks, not exact elapsed times or a coverage oracle. */
public final class SummarizeClearingPhases {
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("INPUT_JFR OUTPUT_JSON NCC_GENERATOR_SOURCE");
        int footprintStart=-1,footprintEnd=-1,subtract=-1;
        var lines=Files.readAllLines(Path.of(args[2]));
        for(int i=0;i<lines.size();i++){
            String line=lines.get(i);
            if(line.contains("if(computeFootprint)for"))footprintStart=i+1;
            if(line.contains("var diagnostics=diagnose"))footprintEnd=i;
            if(line.contains("remainingArea = OverlayNGRobust.overlay"))subtract=i+1;
        }
        if(footprintStart<0||footprintEnd<footprintStart||subtract<0)throw new IllegalArgumentException("Cannot resolve source phase ranges");
        Map<String,Long> phases=new TreeMap<>(),top=new HashMap<>();long count=0;
        try(var input=new RecordingFile(Path.of(args[0]))){while(input.hasMoreEvents()){
            var event=input.readEvent();if(!event.getEventType().getName().equals("jdk.ExecutionSample"))continue;
            var trace=event.getStackTrace();if(trace==null)continue;var frames=trace.getFrames();count++;
            boolean connect=false,diagnostics=false,footprint=false,subtraction=false,prepare=false,clear=false;
            for(var frame:frames){
                var m=frame.getMethod();String type=m.getType().getName(),name=m.getName();
                if(type.equals("org.flatcam.cam.ncc.NccGenerator")){
                    connect|=name.equals("connectSafePaths")||name.equals("connectOrderedPaths");
                    footprint|=name.equals("clearAreaImpl")&&frame.getLineNumber()>=footprintStart&&frame.getLineNumber()<=footprintEnd;
                    subtraction|=name.equals("generate")&&frame.getLineNumber()==subtract;
                    prepare|=List.of("isolationEnvelope","prepareBoundary","clearingArea").contains(name);
                    clear|=List.of("clearAreaImpl","clearPolygon","linePaths","standardPaths","seedPaths").contains(name);
                }
                diagnostics|=name.equals("unvisitedComponents")&&type.contains("LinesRasterRefinement");
                prepare|=type.equals("org.flatcam.cam.ncc.NccCopperUnion");
            }
            String phase=connect?"connect":diagnostics?"coverageDiagnostics":footprint?"sweptFootprintAndAssembly":subtraction?"restStockSubtraction":prepare?"copperBoundaryPreparation":clear?"clearingPaths":"other";
            phases.merge(phase,1L,Long::sum);
            if(!frames.isEmpty()){var m=frames.getFirst().getMethod();top.merge(m.getType().getName()+"."+m.getName(),1L,Long::sum);}
        }}
        var sorted=new JSONArray();top.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(15)
                .forEach(e->sorted.put(new JSONObject().put("method",e.getKey()).put("samples",e.getValue())));
        Files.writeString(Path.of(args[1]),new JSONObject().put("executionSamples",count).put("exclusivePhases",phases).put("topMethods",sorted)
                .put("footprintSourceLines",List.of(footprintStart,footprintEnd)).put("subtractionSourceLine",subtract)
                .put("note","Sampled attribution. First matching phase by stated priority; some stacks may be incomplete. Not exact phase wall times.").toString(2));
    }
}
