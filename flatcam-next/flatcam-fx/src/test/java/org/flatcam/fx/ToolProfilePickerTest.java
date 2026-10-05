package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class ToolProfilePickerTest {
    @Test void everyProfileHasDistinctOriginalArtworkAndDescription() {
        var paths = new HashSet<String>();
        for (ToolProfile profile : ToolProfile.values()) {
            assertTrue(paths.add(ToolProfilePicker.path(profile)), profile.name());
            assertFalse(ToolProfilePicker.description(profile).isBlank());
        }
        assertTrue(ToolProfilePicker.description(ToolProfile.C4).contains("4 dentes"));
        assertTrue(ToolProfilePicker.description(ToolProfile.B).contains("esférica"));
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void isolationPickerShowsIconsAndLabelsWithoutChangingProfilesOrColumnWidth(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var geometry = new GeometryFactory().createPolygon(new Coordinate[]{new Coordinate(0,0),
                    new Coordinate(10,0), new Coordinate(10,10), new Coordinate(0,10), new Coordinate(0,0)});
            var source = new IsolationToolPanel.SourceCandidate(new TreeItem<>("Copper"),
                    GerberImage.of("MM", Map.of(), geometry, geometry, Map.of()));
            Node panel = IsolationToolPanel.build(List.of(source), source, List.of(),
                    (a,b,c,d) -> false, () -> {}, List::of, result -> {}, () -> {});
            var table = (TableView<?>) panel.lookupAll(".table-view").stream()
                    .filter(node -> node.getStyleClass().contains("compact-tools-table")).findFirst().orElseThrow();
            ((Pane) table.getParent()).getChildren().remove(table);
            var title = new Label("Isolation Routing — Tipo de fresa (TT)");
            VBox root = new VBox(10, title, table);
            root.setStyle("-fx-padding: 12;"); root.getStyleClass().add("tool-panel");
            Scene scene = new Scene(root, 400, 290); theme.applyTo(scene);
            Stage stage = new Stage(); stage.setScene(scene); stage.setX(-3000); stage.setY(100); // off-screen
            try {
                stage.show(); root.applyCss(); root.layout();
                @SuppressWarnings("unchecked") ComboBox<ToolProfile> choice = (ComboBox<ToolProfile>) table.lookupAll(".table-cell").stream()
                        .filter(node -> node instanceof TableCell<?,?> cell && cell.getIndex() == 0 && cell.getGraphic() instanceof ComboBox<?>)
                        .map(node -> ((TableCell<?,?>) node).getGraphic()).findFirst().orElseThrow();
                assertTrue(choice.getStyleClass().contains("tool-profile-choice"));
                assertEquals(List.of(ToolProfile.values()), List.copyOf(choice.getItems()));
                assertEquals(ToolProfile.C1, choice.getValue());
                for (ToolProfile profile : ToolProfile.values()) {
                    choice.setValue(profile); root.applyCss(); root.layout();
                    assertEquals(profile, table.getColumns().get(2).getCellData(0));
                    assertEquals(profile.name(), choice.getButtonCell().getText());
                    assertNotNull(choice.getButtonCell().getGraphic());
                    assertTrue(choice.getAccessibleHelp().contains(ToolProfilePicker.description(profile)));
                    assertNotNull(choice.getProperties().get(FluidTooltips.TEXT_KEY));
                    assertTrue(table.getColumns().get(2).getWidth() >= 72 && table.getColumns().get(2).getWidth() <= 96);
                    var outline = (SVGPath) choice.getButtonCell().getGraphic().lookup(".tool-profile-outline");
                    assertEquals(choice.getButtonCell().getTextFill(), outline.getStroke());
                }
                choice.setValue(ToolProfile.C2);
                for (ThemeOption next : ThemeOption.values()) {
                    next.applyTo(scene); root.applyCss(); root.layout();
                    var outline = (SVGPath) choice.getButtonCell().getGraphic().lookup(".tool-profile-outline");
                    assertEquals(choice.getButtonCell().getTextFill(), outline.getStroke());
                    assertEquals(ToolProfile.C2, table.getColumns().get(2).getCellData(0));
                }
                theme.applyTo(scene); root.applyCss(); root.layout();
                choice.show();
                var popup = ((ComboBoxListViewSkin<?>) choice.getSkin()).getPopupContent();
                popup.applyCss(); popup.autosize();
                if (popup instanceof javafx.scene.Parent parent) parent.layout();
                var cells = popup.lookupAll(".list-cell").stream()
                        .filter(node -> node instanceof javafx.scene.control.ListCell<?> cell && !cell.isEmpty()).toList();
                assertEquals(6, cells.size());
                for (var node : cells) {
                    var cell = (javafx.scene.control.ListCell<?>) node;
                    assertTrue(cell.getText().contains(" — "));
                    assertNotNull(cell.getGraphic());
                    var outline = (SVGPath) cell.getGraphic().lookup(".tool-profile-outline");
                    assertEquals(cell.getTextFill(), outline.getStroke());
                }
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    snapshot(root, "tool-profile-table-" + theme);
                    snapshot(popup, "tool-profile-popup-" + theme);
                }
                choice.hide();
                var cell = choice.getCellFactory().call(null);
                cell.updateIndex(-1);
                assertNull(cell.getGraphic());
            } finally { stage.close(); }
            return null;
        });
    }

    private static void snapshot(Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<output.getHeight();y++) for (int x=0;x<output.getWidth();x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(output, "png", Path.of("target", name + ".png").toFile());
    }
}
