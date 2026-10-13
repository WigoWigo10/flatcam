package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class CncReviewDialogTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void realDialogThemesResizeCancelAndContinue(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var dialog = new CncReviewDialog(null, theme, CncPreflight.geometry("Board", "MM", CncPreflightTest.geometry()));
            try {
                var pane = dialog.getDialogPane();
                assertTrue(pane.getScene().getStylesheets().stream().anyMatch(url -> url.contains(theme.isDark() ? "dark" : "light")));
                for (double width : new double[]{460, 700, 900}) {
                    pane.resize(width, 650); pane.applyCss(); pane.layout();
                    assertTrue(pane.lookup("#cnc-review-tools").getLayoutBounds().getWidth() <= width);
                }
                var table = (TableView<?>) pane.lookup("#cnc-review-tools");
                assertEquals(2, table.getItems().size()); assertFalse(pane.lookupButton(CncReviewDialog.CONTINUE).isDisabled());
                if (Boolean.getBoolean("flatcam.tests.snapshots")) snapshot(pane, "cnc-review-" + theme + ".png");
                ((Button) pane.lookupButton(CncReviewDialog.CONTINUE)).fire();
                assertEquals(new CncReviewDialog.Decision(true, null), dialog.getResult());
            } finally { dialog.close(); }
            var cancel = new CncReviewDialog(null, theme, CncPreflight.drilling(CncPreflightTest.drilling()));
            try {
                ButtonType back = cancel.getDialogPane().getButtonTypes().getLast();
                ((Button) cancel.getDialogPane().lookupButton(back)).fire(); assertNull(cancel.getResult());
            } finally { cancel.close(); }
            return null;
        });
    }
    @Test void blockedReportCannotContinueAndEditLinkReturnsAnExactTarget() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = CncPreflightTest.geometry();
            var noChange = new GeometryGCodeParameters(3, .1, false, 1, 100, 10000, false);
            var dialog = new CncReviewDialog(null, ThemeOption.CLASSIC_DARK, CncPreflight.geometry("Board", "MM",
                    new GeometryCncToolPanel.Result(result.tools(), noChange, Map.of(), result.preprocessor(), Map.of())));
            try {
                var pane = dialog.getDialogPane(); pane.applyCss(); pane.layout();
                assertTrue(pane.lookupButton(CncReviewDialog.CONTINUE).isDisabled());
                var link = pane.lookupAll(".hyperlink").stream().filter(node ->
                        new CncPreflight.Target(-1, "cnc-tool-change").equals(node.getUserData())).findFirst().orElseThrow();
                ((Hyperlink) link).fire();
                assertEquals(new CncReviewDialog.Decision(false, new CncPreflight.Target(-1, "cnc-tool-change")), dialog.getResult());
            } finally { dialog.close(); }
            return null;
        });
    }
    @Test void geometryNavigationSelectsTheRequestedToolWithoutApplyingToAll() throws Exception {
        TerminalPanelTest.fx(() -> {
            var result = CncPreflightTest.geometry();
            var settings = new GeometryCncSettings(result.preprocessor(), null, Map.of(), result.parametersByTool());
            Parent root = (Parent) GeometryCncToolPanel.build("MM", CncPreflightTest.path(), result.tools(), result.parameters(), settings, ignored -> {}, () -> {});
            new Scene(root, 400, 1600); root.applyCss(); root.layout();
            assertTrue(CncReviewNavigation.focus(root, new CncPreflight.Target(1, "cnc-feed")));
            assertEquals("150.0", ((TextField) root.lookup("#cnc-feed")).getText());
            assertEquals(1, ((TableView<?>) root.lookup("#cnc-tools")).getSelectionModel().getSelectedIndex());
            assertTrue(CncReviewNavigation.focus(root, new CncPreflight.Target(-1, "cnc-change-xy")));
            assertFalse(CncReviewNavigation.focus(root, new CncPreflight.Target(-1, "missing")));
            return null;
        });
    }
    @Test void drillingEditUsesToolIdNotRowIndexAndRestoresTheExportSelection() throws Exception {
        TerminalPanelTest.fx(() -> {
            var initial = CncPreflightTest.drilling();
            var source = new DrillGCodeToolPanel.SourceCandidate(initial.source().item(), initial.source().image(),
                    initial.settingsByTool(), initial.cncSettings());
            var output = new AtomicReference<DrillGCodeToolPanel.Result>();
            Parent root = (Parent) DrillGCodeToolPanel.build(List.of(source), source, List::of, null, ignored -> {}, () -> {}, output::set, () -> {});
            new Scene(root, 400, 1600); root.applyCss(); root.layout();
            assertEquals(2, ((TableView<?>) root.lookup("#drill-tools")).getSelectionModel().getSelectedItems().size());
            assertTrue(CncReviewNavigation.focus(root, new CncPreflight.Target(7, "drill-feed-z")));
            assertTrue(((Label) root.lookup("#drill-review-notice")).getText().contains("2 ferramenta(s)"));
            ((TextField) root.lookup("#drill-feed-z")).setText("77");
            ((Button) root.lookup("#drill-generate")).fire();
            assertNotNull(output.get(), ((Label) root.lookup("#drill-error")).getText());
            assertEquals(List.of(7, 2), output.get().orderedToolIds());
            assertEquals(77, output.get().settingsByTool().get(7).feedRate());
            assertEquals(100, output.get().settingsByTool().get(2).feedRate());
            assertTrue(CncReviewNavigation.focus(root, new CncPreflight.Target(-1, "probe-change-z")));
            CncReviewNavigation.focus(root, new CncPreflight.Target(7, "drill-feed-z"));
            ((TableView<?>) root.lookup("#drill-tools")).getSelectionModel().clearAndSelect(0);
            assertEquals("", ((Label) root.lookup("#drill-review-notice")).getText());
            ((Button) root.lookup("#drill-generate")).fire();
            assertEquals(List.of(2), output.get().orderedToolIds(), "An explicit user selection replaces the saved review selection");
            return null;
        });
    }
    @Test void aShownDialogReturnsTheEditDecisionWithoutInvokingContinue() throws Exception {
        TerminalPanelTest.fx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            var dialog = new CncReviewDialog(null, ThemeOption.ICE_LIGHT, CncPreflight.drilling(CncPreflightTest.drilling()));
            try {
                dialog.show(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                var edit = dialog.getDialogPane().lookupAll(".hyperlink").stream()
                        .filter(node -> new CncPreflight.Target(7, "drill-feed-z").equals(node.getUserData()))
                        .findFirst().orElseThrow();
                ((Hyperlink) edit).fire();
                assertFalse(dialog.isShowing());
                assertEquals(new CncReviewDialog.Decision(false, new CncPreflight.Target(7, "drill-feed-z")), dialog.getResult());
            } finally { dialog.close(); }
            return null;
        });
    }
    private static void snapshot(javafx.scene.Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", java.nio.file.Path.of("target", name).toFile());
    }
}
