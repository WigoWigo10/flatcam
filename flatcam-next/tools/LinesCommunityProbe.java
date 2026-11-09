import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.flatcam.cam.*;
import org.flatcam.cam.ncc.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;

/** Exports untouched raw Lines paths, so duplicate travel is NOT lost in a spatial union. */
public class LinesCommunityProbe {
    public static void main(String[] args) throws Exception {
        if(args.length<2||args.length>3)throw new IllegalArgumentException("Usage: LinesCommunityProbe.java FX_IMPORT_JSON OUTPUT_DIR [BOARD_ID]");
        var inventory=new JSONObject(Files.readString(Path.of(args[0])));var directory=Path.of(args[1]);Files.createDirectories(directory);
        var writer=new WKTWriter();var reader=new WKTReader();
        for(Object entry:inventory.getJSONArray("boards")) {
            var board=(JSONObject)entry;String id=board.getString("id");
            if(args.length==3&&!args[2].equals(id))continue;
            if(!board.has("preview")){System.out.println(id+": no preview");continue;}
            var preview=board.getJSONObject("preview");Geometry copper=reader.read(preview.getString("wkt"));
            Geometry boundary=copper.getFactory().toGeometry(copper.getEnvelopeInternal());
            Geometry paintArea=boundary.difference(copper);String before=writer.write(copper);
            String units=preview.getString("units");double diameter=units.equals("IN")?.4/25.4:.4;
            for(boolean connect:List.of(false,true))for(boolean contour:List.of(false,true))for(String operation:List.of("ncc","paint")) {
                String caseId=id+"-"+operation+"-c"+(connect?1:0)+"-e"+(contour?1:0);
                Path file=directory.resolve(caseId+".json");
                if(Files.exists(file)){System.out.println(caseId+": already exported");continue;}
                long start=System.nanoTime();
                CancellationToken cancellation=()->System.nanoTime()-start>90_000_000_000L;
                var document=new JSONObject().put("schema",1).put("id",caseId).put("board",id).put("name",board.getString("name"))
                        .put("source",preview.getString("path")).put("units",units).put("operation",operation).put("diameter",diameter)
                        .put("overlap",.4).put("connect",connect).put("contour",contour)
                        .put("boundaryPolicy","explicit copper bounding rectangle; diagnostic, not a CNC setup");
                try {
                    NccResult result=operation.equals("ncc")
                            ?NccGenerator.generate(units,copper,new NccParameters(List.of(diameter),.4,0,NccMethod.LINES,connect,contour,0,false,NccOrder.NONE,new NccBoundary.Area(boundary)),cancellation,ProgressCallback.none())
                            :NccGenerator.paint(units,paintArea,new PaintParameters(List.of(diameter),.4,0,NccMethod.LINES,connect,contour,NccOrder.NONE,false),cancellation,ProgressCallback.none());
                    document.put("status","GENERATED").put("rawPathsWkt",writer.write(result.geometry()))
                            .put("clearingAreaWkt",writer.write(result.clearingArea())).put("copperWkt",writer.write(copper))
                            .put("failedPolygons",result.totalFailedPolygonCount());
                    if(!before.equals(writer.write(copper)))throw new AssertionError("Source changed");
                }catch(Exception error){document.put("status","ERROR_OR_BUDGET").put("error",error.toString());}
                document.put("elapsedMs",(System.nanoTime()-start)/1e6);Files.writeString(file,document.toString());
                System.out.println(caseId+": "+document.getString("status")+" "+document.getDouble("elapsedMs")+" ms");System.out.flush();
            }
        }
    }
}
