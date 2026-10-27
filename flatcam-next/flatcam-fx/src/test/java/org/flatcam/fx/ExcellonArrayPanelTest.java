package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.*;
import org.flatcam.cam.excellon.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class ExcellonArrayPanelTest {
    @BeforeEach @AfterEach void defaults() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    static class Harness implements ExcellonEditorController.Host {
        final PlotAreaView plot=new PlotAreaView(); final VBox chrome=new VBox();
        final ExcellonEditorController editor=new ExcellonEditorController(plot,this);
        ExcellonArray.Parameters parameters; Consumer<ExcellonArray.Prepared> done; Consumer<String> failed;
        Node panel;
        Harness(boolean existing) {
            new Scene(chrome,380,1100); plot.resize(800,600); plot.setGridSnap(false,1,1);
            var image=ExcellonImage.of("MM",existing ? Map.of(7,.8) : Map.of(),
                    existing ? List.of(new ExcellonImage.Drill(7,10,10)) : List.of(),List.of(),new GeometryFactory().createPolygon());
            editor.start(new TreeItem<>("holes"),image); chrome.applyCss(); chrome.layout();
        }
        public void openToolPanel(String title,Node node) { panel=node; chrome.getChildren().add(node); }
        public void closeToolPanel() { chrome.getChildren().clear(); }
        public void showToolbar(Node node) { }
        public void hideToolbar() { }
        public void setObjectVisible(TreeItem<String> item,boolean visible) { }
        public void apply(TreeItem<String> item,ExcellonImage image) { }
        public Node icon(String file) { return new Label(file); }
        public void log(String message) { }
        public boolean prepareArray(ExcellonArray.Parameters p,Consumer<ExcellonArray.Prepared> success,Consumer<String> failure) {
            assertNull(parameters); parameters=p; done=success; failed=failure; return true;
        }
        Object field(String name) {
            try { var f=ExcellonEditorController.class.getDeclaredField(name); f.setAccessible(true); return f.get(editor); }
            catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        ExcellonArrayPanel form() { return (ExcellonArrayPanel)field("arrays"); }
        ExcellonEditSession session() { return (ExcellonEditSession)field("session"); }
        void finish() throws Exception {
            assertFalse(javafx.application.Platform.isFxApplicationThread());
            var result=ExcellonArray.generate(parameters,CancellationToken.none(),ProgressCallback.none());
            var callback=done;
            TerminalPanelTest.fx(()-> { parameters=null; done=null; failed=null; callback.accept(result); return null; });
        }
        void click(double x,double y) {
            double sx=plot.cameraSnapshot().screenX(x),sy=plot.cameraSnapshot().screenY(y);
            for(var type:List.of(MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED)) plot.fireEvent(new MouseEvent(type,sx,sy,sx,sy,MouseButton.PRIMARY,1,
                    false,false,false,false,type==MouseEvent.MOUSE_PRESSED,false,false,false,false,true,null));
        }
    }
    @Test void circularSlotsNeedNoSelectionAndPublishOnlyAfterConfirmationWithOneUndo() throws Exception {
        var h=TerminalPanelTest.fx(()->new Harness(false));
        try {
            TerminalPanelTest.fx(()-> {
                var f=h.form(); f.mode.setValue("CIRCULAR"); f.shape.setValue("Slots"); f.count.setText("4"); f.step.setText("90");
                f.radius.setText("5"); f.start.setText("180"); f.origin(40,30); f.slotLength.setText("3");
                ((TextField)h.panel.lookup("#excellon-new-diameter")).setText("0.8");
                f.preview.fire(); assertNotNull(h.parameters); assertFalse(h.session().isDirty()); assertTrue(h.session().toolDiameters().isEmpty()); return null;
            }); h.finish();
            TerminalPanelTest.fx(()-> {
                assertFalse(h.form().confirm.isDisabled()); assertFalse(h.session().isDirty()); assertEquals(0,h.session().size());
                h.form().confirm.fire(); var image=h.session().resultImage(); assertEquals(4,image.slots().size()); assertEquals(Map.of(1,.8),image.toolDiameters());
                assertEquals(35,(image.slots().getFirst().x1()+image.slots().getFirst().x2())/2,1e-10);
                assertTrue(h.editor.undoFromShortcut()); assertFalse(h.session().isDirty()); assertTrue(h.session().toolDiameters().isEmpty());
                assertTrue(h.editor.redoFromShortcut()); assertEquals(4,h.session().size()); return null;
            });
        } finally { TerminalPanelTest.fx(()-> { h.editor.cancel(); return null; }); }
    }
    @Test void parameterChangesAndEscapeDiscardReadyAndPendingResultsAndFailureReenablesControls() throws Exception {
        var h=TerminalPanelTest.fx(()->new Harness(true));
        try {
            TerminalPanelTest.fx(()-> { h.form().preview.fire(); return null; }); h.finish();
            TerminalPanelTest.fx(()-> {
                h.form().pitch.setText("3"); assertFalse(h.plot.isPlacementActive()); assertTrue(h.form().confirm.isDisabled());
                h.form().preview.fire(); assertTrue(h.editor.cancelArrayFromShortcut()); return null;
            }); h.finish();
            TerminalPanelTest.fx(()-> {
                assertFalse(h.session().isDirty()); assertFalse(h.plot.isPlacementActive()); assertFalse(h.form().preview.isDisabled());
                h.form().preview.fire(); var failure=h.failed; h.parameters=null; h.failed=null; h.done=null; failure.accept("cancelado");
                assertFalse(h.form().preview.isDisabled()); assertFalse(h.session().isDirty()); return null;
            });
        } finally { TerminalPanelTest.fx(()-> { h.editor.cancel(); return null; }); }
    }
    @Test void circlePickingUsesSnapAndAtan2InLeftHalfPlane() throws Exception {
        var h=TerminalPanelTest.fx(()->new Harness(true));
        try {
            TerminalPanelTest.fx(()-> {
                var f=h.form(); f.mode.setValue("CIRCULAR"); f.count.setText("4"); f.step.setText("90");
                h.plot.setGridSnap(true,1,1); f.pick.fire(); h.click(40.2,30.3); h.click(34.8,35.3);
                assertNotNull(h.parameters); assertEquals(40,h.parameters.x()); assertEquals(30,h.parameters.y());
                assertEquals(135,h.parameters.startAngle(),1e-10); assertEquals(Math.sqrt(50),h.parameters.radius(),1e-10); return null;
            }); h.finish();
            TerminalPanelTest.fx(()-> { h.form().cancel.fire(); assertFalse(h.session().isDirty()); return null; });
        } finally { TerminalPanelTest.fx(()-> { h.editor.cancel(); return null; }); }
    }
    @Test void hiddenCircularAndSlotDraftsDoNotBlockLinearDrillsAndGridRetainsOldReplication() throws Exception {
        var h=TerminalPanelTest.fx(()->new Harness(true));
        try {
            TerminalPanelTest.fx(()-> {
                var f=h.form(); f.radius.setText("invalid"); f.slotLength.setText("invalid"); f.axis.setValue("Y"); f.count.setText("3");
                f.preview.fire(); assertNotNull(h.parameters); assertEquals(90,h.parameters.linearAngle()); return null;
            }); h.finish();
            TerminalPanelTest.fx(()-> {
                h.form().confirm.fire(); assertEquals(4,h.session().size()); assertTrue(h.editor.undoFromShortcut());
                var table=(TableView<?>)h.panel.lookup("#excellon-shapes"); table.getSelectionModel().select(0);
                h.form().mode.setValue("GRID"); h.form().preview.fire();
                assertEquals(4,h.session().size()); assertTrue(h.editor.undoFromShortcut()); return null;
            });
        } finally { TerminalPanelTest.fx(()-> { h.editor.cancel(); return null; }); }
    }
    @Test void toolbarHelpExplainsNewPatternsAndDistinguishesTheLegacyGrid() {
        for(String label:List.of("Array de furos","Array de slots")) {
            String help=PanelTooltips.help("Editor Excellon",label);
            assertTrue(help.contains("LINEAR") && help.contains("CIRCULAR") && help.contains("sem exigir seleção"));
            assertTrue(help.contains("prévia") && help.contains("Ctrl+Z") && help.contains("Ctrl+Y"));
        }
        String grid=PanelTooltips.help("Editor Excellon","Array de furos selecionados");
        assertTrue(grid.contains("selecionados") && grid.contains("direta"));
    }
    @Test void closedEditorIgnoresWorkerCompletion() throws Exception {
        var h=TerminalPanelTest.fx(()->new Harness(false));
        TerminalPanelTest.fx(()-> { h.form().preview.fire(); h.editor.cancel(); return null; });
        h.finish(); assertFalse(h.editor.isActive());
    }
}
