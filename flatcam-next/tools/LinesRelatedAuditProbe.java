package org.flatcam.fx;

import java.nio.file.*;
import java.util.*;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

/** Independent distance guards on real generated paths, not a full coverage or parity proof. */
public final class LinesRelatedAuditProbe {
    private static final GeometryFactory F=new GeometryFactory();
    private static Geometry box(double x1,double x2,double y1,double y2){return F.toGeometry(new Envelope(x1,x2,y1,y2));}
    private static Geometry trace(double x1,double y1,double x2,double y2,double radius){return F.createLineString(new Coordinate[]{new Coordinate(x1,y1),new Coordinate(x2,y2)}).buffer(radius,64);}
    public static void main(String[] args)throws Exception {
        NccMethod method=NccMethod.valueOf(System.getProperty("flatcam.audit.method","LINES"));
        Geometry board=box(0,36,0,24);
        Geometry copper=F.createPoint(new Coordinate(10,17)).buffer(2.8,64)
                .union(F.createPoint(new Coordinate(10,7)).buffer(2.8,64))
                .union(trace(3,12,9,17,.65)).union(trace(3,3,9,7,.65))
                .union(box(23,29,14,18).buffer(1.2,64)).union(trace(29,16,33,16,.6))
                .union(trace(24,7,29,7,1.8)).union(trace(29,7,33,4,.5));
        JSONArray cases=new JSONArray();int failures=0,errors=0;
        for(String units:List.of("MM","IN")) {
            double scale=units.equals("MM")?1:1/25.4;var transform=AffineTransformation.scaleInstance(scale,scale);
            Geometry b=transform.transform(board),c=transform.transform(copper),area=b.difference(c);String before=c.toText();
            for(double angle:!method.usesRaster()?List.of(0.):List.of(0.,37.5123456,90.))for(boolean rest:List.of(false,true))for(boolean connect:List.of(false,true))for(boolean contour:List.of(false,true))for(String operation:List.of("NCC","Paint")) {
                var row=new JSONObject().put("units",units).put("angle",angle).put("rest",rest).put("connect",connect).put("contour",contour).put("operation",operation);
                try {
                    double d=.8*scale,small=.4*scale;var tools=List.of(d,small);
                    var nccSettings=Map.of(d,new NccToolSettings(.4,method,connect,contour,0,NccSeedPolicy.STABLE,angle),small,new NccToolSettings(.4,method,connect,contour,0,NccSeedPolicy.STABLE,135.));
                    var paintSettings=Map.of(d,new PaintToolSettings(.4,0,method,connect,contour,angle),small,new PaintToolSettings(.4,0,method,connect,contour,135.));
                    var result=operation.equals("NCC")?NccGenerator.generate(units,c,new NccParameters(tools,.4,0,method,connect,contour,0,rest,NccOrder.NONE,new NccBoundary.Area(b),List.of(),nccSettings))
                            :NccGenerator.paint(units,area,new PaintParameters(tools,.4,0,method,connect,contour,NccOrder.NONE,rest,paintSettings),CancellationToken.NONE,ProgressCallback.none());
                    var details=new JSONArray();boolean ok=!result.isEmpty() && before.equals(c.toText());
                    for(var tool:result.toolResults()) {
                        if(tool.isEmpty())continue;
                        // Existing round buffers approximate arcs; flag only intrusion exceeding 0.5% of cutter diameter.
                        // This avoids claiming that approximation sagitta is an exact cutter collision proof.
                        double distance=tool.geometry().distance(c),edge=tool.geometry().distance(b.getBoundary());
                        double allowance=.005*tool.toolDiameter();boolean safe=distance+allowance>=tool.toolDiameter()/2 && edge+allowance>=tool.toolDiameter()/2 && b.buffer(allowance).covers(tool.geometry());
                        ok &= safe;
                        details.put(new JSONObject().put("diameter",tool.toolDiameter()).put("copperDistance",distance).put("edgeDistance",edge).put("allowance",allowance).put("safeWithinAllowance",safe).put("topLevelPathParts",tool.geometry().getNumGeometries()));
                    }
                    if(!ok)failures++;row.put("status",ok?"PASS":"FAIL").put("tools",details).put("failedPolygons",result.totalFailedPolygonCount());
                }catch(Exception failure){errors++;row.put("status","ORACLE_ERROR").put("error",failure.toString());failure.printStackTrace();}
                cases.put(row);System.out.println(operation+" "+units+" angle="+angle+" rest="+rest+" connect="+connect+" contour="+contour+": "+row.getString("status"));
            }
        }
        var output=Path.of(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,new JSONObject().put("schema",1).put("version",AboutInfo.version()).put("binarySource",AboutInfo.build().getProperty("sourceRevision"))
                .put("fixture","rounded pads, round rectangles and capsules; 36x24 mm diagnostic board; two cutters .8/.4 mm; smaller angle 135 degrees")
                .put("method",method.name()).put("scope","96 synthetic MM/IN raster controls, or 32 angle-independent controls: Rest, Connect, Contour. Spiral ignores raster angles. Global copper/outer-edge distances with 0.5%-diameter allowance; no whole-area coverage, path duplication, runtime or physical CNC proof")
                .put("cases",cases).put("failedCases",failures).put("oracleErrors",errors).toString(2));
        System.out.println("Lines distance audit: "+cases.length()+" cases; "+failures+" failed; "+errors+" errors");System.exit(failures>0 || errors>0?1:0);
    }
}
