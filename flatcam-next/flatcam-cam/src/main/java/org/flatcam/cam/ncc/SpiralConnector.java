package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;

/** Ordered spiral links. Short center-boundary walks and finishing loops never authorize a copper shortcut. */
final class SpiralConnector {
    private static final class Ring {
        final LineString line;final LengthIndexedLine indexed;final double length;boolean finish,used;Boolean finishPossible;
        Ring(LineString line){this.line=line;indexed=new LengthIndexedLine(line);length=line.getLength();}
    }
    private final GeometryFactory factory;private final PreparedGeometry allowed;
    private final IndexedFacetDistance clearance;
    private final double diameter,tolerance,limit;private final CancellationToken token;private final STRtree index=new STRtree();
    private final List<Ring> rings=new ArrayList<>();
    private SpiralConnector(Geometry area,Geometry centers,double diameter,CancellationToken token){
        factory=area.getFactory();allowed=PreparedGeometryFactory.prepare(area);clearance=new IndexedFacetDistance(area.getBoundary());this.diameter=diameter;this.token=token;limit=10*diameter;
        var e=area.getEnvelopeInternal();double magnitude=Math.max(Math.max(Math.abs(e.getMinX()),Math.abs(e.getMaxX())),Math.max(Math.abs(e.getMinY()),Math.abs(e.getMaxY())));
        tolerance=Math.max(diameter*1e-8,Math.ulp(magnitude)*64);
        for(var polygon:LinesRasterRefinement.polygons(centers)){
            addRing(polygon.getExteriorRing());for(int i=0;i<polygon.getNumInteriorRing();i++)addRing(polygon.getInteriorRingN(i));
        }
        index.build();
    }
    private void addRing(LineString line){var ring=new Ring(line);rings.add(ring);index.insert(line.getEnvelopeInternal(),ring);}
    static List<LineString> connect(List<LineString> source,Geometry area,Geometry centers,double diameter,boolean contour,CancellationToken token){
        if(source.size()<2||centers==null||centers.isEmpty())return source;
        var previous=NccGenerator.connectOrderedPaths(source,area,diameter,area.getFactory(),token);
        if(previous.size()==1)return previous;
        var candidate=new SpiralConnector(area,centers,diameter,token).connect(source,contour);
        // Per-pocket policy: never increase entry count. At a tie, prefer the shorter nominal XY time.
        if(candidate.size()>previous.size() || candidate.size()==previous.size()&&xyCost(candidate)>=xyCost(previous))return previous;
        return candidate;
    }
    private static double xyCost(List<LineString> paths){
        var at=new Coordinate(0,0);double cost=0;
        for(var path:paths){cost+=path.getLength()+at.distance(path.getCoordinateN(0))/5;at=path.getCoordinateN(path.getNumPoints()-1);}
        return cost;
    }
    private List<LineString> connect(List<LineString> source,boolean contour){
        var finishing=new IdentityHashMap<LineString,Ring>();
        for(var path:source){
            token.throwIfCancellationRequested();
            if(contour&&path.isClosed()){
                Ring match=null;
                for(var ring:near(path.getCoordinateN(0)))if(ring.line.equalsExact(path)){match=ring;break;}
                if(match!=null){match.finish=true;finishing.put(path,match);}
            }
        }
        var output=new ArrayList<LineString>();var current=new ArrayList<Coordinate>();
        for(var path:source){
            token.throwIfCancellationRequested();var points=path.getCoordinates();
            if(contour&&path.isClosed()) {
                var ring=finishing.get(path);if(ring!=null&&ring.used)continue;
                // Finish this component before visiting the next; never postpone loose contours across pockets.
                if(!current.isEmpty()){
                    var indexed=new LengthIndexedLine(path);
                    var rotated=lap(new Ring(path),indexed.project(current.getLast()),current.getLast(),false);
                    var connector=link(current.getLast(),rotated[0]);
                    if(connector!=null){append(current,connector);points=rotated;}else flush(current,output);
                }
                append(current,points);if(ring!=null)ring.used=true;continue;
            }
            if(!current.isEmpty()){
                var link=link(current.getLast(),points[0]);
                if(link==null)flush(current,output);else append(current,link);
            }
            // A finishing lap can start/end at an existing spiral contact, preserving all spiral cuts and their direction.
            finishAt(points[0],current);append(current,points);finishAt(points[points.length-1],current);
        }
        flush(current,output);return output;
    }
    private void finishAt(Coordinate contact,List<Coordinate> current){
        for(var ring:near(contact))if(ring.finish&&!ring.used){
            token.throwIfCancellationRequested();
            // A chord-approximated center boundary may not admit the exact nominal disk.
            // Reject that finishing lap once, not again at every spiral contact with the same ring.
            if(ring.finishPossible==null)ring.finishPossible=safe(ring.line.getCoordinates());
            if(!ring.finishPossible)continue;
            double at=ring.indexed.project(contact);
            if(ring.indexed.extractPoint(at).distance(contact)>tolerance)continue;
            var lap=lap(ring,at,contact,true);
            if(!safe(lap))continue;
            append(current,lap);ring.used=true;
        }
    }
    private List<Ring> near(Coordinate point){var query=new Envelope(point);query.expandBy(tolerance);return index.query(query);}
    private Coordinate[] link(Coordinate from,Coordinate to){
        token.throwIfCancellationRequested();if(from.equals2D(to))return new Coordinate[]{from};
        if(from.distance(to)>=limit)return null;
        var straight=new Coordinate[]{from,to};if(safe(straight))return straight;
        Coordinate[] best=null;double bestLength=limit;
        for(var ring:near(from)){
            token.throwIfCancellationRequested();double a=ring.indexed.project(from),b=ring.indexed.project(to);
            if(ring.indexed.extractPoint(a).distance(from)>tolerance||ring.indexed.extractPoint(b).distance(to)>tolerance)continue;
            for(boolean forward:List.of(true,false)){
                double length=forward?(b-a+ring.length)%ring.length:(a-b+ring.length)%ring.length;
                if(!(length<bestLength))continue;
                var route=walk(ring,a,b,forward);var points=new ArrayList<Coordinate>();points.add(from);append(points,route);append(points,new Coordinate[]{to});
                var candidate=points.toArray(Coordinate[]::new);
                if(safe(candidate)){best=candidate;bestLength=length;}
            }
        }
        return best;
    }
    private Coordinate[] lap(Ring ring,double at,Coordinate contact,boolean touch){
        var points=new ArrayList<Coordinate>();if(touch)points.add(contact);
        append(points,ring.indexed.extractLine(at,ring.length).getCoordinates());append(points,ring.indexed.extractLine(0,at).getCoordinates());
        if(touch)append(points,new Coordinate[]{contact});return points.toArray(Coordinate[]::new);
    }
    private Coordinate[] walk(Ring ring,double from,double to,boolean forward){
        var points=new ArrayList<Coordinate>();double a=forward?from:to,b=forward?to:from;
        if(b>=a)append(points,ring.indexed.extractLine(a,b).getCoordinates());
        else {append(points,ring.indexed.extractLine(a,ring.length).getCoordinates());append(points,ring.indexed.extractLine(0,b).getCoordinates());}
        if(!forward)Collections.reverse(points);return points.toArray(Coordinate[]::new);
    }
    private boolean safe(Coordinate[] path){
        token.throwIfCancellationRequested();Geometry g=path.length==1?factory.createPoint(path[0]):factory.createLineString(path);
        // Exact clearance against the polygonal boundary: curved-buffer simplification can otherwise
        // falsely reject a safe walk. No radius reduction, chord allowance or enlarged allowed area.
        boolean safe=allowed.contains(g)&&clearance.distance(g)>=diameter/2;token.throwIfCancellationRequested();return safe;
    }
    private static void append(List<Coordinate> target,Coordinate[] points){for(var p:points)if(target.isEmpty()||!target.getLast().equals2D(p))target.add(p);}
    private void flush(List<Coordinate> current,List<LineString> output){if(current.size()>1)output.add(factory.createLineString(current.toArray(Coordinate[]::new)));current.clear();}
}
