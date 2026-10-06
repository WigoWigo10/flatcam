package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class ProjectPlotMenuTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final Geometry LINE = FACTORY.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});

    private static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object invoke(MainWindow window, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = MainWindow.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(window, args);
    }

    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final MainWindow window;
        final TreeView<String> tree;
        final PlotAreaView plot;
        Session() throws Exception {
            window = fx(() -> {
                var result = new MainWindow(jobs);
                var root = new TreeItem<String>("root");
                root.setExpanded(true);
                for (String name : List.of("gerbersNode", "excellonNode", "geometryNode", "cncJobsNode")) {
                    var category = new TreeItem<String>(name);
                    category.setExpanded(true);
                    field(name).set(result, category);
                    root.getChildren().add(category);
                }
                var view = new TreeView<>(root);
                view.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
                field("projectTree").set(result, view);
                field("unitsLabel").set(result, new Label());
                return result;
            });
            @SuppressWarnings("unchecked") var value = (TreeView<String>) field("projectTree").get(window);
            tree = value;
            plot = (PlotAreaView) field("plotAreaView").get(window);
        }

        @SuppressWarnings("unchecked") TreeItem<String> add(String name, String kind) throws Exception {
            return switch (kind) {
                case "gerber" -> (TreeItem<String>) invoke(window, "addGerberToProject",
                        new Class<?>[]{String.class, Path.class, GerberImage.class}, name, null,
                        new GerberParser().parse(List.of("%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1.0*%", "D10*", "X0Y0D03*", "M02*")));
                case "excellon" -> (TreeItem<String>) invoke(window, "addExcellonToProject",
                        new Class<?>[]{String.class, Path.class, ExcellonImage.class}, name, null,
                        new ExcellonParser().parse(List.of("M48", "METRIC,TZ", "T01C0.8", "%", "T01", "X001000Y001000", "M30")));
                case "geometry" -> (TreeItem<String>) invoke(window, "addGeometryToProject",
                        new Class<?>[]{String.class, String.class, String.class, Geometry.class, boolean.class},
                        name, "", "MM", LINE, true);
                default -> (TreeItem<String>) invoke(window, "addCncJobToProject",
                        new Class<?>[]{String.class, String.class, Path.class, String.class, Geometry.class, Geometry.class},
                        name, "", null, "G21\nG0 X0 Y0\nG1 X10 Y0\n",
                        kind.equals("cnc-cut") || kind.equals("cnc-empty") ? null : LINE,
                        kind.equals("cnc-travel") || kind.equals("cnc-empty") ? null : LINE);
            };
        }

        ContextMenu menu(TreeItem<String> item) throws Exception {
            return (ContextMenu) invoke(window, "buildContextMenuFor", new Class<?>[]{TreeItem.class}, item);
        }
        void visible(TreeItem<String> item, boolean value) throws Exception {
            invoke(window, "setObjectVisible", new Class<?>[]{TreeItem.class, boolean.class}, item, value);
        }
        boolean visible(TreeItem<String> item) throws Exception {
            return (boolean) invoke(window, "isObjectVisible", new Class<?>[]{TreeItem.class}, item);
        }
        void select(TreeItem<String>... items) {
            tree.getSelectionModel().clearSelection();
            for (var item : items) tree.getSelectionModel().select(item);
        }
        @Override public void close() throws Exception {
            jobs.shutdown();
            fx(() -> { window.disposeViewport(); return null; });
        }
    }

    private static List<MenuItem> plotActions(ContextMenu menu) {
        return menu.getItems().stream().filter(item -> item.getText() != null
                && (item.getText().startsWith("Ativar Plot") || item.getText().startsWith("Desativar Plot"))).toList();
    }

    private static void assertActions(ContextMenu menu, String... expected) {
        var actions = plotActions(menu);
        assertEquals(List.of(expected), actions.stream().map(MenuItem::getText).toList());
        for (var action : actions) {
            assertFalse(action.isDisable());
            assertNotNull(action.getGraphic(), "retain the Python visibility icons");
        }
    }

    private static Menu colorMenu(Session session, TreeItem<String> item) throws Exception {
        return (Menu) session.menu(item).getItems().stream()
                .filter(choice -> "Definir Cor".equals(choice.getText())).findFirst().orElseThrow();
    }

    private static MenuItem choice(Menu menu, String label) {
        return menu.getItems().stream().filter(item -> label.equals(item.getText())).findFirst().orElseThrow();
    }

    private static void assertColorSelected(Menu menu, String label) {
        var selected = menu.getItems().stream().filter(item -> item instanceof RadioMenuItem radio && radio.isSelected()).toList();
        assertEquals(List.of(label), selected.stream().map(MenuItem::getText).toList(), "exactly one current color, independent of hover");
        assertNotNull(selected.getFirst().getGraphic(), "preserve the color swatches and legacy icons");
        assertFalse(choice(menu, "Opacidade...") instanceof RadioMenuItem, "opacity is not a color choice");
    }

    @Test void yellowIsMarkedForAllColorCapableObjectKindsRegardlessOfVisibilityOrOpacity() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                for (String kind : List.of("gerber", "excellon", "geometry")) {
                    var item = session.add(kind, kind);
                    session.plot.setLayerColors(item, Color.web("#FFDF00", 0.25), Color.BLACK);
                    session.visible(item, false);
                    assertColorSelected(colorMenu(session, item), "Amarelo");
                    session.visible(item, true);
                    assertColorSelected(colorMenu(session, item), "Amarelo");
                }
                return null;
            });
        }
    }

    @Test void allLegacyPresetsAreMarkedAndTheirSwatchesRemainExact() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("object", "gerber");
                var menu = colorMenu(session, item);
                var presets = Map.of("Vermelho", "#FF0000", "Azul", "#0000FF", "Amarelo", "#FFDF00",
                        "Verde", "#00FF00", "Roxo", "#FF00FF", "Marrom", "#A52A2A", "Branco", "#FFFFFF", "Preto", "#000000");
                for (var preset : presets.entrySet()) {
                    var colorItem = choice(menu, preset.getKey());
                    colorItem.fire();
                    assertColorSelected(menu, preset.getKey());
                    var swatch = (Rectangle) colorItem.getGraphic();
                    assertEquals(Color.web(preset.getValue()), swatch.getFill());
                    var fill = session.plot.layerColors(item)[0];
                    assertEquals(swatch.getFill(), new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 1));
                    assertTrue(fill.getOpacity() < 1, "preserve Gerber preset opacity");
                }
                return null;
            });
        }
    }

    @Test void openingExistingSubmenuRefreshesExternalColorAndOpacityChanges() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("object", "geometry");
                var menu = colorMenu(session, item);
                assertColorSelected(menu, "Vermelho");
                session.plot.setLayerColors(item, Color.web("#0000FF", 0.05), Color.BLACK);
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertColorSelected(menu, "Azul");
                session.plot.setLayerColors(item, Color.web("#0000FF", 0.9), Color.BLACK);
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertColorSelected(menu, "Azul");
                return null;
            });
        }
    }

    @Test void nonPresetColorsAreMarkedCustomButDoNotOpenTheColorDialog() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("object", "excellon");
                session.plot.setLayerColors(item, Color.web("#2468AC", 0.4), Color.WHITE);
                var menu = colorMenu(session, item);
                assertColorSelected(menu, "Personalizada...");
                assertEquals(Color.web("#2468AC", 0.4), session.plot.layerColors(item)[0], "opening must not mutate colors");
                // Pure yellow differs from the Python yellow preset (#FFDF00).
                session.plot.setLayerColors(item, Color.YELLOW, Color.BLACK);
                menu.getOnShowing().handle(new Event(Menu.ON_SHOWING));
                assertColorSelected(menu, "Personalizada...");
                return null;
            });
        }
    }

    @Test void defaultRestoresBothColorsAndNamedPresetTakesPriorityWhenTheyCoincide() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                for (String kind : List.of("gerber", "excellon", "geometry")) {
                    var item = session.add(kind, kind);
                    var original = session.plot.layerColors(item);
                    var menu = colorMenu(session, item);
                    String expected = kind.equals("geometry") ? "Vermelho" : "Padrao";
                    assertColorSelected(menu, expected);
                    choice(menu, "Amarelo").fire();
                    assertColorSelected(menu, "Amarelo");
                    choice(menu, "Padrao").fire();
                    assertArrayEquals(original, session.plot.layerColors(item));
                    assertColorSelected(menu, expected);
                }
                return null;
            });
        }
    }

    @Test void activatingAlreadySelectedColorKeepsOneRealSelection() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("object", "gerber");
                var menu = colorMenu(session, item);
                choice(menu, "Amarelo").fire();
                var yellow = (RadioMenuItem) choice(menu, "Amarelo");
                yellow.setSelected(false); // Simulate a menu skin toggling the selected item off before dispatch.
                yellow.fire();
                assertColorSelected(menu, "Amarelo");
                assertColorSelected(colorMenu(session, item), "Amarelo");
                return null;
            });
        }
    }

    @Test void eachObjectKindShowsOnlyTheUsefulActionAndReopeningReflectsTheClick() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                for (String kind : List.of("gerber", "excellon", "geometry", "cnc-cut", "cnc-travel", "cnc-both")) {
                    var item = session.add(kind, kind);
                    session.select(item);
                    var shown = session.menu(item);
                    assertActions(shown, "Desativar Plot");
                    plotActions(shown).getFirst().fire();
                    assertFalse(session.visible(item));
                    var hidden = session.menu(item);
                    assertActions(hidden, "Ativar Plot");
                    plotActions(hidden).getFirst().fire();
                    assertTrue(session.visible(item));
                    assertActions(session.menu(item), "Desativar Plot");
                }
                return null;
            });
        }
    }

    @Test void mixedSelectionCountsOnlyAffectedObjectsAndLeavesOthersUnchanged() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var shown = session.add("shown", "geometry");
                var hidden = session.add("hidden", "excellon");
                var noPreview = session.add("no-preview", "cnc-empty");
                session.visible(hidden, false);
                session.select(shown, hidden, noPreview);
                var menu = session.menu(shown);
                assertActions(menu, "Ativar Plot (1)", "Desativar Plot (1)");
                plotActions(menu).getFirst().fire();
                assertTrue(session.visible(shown));
                assertTrue(session.visible(hidden));
                assertFalse(session.visible(noPreview));
                assertActions(session.menu(shown), "Desativar Plot (2)");
                return null;
            });
        }
    }

    @Test void uniformSelectionOffersOnlyOneBulkActionAndMixedDisablePreservesHiddenObjects() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var first = session.add("first", "geometry");
                var second = session.add("second", "cnc-cut");
                session.select(first, second);
                var menu = session.menu(first);
                assertActions(menu, "Desativar Plot (2)");
                plotActions(menu).getFirst().fire();
                assertActions(session.menu(first), "Ativar Plot (2)");
                session.visible(first, true);
                menu = session.menu(first);
                assertActions(menu, "Ativar Plot (1)", "Desativar Plot (1)");
                plotActions(menu).getLast().fire();
                assertFalse(session.visible(first));
                assertFalse(session.visible(second));
                return null;
            });
        }
    }

    @Test void cncJobsWithoutPreviewOfferNeitherPlotActionEvenInBulk() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var first = session.add("first", "cnc-empty");
                var second = session.add("second", "cnc-empty");
                session.select(first);
                assertActions(session.menu(first));
                session.select(first, second);
                var menu = session.menu(first);
                assertActions(menu);
                assertEquals("Copiar (2)", menu.getItems().getFirst().getText(), "no orphan leading separator");
                return null;
            });
        }
    }

    @Test void partiallyVisibleCncJobIsPlottedWhenEitherRealSublayerIsVisible() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("both", "cnc-both");
                session.visible(item, false);
                var keyType = Class.forName("org.flatcam.fx.MainWindow$CncCutLayerKey");
                var constructor = keyType.getDeclaredConstructor(TreeItem.class);
                constructor.setAccessible(true);
                session.plot.setLayerVisible(constructor.newInstance(item), true);
                session.select(item);
                var menu = session.menu(item);
                assertActions(menu, "Desativar Plot");
                plotActions(menu).getFirst().fire();
                assertFalse(session.visible(item));
                assertActions(session.menu(item), "Ativar Plot");
                return null;
            });
        }
    }

    @Test void visibilityChangedOutsideTheMenuIsReadFreshAtTheNextOpening() throws Exception {
        try (var session = new Session()) {
            fx(() -> {
                var item = session.add("object", "geometry");
                session.select(item);
                assertActions(session.menu(item), "Desativar Plot");
                session.plot.setLayerVisible(item, false);
                assertActions(session.menu(item), "Ativar Plot");
                session.plot.setLayerVisible(item, true);
                assertActions(session.menu(item), "Desativar Plot");
                return null;
            });
        }
    }
}
