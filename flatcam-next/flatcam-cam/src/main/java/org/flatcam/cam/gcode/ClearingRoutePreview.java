package org.flatcam.cam.gcode;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ncc.NccResult;
import org.locationtech.jts.geom.*;

/** Nominal single-pass XY route, before the user configures the final CNC Job. No G-code or swept buffers. */
public record ClearingRoutePreview(Geometry cuts,Geometry travel,Geometry entries,List<Tool> tools,
                                   double cutLength,double travelLength,int entryCount,double xyMinutes,
                                   boolean sourceOrder,String units) {
    public record Tool(double diameter,int entries,double cutLength,double travelLength) { }
    public ClearingRoutePreview { tools=List.copyOf(tools); }
    public static ClearingRoutePreview calculate(NccResult result,boolean sourceOrder,CancellationToken token) {
        var factory=result.geometry().getFactory();var cuts=new ArrayList<Geometry>();var travel=new ArrayList<Geometry>();
        var entries=new ArrayList<Coordinate>();var tools=new ArrayList<Tool>();Coordinate cursor=new Coordinate(0,0);
        var routing=sourceOrder?new CncRouting(CncRouting.Mode.SOURCE,true,false,List.of(),0):CncRouting.LEGACY;
        double totalCut=0,totalTravel=0;
        for(var tool:result.toolResults()) {
            token.throwIfCancellationRequested();if(tool.isEmpty())continue;
            int count=0;double cut=0,rapid=0;
            for(Coordinate[] path:CncRouting.order(tool.geometry(),cursor.x,cursor.y,tool.toolDiameter()/2,routing,token)) {
                token.throwIfCancellationRequested();if(path.length==0)continue;
                double distance=cursor.distance(path[0]);rapid+=distance;
                if(distance>0)travel.add(factory.createLineString(new Coordinate[]{new Coordinate(cursor),new Coordinate(path[0])}));
                entries.add(new Coordinate(path[0]));count++;
                if(path.length>1) {
                    var line=factory.createLineString(path);cuts.add(line);cut+=line.getLength();
                }else cuts.add(factory.createPoint(path[0]));
                cursor=path[path.length-1];
            }
            totalCut+=cut;totalTravel+=rapid;tools.add(new Tool(tool.toolDiameter(),count,cut,rapid));
        }
        double scale="IN".equalsIgnoreCase(result.units())?1/25.4:1;
        return new ClearingRoutePreview(factory.buildGeometry(cuts),factory.buildGeometry(travel),
                factory.createMultiPointFromCoords(entries.toArray(Coordinate[]::new)),tools,totalCut,totalTravel,entries.size(),
                totalCut/(300*scale)+totalTravel/(1500*scale),sourceOrder,result.units());
    }
}
