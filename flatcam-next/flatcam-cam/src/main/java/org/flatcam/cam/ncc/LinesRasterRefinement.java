package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.linearref.LengthIndexedLine;

/** Refines raster passes only. Finishing rings and intentional stepover remain unchanged. */
final class LinesRasterRefinement {
    private record Segment(Coordinate a,Coordinate b) { }
    private record Span(double from,double to) { }
    private LinesRasterRefinement() { }

    /** Components with no positive-length pass; touching an endpoint is not machining a region. */
    static List<Polygon> unvisitedComponents(Geometry safeArea,List<LineString> paths,CancellationToken cancellation) {
        var index=new STRtree();
        for(var path:paths)if(path.getLength()>0)index.insert(path.getEnvelopeInternal(),path);
        index.build();var missed=new ArrayList<Polygon>();
        for(var component:polygons(safeArea)) {
            cancellation.throwIfCancellationRequested();boolean visited=false;
            for(Object candidate:index.query(component.getEnvelopeInternal())) {
                cancellation.throwIfCancellationRequested();
                if(component.intersection((LineString)candidate).getLength()>0){visited=true;break;}
            }
            if(!visited)missed.add(component);
        }
        return missed;
    }

    static List<Polygon> polygons(Geometry geometry) {
        var result=new ArrayList<Polygon>();collectPolygons(geometry,result);return result;
    }
    private static void collectPolygons(Geometry geometry,List<Polygon> result) {
        if(geometry instanceof Polygon polygon) { if(!polygon.isEmpty())result.add(polygon); }
        else if(geometry instanceof GeometryCollection)
            for(int i=0;i<geometry.getNumGeometries();i++)collectPolygons(geometry.getGeometryN(i),result);
    }

    /** Keep the requested scan direction, including inside a disconnected, sub-stepover island. */
    static void supplement(List<LineString> paths,List<Polygon> missed,boolean horizontal,CancellationToken cancellation) {
        for(var component:missed) {
            cancellation.throwIfCancellationRequested();var p=component.getInteriorPoint().getCoordinate();
            var e=component.getEnvelopeInternal();var line=component.getFactory().createLineString(horizontal
                    ? new Coordinate[]{new Coordinate(e.getMinX(),p.y),new Coordinate(e.getMaxX(),p.y)}
                    : new Coordinate[]{new Coordinate(p.x,e.getMaxY()),new Coordinate(p.x,e.getMinY())});
            collectLines(component.intersection(line),paths);
        }
    }
    private static void collectLines(Geometry geometry,List<LineString> result) {
        if(geometry instanceof LineString line){if(line.getLength()>0)result.add(line);}
        else if(geometry instanceof GeometryCollection)
            for(int i=0;i<geometry.getNumGeometries();i++)collectLines(geometry.getGeometryN(i),result);
    }

    static List<LineString> withoutContourOverlap(List<LineString> paths,Geometry boundary,CancellationToken cancellation) {
        var index=new STRtree();
        indexSegments(boundary,index,cancellation);index.build();
        var intersector=new RobustLineIntersector();var output=new ArrayList<LineString>();
        for(var path:paths) {
            cancellation.throwIfCancellationRequested();var spans=new ArrayList<Span>();
            var coordinates=path.getCoordinates();double offset=0;
            for(int i=1;i<coordinates.length;i++) {
                var a=coordinates[i-1];var b=coordinates[i];double length=a.distance(b);
                for(Object value:index.query(new Envelope(a,b))) {
                    cancellation.throwIfCancellationRequested();var segment=(Segment)value;
                    intersector.computeIntersection(a,b,segment.a(),segment.b());
                    // Two distinct intersections mean a positive collinear overlap, not a shared endpoint.
                    if(intersector.getIntersectionNum()==2) {
                        double first=a.distance(intersector.getIntersection(0)),last=a.distance(intersector.getIntersection(1));
                        double from=offset+Math.min(first,last),to=offset+Math.max(first,last);
                        if(to>from)spans.add(new Span(from,to));
                    }
                }
                offset+=length;
            }
            if(spans.isEmpty()){output.add(path);continue;}
            spans.sort(Comparator.comparingDouble(Span::from));var indexed=new LengthIndexedLine(path);double cursor=0;
            for(var span:spans) {
                if(span.from()>cursor)addInterval(indexed,cursor,Math.min(span.from(),offset),output);
                cursor=Math.max(cursor,span.to());
            }
            if(cursor<offset)addInterval(indexed,cursor,offset,output);
        }
        return output;
    }
    private static void addInterval(LengthIndexedLine indexed,double from,double to,List<LineString> output) {
        if(to>from && indexed.extractLine(from,to) instanceof LineString line && line.getLength()>0)output.add(line);
    }
    private static void indexSegments(Geometry geometry,STRtree index,CancellationToken cancellation) {
        if(geometry instanceof LineString line) {
            var c=line.getCoordinates();for(int i=1;i<c.length;i++) {
                cancellation.throwIfCancellationRequested();if(!c[i-1].equals2D(c[i]))index.insert(new Envelope(c[i-1],c[i]),new Segment(c[i-1],c[i]));
            }
        } else if(geometry instanceof GeometryCollection)for(int i=0;i<geometry.getNumGeometries();i++)indexSegments(geometry.getGeometryN(i),index,cancellation);
    }
}
