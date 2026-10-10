package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

/** The tool panels open with the saved defaults, in the units of the object, and with the factory ones otherwise. */
@EnabledOnOs(OS.WINDOWS)
class ToolDefaultsPanelsTest {

    @BeforeEach
    @AfterEach
    void freshStore() {
        ToolDefaults.useStore(ToolDefaults.memoryStore());
    }

    private static Node laidOut(Node panel) {
        VBox root = new VBox(panel);
        new Scene(root, 420, 900);
        root.applyCss();
        root.layout();
        return root;
    }

    @Test
    void cutoutOpensWithTheSavedValuesOfItsUnits() throws Exception {
        ToolDefaults.set("cutout.cutz", true, "-2.0");
        ToolDefaults.set("cutout.cutz", false, "-0.08");
        ToolDefaults.set("cutout.multidepth", true, "false");
        ToolDefaults.set("cutout.gaps", true, "EIGHT");
        TerminalPanelTest.fx(() -> {
            Node metric = laidOut(CutoutToolPanel.build("MM", (p, done, cancelled) -> false, () -> { }, result -> { },
                    () -> { }));
            assertEquals("-2.0", ((TextField) metric.lookup("#cutout-cut-z")).getText());
            assertFalse(((CheckBox) metric.lookup("#cutout-multi-depth")).isSelected());
            assertEquals(GapPattern.EIGHT, ((ComboBox<?>) metric.lookup("#cutout-gap-pattern")).getValue());
            assertEquals("0.1", ((TextField) metric.lookup("#cutout-margin")).getText(), "untouched: factory value");
            Node inches = laidOut(CutoutToolPanel.build("IN", (p, done, cancelled) -> false, () -> { }, result -> { },
                    () -> { }));
            assertEquals("-0.08", ((TextField) inches.lookup("#cutout-cut-z")).getText());
            return null;
        });
    }

    @Test
    void isolationAndNccTablesStartFromTheSavedTools() throws Exception {
        ToolDefaults.set("iso.tooldia", true, "0.2");
        ToolDefaults.set("ncc.tooldia", true, "2.0; 0.8 0.3");
        TerminalPanelTest.fx(() -> {
            var polygon = new GeometryFactory().createPolygon(new Coordinate[] {new Coordinate(0, 0),
                    new Coordinate(10, 0), new Coordinate(10, 10), new Coordinate(0, 10), new Coordinate(0, 0)});
            var source = new IsolationToolPanel.SourceCandidate(new TreeItem<>("Copper"),
                    GerberImage.of("MM", Map.of(), polygon, polygon, Map.of()));
            Node isolation = laidOut(IsolationToolPanel.build(List.of(source), source, List.of(),
                    (a, b, c, d) -> false, () -> { }, List::of, result -> { }, () -> { }));
            TableView<?> table = (TableView<?>) isolation.lookupAll(".table-view").stream()
                    .filter(node -> node.getStyleClass().contains("compact-tools-table")).findFirst().orElseThrow();
            assertEquals(1, table.getItems().size());
            assertEquals(0.2, ((Number) table.getColumns().get(1).getCellData(0)).doubleValue(), 1e-12);
            return null;
        });
        assertArrayEquals(new double[] {2.0, 0.8, 0.3}, ToolDefaults.numbers("ncc.tooldia", true));
        assertArrayEquals(new double[] {0.040, 0.020}, ToolDefaults.numbers("ncc.tooldia", false));
        // A list that is not one (or has a non-positive diameter) falls back to the factory tools.
        ToolDefaults.set("ncc.tooldia", true, "1.0, zero");
        assertArrayEquals(new double[] {1.0, 0.5}, ToolDefaults.numbers("ncc.tooldia", true));
        ToolDefaults.set("ncc.tooldia", true, "1.0, -0.5");
        assertArrayEquals(new double[] {1.0, 0.5}, ToolDefaults.numbers("ncc.tooldia", true));
        assertTrue(ToolDefaults.flag("ncc.connect"));
    }
}
