package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Experimental boundary-guided spiral with offset-pocket branches; nearly round regions keep
 * the Archimedean path. This is not medial-axis morphing or constant-engagement/trochoidal machining. */
final class SpiralClearing {
    static final int MAX_POINTS_PER_TOOL=1_000_000;
    static final class Budget {
        private int used;
        void point(){if(++used>MAX_POINTS_PER_TOOL)throw new SampleLimitException();}
        void minimum(double count){if(!Double.isFinite(count)||count>MAX_POINTS_PER_TOOL-used)throw new SampleLimitException();}
    }
    public static final class SampleLimitException extends IllegalArgumentException {
        public SampleLimitException(){super("CAM_SPIRAL_SAMPLE_LIMIT");}
    }
    record Result(List<LineString> paths,Geometry centers) { }
    private record Segment(Coordinate a,Coordinate b,int index) { }
    private record Ranked(LineString line,double from) { }

    static Result generate(Polygon allowed,double diameter,NccToolSettings settings,Budget budget,CancellationToken token) {
        return generate(allowed,diameter,settings,budget,token,adaptiveEnabled());
    }
    static boolean adaptiveEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("flatcam.spiral.adaptive","true"));
    }
    static Result generate(Polygon allowed,double diameter,NccToolSettings settings,Budget budget,CancellationToken token,boolean adaptive) {
        token.throwIfCancellationRequested();
        Geometry safe=GeosBufferOp.bufferOp(allowed,-diameter/1.99999999,new BufferParameters(64));
        var output=new ArrayList<LineString>();
        if(safe.isEmpty())return new Result(output,safe);
        // Radial pitch is exactly the user's maximum stepover; adaptive chords only approximate the curve.
        double pitch=diameter*(1-settings.overlapFraction());
        var components=new ArrayList<>(LinesRasterRefinement.polygons(safe));
        // Keep disconnected regions separate; their numerical order must not imply an unsafe cutting bridge.
        Coordinate cursor=new Coordinate(0,0);
        while(!components.isEmpty()) {
            token.throwIfCancellationRequested();int nearest=0;double best=Double.POSITIVE_INFINITY;
            Point at=allowed.getFactory().createPoint(cursor);
            for(int i=0;i<components.size();i++){double d=components.get(i).distance(at);if(d<best){best=d;nearest=i;}}
            Polygon component=components.remove(nearest);
            output.addAll(adaptive&&SpiralPocketPlanner.needsAdaptation(component)
                    ?SpiralPocketPlanner.generate(component,diameter,pitch,settings.contour(),budget,token):radial(component,component,diameter,pitch,budget,token));
            if(settings.contour()) {
                output.add(component.getFactory().createLineString(component.getExteriorRing().getCoordinates()));
                for(int i=0;i<component.getNumInteriorRing();i++)output.add(component.getFactory().createLineString(component.getInteriorRingN(i).getCoordinates()));
            }
            if(!output.isEmpty()){var last=output.getLast();cursor=last.getCoordinateN(last.getNumPoints()-1);}
        }
        return new Result(output,safe);
    }
    static List<LineString> radial(Polygon cell,Geometry clip,double diameter,double pitch,Budget budget,CancellationToken token) {
        double b=pitch/(2*Math.PI),chordTolerance=Math.min(diameter*.001,pitch*.01);
        Coordinate seed=StableInteriorPoint.find(cell,diameter*.005,token);
        if(seed==null)seed=cell.getInteriorPoint().getCoordinate();
        double radius=0;for(var point:cell.getExteriorRing().getCoordinates())radius=Math.max(radius,Math.hypot(point.x-seed.x,point.y-seed.y));
        double maximum=(radius+pitch)/b;budget.minimum(Math.ceil(maximum/(Math.PI/12)));
        var points=new ArrayList<Coordinate>();budget.point();points.add(new Coordinate(seed));double theta=0;
        while(theta<maximum) {
            token.throwIfCancellationRequested();double r=b*theta;
            double delta=Math.min(Math.PI/12,Math.sqrt(8*chordTolerance/(r+2*b+b*Math.PI/12)));
            double next=Math.min(maximum,theta+delta);
            if(!(next>theta)||!Double.isFinite(next))throw new SampleLimitException();
            theta=next;double rr=b*theta;budget.point();points.add(new Coordinate(seed.x+rr*Math.cos(theta),seed.y+rr*Math.sin(theta)));
        }
        var spiral=cell.getFactory().createLineString(points.toArray(Coordinate[]::new));return orderedClip(spiral,clip,diameter,token);
    }
    static List<LineString> orderedClip(LineString source,Geometry mask,double diameter,CancellationToken token) {
        token.throwIfCancellationRequested();Geometry clipped=OverlayNGRobust.overlay(source,mask,OverlayNG.INTERSECTION);
        var fragments=new ArrayList<LineString>();collect(clipped,fragments);
        return orderFragments(source,fragments,diameter,token);
    }
    private static List<LineString> orderFragments(LineString source,List<LineString> fragments,double diameter,CancellationToken token) {
        if(fragments.isEmpty())return fragments;
        var index=new STRtree();Coordinate[] sampled=source.getCoordinates();double magnitude=0;
        for(int i=1;i<sampled.length;i++)index.insert(new Envelope(sampled[i-1],sampled[i]),new Segment(sampled[i-1],sampled[i],i-1));
        for(var p:sampled)magnitude=Math.max(magnitude,Math.max(Math.abs(p.x),Math.abs(p.y)));
        index.build();double tolerance=Math.max(diameter*1e-8,Math.ulp(magnitude)*64);
        var ordered=new ArrayList<Ranked>();
        for(var fragment:fragments) {
            token.throwIfCancellationRequested();double first=position(fragment.getCoordinateN(0),index,tolerance),last=position(fragment.getCoordinateN(fragment.getNumPoints()-1),index,tolerance);
            ordered.add(new Ranked(first<=last?fragment:fragment.reverse(),Math.min(first,last)));
        }
        ordered.sort(Comparator.comparingDouble(Ranked::from));return ordered.stream().map(Ranked::line).toList();
    }
    private static double position(Coordinate point,STRtree index,double tolerance) {
        var envelope=new Envelope(point);envelope.expandBy(tolerance);double distance=Double.POSITIVE_INFINITY,position=Double.NaN;
        for(Object item:index.query(envelope)) {
            var s=(Segment)item;double dx=s.b().x-s.a().x,dy=s.b().y-s.a().y,length=dx*dx+dy*dy;
            double t=length==0?0:Math.max(0,Math.min(1,((point.x-s.a().x)*dx+(point.y-s.a().y)*dy)/length));
            double d=Math.hypot(point.x-s.a().x-t*dx,point.y-s.a().y-t*dy);
            if(d<distance){distance=d;position=s.index()+t;}
        }
        if(!Double.isFinite(position)||distance>tolerance)throw new IllegalStateException("Spiral clipping lost its path order");
        return position;
    }
    private static void collect(Geometry g,List<LineString> paths){
        if(g instanceof LineString line){if(line.getLength()>0)paths.add(line);}
        else if(g instanceof GeometryCollection)for(int i=0;i<g.getNumGeometries();i++)collect(g.getGeometryN(i),paths);
    }
    private SpiralClearing() { }
}
