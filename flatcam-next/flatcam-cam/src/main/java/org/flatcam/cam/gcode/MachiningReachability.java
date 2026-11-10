package org.flatcam.cam.gcode;

import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Nominal 2D access for a circular cutter, using the original allowed area, not the Rest remainder. */
public final class MachiningReachability {
    public static final int QUADRANT_SEGMENTS=64;
    public record Result(Geometry cutterCenters,Geometry reachable,Geometry unreachable) { }
    /** Raw residual is retained even when its narrow core disappears at the stated analysis tolerance. */
    public record Residual(Geometry reachableUncut,Geometry coreBeyondTolerance,Geometry outsideReachable,double tolerance) { }

    public static Result calculate(Geometry allowed,double diameter,CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if(allowed==null || !Double.isFinite(allowed.getArea()) || !allowed.isEmpty() && (allowed.getDimension()!=2 || !allowed.isValid()))
            throw new IllegalArgumentException("Reachability requires a valid allowed area");
        if(!Double.isFinite(diameter)||diameter<=0)throw new IllegalArgumentException("Cutter diameter must be positive");
        var parameters=new BufferParameters(QUADRANT_SEGMENTS);
        Geometry centers=GeosBufferOp.bufferOp(allowed,-diameter/2,parameters);
        cancellation.throwIfCancellationRequested();
        Geometry reachable=centers.isEmpty()?allowed.getFactory().createPolygon():
                OverlayNGRobust.overlay(allowed,GeosBufferOp.bufferOp(centers,diameter/2,parameters),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();
        Geometry unreachable=OverlayNGRobust.overlay(allowed,reachable,OverlayNG.DIFFERENCE);
        cancellation.throwIfCancellationRequested();return new Result(centers,reachable,unreachable);
    }
    public static Residual classify(Geometry remaining,Result access,double tolerance,CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if(!Double.isFinite(tolerance)||tolerance<0)throw new IllegalArgumentException("Analysis tolerance must be non-negative");
        Geometry raw=OverlayNGRobust.overlay(remaining,access.reachable(),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();
        Geometry core=tolerance==0?raw:GeosBufferOp.bufferOp(raw,-tolerance,new BufferParameters(QUADRANT_SEGMENTS));
        cancellation.throwIfCancellationRequested();
        Geometry outside=OverlayNGRobust.overlay(remaining,access.reachable(),OverlayNG.DIFFERENCE);
        cancellation.throwIfCancellationRequested();return new Residual(raw,core,outside,tolerance);
    }
    /** Sagitta of one nominal circular-buffer chord; this is not a bound on all numerical/topology errors. */
    public static double chordError(double radius,int quadrantSegments) {
        if(!Double.isFinite(radius)||radius<0||quadrantSegments<1)throw new IllegalArgumentException("Invalid circular approximation");
        return radius*(1-Math.cos(Math.PI/(4*quadrantSegments)));
    }
    private MachiningReachability() { }
}
