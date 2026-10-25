package org.flatcam.cam.geometry;

import java.util.List;
import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/** The three point orders used by Python FCArc. Coordinates use the object's units. */
public final class EditorArc {
    public static final int DEFAULT_STEPS = 64;
    public static final int MIN_STEPS = 4;
    public static final int MAX_STEPS = 4096;
    public enum Mode { CENTER_START_END, START_END_CENTER, START_END_THROUGH }
    public record Result(Coordinate center, double radius, boolean clockwise, LineString path) { }
    private static final double TURN = 2 * Math.PI;
    private EditorArc() { }

    public static Result build(GeometryFactory factory, Mode mode, boolean clockwise, List<Coordinate> points) {
        return build(factory, mode, clockwise, points, DEFAULT_STEPS);
    }

    public static void validateSteps(int steps) {
        if (steps < MIN_STEPS || steps > MAX_STEPS)
            throw new IllegalArgumentException("Resolução deve ser um inteiro entre 4 e 4096 segmentos por círculo.");
    }

    public static Result build(GeometryFactory factory, Mode mode, boolean clockwise, List<Coordinate> points, int steps) {
        validateSteps(steps);
        Objects.requireNonNull(factory); Objects.requireNonNull(mode);
        if (points.size() != 3) throw new IllegalArgumentException("O arco exige três pontos.");
        for (Coordinate p : points) if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y))
            throw new IllegalArgumentException("As coordenadas do arco devem ser finitas.");
        Coordinate a = points.get(0), b = points.get(1), c = points.get(2);
        Coordinate center, start, end;
        if (mode == Mode.CENTER_START_END) {
            center = new Coordinate(a); start = b; end = c;
            if (center.equals2D(end)) throw new IllegalArgumentException("O destino deve ser diferente do centro.");
        } else {
            start = a; end = b;
            double dx = b.x - a.x, dy = b.y - a.y, chord = Math.hypot(dx, dy);
            if (!(chord > 0) || !Double.isFinite(chord))
                throw new IllegalArgumentException("Início e fim do arco devem ser distintos.");
            if (mode == Mode.START_END_CENTER) {
                // Python uses the distance to the midpoint, along the perpendicular bisector.
                double mx = a.x + dx / 2, my = a.y + dy / 2;
                double distance = Math.hypot(c.x - mx, c.y - my);
                double side = Math.signum(((c.x - a.x) / chord) * (dy / chord)
                        - ((c.y - a.y) / chord) * (dx / chord));
                center = new Coordinate(mx + dy / chord * distance * side, my - dx / chord * distance * side);
            } else {
                // Translate before solving so an offset project doesn't lose precision by squaring its origin.
                double tx = c.x - a.x, ty = c.y - a.y;
                double scale = Math.max(chord, Math.hypot(tx, ty));
                double ux = dx / scale, uy = dy / scale, vx = tx / scale, vy = ty / scale;
                double cross = ux * vy - uy * vx;
                if (!Double.isFinite(cross) || Math.abs(cross) <= 1e-12)
                    throw new IllegalArgumentException("O ponto do arco deve ser distinto e não alinhado com início e fim.");
                double u2 = ux * ux + uy * uy, v2 = vx * vx + vy * vy;
                center = new Coordinate(a.x + scale * (u2 * vy - v2 * uy) / (2 * cross),
                        a.y + scale * (ux * v2 - vx * u2) / (2 * cross));
                clockwise = cross > 0;
            }
        }
        double radius = start.distance(center);
        if (!(radius > 0) || !Double.isFinite(radius) || !Double.isFinite(center.x) || !Double.isFinite(center.y))
            throw new IllegalArgumentException("Não foi possível calcular um raio válido para o arco.");
        double first = Math.atan2(start.y - center.y, start.x - center.x);
        double last = Math.atan2(end.y - center.y, end.x - center.x);
        double sweep = positive(clockwise ? first - last : last - first);
        if (sweep == 0) sweep = TURN;
        double requestedSegments = sweep / TURN * steps;
        // A half/quarter circle must not gain a segment because the center solver rounded an angle upward.
        double nearest = Math.rint(requestedSegments);
        if (Math.abs(requestedSegments - nearest) < 1e-12) requestedSegments = nearest;
        int segments = Math.max(2, (int) Math.ceil(requestedSegments));
        Coordinate[] path = new Coordinate[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double angle = first + (clockwise ? -1 : 1) * sweep * i / segments;
            path[i] = new Coordinate(center.x + radius * Math.cos(angle), center.y + radius * Math.sin(angle));
            if (!Double.isFinite(path[i].x) || !Double.isFinite(path[i].y))
                throw new IllegalArgumentException("Arco fora do intervalo de coordenadas válido.");
        }
        path[0] = new Coordinate(start);
        // Center/start/end uses the end click's angle, not its radius, just like Python.
        if (mode != Mode.CENTER_START_END) path[segments] = new Coordinate(end);
        else if (sweep == TURN) path[segments] = new Coordinate(start);
        return new Result(center, radius, clockwise, factory.createLineString(path));
    }

    private static double positive(double angle) { double p = angle % TURN; return p < 0 ? p + TURN : p; }
}
