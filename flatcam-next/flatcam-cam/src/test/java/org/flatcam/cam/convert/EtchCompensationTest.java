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

class EtchCompensationTest {

    @TempDir
    Path directory;

    private GerberImage board() throws IOException {
        Path file = directory.resolve("board.gbr");
        Files.writeString(file, String.join(System.lineSeparator(), "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2.0X2.0*%",
                "D10*", "X100000Y100000D03*", "M02*", ""));
        return new GerberParser().parse(file);
    }

    @Test
    void offsetsFollowPythonsRatiosAndTheGerbersUnits() {
        assertEquals(0.018 * 4, EtchCompensation.offsetFromFactor(18, 0.25, "MM"), 1e-9);
        assertEquals(0.018 / 0.33, EtchCompensation.offsetFromEtchant(18, EtchCompensation.Etchant.CUCL2, "MM"), 1e-9);
        assertEquals(18 / 25400.0, EtchCompensation.fromMicrons(18, "IN"), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> EtchCompensation.offsetFromFactor(18, 0, "MM"));
    }

    @Test
    void positiveOffsetGrowsAndNegativeShrinksTheCopper() throws IOException {
        GerberImage board = board();
        GerberImage grown = EtchCompensation.compensate(board, 0.1);
        assertEquals(4 + 8 * 0.1 + Math.PI * 0.01, grown.solidGeometry().getArea(), 0.01);
        assertTrue(grown.solidGeometry().getArea() > board.solidGeometry().getArea());
        GerberImage shrunk = EtchCompensation.compensate(board, -0.1);
        assertEquals(1.8 * 1.8, shrunk.solidGeometry().getArea(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> EtchCompensation.compensate(board, 0));
    }
}
