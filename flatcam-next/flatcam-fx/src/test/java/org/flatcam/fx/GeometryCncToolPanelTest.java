package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
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
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/** Checks real controls on the FX thread, without opening a user-visible window. */
@EnabledOnOs(OS.WINDOWS)
class GeometryCncToolPanelTest {
    @Test
    void selectingLaserDisablesMillingFieldsValidatesPowerAndPreservesValuesOnReturn() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
            // A different UI test may already own the toolkit.
        }
        FutureTask<Void> test = new FutureTask<>(() -> {
            var factory = new GeometryFactory();
            var path = factory.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 0)});
            var tools = List.of(new ToolGeometry(0.2, path), new ToolGeometry(0.4, path));
            AtomicReference<GeometryCncToolPanel.Result> submitted = new AtomicReference<>();
            Node root = GeometryCncToolPanel.build("MM", path, tools,
                    new GeometryGCodeParameters(2, 1, true, 0.1, 100, 10000, true, 600),
                    submitted::set, () -> {});
            @SuppressWarnings("unchecked")
            ComboBox<GCodePreprocessor> profiles = (ComboBox<GCodePreprocessor>) root.lookup("#cnc-preprocessor");
            var depth = (TextField) root.lookup("#cnc-cut-depth");
            var multi = (CheckBox) root.lookup("#cnc-multi-depth");
            var pause = (CheckBox) root.lookup("#cnc-tool-change");
            var power = (TextField) root.lookup("#cnc-power");
            var rapid = (TextField) root.lookup("#cnc-rapid-feed");
            var generate = (Button) root.lookup("#cnc-generate");
            var error = (Label) root.lookup("#cnc-error");
            assertEquals(15, profiles.getItems().size());
            profiles.setValue(GCodePreprocessor.MARLIN_LASER_FAN_PIN);
            assertTrue(depth.isDisabled());
            assertTrue(multi.isDisabled());
            assertTrue(pause.isDisabled());
            assertFalse(rapid.isDisabled());
            generate.fire();
            assertNull(submitted.get());
            assertTrue(error.getText().contains("255"));
            power.setText("128");
            generate.fire();
            assertNotNull(submitted.get(), error.getText());
            assertFalse(submitted.get().parameters().multiDepth());
            assertFalse(submitted.get().parameters().pauseForToolChange());
            assertEquals(600, submitted.get().parameters().rapidFeedRate());
            profiles.setValue(GCodePreprocessor.GRBL_LASER);
            assertTrue(rapid.isDisabled());
            profiles.setValue(GCodePreprocessor.MARLIN);
            assertFalse(depth.isDisabled());
            assertFalse(multi.isDisabled());
            assertFalse(pause.isDisabled());
            assertTrue(multi.isSelected());
            assertTrue(pause.isSelected());
            assertEquals("1", depth.getText());
            generate.fire();
            assertTrue(submitted.get().parameters().multiDepth());
            assertTrue(submitted.get().parameters().pauseForToolChange());
            // Single-tool jobs can also run an initial manual/custom change sequence.
            Node single = GeometryCncToolPanel.build("MM", path, List.of(), submitted::set, () -> {});
            var singlePause = (CheckBox) single.lookup("#cnc-tool-change");
            assertFalse(singlePause.isDisabled());
            singlePause.setSelected(true);
            @SuppressWarnings("unchecked")
            var singleProfiles = (ComboBox<GCodePreprocessor>) single.lookup("#cnc-preprocessor");
            singleProfiles.setValue(GCodePreprocessor.TOOLCHANGE_MANUAL);
            ((Button) single.lookup("#cnc-generate")).fire();
            assertEquals(GCodePreprocessor.TOOLCHANGE_MANUAL, submitted.get().preprocessor());
            assertTrue(submitted.get().parameters().pauseForToolChange());
            singleProfiles.setValue(GCodePreprocessor.ISEL_CNC);
            assertFalse(singlePause.isDisabled());
            return null;
        });
        Platform.runLater(test);
        test.get(15, TimeUnit.SECONDS);
    }
}
