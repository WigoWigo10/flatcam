package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolDefaultsTest {

    @BeforeEach
    @AfterEach
    void freshStore() {
        ToolDefaults.useStore(ToolDefaults.memoryStore());
    }

    @Test
    void factoryValuesDependOnTheUnitsOnlyForLengths() {
        assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
        assertEquals("0.094", ToolDefaults.text("cutout.tooldia", false));
        assertEquals(-1.7, ToolDefaults.number("cutout.cutz", true));
        assertTrue(ToolDefaults.flag("cutout.multidepth"));
        assertEquals("FOUR", ToolDefaults.choice("cutout.gaps"));
        assertEquals(org.flatcam.cam.cutout.GapPattern.FOUR,
                ToolDefaults.choice("cutout.gaps", org.flatcam.cam.cutout.GapPattern.class));
        assertEquals("20", ToolDefaults.text("paint.overlap", true));
        assertEquals("20", ToolDefaults.text("paint.overlap", false));
        assertThrows(IllegalArgumentException.class, () -> ToolDefaults.text("no.such.key", true));
    }

    @Test
    void aSavedValueIsKeptPerUnitsAndADecimalCommaIsAccepted() {
        ToolDefaults.set("cutout.tooldia", true, " 3,175 ");
        assertEquals("3.175", ToolDefaults.text("cutout.tooldia", true));
        assertEquals("0.094", ToolDefaults.text("cutout.tooldia", false), "inches keep their own value");
        ToolDefaults.set("cutout.multidepth", true, "FALSE");
        assertFalse(ToolDefaults.flag("cutout.multidepth"));
        ToolDefaults.set("cutout.gaps", true, "EIGHT");
        assertEquals("EIGHT", ToolDefaults.choice("cutout.gaps"));
    }

    @Test
    void invalidValuesAreRefusedWithTheNameOfTheSetting() {
        IllegalArgumentException number = assertThrows(IllegalArgumentException.class,
                () -> ToolDefaults.set("cutout.tooldia", true, "abc"));
        assertTrue(number.getMessage().contains("Cutout") && number.getMessage().contains("número"));
        assertThrows(IllegalArgumentException.class, () -> ToolDefaults.set("cutout.gaps", true, "SEVEN"));
        assertThrows(IllegalArgumentException.class, () -> ToolDefaults.set("cutout.multidepth", true, "maybe"));
        assertThrows(IllegalArgumentException.class, () -> ToolDefaults.set("cutout.tooldia", true, "NaN"));
        assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
    }

    @Test
    void aCorruptStoredValueFallsBackToTheFactoryOne() {
        ToolDefaults.Store store = ToolDefaults.memoryStore();
        store.put("cutout.tooldia@mm", "garbage");
        store.put("cutout.gaps", "GONE");
        ToolDefaults.useStore(store);
        assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
        assertEquals("FOUR", ToolDefaults.choice("cutout.gaps"));
    }

    @Test
    void resetAndExportImportRoundTrip() {
        ToolDefaults.set("cutout.tooldia", true, "3.0");
        ToolDefaults.set("cutout.tooldia", false, "0.125");
        ToolDefaults.set("paint.connect", true, "false");
        Properties exported = ToolDefaults.export();
        assertEquals("3.0", exported.getProperty("cutout.tooldia@mm"));
        assertEquals("0.125", exported.getProperty("cutout.tooldia@in"));
        assertEquals("false", exported.getProperty("paint.connect"));
        int exportedCount = exported.size();

        ToolDefaults.resetAll();
        assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
        assertTrue(ToolDefaults.flag("paint.connect"));

        exported.setProperty("unknown.key", "1");
        exported.setProperty("cutout.margin@mm", "not a number");
        exported.setProperty("paint.overlap@mm", "30");   // a unitless setting written with units: not ours
        int[] counts = ToolDefaults.importFrom(exported);
        assertEquals(3, counts[1]);
        assertEquals(exportedCount - 1, counts[0]);
        assertEquals("3.0", ToolDefaults.text("cutout.tooldia", true));
        assertEquals("0.125", ToolDefaults.text("cutout.tooldia", false));
        assertFalse(ToolDefaults.flag("paint.connect"));
        assertEquals("0.1", ToolDefaults.text("cutout.margin", true));
    }

    @Test
    void savingTheFactoryValueClearsTheStoredOne() {
        ToolDefaults.Store store = ToolDefaults.memoryStore();
        ToolDefaults.useStore(store);
        ToolDefaults.set("cutout.tooldia", true, "3.0");
        assertEquals("3.0", store.get("cutout.tooldia@mm"));
        ToolDefaults.set("cutout.tooldia", true, "2.4");
        assertNull(store.get("cutout.tooldia@mm"));
        assertArrayEquals(new int[] {0, 0}, ToolDefaults.importFrom(new Properties()));
    }

    @Test
    void everySettingHasAToolALabelAndValidFactoryValues() {
        for (ToolDefaults.Setting setting : ToolDefaults.settings()) {
            assertFalse(setting.tool().isBlank(), setting.key());
            assertFalse(setting.label().isBlank(), setting.key());
            // Saving the factory value back must be accepted in both unit systems.
            ToolDefaults.set(setting.key(), true, setting.factory(true));
            ToolDefaults.set(setting.key(), false, setting.factory(false));
        }
    }
}
