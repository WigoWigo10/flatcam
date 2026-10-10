package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class DatabaseTransferTest {
    @Test void internalCutControlIsExplicitAndRejectsNegativeMargin() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = new java.util.concurrent.atomic.AtomicReference<CutoutToolPanel.Result>();
            var root = CutoutToolPanel.build("MM", (p, done, cancelled) -> false, () -> {}, result::set, () -> {});
            var internal = (CheckBox) root.lookup("#cutout-internal-cuts");
            assertFalse(internal.isSelected());
            assertTrue(internal.getAccessibleHelp().contains("não o cobre"));
            var buttons = ((javafx.scene.layout.VBox) root).getChildren().stream()
                    .filter(n -> n instanceof Button).map(n -> (Button)n).toList();
            var freeform = buttons.stream().filter(b -> b.getText().equals("Gerar (Free-form)")).findFirst().orElseThrow();
            var rectangular = buttons.stream().filter(b -> b.getText().equals("Gerar (Rectangular)")).findFirst().orElseThrow();
            internal.setSelected(true);
            assertTrue(rectangular.isDisabled());
            freeform.fire();
            assertNotNull(result.get()); assertTrue(result.get().cutoutParams().includeInternalCuts());
            result.set(null); ((TextField) root.lookup("#cutout-margin")).setText("-0.1");
            freeform.fire(); assertNull(result.get());
            assertTrue(((Label) root.lookup("#cutout-error")).getText().contains("margem nao negativa"));
            ((TextField) root.lookup("#cutout-margin")).setText("0.1");
            internal.setSelected(false); assertFalse(rectangular.isDisabled());
            freeform.fire(); assertFalse(result.get().cutoutParams().includeInternalCuts());
            return null;
        });
    }
    @Test void cutoutGapHelpExplainsPlacementWidthAndSafetyWithRichAccessibleTooltips() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var root = CutoutToolPanel.build("MM", (p, done, cancelled) -> false,
                    () -> {}, result -> {}, () -> {});
            var pattern = (ComboBox<?>) root.lookup("#cutout-gap-pattern");
            var width = (TextField) root.lookup("#cutout-gap-size");
            assertNull(pattern.getTooltip());
            assertNull(width.getTooltip());
            assertTrue(pattern.getAccessibleHelp().contains("deslocado pela margem"));
            assertTrue(pattern.getAccessibleHelp().contains("recusada"));
            assertTrue(width.getAccessibleHelp().contains("Zero desativa"));
            assertTrue(width.getAccessibleHelp().contains("Thin"));
            for (Control control : java.util.List.of(pattern, width)) {
                assertEquals(control.getAccessibleHelp(), control.getProperties().get(FluidTooltips.TEXT_KEY));
                assertNotNull(control.getProperties().get(FluidTooltips.CONTENT_KEY));
            }
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
    @Test void cncRowsKeepIndependentParametersAndApplyAllIsExplicit() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
            var result = new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var root = GeometryCncToolPanel.build("MM", path, List.of(new ToolGeometry(0.2,path),new ToolGeometry(0.4,path)), result::set, () -> {});
            var table = (TableView<?>) root.lookup("#cnc-tools");
            var feed = (TextField) root.lookup("#cnc-feed");
            feed.setText("120"); table.getSelectionModel().select(1); feed.setText("240");
            table.getSelectionModel().select(0); assertEquals("120", feed.getText());
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get()); assertEquals(120, result.get().parametersByTool().get(0).feedRate());
            assertEquals(240, result.get().parametersByTool().get(1).feedRate());
            ((Button) root.lookup("#cnc-apply-all")).fire();
            ((Button) root.lookup("#cnc-generate")).fire();
            assertTrue(result.get().parametersByTool().isEmpty());
            table.getSelectionModel().select(1); feed.setText("invalid"); table.getSelectionModel().select(0);
            result.set(null); ((Button) root.lookup("#cnc-generate")).fire(); assertNull(result.get());
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
    @Test void cutoutDatabaseTransferUpdatesRealControlsWithoutGenerating() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var db = LegacyToolsDatabase.cutoutTools(new org.json.JSONObject("""
                    {"1":{"tooldia":1.2,"data":{"tool_target":6,"tools_cutout_margin":0.4,
                    "cutz":-2,"multidepth":true,"depthperpass":0.4,"feedrate":210,
                    "tools_cutout_gaps_ff":"8","tools_cutout_gap_type":"bt","tools_cutout_gap_depth":-0.3,"tools_cutout_mb_dia":0.9,
                    "ppname_g":"Marlin","feedrate_rapid":850,"endxy":[2,3]}}}
                    """));
            var result = new java.util.concurrent.atomic.AtomicReference<CutoutToolPanel.Result>();
            var root = CutoutToolPanel.build("MM", (p, done, cancelled) -> false, () -> {}, () -> db, result::set, () -> {});
            ((Button) root.lookup("#cutout-db-load")).fire();
            ((Button) root.lookup("#cutout-db-apply")).fire();
            assertNull(result.get());
            var generate = ((javafx.scene.layout.VBox) root).getChildren().stream()
                    .filter(n -> n instanceof Button b && b.getText().equals("Gerar (Free-form)"))
                    .map(n -> (Button) n).findFirst().orElseThrow();
            generate.fire();
            assertNotNull(result.get());
            assertEquals(1.2, result.get().cutoutParams().toolDiameter());
            assertEquals(0.4, result.get().cutoutParams().margin());
            assertEquals(CutoutToolPanel.GapType.THIN, result.get().gapType());
            assertEquals(2, result.get().machining().cutDepth());
            assertEquals(0.4, result.get().machining().depthPerPass());
            assertEquals(210, result.get().machining().feedRate());
            assertEquals(db.getFirst().jobDefaults(), result.get().jobDefaults());
            assertEquals(GCodePreprocessor.MARLIN, result.get().jobDefaults().preprocessor());
            assertEquals(0.3, result.get().thinMachining().cutDepth());
            assertEquals(org.flatcam.cam.gcode.ToolPathOffset.PATH, result.get().machining().offset());
            ((ComboBox<CutoutToolPanel.GapType>) root.lookup("#cutout-gap-type")).setValue(CutoutToolPanel.GapType.M_BITES);
            generate.fire(); assertEquals(0.9, result.get().biteDiameter());
            ((ComboBox<CutoutToolPanel.GapType>) root.lookup("#cutout-gap-type")).setValue(CutoutToolPanel.GapType.THIN);
            result.set(null); ((TextField) root.lookup("#cutout-thin-z")).setText("-3");
            generate.fire(); assertNull(result.get());
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
    @Test void geometryTransferIsExplicitAndCarriesVTip() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
            var db = new LegacyToolsDatabase.MillingTool("V", 0.3, ToolProfile.V,
                    new GeometryGCodeParameters(2, 0.2, true, 0.05, 180, 9000, false)
                            .withCompensation(ToolPathOffset.CUSTOM, -0.2), new VTipSettings(0.1, 30));
            var result = new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var root = GeometryCncToolPanel.build("MM", path, List.of(), null, null, () -> List.of(db), result::set, () -> {});
            ((Button) root.lookup("#cnc-db-load")).fire();
            assertNotEquals("180.0", ((TextField) root.lookup("#cnc-feed")).getText());
            ((Button) root.lookup("#cnc-db-apply")).fire();
            assertEquals("180.0", ((TextField) root.lookup("#cnc-feed")).getText());
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(), ((Label) root.lookup("#cnc-error")).getText());
            assertEquals(ToolProfile.V, result.get().tools().getFirst().toolProfile());
            assertEquals(db.tip(), result.get().vTools().get(0));
            assertEquals(ToolPathOffset.CUSTOM, result.get().parameters().offset());
            assertEquals(-0.2, result.get().parameters().customOffset());
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
}
