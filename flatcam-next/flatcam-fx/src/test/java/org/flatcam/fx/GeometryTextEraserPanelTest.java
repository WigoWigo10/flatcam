package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class GeometryTextEraserPanelTest {
    private static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(20,TimeUnit.SECONDS);
    }
    private static class Harness implements GeometryEditorController.Host {
        final PlotAreaView plot = new PlotAreaView();
        final GeometryEditorController controller = new GeometryEditorController(plot,this);
        final VBox chrome = new VBox();
        Node panel;
        Node toolbar;
        Geometry applied;
        GeometryEditSession.OperationRequest request;
        Consumer<GeometryEditSession.OperationResult> completed;
        Consumer<String> failed;
        Harness(Geometry source, List<ToolGeometry> tools) {
            new Scene(chrome,500,800);
            plot.resize(800,600); plot.setGridSnap(false,1,1);
            var item = new TreeItem<>("board");
            plot.putLayer(item,PlotAreaView.LayerCategory.GEOMETRY,source,Color.RED,Color.RED,false);
            controller.start(item,source,tools,false,"MM");
            chrome.applyCss(); chrome.layout();
        }
        public void openToolPanel(String label, Node content) { panel=content; chrome.getChildren().add(content); }
        public void closeToolPanel() { chrome.getChildren().remove(panel); }
        public void showEditorToolbar(Node value) { toolbar=value; chrome.getChildren().add(value); }
        public void hideEditorToolbar() { chrome.getChildren().remove(toolbar); }
        public Node icon(String file) { return new Label(file); }
        public void setObjectVisible(TreeItem<String> item,boolean value) { plot.setLayerVisible(item,value); }
        public void apply(TreeItem<String> item,Geometry geometry,List<ToolGeometry> tools) { applied=geometry; }
        public void log(String message) { }
        public boolean runOperation(GeometryEditSession.OperationRequest value,
                Consumer<GeometryEditSession.OperationResult> done,Consumer<String> failure) {
            assertNull(request); request=value; completed=done; failed=failure; return true;
        }
        void finish() throws Exception {
            assertFalse(Platform.isFxApplicationThread());
            var job=request; var callback=completed;
            var result=job.execute(CancellationToken.none(),ProgressCallback.none());
            fx(() -> { request=null; completed=null; callback.accept(result); return null; });
        }
        Button button(String id) { return (Button) toolbar.lookup("#"+id); }
        TableView<?> table() { return (TableView<?>) panel.lookup("#geometry-shapes"); }
        void click(double x,double y) {
            // Default viewport: centre (50,40), scale 3, 44/20 px rulers. No fit/pan in this harness.
            double sx=(x-50)*3+(plot.getWidth()-44)/2+44;
            double sy=(plot.getHeight()-20)/2-(y-40)*3+20;
            for (var type : List.of(MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED))
                plot.fireEvent(new MouseEvent(type,sx,sy,sx,sy,MouseButton.PRIMARY,1,
                        false,false,false,false,type==MouseEvent.MOUSE_PRESSED,false,false,false,false,true,null));
        }
    }
    @Test void textPreviewDoesNotCommitUntilClickAndCancelAndUndoWork() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.button("geometry-text").fire(); h.chrome.applyCss(); h.chrome.layout();
                ((TextArea) h.panel.lookup("#geometry-text-content")).setText("OB");
                ((ComboBox<String>) h.panel.lookup("#geometry-text-font")).setValue("SansSerif");
                ((TextField) h.panel.lookup("#geometry-text-size")).setText("invalid");
                ((Button) h.panel.lookup("#geometry-text-place")).fire(); assertNull(h.request);
                assertEquals("OB",((TextArea) h.panel.lookup("#geometry-text-content")).getText());
                ((TextField) h.panel.lookup("#geometry-text-size")).setText("10");
                ((Button) h.panel.lookup("#geometry-text-place")).fire();
                assertTrue(h.controller.isBusy()); assertTrue(h.button("geometry-text").isDisabled());
                return null;
            });
            h.finish();
            fx(() -> {
                assertFalse(h.controller.isBusy()); assertFalse(h.button("geometry-text").isDisabled());
                assertFalse(h.controller.hasUnappliedChanges());
                h.plot.cancelPlacement(); assertEquals(0,h.table().getItems().size());
                ((Button) h.panel.lookup("#geometry-text-place")).fire(); return null;
            });
            h.finish();
            fx(() -> {
                h.click(50,40); assertTrue(h.controller.hasUnappliedChanges());
                assertTrue(h.table().getItems().size() >= 2);
                assertTrue(h.controller.undoFromShortcut()); assertEquals(0,h.table().getItems().size());
                assertTrue(h.controller.redoFromShortcut());
                h.controller.apply(); assertNotNull(h.applied); assertTrue(h.applied.isValid());
                assertTrue(h.applied.getEnvelopeInternal().getMinX() >= 50);
                return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }
    @Test void eraserRequiresSelectionAndTwoClicksThenRunsOffUiWithUndo() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,44,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> {
                assertTrue(h.button("geometry-eraser").isDisabled());
                h.table().getSelectionModel().select(0); assertFalse(h.button("geometry-eraser").isDisabled());
                h.button("geometry-eraser").fire(); return null;
            });
            h.finish();
            fx(() -> {
                assertFalse(h.controller.hasUnappliedChanges());
                h.click(30,30); assertNull(h.request); h.click(40,30);
                assertTrue(h.controller.isBusy()); assertFalse(h.controller.hasUnappliedChanges()); return null;
            });
            h.finish();
            fx(() -> {
                assertTrue(h.controller.hasUnappliedChanges());
                assertFalse(h.button("geometry-text").isDisabled());
                assertTrue(h.controller.undoFromShortcut()); assertFalse(h.controller.hasUnappliedChanges());
                assertTrue(h.controller.redoFromShortcut()); h.controller.apply();
                assertEquals(16,h.applied.getArea(),1e-6); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }
    @Test void workerFailureReenablesToolsAndPreservesDraft() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.controller.startText(); h.chrome.applyCss(); h.chrome.layout();
                ((TextArea) h.panel.lookup("#geometry-text-content")).setText("A");
                ((Button) h.panel.lookup("#geometry-text-place")).fire();
                var failure=h.failed; h.request=null; h.failed=null; failure.accept("Operacao cancelada.");
                assertFalse(h.controller.isBusy()); assertFalse(h.button("geometry-text").isDisabled());
                assertFalse(h.controller.hasUnappliedChanges()); assertEquals(0,h.table().getItems().size());
                return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }
}
