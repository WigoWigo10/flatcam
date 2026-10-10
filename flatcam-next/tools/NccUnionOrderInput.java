import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;
/** Input envelopes for the read-only native ordering diagnostic. */
public class NccUnionOrderInput {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Usage: EXPORT NEW_OUTPUT");
        Path output=Path.of(args[1]);
        if(Files.exists(output)) throw new IllegalArgumentException("Choose new output");
        if(args[0].equals("--public")) {
            var cases=new JSONArray();
            var factory=new GeometryFactory();
            for(int count:List.of(1,2,10,12,32,33,41,47,80,160)) {
                var polygons=new ArrayList<Polygon>();
                for(int i=0;i<count;i++) {
                    int index=i*17%count;
                    polygons.add((Polygon)factory.createPoint(new Coordinate((index%7)*6,(index/7)*5)).buffer(1,4));
                }
                Geometry source=factory.createMultiPolygon(polygons.toArray(Polygon[]::new));
                cases.put(new JSONObject().put("count",count).put("sourceWkt",new WKTWriter().write(source)));
            }
            Files.writeString(output,new JSONObject().put("schema",1).put("cases",cases).toString());
            return;
        }
        Geometry source=new WKTReader().read(new JSONObject(Files.readString(Path.of(args[0]))).getString("sourceWkt"));
        var parts=new ArrayList<Geometry>();
        collect(source,parts);
        var rows=new ArrayList<String>();
        for(int i=0;i<parts.size();i++) {
            var bounds=parts.get(i).getEnvelopeInternal();
            rows.add(i + " " + (bounds.getMinX()+bounds.getMaxX()) + " " + (bounds.getMinY()+bounds.getMaxY()));
        }
        Files.write(output,rows);
        System.out.println("Polygon count: " + parts.size());
    }
    static void collect(Geometry source,List<Geometry> parts) {
        if(source instanceof Polygon) parts.add(source);
        else if(source instanceof GeometryCollection) for(int i=0;i<source.getNumGeometries();i++) collect(source.getGeometryN(i),parts);
    }
}
