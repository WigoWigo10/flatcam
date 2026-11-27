package org.flatcam.fx;

import static org.flatcam.fx.MainToolPersistenceTest.call;
import static org.flatcam.fx.MainToolPersistenceTest.field;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class CncMixedUnitPlotTest {
    @Test
    void inchProgramUsesMetricPlotOnImportAndReopen(@TempDir Path folder) throws Exception {
        Path gerber = Files.writeString(folder.resolve("board.gbr"),
                "%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,1*%\nD10*\nX254000Y0D03*\nM02*\n");
        String code = "G20\nG90\nG0 X1 Y0 Z1\nG1 Z-0.1 F10\nG1 X2 Y0\nM30\n";
        Path program = Files.writeString(folder.resolve("inch.nc"), code);
        var jobs = new JobExecutor(1);
        var window = TerminalPanelTest.fx(() -> {
            var main = new MainWindow(jobs);
            main.createScene();
            return main;
        });
        try {
            window.openGerber(gerber, "board");
            TerminalPanelTest.fx(() -> {
                call(window, "openGCodeQueue", new Class<?>[]{List.class, int.class}, List.of(program.toFile()), 0);
                return null;
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (TerminalPanelTest.fx(() -> field(window, "runningJob") != null)) {
                assertTrue(System.nanoTime() < deadline, "G-code import did not finish");
                Thread.sleep(10);
            }
            assertMetricPlot(window, code);
            Path file = folder.resolve("mixed.fcnproj");
            window.saveProject(file);
            window.openProject(file, true);
            assertMetricPlot(window, code);
            assertMetricRuleLimit(window);
            assertAlignmentDrills(window);
        } finally {
            jobs.shutdown();
            TerminalPanelTest.fx(() -> { window.disposeViewport(); return null; });
        }
    }

    private static void assertMetricRuleLimit(MainWindow window) throws Exception {
        TerminalPanelTest.fx(() -> {
            call(window, "openRulesCheckTool", new Class<?>[0]);
            var tab = (Tab) field(window, "toolTab");
            var form = ((ScrollPane) tab.getContent()).getContent();
            var traceLimit = (TextField) form.lookup(".text-field");
            assertEquals(ToolDefaults.text("rules.trace_size", true), traceLimit.getText());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void assertMetricPlot(MainWindow window, String code) throws Exception {
        TerminalPanelTest.fx(() -> {
            assertTrue(window.displayUnitsMetric());
            var tree = (TreeView<String>) field(window, "projectTree");
            var gerbers = (TreeItem<String>) field(window, "gerbersNode");
            var jobs = (TreeItem<String>) field(window, "cncJobsNode");
            tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            tree.getSelectionModel().clearSelection();
            tree.getSelectionModel().select(gerbers.getChildren().getFirst());
            tree.getSelectionModel().select(jobs.getChildren().getFirst());
            assertEquals(0, window.measureMinimumDistance().distance(), 1e-9);
            var plot = (PlotAreaView) field(window, "plotAreaView");
            double right = plot.visibleSelectableLayers().stream()
                    .mapToDouble(layer -> layer.geometry().getEnvelopeInternal().getMaxX()).max().orElseThrow();
            assertEquals(50.8, right, .1, "Plot geometry, not just measurements, must use MM");
            var snapshot = (ProjectFile) call(window, "snapshotProject", new Class<?>[0]);
            assertEquals(code, snapshot.cncJobs().getFirst().gcode());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void assertAlignmentDrills(MainWindow window) throws Exception {
        TerminalPanelTest.fx(() -> {
            var tree = (TreeView<String>) field(window, "projectTree");
            var gerbers = (TreeItem<String>) field(window, "gerbersNode");
            tree.getSelectionModel().clearSelection();
            tree.getSelectionModel().select(gerbers.getChildren().getFirst());
            call(window, "openDoubleSidedTool", new Class<?>[0]);
            var tab = (Tab) field(window, "toolTab");
            var form = ((ScrollPane) tab.getContent()).getContent();
            ((TextArea) form.lookup(".text-area")).setText("(25.4, 2)");
            form.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals(I18n.text("ui.b7474bbdaf5e")))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            var images = (Map<TreeItem<String>, ExcellonImage>) field(window, "excellonByItem");
            var generated = images.values().stream().findFirst().orElseThrow();
            assertEquals("MM", generated.units());
            assertEquals(3.125, generated.toolDiameters().get(1), 1e-9);
            return null;
        });
    }
}
