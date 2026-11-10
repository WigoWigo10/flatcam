package org.flatcam.cam.geometry;

import java.util.Arrays;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Nominal round cutter sweep. Endcaps must survive even when a closed ring almost collapses. */
public final class CutterSweep {
    public static Geometry stroke(Geometry path,double radius,int quadrants,CancellationToken token) {
        token.throwIfCancellationRequested();
        if(!Double.isFinite(radius)||radius<=0||quadrants<1)throw new IllegalArgumentException("Invalid cutter sweep");
        if(path instanceof LineString line && line.isClosed() && line.getNumPoints()>2) {
            // Buffering a tiny closed line directly may simplify away its disk. Two open chains
            // retain round endcaps; their union represents the same physical path, without filling its interior.
            var c=line.getCoordinates();int split=0;double farthest=0;
            for(int i=1;i<c.length-1;i++){double d=c[0].distance(c[i]);if(d>farthest){farthest=d;split=i;}}
            if(split==0)return line.getFactory().createPoint(c[0]).buffer(radius,quadrants);
            Geometry first=line.getFactory().createLineString(Arrays.copyOfRange(c,0,split+1)).buffer(radius,quadrants);
            token.throwIfCancellationRequested();
            Geometry last=line.getFactory().createLineString(Arrays.copyOfRange(c,split,c.length)).buffer(radius,quadrants);
            token.throwIfCancellationRequested();Geometry result=OverlayNGRobust.union(List.of(first,last));
            token.throwIfCancellationRequested();return result;
        }
        Geometry result=path.buffer(radius,quadrants);token.throwIfCancellationRequested();return result;
    }
    private CutterSweep() { }
}
