package org.flatcam.cam.gcode;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

/** Ordering only: never joins cuts, changes coordinates or changes the source geometry. */
public record CncRouting(Mode mode, boolean preserveDirection, boolean innerFirst,
                         List<String> boardAreas, double boardMargin) {
    public enum Mode { SOURCE, NEAREST, BOARDS }
    public static final CncRouting LEGACY = new CncRouting(Mode.NEAREST,false,false,List.of(),0);
    public CncRouting {
        Objects.requireNonNull(mode); boardAreas=List.copyOf(boardAreas);
        if (!Double.isFinite(boardMargin) || boardMargin<0) throw new IllegalArgumentException("Margem de agrupamento deve ser finita e não negativa.");
        if (boardAreas.size()>1000) throw new IllegalArgumentException("Máximo de 1000 placas na referência.");
        if (mode==Mode.BOARDS && boardAreas.isEmpty()) throw new IllegalArgumentException("Escolha uma Geometry de área das placas para agrupar.");
        for (String wkt:boardAreas) board(wkt);
    }
    private static Polygon board(String wkt) {
        try {
            Geometry g=new WKTReader().read(wkt);
            if (!(g instanceof Polygon p) || p.isEmpty() || !p.isValid()) throw new IllegalArgumentException("Referência deve conter polígonos válidos de placa.");
            for (Coordinate c:p.getCoordinates()) if (!Double.isFinite(c.x) || !Double.isFinite(c.y)) throw new IllegalArgumentException("Referência não finita.");
            return p;
        } catch (org.locationtech.jts.io.ParseException invalid) { throw new IllegalArgumentException("Referência de placa inválida.",invalid); }
    }
    static List<Coordinate[]> order(Geometry geometry, double x, double y, double toolRadius,
                                    CncRouting options, CancellationToken cancellation) {
        List<Coordinate[]> remaining=new ArrayList<>(); collect(geometry,remaining,cancellation);
        if (options.equals(LEGACY)) return GCodeGenerator.orderedByNearestNeighbor(geometry,x,y,cancellation);
        if (options.mode()==Mode.SOURCE) return remaining;
        var factory=geometry.getFactory();
        List<Envelope> boxes=options.boardAreas().stream().map(CncRouting::board).map(p -> {
            Envelope e=new Envelope(p.getEnvelopeInternal()); e.expandBy(options.boardMargin()+toolRadius); return e;
        }).toList();
        Map<Coordinate[],Integer> groups=new IdentityHashMap<>();
        Map<Coordinate[],Polygon> rings=new IdentityHashMap<>();
        for (Coordinate[] path:remaining) {
            cancellation.throwIfCancellationRequested();
            if (options.mode()==Mode.BOARDS) {
                Envelope bounds=new Envelope(); for (Coordinate c:path) bounds.expandToInclude(c);
                int group=-1;
                for (int i=0;i<boxes.size();i++) if (boxes.get(i).covers(bounds)) {
                    if (group>=0) throw new IllegalArgumentException("Trajeto pertence a duas caixas de placa. Reduza a margem ou corrija a referência.");
                    group=i;
                }
                if (group<0) throw new IllegalArgumentException("Trajeto fora das caixas da referência. Confira origem, unidades e margem de agrupamento.");
                groups.put(path,group);
            }
            if (closed(path)) {
                Polygon ring=factory.createPolygon(path);
                if (ring.isValid() && ring.getArea()>0) rings.put(path,ring);
            }
        }
        List<Coordinate[]> output=new ArrayList<>(); int active=-1;
        while (!remaining.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            if (active>=0) { final int current=active; if (remaining.stream().noneMatch(p -> groups.get(p)==current)) active=-1; }
            Coordinate[] best=null; int entry=0; boolean reverse=false; double distance=Double.POSITIVE_INFINITY;
            for (Coordinate[] path:remaining) {
                cancellation.throwIfCancellationRequested();
                if (active>=0 && groups.get(path)!=active) continue;
                if (options.innerFirst() && blocked(path,remaining,rings,groups,options.mode()==Mode.BOARDS)) continue;
                int count=closed(path) ? path.length-1 : options.preserveDirection() ? 1 : path.length==1 ? 1 : 2;
                for (int i=0;i<count;i++) {
                    int index=closed(path) ? i : i==0 ? 0 : path.length-1;
                    double d=Math.hypot(path[index].x-x,path[index].y-y);
                    if (d<distance) { distance=d; best=path; entry=index; reverse=!closed(path)&&index>0; }
                }
            }
            if (best==null) throw new IllegalArgumentException("Não foi possível ordenar os contornos da referência.");
            remaining.remove(best);
            if (options.mode()==Mode.BOARDS) active=groups.get(best);
            Coordinate[] ordered=best;
            if (closed(best)) {
                int n=best.length-1; ordered=new Coordinate[best.length];
                for(int i=0;i<n;i++) ordered[i]=new Coordinate(best[(entry+i)%n]); ordered[n]=new Coordinate(ordered[0]);
            } else if (reverse) { ordered=new Coordinate[best.length]; for(int i=0;i<best.length;i++) ordered[i]=new Coordinate(best[best.length-1-i]); }
            output.add(ordered); Coordinate end=ordered[ordered.length-1]; x=end.x; y=end.y;
        }
        return output;
    }
    private static boolean blocked(Coordinate[] path,List<Coordinate[]> remaining,Map<Coordinate[],Polygon> rings,
                                   Map<Coordinate[],Integer> groups,boolean grouped) {
        Polygon outer=rings.get(path);
        for (Coordinate[] other:remaining) {
            if (other==path || grouped && !Objects.equals(groups.get(path),groups.get(other))) continue;
            Polygon inner=rings.get(other);
            // Closed internal profiles precede open bridge segments; nested closed profiles precede their parent.
            if (outer==null && inner!=null) return true;
            if (outer!=null && inner!=null && outer.getArea()>inner.getArea() && outer.covers(inner)) return true;
        }
        return false;
    }
    private static boolean closed(Coordinate[] p) { return p.length>=4 && p[0].equals2D(p[p.length-1]); }
    private static void collect(Geometry geometry,List<Coordinate[]> paths,CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested(); if (geometry==null || geometry.isEmpty()) return;
        if (geometry instanceof Polygon p) {
            paths.add(p.getExteriorRing().getCoordinates());
            for(int i=0;i<p.getNumInteriorRing();i++) paths.add(p.getInteriorRingN(i).getCoordinates());
        } else if (geometry instanceof LineString || geometry instanceof Point) paths.add(geometry.getCoordinates());
        else for(int i=0;i<geometry.getNumGeometries();i++) collect(geometry.getGeometryN(i),paths,cancellation);
    }
}
