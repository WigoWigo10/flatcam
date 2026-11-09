import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.flatcam.cam.gerber.*;
import org.flatcam.cam.excellon.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;

/** Reads the downloaded corpus with production FX parsers. Does not generate CAM or change originals. */
public class CommunityBoardInventory {
    static final WKTWriter WKT = new WKTWriter();
    public static void main(String[] args) throws Exception {
        if (args.length!=2) throw new IllegalArgumentException("Usage: CommunityBoardInventory.java INVENTORY_JSON OUTPUT_JSON");
        Path input=Path.of(args[0]).toAbsolutePath(), root=input.getParent();
        var inventory=new JSONObject(Files.readString(input));var boards=new JSONArray();int errors=0,parsed=0;
        for (Object value:inventory.getJSONArray("boards")) {
            var board=(JSONObject)value;var entries=new JSONArray();var drills=new JSONArray();
            Geometry preview=null,outline=null;String previewPath=null,units="MM";int bestPoints=-1;
            int drillCount=0,slotCount=0;
            for (Object fileValue:board.getJSONArray("fabricationFiles")) {
                var file=(JSONObject)fileValue;String name=file.getString("path"), lower=name.toLowerCase(Locale.ROOT);
                Path path=root.resolve(name).normalize();if(!path.startsWith(root))throw new IllegalArgumentException("Corpus escape");
                if(lower.endsWith(".gbrjob"))continue;
                var entry=new JSONObject().put("path",name);
                try {
                    if(lower.endsWith(".drl")||lower.endsWith(".xln")||lower.endsWith(".exc")) {
                        var image=new ExcellonParser().parse(path);
                        entry.put("kind","excellon").put("units",image.units()).put("drills",image.totalDrills()).put("slots",image.totalSlots())
                                .put("toolDiameters",image.toolDiameters().toString()).put("empty",image.solidGeometry().isEmpty());
                        drillCount+=image.totalDrills();slotCount+=image.totalSlots();
                        if(!image.solidGeometry().isEmpty())drills.put(new JSONObject().put("units",image.units()).put("wkt",WKT.write(image.solidGeometry())));
                    } else {
                        var image=new GerberParser().parse(path);var geometry=image.solidGeometry();
                        entry.put("kind","gerber").put("units",image.units()).put("empty",geometry.isEmpty())
                                .put("points",geometry.getNumPoints()).put("parts",geometry.getNumGeometries());
                        var apertures=new JSONObject();for(var aperture:image.apertures().values()) {
                            String kind=aperture.kind.name();apertures.put(kind,apertures.optInt(kind)+1);
                        }
                        entry.put("apertureKinds",apertures);
                        boolean copper=lower.matches(".*(?:[fb][._]cu|cu(?:top|bottom)|\\.(?:gbl|gtl)$|/(?:tl|bl)\\.gbr$).*");
                        String preferred=board.optString("preferredPreview","").toLowerCase(Locale.ROOT);
                        int score=geometry.getNumPoints()+(!preferred.isEmpty()&&lower.endsWith(preferred)?1_000_000_000:0);
                        if(copper&&!geometry.isEmpty()&&score>bestPoints) {
                            preview=geometry;previewPath=name;units=image.units();bestPoints=score;
                        }
                        if(lower.matches(".*(?:edge[._]?cuts|edgecuts|\\.gm1$|\\.gko$|/ko\\.gbr$).*"))outline=geometry;
                    }
                    entry.put("status","OK");parsed++;
                } catch(Exception failure) {entry.put("status","ERROR").put("error",failure.toString());errors++;}
                entries.put(entry);
            }
            var result=new JSONObject().put("id",board.getString("id")).put("name",board.getString("name"))
                    .put("category",board.getString("category")).put("files",entries).put("totalDrills",drillCount).put("totalSlots",slotCount);
            if(preview!=null) {
                var e=preview.getEnvelopeInternal();
                result.put("preview",new JSONObject().put("path",previewPath).put("units",units).put("wkt",WKT.write(preview))
                        .put("width",e.getWidth()).put("height",e.getHeight()));
            }
            if(outline!=null)result.put("outlineWkt",WKT.write(outline));
            result.put("drillLayers",drills);boards.put(result);
            System.out.println(board.getString("id")+": "+entries.length()+" CAM files; "+drillCount+" drills; "+slotCount+" slots; preview="+previewPath);
        }
        var output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,new JSONObject().put("schema",1).put("scope","FX production parsers; no GUI/CAM/machine validation")
                .put("parsed",parsed).put("errors",errors).put("boards",boards).toString(2));
        System.out.println("Parsed="+parsed+"; errors="+errors);
        if(errors>0)System.exit(1);
    }
}
