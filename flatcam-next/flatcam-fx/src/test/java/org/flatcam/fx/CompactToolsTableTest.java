package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class CompactToolsTableTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void realToolTablesResizeWithoutStretchingTypeEditorsOrLeavingBlankColumns(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            for (String tool : List.of("Isolation", "NCC", "GeometryCNC")) {
                Node panel = panel(tool);
                TableView<?> table = panel.lookupAll(".table-view").stream()
                        .filter(node -> node instanceof TableView<?> candidate
                                && candidate.getColumns().size() == 3
                                && candidate.getStyleClass().contains("compact-tools-table"))
                        .map(node -> (TableView<?>) node).findFirst().orElseThrow();
                ((Pane) table.getParent()).getChildren().remove(table);
                VBox root = new VBox(table);
                root.getStyleClass().add("tool-panel");
                Scene scene = new Scene(root, 320, 180);
                theme.applyTo(scene);
                var selected = List.copyOf(table.getSelectionModel().getSelectedIndices());
                var originalItems = List.copyOf(table.getItems());
                for (double width : new double[]{180, 320, 560, 240, 180, 320}) {
                    root.resize(width, 180); root.applyCss(); root.layout();
                    assertEquals(3, table.getVisibleLeafColumns().size());
                    assertEquals(32, table.getColumns().get(0).getWidth(), 0.01);
                    assertTrue(table.getColumns().get(1).getWidth() >= 64);
                    double typeWidth = table.getColumns().get(2).getWidth();
                    assertTrue(typeWidth >= 72 && typeWidth <= 96, tool + " TT width: " + typeWidth);
                    double columnsWidth = table.getColumns().stream().mapToDouble(column -> column.getWidth()).sum();
                    double viewport = table.getWidth() - table.getInsets().getLeft() - table.getInsets().getRight();
                    for (Node node : table.lookupAll(".scroll-bar")) {
                        if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL && bar.isVisible())
                            viewport -= bar.getWidth();
                    }
                    assertEquals(viewport, columnsWidth, 1, "columns must fill the viewport: " + tool);
                    for (Node node : table.lookupAll(".scroll-bar")) {
                        if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL)
                            assertFalse(bar.isVisible(), tool + " must not require horizontal scrolling");
                    }
                    for (Node node : table.lookupAll(".table-cell")) {
                        if (node instanceof TableCell<?, ?> cell && !cell.isEmpty()) {
                            assertEquals(Pos.CENTER, cell.getAlignment());
                            if (cell.getGraphic() instanceof ComboBox<?> choice) {
                                choice.applyCss(); choice.layout();
                                assertTrue(choice.getWidth() <= typeWidth - 3);
                                assertTrue(choice.getHeight() <= 24.01);
                                assertTrue(choice.getLayoutY() >= 0);
                                assertTrue(choice.getLayoutY() + choice.getHeight() <= cell.getHeight() + 0.01);
                                assertEquals(ToolProfile.C1, choice.getValue());
                                var display = choice.lookupAll(".list-cell").stream()
                                        .filter(nodeValue -> nodeValue.getParent() == choice && nodeValue.isVisible())
                                        .findFirst().orElseThrow();
                                var value = (javafx.scene.text.Text) display.lookup(".text");
                                assertNotNull(value);
                                assertEquals("C1", value.getText(), tool + " must not ellipsize TT");
                            }
                        }
                    }
                    assertEquals(selected, List.copyOf(table.getSelectionModel().getSelectedIndices()));
                    assertEquals(originalItems, List.copyOf(table.getItems()));
                }
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    var snapshot = root.snapshot(null, null);
                    var output = new java.awt.image.BufferedImage((int) snapshot.getWidth(),
                            (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                        output.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(output, "png",
                            Path.of("target", "compact-tools-" + tool + "-" + theme + ".png").toFile());
                }
                if (!tool.equals("GeometryCNC")) {
                    var editor = table.lookupAll(".table-cell").stream()
                            .filter(node -> node instanceof TableCell<?, ?> cell && cell.getIndex() == 0
                                    && cell.getGraphic() instanceof ComboBox<?>)
                            .map(node -> (ComboBox<?>) ((TableCell<?, ?>) node).getGraphic()).findFirst().orElseThrow();
                    editor.getSelectionModel().select(1);
                    assertEquals(ToolProfile.C2, table.getColumns().get(2).getCellData(0),
                            "layout must preserve the profile editor's action");
                }
            }
            return null;
        });
    }

    private static Node panel(String name) {
        var geometry = new GeometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10),
                new Coordinate(0, 10), new Coordinate(0, 0)});
        return switch (name) {
            case "Isolation" -> {
                var source = new IsolationToolPanel.SourceCandidate(new TreeItem<>("Copper"),
                        GerberImage.of("MM", Map.of(), geometry, geometry, Map.of()));
                yield IsolationToolPanel.build(List.of(source), source, List.of(),
                        (a, b, c, d) -> false, () -> { }, List::of, result -> { }, () -> { });
            }
            case "NCC" -> {
                var source = new NccToolPanel.SourceCandidate(new TreeItem<>("Copper"), "Copper", "MM", true, geometry);
                yield NccToolPanel.build(List.of(source), source, List.of(),
                        (a, b, c, d) -> false, () -> { }, List::of, result -> { }, () -> { });
            }
            case "GeometryCNC" -> GeometryCncToolPanel.build("MM", geometry,
                    List.of(new ToolGeometry(1, geometry), new ToolGeometry(0.5, geometry)), result -> { }, () -> { });
            default -> throw new IllegalArgumentException(name);
        };
    }
}
