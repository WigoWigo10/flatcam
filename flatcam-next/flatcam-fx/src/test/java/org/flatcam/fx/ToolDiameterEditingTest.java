package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class ToolDiameterEditingTest {
    private static final class Form {
        final Node pane;
        final VBox root;
        final TableView<Object> table;
        final AtomicReference<Object> generated = new AtomicReference<>();
        final String tool;
        Form(String tool, String units) {
            this.tool = tool;
            var geometry = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 8));
            var item = new TreeItem<String>("board");
            if (tool.equals("NCC")) {
                var source = new NccToolPanel.SourceCandidate(item, "board", units, true, geometry);
                pane = NccToolPanel.build(List.of(source), source, List.of(), (s, a, done, cancel) -> false,
                        () -> {}, List::of, generated::set, () -> {});
            } else {
                var source = new IsolationToolPanel.SourceCandidate(item, GerberImage.of(units, Map.of(), geometry, geometry.getBoundary(), Map.of()));
                pane = IsolationToolPanel.build(List.of(source), source, List.of(), (s, a, done, cancel) -> false,
                        () -> {}, List::of, generated::set, () -> {});
            }
            root = new VBox(pane); new Scene(root, 540, 1400);
            root.applyCss(); root.layout();
            table = (TableView<Object>) pane.lookup(".table-view");
            table.getSelectionModel().clearAndSelect(0);
        }
        TextField edit(String text) {
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F2, false, false, false, false));
            root.applyCss(); root.layout();
            var editor = (TextField) table.lookup("#tool-diameter-editor");
            assertNotNull(editor, "F2 must edit the diameter even when the focused column is # or TT");
            editor.setText(text); return editor;
        }
        void generate() { ((Button) pane.lookup(tool.equals("NCC") ? "#ncc-generate" : "#isolation-generate")).fire(); }
        Object row() { return table.getItems().getFirst(); }
        double diameter() throws Exception { return (double) field(row(), "diameter"); }
        double outputDiameter() {
            return generated.get() instanceof NccToolPanel.Result ncc ? ncc.parameters().toolDiameters().getFirst()
                    : ((IsolationToolPanel.Result) generated.get()).tools().getFirst().toolDiameter();
        }
        Map<Double, LegacyToolsDatabase.MillingTool> outputMachining() {
            return generated.get() instanceof NccToolPanel.Result ncc ? ncc.machining()
                    : ((IsolationToolPanel.Result) generated.get()).machining();
        }
    }
    @ParameterizedTest @ValueSource(strings={"bad", "duplicate"})
    void invalidDraftCanBeDiscardedWithoutChangingSelectedTools(String invalid) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form("NCC", "MM");
            double other = (double) field(form.table.getItems().get(1), "diameter");
            form.edit(invalid.equals("duplicate") ? Double.toString(other) : invalid);
            form.table.edit(-1, null); form.table.getSelectionModel().clearAndSelect(1);
            form.generate(); assertNull(form.generated.get());
            var before = List.copyOf(form.table.getSelectionModel().getSelectedIndices());
            var menu = form.table.getContextMenu();
            menu.getItems().stream().filter(item -> item.getText().equals(I18n.text("tool.diameter.correct"))).findFirst().orElseThrow().fire();
            assertEquals(0, form.table.getEditingCell().getRow());
            assertEquals(before, List.copyOf(form.table.getSelectionModel().getSelectedIndices()));
            menu.getItems().stream().filter(item -> item.getText().equals(I18n.text("tool.diameter.discard"))).findFirst().orElseThrow().fire();
            form.generate(); assertNotNull(form.generated.get());
            assertEquals(before, List.copyOf(form.table.getSelectionModel().getSelectedIndices())); return null;
        });
    }

    private static Object field(Object row, String name) throws Exception {
        var f = row.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(row);
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void committedDiameterCanBeUndoneAndRedoneWithoutLosingToolSettings(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM"); double original = form.diameter();
            var before = field(form.row(), tool.equals("NCC") ? "overlapPercent" : "overlap");
            form.edit("0.198").fireEvent(new ActionEvent());
            assertEquals(.198, form.diameter());
            form.table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Z, false, true, false, false));
            assertEquals(original, form.diameter());
            assertEquals(before, field(form.row(), tool.equals("NCC") ? "overlapPercent" : "overlap"));
            assertEquals(0, form.table.getSelectionModel().getSelectedIndex());
            form.table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Y, false, true, false, false));
            assertEquals(.198, form.diameter()); form.generate(); assertEquals(.198, form.outputDiameter()); return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void undoRemovalRestoresSelectionAndImportedMachiningAndNewEditClearsRedo(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM");
            // Create enough rows to exercise non-adjacent multi-selection and each row's own settings.
            while (form.table.getItems().size() < 3) {
                var ctor = form.row().getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
                double diameter = .35 + form.table.getItems().size();
                form.table.getItems().add(tool.equals("NCC") ? ctor.newInstance(diameter)
                        : ctor.newInstance(diameter, ToolProfile.C1, 3, .375, org.flatcam.cam.isolation.IsolationType.BOTH));
            }
            var row = form.table.getItems().get(2); var machiningField = row.getClass().getDeclaredField("machining"); machiningField.setAccessible(true);
            double diameter = (double) field(row, "diameter");
            var parameters = new org.flatcam.cam.gcode.GeometryGCodeParameters(2, .1, false, .1, 123.456789, 12345, false);
            var machining = new LegacyToolsDatabase.MillingTool("imported", diameter, ToolProfile.C1, parameters, null);
            machiningField.set(row, machining);
            form.table.getSelectionModel().clearSelection(); form.table.getSelectionModel().selectIndices(0, 2);
            int count = form.table.getItems().size();
            form.table.fireEvent(key(KeyCode.DELETE)); assertEquals(count - 2, form.table.getItems().size());
            shortcut(form.table, KeyCode.Z);
            assertEquals(count, form.table.getItems().size());
            assertEquals(List.of(0, 2), List.copyOf(form.table.getSelectionModel().getSelectedIndices()));
            assertEquals(machining, field(form.table.getItems().get(2), "machining"));
            shortcut(form.table, KeyCode.Y); assertEquals(count - 2, form.table.getItems().size());
            shortcut(form.table, KeyCode.Z);
            form.table.getSelectionModel().clearAndSelect(0);
            form.edit("0.198").fireEvent(new ActionEvent());
            shortcut(form.table, KeyCode.Y); assertEquals(count, form.table.getItems().size()); assertEquals(.198, form.diameter());
            return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void textUndoDoesNotUndoRowsAndInvalidDiameterBlocksTableUndo(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM"); double original = form.diameter();
            form.edit("0.198").fireEvent(new ActionEvent());
            var editor = form.edit("0.198"); editor.appendText("7");
            shortcut(editor, KeyCode.Z); assertEquals("0.198", editor.getText()); assertEquals(.198, form.diameter());
            editor.setText("invalid"); form.table.edit(-1, null); shortcut(form.table, KeyCode.Z);
            assertEquals(.198, form.diameter()); form.generate(); assertNull(form.generated.get());
            form.table.getContextMenu().getItems().stream().filter(item -> I18n.text("tool.diameter.discard").equals(item.getText())).findFirst().orElseThrow().fire();
            shortcut(form.table, KeyCode.Z); assertEquals(original, form.diameter()); return null;
        });
    }

    private static void shortcut(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false));
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void deleteRemovesSelectedToolAndKeepsTableReadyForKeyboard(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM");
            if (form.table.getItems().size() == 1) {
                var ctor = form.row().getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
                form.table.getItems().add(ctor.newInstance(.35, ToolProfile.C1, 1, .1, org.flatcam.cam.isolation.IsolationType.BOTH));
            }
            int before = form.table.getItems().size(); Object removed = form.row();
            form.table.fireEvent(key(KeyCode.DELETE));
            assertEquals(before-1, form.table.getItems().size()); assertFalse(form.table.getItems().contains(removed));
            assertEquals(0, form.table.getSelectionModel().getSelectedIndex());
            form.generate(); assertNotNull(form.generated.get()); return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void deleteInsideCellOnlyDeletesText(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM"); int before = form.table.getItems().size();
            var editor = form.edit("0.198"); editor.positionCaret(2); editor.fireEvent(key(KeyCode.DELETE));
            assertEquals("0.98", editor.getText()); assertEquals(before,form.table.getItems().size());
            editor.fireEvent(new ActionEvent()); form.generate(); assertEquals(.98,form.outputDiameter()); return null;
        });
    }

    @org.junit.jupiter.api.Test void enterStartsEditingAndTabMovesToTheNextDiameter() throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form("NCC", "MM");
            form.table.fireEvent(key(KeyCode.ENTER)); form.root.applyCss(); form.root.layout();
            var editor = (TextField) form.table.lookup("#tool-diameter-editor"); assertNotNull(editor);
            editor.setText("0.198"); editor.fireEvent(key(KeyCode.TAB)); form.root.applyCss(); form.root.layout();
            assertNotNull(form.table.getEditingCell()); assertEquals(1,form.table.getEditingCell().getRow());
            assertEquals(.198, form.diameter()); return null;
        });
    }

    @org.junit.jupiter.api.Test void invalidEnterKeepsTheCellOpenForCorrection() throws Exception {
        var form = TerminalPanelTest.fx(() -> {
            var value = new Form("NCC", "MM"); value.edit("bad").fireEvent(new ActionEvent()); return value;
        });
        TerminalPanelTest.fx(() -> {
            form.root.applyCss(); form.root.layout();
            assertNotNull(form.table.getEditingCell());
            var editor=(TextField)form.table.lookup("#tool-diameter-editor"); assertNotNull(editor); assertEquals("bad",editor.getText());
            editor.setText("0.198");editor.fireEvent(new ActionEvent());form.generate();assertEquals(.198,form.outputDiameter());return null;
        });
    }

    @org.junit.jupiter.api.Test void shiftTabGoesBackAndInvalidTabDoesNotLeaveTheCell() throws Exception {
        TerminalPanelTest.fx(() -> {
            var form=new Form("NCC","MM");form.edit("0.198").fireEvent(key(KeyCode.TAB));
            form.root.applyCss();form.root.layout();
            var second=(TextField)form.table.lookup("#tool-diameter-editor");assertNotNull(second);
            second.setText("bad");second.fireEvent(key(KeyCode.TAB));
            form.root.applyCss();form.root.layout();assertEquals(1,form.table.getEditingCell().getRow());
            second=(TextField)form.table.lookup("#tool-diameter-editor");second.setText("0.398");
            second.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.TAB,true,false,false,false));
            form.root.applyCss();form.root.layout();assertEquals(0,form.table.getEditingCell().getRow());
            var first=(TextField)form.table.lookup("#tool-diameter-editor");assertEquals("0.198",first.getText());
            first.fireEvent(key(KeyCode.ESCAPE));assertNull(form.table.getEditingCell());
            assertEquals(.398,(double)field(form.table.getItems().get(1),"diameter"));return null;
        });
    }

    @org.junit.jupiter.api.Test void aSingleClickOnSelectedDiameterDoesNotAccidentallyStartEditing() throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form("NCC", "MM");
            var column=form.table.getColumns().get(1); form.table.getFocusModel().focus(0,column);
            var cell=(TableCell<?,?>) form.table.lookupAll(".table-cell").stream().filter(n -> n instanceof TableCell<?,?> c
                    && c.getIndex()==0&&c.getTableColumn()==column).findFirst().orElseThrow();
            for(var type:List.of(MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED,MouseEvent.MOUSE_CLICKED))
                cell.fireEvent(new MouseEvent(type,5,5,5,5,MouseButton.PRIMARY,1,false,false,false,false,
                        type==MouseEvent.MOUSE_PRESSED,false,false,false,false,true,null));
            assertNull(form.table.getEditingCell());return null;
        });
    }

    private static KeyEvent key(KeyCode code) { return new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,false,false,false); }
    private static void set(Object row, String name, Object value) throws Exception {
        var f = row.getClass().getDeclaredField(name); f.setAccessible(true); f.set(row, value);
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void doubleClickOpensTheSameInlineEditor(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM");
            var cell = (TableCell<?, ?>) form.table.lookupAll(".table-cell").stream()
                    .filter(node -> node instanceof TableCell<?, ?> candidate && candidate.getIndex() == 0
                            && candidate.getTableColumn() == form.table.getColumns().get(1)).findFirst().orElseThrow();
            cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 2,
                    false, false, false, false, false, false, false, false, false, false, null));
            var editor = (TextField) form.table.lookup("#tool-diameter-editor"); assertNotNull(editor);
            editor.setText("0.2719"); editor.fireEvent(new ActionEvent()); form.generate();
            assertEquals(.2719, form.outputDiameter()); return null;
        });
    }

    @ParameterizedTest @CsvSource({"NCC,MM,0.198", "NCC,IN,0.0078", "Isolation,MM,0.198", "Isolation,IN,0.0078"})
    void f2AndEnterUpdateDiameterWithoutLosingRowOrImportedMachining(String tool, String units, double target) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, units); Object row = form.row(); double old = form.diameter();
            var imported = MainIsolationMachiningTest.db(old, ToolProfile.C1, 87);
            set(row, "machining", imported);
            if (tool.equals("NCC")) { set(row, "method", NccMethod.SEED); set(row, "overlapPercent", "37.5"); set(row, "connect", false); }
            else { set(row, "passes", "3"); set(row, "overlap", "37.5"); }
            var editor = form.edit(Double.toString(target).replace('.', ','));
            editor.fireEvent(new ActionEvent()); form.generate();
            assertNotNull(form.generated.get()); assertSame(row, form.row());
            assertSame(row, form.table.getSelectionModel().getSelectedItem());
            assertEquals(target, form.diameter()); assertEquals(target, form.outputDiameter());
            var local = form.outputMachining().get(target); assertNotNull(local);
            assertEquals(target, local.diameter()); assertEquals(old, imported.diameter());
            assertNotSame(imported, local); assertSame(imported.parameters(), local.parameters());
            assertSame(imported.tip(), local.tip()); assertSame(imported.jobDefaults(), local.jobDefaults());
            assertFalse(form.outputMachining().containsKey(old));
            if (form.generated.get() instanceof NccToolPanel.Result ncc) {
                var settings = ncc.parameters().settingsFor(target);
                assertEquals(NccMethod.SEED, settings.method()); assertEquals(.375, settings.overlapFraction()); assertFalse(settings.connect());
            } else {
                var isolation = (IsolationToolPanel.Result) form.generated.get();
                assertEquals(3, isolation.tools().getFirst().passes()); assertEquals(.375, isolation.tools().getFirst().overlapFraction());
            }
            return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void generateCommitsTheActiveEditorWithoutNeedingEnterAndKeepsFullPrecision(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM");
            form.edit("0,1981234567"); form.generate();
            assertNotNull(form.generated.get()); assertEquals(.1981234567, form.outputDiameter());
            assertNull(form.table.getEditingCell());
            form.edit("0,4173016452").fireEvent(new ActionEvent()); form.generate();
            assertEquals(.4173016452, form.outputDiameter());
            assertNull(form.table.getEditingCell());
            return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void userDiameterReachesActualCamCalculation(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM");
            double target = .1981234567;
            form.edit(Double.toString(target)); form.generate();
            var copper = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 8));
            if (form.generated.get() instanceof NccToolPanel.Result ncc) {
                var result = NccGenerator.generate("MM", copper, ncc.parameters(),
                        org.flatcam.cam.CancellationToken.none(), org.flatcam.cam.ProgressCallback.none());
                assertEquals(target, result.toolResults().getFirst().toolDiameter()); assertFalse(result.isEmpty());
            } else {
                var params = ((IsolationToolPanel.Result) form.generated.get()).tools().getFirst();
                var result = org.flatcam.cam.isolation.IsolationGenerator.generate("MM", copper, params);
                assertFalse(result.isEmpty());
                assertEquals(-target / 2, result.passGeometries().getFirst().getEnvelopeInternal().getMinX(), 1e-12);
            }
            return null;
        });
    }

    @ParameterizedTest @CsvSource({"NCC,0", "NCC,-1", "NCC,NaN", "NCC,Infinity", "NCC,text", "NCC,''",
            "Isolation,0", "Isolation,-1", "Isolation,NaN", "Isolation,Infinity", "Isolation,text", "Isolation,''"})
    void invalidDraftCannotGenerateWithOldDiameterAndEscapeRestoresIt(String tool, String invalid) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM"); double old = form.diameter();
            var editor = form.edit(invalid); form.generate();
            assertNull(form.generated.get()); assertEquals(old, form.diameter());
            // Generate may refresh/cancel JavaFX editing; F2 still has to recover the draft for correction.
            if (form.table.getEditingCell() == null) editor = form.edit(invalid);
            editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            form.generate(); assertNotNull(form.generated.get()); assertEquals(old, form.outputDiameter());
            return null;
        });
    }

    @ParameterizedTest @ValueSource(strings={"NCC", "Isolation"})
    void duplicateDiameterIsRejectedAndCanBeCorrectedWithoutRemovingTheTool(String tool) throws Exception {
        TerminalPanelTest.fx(() -> {
            var form = new Form(tool, "MM"); double old = form.diameter(); Object row = form.row();
            if (form.table.getItems().size() == 1) {
                var ctor = row.getClass().getDeclaredConstructors()[0]; ctor.setAccessible(true);
                form.table.getItems().add(ctor.newInstance(.35, ToolProfile.C1, 1, .1, org.flatcam.cam.isolation.IsolationType.BOTH));
                form.root.applyCss(); form.root.layout();
            }
            double other = (double) field(form.table.getItems().get(1), "diameter");
            var editor = form.edit(Double.toString(other)); form.generate();
            assertNull(form.generated.get()); assertEquals(old, form.diameter());
            editor.setText("0,198"); form.generate();
            assertNotNull(form.generated.get()); assertEquals(.198, form.outputDiameter()); assertSame(row, form.row());
            return null;
        });
    }
}
