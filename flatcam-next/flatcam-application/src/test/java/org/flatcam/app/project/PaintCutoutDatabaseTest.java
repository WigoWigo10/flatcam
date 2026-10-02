package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.cutout.GapPattern;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class PaintCutoutDatabaseTest {
    @Test void paintComboIsPythonIndexFourNotNccIndexThree() throws Exception {
        var tools = LegacyToolsDatabase.paintTools(new JSONObject("""
                {"1":{"name":"paint","tooldia":0.4,"data":{"tool_target":4,
                "tools_paint_method":4,"tools_paint_overlap":35,"tools_paint_offset":0.1,"tools_paint_connect":false}},
                "2":{"tooldia":1,"data":{"tool_target":5}}}
                """));
        assertEquals(1, tools.size());
        assertEquals(NccMethod.COMBO, tools.getFirst().parameters().method());
        assertEquals(0.35, tools.getFirst().parameters().overlapFraction());
        assertFalse(tools.getFirst().parameters().connect());
    }
    @Test void laserLinesAndVAreExplicitlyRejected() {
        assertThrows(IOException.class, () -> LegacyToolsDatabase.paintTools(new JSONObject("""
                {"1":{"tooldia":1,"data":{"tool_target":4,"tools_paint_method":3}}}
                """)));
        assertThrows(IOException.class, () -> LegacyToolsDatabase.cutoutTools(new JSONObject("""
                {"1":{"tooldia":1,"tool_type":"V","data":{"tool_target":6}}}
                """)));
    }
    @Test void cutoutTransfersGapsAndMouseBites() throws Exception {
        var p = LegacyToolsDatabase.cutoutTools(new JSONObject("""
                {"1":{"tooldia":2,"data":{"tool_target":"Cutout","tools_cutout_gaps_ff":"2TB",
                "tools_cutout_gap_type":"mb","tools_cutout_mb_dia":0.8,"tools_cutout_mb_spacing":0.2}}}
                """)).getFirst();
        assertEquals(GapPattern.TWO_TB, p.parameters().gapPattern());
        assertEquals("mb", p.gapType()); assertEquals(0.8, p.biteDiameter()); assertEquals(0.2, p.biteSpacing());
        assertThrows(IOException.class, () -> LegacyToolsDatabase.cutoutTools(new JSONObject("""
                {"1":{"tooldia":2,"data":{"tool_target":6,"tools_cutout_gaps_ff":"unknown"}}}
                """)));
    }
}
