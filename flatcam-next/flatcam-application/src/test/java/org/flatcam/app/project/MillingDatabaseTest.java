package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.flatcam.cam.geometry.ToolProfile;

class MillingDatabaseTest {
    @Test void transfersOffsetAndCutoutDepthsWithoutDoubleCompensation() throws Exception {
        var root = new JSONObject("""
                {"1":{"tooldia":1,"offset":"Custom","offset_value":-0.2,"data":{"tool_target":0,
                "cutz":-2,"tools_cutout_z":-9,"multidepth":true,"depthperpass":0.4,
                "feedrate":210,"tools_cutout_gap_type":"bt","tools_cutout_gap_depth":-0.3}}}
                """);
        var milling = LegacyToolsDatabase.millingTools(root).getFirst().parameters();
        assertEquals(org.flatcam.cam.gcode.ToolPathOffset.CUSTOM, milling.offset());
        assertEquals(-0.2, milling.customOffset());
        var cutout = LegacyToolsDatabase.cutoutTools(root).getFirst();
        assertEquals(2, cutout.machining().cutDepth());
        assertTrue(cutout.machining().multiDepth());
        assertEquals(0.4, cutout.machining().depthPerPass());
        assertEquals(210, cutout.machining().feedRate());
        assertEquals(0.3, cutout.thinDepth());
        assertEquals(org.flatcam.cam.gcode.ToolPathOffset.PATH, cutout.machining().offset());
        root.getJSONObject("1").getJSONObject("data").put("tools_cutout_gap_depth", -2);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.cutoutTools(root));
        root.getJSONObject("1").put("offset_value", 0);
        assertThrows(IOException.class, () -> LegacyToolsDatabase.millingTools(root));
    }
    @Test void transfersSupportedFieldsAndFiltersTargets() throws Exception {
        JSONObject root = new JSONObject("""
                {"1":{"name":"V fine","tooldia":0.3,"tool_type":"V","data":{
                "tool_target":1,"vtipdia":0.1,"vtipangle":30,"cutz":-0.2,"travelz":3,
                "multidepth":true,"depthperpass":0.05,"feedrate":180,"feedrate_rapid":600,"spindlespeed":12000}},
                "2":{"tooldia":1,"data":{"tool_target":2}},
                "3":{"tooldia":2,"data":{"tool_target":"General"}}}
                """);
        var tools = LegacyToolsDatabase.millingTools(root);
        assertEquals(2, tools.size());
        var v = tools.stream().filter(t -> t.profile() == ToolProfile.V).findFirst().orElseThrow();
        assertEquals(180, v.parameters().feedRate());
        assertEquals(600, v.parameters().rapidFeedRate());
        assertEquals(0.1, v.tip().tipDiameter());
        assertTrue(v.parameters().multiDepth());
    }
    @Test void rejectsUnsafeCutAndImpossibleVWidth() {
        for (String data : new String[]{"\"cutz\":0", "\"cutz\":1", "\"vtipdia\":2"}) {
            JSONObject root = new JSONObject("{\"1\":{\"tooldia\":0.3,\"tool_type\":\"V\",\"data\":{\"tool_target\":1," + data + "}}}");
            assertThrows(IOException.class, () -> LegacyToolsDatabase.millingTools(root));
        }
    }
}
