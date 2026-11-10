package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.distance.IndexedFacetDistance;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Integrates raster and requested finishing edges. Only existing boundary edges may be retraced.
 * Short boundary walks pair odd vertices; remaining pairs are virtual lifts, never cutting shortcuts.
 * Region ordering supplies traversal priorities rather than postponing all finishing until the end. */
final class RegionContourConnector {
    private record Key(double x,double y) { Key(Coordinate p){this(p.x==0?0:p.x,p.y==0?0:p.y);} }
    private static final class Vertex {
        final Coordinate point;final List<Edge> edges=new ArrayList<>();int cursor;
        Vertex(Coordinate point){this.point=point;}
    }
    private static final class Edge {
        final Vertex a,b;final Coordinate[] points;final int rank;final boolean boundary,extra,travel;final double length;boolean used;
        Edge(Vertex a,Vertex b,Coordinate[] points,int rank,boolean boundary,boolean extra,boolean travel,double length){
            this.a=a;this.b=b;this.points=points;this.rank=rank;this.boundary=boundary;this.extra=extra;this.travel=travel;this.length=length;
            a.edges.add(this);b.edges.add(this);
        }
        Vertex other(Vertex v){return v==a?b:a;}
    }
    private record Source(int rank,LineString path) { }
    private record Hop(Vertex vertex,double distance) { }
    private record Visit(Vertex vertex,Edge incoming,boolean forward) { }
    private record Directed(Edge edge,boolean forward) { }

    static List<LineString> connect(List<LineString> paths,List<LineString> priorities,Geometry safe,double diameter,CancellationToken token) {
        if(paths.size()<2)return paths;
        GeometryFactory factory=safe.getFactory();Geometry boundary=safe.getBoundary();
        var boundaryIndex=new IndexedFacetDistance(boundary);var sourceIndex=new STRtree();
        for(int i=0;i<priorities.size();i++)sourceIndex.insert(priorities.get(i).getEnvelopeInternal(),new Source(i,priorities.get(i)));
        sourceIndex.build();
        double magnitude=0;var bounds=safe.getEnvelopeInternal();
        for(double d:new double[]{bounds.getMinX(),bounds.getMaxX(),bounds.getMinY(),bounds.getMaxY()})magnitude=Math.max(magnitude,Math.abs(d));
        double tolerance=Math.max(diameter*1e-9,Math.ulp(magnitude)*64);
        token.throwIfCancellationRequested();Geometry noded=OverlayNGRobust.union(new ArrayList<Geometry>(paths));token.throwIfCancellationRequested();
        var vertices=new LinkedHashMap<Key,Vertex>();var lines=new ArrayList<LineString>();collect(noded,lines);
        for(var line:lines) {
            token.throwIfCancellationRequested();Coordinate[] points=line.getCoordinates();if(line.getLength()==0)continue;
            // A previously joined path may alternate raster/connector/finishing. Split at those
            // transitions so a boundary label can never authorize retracing an entire raster span.
            int begin=0;Boolean border=null;
            for(int i=1;i<points.length;i++) {
                token.throwIfCancellationRequested();if(points[i-1].equals2D(points[i]))continue;
                var midpoint=factory.createPoint(new Coordinate((points[i-1].x+points[i].x)/2,(points[i-1].y+points[i].y)/2));
                boolean current=boundaryIndex.distance(midpoint)<=tolerance;
                if(border!=null&&border!=current){addRun(points,begin,i-1,border,vertices,sourceIndex,factory,tolerance);begin=i-1;}
                border=current;
            }
            if(border!=null)addRun(points,begin,points.length-1,border,vertices,sourceIndex,factory,tolerance);
        }
        var pending=new LinkedHashSet<>(vertices.values());var output=new ArrayList<LineString>();
        while(!pending.isEmpty()) {
            token.throwIfCancellationRequested();var root=pending.iterator().next();var component=new LinkedHashSet<Vertex>();var queue=new ArrayDeque<Vertex>();queue.add(root);component.add(root);
            while(!queue.isEmpty()){token.throwIfCancellationRequested();var v=queue.remove();for(var edge:v.edges)if(component.add(edge.other(v)))queue.add(edge.other(v));}
            pending.removeAll(component);
            var odd=new LinkedHashSet<Vertex>();for(var v:component)if((v.edges.size()&1)==1)odd.add(v);
            pairShortBoundaryWalks(odd,10*diameter,token);
            while(!odd.isEmpty()) {
                token.throwIfCancellationRequested();var a=odd.iterator().next();odd.remove(a);Vertex nearest=null;double distance=Double.POSITIVE_INFINITY;
                for(var b:odd){double d=a.point.distance(b.point);if(d<distance){distance=d;nearest=b;}}
                odd.remove(nearest);new Edge(a,nearest,null,Integer.MAX_VALUE,false,false,true,0);
            }
            for(var v:component)v.edges.sort(Comparator.comparingInt((Edge e)->e.rank).thenComparing(e->e.travel).thenComparing(e->e.extra));
            var stack=new ArrayDeque<Visit>();var circuit=new ArrayList<Directed>();stack.push(new Visit(root,null,true));
            while(!stack.isEmpty()) {
                token.throwIfCancellationRequested();var top=stack.peek();var v=top.vertex();
                while(v.cursor<v.edges.size()&&v.edges.get(v.cursor).used)v.cursor++;
                if(v.cursor==v.edges.size()){stack.pop();if(top.incoming()!=null)circuit.add(new Directed(top.incoming(),top.forward()));}
                else {var edge=v.edges.get(v.cursor++);edge.used=true;stack.push(new Visit(edge.other(v),edge,v==edge.a));}
            }
            Collections.reverse(circuit);int start=0;
            for(int i=0;i<circuit.size();i++)if(circuit.get(i).edge().travel){start=(i+1)%circuit.size();break;}
            var current=new ArrayList<Coordinate>();
            for(int i=0;i<circuit.size();i++) {
                var move=circuit.get((start+i)%circuit.size());var e=move.edge();
                if(e.travel){flush(current,output,factory);continue;}
                for(int j=0;j<e.points.length;j++) {
                    var p=e.points[move.forward()?j:e.points.length-1-j];
                    if(current.isEmpty()||!current.getLast().equals2D(p))current.add(p);
                }
            }
            flush(current,output,factory);
        }
        return output;
    }
    private static void addRun(Coordinate[] all,int from,int to,boolean border,Map<Key,Vertex> vertices,STRtree sourceIndex,GeometryFactory factory,double tolerance) {
        if(from==to)return;var points=Arrays.copyOfRange(all,from,to+1);
        var a=vertices.computeIfAbsent(new Key(points[0]),key->new Vertex(points[0]));
        var b=vertices.computeIfAbsent(new Key(points[points.length-1]),key->new Vertex(points[points.length-1]));
        var midpoint=factory.createPoint(new Coordinate((points[0].x+points[1].x)/2,(points[0].y+points[1].y)/2));int rank=Integer.MAX_VALUE-1;
        var query=new Envelope(midpoint.getCoordinate());query.expandBy(tolerance);
        for(Object entry:sourceIndex.query(query)){var original=(Source)entry;if(original.path().distance(midpoint)<=tolerance)rank=Math.min(rank,original.rank());}
        new Edge(a,b,points,rank,border,false,false,factory.createLineString(points).getLength());
    }
    private static void pairShortBoundaryWalks(Set<Vertex> odd,double limit,CancellationToken token) {
        for(var start:new ArrayList<>(odd)) {
            if(!odd.contains(start))continue;token.throwIfCancellationRequested();
            var best=new HashMap<Vertex,Double>();var previous=new HashMap<Vertex,Edge>();
            var queue=new PriorityQueue<Hop>(Comparator.comparingDouble(Hop::distance));best.put(start,0.);queue.add(new Hop(start,0));Vertex target=null;
            while(!queue.isEmpty()) {
                token.throwIfCancellationRequested();var hop=queue.remove();if(hop.distance()!=best.get(hop.vertex()))continue;
                if(hop.vertex()!=start&&odd.contains(hop.vertex())){target=hop.vertex();break;}
                for(var edge:hop.vertex().edges)if(edge.boundary&&!edge.extra&&!edge.travel) {
                    var next=edge.other(hop.vertex());double distance=hop.distance()+edge.length;
                    if(distance<limit&&distance<best.getOrDefault(next,Double.POSITIVE_INFINITY)){best.put(next,distance);previous.put(next,edge);queue.add(new Hop(next,distance));}
                }
            }
            if(target!=null) {
                odd.remove(start);odd.remove(target);var v=target;
                while(v!=start){var edge=previous.get(v);new Edge(edge.a,edge.b,edge.points,edge.rank,true,true,false,edge.length);v=edge.other(v);}
            }
        }
    }
    private static void collect(Geometry g,List<LineString> lines){if(g instanceof LineString l)lines.add(l);else if(g instanceof GeometryCollection)for(int i=0;i<g.getNumGeometries();i++)collect(g.getGeometryN(i),lines);}
    private static void flush(List<Coordinate> coordinates,List<LineString> output,GeometryFactory factory){if(coordinates.size()>1)output.add(factory.createLineString(coordinates.toArray(Coordinate[]::new)));coordinates.clear();}
    private RegionContourConnector() { }
}
