package org.flatcam.cam.gcode;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.*;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.overlayng.OverlayNG;

/** Conservative visibility routing for XY rapids. Does not model machine macros or fixtures in 3D. */
public final class CncExclusionPlanner {
    public record Travel(List<Coordinate> points,double z) { public Travel { points=List.copyOf(points); } }
    /** One XY segment at an absolute Z. Change Z at from, before moving to to. */
    public record TravelLeg(Coordinate from, Coordinate to, double z) { }
    private record Obstacle(Geometry geometry,CncExclusionArea.Strategy strategy,double overZ) { }
    private final GeometryFactory factory=new GeometryFactory();
    private final List<Obstacle> obstacles;
    private final Geometry around;
    private final CancellationToken cancellation;

    public CncExclusionPlanner(String units,List<CncExclusionArea> areas,double diameter,CancellationToken cancellation) {
        if (!Double.isFinite(diameter)||diameter<=0) throw new IllegalArgumentException("Diametro invalido.");
        if (!"MM".equals(units)&&!"IN".equals(units)) throw new IllegalArgumentException("Unidades invalidas.");
        this.cancellation=Objects.requireNonNull(cancellation);
        double margin=diameter/2 + (.1/("IN".equals(units)?25.4:1));
        List<Obstacle> buffered=new ArrayList<>(); List<Geometry> aroundParts=new ArrayList<>();
        for (var area:areas) {
            cancellation.throwIfCancellationRequested();
            Geometry shape=BufferOp.bufferOp(area.geometry(),margin,new BufferParameters(16,BufferParameters.CAP_ROUND,BufferParameters.JOIN_MITRE,5));
            buffered.add(new Obstacle(shape,area.strategy(),area.overZ()));
            if (area.strategy()==CncExclusionArea.Strategy.AROUND) aroundParts.add(shape);
        }
        obstacles=List.copyOf(buffered);
        around=aroundParts.isEmpty()?factory.createPolygon():OverlayNGRobust.union(aroundParts);
    }
    public void validateCut(Geometry path) {
        cancellation.throwIfCancellationRequested();
        for (var obstacle:obstacles) if (path.getEnvelopeInternal().intersects(obstacle.geometry().getEnvelopeInternal()) && path.intersects(obstacle.geometry()))
            throw new IllegalArgumentException("Corte ou ferramenta atinge uma area de exclusao; regenere os caminhos ou ajuste a area. Nenhum G-code gerado.");
    }
    public Travel travel(Coordinate start,Coordinate end,double safeZ) {
        cancellation.throwIfCancellationRequested();
        for (var point:List.of(start,end)) for (var obstacle:obstacles)
            if (obstacle.geometry().covers(factory.createPoint(point)))
                throw new IllegalArgumentException("Origem/destino de deslocamento dentro de exclusao (inclui raio da ferramenta e margem). Ajuste origem, troca ou estacionamento.");
        List<Coordinate> points;
        if (visible(start,end)) points=List.of(new Coordinate(start),new Coordinate(end));
        else points=route(start,end);
        double z=safeZ;
        for (int i=1;i<points.size();i++) {
            cancellation.throwIfCancellationRequested();
            Geometry segment=factory.createLineString(new Coordinate[]{points.get(i-1),points.get(i)});
            for (var obstacle:obstacles) if (obstacle.strategy()==CncExclusionArea.Strategy.OVER && segment.intersects(obstacle.geometry()))
                z=Math.max(z,obstacle.overZ());
        }
        return new Travel(points,z);
    }

    /**
     * Around keeps the visibility route. Over raises/lowers outside the cutter's
     * footprint, at buffered entry/exit boundaries, never below safeZ. Overlapping
     * areas use their maximum height on each interval, regardless of list order.
     * travel() remains the conservative single-height API for existing callers.
     */
    public List<TravelLeg> travelLegs(Coordinate start, Coordinate end, double safeZ) {
        if (!Double.isFinite(safeZ) || safeZ < 0)
            throw new IllegalArgumentException("Altura de deslocamento invalida.");
        List<Coordinate> path = travel(start,end,safeZ).points();
        List<TravelLeg> result = new ArrayList<>();
        for (int i=1; i<path.size(); i++) {
            cancellation.throwIfCancellationRequested();
            var segment = new LineSegment(path.get(i-1),path.get(i));
            if (segment.getLength()==0) { result.add(new TravelLeg(segment.p0,segment.p1,safeZ)); continue; }
            Geometry line = factory.createLineString(new Coordinate[]{segment.p0,segment.p1});
            var breaks = new TreeSet<Double>(List.of(0.0,1.0));
            var crossing = new ArrayList<Obstacle>();
            for (var obstacle:obstacles) {
                cancellation.throwIfCancellationRequested();
                if (obstacle.strategy()!=CncExclusionArea.Strategy.OVER || obstacle.overZ()<=safeZ
                        || !line.getEnvelopeInternal().intersects(obstacle.geometry().getEnvelopeInternal())) continue;
                Geometry intersection = OverlayNGRobust.overlay(line,obstacle.geometry(),OverlayNG.INTERSECTION);
                if (intersection.isEmpty()) continue;
                crossing.add(obstacle);
                for (Coordinate point:intersection.getCoordinates()) breaks.add(segment.segmentFraction(point));
                if (breaks.size()>8192) throw new IllegalArgumentException("Exclusoes Over complexas demais; simplifique as areas.");
            }
            var stations = new ArrayList<>(breaks);
            TravelLeg pending = null;
            for (int k=1; k<stations.size(); k++) {
                cancellation.throwIfCancellationRequested();
                double a=stations.get(k-1), b=stations.get(k);
                if (a==b) continue;
                Geometry middle = factory.createPoint(segment.pointAlong(a+(b-a)/2));
                double z=safeZ;
                for (var obstacle:crossing) if (obstacle.geometry().covers(middle)) z=Math.max(z,obstacle.overZ());
                Coordinate destination=segment.pointAlong(b);
                if (pending!=null && Double.compare(pending.z(),z)==0)
                    pending=new TravelLeg(pending.from(),destination,z);
                else {
                    if (pending!=null) result.add(pending);
                    pending=new TravelLeg(segment.pointAlong(a),destination,z);
                }
            }
            if (pending!=null) result.add(pending);
        }
        return List.copyOf(result);
    }
    private boolean visible(Coordinate a,Coordinate b) {
        if (a.equals2D(b) || around.isEmpty()) return true;
        Geometry segment=factory.createLineString(new Coordinate[]{a,b});
        // Boundary travel is allowed: buffering already includes cutter radius + safety margin.
        return !segment.getEnvelopeInternal().intersects(around.getEnvelopeInternal())
                || segment.relate(around).get(Location.INTERIOR,Location.INTERIOR)==Dimension.FALSE;
    }
    private List<Coordinate> route(Coordinate start,Coordinate end) {
        List<Coordinate> nodes=new ArrayList<>(List.of(new Coordinate(start),new Coordinate(end)));
        collectVertices(around,nodes);
        if (nodes.size()>512) throw new IllegalArgumentException("Exclusoes complexas demais para desvio; simplifique as areas (maximo 510 vertices combinados).");
        int n=nodes.size(); double[] distance=new double[n]; Arrays.fill(distance,Double.POSITIVE_INFINITY);
        int[] previous=new int[n]; Arrays.fill(previous,-1); boolean[] visited=new boolean[n]; distance[0]=0;
        for (int step=0;step<n;step++) {
            cancellation.throwIfCancellationRequested();
            int current=-1;
            for(int i=0;i<n;i++) if(!visited[i]&&(current<0||distance[i]<distance[current])) current=i;
            if(current<0||!Double.isFinite(distance[current])) break;
            if(current==1) break;
            visited[current]=true;
            for(int next=0;next<n;next++) if(!visited[next]) {
                cancellation.throwIfCancellationRequested();
                double cost=distance[current]+nodes.get(current).distance(nodes.get(next));
                if(cost<distance[next] && visible(nodes.get(current),nodes.get(next))) { distance[next]=cost; previous[next]=current; }
            }
        }
        if(!Double.isFinite(distance[1])) throw new IllegalArgumentException("Nao existe desvio seguro entre origem e destino.");
        List<Coordinate> path=new ArrayList<>();
        for(int index=1;index>=0;index=previous[index]) path.add(nodes.get(index));
        Collections.reverse(path); return List.copyOf(path);
    }
    private static void collectVertices(Geometry geometry,List<Coordinate> nodes) {
        if(geometry instanceof Polygon polygon) {
            appendRing(polygon.getExteriorRing(),nodes);
            for(int i=0;i<polygon.getNumInteriorRing();i++) appendRing(polygon.getInteriorRingN(i),nodes);
        } else if(geometry instanceof GeometryCollection) for(int i=0;i<geometry.getNumGeometries();i++) collectVertices(geometry.getGeometryN(i),nodes);
    }
    private static void appendRing(LineString ring,List<Coordinate> nodes) {
        for(int i=0;i<ring.getNumPoints()-1;i++) nodes.add(new Coordinate(ring.getCoordinateN(i)));
    }
}
