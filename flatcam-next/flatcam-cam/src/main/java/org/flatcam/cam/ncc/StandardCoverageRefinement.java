package org.flatcam.cam.ncc;

import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.*;

/** Additive passes for reachable stock lost between legacy inward offsets.
 * Keeps every original offset and the nominal cutter; never treats stock as a clearance boundary. */
final class StandardCoverageRefinement {
    static boolean enabled(){return !"false".equalsIgnoreCase(System.getProperty("flatcam.standard.refine","true"));}
    static void supplement(Geometry current,Geometry next,double diameter,double step,List<LineString> paths,CancellationToken token){
        token.throwIfCancellationRequested();var parameters=new BufferParameters(64);double radius=diameter/2;
        var e=current.getEnvelopeInternal();double magnitude=Math.max(Math.max(Math.abs(e.getMinX()),Math.abs(e.getMaxX())),Math.max(Math.abs(e.getMinY()),Math.abs(e.getMaxY())));
        // Selection guard only; physical cutter size and emitted offsets stay unchanged.
        double guard=Math.max(diameter*1e-7,Math.ulp(magnitude)*128);
        Geometry core=GeosBufferOp.bufferOp(current,-radius-guard,parameters);token.throwIfCancellationRequested();
        if(core.isEmpty())return;
        Geometry served=next.isEmpty()?next:GeosBufferOp.bufferOp(next,radius+guard,parameters);token.throwIfCancellationRequested();
        Geometry need=served.isEmpty()?core:OverlayNGRobust.overlay(core,served,OverlayNG.DIFFERENCE);token.throwIfCancellationRequested();
        if(need.isEmpty())return;
        // Half a requested stepover never exceeds the nominal radius, including at zero overlap.
        Geometry half=GeosBufferOp.bufferOp(current,-step/2,parameters);token.throwIfCancellationRequested();
        Geometry mask=GeosBufferOp.bufferOp(need,radius+guard,parameters);token.throwIfCancellationRequested();
        Geometry trace=OverlayNGRobust.overlay(half.getBoundary(),mask,OverlayNG.INTERSECTION);token.throwIfCancellationRequested();
        collect(trace,paths);
    }
    private static void collect(Geometry geometry,List<LineString> paths){
        if(geometry instanceof LineString line){if(line.getLength()>0)paths.add(line);}
        else if(geometry instanceof GeometryCollection)for(int i=0;i<geometry.getNumGeometries();i++)collect(geometry.getGeometryN(i),paths);
    }
    private StandardCoverageRefinement(){ }
}
