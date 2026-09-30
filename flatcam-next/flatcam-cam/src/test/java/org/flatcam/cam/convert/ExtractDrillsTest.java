package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractDrillsTest {

    @TempDir
    Path directory;

    /** D10 = 2.0 round (two flashes), D11 = 3.0 round, D12 = 4 x 4 rectangle, D13 = 4 x 2 rectangle, D14 = obround 3 x 1. */
    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%",
                "%ADD10C,2.0*%", "%ADD11C,3.0*%", "%ADD12R,4.0X4.0*%", "%ADD13R,4.0X2.0*%", "%ADD14O,3.0X1.0*%",
                "D10*", "X100000Y100000D03*", "X200000Y100000D03*",
                "D11*", "X300000Y100000D03*",
                "D12*", "X400000Y100000D03*",
                "D13*", "X500000Y100000D03*",
                "D14*", "X600000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    private static ExtractDrills.Options options(ExtractDrills.Mode mode, boolean circular, boolean oblong, boolean square,
                                                 boolean rectangular) {
        return new ExtractDrills.Options(mode, 0.8, 0.5, 0.25, 0.1, 0.5, 0.3, 0.2, circular, oblong, square, rectangular,
                false);
    }

    @Test
    void fixedHolesGoIntoOneToolAtEveryChosenFlash() throws IOException {
        ExcellonImage drills = ExtractDrills.extract(board(), options(ExtractDrills.Mode.FIXED, true, false, false, false));
        assertEquals(3, drills.totalDrills());
        assertEquals(1, drills.toolDiameters().size());
        assertEquals(0.8, drills.toolDiameters().get(1), 1e-9);
    }

    @Test
    void ringModeLeavesTheAnnularRingAndSharesToolsOfEqualDiameter() throws IOException {
        ExcellonImage drills = ExtractDrills.extract(board(), options(ExtractDrills.Mode.RING, true, false, true, true));
        // round 2.0 - 2*0.25 = 1.5 (x2), round 3.0 - 0.5 = 2.5, square 4 - 1.0 = 3.0, rectangle min(4,2) - 0.6 = 1.4
        assertEquals(5, drills.totalDrills());
        assertEquals(4, drills.toolDiameters().size());
        assertEquals(java.util.Set.of(1.5, 2.5, 3.0, 1.4),
                drills.toolDiameters().values().stream().map(d -> Math.round(d * 1e4) / 1e4)
                        .collect(java.util.stream.Collectors.toSet()));
        int shared = drills.toolDiameters().entrySet().stream()
                .filter(e -> Math.abs(e.getValue() - 1.5) < 1e-9).findFirst().orElseThrow().getKey();
        assertEquals(2, drills.drills().stream().filter(d -> d.toolId() == shared).count());
    }

    @Test
    void proportionalModeTakesAShareOfTheSmallerSide() throws IOException {
        ExcellonImage drills = ExtractDrills.extract(board(), options(ExtractDrills.Mode.PROPORTIONAL, false, true, false, false));
        assertEquals(1, drills.totalDrills());
        assertEquals(0.5, drills.toolDiameters().get(1), 1e-9);
    }

    @Test
    void nothingMatchingIsReportedAndTooBigARingSkipsThePad() throws IOException {
        GerberImage board = board();
        assertThrows(IllegalArgumentException.class,
                () -> ExtractDrills.extract(board, options(ExtractDrills.Mode.FIXED, false, false, false, false)));
        ExtractDrills.Options tooThick = new ExtractDrills.Options(ExtractDrills.Mode.RING, 0.8, 0.5, 1.5, 0.1, 0.5, 0.3,
                0.2, true, false, false, false, false);
        // round 2.0 - 3.0 < 0 is skipped, round 3.0 - 3.0 = 0 is skipped: nothing left.
        assertThrows(IllegalArgumentException.class, () -> ExtractDrills.extract(board, tooThick));
    }
}
