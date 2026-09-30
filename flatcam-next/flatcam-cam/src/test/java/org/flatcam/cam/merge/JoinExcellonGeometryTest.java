package org.flatcam.cam.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class JoinExcellonGeometryTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static ExcellonImage excellon(String units, Map<Integer, Double> tools, List<ExcellonImage.Drill> drills) {
        Geometry solid = FACTORY.buildGeometry(drills.stream()
                .map(d -> (Geometry) FACTORY.createPoint(new Coordinate(d.x(), d.y())).buffer(tools.get(d.toolId()) / 2))
                .toList());
        return ExcellonImage.of(units, tools, drills, List.of(), solid);
    }

    @Test
    void excellonJoinFusesToolsOfTheSameDiameterAndRenumbersDrills() {
        ExcellonImage first = excellon("MM", Map.of(1, 0.8, 2, 1.0),
                List.of(new ExcellonImage.Drill(1, 0, 0), new ExcellonImage.Drill(2, 5, 0)));
        ExcellonImage second = excellon("MM", Map.of(1, 1.0, 2, 3.2),
                List.of(new ExcellonImage.Drill(1, 9, 0), new ExcellonImage.Drill(2, 12, 0)));
        ExcellonImage joined = ExcellonJoin.join(List.of(first, second), true);
        assertEquals(3, joined.toolDiameters().size());
        assertEquals(4, joined.totalDrills());
        // 1.0 mm exists once; the second file's drill on its tool 1 (1.0 mm) lands on the first file's tool 2.
        assertEquals(2, joined.drills().get(2).toolId());
        assertEquals(3.2, joined.toolDiameters().get(3), 1e-9);
        ExcellonImage separate = ExcellonJoin.join(List.of(first, second), false);
        assertEquals(4, separate.toolDiameters().size());
    }

    @Test
    void excellonJoinNeedsTwoObjectsWithTheSameUnits() {
        ExcellonImage mm = excellon("MM", Map.of(1, 0.8), List.of(new ExcellonImage.Drill(1, 0, 0)));
        ExcellonImage in = excellon("IN", Map.of(1, 0.03), List.of(new ExcellonImage.Drill(1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ExcellonJoin.join(List.of(mm), true));
        assertThrows(IllegalArgumentException.class, () -> ExcellonJoin.join(List.of(mm, in), true));
    }

    private static GeometryJoin.Source single(double x) {
        return new GeometryJoin.Source("MM", FACTORY.createLineString(
                new Coordinate[]{new Coordinate(x, 0), new Coordinate(x + 1, 0)}), true, List.of());
    }

    private static GeometryJoin.Source multi(double diameter, double x) {
        Geometry path = FACTORY.createLineString(new Coordinate[]{new Coordinate(x, 0), new Coordinate(x + 1, 0)});
        return new GeometryJoin.Source("MM", path, false, List.of(new ToolGeometry(diameter, path)));
    }

    @Test
    void geometryJoinCollectsTheShapesOfSingleObjects() {
        GeometryJoin.Joined joined = GeometryJoin.join(List.of(single(0), single(5)), true);
        assertEquals(2, joined.geometry().getNumGeometries());
        assertTrue(joined.tools().isEmpty());
        assertTrue(joined.strokeOnly());
    }

    @Test
    void geometryJoinFusesMultiToolGeometriesByDiameterAndProfile() {
        GeometryJoin.Joined joined = GeometryJoin.join(List.of(multi(0.2, 0), multi(0.2, 5), multi(1.0, 9)), true);
        assertEquals(2, joined.tools().size());
        assertEquals(2, joined.tools().get(0).geometry().getNumGeometries());
        assertEquals(3, joined.geometry().getNumGeometries());
        assertEquals(3, GeometryJoin.join(List.of(multi(0.2, 0), multi(0.2, 5), multi(1.0, 9)), false).tools().size());
    }

    @Test
    void geometryJoinRefusesToMixSingleAndMultiToolObjects() {
        assertThrows(IllegalArgumentException.class, () -> GeometryJoin.join(List.of(single(0), multi(0.2, 5)), true));
        assertThrows(IllegalArgumentException.class, () -> GeometryJoin.join(List.of(single(0)), true));
    }
}
