package org.flatcam.cam.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.flatcam.cam.svg.FilmExporter.FileType;
import org.flatcam.cam.svg.FilmExporter.Mirror;
import org.flatcam.cam.svg.FilmExporter.Options;
import org.flatcam.cam.svg.FilmExporter.SkewReference;

class FilmExporterTest {

    @TempDir
    Path directory;

    private static Geometry wkt(String text) throws ParseException {
        return new WKTReader().read(text);
    }

    private static Options options(boolean negative, double boundary, Mirror mirror, FileType type) {
        return new Options(negative, boundary, 0, 1, 1, 0, 0, SkewReference.BOTTOM_LEFT, mirror, "#000000", type, "Bounds",
                true, 254);
    }

    @Test
    void negativeFilmIsABlackSheetWithWhiteFeaturesAndTheBoundary() throws Exception {
        Geometry pad = wkt("POLYGON((2 2, 4 2, 4 4, 2 4, 2 2))");
        Geometry box = wkt("POLYGON((0 0, 10 0, 10 6, 0 6, 0 0))");
        String svg = FilmExporter.svg(pad, box, "MM", options(true, 1.0, Mirror.NONE, FileType.SVG));
        assertTrue(svg.contains("width=\"12mm\" height=\"8mm\""), svg);
        assertTrue(svg.contains("viewBox=\"-1 -7 12 8\""), svg);
        assertTrue(svg.contains("neg_rect") && svg.contains("fill:#000000"), svg);
        assertTrue(svg.contains("fill=\"#FFFFFF\""), svg);
    }

    @Test
    void positiveFilmUsesTheColourAndMirrorFlipsAroundTheBoxCentre() throws Exception {
        Geometry pad = wkt("POLYGON((0 0, 2 0, 2 2, 0 2, 0 0))");
        Geometry box = wkt("POLYGON((0 0, 10 0, 10 2, 0 2, 0 0))");
        String svg = FilmExporter.svg(pad, box, "MM", options(false, 0.0, Mirror.Y, FileType.SVG));
        assertTrue(svg.contains("fill=\"#000000\"") && !svg.contains("neg_rect"), svg);
        // Mirrored across x = 5: the pad moves to x 8..10.
        assertTrue(svg.contains("M10,0 L8,0 L8,2 L10,2 L10,0 z"), svg);
    }

    @Test
    void scaleGrowsTheSheetFromTheBoxCorner() throws Exception {
        Geometry box = wkt("POLYGON((0 0, 10 0, 10 2, 0 2, 0 0))");
        Options scaled = new Options(false, 0, 0, 2, 1, 0, 0, SkewReference.CENTER, Mirror.NONE, "#000000", FileType.SVG,
                "Bounds", true, 96);
        assertTrue(FilmExporter.svg(box, box, "MM", scaled).contains("width=\"20mm\" height=\"2mm\""));
    }

    @Test
    void pngAndPdfAreWritten() throws Exception {
        Geometry pad = wkt("POLYGON((2 2, 4 2, 4 4, 2 4, 2 2))");
        Geometry box = wkt("POLYGON((0 0, 10 0, 10 6, 0 6, 0 0))");
        Path png = directory.resolve("film.png");
        FilmExporter.write(pad, box, "MM", options(false, 0, Mirror.NONE, FileType.PNG), png);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(png)));
        assertEquals(100, image.getWidth());
        assertEquals(60, image.getHeight());
        assertEquals(0, image.getRGB(30, 30) & 0xFFFFFF);
        assertEquals(0xFFFFFF, image.getRGB(90, 5) & 0xFFFFFF);

        Path pdf = directory.resolve("film.pdf");
        FilmExporter.write(pad, box, "MM", options(true, 0, Mirror.NONE, FileType.PDF), pdf);
        String text = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4") && text.contains("xref") && text.trim().endsWith("%%EOF"));
        assertTrue(text.contains("/MediaBox [0 0 28.3465 17.0079]"), text);
    }
}
