package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

class PlotObjectSelectionTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void picksTheTopmostGeometryAndNotAnEmptyArea() {
        List<PlotObjectSelection.Layer<String>> layers = List.of(
                new PlotObjectSelection.Layer<>("Gerber", rectangle(0, 0, 10, 10)),
                new PlotObjectSelection.Layer<>("Excellon", rectangle(5, 5, 15, 15)));

        assertEquals("Excellon", PlotObjectSelection.topmostAt(layers, 7, 7, 0));
        assertEquals("Excellon", PlotObjectSelection.nextAt(layers, 7, 7, 0, null));
        assertEquals("Gerber", PlotObjectSelection.nextAt(layers, 7, 7, 0, "Excellon"));
        assertEquals("Excellon", PlotObjectSelection.nextAt(layers, 7, 7, 0, "Gerber"));
        assertEquals("Gerber", PlotObjectSelection.topmostAt(layers, 1, 1, 0));
        assertNull(PlotObjectSelection.topmostAt(layers, 20, 20, 0));
        assertNull(PlotObjectSelection.nextAt(layers, 20, 20, 0, null));
    }

    @Test
    void respectsPolygonHolesAndAllowsPickingThinPaths() throws ParseException {
        Geometry withHole = new WKTReader().read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0),"
                + "(3 3, 3 7, 7 7, 7 3, 3 3))");
        List<PlotObjectSelection.Layer<String>> layers = List.of(
                new PlotObjectSelection.Layer<>("Gerber", withHole),
                new PlotObjectSelection.Layer<>("Geometry", FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(12, 0), new Coordinate(12, 10)})));

        assertNull(PlotObjectSelection.topmostAt(layers, 5, 5, 0.1));
        assertEquals("Geometry", PlotObjectSelection.topmostAt(layers, 12.2, 5, 0.25));
    }

    @Test
    void boxDirectionControlsEnclosingVersusTouching() {
        List<PlotObjectSelection.Layer<String>> layers = List.of(
                new PlotObjectSelection.Layer<>("Gerber", rectangle(0, 0, 10, 10)),
                new PlotObjectSelection.Layer<>("Excellon", rectangle(8, 8, 12, 12)));

        assertEquals(List.of("Gerber"), PlotObjectSelection.inBox(layers, -1, -1, 11, 11));
        assertEquals(List.of("Gerber", "Excellon"), PlotObjectSelection.inBox(layers, 11, 11, -1, -1));
    }

    @Test
    void cncJobCutAndTravelAreOneObjectForBoxSelection() {
        List<PlotObjectSelection.Layer<String>> layers = List.of(
                new PlotObjectSelection.Layer<>("CNC Job", rectangle(0, 0, 1, 1)),
                new PlotObjectSelection.Layer<>("CNC Job", rectangle(20, 20, 21, 21)));

        assertEquals(List.of(), PlotObjectSelection.inBox(layers, -1, -1, 2, 2));
        assertEquals(List.of("CNC Job"), PlotObjectSelection.inBox(layers, 2, 2, -1, -1));
        assertEquals(List.of("CNC Job"), PlotObjectSelection.at(layers, 0.5, 0.5, 0));
        assertEquals(21, PlotObjectSelection.boundsByOwner(layers).get("CNC Job").getMaxX());
    }

    private static Geometry rectangle(double minX, double minY, double maxX, double maxY) {
        return FACTORY.toGeometry(new org.locationtech.jts.geom.Envelope(minX, maxX, minY, maxY));
    }
}
