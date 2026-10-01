package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.convert.QrCodeMarker.ErrorLevel;
import org.flatcam.cam.convert.QrCodeMarker.Options;
import org.flatcam.cam.convert.QrCodeMarker.Polarity;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

class QrCodeMarkerTest {

    @TempDir
    Path directory;

    private static Options options(String text, int version, Polarity polarity, boolean rounded) {
        return new Options(text, version, ErrorLevel.L, 10, 2, polarity, rounded);
    }

    /** A 40 x 40 copper square centred on (20, 20). */
    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,40.0X40.0*%",
                "D10*", "X200000Y200000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    @Test
    void versionOneIs21ModulesWithTheFinderPatternsAndGrowsWhenTheTextIsLong() {
        QrCodeMarker.Code small = QrCodeMarker.encode(options("HELLO", 1, Polarity.POSITIVE, false));
        assertEquals(21, small.size());
        assertEquals(1, small.version());
        assertTrue(small.modules()[0][0] && small.modules()[0][6] && small.modules()[6][0] && !small.modules()[1][1]);
        QrCodeMarker.Code large = QrCodeMarker.encode(options("x".repeat(120), 1, Polarity.POSITIVE, false));
        assertTrue(large.version() > 1 && large.size() == 17 + 4 * large.version());
        assertEquals(25, QrCodeMarker.encode(options("HELLO", 2, Polarity.POSITIVE, false)).size());
        assertThrows(IllegalArgumentException.class,
                () -> QrCodeMarker.encode(options("x".repeat(5000), 1, Polarity.POSITIVE, false)));
        assertThrows(IllegalArgumentException.class, () -> options("", 1, Polarity.POSITIVE, false));
    }

    @Test
    void positiveCodeIsCentredClearsTheMaskAndAddsOneSquarePerDarkModule() throws IOException {
        GerberImage board = board();
        Options options = options("HELLO", 1, Polarity.POSITIVE, false);
        QrCodeMarker.Code code = QrCodeMarker.encode(options);
        long dark = 0;
        for (boolean[] row : code.modules()) {
            for (boolean module : row) {
                dark += module ? 1 : 0;
            }
        }
        GerberImage placed = QrCodeMarker.place(board, options, new Coordinate(20, 20));
        assertEquals(board.shapes().size() + 1 + dark, placed.shapes().size());
        double side = 21.0;   // 21 modules of 1.0
        double maskSide = side + 2 * 2.0;
        assertEquals(1600 - maskSide * maskSide + dark * 1.0, placed.solidGeometry().getArea(), 1e-6);
        Envelope box = QrCodeMarker.modules(code, options, new Coordinate(20, 20)).stream()
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                        list -> { Envelope e = new Envelope(); list.forEach(g -> e.expandToInclude(g.getEnvelopeInternal())); return e; }));
        assertEquals(20.0, (box.getMinX() + box.getMaxX()) / 2, 1e-9);
        assertEquals(20.0, (box.getMinY() + box.getMaxY()) / 2, 1e-9);
    }

    @Test
    void negativeCodeIsTheMaskMinusTheModules() throws IOException {
        GerberImage board = board();
        Options options = options("HELLO", 1, Polarity.NEGATIVE, false);
        QrCodeMarker.Code code = QrCodeMarker.encode(options);
        long dark = 0;
        for (boolean[] row : code.modules()) {
            for (boolean module : row) {
                dark += module ? 1 : 0;
            }
        }
        GerberImage placed = QrCodeMarker.place(board, options, new Coordinate(20, 20));
        double maskSide = 21.0 + 4.0;
        assertEquals(1600 - maskSide * maskSide + (maskSide * maskSide - dark), placed.solidGeometry().getArea(), 1e-6);
        GerberImage rounded = QrCodeMarker.place(board, options("HELLO", 1, Polarity.POSITIVE, true), new Coordinate(20, 20));
        assertTrue(rounded.solidGeometry().getArea() < 1600);
    }

    @Test
    void theGeometryDecodesBackToTheText() throws Exception {
        Options options = options("FlatCAM FX https://example.com/1", 1, Polarity.POSITIVE, false);
        QrCodeMarker.Code code = QrCodeMarker.encode(options);
        Coordinate centre = new Coordinate(100, 50);
        int n = code.size();
        int quiet = 4;
        int size = (n + 2 * quiet) * 4;
        int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, 0xFFFFFF);
        double module = options.boxSize() / 10.0;
        double left = centre.x - n * module / 2;
        double top = centre.y + n * module / 2;
        for (var square : QrCodeMarker.modules(code, options, centre)) {
            Coordinate c = square.getCentroid().getCoordinate();
            int col = (int) Math.floor((c.x - left) / module);
            int row = (int) Math.floor((top - c.y) / module);
            for (int dy = 0; dy < 4; dy++) {
                for (int dx = 0; dx < 4; dx++) {
                    pixels[((row + quiet) * 4 + dy) * size + (col + quiet) * 4 + dx] = 0x000000;
                }
            }
        }
        var source = new com.google.zxing.RGBLuminanceSource(size, size, pixels);
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source));
        assertEquals("FlatCAM FX https://example.com/1", new com.google.zxing.qrcode.QRCodeReader().decode(bitmap).getText());
    }
}
