package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ncc.geosbuffer.GeosBufferOp;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.overlayng.*;

/** Offset-contour spiral. Erosion splits form pocket branches; complete laps retain coverage.
 * Connect joins the local laps. No medial-axis, curvature or constant-engagement guarantee. */
final class SpiralPocketPlanner {
    static final int MAX_NODES=4096,MAX_DEPTH=2048;
    private static final int LOCAL_OFFSET_POINTS=2048,OFFSET_CACHE_LEVELS=8;
    static final class Pocket {
        final Polygon area;final List<Pocket> children=new ArrayList<>();final List<LineString> supplements=new ArrayList<>();final boolean root;final double distance;boolean fallback;
        Pocket(Polygon area,boolean root){this(area,root,0);}
        Pocket(Polygon area,boolean root,double distance){this.area=area;this.root=root;this.distance=distance;}
    }
    private record Pending(Pocket pocket,int depth) { }
    private static final class Visit {
        final Pocket pocket;final List<Pocket> pending;
        Visit(Pocket pocket){this.pocket=pocket;pending=new ArrayList<>(pocket.children);}
    }
    static boolean needsAdaptation(Polygon area) {
        var box=area.getEnvelopeInternal();double aspect=Math.max(box.getWidth(),box.getHeight())/Math.min(box.getWidth(),box.getHeight());
        return area.getNumInteriorRing()>0||aspect>1.4||area.getLength()*area.getLength()/(4*Math.PI*area.getArea())>1.08;
    }
    static Pocket partition(Polygon component,double diameter,double pitch,boolean contour,SpiralClearing.Budget budget,CancellationToken token) {
        // This point supplies a lower bound on the number of inward offsets, not a modified cutter size.
        var seed=component.getInteriorPoint();double clearance=seed.distance(component.getBoundary());
        budget.minimum(3*Math.floor(clearance/pitch));
        var root=new Pocket(component,true);var pending=new ArrayDeque<Pending>();pending.add(new Pending(root,0));int nodes=1;
        var parameters=new BufferParameters(64);
        // Rebase dense offsets on this immutable center component, not on the previous sampled arc.
        // Keep only a few levels alive; the published-path budget is unchanged.
        var offsets=new LinkedHashMap<Double,Geometry>(OFFSET_CACHE_LEVELS,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<Double,Geometry> entry){return size()>OFFSET_CACHE_LEVELS;}
        };
        while(!pending.isEmpty()) {
            token.throwIfCancellationRequested();var work=pending.remove();var node=work.pocket();
            if(work.depth()>=MAX_DEPTH){node.fallback=true;continue;}
            double localStep=cornerStep(node.area,diameter,pitch);
            double step=node.root&&!contour?localStep/2:localStep;
            Geometry local=GeosBufferOp.bufferOp(node.area,-step,parameters);token.throwIfCancellationRequested();
            // Small branches and terminal passes are cheaper locally; only dense levels need rebasing.
            Geometry eroded=local.getNumPoints()<=LOCAL_OFFSET_POINTS?local:
                    directOffset(component,node.distance+step,node.area,offsets,token);
            token.throwIfCancellationRequested();
            if(!eroded.isEmpty()&&eroded.getArea()>=node.area.getArea()){node.fallback=true;continue;}
            var children=LinesRasterRefinement.polygons(eroded);boolean terminal=false;
            if(children.isEmpty()) {
                // An extra half-step closes the last core when stepover is larger than the cutter radius.
                if(!(node.root&&!contour))children=LinesRasterRefinement.polygons(GeosBufferOp.bufferOp(node.area,-step/2,parameters));
                terminal=true;
            } else {
                // Some local lobes disappear even while another branch survives. A half-step
                // trace closes those lobes; doing this only at the last global level misses them.
                var e=node.area.getEnvelopeInternal();double magnitude=Math.max(Math.max(Math.abs(e.getMinX()),Math.abs(e.getMaxX())),Math.max(Math.abs(e.getMinY()),Math.abs(e.getMaxY())));
                double guard=Math.max(diameter*1e-7,Math.ulp(magnitude)*128);
                double radius=diameter/2;
                Geometry core=node.root&&!contour?node.area:GeosBufferOp.bufferOp(node.area,-radius-guard,parameters);
                Geometry served=GeosBufferOp.bufferOp(eroded,radius+guard,parameters);token.throwIfCancellationRequested();
                Geometry need=OverlayNGRobust.overlay(core,served,OverlayNG.DIFFERENCE);token.throwIfCancellationRequested();
                if(!need.isEmpty()) {
                    Geometry half=GeosBufferOp.bufferOp(node.area,-step/2,parameters);token.throwIfCancellationRequested();
                    Geometry mask=GeosBufferOp.bufferOp(need,radius+guard,parameters);token.throwIfCancellationRequested();
                    Geometry trace=OverlayNGRobust.overlay(half.getBoundary(),mask,OverlayNG.INTERSECTION);token.throwIfCancellationRequested();
                    collect(trace,node.supplements);
                }
                for(var path:node.supplements)for(int i=0;i<path.getNumPoints();i++)budget.point();
            }
            if(nodes+children.size()>MAX_NODES){node.fallback=true;continue;}
            for(var child:children)if(child.getArea()>0) {
                token.throwIfCancellationRequested();var next=new Pocket(child,false,node.distance+(terminal?step/2:step));node.children.add(next);nodes++;
                for(int i=0;i<child.getNumPoints();i++)budget.point();
                if(!terminal)pending.add(new Pending(next,work.depth()+1));
            }
        }
        return root;
    }
    /** Keep each rebased child inside its actual parent; do not introduce a bridge across pocket branches. */
    private static Geometry directOffset(Polygon root,double distance,Polygon parent,Map<Double,Geometry> cache,CancellationToken token){
        token.throwIfCancellationRequested();
        Geometry offset=cache.computeIfAbsent(distance,d->GeosBufferOp.bufferOp(root,-d,new BufferParameters(64)));
        token.throwIfCancellationRequested();
        if(offset.isEmpty())return offset;
        var prepared=PreparedGeometryFactory.prepare(parent);var pieces=new ArrayList<Polygon>();var bounds=parent.getEnvelopeInternal();
        for(var part:LinesRasterRefinement.polygons(offset)){
            token.throwIfCancellationRequested();if(!bounds.intersects(part.getEnvelopeInternal()))continue;
            if(prepared.covers(part))pieces.add(part);
            else if(prepared.intersects(part))pieces.addAll(LinesRasterRefinement.polygons(OverlayNGRobust.overlay(part,parent,OverlayNG.INTERSECTION)));
        }
        return parent.getFactory().buildGeometry(pieces);
    }
    /** Convex corners need a smaller normal step so the nominal circular cutter covers their bisectors. */
    static double cornerStep(Polygon area,double diameter,double requested) {
        double maximum=requested;var rings=new ArrayList<LineString>();rings.add(area.getExteriorRing());
        for(int i=0;i<area.getNumInteriorRing();i++)rings.add(area.getInteriorRingN(i));
        for(int r=0;r<rings.size();r++) {
            var points=rings.get(r).getCoordinates().clone();boolean ccw=Orientation.isCCW(points);
            if(ccw!=(r==0))Collections.reverse(Arrays.asList(points));
            int n=points.length-1;
            for(int i=0;i<n;i++) {
                var p=points[(i+n-1)%n];var v=points[i];var q=points[(i+1)%n];
                double ax=p.x-v.x,ay=p.y-v.y,bx=q.x-v.x,by=q.y-v.y;
                if(ax*by-ay*bx>=0)continue;double denominator=Math.hypot(ax,ay)*Math.hypot(bx,by);if(denominator==0)continue;
                double cos=Math.max(-1,Math.min(1,(ax*bx+ay*by)/denominator));
                double halfSin=Math.sqrt((1-cos)/2);
                maximum=Math.min(maximum,diameter*.5*(1+halfSin)*(1-1e-6));
            }
        }
        return maximum;
    }
    static List<LineString> generate(Polygon component,double diameter,double pitch,boolean contour,SpiralClearing.Budget budget,CancellationToken token) {
        var root=partition(component,diameter,pitch,contour,budget,token);var stack=new ArrayDeque<Visit>();stack.push(new Visit(root));
        var output=new ArrayList<LineString>();Coordinate cursor=new Coordinate(0,0);
        while(!stack.isEmpty()) {
            token.throwIfCancellationRequested();var visit=stack.peek();
            if(!visit.pending.isEmpty()) {
                int nearest=0;double best=Double.POSITIVE_INFINITY;Point point=component.getFactory().createPoint(cursor);
                for(int i=0;i<visit.pending.size();i++){double d=visit.pending.get(i).area.distance(point);if(d<best){best=d;nearest=i;}}
                stack.push(new Visit(visit.pending.remove(nearest)));continue;
            }
            stack.pop();var node=visit.pocket;
            if(node.fallback) {
                var extra=SpiralClearing.radial(node.area,component,diameter,pitch,budget,token);output.addAll(extra);
                if(!extra.isEmpty()){var last=extra.getLast();cursor=last.getCoordinateN(last.getNumPoints()-1);}
            }
            if(!node.supplements.isEmpty())cursor=supplements(node.supplements,cursor,output,token);
            if(!node.root)cursor=boundaries(node.area,cursor,output,budget,token);
            else if(node.children.isEmpty()&&!contour&&!node.fallback) {
                // A thin root has no inner offset: a clearing lap just inside it avoids an empty result.
                double epsilon=Math.min(diameter*.005,Math.min(component.getEnvelopeInternal().getWidth(),component.getEnvelopeInternal().getHeight())*.1);
                Geometry thin=GeosBufferOp.bufferOp(component,-epsilon,new BufferParameters(64));token.throwIfCancellationRequested();
                if(thin.isEmpty())output.addAll(SpiralClearing.radial(component,component,diameter,pitch,budget,token));
                else for(var part:LinesRasterRefinement.polygons(thin)){
                    for(int i=0;i<part.getNumPoints();i++)budget.point();
                    cursor=boundaries(part,cursor,output,budget,token);
                }
            }
        }
        return output;
    }
    private static Coordinate supplements(List<LineString> paths,Coordinate cursor,List<LineString> output,CancellationToken token){
        var pending=new ArrayList<>(paths);
        while(!pending.isEmpty()){
            token.throwIfCancellationRequested();int nearest=0;boolean reverse=false;double best=Double.POSITIVE_INFINITY;
            for(int i=0;i<pending.size();i++){
                var path=pending.get(i);double a=cursor.distance(path.getCoordinateN(0)),b=cursor.distance(path.getCoordinateN(path.getNumPoints()-1));
                if(a<best){best=a;nearest=i;reverse=false;}if(b<best){best=b;nearest=i;reverse=true;}
            }
            var path=pending.remove(nearest);if(reverse)path=path.reverse();output.add(path);cursor=path.getCoordinateN(path.getNumPoints()-1);
        }
        return cursor;
    }
    private static void collect(Geometry geometry,List<LineString> paths){
        if(geometry instanceof LineString line){if(line.getLength()>0)paths.add(line);}
        else if(geometry instanceof GeometryCollection)for(int i=0;i<geometry.getNumGeometries();i++)collect(geometry.getGeometryN(i),paths);
    }
    private static Coordinate boundaries(Polygon area,Coordinate cursor,List<LineString> output,SpiralClearing.Budget budget,CancellationToken token) {
        var rings=new ArrayList<LineString>();rings.add(area.getExteriorRing());
        for(int i=0;i<area.getNumInteriorRing();i++)rings.add(area.getInteriorRingN(i));
        while(!rings.isEmpty()) {
            token.throwIfCancellationRequested();int nearest=0;double best=Double.POSITIVE_INFINITY;Point at=area.getFactory().createPoint(cursor);
            for(int i=0;i<rings.size();i++){double d=rings.get(i).distance(at);if(d<best){best=d;nearest=i;}}
            var ring=rings.remove(nearest);Coordinate[] points=ring.getCoordinates().clone();
            if(!Orientation.isCCW(points))Collections.reverse(Arrays.asList(points));
            var line=area.getFactory().createLineString(points);var indexed=new LengthIndexedLine(line);double entry=indexed.project(cursor);
            var rotated=new ArrayList<Coordinate>();append(rotated,indexed.extractLine(entry,line.getLength()).getCoordinates());append(rotated,indexed.extractLine(0,entry).getCoordinates());
            var path=area.getFactory().createLineString(rotated.toArray(Coordinate[]::new));output.add(path);cursor=path.getCoordinateN(path.getNumPoints()-1);
            budget.point();budget.point(); // The projected entry can add two vertices to a lap.
        }
        return cursor;
    }
    private static void append(List<Coordinate> target,Coordinate[] points){for(var p:points)if(target.isEmpty()||!target.getLast().equals2D(p))target.add(p);}
    private SpiralPocketPlanner() { }
}
