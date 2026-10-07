package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;

class CncJobDefaultsTest {
    @TempDir Path directory;
    @Test void explicitFieldsNormalizeWithoutInventingAbsentDefaults() {
        var defaults = CncJobDefaults.fromLegacy(new JSONObject("""
                {"ppname_g":"GRBL_11_no_M6", "feedrate_rapid":850, "toolchange":true,
                 "startz":null,"endz":0,"endxy":[12.5,-4],"toolchangez":10,"toolchangexy":"(1, 2)"}
                """), false);
        assertEquals(GCodePreprocessor.GRBL_11_NO_M6, defaults.preprocessor());
        assertEquals("850.0", defaults.value(CncJobDefaults.Field.RAPID_FEED));
        var positions = defaults.positions(new GeometryJobOptions(7.,9.,4.,5.,20.,3.,4.));
        assertNull(positions.startZ()); assertEquals(0., positions.endZ());
        assertEquals(12.5, positions.endX()); assertEquals(-4., positions.endY());
        assertEquals(10., positions.toolChangeZ()); assertEquals(1., positions.toolChangeX());
        assertEquals(defaults, CncJobDefaults.fromJson(defaults.toJson()));
        assertTrue(CncJobDefaults.fromLegacy(new JSONObject(), false).isEmpty());
        var absent = CncJobDefaults.fromLegacy(new JSONObject().put("feedrate_rapid", 0), false);
        assertEquals(7., absent.positions(new GeometryJobOptions(7.,null,null,null,null,null,null)).startZ());
    }
    @ParameterizedTest @CsvSource({"ppname_g,not-a-controller", "feedrate_rapid,-1", "feedrate_rapid,NaN",
            "toolchange,maybe", "startz,-2", "endz,Infinity", "endxy,1", "toolchangez,0", "toolchangexy,[1;NaN]"})
    void unsafeValuesAreNotSilentlyDefaulted(String key, String value) {
        assertThrows(RuntimeException.class, () -> CncJobDefaults.fromLegacy(new JSONObject().put(key, value), false));
    }
    @Test void conflictsAreFieldSpecificCanonicalAndOrderIndependent() {
        var a = CncJobDefaults.fromLegacy(new JSONObject().put("endz", "0").put("ppname_g", "Marlin").put("startz", "None"), false);
        var b = CncJobDefaults.fromLegacy(new JSONObject().put("endz", "0.0").put("ppname_g", "default").put("startz", "None"), false);
        var result = CncJobDefaults.resolve(Map.of(0, a, 1, b, 2, CncJobDefaults.EMPTY));
        assertEquals(Map.of(CncJobDefaults.Field.PREPROCESSOR, List.of(0,1)), result.conflicts());
        assertNull(result.agreed().preprocessor()); assertEquals("0.0", result.agreed().value(CncJobDefaults.Field.END_Z));
        assertEquals("None", result.agreed().value(CncJobDefaults.Field.START_Z));
        assertEquals(result, CncJobDefaults.resolve(Map.of(1, b, 0, a)));
    }
    @Test void nativeRoundTripKeepsUnresolvedSuggestionsAndPythonExportDoesNotDropThem() throws Exception {
        var a = CncJobDefaults.fromLegacy(new JSONObject().put("ppname_g", "Marlin"), false);
        var b = CncJobDefaults.fromLegacy(new JSONObject().put("ppname_g", "default"), false);
        var settings = new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE, null, Map.of(), Map.of(), ToolProfile.C1, Map.of(0,a,1,b));
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
        var p = new GeometryGCodeParameters(3,.1,false,1,100,100,false);
        var tools = List.of(new ToolGeometry(.3,path), new ToolGeometry(.5,path));
        var entry = new ProjectFile.GeometryEntry("DB", "", "MM", path, true, tools, null,null,true,p,settings);
        var project = new ProjectFile(List.of(),List.of(),List.of(entry),List.of());
        Path file = directory.resolve("pending.fcnproj"); ProjectFileIO.save(project,file);
        assertEquals(settings, ProjectFileIO.load(file).geometries().getFirst().cncSettings());
        Path legacy = directory.resolve("pending.FlatPrj");
        assertThrows(java.io.IOException.class, () -> PythonProjectWriter.save(project,legacy)); assertFalse(Files.exists(legacy));
        var root = ProjectFileIO.toJson(project);
        var jsonSettings = root.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0).getJSONObject("cncSettings");
        jsonSettings.remove("jobDefaultsByTool"); ProjectFileIO.writeRoot(root,file,false);
        assertTrue(ProjectFileIO.load(file).geometries().getFirst().cncSettings().jobDefaultsByTool().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ProjectFile.GeometryEntry("bad", "", "MM", path,true,List.of(),null,null,true,p,settings));
    }
    @Test void drillingProjectionUsesPrefixedFieldsAndRejectsLaser() throws Exception {
        var data = new JSONObject().put("tool_target",2).put("tools_drill_ppname_e","Marlin")
                .put("tools_drill_feedrate_rapid",600).put("tools_drill_endxy", "1;2");
        var root = new JSONObject().put("7",new JSONObject().put("tooldia",.8).put("data",data));
        var tool = LegacyToolsDatabase.drillTools(root).getFirst();
        assertEquals(GCodePreprocessor.MARLIN, tool.jobDefaults().preprocessor());
        assertEquals("1.0,2.0", tool.jobDefaults().value(CncJobDefaults.Field.END_XY));
        data.put("tools_drill_ppname_e", "GRBL_laser");
        assertThrows(java.io.IOException.class, () -> LegacyToolsDatabase.drillTools(root));
    }
    @Test void camCommonFieldsCanExistWithoutImportingUnspecifiedCuttingParameters() throws Exception {
        var root = new JSONObject("""
                {"1":{"tooldia":0.3,"data":{"tool_target":3,"ppname_g":"Marlin","feedrate_rapid":800}}}
                """);
        var machining = LegacyToolsDatabase.isolationTools(root).getFirst().machining();
        assertNotNull(machining); assertNull(machining.parameters());
        assertEquals(GCodePreprocessor.MARLIN, machining.jobDefaults().preprocessor());
    }
}
