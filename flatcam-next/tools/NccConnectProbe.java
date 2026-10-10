import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.*;
import org.locationtech.jts.operation.overlay.OverlayOp;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.flatcam.cam.ncc.*;

/** Public, stage-by-stage Connect diagnostics. Never reads a private project. */
public class NccConnectProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: NccConnectProbe.java OUTPUT_JSON");
        var output = new JSONArray();
        var reader = new WKTReader();
        var writer = new WKTWriter();
        var fixtures = new ArrayList<>(List.of(
                "POLYGON ((0 0,30 0,30 20,0 20,0 0),(4 4,4 16,26 16,26 4,4 4))",
                "POLYGON ((0 0,16 0,16 12,10 12,10 8,6 8,6 12,0 12,0 0))",
                "MULTIPOLYGON (((0 0,6 0,6 8,0 8,0 0)),((8 0,14 0,14 8,8 8,8 0)))"));
        // Public many-island control: exceeds both union index capacities,
        // with equal X/Y keys. No coordinates extracted from a private board.
        var factory = new GeometryFactory();
        var islands = new ArrayList<Polygon>();
        for (int row=0;row<3;row++) for(int column=0;column<4;column++) {
            islands.add((Polygon)factory.createPoint(new Coordinate(6*column,5*row)).buffer(1,16));
        }
        fixtures.add(writer.write(factory.createMultiPolygon(islands.toArray(Polygon[]::new))));
        for (int i = 0; i < fixtures.size(); i++) {
            for (double scale : List.of(1., 1 / 25.4)) {
                for (double shift : List.of(0., -40.)) {
                    Geometry copper = AffineTransformation.translationInstance(shift,shift / 2).transform(reader.read(fixtures.get(i)));
                    copper = AffineTransformation.scaleInstance(scale,scale).transform(copper);
                    for (boolean listed : List.of(false,true)) {
                        Geometry multipart = copper instanceof MultiPolygon ? copper
                                : copper.getFactory().createMultiPolygon(new Polygon[]{(Polygon)copper});
                        Geometry input = listed ? copper.getFactory().createGeometryCollection(new Geometry[]{multipart}) : copper;
                        // These classic/NG diagnostic stages deliberately keep
                        // the direct input; only production paths use the list.
                        Geometry boundary = BufferOp.bufferOp(copper.convexHull(),scale,
                                new BufferParameters(64,BufferParameters.CAP_ROUND,BufferParameters.JOIN_MITRE,5));
                        Geometry defaultOverlay = boundary.difference(copper).buffer(0);
                        Geometry classic = OverlayOp.overlayOp(boundary,copper,OverlayOp.DIFFERENCE).buffer(0);
                        Geometry ng = OverlayNGRobust.overlay(boundary,copper,OverlayOp.DIFFERENCE).buffer(0);
                        Geometry ngClean = OverlayNGRobust.overlay(boundary,copper.buffer(0),OverlayOp.DIFFERENCE).buffer(0);
                        var params = new NccParameters(.5 * scale,.4,scale,NccMethod.STANDARD,true,true,0);
                        var result = NccGenerator.generate(scale == 1 ? "MM" : "IN",input,params);
                        var plain = NccGenerator.generate(scale == 1 ? "MM" : "IN",input,
                                new NccParameters(.5 * scale,.4,scale,NccMethod.STANDARD,false,true,0));
                        output.put(new JSONObject().put("id",i + ":" + scale + ":" + shift + ":" + listed)
                                .put("sourceContainer",listed ? "list-multipart" : "direct")
                                .put("diameter",.5 * scale).put("margin",scale)
                                .put("inputWkt",writer.write(copper)).put("clearingWkt",writer.write(result.clearingArea()))
                                .put("defaultOverlayWkt",writer.write(defaultOverlay)).put("classicWkt",writer.write(classic))
                                .put("ngWkt",writer.write(ng))
                                .put("ngCleanWkt",writer.write(ngClean))
                                .put("plainWkt",writer.write(plain.geometry())).put("connectedWkt",writer.write(result.geometry())));
                    }
                }
            }
        }
        Files.writeString(Path.of(args[0]),new JSONObject().put("schema",1).put("cases",output).toString(2));
    }
}
