package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class PaintDiameterEditingTest {
    @Test void decimalCommaListsAndLegacyPointListsAreUnambiguous() {
        assertEquals(List.of(.198), PaintToolPanel.parseDiameters("0,198"));
        assertEquals(List.of(.198, .25), PaintToolPanel.parseDiameters("0,198; 0,25"));
        assertEquals(List.of(.198, .25), PaintToolPanel.parseDiameters("0.198;0,25"));
        assertEquals(List.of(.3, .5), PaintToolPanel.parseDiameters("0.3,0.5"));
        assertThrows(IllegalArgumentException.class, () -> PaintToolPanel.parseDiameters("1,2,3"));
    }
    @Test void inlineDiameterEditKeepsEachCuttersMethodMarginAndOverlap() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new AtomicReference<PaintParameters>(); var item = new TreeItem<String>("board");
            var geometry = new GeometryFactory().toGeometry(new Envelope(0,10,0,8));
            var form = PaintToolPanel.build(new PaintToolPanel.Host() {
                public List<TreeItem<String>> sources() { return List.of(item); }
                public TreeItem<String> initialSource() { return item; }
                public String units(TreeItem<String> source) { return "MM"; }
                public Geometry polygons(TreeItem<String> source) { return geometry; }
                public void pickPoint(Consumer<Coordinate> callback) { }
                public void cancelPick() { }
                public void selectArea(Geometry source, boolean polygon, Consumer<Geometry> done, Runnable cancel) { }
                public void cancelAreaSelection() { }
                public void preview(Geometry area) { }
                public void paint(TreeItem<String> source, String units, Geometry polygons, PaintParameters p) { output.set(p); }
            }, () -> {});
            var root = new VBox(form); new Scene(root,540,1300); root.applyCss(); root.layout();
            var list = (TextField) form.lookup("#paint-diameters"); list.setText("0.3;0.5");
            ((Button) form.lookup("#paint-update-tools")).fire();
            var table = (TableView<?>) form.lookup("#paint-tools");
            table.getSelectionModel().clearAndSelect(0);
            ((TextField) form.lookup("#paint-overlap")).setText("37.5123456");
            ((TextField) form.lookup("#paint-margin")).setText("0.123456789");
            ((ComboBox<NccMethod>) form.lookup("#paint-method")).setValue(NccMethod.LINES);
            table.getSelectionModel().clearAndSelect(1);
            ((TextField) form.lookup("#paint-overlap")).setText("45");
            table.getSelectionModel().clearAndSelect(0);
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F2,false,false,false,false));
            root.applyCss(); root.layout();
            var editor = (TextField) table.lookup("#tool-diameter-editor"); assertNotNull(editor);
            editor.setText("0,1981234567"); editor.fireEvent(new ActionEvent());
            ((Button) form.lookup("#paint-generate")).fire();
            var result = output.get(); assertNotNull(result);
            assertEquals(List.of(.1981234567, .5), result.toolDiameters());
            assertEquals(37.5123456 / 100, result.settingsFor(.1981234567).overlapFraction());
            assertEquals(.123456789, result.settingsFor(.1981234567).offset());
            assertEquals(NccMethod.LINES, result.settingsFor(.1981234567).method());
            assertEquals(.45, result.settingsFor(.5).overlapFraction());
            return null;
        });
    }
}
