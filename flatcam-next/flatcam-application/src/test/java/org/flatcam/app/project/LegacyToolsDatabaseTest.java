package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyToolsDatabaseTest {

    @TempDir Path tempDir;

    @Test
    void readsOnlyPythonNccToolsAndSettings() throws Exception {
        Path file = tempDir.resolve("tools_db.FlatDB");
        Files.writeString(file, """
                {
                  "1": {"name":"fine", "tooldia":0.3, "data":{
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
}
