package org.flatcam.cam.dxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;

class DxfImporterTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void ourOwnDxfExportImportsBackAsClosedPathsAndFilledCopper() throws Exception {
        Geometry board = new WKTReader().read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 4 2, 4 4, 2 4, 2 2))");
        String dxf = new DxfExporter().export(board, "MM");

        DxfImporter.Result result = DxfImporter.parse(dxf, "MM");

        assertEquals(2, result.shapes().getNumGeometries());
        assertTrue(((LineString) result.shapes().getGeometryN(0)).isClosed(), "closed polylines stay closed paths");
        assertEquals(96, result.copper().getArea(), 1e-9, "the inner outline is a hole in the Gerber");
        assertTrue(board.symDifference(result.copper()).getArea() < 1e-9);
    }

    @Test
    void drawingUnitsAreConvertedAndUnitlessDrawingsAreTakenAsIs() {
        String inches = new Dxf().header(1).line(0, 0, 1, 0).build();
        String unitless = new Dxf().line(0, 0, 1, 0).build();

        assertEquals(25.4, DxfImporter.parse(inches, "MM").shapes().getLength(), 1e-9);
        assertEquals(1, DxfImporter.parse(inches, "IN").shapes().getLength(), 1e-9);
        assertEquals(1, DxfImporter.parse(unitless, "MM").shapes().getLength(), 1e-9);
    }

    @Test
    void bulgesAreArcsNotChords() {
        // (0,0) -> (10,0) with bulge 1 is a counter-clockwise half circle below the chord.
        String dxf = new Dxf().entity("LWPOLYLINE", 90, "2", 70, "1",
                10, "0", 20, "0", 42, "1", 10, "10", 20, "0").build();

        DxfImporter.Result result = DxfImporter.parse(dxf, "MM");

        Envelope box = result.shapes().getEnvelopeInternal();
        assertEquals(-5, box.getMinY(), 1e-3);
        assertEquals(0, box.getMaxY(), 1e-9);
        assertEquals(Math.PI * 25 / 2, result.copper().getArea(), 0.02);
    }

    @Test
    void linesAndArcsThatCloseALoopBecomeOneOutlineAndFilledCopper() {
        // A 10 x 4 slot: two straight sides and two half circles.
        String dxf = new Dxf()
                .line(2, 0, 8, 0).line(8, 4, 2, 4)
                .entity("ARC", 10, "8", 20, "2", 40, "2", 50, "270", 51, "90")
                .entity("ARC", 10, "2", 20, "2", 40, "2", 50, "90", 51, "270")
                .build();

        DxfImporter.Result result = DxfImporter.parse(dxf, "MM");

        assertEquals(1, result.shapes().getNumGeometries(), "merged end to end like Python's linemerge");
        assertTrue(((LineString) result.shapes().getGeometryN(0)).isClosed());
        assertEquals(6 * 4 + Math.PI * 4, result.copper().getArea(), 0.02);
    }

    @Test
    void blockInsertsApplyBaseScaleRotationAndArrays() {
        String dxf = new Dxf()
                .block("PAD", 1, 1, List.<String[]>of(new String[]{"CIRCLE", "10", "1", "20", "1", "40", "0.5"}))
                .entity("INSERT", 2, "PAD", 10, "10", 20, "0", 41, "2", 42, "2", 50, "90",
                        70, "3", 44, "5")
                .build();

        DxfImporter.Result result = DxfImporter.parse(dxf, "MM");

        List<Polygon> circles = polygons(result.shapes());
        assertEquals(3, circles.size());
        // The array runs along the block's X, which the 90 degree rotation turns into +Y.
        for (int index = 0; index < 3; index++) {
            Coordinate center = circles.get(index).getCentroid().getCoordinate();
            assertEquals(10, center.x, 1e-6);
            assertEquals(5 * index, center.y, 1e-6);
            assertEquals(Math.PI, circles.get(index).getArea(), 0.01, "radius 0.5 scaled by 2");
        }
    }

    @Test
    void splinesAndOldStylePolylinesAreFollowed() {
        String dxf = new Dxf()
                .entity("SPLINE", 71, "2", 72, "6", 73, "3", 40, "0", 40, "0", 40, "0", 40, "1", 40, "1", 40, "1",
                        10, "0", 20, "0", 10, "5", 20, "10", 10, "10", 20, "0")
                .entity("POLYLINE", 66, "1", 70, "0", 10, "0", 20, "0")
                .entity("VERTEX", 10, "20", 20, "0")
                .entity("VERTEX", 10, "30", 20, "0")
                .entity("VERTEX", 10, "30", 20, "5")
                .entity("SEQEND")
                .build();

        DxfImporter.Result result = DxfImporter.parse(dxf, "MM");

        LineString bezier = (LineString) result.shapes().getGeometryN(0);
        assertEquals(5, bezier.getEnvelopeInternal().getMaxY(), 1e-3, "a quadratic Bezier peaks at half its control");
        LineString polyline = (LineString) result.shapes().getGeometryN(1);
        assertEquals(15, polyline.getLength(), 1e-9, "the POLYLINE header point is not a vertex");
    }

    @Test
    void textIsCountedAndInvalidFilesAreRejected() {
        String withText = new Dxf().line(0, 0, 1, 0).entity("TEXT", 10, "0", 20, "0", 1, "R1").build();

        assertEquals(1, DxfImporter.parse(withText, "MM").skippedTextEntities());
        assertThrows(IllegalArgumentException.class,
                () -> DxfImporter.parse(new Dxf().entity("TEXT", 1, "only text").build(), "MM"));
        assertThrows(IllegalArgumentException.class, () -> DxfImporter.parse("hello\nworld\n", "MM"));
    }

    private static List<Polygon> polygons(Geometry geometry) {
        List<Polygon> polygons = new ArrayList<>();
        for (int index = 0; index < geometry.getNumGeometries(); index++) {
            if (geometry.getGeometryN(index) instanceof Polygon polygon) {
                polygons.add(polygon);
            }
        }
        return polygons;
    }

    /** Minimal ASCII DXF writer for the tests. */
    private static final class Dxf {
        private final StringBuilder header = new StringBuilder();
        private final StringBuilder blocks = new StringBuilder();
        private final StringBuilder entities = new StringBuilder();

        Dxf header(int insunits) {
            pair(header, 9, "$INSUNITS");
            pair(header, 70, Integer.toString(insunits));
            return this;
        }

        Dxf line(double x1, double y1, double x2, double y2) {
            return entity("LINE", 10, fmt(x1), 20, fmt(y1), 11, fmt(x2), 21, fmt(y2));
        }

        Dxf entity(String type, Object... codesAndValues) {
            append(entities, type, codesAndValues);
            return this;
        }

        Dxf block(String name, double baseX, double baseY, List<String[]> content) {
            pair(blocks, 0, "BLOCK");
            pair(blocks, 2, name);
            pair(blocks, 10, fmt(baseX));
            pair(blocks, 20, fmt(baseY));
            for (String[] entity : content) {
                pair(blocks, 0, entity[0]);
                for (int index = 1; index + 1 < entity.length; index += 2) {
                    pair(blocks, Integer.parseInt(entity[index]), entity[index + 1]);
                }
            }
            pair(blocks, 0, "ENDBLK");
            return this;
        }

        String build() {
            StringBuilder dxf = new StringBuilder();
            section(dxf, "HEADER", header);
            section(dxf, "BLOCKS", blocks);
            section(dxf, "ENTITIES", entities);
            pair(dxf, 0, "EOF");
            return dxf.toString();
        }

        private static void append(StringBuilder target, String type, Object... codesAndValues) {
            pair(target, 0, type);
            for (int index = 0; index + 1 < codesAndValues.length; index += 2) {
                pair(target, (Integer) codesAndValues[index], codesAndValues[index + 1].toString());
            }
        }

        private static void section(StringBuilder dxf, String name, StringBuilder content) {
            pair(dxf, 0, "SECTION");
            pair(dxf, 2, name);
            dxf.append(content);
            pair(dxf, 0, "ENDSEC");
        }

        private static void pair(StringBuilder target, int code, String value) {
            target.append(code).append('\n').append(value).append('\n');
        }

        private static String fmt(double value) {
            return Double.toString(value);
        }
    }
}
