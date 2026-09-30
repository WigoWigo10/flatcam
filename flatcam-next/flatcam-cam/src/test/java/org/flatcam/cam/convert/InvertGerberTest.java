package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvertGerberTest {

    @TempDir
    Path directory;

    /** One 2 mm round pad at (10, 10) and one at (30, 10): the box is 32 x 2 (pad edges), 2 mm tall. */
    private GerberImage twoPads() throws IOException {
        Path file = directory.resolve("pads.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,2.0*%",
                "D10*", "X100000Y100000D03*", "X300000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    @Test
    void theInvertedGerberIsTheMarginBoxMinusTheCopper() throws IOException {
        GerberImage source = twoPads();
        double[] b = source.bounds();
        double margin = 1.0;
        GerberImage inverted = InvertGerber.invert(source, margin, InvertGerber.JoinStyle.SQUARE);
        double boxArea = (b[2] - b[0] + 2 * margin) * (b[3] - b[1] + 2 * margin);
        assertEquals(boxArea - source.solidGeometry().getArea(), inverted.solidGeometry().getArea(), 1e-6);
        // What was copper is now empty and the other way round.
        assertTrue(inverted.solidGeometry().disjoint(source.solidGeometry().buffer(-1e-6)));
        assertEquals(1, inverted.shapes().size());
        assertEquals(1, inverted.partCount());
    }

    @Test
    void theJoinStyleShapesTheCornersOfTheMarginBox() throws IOException {
        GerberImage source = twoPads();
        double square = InvertGerber.invert(source, 2.0, InvertGerber.JoinStyle.SQUARE).solidGeometry().getArea();
        double round = InvertGerber.invert(source, 2.0, InvertGerber.JoinStyle.ROUND).solidGeometry().getArea();
        double bevel = InvertGerber.invert(source, 2.0, InvertGerber.JoinStyle.BEVEL).solidGeometry().getArea();
        assertTrue(square > round && round > bevel, square + " " + round + " " + bevel);
    }

    @Test
    void aZeroMarginStillWorksAndABadOneIsRefused() throws IOException {
        GerberImage source = twoPads();
        double[] b = source.bounds();
        GerberImage inverted = InvertGerber.invert(source, 0, InvertGerber.JoinStyle.SQUARE);
        assertEquals((b[2] - b[0]) * (b[3] - b[1]) - source.solidGeometry().getArea(),
                inverted.solidGeometry().getArea(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> InvertGerber.invert(source, -1, InvertGerber.JoinStyle.ROUND));
    }
}
