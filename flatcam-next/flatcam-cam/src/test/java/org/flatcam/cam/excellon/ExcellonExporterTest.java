package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcellonExporterTest {

    private final ExcellonParser parser = new ExcellonParser();
    private final ExcellonExporter exporter = new ExcellonExporter();

    @Test
    void exportsCurrentDrillsAndSlotsInsteadOfOriginalSource() {
        ExcellonImage original = parser.parse(List.of(
                "M48", "METRIC", "T1C0.8", "T2C1.2", "%", "T1", "X1.0Y2.0", "M30"));
        ExcellonImage edited = ExcellonImage.of("MM", original.toolDiameters(),
                List.of(new ExcellonImage.Drill(1, -3.25, 4.125),
                        new ExcellonImage.Drill(2, 10.0, -8.0)),
                List.of(new ExcellonImage.Slot(2, 0.125, 1.5, 2.75, -3.625)),
                original.solidGeometry());

        String exported = exporter.export(edited);
        ExcellonImage reopened = parser.parse(exported.lines().toList());

        assertEquals("MM", reopened.units());
        assertEquals(edited.toolDiameters(), reopened.toolDiameters());
        assertEquals(edited.drills(), reopened.drills());
        assertEquals(edited.slots(), reopened.slots());
        assertFalse(exported.contains("X1.0Y2.0"), "old source hit must not be copied");
    }

    @Test
    void inchCoordinatesAndSubMicronValuesRoundTrip() {
        ExcellonImage image = ExcellonImage.of("IN", Map.of(7, 0.03125),
                List.of(new ExcellonImage.Drill(7, 0.00000025, -12.75)),
                List.of(), null);

        ExcellonImage reopened = parser.parse(exporter.export(image).lines().toList());

        assertEquals(image.toolDiameters(), reopened.toolDiameters());
        assertEquals(image.drills(), reopened.drills());
    }

    @Test
    void missingToolsAndNonFiniteValuesFailBeforeReplacingDestination(@TempDir Path temp) throws IOException {
        Path target = temp.resolve("drills.drl");
        Files.writeString(target, "previous content");
        ExcellonImage invalid = ExcellonImage.of("MM", Map.of(1, 0.8),
                List.of(new ExcellonImage.Drill(2, 0, 0)), List.of(), null);
        ExcellonImage infinite = ExcellonImage.of("MM", Map.of(1, 0.8),
                List.of(new ExcellonImage.Drill(1, Double.POSITIVE_INFINITY, 0)), List.of(), null);

        assertThrows(IllegalArgumentException.class, () -> exporter.write(invalid, target));
        assertThrows(IllegalArgumentException.class, () -> exporter.write(infinite, target));
        assertEquals("previous content", Files.readString(target));
    }

    @Test
    void writesAndReopensEditedFile(@TempDir Path temp) throws IOException {
        ExcellonImage image = ExcellonImage.of("MM", Map.of(1, 0.6),
                List.of(new ExcellonImage.Drill(1, 2, 3)),
                List.of(new ExcellonImage.Slot(1, 4, 5, 6, 7)), null);
        Path target = temp.resolve("edited.drl");

        exporter.write(image, target);

        ExcellonImage reopened = parser.parse(target);
        assertEquals(image.drills(), reopened.drills());
        assertEquals(image.slots(), reopened.slots());
        assertTrue(Files.readString(target).endsWith("M30\n"));
    }
}
