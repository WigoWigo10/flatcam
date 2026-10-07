package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.isolation.IsolationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyToolsDatabaseTest {

    @Test void positiveDrillCutZIsNotSilentlyTurnedIntoACutBelowTheSurface() {
        var db = new org.json.JSONObject("""
                {"1":{"tooldia":1,"data":{"tool_target":2,"tools_drill_cutz":1}}}
                """);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.drillTools(db));
    }

    @TempDir Path tempDir;

    @Test
    void readsOnlyPythonNccToolsAndSettings() throws Exception {
        Path file = tempDir.resolve("tools_db.FlatDB");
        Files.writeString(file, """
                {
                  "1": {"name":"fine", "tooldia":0.3, "tool_type":"C2", "data":{
                    "tool_target":5, "tools_ncc_operation":"iso", "tools_ncc_method":1,
                    "tools_ncc_overlap":35, "tools_ncc_connect":false,
                    "tools_ncc_contour":true, "tools_ncc_offset_choice":true,
                    "tools_ncc_offset_value":0.12}},
                  "2": {"name":"mill", "tooldia":2, "data":{"tool_target":1}}
                }
                """);

        var tools = LegacyToolsDatabase.loadNccTools(file);

        assertEquals(1, tools.size());
        assertEquals("fine", tools.get(0).name());
        assertEquals(0.3, tools.get(0).diameter());
        assertEquals(ToolProfile.C2, tools.get(0).toolProfile());
        assertEquals(NccOperation.ISO, tools.get(0).operation());
        assertEquals(NccMethod.SEED, tools.get(0).settings().method());
        assertEquals(0.35, tools.get(0).settings().overlapFraction());
        assertEquals(0.12, tools.get(0).settings().copperOffset());
    }

    @Test
    void invalidNccRecordIsNotSilentlySubstituted() throws Exception {
        Path file = tempDir.resolve("invalid.FlatDB");
        Files.writeString(file, """
                {"1":{"tooldia":-1,"data":{"tool_target":5}}}
                """);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.loadNccTools(file));
    }

    @Test
    void generalToolsAreEligibleLikeInPythonPicker() throws Exception {
        Path file = tempDir.resolve("general.FlatDB");
        Files.writeString(file, """
                {"1":{"name":"general cutter","tooldia":0.5,"data":{"tool_target":0}}}
                """);
        assertEquals(1, LegacyToolsDatabase.loadNccTools(file).size());
    }

    @Test
    void vTipFromPythonDatabaseUsesIsolationOperation() throws Exception {
        Path file = tempDir.resolve("v-tip.FlatDB");
        Files.writeString(file, """
                {"1":{"name":"v-bit","tooldia":0.2,"tool_type":"V",
                      "data":{"tool_target":5,"tools_ncc_operation":"clear"}}}
                """);
        var tool = LegacyToolsDatabase.loadNccTools(file).get(0);
        assertEquals(ToolProfile.V, tool.toolProfile());
        assertEquals(NccOperation.ISO, tool.operation());
    }

    @Test
    void readsPythonIsolationToolsWithPerToolParameters() throws Exception {
        Path file = tempDir.resolve("isolation.FlatDB");
        Files.writeString(file, """
                {
                  "1":{"name":"v-bit","tooldia":0.1,"tool_type":"V","data":{
                    "tool_target":3,"tools_iso_passes":2,"tools_iso_overlap":12.5,
                    "tools_iso_isotype":"ext"}},
                  "2":{"name":"ncc","tooldia":0.5,"data":{"tool_target":5}}
                }
                """);
        var tools = LegacyToolsDatabase.loadIsolationTools(file);
        assertEquals(1, tools.size());
        assertEquals(ToolProfile.V, tools.get(0).toolProfile());
        assertEquals(2, tools.get(0).parameters().passes());
        assertEquals(0.125, tools.get(0).parameters().overlapFraction());
        assertEquals(IsolationType.EXTERIOR, tools.get(0).parameters().type());
    }

    @Test
    void rejectsInvalidIsolationToolInsteadOfChangingItsMeaning() throws Exception {
        Path file = tempDir.resolve("invalid-isolation.FlatDB");
        Files.writeString(file, """
                {"1":{"tooldia":0.1,"data":{"tool_target":3,"tools_iso_isotype":"unknown"}}}
                """);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.loadIsolationTools(file));
    }

    @Test
    void readsPythonDrillingParametersAndDiameterTolerance() throws Exception {
        Path file = tempDir.resolve("drilling.FlatDB");
        Files.writeString(file, """
                {
                  "1":{"name":"small drill","tooldia":0.8,"data":{
                    "tool_target":2,"tol_min":0.75,"tol_max":0.85,
                    "tools_drill_cutz":-1.5,"tools_drill_multidepth":true,
                    "tools_drill_depthperpass":0.4,"tools_drill_travelz":2.5,
                    "tools_drill_feedrate_z":250,"tools_drill_spindlespeed":12000,
                    "tools_drill_dwell":true,"tools_drill_dwelltime":1.25,
                    "tools_drill_offset":0.2}},
                  "2":{"name":"ncc","tooldia":0.5,"data":{"tool_target":5}}
                }
                """);

        var tools = LegacyToolsDatabase.loadDrillTools(file);

        assertEquals(1, tools.size());
        var drill = tools.get(0);
        assertEquals("small drill", drill.name());
        assertEquals(0.8, drill.diameter());
        assertEquals(true, drill.matchesDiameter(0.82));
        assertEquals(false, drill.matchesDiameter(0.9));
        assertEquals(1.7, drill.parameters().effectiveDepth());
        assertEquals(true, drill.parameters().multiDepth());
        assertEquals(0.4, drill.parameters().depthPerPass());
        assertEquals(12000, drill.parameters().spindleSpeedRpm());
        assertEquals(true, drill.parameters().dwell());
        assertEquals(1.25, drill.parameters().dwellSeconds());
    }

    @Test
    void invalidPythonDrillOffsetIsRejected() throws Exception {
        Path file = tempDir.resolve("invalid-drill.FlatDB");
        Files.writeString(file, """
                {"1":{"tooldia":0.8,"data":{"tool_target":2,
                    "tools_drill_cutz":-1.0,"tools_drill_offset":-1.0}}}
                """);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.loadDrillTools(file));
    }

    @Test
    void exactDrillDiameterWinsOverToleranceAndAmbiguityIsRejected() {
        var params = new DrillGCodeParameters(2, 1, 100, 0, false);
        var wide = new LegacyToolsDatabase.DrillTool("wide", 1.0, 0.7, 1.2, params);
        var exact = new LegacyToolsDatabase.DrillTool("exact", 0.8, 0, 0, params);
        assertEquals(exact, LegacyToolsDatabase.matchDrillTool(List.of(wide, exact), 0.8).orElseThrow());
        assertEquals(true, LegacyToolsDatabase.matchDrillTool(List.of(wide), 0.9).isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> LegacyToolsDatabase.matchDrillTool(List.of(wide, wide), 0.9));
    }
}
