import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;
import org.locationtech.jts.operation.overlayng.*;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;

/** Diagnostic only: public source -> union -> difference -> clearing, no ring normalization. */
public class NccUnionProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) throw new IllegalArgumentException("Usage: TRACE NEW_OUTPUT [CASE_ID]");
        var output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Choose a new output file");
        var document = new JSONObject(Files.readString(Path.of(args[0])));
        if (document.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported trace schema");
        var reader = new WKTReader();
        var writer = new WKTWriter();
        var clear = NccGenerator.class.getDeclaredMethod("clearArea",Geometry.class,double.class,
                NccToolSettings.class,boolean.class,CancellationToken.class,DoubleConsumer.class);
        clear.setAccessible(true);
        var cases = new JSONArray();
        for (Object input : document.getJSONArray("cases")) {
            var entry = (JSONObject)input;
            if (!entry.getString("sourceContainer").equals("list-multipart")) continue;
            if (args.length == 3 && !entry.getString("id").equals(args[2])) continue;
            Geometry source = reader.read(entry.getString("inputWkt"));
            for (boolean repairBefore : List.of(false,true)) {
                var operations = new JSONArray();
                Geometry copper = flatUnion(repairBefore ? source.buffer(0) : source,operations);
                Geometry boundary = GeosBufferOp.bufferOp(copper.convexHull(),entry.getDouble("margin"),
                        new org.locationtech.jts.operation.buffer.BufferParameters(64,1,2,5));
                Geometry difference = OverlayNGRobust.overlay(boundary,copper,OverlayNG.DIFFERENCE);
                for (boolean repairAfter : List.of(false,true)) {
                    Geometry area = repairAfter ? difference.buffer(0) : difference;
                    var candidate = new JSONObject(entry.toString());
                    candidate.put("id",entry.getString("id") + ":" + repairBefore + ":" + repairAfter);
                    candidate.put("clearingWkt",writer.write(area)).put("candidateCopperWkt",writer.write(copper));
                    candidate.put("unionOperations",operations);
                    for (boolean connected : List.of(false,true)) {
                        Object result = clear.invoke(null,area,entry.getDouble("diameter"),
                                new NccToolSettings(.4,NccMethod.STANDARD,connected,true,0),false,
                                CancellationToken.none(),(DoubleConsumer)(v -> {}));
                        var method = result.getClass().getDeclaredMethod("geometry");
                        method.setAccessible(true);
                        candidate.put(connected ? "connectedWkt" : "plainWkt",writer.write((Geometry)method.invoke(result)));
                    }
                    cases.put(candidate);
                }
            }
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("No matching list-multipart case");
        Files.writeString(output,new JSONObject().put("schema",1).put("cases",cases).toString());
        System.out.println("Diagnostic only: " + cases.length() + " candidates");
    }

    static Geometry flatUnion(Geometry source,JSONArray operations) {
        var parts = new ArrayList<Geometry>();
        collect(source,parts);
        if (parts.isEmpty()) throw new IllegalArgumentException("No polygonal input");
        parts.sort(Comparator.comparingDouble(g -> g.getEnvelopeInternal().getMinX() + g.getEnvelopeInternal().getMaxX()));
        int slices = (int)Math.ceil(Math.sqrt(Math.ceil(parts.size()/10.)));
        int capacity = (int)Math.ceil(parts.size()/(double)slices);
        for (int start=0;start<parts.size();start+=capacity) {
            parts.subList(start,Math.min(start+capacity,parts.size())).sort(Comparator.comparingDouble(
                    g -> g.getEnvelopeInternal().getMinY() + g.getEnvelopeInternal().getMaxY()));
        }
        return binary(parts,0,parts.size(),operations);
    }
    static Geometry binary(List<Geometry> parts,int start,int end,JSONArray operations) {
        if (end-start==1) return parts.get(start).copy();
        int middle=(start+end)/2;
        Geometry left=binary(parts,start,middle,operations),right=binary(parts,middle,end,operations);
        Geometry result=OverlayNGRobust.overlay(left,right,OverlayNG.UNION);
        var writer=new WKTWriter();
        operations.put(new JSONObject().put("range",start + ":" + end).put("left",writer.write(left))
                .put("right",writer.write(right)).put("result",writer.write(result)));
        return result;
    }
    static void collect(Geometry source,List<Geometry> target) {
        if (source instanceof Polygon) target.add(source);
        else if (source instanceof GeometryCollection) for(int i=0;i<source.getNumGeometries();i++) collect(source.getGeometryN(i),target);
    }
}
