package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.ProbeToolChangeParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class Mach3ProbePanelTest {
    private static void onFx(Runnable action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<GCodePreprocessor> profiles(Node root, String id) {
        return (ComboBox<GCodePreprocessor>) root.lookup(id);
    }

    private static TextField field(Node root, String id) { return (TextField) root.lookup(id); }
    private static CheckBox check(Node root, String id) { return (CheckBox) root.lookup(id); }

    @Test
    void geometryRequiresConfirmationValidatesInputsAndRestoresPreviousChangeChoice() throws Exception {
        onFx(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
            var restored = new ProbeToolChangeParameters(15, -4, 40, 0.5, 7.0, 11.0);
            var defaults = new GeometryGCodeParameters(3, 1, false, 1, 100, 9000, false, 0, restored);
            AtomicReference<GeometryCncToolPanel.Result> submitted = new AtomicReference<>();
            Node root = GeometryCncToolPanel.build("MM", path, List.of(), defaults, submitted::set, () -> {});
            var profiles = profiles(root, "#cnc-preprocessor");
            var change = check(root, "#cnc-tool-change");
            var confirm = check(root, "#probe-confirm");
            var generate = (Button) root.lookup("#cnc-generate");
            var error = (Label) root.lookup("#cnc-error");
            assertFalse(root.lookup("#probe-settings").isManaged());
            assertFalse(change.isSelected());
            profiles.setValue(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3);
            assertTrue(root.lookup("#probe-settings").isManaged());
            assertTrue(change.isSelected());
            assertTrue(change.isDisabled());
            change.setSelected(false);
            assertTrue(change.isSelected());
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("Confirme"));
            confirm.setSelected(true);
            generate.fire();
            assertNotNull(submitted.get(), error.getText());
            assertEquals(restored, submitted.get().parameters().probing());
            assertTrue(submitted.get().parameters().pauseForToolChange());
            submitted.set(null);
            field(root, "#probe-depth").setText("5");
            generate.fire();
            assertNull(submitted.get());
            field(root, "#probe-depth").setText("-4");
            field(root, "#cnc-power").setText("0");
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("RPM positivo"));
            field(root, "#cnc-power").setText("9000");
            field(root, "#probe-xy").setText("1;");
            generate.fire();
            assertNull(submitted.get());
            field(root, "#probe-xy").setText("1,5;2,5");
            generate.fire();
            assertEquals(1.5, submitted.get().parameters().probing().toolChangeX());
            assertEquals(2.5, submitted.get().parameters().probing().toolChangeY());
            profiles.setValue(GCodePreprocessor.FX_PORTABLE);
            assertFalse(change.isSelected());
            assertFalse(change.isDisabled());
            assertFalse(root.lookup("#probe-settings").isManaged());
            profiles.setValue(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3);
            assertFalse(confirm.isSelected());
        });
    }

    @Test
    void drillingRequiresPositiveRpmAndValidatesEverySelectedTravelHeight() throws Exception {
        onFx(() -> {
            var image = new org.flatcam.cam.excellon.ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "T2C1.0",
                    "%", "T1", "X1.0Y1.0", "T2", "X3.0Y3.0", "M30"));
            var source = new DrillGCodeToolPanel.SourceCandidate(new javafx.scene.control.TreeItem<>("source.drl"), image, Map.of());
            AtomicReference<DrillGCodeToolPanel.Result> submitted = new AtomicReference<>();
            Node root = DrillGCodeToolPanel.build(List.of(source), source, List::of, submitted::set, () -> {});
            var profiles = profiles(root, "#drill-preprocessor");
            profiles.setValue(GCodePreprocessor.TOOLCHANGE_PROBE_MACH3);
            assertTrue(check(root, "#drill-tool-change").isSelected());
            assertTrue(check(root, "#drill-tool-change").isDisabled());
            assertFalse(field(root, "#probe-change-z").isDisabled());
            var generate = (Button) root.lookup("#drill-generate");
            var error = (Label) root.lookup("#drill-error");
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("RPM positivo"));
            field(root, "#drill-power").setText("9000");
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("Confirme"));
            check(root, "#probe-confirm").setSelected(true);
            generate.fire();
            assertNotNull(submitted.get(), error.getText());
            assertEquals(2, submitted.get().orderedToolIds().size());
            assertNotNull(submitted.get().options().probing());
            assertTrue(submitted.get().options().pauseForToolChange());
            submitted.set(null);
            field(root, "#probe-contact-z").setText("2");
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("Travel Z"));
            profiles.setValue(GCodePreprocessor.FX_PORTABLE);
            assertFalse(check(root, "#drill-tool-change").isSelected());
        });
    }
}
