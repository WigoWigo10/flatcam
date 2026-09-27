package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOperation;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.isolation.IsolationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyToolsDatabaseTest {

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
}
