package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Help > Shortcut List lists the keys this port handles, in one reusable plot-area tab. */
@EnabledOnOs(OS.WINDOWS)
class ShortcutListPanelTest {

    @Test void longShortcutDescriptionsWrapInsteadOfBeingLostBehindEllipses() throws Exception {
        TerminalPanelTest.fx(() -> {
            var panel = new ShortcutListPanel();
            new javafx.scene.Scene(panel, 420, 550);
            ThemeOption.ICE_LIGHT.applyTo(panel.getScene());
            panel.applyCss(); panel.layout();
            var table = (TableView<?>) panel.lookup("#shortcut-table");
            var cells = table.lookupAll(".table-cell").stream()
                    .filter(javafx.scene.control.TableCell.class::isInstance)
                    .map(javafx.scene.control.TableCell.class::cast).filter(cell -> !cell.isEmpty()).toList();
            assertFalse(cells.isEmpty());
            assertTrue(cells.stream().allMatch(cell -> cell.getGraphic() instanceof javafx.scene.control.Label label
                    && label.isWrapText() && cell.getText() == null));
            return null;
        });
    }

    @Test void listsTheMainWindowShortcutsAndOpensOnceAsATab() throws Exception {
        List<String> keys = ShortcutListPanel.GROUPS.values().stream().flatMap(List::stream)
                .map(ShortcutListPanel.Shortcut::keys).toList();
        for (String expected : List.of("Ctrl+N", "Ctrl+S", "Ctrl+Shift+S", "Ctrl+P", "Ctrl+A", "G", "O", "Shift+O",
                "J", "Shift+J", "Ctrl+M", "Shift+M", "F2", "Delete"))
            assertTrue(keys.contains(expected), expected);
        assertTrue(ShortcutListPanel.GROUPS.values().stream().noneMatch(List::isEmpty));

        Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/shortcuts-" + UUID.randomUUID());
        JobExecutor jobs = new JobExecutor(1);
        try {
            TerminalPanelTest.fx(() -> {
                MainWindow window = new MainWindow(jobs, new RecentFiles(prefs));
                window.createScene();
                window.openShortcutList();
                window.openShortcutList();
                Field f = MainWindow.class.getDeclaredField("centerTabs"); f.setAccessible(true);
                TabPane tabs = (TabPane) f.get(window);
                assertEquals(1, tabs.getTabs().stream().filter(tab -> "Lista de Atalhos".equals(tab.getText())).count());
                var panel = (ShortcutListPanel) tabs.getSelectionModel().getSelectedItem().getContent();
                @SuppressWarnings("unchecked") TableView<List<String>> table = (TableView<List<String>>) panel.lookup("#shortcut-table");
                assertEquals(keys.size(), table.getItems().size());
                window.disposeViewport();
                return null;
            });
        } finally {
            jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }
}
