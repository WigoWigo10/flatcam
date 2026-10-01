package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolsDatabaseTest {
    @TempDir Path directory;
    private static JSONObject tool(String name, int target) {
        return new JSONObject().put("name", name).put("tooldia", 0.8).put("tool_type", "C2")
                .put("offset", "Custom").put("offset_value", -0.12)
                .put("vendor", new JSONObject().put("notes", List.of("original", "custom")))
                .put("data", new JSONObject().put("tool_target", target).put("tol_min", 0.7).put("tol_max", 0.9)
                        .put("ppname_g", "Toolchange_Probe_MACH3").put("unrecognized", JSONObject.NULL));
    }
    @Test void retainsIdsUnknownFieldsAndDefensiveCopiesDuringEditAndRoundTrip() throws Exception {
        ToolsDatabase bank = ToolsDatabase.fromJson(new JSONObject().put("7", tool("mill", 1)).put("2", tool("drill", 2)));
        assertEquals(List.of(2, 7), bank.ids());
        JSONObject edit = bank.entry(7); edit.put("name", "edited"); bank.update(7, edit); edit.put("tooldia", 999);
        JSONObject external = bank.toJson(); external.getJSONObject("7").put("name", "outside");
        assertEquals("edited", bank.entry(7).getString("name"));
        assertEquals(0.8, bank.entry(7).getDouble("tooldia"));
        Path file = directory.resolve("bank.FlatDB"); assertNull(bank.save(file));
        JSONObject roundTrip = ToolsDatabase.load(file).entry(7);
        assertEquals("Toolchange_Probe_MACH3", roundTrip.getJSONObject("data").getString("ppname_g"));
        assertTrue(roundTrip.getJSONObject("data").has("unrecognized"));
        assertTrue(bank.toJson().similar(ToolsDatabase.load(file).toJson()));
    }
    @Test void copiesAreIndependentAndNewIdsDoNotOverwriteSparseEntries() {
        ToolsDatabase bank = ToolsDatabase.fromJson(new JSONObject().put("1", tool("one", 0)).put("3", tool("three", 5)));
        int copy = bank.duplicate(1); assertEquals(4, copy);
        JSONObject entry = bank.entry(copy); entry.getJSONObject("data").put("tol_max", 5); bank.update(copy, entry);
        assertEquals(0.9, bank.entry(1).getJSONObject("data").getDouble("tol_max"));
        assertEquals("one_copy", bank.entry(copy).getString("name"));
        bank.remove(List.of(1, 4)); assertEquals(List.of(3), bank.ids());
        assertEquals(4, bank.add(tool("new", 3)));
    }
    @Test void overwritesRetainExactBackupsAndNoTemporaryFiles() throws Exception {
        Path file = directory.resolve("tools_db.FlatDB"); String original = "{\"1\":" + tool("old", 2) + "}\n";
        Files.writeString(file, original);
        ToolsDatabase bank = ToolsDatabase.load(file); JSONObject edit = bank.entry(1); edit.put("name", "new"); bank.update(1, edit);
        Path backup = bank.save(file); assertEquals(original, Files.readString(backup));
        assertEquals("new", ToolsDatabase.load(file).entry(1).getString("name"));
        Path second = bank.save(file); assertNotEquals(backup, second); assertEquals(original, Files.readString(backup));
        try (var files = Files.list(directory)) { assertEquals(3, files.count()); }
    }
    @Test void invalidEditsAreAtomicAndInvalidFilesDoNotLoad() throws Exception {
        ToolsDatabase bank = new ToolsDatabase(); int id = bank.add(tool("valid", 0));
        JSONObject bad = bank.entry(id); bad.put("tooldia", 0);
        assertThrows(IllegalArgumentException.class, () -> bank.update(id, bad)); assertEquals(0.8, bank.entry(id).getDouble("tooldia"));
        bad.put("tooldia", 0.8).getJSONObject("data").put("tol_min", 1);
        assertThrows(IllegalArgumentException.class, () -> bank.update(id, bad));
        Path invalid = directory.resolve("invalid.FlatDB"); Files.writeString(invalid, "[]");
        assertThrows(IOException.class, () -> ToolsDatabase.load(invalid));
        assertThrows(IllegalArgumentException.class, () -> ToolsDatabase.fromJson(new JSONObject().put("0", tool("bad", 0))));
    }
    @Test void editedBankRemainsUsableByAllExistingCamPickers() throws Exception {
        ToolsDatabase bank = new ToolsDatabase(); bank.add(tool("general", 0)); bank.add(tool("drill", 2)); bank.add(tool("iso", 3)); bank.add(tool("ncc", 5));
        assertEquals(2, LegacyToolsDatabase.drillTools(bank.toJson()).size());
        assertEquals(2, LegacyToolsDatabase.isolationTools(bank.toJson()).size());
        assertEquals(2, LegacyToolsDatabase.nccTools(bank.toJson()).size());
        Path file = directory.resolve("bank.FlatDB"); bank.save(file);
        assertEquals(LegacyToolsDatabase.nccTools(bank.toJson()), LegacyToolsDatabase.loadNccTools(file));
        assertEquals(LegacyToolsDatabase.drillTools(bank.toJson()), LegacyToolsDatabase.loadDrillTools(file));
        assertEquals(LegacyToolsDatabase.isolationTools(bank.toJson()), LegacyToolsDatabase.loadIsolationTools(file));
    }
    @Test void targetsAcceptLegacyEnglishNamesAndPreserveUnknownTargets() {
        assertEquals(2, ToolsDatabase.targetIndex("Drilling")); assertEquals(5, ToolsDatabase.targetIndex(5));
        assertEquals(-1, ToolsDatabase.targetIndex(2.5)); assertEquals(-1, ToolsDatabase.targetIndex("foreign"));
        JSONObject entry = tool("foreign", 0); entry.getJSONObject("data").put("tool_target", "foreign");
        ToolsDatabase bank = new ToolsDatabase(); int id = bank.add(entry);
        assertEquals("foreign", ToolsDatabase.targetLabel(bank.entry(id)));
    }
    @Test void rejectsOversizedFilesAndFailedSaveKeepsExistingDestination() throws Exception {
        Path large = directory.resolve("large.FlatDB");
        try (var channel = java.nio.channels.FileChannel.open(large, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(10_000_000); channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        assertThrows(IOException.class, () -> ToolsDatabase.load(large));
        ToolsDatabase bank = new ToolsDatabase(); bank.add(tool("valid", 0));
        Path existing = directory.resolve("destination"); Files.createDirectory(existing); Files.writeString(existing.resolve("sentinel"), "safe");
        assertThrows(IOException.class, () -> bank.save(existing));
        assertEquals("safe", Files.readString(existing.resolve("sentinel")));
    }
}
