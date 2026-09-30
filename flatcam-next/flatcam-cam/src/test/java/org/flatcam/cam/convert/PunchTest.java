package org.flatcam.cam.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PunchTest {

    @TempDir
    Path directory;

    /** D10 = 2.0 round pads at x=10 and x=20; D11 = 3.0 round pad at x=30. */
    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%",
                "%ADD10C,2.0*%", "%ADD11C,3.0*%",
                "D10*", "X100000Y100000D03*", "X200000Y100000D03*",
                "D11*", "X300000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    private static ExtractDrills.Options fixed(double diameter) {
        return new ExtractDrills.Options(ExtractDrills.Mode.FIXED, diameter, 0.5, 0.2, 0.2, 0.2, 0.2, 0.2, true, false,
                false, false, false);
    }

    @Test
    void fixedHolesRemoveCopperOfTheChosenPadsOnly() throws IOException {
        GerberImage board = board();
        double before = board.solidGeometry().getArea();
        GerberImage punched = Punch.bySize(board, fixed(0.5), Set.of("10"));
        double removed = 2 * Math.PI * 0.25 * 0.25;
        assertEquals(before - removed, punched.solidGeometry().getArea(), 0.02);
    }

    @Test
    void fixedHoleAsBigAsThePadFails() throws IOException {
        GerberImage board = board();
        assertThrows(IllegalArgumentException.class, () -> Punch.bySize(board, fixed(2.0), Set.of("10")));
    }

    @Test
    void excellonDrillsPunchOnlyWhereTheyLandInsideAPad() throws IOException {
        GerberImage board = board();
        ExcellonImage drills = ExcellonImage.of("MM", Map.of(1, 1.0),
                List.of(new ExcellonImage.Drill(1, 10, 10), new ExcellonImage.Drill(1, 50, 50)), List.of(), null);
        double before = board.solidGeometry().getArea();
        GerberImage punched = Punch.byExcellon(board, drills, Set.of("10", "11"));
        assertEquals(before - Math.PI * 0.25, punched.solidGeometry().getArea(), 0.02);
        assertTrue(punched.shapes().size() == board.shapes().size() + 1);
        ExcellonImage outside = ExcellonImage.of("MM", Map.of(1, 1.0), List.of(new ExcellonImage.Drill(1, 50, 50)),
                List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> Punch.byExcellon(board, outside, Set.of("10")));
    }
}
