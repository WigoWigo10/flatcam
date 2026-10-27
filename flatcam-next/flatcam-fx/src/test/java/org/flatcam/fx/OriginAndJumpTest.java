package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Edit > Set Origin, Move to Origin and Jump to Location (Python on_set_zero_click/on_move2origin/on_jump_to). */
@EnabledOnOs(OS.WINDOWS)
class OriginAndJumpTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Object field(MainWindow window, String name) throws Exception {
        var f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f.get(window);
    }

    private static final class Session implements AutoCloseable {
        final Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/origin-" + UUID.randomUUID());
        final JobExecutor jobs = new JobExecutor(1);
        final MainWindow window;
        Session() throws Exception {
            window = TerminalPanelTest.fx(() -> { var w = new MainWindow(jobs, new RecentFiles(prefs)); w.createScene(); return w; });
        }
        @SuppressWarnings("unchecked")
        TreeItem<String> geometry(String name, Envelope box) throws Exception {
            return TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addGeometryToProject",
                        String.class, String.class, String.class, Geometry.class, boolean.class);
                add.setAccessible(true);
                return (TreeItem<String>) add.invoke(window, name, "", "MM", FACTORY.toGeometry(box), false);
            });
        }
        @SuppressWarnings("unchecked")
        TreeItem<String> cncJob(String name) throws Exception {
            return TerminalPanelTest.fx(() -> {
                Method add = MainWindow.class.getDeclaredMethod("addCncJobToProject", String.class, String.class,
                        Path.class, String.class, Geometry.class, Geometry.class);
                add.setAccessible(true);
                Geometry cut = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 0)});
                return (TreeItem<String>) add.invoke(window, name, "src", Path.of(name), "G1 X1\n", null, cut);
            });
        }
        Envelope bounds(TreeItem<String> item) throws Exception {
            return TerminalPanelTest.fx(() -> {
                @SuppressWarnings("unchecked") Map<TreeItem<String>, Object> geometries =
                        (Map<TreeItem<String>, Object>) field(window, "geometryByItem");
                Object entry = geometries.get(item);
                Method geometry = entry.getClass().getDeclaredMethod("geometry");
                geometry.setAccessible(true);
                return ((Geometry) geometry.invoke(entry)).getEnvelopeInternal();
            });
        }
        @SuppressWarnings("unchecked")
        void select(TreeItem<String>... items) throws Exception {
            TerminalPanelTest.fx(() -> {
                TreeView<String> tree = (TreeView<String>) field(window, "projectTree");
                tree.getSelectionModel().clearSelection();
                for (TreeItem<String> item : items) tree.getSelectionModel().select(item);
                return null;
            });
        }
        @Override public void close() throws Exception {
            TerminalPanelTest.fx(() -> {
                ((FluidTooltips) ((javafx.scene.Scene) field(window, "scene")).getProperties().get("fx.tip.handler")).closeNow();
                window.disposeViewport(); return null;
            });
            jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }

    @Test void setOriginOffsetsEveryObjectAndRefusesProjectsWithCncJobs() throws Exception {
        try (var s = new Session()) {
            var a = s.geometry("a", new Envelope(5, 6, 3, 4));
            var b = s.geometry("b", new Envelope(10, 12, 8, 9));
            assertTrue(TerminalPanelTest.fx(() -> s.window.setOrigin(5, 3)));
            assertEquals(new Envelope(0, 1, 0, 1), s.bounds(a));
            assertEquals(new Envelope(5, 7, 5, 6), s.bounds(b));
            s.cncJob("job");
            assertFalse(TerminalPanelTest.fx(() -> s.window.setOrigin(1, 1)));
            assertEquals(new Envelope(0, 1, 0, 1), s.bounds(a), "nothing moves when a CNC Job would be left behind");
        }
    }

    @Test void moveToOriginPlacesTheSelectionsLowerLeftCornerAtZero() throws Exception {
        try (var s = new Session()) {
            var a = s.geometry("a", new Envelope(2, 3, 7, 8));
            var b = s.geometry("b", new Envelope(4, 6, 4, 5));
            var other = s.geometry("other", new Envelope(20, 21, 20, 21));
            assertFalse(TerminalPanelTest.fx(s.window::moveSelectionToOrigin), "nothing selected");
            s.select(a, b);
            assertTrue(TerminalPanelTest.fx(s.window::moveSelectionToOrigin));
            assertEquals(new Envelope(0, 1, 3, 4), s.bounds(a));
            assertEquals(new Envelope(2, 4, 0, 1), s.bounds(b));
            assertEquals(new Envelope(20, 21, 20, 21), s.bounds(other), "unselected objects stay");
            var job = s.cncJob("job");
            s.select(a, job);
            assertFalse(TerminalPanelTest.fx(s.window::moveSelectionToOrigin));
            assertEquals(new Envelope(0, 1, 3, 4), s.bounds(a));
        }
    }

    @Test void jumpToCentersTheViewAbsolutelyOrRelativeToThePointer() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                PlotAreaView plot = (PlotAreaView) field(s.window, "plotAreaView");
                s.window.jumpTo(12.5, -3, false);
                assertArrayEquals(new double[]{12.5, -3}, plot.viewCenter(), 1e-12);
                s.window.jumpTo(1, 1, true);
                assertArrayEquals(new double[]{13.5, -2}, plot.viewCenter(), 1e-12,
                        "with no pointer over the plot yet, relative uses the view center");
                assertThrows(IllegalArgumentException.class, () -> s.window.jumpTo(Double.NaN, 0, false));
                return null;
            });
        }
    }

    @Test void coordinatesParseWithoutEvaluatingExpressions() {
        assertArrayEquals(new double[]{1.5, -2}, MainWindow.parseXY(" 1.5, -2 "));
        assertArrayEquals(new double[]{3, 4}, MainWindow.parseXY("(3,4)"));
        assertNull(MainWindow.parseXY("1+1,2"));
        assertNull(MainWindow.parseXY("1"));
        assertNull(MainWindow.parseXY("1,2,3"));
        assertNull(MainWindow.parseXY("NaN,0"));
        assertNull(MainWindow.parseXY(null));
    }
}
