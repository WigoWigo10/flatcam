package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Edit > Distance / Minimum Distance / Locate in Object (ToolDistance, ToolDistanceMin, on_locate). */
@EnabledOnOs(OS.WINDOWS)
class MeasurementToolTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test void measurementMatchesThePythonFormulas() {
        var m = new MeasurementToolPanel.Measurement(1, 1, 4, 5);
        assertEquals(3, m.dx(), 1e-12);
        assertEquals(4, m.dy(), 1e-12);
        assertEquals(5, m.distance(), 1e-12);
        assertEquals(Math.toDegrees(Math.atan2(4, 3)), m.angle(), 1e-12);
        assertEquals(180 + 45, new MeasurementToolPanel.Measurement(0, 0, -1, -1).angle(), 1e-12, "atan2 normalized to [0, 360)");
        assertEquals(Math.toDegrees(Math.atan(4.0 / 3.0)), m.minimumAngle(), 1e-12);
        assertEquals(90, new MeasurementToolPanel.Measurement(2, 0, 2, 3).minimumAngle(), 1e-12);
        assertArrayEquals(new double[]{2.5, 3}, m.halfPoint(), 1e-12);
        var nearest = MeasurementToolPanel.nearest(FACTORY.toGeometry(new Envelope(0, 1, 0, 1)),
                FACTORY.toGeometry(new Envelope(4, 5, 0, 1)));
        assertEquals(3, nearest.distance(), 1e-12);
        assertEquals(1, nearest.x1(), 1e-12);
        assertEquals(4, nearest.x2(), 1e-12);
    }

    private static Object field(Object owner, Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }

    @Test void measureToolUsesTwoPlotClicksAndMinimumDistanceUsesTheSelection() throws Exception {
        Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/measure-" + UUID.randomUUID());
        JobExecutor jobs = new JobExecutor(1);
        try {
            MainWindow window = TerminalPanelTest.fx(() -> { var w = new MainWindow(jobs, new RecentFiles(prefs)); w.createScene(); return w; });
            TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addGeometryToProject",
                        String.class, String.class, String.class, Geometry.class, boolean.class);
                add.setAccessible(true);
                @SuppressWarnings("unchecked") TreeItem<String> a = (TreeItem<String>) add.invoke(window, "a", "", "MM",
                        FACTORY.toGeometry(new Envelope(0, 1, 0, 1)), false);
                @SuppressWarnings("unchecked") TreeItem<String> b = (TreeItem<String>) add.invoke(window, "b", "", "MM",
                        FACTORY.toGeometry(new Envelope(0, 1, 5, 6)), false);

                window.openMeasureTool();
                PlotAreaView plot = (PlotAreaView) field(window, MainWindow.class, "plotAreaView");
                PlotAreaView.SelectionHandler handler = (PlotAreaView.SelectionHandler) field(plot, PlotAreaView.class, "selectionHandler");
                handler.onClick(1, 1, false);
                handler = (PlotAreaView.SelectionHandler) field(plot, PlotAreaView.class, "selectionHandler");
                handler.onClick(4, 5, false);
                MeasurementToolPanel panel = (MeasurementToolPanel) field(window, MainWindow.class, "measurePanel");
                assertEquals("5.0000", ((TextField) panel.lookup("#measure-distance")).getText());
                assertEquals("3.0000", ((TextField) panel.lookup("#measure-dx")).getText());

                @SuppressWarnings("unchecked") TreeView<String> tree = (TreeView<String>) field(window, MainWindow.class, "projectTree");
                tree.getSelectionModel().clearSelection();
                tree.getSelectionModel().select(a);
                window.openMinimumDistanceTool();
                assertNull(window.measureMinimumDistance(), "one selected object is refused");
                tree.getSelectionModel().select(b);
                var minimum = window.measureMinimumDistance();
                assertEquals(4, minimum.distance(), 1e-12);
                panel = (MeasurementToolPanel) field(window, MainWindow.class, "measurePanel");
                String half = ((TextField) panel.lookup("#measure-half")).getText();
                assertTrue(half.endsWith(", 3.0000)"), half);

                assertArrayEquals(new double[]{1, 6}, window.locateInObject(b, MainWindow.LocatePoint.TOP_RIGHT), 1e-12);
                assertArrayEquals(new double[]{0.5, 5.5}, window.locateInObject(b, MainWindow.LocatePoint.CENTER), 1e-12);
                assertArrayEquals(new double[]{0.5, 5.5}, plot.viewCenter(), 1e-12);
                window.disposeViewport();
                return null;
            });
        } finally {
            jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }
}
