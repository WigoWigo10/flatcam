package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Tab;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.panel.Panelize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class MainPanelizeFlowTest {
    @TempDir Path directory;
    private static final GeometryFactory F = new GeometryFactory();
    private static java.lang.reflect.Field field(String name) throws Exception {
        var f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var m = MainWindow.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(window, args);
    }
    private static TreeItem<String> addDrills(MainCamFlowTest.Session s, String units) throws Exception {
        double u = s.unit;
        var drills = ExcellonImage.of(units, Map.of(7, .812345 * u),
                List.of(new ExcellonImage.Drill(7, 3 * u, 3 * u)),
                List.of(new ExcellonImage.Slot(7, 4 * u, 3 * u, 5 * u, 3 * u)),
                F.createPoint(new Coordinate(3 * u, 3 * u)).buffer(.4 * u).union(
                        F.createLineString(new Coordinate[]{new Coordinate(4 * u, 3 * u), new Coordinate(5 * u, 3 * u)}).buffer(.4 * u)));
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addExcellonToProject",
                new Class<?>[]{String.class, Path.class, ExcellonImage.class}, "holes", null, drills));
    }
    private static PanelizeToolPanel.Request request(MainCamFlowTest.Session s, List<TreeItem<String>> sources) {
        double u = s.unit;
        double[] box = {-2 * u, -2 * u, 12 * u, 10 * u};
        return new PanelizeToolPanel.Request(sources, s.reference, box,
                Panelize.layout(box, 2, 2, u, 2 * u, Double.NaN, Double.NaN), false);
    }
    private static JobHandle<?> start(MainCamFlowTest.Session s, PanelizeToolPanel.Request request) throws Exception {
        return TerminalPanelTest.fx(() -> {
            call(s.window, "runPanelize", new Class<?>[]{PanelizeToolPanel.Request.class}, request);
            return (JobHandle<?>) field("runningJob").get(s.window);
        });
    }
    private static ProjectFile snapshot(MainCamFlowTest.Session s) throws Exception {
        return TerminalPanelTest.fx(() -> (ProjectFile) call(s.window, "snapshotProject", new Class<?>[]{}));
    }
    private static void assertNoPanels(MainCamFlowTest.Session s) throws Exception {
        s.awaitUi(); var project = snapshot(s);
        assertTrue(project.gerbers().stream().noneMatch(g -> g.name().contains("_panelized")));
        assertTrue(project.excellons().stream().noneMatch(g -> g.name().contains("_panelized")));
        assertTrue(project.geometries().stream().noneMatch(g -> g.name().contains("_panelized")));
    }

    @ParameterizedTest @ValueSource(strings={"MM", "IN"})
    void batchAndSeparateRunsPreserveOneGridAndNativeRoundTrip(String units) throws Exception {
        try (var s = new MainCamFlowTest.Session(units)) {
            var drills = addDrills(s, units); var request = request(s, List.of(s.item, drills, s.reference));
            var h = start(s, request); s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            var project = snapshot(s);
            assertEquals(2, project.gerbers().size()); assertEquals(2, project.excellons().size()); assertEquals(2, project.geometries().size());
            var g = project.gerbers().stream().filter(e -> e.name().contains("_panelized")).findFirst().orElseThrow();
            var e = project.excellons().stream().filter(v -> v.name().contains("_panelized")).findFirst().orElseThrow();
            assertEquals(25 * s.unit, g.image().solidGeometry().getEnvelopeInternal().getMaxX(), 1e-9);
            assertEquals(4, e.image().totalDrills()); assertEquals(4, e.image().totalSlots());
            assertEquals(7, e.image().drills().getLast().toolId());
            assertEquals(18 * s.unit, e.image().drills().getLast().x(), 1e-9);
            assertEquals(17 * s.unit, e.image().drills().getLast().y(), 1e-9);
            var single = start(s, request(s, List.of(drills))); single.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            var second = snapshot(s).excellons().getLast();
            assertEquals(e.image().drills(), second.image().drills()); assertEquals(e.image().slots(), second.image().slots());
            var saved = snapshot(s);
            Path file = directory.resolve("panel.fcnproj");
            org.flatcam.app.project.ProjectFileIO.save(saved, file);
            var reopened = org.flatcam.app.project.ProjectFileIO.load(file);
            var before = saved.excellons().getLast().image(); var after = reopened.excellons().getLast().image();
            assertEquals(before.totalDrills(), after.totalDrills()); assertEquals(before.totalSlots(), after.totalSlots());
            for (int i = 0; i < before.totalDrills(); i++) {
                assertEquals(before.drills().get(i).toolId(), after.drills().get(i).toolId());
                assertEquals(before.drills().get(i).x(), after.drills().get(i).x(), 1e-12);
                assertEquals(before.drills().get(i).y(), after.drills().get(i).y(), 1e-12);
            }
            for (int i = 0; i < before.totalSlots(); i++) {
                assertEquals(before.slots().get(i).toolId(), after.slots().get(i).toolId());
                assertEquals(before.slots().get(i).x1(), after.slots().get(i).x1(), 1e-12);
                assertEquals(before.slots().get(i).y1(), after.slots().get(i).y1(), 1e-12);
                assertEquals(before.slots().get(i).x2(), after.slots().get(i).x2(), 1e-12);
                assertEquals(before.slots().get(i).y2(), after.slots().get(i).y2(), 1e-12);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"source", "reference", "project", "cancel"})
    void lateChangesRejectEveryMemberBeforePublication(String change) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var drills = addDrills(s, "MM"); var h = start(s, request(s, List.of(s.item, drills)));
            TerminalPanelTest.fx(() -> {
                s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS);
                switch(change) {
                    case "source" -> drills.setValue("renamed");
                    case "reference" -> ((Map) field("geometryByItem").get(s.window)).remove(s.reference);
                    case "project" -> field("tclProjectEpoch").setLong(s.window, 123);
                    default -> h.cancel();
                }
                return null;
            });
            assertNoPanels(s);
        }
    }
    @Test void unitsMismatchIsRejectedByHostEvenWhenBypassingTheForm() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var drills = addDrills(s, "IN");
            TerminalPanelTest.fx(() -> {
                var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> call(s.window,
                        "runPanelize", new Class<?>[]{PanelizeToolPanel.Request.class}, request(s, List.of(s.item, drills))));
                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
                assertNull(field("runningJob").get(s.window)); return null;
            });
            assertNoPanels(s);
        }
    }
    @Test void completionDoesNotCloseAnUnrelatedPanel() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var h = start(s, request(s, List.of(s.item)));
            var other = new VBox(new Label("other"));
            TerminalPanelTest.fx(() -> { ((Tab) field("toolTab").get(s.window)).setContent(other); return null; });
            s.release.countDown(); h.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
            TerminalPanelTest.fx(() -> { assertSame(other, ((Tab) field("toolTab").get(s.window)).getContent()); return null; });
            assertEquals(2, snapshot(s).gerbers().size());
        }
    }

    @ParameterizedTest @ValueSource(strings={"CLASSIC_LIGHT", "CLASSIC_DARK"})
    void realPreviewUsesTwoSidedOverlaysAndCleansUpInBothThemes(String themeName) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var drills = addDrills(s, "MM");
            s.release.countDown();
            var plot = TerminalPanelTest.fx(() -> {
                var board = F.toGeometry(new Envelope(-2, 12, -2, 10))
                        .difference(F.toGeometry(new Envelope(7, 9, 5, 7)));
                var edge = (TreeItem<String>) call(s.window, "addGeometryToProject", new Class<?>[]{String.class, String.class, String.class, Geometry.class, boolean.class},
                        "Edge_Cuts", "", "MM", board, true);
                ((Map) field("geometryByItem").get(s.window)).remove(s.reference);
                ((TreeItem<String>) field("geometryNode").get(s.window)).getChildren().remove(s.reference);
                var copper = s.image.solidGeometry().difference(F.createPoint(new Coordinate(3, 3)).buffer(.6))
                        .difference(F.toGeometry(new Envelope(7, 9, 5, 7)));
                ((Map) field("gerberByItem").get(s.window)).put(s.item,
                        org.flatcam.cam.gerber.GerberImage.of("MM", Map.of(), copper, copper.getBoundary(), Map.of()));
                var tree = (javafx.scene.control.TreeView<String>) field("projectTree").get(s.window);
                tree.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
                tree.getSelectionModel().clearSelection();
                for (var item : List.of(s.item, drills, edge)) tree.getSelectionModel().select(item);
                call(s.window, "openPanelizeTool", new Class<?>[]{});
                var viewport = (PlotAreaView) field("plotAreaView").get(s.window);
                viewport.removeLayer(s.reference); viewport.updateLayerGeometry(s.item, copper);
                var scroll = (javafx.scene.control.ScrollPane) ((Tab) field("toolTab").get(s.window)).getContent();
                ((javafx.scene.control.CheckBox) scroll.getContent().lookup("#panelize-together")).fire();
                scroll.setPrefWidth(320); scroll.setMinWidth(320); scroll.setMaxWidth(320);
                var root = new javafx.scene.layout.HBox(scroll, viewport);
                javafx.scene.layout.HBox.setHgrow(viewport, javafx.scene.layout.Priority.ALWAYS);
                var scene = new javafx.scene.Scene(root, 1100, 850); ThemeOption.valueOf(themeName).applyTo(scene);
                viewport.applyTheme(ThemeOption.valueOf(themeName));
                root.resize(1100, 850); root.applyCss(); root.layout(); return viewport;
            });
            Geometry content = null;
            for (int i = 0; i < 100; i++) {
                content = TerminalPanelTest.fx(() -> {
                    var f = PlotAreaView.class.getDeclaredField("editorContentGeometry"); f.setAccessible(true);
                    return (Geometry) f.get(plot);
                });
                if (content != null) break; Thread.sleep(10);
            }
            assertNotNull(content); assertEquals(12, content.getNumGeometries());
            Thread.sleep(100); // permit asynchronous drawable indexes to publish, outside FX
            TerminalPanelTest.fx(() -> {
                var scroll = (javafx.scene.control.ScrollPane) ((Tab) field("toolTab").get(s.window)).getContent();
                ((javafx.scene.control.Button) scroll.getContent().lookup("#panelize-preview-fit")).fire();
                assertEquals(3, plot.visibleSelectableLayers().size(), "preview copies must not become selectable project objects");
                var stylesField = PlotAreaView.class.getDeclaredField("toolPreviewContents"); stylesField.setAccessible(true);
                var styled = (List<PlotAreaView.PreviewLayer>) stylesField.get(plot);
                assertEquals(List.of(PlotAreaView.LayerCategory.GERBER, PlotAreaView.LayerCategory.GEOMETRY, PlotAreaView.LayerCategory.EXCELLON),
                        styled.stream().map(PlotAreaView.PreviewLayer::category).toList());
                assertTrue(styled.get(1).strokeOnly());
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    var root = plot.getScene().getRoot(); root.applyCss(); root.layout();
                    var image = root.snapshot(null, null);
                    var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(png, "png", Path.of("target", "panelize-preview-" + themeName + ".png").toFile());
                }
                call(s.window, "closeToolPanel", new Class<?>[]{});
                assertTrue(((List<?>) stylesField.get(plot)).isEmpty());
                for (String name : List.of("editorContentGeometry", "editorFillGeometry", "editorReferenceGeometry", "editorHighlightGeometry")) {
                    var f = PlotAreaView.class.getDeclaredField(name); f.setAccessible(true); assertNull(f.get(plot));
                }
                return null;
            });
        }
    }
}
