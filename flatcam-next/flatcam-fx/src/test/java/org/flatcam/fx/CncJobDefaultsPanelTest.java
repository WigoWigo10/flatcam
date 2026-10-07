package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.app.project.*;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

/** Real form controls, no visible windows or writes to user preferences. */
@EnabledOnOs(OS.WINDOWS)
class CncJobDefaultsPanelTest {
    private static CncJobDefaults defaults(String json) { return CncJobDefaults.fromLegacy(new JSONObject(json), false); }
    private static TextField field(Node root, String id) { return (TextField) root.lookup("#" + id); }
    private static Button button(Node root, String id) { return (Button) root.lookup("#" + id); }
    private static void layout(Node root) { new Scene((Parent) root, 410, 1600); root.applyCss(); ((Parent) root).layout(); }
    private static Geometry path() {
        return new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,0)});
    }
    @Test void commonSuggestionsFillPositionsButConflictsBlockUntilExplicitReview() throws Exception {
        TerminalPanelTest.fx(() -> {
            var path = path(); var tools = List.of(new ToolGeometry(.3,path),new ToolGeometry(.5,path));
            var a = defaults("{ppname_g:'Marlin',feedrate_rapid:700,startz:12,endz:0,endxy:[4,5],toolchange:true,toolchangez:15,toolchangexy:[1,2]}");
            var b = defaults("{ppname_g:'default',feedrate_rapid:900,startz:12,endz:0,endxy:[4,5],toolchange:true,toolchangez:15,toolchangexy:[1,2]}");
            var p = new GeometryGCodeParameters(3,.1,false,1,100,10000,true,300);
            var other = new GeometryGCodeParameters(3,.2,false,1,200,10000,true,300);
            var settings = new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE,null,Map.of(),Map.of(1,other),ToolProfile.C1,Map.of(0,a,1,b));
            var submitted = new AtomicReference<GeometryCncToolPanel.Result>();
            var root = GeometryCncToolPanel.build("MM",path,tools,p,settings,submitted::set,() -> {});
            layout(root);
            assertEquals("12.0",field(root,"cnc-start-z").getText());
            assertEquals("4.0,5.0",field(root,"cnc-end-xy").getText());
            assertEquals("300.0",field(root,"cnc-rapid-feed").getText(),"conflicting field is not arbitrarily chosen");
            assertTrue(((Label) root.lookup("#cnc-common-db-notice")).getText().contains("[1, 2]"));
            button(root,"cnc-generate").fire(); assertNull(submitted.get());
            assertTrue(((Label) root.lookup("#cnc-error")).getText().contains("Conflito"));
            ((ComboBox<GCodePreprocessor>) root.lookup("#cnc-preprocessor")).setValue(GCodePreprocessor.MARLIN);
            field(root,"cnc-rapid-feed").setText("800"); button(root,"cnc-common-db-confirm").fire();
            button(root,"cnc-generate").fire(); assertNotNull(submitted.get(),((Label) root.lookup("#cnc-error")).getText());
            var result = submitted.get(); assertEquals(GCodePreprocessor.MARLIN,result.preprocessor());
            assertEquals(800,result.parameters().rapidFeedRate()); assertEquals(12.,result.parameters().jobOptions().startZ());
            assertEquals(200,result.parametersByTool().get(1).feedRate());
            var job = GCodeGenerator.generateGeometryCncJob("MM",result.tools(),result.parameters(),result.vTools(),result.parametersByTool(),
                    org.flatcam.cam.CancellationToken.none(),result.preprocessor());
            assertFalse(job.cutGeometry().isEmpty()); assertTrue(job.gcode().contains("F800"));
            return null;
        });
    }
    @Test void databaseAppliesExplicitNoneAndPreservesAbsentFieldsAndDisabledRapidDraft() throws Exception {
        TerminalPanelTest.fx(() -> {
            var p = new GeometryGCodeParameters(.12,.04,false,1,4,100,false,30)
                    .withJobOptions(new GeometryJobOptions(.8,.02,null,null,null,null,null));
            var db = LegacyToolsDatabase.millingTools(new JSONObject("""
                    {"1":{"tooldia":0.03,"data":{"tool_target":1,"cutz":-0.04,"travelz":0.12,
                    "feedrate":4,"startz":"None","ppname_g":"GRBL_11_no_M6","feedrate_rapid":40}}}
                    """));
            var submitted = new AtomicReference<GeometryCncToolPanel.Result>();
            var root = GeometryCncToolPanel.build("IN",path(),List.of(),p,null,() -> db,submitted::set,() -> {});
            button(root,"cnc-db-load").fire(); button(root,"cnc-db-apply").fire();
            layout(root);
            assertEquals("None",field(root,"cnc-start-z").getText());
            assertEquals("0.02",field(root,"cnc-end-z").getText());
            assertTrue(field(root,"cnc-rapid-feed").isDisabled());
            button(root,"cnc-generate").fire(); assertNotNull(submitted.get(),((Label) root.lookup("#cnc-error")).getText());
            assertEquals(40,submitted.get().parameters().rapidFeedRate()); assertNull(submitted.get().parameters().jobOptions().startZ());
            assertEquals(.02,submitted.get().parameters().jobOptions().endZ());
            return null;
        });
    }
    @Test void drillingResolvesOnlySelectedToolsAndClearsImportsOnSourceSwitch() throws Exception {
        TerminalPanelTest.fx(() -> {
            var image = new ExcellonParser().parse(List.of("M48","METRIC","T1C0.8","T2C1.0","%","T1","X1.0Y0.0","T2","X2.0Y0.0","M30"));
            var source = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("holes"),image,Map.of());
            var clean = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("clean"),image,Map.of());
            var db = LegacyToolsDatabase.drillTools(new JSONObject("""
                    {"1":{"tooldia":0.8,"data":{"tool_target":2,"tools_drill_ppname_e":"Marlin",
                    "tools_drill_feedrate_rapid":700,"tools_drill_toolchange":true,"tools_drill_endz":"None"}},
                    "2":{"tooldia":1.0,"data":{"tool_target":2,"tools_drill_ppname_e":"default",
                    "tools_drill_feedrate_rapid":900,"tools_drill_toolchange":true,"tools_drill_endz":"None"}}}
                    """));
            var submitted = new AtomicReference<DrillGCodeToolPanel.Result>();
            var root = DrillGCodeToolPanel.build(List.of(source,clean),source,() -> db,submitted::set,() -> {});
            button(root,"drill-db-search").fire(); button(root,"drill-generate").fire(); assertNull(submitted.get());
            assertTrue(((Label) root.lookup("#drill-error")).getText().contains("Conflito"));
            var table = (TableView<?>) root.lookup("#drill-tools"); table.getSelectionModel().clearAndSelect(0);
            assertEquals("700.0",field(root,"drill-rapid-feed").getText());
            button(root,"drill-generate").fire(); assertNotNull(submitted.get(),((Label) root.lookup("#drill-error")).getText());
            assertEquals(GCodePreprocessor.MARLIN,submitted.get().preprocessor()); assertEquals(List.of(1),submitted.get().orderedToolIds());
            assertEquals(submitted.get().settingsByTool().get(1).safeZ(),submitted.get().options().endMoveZ());
            table.getSelectionModel().selectAll(); submitted.set(null); button(root,"drill-generate").fire(); assertNull(submitted.get());
            field(root,"drill-rapid-feed").setText("800"); button(root,"drill-common-db-confirm").fire();
            button(root,"drill-generate").fire(); assertNotNull(submitted.get()); assertEquals(800,submitted.get().options().rapidFeedRate());
            ((ComboBox<DrillGCodeToolPanel.SourceCandidate>) root.lookup("#drill-source")).setValue(clean);
            assertEquals("",((Label) root.lookup("#drill-common-db-notice")).getText());
            assertEquals("None",field(root,"drill-start-z").getText());
            return null;
        });
    }
}
