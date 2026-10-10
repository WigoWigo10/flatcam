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
        ToolDefaults.useDisplayUnits(() -> true);
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

    @Test
    void millimetreDefaultsHaveTheSameLengthInInches() {
        assertEquals("0.0394", ToolDefaults.text("fiducials.size", false));
        assertEquals("7.874", ToolDefaults.text("panelize.limitwidth", false));
        assertEquals("0.0", ToolDefaults.text("corners.margin", false));
        assertEquals("0.123", ToolDefaults.text("twosided.drilldia", false));
        // A count or a percentage is the same in both.
        assertEquals("2", ToolDefaults.text("panelize.columns", false));
        assertEquals("80", ToolDefaults.text("punch.factor", false));
    }

    @Test
    void panelsWithoutAnObjectUseTheUnitsTheApplicationShows() {
        assertEquals("1.0", ToolDefaults.text("fiducials.size"));
        ToolDefaults.useDisplayUnits(() -> false);
        assertEquals("0.0394", ToolDefaults.text("fiducials.size"));
        ToolDefaults.set("fiducials.size", false, "0.05");
        assertEquals("0.05", ToolDefaults.text("fiducials.size"));
        ToolDefaults.useDisplayUnits(() -> true);
        assertEquals("1.0", ToolDefaults.text("fiducials.size"));
    }

    @Test
    void everyDesignRuleHasItsDefaultAndPythonsValue() {
        var python = org.flatcam.cam.analysis.RulesCheck.defaults();
        for (var rule : org.flatcam.cam.analysis.RulesCheck.Rule.values()) {
            String key = "rules." + rule.name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(python.get(rule).value(), ToolDefaults.number(key, true), rule.name());
            assertEquals(python.get(rule).enabled(), ToolDefaults.flag(key + ".enabled"), rule.name());
        }
    }

    @Test
    void cncDefaultsKeepThePanelsValuesAndEveryPreprocessorCanBeChosen() {
        assertEquals("-1.7", ToolDefaults.text("drilling.cutz", true));
        assertEquals("-0.07", ToolDefaults.text("drilling.cutz", false));
        assertEquals("0.8", ToolDefaults.text("geometry.tooldia", true));
        assertEquals("0.0315", ToolDefaults.text("milling.tooldia", false));
        var preprocessors = org.flatcam.cam.gcode.GCodePreprocessor.values();
        assertEquals(preprocessors.length, ToolDefaults.setting("cnc.preprocessor").choices().size());
        for (var preprocessor : preprocessors) {
            ToolDefaults.set("cnc.preprocessor", true, preprocessor.name());
            assertEquals(preprocessor, ToolDefaults.choice("cnc.preprocessor",
                    org.flatcam.cam.gcode.GCodePreprocessor.class));
        }
    }
}
