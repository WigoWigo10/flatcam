package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Python View > Replot / Clear Plot / Zoom In-Out and Objects > Select/Deselect All. */
@EnabledOnOs(OS.WINDOWS)
class PlotAreaClearAndZoomTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void clearPlotsKeepsEachLayersPlotStateAndReplotRestoresDrawing() throws Exception {
        TerminalPanelTest.fx(() -> {
            PlotAreaView plot = new PlotAreaView();
            Geometry a = FACTORY.toGeometry(new Envelope(0, 1, 0, 1));
            Geometry b = FACTORY.toGeometry(new Envelope(5, 6, 5, 6));
            plot.putLayer("a", PlotAreaView.LayerCategory.GERBER, a, Color.RED, Color.RED, false);
            plot.putLayer("b", PlotAreaView.LayerCategory.GERBER, b, Color.RED, Color.RED, false);
            plot.setLayerVisible("b", false);

            plot.clearPlots();
            assertTrue(plot.isLayerVisible("a"), "Clear Plot must not uncheck an object's Plot state");
            assertTrue(plot.isLayerCleared("a"));
            assertTrue(plot.visibleSelectableLayers().isEmpty(), "cleared shapes are no longer on the canvas");

            plot.setLayerVisible("b", true);
            assertEquals(List.of("b"), plot.visibleSelectableLayers().stream().map(PlotAreaView.SelectableLayer::key).toList(),
                    "re-enabling an object draws it again, as Python's obj.plot()");

            plot.putLayer("a", PlotAreaView.LayerCategory.GERBER, a, Color.BLUE, Color.BLUE, false);
            assertFalse(plot.isLayerCleared("a"), "an updated object is drawn again");

            plot.clearPlots();
            plot.replotAll();
            assertEquals(2, plot.visibleSelectableLayers().size());
            plot.dispose();
            return null;
        });
    }

    @Test
    void zoomByScalesAboutTheCenterAndRejectsInvalidFactors() throws Exception {
        TerminalPanelTest.fx(() -> {
            PlotAreaView plot = new PlotAreaView();
            double before = plot.zoomScale();
            plot.zoomBy(MainWindow.MENU_ZOOM_RATIO);
            assertEquals(before * 1.5, plot.zoomScale(), before * 1e-9);
            plot.zoomBy(1 / MainWindow.MENU_ZOOM_RATIO);
            assertEquals(before, plot.zoomScale(), before * 1e-9);
            assertThrows(IllegalArgumentException.class, () -> plot.zoomBy(0));
            assertThrows(IllegalArgumentException.class, () -> plot.zoomBy(Double.NaN));
            for (int i = 0; i < 400; i++) plot.zoomBy(1.5);
            assertTrue(Double.isFinite(plot.zoomScale()), "zoom stays clamped");
            plot.dispose();
            return null;
        });
    }

    @Test
    void selectAllDeselectAllAndClearPlotThroughTheMainWindow() throws Exception {
        JobExecutor jobs = new JobExecutor(1);
        try {
            TerminalPanelTest.fx(() -> {
                MainWindow window = new MainWindow(jobs);
                TreeItem<String> root = new TreeItem<>("root");
                for (String category : new String[]{"gerbersNode", "excellonNode", "geometryNode", "cncJobsNode"}) {
                    TreeItem<String> node = new TreeItem<>(category);
                    field(category).set(window, node);
                    root.getChildren().add(node);
                }
                TreeView<String> tree = new TreeView<>(root);
                tree.setShowRoot(false);
                tree.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
                field("projectTree").set(window, tree);
                field("unitsLabel").set(window, new Label());
                Method add = MainWindow.class.getDeclaredMethod("addGeometryToProject",
                        String.class, String.class, String.class, Geometry.class, boolean.class);
                add.setAccessible(true);
                @SuppressWarnings("unchecked") TreeItem<String> first = (TreeItem<String>) add.invoke(window, "g1", "", "MM",
                        FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)}), true);
                @SuppressWarnings("unchecked") TreeItem<String> second = (TreeItem<String>) add.invoke(window, "g2", "", "MM",
                        FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 5), new Coordinate(10, 5)}), true);

                call(window, "selectAllObjects");
                assertEquals(List.of(first, second), List.copyOf(tree.getSelectionModel().getSelectedItems()));
                call(window, "deselectAllObjects");
                assertTrue(tree.getSelectionModel().getSelectedItems().isEmpty());

                PlotAreaView plot = (PlotAreaView) field("plotAreaView").get(window);
                call(window, "clearAllPlots");
                assertTrue(plot.isLayerVisible(first) && plot.isLayerVisible(second), "Plot state preserved");
                assertTrue(plot.visibleSelectableLayers().isEmpty());
                call(window, "replotAllObjects");
                assertEquals(2, plot.visibleSelectableLayers().size());
                window.disposeViewport();
                return null;
            });
        } finally {
            jobs.shutdown();
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MainWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void call(MainWindow window, String method) throws Exception {
        Method m = MainWindow.class.getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(window);
    }
}
