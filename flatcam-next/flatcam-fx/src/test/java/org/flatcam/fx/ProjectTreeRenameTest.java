package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class ProjectTreeRenameTest {
    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final MainWindow window;
        final TreeView<String> tree;
        final TabPane tabs;
        final StackPane root;
        final TreeItem<String> first = new TreeItem<>("First.gbr");
        final TreeItem<String> second = new TreeItem<>("Second.gbr");

        @SuppressWarnings("unchecked")
        Session() throws Exception {
            window = TerminalPanelTest.fx(() -> new MainWindow(jobs));
            tabs = TerminalPanelTest.fx(() -> (TabPane) invoke(window, "buildLeftTabs"));
            tree = (TreeView<String>) field("projectTree").get(window);
            root = TerminalPanelTest.fx(() -> {
                var shape = new GeometryFactory().createPoint(new Coordinate(0, 0)).buffer(1);
                var image = GerberImage.of("MM", Map.of(), shape, shape, Map.of());
                var objects = (Map<TreeItem<String>, GerberImage>) field("gerberByItem").get(window);
                objects.put(first, image); objects.put(second, image);
                var category = (TreeItem<String>) field("gerbersNode").get(window);
                category.getChildren().add(first); category.getChildren().add(second);
                StackPane owner = new StackPane(tabs);
                Scene scene = new Scene(owner, 420, 420);
                ThemeOption.CLASSIC_DARK.applyTo(scene);
                owner.resize(420, 420); owner.applyCss(); owner.layout();
                tree.getSelectionModel().clearAndSelect(tree.getRow(first));
                owner.applyCss(); owner.layout();
                return owner;
            });
        }

        TreeCell<?> cell(TreeItem<String> item) {
            return tree.lookupAll(".tree-cell").stream()
                    .filter(node -> node instanceof TreeCell<?> cell && cell.getTreeItem() == item)
                    .map(node -> (TreeCell<?>) node).findFirst().orElseThrow();
        }
        void press(TreeItem<String> item, int count, boolean control, boolean shift) {
            TreeCell<?> cell = cell(item);
            Event.fireEvent(cell, new MouseEvent(cell, cell, MouseEvent.MOUSE_PRESSED, 80, 10, 80, 10,
                    MouseButton.PRIMARY, count, shift, control, false, false,
                    true, false, false, false, false, false,
                    new javafx.scene.input.PickResult(cell, new javafx.geometry.Point3D(80, 10, 0), 0)));
            Event.fireEvent(cell, new MouseEvent(cell, cell, MouseEvent.MOUSE_RELEASED, 80, 10, 80, 10,
                    MouseButton.PRIMARY, count, shift, control, false, false,
                    false, false, false, false, false, false,
                    new javafx.scene.input.PickResult(cell, new javafx.geometry.Point3D(80, 10, 0), 0)));
        }
        void f2() { Event.fireEvent(tree, key(KeyCode.F2)); }
        TextField editor() { assertTrue(cell(first).isEditing()); return (TextField) cell(first).getGraphic(); }
        public void close() { jobs.shutdown(); }
    }

    @Test void repeatedSingleClicksDoNotRenameSelectedObjects() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                for (int click = 0; click < 3; click++) session.press(session.first, 1, false, false);
                assertSame(session.first, session.tree.getSelectionModel().getSelectedItem());
                assertNull(session.tree.getEditingItem()); assertFalse(session.cell(session.first).isEditing());
                assertEquals("First.gbr", session.first.getValue());
                // Native click/F2 behavior cannot bypass the explicit command gate.
                session.tree.edit(session.first);
                assertNull(session.tree.getEditingItem());
                // Confirm the same native single-click reaches TreeView.edit if the gate is open.
                field("requestedTreeRename").set(session.window, session.first);
                try {
                    session.press(session.first, 1, false, false);
                    assertSame(session.first, session.tree.getEditingItem());
                    assertTrue(session.cell(session.first).isEditing());
                } finally {
                    field("requestedTreeRename").set(session.window, null);
                    session.tree.edit(null);
                }
                session.press(session.first, 1, false, false);
                assertNull(session.tree.getEditingItem());
                return null;
            });
        }
    }

    @Test void doubleClickStillOpensPropertiesWithoutStartingRename() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                session.press(session.first, 2, false, false);
                assertSame(field("propertiesTab").get(session.window), session.tabs.getSelectionModel().getSelectedItem());
                assertNull(session.tree.getEditingItem());
                assertEquals("First.gbr", session.first.getValue());
                return null;
            });
        }
    }

    @Test void f2RenamesAndCommitReturnsToNonEditingClickBehavior() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                session.f2();
                assertSame(session.first, session.tree.getEditingItem());
                TextField editor = session.editor(); editor.setText("Renamed.gbr"); editor.fireEvent(new ActionEvent());
                assertEquals("Renamed.gbr", session.first.getValue());
                assertNull(session.tree.getEditingItem());
                session.press(session.first, 1, false, false);
                assertNull(session.tree.getEditingItem());
                return null;
            });
        }
    }

    @Test void contextRenameCommandStillStartsEditingOnTheNextFxEvent() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> { invoke(session.window, "beginRename", session.first); return null; });
            TerminalPanelTest.fx(() -> {
                assertSame(session.first, session.tree.getEditingItem());
                assertEquals("First.gbr", session.editor().getText());
                session.tree.edit(null);
                assertNull(session.tree.getEditingItem()); assertFalse(session.cell(session.first).isEditing());
                return null;
            });
        }
    }

    @Test void escapeCancelsWithoutChangingTheName() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                session.f2(); TextField editor = session.editor(); editor.setText("Discard.gbr");
                Event.fireEvent(editor, key(KeyCode.ESCAPE));
                assertEquals("First.gbr", session.first.getValue());
                assertNull(session.tree.getEditingItem()); assertFalse(session.cell(session.first).isEditing());
                return null;
            });
        }
    }

    @Test void ctrlAndShiftSelectionArePreservedWithoutRename() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                session.press(session.second, 1, true, false);
                assertTrue(session.tree.getSelectionModel().getSelectedItems().containsAll(java.util.List.of(session.first, session.second)));
                assertNull(session.tree.getEditingItem());
                session.press(session.first, 1, true, false);
                assertFalse(session.tree.getSelectionModel().getSelectedItems().contains(session.first));
                assertTrue(session.tree.getSelectionModel().getSelectedItems().contains(session.second));
                session.press(session.first, 1, false, true);
                assertTrue(session.tree.getSelectionModel().getSelectedItems().containsAll(java.util.List.of(session.first, session.second)));
                assertNull(session.tree.getEditingItem());
                return null;
            });
        }
    }

    @Test void categoryAndInvalidNamesCannotBeRenamed() throws Exception {
        try (Session session = new Session()) {
            TerminalPanelTest.fx(() -> {
                invoke(session.window, "requestTreeRename", field("gerbersNode").get(session.window));
                assertNull(session.tree.getEditingItem());
                for (String name : new String[]{"  ", "Second.gbr"}) {
                    session.f2(); TextField editor = session.editor(); editor.setText(name); editor.fireEvent(new ActionEvent());
                    assertEquals("First.gbr", session.first.getValue());
                    assertNull(session.tree.getEditingItem());
                }
                return null;
            });
        }
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }
    private static Field field(String name) throws Exception {
        Field field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object invoke(MainWindow target, String name, Object... item) throws Exception {
        Method method = MainWindow.class.getDeclaredMethod(name, item.length == 0 ? new Class<?>[0] : new Class<?>[]{TreeItem.class});
        method.setAccessible(true); return method.invoke(target, item);
    }
}
