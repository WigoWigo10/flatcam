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
    public record Result(Geometry cutterCenters,Geometry reachable,Geometry unreachable,
                         Geometry stableReachable,Geometry precisionSensitiveReachable,double centerTolerance) { }
    /** Raw residual is retained even when its narrow core disappears at the stated analysis tolerance. */
    public record Residual(Geometry reachableUncut,Geometry coreBeyondTolerance,Geometry outsideReachable,double tolerance,
                           Geometry stableReachableUncut,Geometry stableCore,Geometry precisionSensitiveUncut) { }

    public static Result calculate(Geometry allowed,double diameter,CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if(allowed==null || !Double.isFinite(allowed.getArea()) || !allowed.isEmpty() && (allowed.getDimension()!=2 || !allowed.isValid()))
            throw new IllegalArgumentException("Reachability requires a valid allowed area");
        if(!Double.isFinite(diameter)||diameter<=0)throw new IllegalArgumentException("Cutter diameter must be positive");
        if(allowed.isEmpty()) {
            Geometry empty=allowed.getFactory().createPolygon();
            return new Result(empty,empty,empty,empty,empty,diameter*1e-8);
        }
        var parameters=new BufferParameters(QUADRANT_SEGMENTS);
        Geometry centers=GeosBufferOp.bufferOp(allowed,-diameter/2,parameters);
        cancellation.throwIfCancellationRequested();
        Geometry reachable=centers.isEmpty()?allowed.getFactory().createPolygon():
                OverlayNGRobust.overlay(allowed,GeosBufferOp.bufferOp(centers,diameter/2,parameters),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();
        Geometry unreachable=OverlayNGRobust.overlay(allowed,reachable,OverlayNG.DIFFERENCE);
        // Exact-fit channels can survive erosion only as ULP-width slivers. Keep nominal access,
        // but distinguish access with a positive numerical interior; never shrink the physical cutter.
        var bounds=allowed.getEnvelopeInternal();double magnitude=Math.max(Math.max(Math.abs(bounds.getMinX()),Math.abs(bounds.getMaxX())),Math.max(Math.abs(bounds.getMinY()),Math.abs(bounds.getMaxY())));
        double tolerance=Math.max(diameter*1e-8,Math.ulp(magnitude)*64);
        Geometry stableCenters=GeosBufferOp.bufferOp(allowed,-diameter/2-tolerance,parameters);
        cancellation.throwIfCancellationRequested();
        Geometry stable=stableCenters.isEmpty()?allowed.getFactory().createPolygon():OverlayNGRobust.overlay(reachable,GeosBufferOp.bufferOp(stableCenters,diameter/2,parameters),OverlayNG.INTERSECTION);
        Geometry sensitive=OverlayNGRobust.overlay(reachable,stable,OverlayNG.DIFFERENCE);
        cancellation.throwIfCancellationRequested();return new Result(centers,reachable,unreachable,stable,sensitive,tolerance);
    }
    public static Residual classify(Geometry remaining,Result access,double tolerance,CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if(!Double.isFinite(tolerance)||tolerance<0)throw new IllegalArgumentException("Analysis tolerance must be non-negative");
        Geometry raw=OverlayNGRobust.overlay(remaining,access.reachable(),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();
        Geometry core=tolerance==0?raw:GeosBufferOp.bufferOp(raw,-tolerance,new BufferParameters(QUADRANT_SEGMENTS));
        cancellation.throwIfCancellationRequested();
        Geometry outside=OverlayNGRobust.overlay(remaining,access.reachable(),OverlayNG.DIFFERENCE);
        Geometry stable=OverlayNGRobust.overlay(remaining,access.stableReachable(),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();
        Geometry stableCore=tolerance==0?stable:GeosBufferOp.bufferOp(stable,-tolerance,new BufferParameters(QUADRANT_SEGMENTS));
        Geometry sensitive=OverlayNGRobust.overlay(remaining,access.precisionSensitiveReachable(),OverlayNG.INTERSECTION);
        cancellation.throwIfCancellationRequested();return new Residual(raw,core,outside,tolerance,stable,stableCore,sensitive);
    }
    /** Sagitta of one nominal circular-buffer chord; this is not a bound on all numerical/topology errors. */
    public static double chordError(double radius,int quadrantSegments) {
        if(!Double.isFinite(radius)||radius<0||quadrantSegments<1)throw new IllegalArgumentException("Invalid circular approximation");
        return radius*(1-Math.cos(Math.PI/(4*quadrantSegments)));
    }
    private MachiningReachability() { }
}
