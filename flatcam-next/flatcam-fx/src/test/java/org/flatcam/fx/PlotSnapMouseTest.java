package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Opt-in desktop tests: move the actual mouse only inside a temporary test window. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "flatcam.test.robot", matches = "true")
class PlotSnapMouseTest {
    @ParameterizedTest
    @ValueSource(doubles = {0.1, 5.0})
    void mouseButtonToggleSurvivesFieldFocusAndReturningToPlot(double step) throws Exception {
        Fixture fixture = TerminalPanelTest.fx(() -> new Fixture(step));
        try {
            settle();
            // Transfer focus from an edited step field, as happens in the real status bar.
            click(fixture, fixture.field.localToScreen(10, 10));
            TerminalPanelTest.fx(() -> { fixture.field.setText(Double.toString(step)); return null; });
            for (int cycle = 0; cycle < 3; cycle++) {
                move(fixture, fixture.plot.localToScreen(227, 164));
                verify(fixture, true, step);
                click(fixture, fixture.snap.localToScreen(fixture.snap.getWidth() / 2, fixture.snap.getHeight() / 2));
                move(fixture, fixture.plot.localToScreen(227, 164));
                verify(fixture, false, step);
                click(fixture, fixture.snap.localToScreen(fixture.snap.getWidth() / 2, fixture.snap.getHeight() / 2));
                move(fixture, fixture.plot.localToScreen(227, 164));
                verify(fixture, true, step);
            }
        } finally {
            TerminalPanelTest.fx(() -> {
                fixture.stage.close();
                fixture.robot.mouseMove(fixture.originalMouse);
                return null;
            });
        }
    }

    private static final class Fixture {
        final PlotAreaView plot = new PlotAreaView();
        final PlotStatusControls controls;
        final ToggleButton snap;
        final TextField field;
        final Stage stage = new Stage();
        final Robot robot = new Robot();
        final Point2D originalMouse = robot.getMousePosition();
        Fixture(double step) throws Exception {
            Platform.setImplicitExit(false);
            controls = new PlotStatusControls(plot, name -> new Label("G"), new ToggleButton("T"),
                    text -> { }, () -> { },
                    new AppPreferences.PlotStatusSettings(true, true, step, step, true, true, true, false),
                    new ArrayList<AppPreferences.PlotStatusSettings>()::add);
            snap = (ToggleButton) controls.node().lookup(".status-grid-snap");
            field = (TextField) reflected(controls, "gridX");
            BorderPane root = new BorderPane(plot);
            root.setBottom(controls.node());
            stage.setScene(new Scene(root, 900, 450));
            stage.setTitle("FlatCAM FX - teste de Snap pelo mouse");
            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            root.applyCss(); root.layout();
        }
    }

    private static void settle() throws InterruptedException { Thread.sleep(160); }
    private static void move(Fixture fixture, Point2D point) throws Exception {
        TerminalPanelTest.fx(() -> { fixture.robot.mouseMove(point); return null; });
        settle();
    }
    private static void click(Fixture fixture, Point2D point) throws Exception {
        move(fixture, point);
        TerminalPanelTest.fx(() -> { fixture.robot.mousePress(MouseButton.PRIMARY); return null; });
        settle();
        TerminalPanelTest.fx(() -> { fixture.robot.mouseRelease(MouseButton.PRIMARY); return null; });
        settle();
    }
    private static void verify(Fixture fixture, boolean enabled, double step) throws Exception {
        TerminalPanelTest.fx(() -> {
            assertEquals(enabled, fixture.snap.isSelected(), "physical button state");
            assertEquals(enabled, reflected(fixture.plot, "gridSnapEnabled"), "Plot state must match button");
            assertTrue((boolean) reflected(fixture.plot, "cursorInsidePlot"), "real mouse returned to Plot");
            double x = (double) reflected(fixture.plot, "cursorScreenX");
            double y = (double) reflected(fixture.plot, "cursorScreenY");
            Method worldMethod = PlotAreaView.class.getDeclaredMethod("snappedWorld", double.class, double.class);
            worldMethod.setAccessible(true);
            double[] actual = (double[]) worldMethod.invoke(fixture.plot, x, y);
            // Get the unrounded reference without moving the cursor or changing the button.
            Field flag = PlotAreaView.class.getDeclaredField("gridSnapEnabled"); flag.setAccessible(true);
            double[] raw;
            try {
                flag.setBoolean(fixture.plot, false);
                raw = (double[]) worldMethod.invoke(fixture.plot, x, y);
            } finally { flag.setBoolean(fixture.plot, enabled); }
            assertEquals(enabled ? PlotAreaView.snapCoordinate(raw[0], step) : raw[0], actual[0], 1e-9);
            assertEquals(enabled ? PlotAreaView.snapCoordinate(raw[1], step) : raw[1], actual[1], 1e-9);
            Label coords = (Label) fixture.controls.node().lookup(".status-coordinates");
            assertEquals(String.format(java.util.Locale.ROOT, "X: %.4f   Y: %.4f", actual[0], actual[1]), coords.getText());
            SnapshotParameters parameters = new SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
            var image = ((Canvas) reflected(fixture.plot, "snapCursorCanvas")).snapshot(parameters, null);
            boolean cross = false;
            for (int row = 0; row < (int) image.getHeight() && !cross; row++)
                for (int col = 0; col < (int) image.getWidth(); col++)
                    if ((image.getPixelReader().getArgb(col, row) >>> 24) != 0) { cross = true; break; }
            assertEquals(enabled, cross, "red cross after reentering Plot");
            return null;
        });
    }
    private static Object reflected(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }
}
