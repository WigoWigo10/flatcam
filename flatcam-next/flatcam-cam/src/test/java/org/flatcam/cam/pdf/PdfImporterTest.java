package org.flatcam.cam.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.Deflater;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;

/**
 * Values asserted here (areas, bounds, drill diameters/centres) were cross-checked against
 * FlatCAM Python's own ParsePDF.PdfParser run directly (stubbing only its Qt/app dependency)
 * on the same content streams, confirming this port's fixes land on the SAME final geometry
 * Python's buggy code eventually produces (see PdfImporter's class doc for which bugs).
 */
class PdfImporterTest {

    private static final double POINT_TO_MM = 25.4 / 72.0;

    @Test
    void strokeColorChangeStartsANewLayerAndTheSameColorDoesNot() {
        PdfImporter.Result result = parse("""
                0 0 0 RG
                10 10 m
                20 10 l
                20 20 l
                h
                f
                0 0 0 RG
                30 10 m
                40 10 l
                40 20 l
                h
                f
                1 0 0 RG
                50 10 m
                60 10 l
                60 20 l
                h
                f
                """, "MM");

        assertEquals(2, result.layers().size(), "two distinct RG colors, the repeated one does not split a layer");
        double triangleArea = 0.5 * (10 * POINT_TO_MM) * (10 * POINT_TO_MM);
        assertEquals(triangleArea * 2, result.layers().get(0).solidGeometry().getArea(), 1e-9,
                "both black triangles landed in the first layer");
        assertEquals(triangleArea, result.layers().get(1).solidGeometry().getArea(), 1e-9);
    }

    @Test
    void aBezierCircleFillBecomesOneHoleNotOneWrongPolygonPerCurveSegment() {
        // Python's own scope-leak bug (never reset between curve segments) builds 3 extra, wrong
        // polygons for this same circle before landing on this correct, final one.
        PdfImporter.Result result = parse("""
                0 0 0 RG
                1 w
                10 10 m
                50 10 l
                50 50 l
                10 50 l
                h
                S
                2 w
                100 100 200 50 re
                S
                1 1 1 rg
                300 300 m
                300 316.568542 313.431458 330 330 330 c
                346.568542 330 360 316.568542 360 300 c
                360 283.431458 346.568542 270 330 270 c
                313.431458 270 300 283.431458 300 300 c
                h
                f
                """, "MM");

        assertEquals(1, result.layers().size());
        assertEquals(144.230919, result.layers().get(0).solidGeometry().getArea(), 1e-4,
                "the square outline plus the rectangle outline, untouched by the far-away hole");
        ExcellonImage drills = result.drills();
        assertEquals(List.of(20.616), List.copyOf(drills.toolDiameters().values()));
        ExcellonImage.Drill drill = drills.drills().get(0);
        assertEquals(116.416667, drill.x(), 1e-4);
        assertEquals(105.833333, drill.y(), 1e-4);
    }

    @Test
    void rectangleWidthAndHeightAreNotOffsetASecondTime() {
        // Python added the active offset to the width/height too, bloating every rectangle
        // drawn while a translation was active - this is exact (a fill, no buffer fuzziness).
        PdfImporter.Result result = parse("""
                q
                1 0 0 1 100 200 cm
                0 0 0 RG
                0 0 50 30 re
                f
                Q
                """, "MM");

        double expectedArea = (50 * POINT_TO_MM) * (30 * POINT_TO_MM);
        assertEquals(expectedArea, result.layers().get(0).solidGeometry().getArea(), 1e-9);
        Envelope bounds = result.layers().get(0).solidGeometry().getEnvelopeInternal();
        assertEquals(100 * POINT_TO_MM, bounds.getMinX(), 1e-9);
        assertEquals(200 * POINT_TO_MM, bounds.getMinY(), 1e-9);
    }

    @Test
    void graphicsStateStackRestoresOffsetAndScaleTogether() {
        PdfImporter.Result result = parse("""
                q
                1 0 0 1 100 200 cm
                0 0 0 RG
                0 0 50 30 re
                f
                Q
                0 1 0 RG
                2 0 0 2 0 0 cm
                10 10 20 20 re
                f
                """, "MM");

        assertEquals(2, result.layers().size());
        // Matches Python bit-for-bit here since the offset happens to be zero at this point too.
        assertEquals(199.123457, result.layers().get(1).solidGeometry().getArea(), 1e-4);
        Envelope bounds = result.layers().get(1).solidGeometry().getEnvelopeInternal();
        assertEquals(10 * POINT_TO_MM * 2, bounds.getMinX(), 1e-6);
        assertEquals((10 + 20) * POINT_TO_MM * 2, bounds.getMaxX(), 1e-6);
    }

    @Test
    void curveVAndCurveYOperatorsAreReadWithTheRightControlPoints() {
        // 'v' (start doubles as its own first control point) and 'y' (stop doubles as the
        // second) - Python's own 'y' handler forgets to mark its subpath as a curve at all.
        PdfImporter.Result result = parse("""
                0 0 0 RG
                0 0 100 100 re
                f
                1 1 1 rg
                20 20 m
                20 30 25 40 35 40 c
                45 40 50 35 v
                50 15 35 10 y
                20 15 20 20 y
                h
                f
                """, "MM");

        assertEquals(1161.459283, result.layers().get(0).solidGeometry().getArea(), 1e-3);
        ExcellonImage.Drill drill = result.drills().drills().get(0);
        assertEquals(12.347222, drill.x(), 1e-4);
        assertEquals(8.819444, drill.y(), 1e-4);
        assertEquals(10.308, result.drills().toolDiameters().get(1), 1e-9);
    }

    @Test
    void aWhiteRectangleCutsTheLayerButIsNotACurveSoItIsNotADrill() {
        PdfImporter.Result result = parse("""
                0 0 0 RG
                0 0 100 100 re
                f
                1 1 1 rg
                20 20 30 30 re
                f
                """, "MM");

        assertNull(result.drills(), "only curved white fills become holes, per Python");
        double outer = (100 * POINT_TO_MM) * (100 * POINT_TO_MM);
        double hole = (30 * POINT_TO_MM) * (30 * POINT_TO_MM);
        assertEquals(outer - hole, result.layers().get(0).solidGeometry().getArea(), 1e-6);
    }

    @Test
    void aWhiteStrokeIsStillCopperOnlyAFillIsAHole() {
        PdfImporter.Result result = parse("""
                0 0 0 RG
                1 1 1 rg
                5 w
                10 10 m
                40 10 l
                S
                """, "MM");

        assertNull(result.drills());
        assertTrue(result.layers().get(0).solidGeometry().getArea() > 0, "the white stroke is still drawn as copper");
    }

    @Test
    void closeStrokeAndCloseFillStrokeOperatorsAreRecognized() {
        // Python compiled patterns for 's' and 'b'/'b*' but never actually checked them.
        PdfImporter.Result withS = parse("0 0 0 RG\n1 w\n10 10 m\n30 10 l\n30 30 l\ns\n", "MM");
        PdfImporter.Result withB = parse("0 0 1 RG\n0 1 0 rg\n10 10 m\n90 10 l\n90 90 l\nb\n", "MM");

        assertEquals(1, withS.layers().size());
        assertEquals(1, withB.layers().size());
        double triangleFillArea = 0.5 * (80 * POINT_TO_MM) * (80 * POINT_TO_MM);
        assertEquals(triangleFillArea, withB.layers().get(0).solidGeometry().getArea(), 1e-6);
    }

    @Test
    void clippingPathsAreDiscardedNotPainted() {
        PdfImporter.Result result = parse("""
                0 0 0 RG
                10 10 100 100 re
                W n
                50 50 10 10 re
                f
                """, "MM");

        assertEquals(1, result.layers().size());
        double expectedArea = (10 * POINT_TO_MM) * (10 * POINT_TO_MM);
        assertEquals(expectedArea, result.layers().get(0).solidGeometry().getArea(), 1e-9,
                "the clip rectangle itself must not be painted");
    }

    @Test
    void unrecognizedLinesAndEmptyPaintsAreIgnoredWithoutFailing() {
        PdfImporter.Result result = parse("""
                garbage line that matches nothing
                0 0 0 RG
                S
                n
                10 10 20 20 re
                f
                """, "MM");

        assertEquals(1, result.layers().size());
    }

    // ---- container: zlib/FlateDecode extraction ----------------------------------------------------------------------

    @Test
    void extractsAndDecompressesAFlateDecodeStreamFromARawPdfFile() {
        byte[] pdf = fakePdf("0 0 0 RG\n0 0 100 100 re\nf\n");

        PdfImporter.Result result = PdfImporter.parse(pdf, "MM", CancellationToken.none());

        assertEquals(1, result.layers().size());
        assertEquals((100 * POINT_TO_MM) * (100 * POINT_TO_MM), result.layers().get(0).solidGeometry().getArea(), 1e-6);
    }

    @Test
    void severalStreamsInOneFileAreConcatenated() {
        byte[] first = fakePdfObject(1, "0 0 0 RG\n0 0 50 50 re\nf\n");
        byte[] second = fakePdfObject(2, "1 0 0 RG\n60 0 50 50 re\nf\n");
        byte[] pdf = concat(first, second);

        PdfImporter.Result result = PdfImporter.parse(pdf, "MM", CancellationToken.none());

        assertEquals(2, result.layers().size());
    }

    @Test
    void malformedOrMissingContentIsRejectedClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> PdfImporter.parse("not a pdf at all".getBytes(StandardCharsets.US_ASCII), "MM",
                        CancellationToken.none()),
                "no FlateDecode stream present");

        // A stream cut short before its compressed data is complete.
        byte[] compressed = deflate("0 0 0 RG\n0 0 50 50 re\nf\n".repeat(50).getBytes(StandardCharsets.UTF_8));
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);
        assertThrows(IllegalArgumentException.class,
                () -> PdfImporter.parse(wrapAsPdfObject(1, truncated), "MM", CancellationToken.none()));

        // Valid FlateDecode data whose decompressed bytes are not valid UTF-8.
        byte[] notUtf8 = deflate(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD});
        assertThrows(IllegalArgumentException.class,
                () -> PdfImporter.parse(wrapAsPdfObject(1, notUtf8), "MM", CancellationToken.none()));

        assertThrows(IllegalArgumentException.class, () -> PdfImporter.parse(
                fakePdf("this stream has no drawing at all, just prose"), "MM", CancellationToken.none()));
    }

    @Test
    void cancellationStopsParsing() {
        String manyLines = "0 0 0 RG\n0 0 10 10 re\nf\n".repeat(5_000);
        assertThrows(CancellationException.class,
                () -> PdfImporter.parseContent(manyLines, "MM", () -> true));
    }

    @Test
    void unsupportedUnitsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PdfImporter.parseContent("0 0 0 RG\n0 0 10 10 re\nf\n", "CM", CancellationToken.none()));
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static PdfImporter.Result parse(String content, String units) {
        return PdfImporter.parseContent(content, units, CancellationToken.none());
    }

    private static byte[] fakePdf(String contentStream) {
        return fakePdfObject(1, contentStream);
    }

    private static byte[] fakePdfObject(int objectNumber, String contentStream) {
        return wrapAsPdfObject(objectNumber, deflate(contentStream.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] wrapAsPdfObject(int objectNumber, byte[] compressed) {
        String header = "%PDF-1.4\n" + objectNumber + " 0 obj\n<< /Length " + compressed.length
                + " /Filter /FlateDecode >>\nstream\n";
        String footer = "\nendstream\nendobj\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(compressed);
        out.writeBytes(footer.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(a);
        out.writeBytes(b);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }
}
