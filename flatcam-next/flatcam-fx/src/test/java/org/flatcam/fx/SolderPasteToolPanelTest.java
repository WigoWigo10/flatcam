package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import org.flatcam.cam.solderpaste.SolderPaste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class SolderPasteToolPanelTest {
    @Test
    void usesCheckedNozzlesForGeometryAndHighlightedNozzleForRemoval() throws Exception {
        TerminalPanelTest.fx(() -> {
            var gerber = new TreeItem<>("Paste mask");
            var submissions = new ArrayList<List<Double>>();
            var root = (Parent) SolderPasteToolPanel.build(new SolderPasteToolPanel.Host() {
                public List<TreeItem<String>> gerbers() { return List.of(gerber); }
                public List<TreeItem<String>> pasteGeometries() { return List.of(); }
                public TreeItem<String> initialGerber() { return gerber; }
                public String createGeometry(TreeItem<String> source, List<Double> nozzles) {
                    assertSame(gerber, source);
                    submissions.add(nozzles);
                    return null;
                }
                public String createJob(TreeItem<String> geometry, SolderPaste.Parameters parameters) {
                    fail("Geometry generation must not create a CNC Job");
                    return null;
                }
            }, () -> {});
            new Scene(root, 360, 850);
            root.applyCss();
            root.layout();
            var nozzles = (ListView<?>) root.lookup("#solder-paste-nozzles");
            var generate = (Button) root.lookup("#solder-paste-create-geometry");
            nozzles.getSelectionModel().select(1);
            generate.fire();
            assertEquals(List.of(1.0, .3), submissions.getLast());

            checkbox(nozzles, 0).fire();
            generate.fire();
            assertEquals(List.of(.3), submissions.getLast());
            checkbox(nozzles, 1).fire();
            generate.fire();
            assertEquals(2, submissions.size());
            assertEquals(I18n.text("paste.nozzles.required"), ((Label) root.lookup("#solder-paste-error")).getText());

            var diameter = (TextField) root.lookup("#solder-paste-nozzle-diameter");
            diameter.setText("0.6");
            ((Button) root.lookup("#solder-paste-add-nozzle")).fire();
            generate.fire();
            assertEquals(List.of(.6), submissions.getLast());
            nozzles.getSelectionModel().select(1);
            ((Button) root.lookup("#solder-paste-remove-nozzle")).fire();
            assertEquals(2, nozzles.getItems().size());
            generate.fire();
            assertEquals(List.of(.6), submissions.getLast());
            return null;
        });
    }

    private static CheckBox checkbox(ListView<?> nozzles, int index) {
        return nozzles.lookupAll(".list-cell").stream()
                .filter(node -> node instanceof ListCell<?> cell && cell.getIndex() == index && cell.getGraphic() instanceof CheckBox)
                .map(node -> (CheckBox) ((ListCell<?>) node).getGraphic()).findFirst().orElseThrow();
    }
}
