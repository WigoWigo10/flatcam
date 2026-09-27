package org.flatcam.cam.excellon;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

/** Centre-line Geometry for milling Excellon holes or slots with a smaller end mill. */
public final class ExcellonMillingGenerator {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final double MIN_RADIUS = 1e-7;

    public enum Kind { DRILLS, SLOTS }

    private ExcellonMillingGenerator() { }

    public static Geometry generate(ExcellonImage image, Set<Integer> toolIds, double millDiameter,
                                    Kind kind, CancellationToken cancellation) {
        if (image == null || toolIds == null || toolIds.isEmpty() || kind == null || cancellation == null) {
            throw new IllegalArgumentException("Selecione uma origem, ferramentas e o tipo de fresagem.");
        }
        if (!Double.isFinite(millDiameter) || millDiameter <= 0) {
            throw new IllegalArgumentException("O diametro da fresa deve ser positivo.");
        }
        for (int id : toolIds) {
            Double holeDiameter = image.toolDiameters().get(id);
            if (holeDiameter == null) throw new IllegalArgumentException("Ferramenta Excellon T" + id + " inexistente.");
            if (!Double.isFinite(holeDiameter) || holeDiameter <= 0 || millDiameter > holeDiameter + 1e-9) {
                throw new IllegalArgumentException("A fresa e maior que o furo/slot da ferramenta T" + id + ".");
            }
        }

        List<LineString> paths = new ArrayList<>();
        if (kind == Kind.DRILLS) {
            for (ExcellonImage.Drill drill : image.drills()) {
                cancellation.throwIfCancellationRequested();
                if (!toolIds.contains(drill.toolId())) continue;
                double radius = Math.max(MIN_RADIUS,
                        (image.toolDiameters().get(drill.toolId()) - millDiameter) / 2);
                Point centre = FACTORY.createPoint(new Coordinate(drill.x(), drill.y()));
                paths.add((LineString) centre.buffer(radius, 32).getBoundary());
            }
        } else {
            for (ExcellonImage.Slot slot : image.slots()) {
                cancellation.throwIfCancellationRequested();
                if (!toolIds.contains(slot.toolId())) continue;
                double radius = Math.max(MIN_RADIUS,
                        (image.toolDiameters().get(slot.toolId()) - millDiameter) / 2);
                LineString centre = FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(slot.x1(), slot.y1()), new Coordinate(slot.x2(), slot.y2())});
                paths.add((LineString) centre.buffer(radius, 32).getBoundary());
            }
        }
        cancellation.throwIfCancellationRequested();
        return FACTORY.createMultiLineString(paths.toArray(LineString[]::new));
    }
}
