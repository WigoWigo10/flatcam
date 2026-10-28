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
    private static Object field(Object row, String name) throws Exception {
        var f = row.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(row);
    }
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
