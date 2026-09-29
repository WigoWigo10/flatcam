package org.flatcam.cam.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

class SvgImporterTest {

    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" "
            + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" ";

    @Test
    void inkscapeMillimetreDocumentKeepsSizesAndFlipsYUp() {
        SvgImporter.Result result = SvgImporter.parse(SVG
                + "width=\"100mm\" height=\"50mm\" viewBox=\"0 0 100 50\">"
                + "<rect x=\"10\" y=\"10\" width=\"20\" height=\"10\"/></svg>", "MM");

        Envelope box = result.shapes().getEnvelopeInternal();
        assertEquals(10, box.getMinX(), 1e-9);
        assertEquals(30, box.getMaxX(), 1e-9);
        assertEquals(30, box.getMinY(), 1e-9, "y=10..20 from the top is 30..40 from the bottom");
        assertEquals(40, box.getMaxY(), 1e-9);
        assertEquals(200, result.copper().getArea(), 1e-9);
    }

    @Test
    void unitlessLengthsAreCssPixelsAndInchOutputConverts() {
        String svg = SVG + "width=\"96\" height=\"96\"><line x1=\"0\" y1=\"0\" x2=\"96\" y2=\"0\"/></svg>";

        SvgImporter.Result millimetres = SvgImporter.parse(svg, "MM");
        SvgImporter.Result inches = SvgImporter.parse(svg, "IN");

        assertEquals(25.4, millimetres.shapes().getLength(), 1e-9, "96 px is one inch");
        assertEquals(25.4, millimetres.shapes().getEnvelopeInternal().getMaxY(), 1e-9);
        assertEquals(1, inches.shapes().getLength(), 1e-9);
    }

    @Test
    void illustratorAndOldInkscapePixelsKeepTheirWritersSize() {
        String illustrator = "<?xml version=\"1.0\"?><!-- Generator: Adobe Illustrator 16.0.0, SVG Export -->"
                + SVG + "width=\"72px\" height=\"72px\" viewBox=\"0 0 72 72\"><line x1=\"0\" y1=\"0\" x2=\"72\" y2=\"0\"/></svg>";
        String oldInkscape = SVG + "xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" "
                + "inkscape:version=\"0.48.4 r9939\" width=\"90\" height=\"90\">"
                + "<line x1=\"0\" y1=\"0\" x2=\"90\" y2=\"0\"/></svg>";
        String newInkscape = oldInkscape.replace("0.48.4 r9939", "1.3.2").replace("90", "96");

        assertEquals(25.4, SvgImporter.parse(illustrator, "MM").shapes().getLength(), 1e-9);
        assertEquals(25.4, SvgImporter.parse(oldInkscape, "MM").shapes().getLength(), 1e-9);
        assertEquals(25.4, SvgImporter.parse(newInkscape, "MM").shapes().getLength(), 1e-9);
    }

    @Test
    void viewBoxOriginAndUniformAspectAreHonoured() {
        SvgImporter.Result result = SvgImporter.parse(SVG
                + "width=\"20mm\" height=\"10mm\" viewBox=\"100 100 10 10\">"
                + "<rect x=\"100\" y=\"100\" width=\"10\" height=\"10\"/></svg>", "MM");

        Envelope box = result.shapes().getEnvelopeInternal();
        assertEquals(5, box.getMinX(), 1e-9, "a 10x10 box in 20x10 is scaled 1:1 and centered");
        assertEquals(15, box.getMaxX(), 1e-9);
        assertEquals(0, box.getMinY(), 1e-9);
        assertEquals(10, box.getMaxY(), 1e-9);
    }

    @Test
    void pathRingsAreFilledEvenOddAndRelativeCommandsWork() {
        SvgImporter.Result result = SvgImporter.parse(SVG + "width=\"20mm\" height=\"20mm\" viewBox=\"0 0 20 20\">"
                + "<path d=\"M0 0 H10 V10 H0 Z M2 2 H4 V4 H2 Z\"/>"
                + "<path d=\"m 12 12 2 0 0 2 z\"/></svg>", "MM");

        List<Polygon> polygons = polygons(result.shapes());
        assertEquals(2, polygons.size());
        assertEquals(1, polygons.get(0).getNumInteriorRing(), "the inner ring is a hole");
        assertEquals(96, polygons.get(0).getArea(), 1e-9);
        assertEquals(2, polygons.get(1).getArea(), 1e-9, "implicit relative line-tos after m");
    }

    @Test
    void arcsCurvesAndCirclesFollowTheirShape() {
        SvgImporter.Result result = SvgImporter.parse(SVG + "width=\"40mm\" height=\"40mm\" viewBox=\"0 0 40 40\">"
                + "<path d=\"M0,5 A5,5 0 1,1 10,5 A5,5 0 1,1 0,5 Z\"/>"
                + "<circle cx=\"25\" cy=\"5\" r=\"5\"/>"
                + "<path d=\"M0 30 C 0 40, 10 40, 10 30\" fill=\"none\"/></svg>", "MM");

        List<Polygon> polygons = polygons(result.shapes());
        assertEquals(Math.PI * 25, polygons.get(0).getArea(), 0.05);
        assertEquals(Math.PI * 25, polygons.get(1).getArea(), 0.05);
        LineString curve = (LineString) result.shapes().getGeometryN(2);
        assertEquals(0, curve.getStartPoint().getX(), 1e-9);
        assertEquals(10, curve.getEndPoint().getX(), 1e-9);
        assertEquals(40 - 37.5, curve.getEnvelopeInternal().getMinY(), 1e-3, "the curve peaks at y=37.5");
    }

    @Test
    void nestedTransformsComposeInSvgOrder() {
        SvgImporter.Result result = SvgImporter.parse(SVG + "width=\"100mm\" height=\"100mm\" viewBox=\"0 0 100 100\">"
                + "<g transform=\"translate(10,0)\"><rect transform=\"scale(2) rotate(90)\" "
                + "width=\"1\" height=\"3\"/></g></svg>", "MM");

        Envelope box = result.shapes().getEnvelopeInternal();
        // rotate(90) maps the 1x3 rect to x -3..0, y 0..1; scale(2) -> x -6..0, y 0..2; translate -> x 4..10.
        assertEquals(4, box.getMinX(), 1e-9);
        assertEquals(10, box.getMaxX(), 1e-9);
        assertEquals(98, box.getMinY(), 1e-9);
        assertEquals(100, box.getMaxY(), 1e-9);
    }

    @Test
    void definitionsAreDrawnOnlyWhereUsedAndHiddenOrTextIsSkipped() {
        SvgImporter.Result result = SvgImporter.parse(SVG + "width=\"20mm\" height=\"20mm\" viewBox=\"0 0 20 20\">"
                + "<defs><rect id=\"r\" width=\"1\" height=\"1\"/></defs>"
                + "<use xlink:href=\"#r\" x=\"5\"/>"
                + "<rect width=\"4\" height=\"4\" style=\"display:none\"/>"
                + "<g visibility=\"hidden\"><rect width=\"4\" height=\"4\"/></g>"
                + "<text x=\"1\" y=\"1\">T1</text></svg>", "MM");

        List<Polygon> polygons = polygons(result.shapes());
        assertEquals(1, polygons.size());
        assertEquals(5, polygons.get(0).getEnvelopeInternal().getMinX(), 1e-9);
        assertEquals(1, result.skippedTextElements());
    }

    @Test
    void gerberCopperGivesStrokedLinesTheirWidth() {
        SvgImporter.Result result = SvgImporter.parse(SVG + "width=\"20mm\" height=\"20mm\" viewBox=\"0 0 20 20\">"
                + "<g style=\"stroke:#000;stroke-width:2\"><line x1=\"2\" y1=\"10\" x2=\"12\" y2=\"10\"/></g>"
                + "<line x1=\"2\" y1=\"15\" x2=\"12\" y2=\"15\"/></svg>", "MM");

        assertEquals(20, result.shapes().getLength(), 1e-9, "both lines are Geometry paths");
        assertEquals(10 * 2 + Math.PI, result.copper().getArea(), 0.05,
                "only the stroked line is copper, with round ends");
    }

    @Test
    void ourOwnSvgExportImportsBackWithTheSameShapeFromThePageCorner() throws Exception {
        Geometry original = new org.locationtech.jts.io.WKTReader().read(
                "POLYGON ((-3 -40, 12.5 -40, 12.5 -21, -3 -21, -3 -40), (0 -35, 5 -35, 5 -30, 0 -30, 0 -35))");
        String svg = new SvgExporter().export(List.of(SvgExporter.shapes(original)), "MM");

        SvgImporter.Result result = SvgImporter.parse(svg, "MM");

        // An SVG only knows its page: the page's bottom-left corner lands on the origin, and the
        // export framed the shape with half its stroke width (0.01) around it.
        double margin = SvgExporter.SHAPE_STROKE_WIDTH / 2;
        Geometry expected = org.locationtech.jts.geom.util.AffineTransformation
                .translationInstance(3 + margin, 40 + margin).transform(original);
        assertTrue(expected.symDifference(result.copper()).getArea() < 1e-9,
                () -> "resized or distorted: " + result.copper());
    }

    @Test
    void textOnlyAndMalformedFilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SvgImporter.parse(SVG + "><text>x</text></svg>", "MM"));
        assertThrows(IllegalArgumentException.class, () -> SvgImporter.parse("<html/>", "MM"));
        assertThrows(IllegalArgumentException.class, () -> SvgImporter.parse(SVG + "><path d=\"M 0 x\"/></svg>", "MM"));
    }

    private static List<Polygon> polygons(Geometry geometry) {
        java.util.ArrayList<Polygon> polygons = new java.util.ArrayList<>();
        for (int index = 0; index < geometry.getNumGeometries(); index++) {
            if (geometry.getGeometryN(index) instanceof Polygon polygon) {
                polygons.add(polygon);
            }
        }
        return polygons;
    }
}
