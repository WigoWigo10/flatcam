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
import org.flatcam.cam.gcode.*;

/** Diagnostic only: isolate union/repair ordering, never normalize oracle rings. */
public class NccRepresentationProbe {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) throw new IllegalArgumentException("Usage: FX_EXPORT NEW_DIRECTORY [VARIANT [ORDER_FILE]]");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Choose a new directory");
        Files.createDirectories(output);
        var export = new JSONObject(Files.readString(Path.of(args[0])));
        var reader = new WKTReader();
        var writer = new WKTWriter();
        Geometry source = reader.read(export.getString("sourceWkt"));
        JSONObject connected = null;
        for (Object entry : export.getJSONArray("cases")) {
            if (((JSONObject)entry).getString("id").equals("ncc-connect")) connected = (JSONObject)entry;
        }
        if (connected == null) throw new IllegalArgumentException("Missing Connect case");
        var p = connected.getJSONObject("parameters");
        double diameter = connected.getDouble("diameter");
        var settings = new NccToolSettings(p.getDouble("overlap"), NccMethod.STANDARD,true,true,0);
        var bufferParams = new org.locationtech.jts.operation.buffer.BufferParameters(64,
                org.locationtech.jts.operation.buffer.BufferParameters.CAP_ROUND,
                org.locationtech.jts.operation.buffer.BufferParameters.JOIN_MITRE,5);
        var clear = NccGenerator.class.getDeclaredMethod("clearArea",Geometry.class,double.class,
                NccToolSettings.class,boolean.class,CancellationToken.class,DoubleConsumer.class);
        clear.setAccessible(true);
        var stages = new JSONObject();
        var variants = List.of("repair-union","union-raw","geos-repair-union","geos-flat-union","native-order","java-msvc-order");
        if (args.length >= 3 && !variants.contains(args[2])) throw new IllegalArgumentException("Unknown variant");
        if (args.length >= 3 && args[2].equals("native-order") && args.length != 4) throw new IllegalArgumentException("native-order requires ORDER_FILE");
        for (String variant : args.length >= 3 ? List.of(args[2]) : variants.subList(0,4)) {
            Geometry repaired = switch (variant) {
                case "union-raw", "geos-flat-union", "native-order", "java-msvc-order" -> source;
                case "geos-repair-union" -> GeosBufferOp.bufferOp(source,0,new org.locationtech.jts.operation.buffer.BufferParameters(64));
                default -> source.buffer(0);
            };
            Geometry copper = variant.equals("java-msvc-order") ? NccCopperUnion.union(repaired,CancellationToken.none())
                    : variant.equals("native-order") ? orderedUnion(repaired,Path.of(args[3]))
                    : variant.equals("geos-flat-union") ? flatUnion(repaired) : OverlayNGRobust.union(repaired);
            Geometry boundary = GeosBufferOp.bufferOp(copper.convexHull(),p.getDouble("margin"),bufferParams);
            Geometry difference = OverlayNGRobust.overlay(boundary,copper,OverlayNG.DIFFERENCE);
            for (boolean repair : List.of(false,true)) {
                Geometry area = repair ? difference.buffer(0) : difference;
                Object result = clear.invoke(null,area,diameter,settings,false,CancellationToken.none(),(DoubleConsumer)(v -> {}));
                var geometryMethod = result.getClass().getDeclaredMethod("geometry");
                geometryMethod.setAccessible(true);
                Geometry paths = (Geometry)geometryMethod.invoke(result);
                var candidate = new JSONObject(connected.toString());
                candidate.put("fxWkt",writer.write(paths));
                candidate.getJSONObject("parameters").put("fxClearingAreaWkt",writer.write(area));
                String units = export.getString("units");
                double mm = units.equals("IN") ? 1/25.4 : 1;
                candidate.put("gcode",GCodeGenerator.generateGeometryCncJob(units,paths,
                        new GeometryGCodeParameters(3*mm,.1*mm,false,0,300*mm,0,false),diameter).gcode());
                candidate.put("fxDetailedPreviewAvailable",false).put("fxPreviewWarning","Diagnostic candidate; preview not checked");
                String name = variant + (repair ? "-final-repair" : "-no-final-repair");
                var control = new JSONObject(export.toString()).put("cases",new JSONArray().put(candidate))
                        .put("diagnosticControl",name + "; not production parity");
                Files.writeString(output.resolve(name + ".json"),control.toString());
                stages.put(name,new JSONObject().put("copper",writer.write(copper)).put("area",writer.write(area)));
                System.out.println("Diagnostic candidate: " + name);
            }
        }
        Files.writeString(output.resolve("stages.json"),stages.toString());
    }

    private static Geometry flatUnion(Geometry source) {
        var parts = new ArrayList<Geometry>();
        collect(source,parts);
        if(parts.isEmpty()) throw new IllegalArgumentException("No polygonal input");
        // Diagnostic approximation, NOT a GEOS port: Java's stable sorting
        // cannot reproduce all equal-key choices of C++ std::sort.
        parts.sort(Comparator.comparingDouble(g -> g.getEnvelopeInternal().centre().x));
        int slices = (int)Math.ceil(Math.sqrt(Math.ceil(parts.size()/10.)));
        int capacity = (int)Math.ceil(parts.size()/(double)slices);
        for (int start = 0; start < parts.size(); start += capacity) {
            parts.subList(start,Math.min(start+capacity,parts.size())).sort(
                    Comparator.comparingDouble(g -> g.getEnvelopeInternal().centre().y));
        }
        return binaryUnion(parts,0,parts.size());
    }

    private static void collect(Geometry source,List<Geometry> parts) {
        if (source instanceof Polygon) parts.add(source);
        else if (source instanceof GeometryCollection) for (int i=0;i<source.getNumGeometries();i++) collect(source.getGeometryN(i),parts);
    }

    private static Geometry orderedUnion(Geometry source,Path orderFile) throws Exception {
        var parts=new ArrayList<Geometry>();
        collect(source,parts);
        var order=Files.readAllLines(orderFile).stream().map(Integer::parseInt).toList();
        if(order.size()!=parts.size() || new HashSet<>(order).size()!=parts.size()
                || order.stream().anyMatch(i -> i<0 || i>=parts.size())) throw new IllegalArgumentException("Invalid permutation");
        return binaryUnion(order.stream().map(parts::get).toList(),0,parts.size());
    }

    private static Geometry binaryUnion(List<Geometry> parts,int start,int end) {
        if(end-start==1) return parts.get(start).copy();
        int mid=(start+end)/2;
        return OverlayNGRobust.overlay(binaryUnion(parts,start,mid),binaryUnion(parts,mid,end),OverlayNG.UNION);
    }
}
