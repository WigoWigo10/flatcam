package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flatcam.app.project.PythonProjectIO;
import org.flatcam.app.project.flatprj.WktJson;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.json.JSONArray;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;

class PlotAreaNestedGeometryTest {

    @Test
    void visitsPolygonsInsideNestedCollectionsAndPythonGeometryLists() {
        GeometryFactory factory = new GeometryFactory();
        Polygon first = square(factory, 0);
        Polygon second = square(factory, 3);
        Geometry multi = factory.createMultiPolygon(new Polygon[]{first, second});
        Geometry nested = factory.createGeometryCollection(new Geometry[]{
                factory.createGeometryCollection(new Geometry[]{multi})});
        List<Geometry> parts = new ArrayList<>();

        PlotAreaView.forEachDrawablePart(nested, parts::add);
        assertEquals(List.of(first, second), parts);

        parts.clear();
        Geometry pythonList = WktJson.unwrap(new JSONArray().put(WktJson.wrap(multi)));
        PlotAreaView.forEachDrawablePart(pythonList, parts::add);
        assertEquals(List.of(first, second), parts);
    }

    @Test
    void viewportCullsOnlyPartsOutsideTheVisibleWorldWithStrokeMargin() {
        GeometryFactory factory = new GeometryFactory();
        Envelope view = PlotAreaView.visibleWorldBounds(0, 0, 10, 100, 100);
        assertTrue(PlotAreaView.intersectsViewport(square(factory, 4.9), view));
        assertFalse(PlotAreaView.intersectsViewport(square(factory, 6), view));
        assertEquals(-5.2, view.getMinX(), 1e-9);
        assertEquals(5.2, view.getMaxY(), 1e-9);
    }

    @Test
    void cncDisplayLodSwitchesBackToPreciseGeometryAtHighZoomOrSpecialPlotModes() {
        assertTrue(PlotAreaView.shouldUseCenterlineLod(true, false, 0.02, 10));
        assertFalse(PlotAreaView.shouldUseCenterlineLod(true, false, 0.02, 100));
        assertFalse(PlotAreaView.shouldUseCenterlineLod(false, false, 0.02, 10));
        assertFalse(PlotAreaView.shouldUseCenterlineLod(true, true, 0.02, 10));
    }

    @Test
    void openPathLodKeepsEndpointsAndDoesNotSimplifyFilledRings() {
        assertTrue(PlotAreaView.omitSubpixelOpenPathVertex(false, 1, 3, 0.2, 0.3));
        assertFalse(PlotAreaView.omitSubpixelOpenPathVertex(false, 2, 3, 0.2, 0.3));
        assertFalse(PlotAreaView.omitSubpixelOpenPathVertex(true, 1, 3, 0.2, 0.3));
        assertFalse(PlotAreaView.omitSubpixelOpenPathVertex(false, 1, 3, 1.0, 0.1));
    }

    @Test
    void optionalRealPythonProjectHasDrawableFrontAndBackCopper() throws IOException {
        String fixture = System.getProperty("flatcam.python.plot.fixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());
        var project = PythonProjectIO.load(Path.of(fixture));
        for (String side : new String[]{"F_Cu", "B_Cu"}) {
            var entry = project.gerbers().stream()
                    .filter(gerber -> gerber.name().contains(side))
                    .findFirst().orElseThrow();
            assertFalse(entry.visible(), side + " was saved with plot=false in Python");
            GeometryCollection wrapper = assertInstanceOf(GeometryCollection.class,
                    entry.image().solidGeometry(), side);
            assertInstanceOf(MultiPolygon.class, wrapper.getGeometryN(0), side);
            List<Geometry> parts = new ArrayList<>();
            PlotAreaView.forEachDrawablePart(entry.image().solidGeometry(), parts::add);
            assertFalse(parts.isEmpty(), side + " must have drawable copper after activation");
            assertTrue(parts.stream().allMatch(Polygon.class::isInstance), side);
        }
    }

    @Test
    void optionalDensePythonCncJobUsesFewerDisplayPathsThanPreciseBuffers() throws IOException {
        String fixture = System.getProperty("flatcam.python.plot.fixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank());
        var project = PythonProjectIO.load(Path.of(fixture));
        var job = project.cncJobs().stream()
                .filter(entry -> "Cobre_Morto_Bottom_cnc".equals(entry.name()))
                .findFirst().orElseThrow();
        var parsed = GCodeToolpathParser.parse(job.gcode(), CancellationToken.NONE, ProgressCallback.NONE);
        assertTrue(parsed.plotAvailable());
        int preciseParts = parsed.cutGeometry().getNumGeometries();
        int displayParts = parsed.cutCenterlines().getNumGeometries();
        assertTrue(displayParts * 5 < preciseParts,
                () -> "display paths=" + displayParts + ", precise buffers=" + preciseParts);
    }

    private static Polygon square(GeometryFactory factory, double x) {
        return factory.createPolygon(new Coordinate[]{
                new Coordinate(x, 0), new Coordinate(x + 1, 0),
                new Coordinate(x + 1, 1), new Coordinate(x, 1), new Coordinate(x, 0)});
    }
}
