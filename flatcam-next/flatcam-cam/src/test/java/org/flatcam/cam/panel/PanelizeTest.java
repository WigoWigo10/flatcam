package org.flatcam.cam.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.merge.GeometryJoin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class PanelizeTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void gerberPanelPreservesTranslatedMembersUntilCamInsteadOfEagerlyUnioningThem() {
        for (double scale : List.of(1.,1/25.4)) {
            Geometry solid = FACTORY.createGeometryCollection(new Geometry[]{
                    FACTORY.createMultiPolygon(new org.locationtech.jts.geom.Polygon[]{
                            (org.locationtech.jts.geom.Polygon)FACTORY.toGeometry(new Envelope(0,2*scale,0,3*scale)),
                            (org.locationtech.jts.geom.Polygon)FACTORY.toGeometry(new Envelope(4*scale,6*scale,0,3*scale))})});
            String original = solid.toText();
            var source = GerberImage.of(scale==1?"MM":"IN",Map.of(),solid,solid.getGeometryN(0).getBoundary(),Map.of());
            var layout = Panelize.layout(new double[]{0,0,6*scale,3*scale},2,2,scale,scale,Double.NaN,Double.NaN);
            var panel = Panelize.gerber(source,layout);
            assertEquals("GeometryCollection",panel.solidGeometry().getGeometryType());
            assertEquals(4,panel.solidGeometry().getNumGeometries());
            for (int i=0;i<4;i++) {
                double[] cell=layout.offsets().get(i);
                Geometry expected=new org.flatcam.cam.transform.TransformOp.Offset(cell[0],cell[1]).apply(solid);
                assertTrue(expected.equalsExact(panel.solidGeometry().getGeometryN(i)),
                        "Part order and ring endpoints must survive until NCC prepares the copper");
            }
            assertEquals(original,solid.toText());
            // Joining objects is still a resolved polygon union, not a panel list.
            assertTrue(org.flatcam.cam.merge.GerberJoin.join(List.of(source,source)).solidGeometry()
                    instanceof org.locationtech.jts.geom.Polygonal);
        }
    }

    @TempDir
    Path directory;

    @Test
    void layoutStepsByTheBoxPlusTheSpacingAndListsCellsRowByRow() {
        Panelize.Layout layout = Panelize.layout(new double[]{0, 0, 60, 40}, 3, 2, 5, 10, Double.NaN, Double.NaN);
        assertEquals(65, layout.stepX(), 1e-9);
        assertEquals(50, layout.stepY(), 1e-9);
        List<double[]> cells = layout.offsets();
        assertEquals(6, cells.size());
        assertEquals(130, cells.get(2)[0], 1e-9);
        assertEquals(50, cells.get(3)[1], 1e-9);
        assertEquals(0, cells.get(3)[0], 1e-9);
        assertEquals(60 * 3 + 5 * 2, layout.size(60, 40, 5, 10)[0], 1e-9);
    }

    @Test
    void theSizeLimitReducesTheGridLikePython() {
        Panelize.Layout layout = Panelize.layout(new double[]{0, 0, 60, 40}, 5, 5, 0, 0, 200, 100);
        assertEquals(3, layout.columns());
        assertEquals(2, layout.rows());
        assertTrue(layout.constrained());
        assertThrows(IllegalArgumentException.class,
                () -> Panelize.layout(new double[]{0, 0, 60, 40}, 2, 2, 0, 0, 50, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> Panelize.layout(new double[]{0, 0, 60, 40}, 0, 2, 0, 0, Double.NaN, Double.NaN));
    }

    @Test
    void aGerberPanelHoldsEveryCopyOfTheCopper() throws IOException {
        Path file = directory.resolve("a.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1.0*%",
                "D10*", "X10000Y10000D03*", "X50000Y10000D03*", "M02*", ""));
        GerberImage source = new GerberParser().parse(file);
        Panelize.Layout layout = Panelize.layout(source.bounds(), 3, 2, 2, 2, Double.NaN, Double.NaN);
        GerberImage panel = Panelize.gerber(source, layout);
        assertEquals(6 * source.solidGeometry().getArea(), panel.solidGeometry().getArea(), 1e-6);
        assertEquals(6 * source.shapes().size(), panel.shapes().size());
        assertEquals(source.apertures().size(), panel.apertures().size());
        Envelope box = panel.solidGeometry().getEnvelopeInternal();
        assertEquals(source.bounds()[0], box.getMinX(), 1e-9);
        assertEquals(source.bounds()[2] + 2 * layout.stepX(), box.getMaxX(), 1e-9);
    }

    @Test
    void anExcellonPanelKeepsTheToolsAndMultipliesTheDrills() {
        ExcellonImage source = ExcellonImage.of("MM", Map.of(1, 0.8),
                List.of(new ExcellonImage.Drill(1, 1, 1), new ExcellonImage.Drill(1, 5, 1)), List.of(),
                FACTORY.createPoint(new Coordinate(1, 1)).buffer(0.4));
        ExcellonImage panel = Panelize.excellon(source, Panelize.layout(new double[]{0, 0, 10, 10}, 2, 2, 0, 0,
                Double.NaN, Double.NaN));
        assertEquals(8, panel.totalDrills());
        assertEquals(1, panel.toolDiameters().size());
        assertEquals(11, panel.drills().get(2).x(), 1e-9);
    }

    @Test
    void aGeometryPanelCopiesEachToolsPathsOnly() {
        Geometry path = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(4, 0)});
        GeometryJoin.Joined panel = Panelize.geometry("MM", path, true, List.of(new ToolGeometry(0.2, path)),
                Panelize.layout(new double[]{0, 0, 4, 1}, 3, 1, 1, 0, Double.NaN, Double.NaN));
        assertEquals(3, panel.geometry().getNumGeometries());
        assertEquals(1, panel.tools().size());
        assertEquals(3, panel.tools().get(0).geometry().getNumGeometries());
    }
}
