package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javafx.event.Event;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledOnOs(OS.WINDOWS)
class PlotStatusControlsTest {
    private static final class Fixture {
        final PlotAreaView plot = new PlotAreaView();
        final List<AppPreferences.PlotStatusSettings> saved = new ArrayList<>();
        final List<String> feedback = new ArrayList<>();
        final PlotStatusControls controls;
        final ToggleButton snap;
        Fixture(boolean linked) {
            plot.resize(600, 400); plot.layout();
            controls = new PlotStatusControls(plot, Label::new, new ToggleButton(), feedback::add, () -> { },
                    new AppPreferences.PlotStatusSettings(true, true, 2, linked ? 2 : 5, linked, true, true, false),
                    saved::add);
            snap = (ToggleButton) controls.node().lookup(".status-grid-snap");
            assertNotNull(snap);
        }
        TextField field(String name) throws Exception { return (TextField) reflected(controls, name); }
        void move() {
            move(215.3, 147.7);
        }
        void move(double x, double y) {
            Event.fireEvent(plot, new MouseEvent(MouseEvent.MOUSE_MOVED, x, y, x, y,
                    MouseButton.NONE, 0, false, false, false, false, false, false, false, false, false, false, null));
        }
        double[] world() throws Exception {
            return world(215.3, 147.7);
        }
        double[] world(double x, double y) throws Exception {
            Method method = PlotAreaView.class.getDeclaredMethod("snappedWorld", double.class, double.class);
            method.setAccessible(true); return (double[]) method.invoke(plot, x, y);
        }
        void click(double x, double y) {
            for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED))
                Event.fireEvent(plot, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                        false, false, false, false, type == MouseEvent.MOUSE_PRESSED,
                        false, false, false, false, false, null));
        }
        boolean hasCross() throws Exception {
            var canvas = (Canvas) reflected(plot, "snapCursorCanvas");
            var parameters = new javafx.scene.SnapshotParameters();
            parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
            var snapshot = canvas.snapshot(parameters, null);
            for (int y = 0; y < (int) snapshot.getHeight(); y++)
                for (int x = 0; x < (int) snapshot.getWidth(); x++)
                    if ((snapshot.getPixelReader().getArgb(x, y) >>> 24) != 0) return true;
            return false;
        }
    }

    @Test void buttonAndMenuToggleTheActualSnappingWithoutHidingTheGrid() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false);
            fixture.snap.fire();
            assertFalse(fixture.snap.isSelected());
            double[] raw = fixture.world();
            fixture.controls.toggleGrid();
            assertTrue(fixture.snap.isSelected());
            double[] snapped = fixture.world();
            assertEquals(PlotAreaView.snapCoordinate(raw[0], 2), snapped[0]);
            assertEquals(PlotAreaView.snapCoordinate(raw[1], 5), snapped[1]);
            assertNotEquals(raw[0], snapped[0]); assertNotEquals(raw[1], snapped[1]);
            assertTrue((boolean) reflected(fixture.plot, "gridVisible"));
            assertFalse(fixture.saved.getFirst().gridSnap());
            assertTrue(fixture.saved.getLast().gridSnap());
            return null;
        });
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-2", "NaN", "Infinity", "texto"})
    void invalidStepDoesNotCancelTheUsersSnapToggle(String input) throws Exception {
        TerminalPanelTest.fx(() -> {
            for (boolean linked : new boolean[]{false, true}) {
                Fixture fixture = new Fixture(linked);
                fixture.field("gridX").setText(input);
                fixture.snap.fire();
                assertFalse(fixture.snap.isSelected(), "invalid step must not prevent disabling snap");
                assertFalse((boolean) reflected(fixture.plot, "gridSnapEnabled"));
                assertFalse(fixture.saved.getLast().gridSnap());
                assertEquals(2, fixture.saved.getLast().gridX());
                assertEquals(linked ? 2 : 5, fixture.saved.getLast().gridY());
                fixture.field(linked ? "gridX" : "gridY").setText(input);
                fixture.controls.toggleGrid();
                assertTrue(fixture.snap.isSelected(), "invalid step must not prevent enabling with last valid steps");
                assertTrue((boolean) reflected(fixture.plot, "gridSnapEnabled"));
                assertTrue(fixture.saved.getLast().gridSnap());
                assertTrue(fixture.feedback.getLast().contains("ativado"));
            }
            return null;
        });
    }

    @Test void toggleUpdatesCoordinatesAndCrossWithoutNeedingAnotherMouseMove() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false);
            fixture.move();
            Label coordinates = (Label) fixture.controls.node().lookup(".status-coordinates");
            String snappedText = coordinates.getText();
            assertTrue(fixture.hasCross());
            fixture.snap.fire();
            double[] raw = fixture.world();
            assertEquals(String.format(java.util.Locale.ROOT, "X: %.4f   Y: %.4f", raw[0], raw[1]), coordinates.getText());
            assertNotEquals(snappedText, coordinates.getText());
            assertFalse(fixture.hasCross());
            fixture.controls.toggleGrid();
            assertEquals(snappedText, coordinates.getText());
            assertTrue(fixture.hasCross());
            return null;
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void activePlacementRefreshesAndCommitsUnsnappedMovementAfterToggle(boolean objectMove) throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false);
            List<double[]> committed = new ArrayList<>();
            var handler = new PlotAreaView.PlacementHandler() {
                public void onCommit(double dx, double dy) { committed.add(new double[]{dx, dy}); }
                public void onCancel() { }
            };
            double[] anchor = fixture.world();
            if (objectMove) {
                var shape = new org.locationtech.jts.geom.GeometryFactory()
                        .createPoint(new org.locationtech.jts.geom.Coordinate(0, 0)).buffer(1);
                fixture.plot.putLayer("object", PlotAreaView.LayerCategory.GERBER, shape,
                        javafx.scene.paint.Color.GREEN, javafx.scene.paint.Color.BLACK, false);
                assertTrue(fixture.plot.beginPlacement(List.of("object"), anchor[0], anchor[1], handler));
            } else {
                assertTrue(fixture.plot.beginAreaRectanglePlacement(handler));
                fixture.click(215.3, 147.7);
            }
            fixture.move(245.9, 171.8);
            double[] snapped = fixture.world(245.9, 171.8);
            assertEquals(snapped[0], (double) reflected(fixture.plot, "placementCurrentWorldX"));
            fixture.snap.fire();
            double[] free = fixture.world(245.9, 171.8);
            assertNotEquals(snapped[0], free[0]);
            assertEquals(free[0], (double) reflected(fixture.plot, "placementCurrentWorldX"));
            assertEquals(free[1], (double) reflected(fixture.plot, "placementCurrentWorldY"));
            fixture.move(248.2, 173.3);
            double[] end = fixture.world(248.2, 173.3);
            assertEquals(end[0], (double) reflected(fixture.plot, "placementCurrentWorldX"));
            fixture.click(248.2, 173.3);
            assertEquals(1, committed.size());
            assertArrayEquals(new double[]{end[0] - anchor[0], end[1] - anchor[1]}, committed.getFirst(), 1e-9);
            assertFalse(fixture.plot.isPlacementActive());
            return null;
        });
    }

    @Test void trackEditorDoesNotKeepGridRoundingOrBendConstraintsWhenSnapIsOff() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false);
            fixture.plot.setSelectionHandler(new PlotAreaView.SelectionHandler() {
                public void onClick(double x, double y, boolean additive) { }
                public void onBox(double x, double y, double endX, double endY, boolean additive) { }
            });
            List<List<org.locationtech.jts.geom.Coordinate>> committed = new ArrayList<>();
            assertTrue(fixture.plot.beginEditorTrackPlacement(0.5, new PlotAreaView.TrackPlacementHandler() {
                public void onCommit(List<org.locationtech.jts.geom.Coordinate> points) { committed.add(points); }
                public void onCancel() { }
            }));
            double[] start = fixture.world();
            fixture.click(215.3, 147.7);
            fixture.move(248.2, 173.3);
            fixture.snap.fire();
            double[] end = fixture.world(248.2, 173.3);
            fixture.click(248.2, 173.3);
            assertTrue(fixture.plot.finishEditorTrackPlacement());
            assertEquals(1, committed.size());
            assertEquals(2, committed.getFirst().size(), "free mode must not insert bend points");
            assertEquals(start[0], committed.getFirst().getFirst().x, 1e-9);
            assertEquals(start[1], committed.getFirst().getFirst().y, 1e-9);
            assertEquals(end[0], committed.getFirst().getLast().x, 1e-9);
            assertEquals(end[1], committed.getFirst().getLast().y, 1e-9);
            return null;
        });
    }

    private static Object reflected(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
}
