import java.nio.file.*;
import org.json.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.io.WKTReader;

/** Reuses the independently guarded continuous interval auditor; same required raw cuts, not only samples. */
public class CompareSpiralConnectProbe {
    public static void main(String[] args)throws Exception {
        var before=Path.of(args[0]);var after=Path.of(args[1]);var rows=new JSONArray();int errors=0;
        for(String board:new String[]{"breadboard-power-supply","modxo-adapter","single-sided-5v"})for(String method:new String[]{"LINES","SEED","SPIRAL"}){
            String id=board+"-"+method;var row=new JSONObject().put("id",id);long start=System.nanoTime();CancellationToken token=()->System.nanoTime()-start>120e9;
            try {
                var a=new JSONObject(Files.readString(before.resolve(id+".json")));var b=new JSONObject(Files.readString(after.resolve(id+".json")));
                if(!a.getString("status").equals("MEASURED")||!b.getString("status").equals("MEASURED"))throw new AssertionError("Case not measured");
                var r=new WKTReader();var old=r.read(Files.readString(before.resolve(id+".wkt")));var fresh=r.read(Files.readString(after.resolve(id+".wkt")));
                // Connector shortcuts are optional: only the raw spiral and requested finishing cuts are required.
                var required=method.equals("SPIRAL")?r.read(Files.readString(Path.of(args[3]).resolve(id+".wkt"))):old;
                double missing=CompareRegionCncProbe.uncoveredLength(required,fresh,1e-8,token);
                if(missing>1e-8)throw new AssertionError("Missing source intervals: "+missing);
                if(!method.equals("SPIRAL")&&!old.equalsExact(fresh))throw new AssertionError("Legacy method changed");
                if(b.getInt("retracts")>a.getInt("retracts"))throw new AssertionError("More retracts");
                if(b.getDouble("stableResidualCoreArea")>a.getDouble("stableResidualCoreArea")+1e-8)throw new AssertionError("Stable coverage regression");
                row.put("status","PASS").put("uncoveredRequiredLength",missing).put("before",a).put("after",b);
            }catch(Exception|AssertionError failure){row.put("status","FAIL").put("error",failure.toString());errors++;}
            rows.put(row);System.out.println(id+": "+row.getString("status"));
        }
        Files.writeString(Path.of(args[2]),new JSONObject().put("schema",1).put("cases",rows).put("errors",errors).put("scope","Same raw required cuts, continuous segment-interval coverage at 1e-8 mm. Existing auditor guards detect interior gaps. Same cutter/target/GRBL; no physical machine time claim. New source path order preserved for open spiral fragments; finishing can be interleaved.").toString(2));
        if(errors>0)System.exit(1);
    }
}
