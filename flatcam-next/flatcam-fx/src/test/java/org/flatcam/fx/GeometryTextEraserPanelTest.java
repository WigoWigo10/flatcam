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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class GeometryTextEraserPanelTest {
    @BeforeEach @AfterEach void defaults() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    private static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(20,TimeUnit.SECONDS);
    }
    private static class Harness implements GeometryEditorController.Host {
        final PlotAreaView plot = new PlotAreaView();
        final GeometryEditorController controller;
        final VBox chrome = new VBox();
        Node panel;
        Node toolbar;
        Geometry applied;
        GeometryEditSession.OperationRequest request;
        Consumer<GeometryEditSession.OperationResult> completed;
        Consumer<String> failed;
        Consumer<ArcCoordinateDialog.Choice> coordinateReply;
        Coordinate coordinateReference, movedCursor;
        public void requestArcCoordinates(Coordinate cursor,String units,Consumer<ArcCoordinateDialog.Choice> selected) {
            coordinateReference = new Coordinate(cursor); coordinateReply = selected;
        }
        public void movePlotCursor(Coordinate point) { movedCursor = new Coordinate(point); }
        Harness(Geometry source, List<ToolGeometry> tools) {
            this(source, tools, AsyncFontCatalog.SYSTEM);
        }
        Harness(Geometry source, List<ToolGeometry> tools, AsyncFontCatalog fonts) {
            controller = new GeometryEditorController(plot,this,fonts);
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
            click(x, y, MouseButton.PRIMARY);
        }
        void click(double x,double y, MouseButton button) {
            // Default viewport: centre (50,40), scale 3, 44/20 px rulers. No fit/pan in this harness.
            double sx=plot.cameraSnapshot().screenX(x), sy=plot.cameraSnapshot().screenY(y);
            for (var type : List.of(MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED))
                plot.fireEvent(new MouseEvent(type,sx,sy,sx,sy,button,1,
                        false,false,false,false,type==MouseEvent.MOUSE_PRESSED && button==MouseButton.PRIMARY,
                        false,type==MouseEvent.MOUSE_PRESSED && button==MouseButton.SECONDARY,false,false,true,null));
        }
        void move(double x,double y) {
            double sx=plot.cameraSnapshot().screenX(x), sy=plot.cameraSnapshot().screenY(y);
            plot.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED,sx,sy,sx,sy,MouseButton.NONE,0,
                    false,false,false,false,false,false,false,false,false,true,null));
        }
        double area() { return reflectedSession().resultGeometry().getArea(); }
        GeometryEditSession reflectedSession() {
            try { var field=GeometryEditorController.class.getDeclaredField("session"); field.setAccessible(true); return (GeometryEditSession)field.get(controller); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
    }
    @Test void fontsAreNotEnumeratedUntilTextOpensAndClosedEditorsIgnoreCompletions() throws Exception {
        var tasks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var fonts = new AsyncFontCatalog(() -> {
            assertFalse(Platform.isFxApplicationThread());
            calls.incrementAndGet();
            return List.of("SansSerif", "Test font marker");
        }, tasks::add);
        var source = new GeometryFactory().createGeometryCollection();
        var h = fx(() -> new Harness(source, List.of(), fonts));
        try {
            fx(() -> {
                assertEquals(0, calls.get());
                assertTrue(tasks.isEmpty());
                h.controller.startText();
                assertEquals(1, tasks.size());
                h.controller.cancel();
                h.controller.start(new TreeItem<>("new editor"), source, List.of(), false, "MM");
                h.chrome.applyCss(); h.chrome.layout();
                return null;
            });
            tasks.remove().run();
            fx(() -> {
                var picker = (ComboBox<String>) h.panel.lookup("#geometry-text-font");
                assertFalse(picker.getItems().contains("Test font marker"));
                h.controller.startText();
                return null;
            });
            fx(() -> {
                var picker = (ComboBox<String>) h.panel.lookup("#geometry-text-font");
                assertTrue(picker.getItems().contains("Test font marker"));
                assertEquals("SansSerif", picker.getValue());
                assertFalse(picker.isDisabled());
                assertEquals(1, calls.get());
                return null;
            });
        } finally { fx(() -> { h.controller.cancel(); h.plot.dispose(); return null; }); }
    }
    @Test void editorControlsExposeSpecificHelpWithoutNativeTooltipDuplication() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                PanelTooltips.install(h.panel,"Editor Geometry");
                var text=h.button("geometry-text");
                var eraser=h.button("geometry-eraser");
                assertEquals("Texto vetorial",text.getAccessibleText());
                assertTrue(text.getAccessibleHelp().contains("linha de base"));
                assertTrue(eraser.getAccessibleHelp().contains("todas as ferramentas"));
                assertNull(text.getTooltip()); assertNull(eraser.getTooltip());
                h.controller.startText();
                var size=(TextField) h.panel.lookup("#geometry-text-size");
                var place=(Button) h.panel.lookup("#geometry-text-place");
                assertTrue(size.getAccessibleHelp().contains("0.1"));
                assertTrue(place.getAccessibleHelp().contains("Ctrl+Z"));
                assertNotNull(place.getProperties().get(FluidTooltips.CONTENT_KEY));
                return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
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
    @Test void paintPreviewIsFixedCancelableAndAddsOnlyAfterConfirmation() throws Exception {
        var f=new GeometryFactory(); var source=f.toGeometry(new Envelope(45,55,35,45));
        var h=fx(() -> new Harness(source,List.of(new ToolGeometry(1,source))));
        try {
            fx(() -> {
                h.table().getSelectionModel().select(0); h.button("geometry-paint").fire();
                assertEquals("1.0",((TextField)h.panel.lookup("#geometry-paint-diameter")).getText());
                ((Button)h.panel.lookup("#geometry-paint-preview")).fire(); return null;
            });
            h.finish();
            fx(() -> {
                assertFalse(h.controller.hasUnappliedChanges()); assertTrue(h.plot.isPlacementActive());
                h.plot.cancelPlacement(); assertEquals(1,h.table().getItems().size());
                ((Button)h.panel.lookup("#geometry-paint-preview")).fire(); return null;
            });
            h.finish();
            fx(() -> {
                h.click(60,50); // Confirmation location must NOT translate the generated paths.
                assertTrue(h.controller.hasUnappliedChanges()); assertTrue(h.table().getItems().size()>1);
                h.controller.apply(); assertTrue(source.covers(h.applied)); return null;
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

    @Test void eraserKeepsOriginAcrossTargetsAndUndoRedoWithoutRecomputingItsStencil() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,48,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> { h.table().getSelectionModel().select(0); h.controller.startEraser(); return null; });
            h.finish();
            fx(() -> { h.click(30,30); h.click(40,30); return null; }); h.finish();
            fx(() -> {
                assertEquals(32,h.area(),1e-6); assertTrue(h.controller.isEraserActive());
                assertTrue(h.plot.isPlacementActive()); assertFalse(h.button("geometry-eraser").isDisabled());
                h.click(44,30); assertEquals(GeometryEditSession.Operation.ERASE,h.request.operation()); return null;
            }); h.finish();
            fx(() -> {
                assertEquals(28,h.area(),1e-6); assertTrue(h.plot.isPlacementActive());
                assertTrue(h.controller.undoFromShortcut()); assertEquals(32,h.area(),1e-6);
                assertTrue(h.controller.isEraserActive()); assertTrue(h.plot.isPlacementActive());
                assertTrue(h.controller.redoFromShortcut()); assertEquals(28,h.area(),1e-6);
                assertTrue(h.plot.isPlacementActive());
                h.click(80,70); return null; // A miss must not end the gesture or create undo history.
            }); h.finish();
            fx(() -> {
                assertEquals(28,h.area(),1e-6); assertTrue(h.plot.isPlacementActive());
                assertTrue(h.controller.undoFromShortcut()); assertEquals(32,h.area(),1e-6);
                h.click(44,32); return null;
            }); h.finish();
            fx(() -> {
                assertEquals(28,h.area(),1e-6);
                h.click(44,32,MouseButton.SECONDARY);
                assertFalse(h.controller.isEraserActive()); assertFalse(h.plot.isPlacementActive());
                assertTrue(h.button("geometry-eraser").isDisabled());
                h.controller.apply(); assertEquals(28,h.applied.getArea(),1e-6); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void eraserRetainsItsStencilEvenAfterErasingTheSourceAndCanStopByItsButton() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,44,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> { h.table().getSelectionModel().select(0); h.controller.startEraser(); return null; }); h.finish();
            fx(() -> { h.click(30,30); h.click(30,30); return null; }); h.finish();
            fx(() -> {
                assertEquals(16,h.area(),1e-6); assertEquals(1,h.table().getItems().size());
                h.click(40,30); return null;
            }); h.finish();
            fx(() -> {
                assertEquals(12,h.area(),1e-6); assertTrue(h.controller.isEraserActive());
                h.button("geometry-eraser").fire();
                assertFalse(h.controller.isEraserActive()); assertFalse(h.plot.isPlacementActive());
                assertEquals(12,h.area(),1e-6); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void escapeWhilePreparingOrErasingDiscardsPendingWorkAndNeverRearmsIt() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,44,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> {
                h.table().getSelectionModel().select(0); h.controller.startEraser();
                assertTrue(h.controller.cancelEraserFromShortcut()); assertTrue(h.controller.isBusy()); return null;
            }); h.finish();
            fx(() -> {
                assertFalse(h.controller.isEraserActive()); assertFalse(h.plot.isPlacementActive());
                assertFalse(h.controller.hasUnappliedChanges()); h.controller.startEraser(); return null;
            }); h.finish();
            fx(() -> {
                h.click(30,30); h.click(40,30);
                assertTrue(h.controller.isBusy()); assertTrue(h.controller.cancelEraserFromShortcut()); return null;
            }); h.finish();
            fx(() -> {
                assertFalse(h.controller.isBusy()); assertFalse(h.controller.isEraserActive());
                assertFalse(h.controller.hasUnappliedChanges()); assertFalse(h.plot.isPlacementActive());
                assertEquals(20,h.area(),1e-6); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void selectionChangeAndSwitchingToolsStopRepeatedErasing() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,44,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> { h.table().getSelectionModel().select(0); h.controller.startEraser(); return null; }); h.finish();
            fx(() -> {
                h.click(30,30); h.table().getSelectionModel().clearAndSelect(1);
                assertFalse(h.controller.isEraserActive()); assertFalse(h.plot.isPlacementActive());
                assertEquals(java.util.Set.of(1),h.reflectedSession().selectedIndices());
                h.controller.startEraser(); return null;
            }); h.finish();
            fx(() -> {
                h.controller.startCircle(); assertFalse(h.controller.isEraserActive());
                assertTrue(h.plot.isPlacementActive()); h.plot.cancelPlacement();
                assertFalse(h.controller.hasUnappliedChanges()); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void failedEraseEndsRepeatModeAndKeepsEarlierConfirmedEdits() throws Exception {
        var f=new GeometryFactory();
        Geometry template=f.toGeometry(new Envelope(30,32,30,32)), target=f.toGeometry(new Envelope(40,48,30,34));
        var h=fx(() -> new Harness(f.buildGeometry(List.of(template,target)),List.of()));
        try {
            fx(() -> { h.table().getSelectionModel().select(0); h.controller.startEraser(); return null; }); h.finish();
            fx(() -> { h.click(30,30); h.click(40,30); return null; }); h.finish();
            fx(() -> {
                h.click(44,30); var fail=h.failed; h.request=null; h.failed=null;
                fail.accept("Operação cancelada.");
                assertFalse(h.controller.isBusy()); assertFalse(h.controller.isEraserActive());
                assertFalse(h.plot.isPlacementActive()); assertEquals(32,h.area(),1e-6);
                assertTrue(h.controller.undoFromShortcut()); assertEquals(36,h.area(),1e-6); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void arcPreviewIsCurvedAndThirdClickCommitsWithOneUndoTransaction() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.button("geometry-arc").fire(); assertTrue(h.controller.isArcActive());
                h.click(50,40); h.click(60,40); h.move(50,50);
                assertEquals(0,h.table().getItems().size()); assertFalse(h.controller.hasUnappliedChanges());
                var f=PlotAreaView.class.getDeclaredField("threePointPreview"); f.setAccessible(true);
                var preview=(Geometry)f.get(h.plot);
                assertTrue(preview.getGeometryN(0).getNumPoints()>10);
                double oldLength=preview.getGeometryN(0).getLength();
                assertTrue(h.controller.handleArcKey(KeyCode.D));
                preview=(Geometry)f.get(h.plot); assertTrue(preview.getGeometryN(0).getLength()<oldLength);
                h.click(50,50); assertFalse(h.controller.isArcActive()); assertFalse(h.plot.isPlacementActive());
                assertEquals(1,h.table().getItems().size()); assertTrue(h.controller.undoFromShortcut());
                assertEquals(0,h.table().getItems().size()); assertTrue(h.controller.redoFromShortcut());
                assertEquals(1,h.table().getItems().size()); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void arcModeChangeResetsPointsAndInvalidThirdPointCanBeRetried() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.controller.startArc(); h.click(10,10);
                h.controller.handleArcKey(KeyCode.M); // 12C, first click discarded.
                assertEquals(EditorArc.Mode.START_END_CENTER,((ComboBox<?>)h.panel.lookup("#geometry-arc-mode")).getValue());
                h.click(40,40); h.click(60,40); h.click(50,45);
                assertEquals(1,h.table().getItems().size());
                h.controller.startArc(); h.controller.handleArcKey(KeyCode.M); // 132.
                var direction=(CheckBox)h.panel.lookup("#geometry-arc-clockwise");
                assertTrue(direction.isDisabled());
                boolean old=direction.isSelected(); h.controller.handleArcKey(KeyCode.D); assertEquals(old,direction.isSelected());
                h.click(40,40); h.click(60,40); h.click(50,40); // Collinear.
                assertTrue(h.controller.isArcActive()); assertEquals(1,h.table().getItems().size());
                assertTrue(((Label)h.panel.lookup("#geometry-instruction")).getText().contains("terceiro ponto"));
                h.controller.handleArcKey(KeyCode.BACK_SPACE); // Remove end, retain start.
                h.click(60,40); h.click(50,50);
                assertFalse(h.controller.isArcActive()); assertEquals(2,h.table().getItems().size());
                return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void arcUsesSnapAndRightClickCancelsWithoutCreatingAnIncompleteShape() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.plot.setGridSnap(true,2,2); h.controller.startArc();
                h.click(50.3,40.4); h.click(60.3,40.4); h.move(50.3,50.4);
                h.click(50.3,50.4,MouseButton.SECONDARY);
                assertFalse(h.controller.isArcActive()); assertFalse(h.controller.hasUnappliedChanges());
                h.controller.startArc(); h.click(50.3,40.4); h.click(60.3,40.4); h.click(50.3,50.4);
                assertEquals(1,h.table().getItems().size());
                Geometry expected=EditorArc.build(new GeometryFactory(),EditorArc.Mode.CENTER_START_END,true,
                        List.of(new Coordinate(50,40),new Coordinate(60,40),new Coordinate(50,50))).path();
                assertTrue(expected.equalsTopo(h.reflectedSession().resultGeometry().getGeometryN(0)));
                h.controller.startArc(); h.click(50,40); h.controller.startCircle();
                assertFalse(h.controller.isArcActive()); h.plot.cancelPlacement();
                assertEquals(1,h.table().getItems().size()); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void numericPreviewKeepsAnchorsAndZoomWhileExactInsertionBypassesSnap() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.plot.setGridSnap(true,2,2); h.controller.startArc();
                h.click(50,40); h.click(60,40); double zoom=h.plot.cameraSnapshot().scale();
                assertTrue(h.controller.handleArcKey(KeyCode.J));
                h.coordinateReply.accept(new ArcCoordinateDialog.Choice(new Coordinate(25.3,30.8),false));
                assertEquals(new Coordinate(26,30),h.movedCursor);
                assertEquals(zoom,h.plot.cameraSnapshot().scale()); assertEquals(26,h.plot.cameraSnapshot().centerX());
                assertEquals(0,h.table().getItems().size()); assertTrue(h.controller.isArcActive());
                h.coordinateReply.accept(new ArcCoordinateDialog.Choice(new Coordinate(50.25,50.75),true));
                assertEquals(1,h.table().getItems().size());
                var expected=EditorArc.build(new GeometryFactory(),EditorArc.Mode.CENTER_START_END,true,
                        List.of(new Coordinate(50,40),new Coordinate(60,40),new Coordinate(50.25,50.75))).path();
                assertTrue(expected.equalsTopo(h.reflectedSession().resultGeometry().getGeometryN(0)));
                assertTrue(h.controller.undoFromShortcut()); assertEquals(0,h.table().getItems().size()); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void numericEntryCanSupplyAllPointsButLateDialogRepliesCannotAffectANewGesture() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                h.controller.startArc(); h.controller.handleArcKey(KeyCode.J); var stale=h.coordinateReply;
                h.controller.handleArcKey(KeyCode.M); stale.accept(new ArcCoordinateDialog.Choice(new Coordinate(500,500),true));
                for (Coordinate p : List.of(new Coordinate(40,40),new Coordinate(60,40),new Coordinate(50,45))) {
                    h.controller.handleArcKey(KeyCode.J); h.coordinateReply.accept(new ArcCoordinateDialog.Choice(p,true));
                }
                assertEquals(1,h.table().getItems().size()); assertFalse(h.controller.isArcActive());
                h.controller.startArc(); h.controller.handleArcKey(KeyCode.J); stale=h.coordinateReply;
                h.plot.cancelPlacement(); h.controller.startArc();
                stale.accept(new ArcCoordinateDialog.Choice(new Coordinate(500,500),true));
                assertEquals(1,h.table().getItems().size());
                h.controller.handleArcKey(KeyCode.J); h.coordinateReply.accept(null);
                assertTrue(h.controller.isArcActive()); return null;
            });
        } finally { fx(() -> { h.controller.cancel(); return null; }); }
    }

    @Test void resolutionUpdatesArcPreviewAndSavedDefaultAndCancelsAnOldCirclePreview() throws Exception {
        var h=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
        try {
            fx(() -> {
                var steps=(TextField)h.panel.lookup("#geometry-curve-steps"); steps.setText("4");
                h.controller.startArc(); h.click(50,40); h.click(60,40); h.move(50,50);
                var f=PlotAreaView.class.getDeclaredField("threePointPreview"); f.setAccessible(true);
                assertEquals(4,((Geometry)f.get(h.plot)).getGeometryN(0).getNumPoints());
                steps.setText("128"); assertEquals(97,((Geometry)f.get(h.plot)).getGeometryN(0).getNumPoints());
                assertEquals(64,ToolDefaults.integer("geometry.editorcirclesteps"));
                ((Button)h.panel.lookup("#geometry-curve-steps-save")).fire();
                assertEquals(128,ToolDefaults.integer("geometry.editorcirclesteps"));
                h.click(50,50); assertEquals(97,h.reflectedSession().resultGeometry().getGeometryN(0).getNumPoints());
                steps.setText("3"); h.controller.startArc(); assertFalse(h.controller.isArcActive());
                steps.setText("16"); h.controller.startCircle(); h.click(50,40); steps.setText("65");
                assertFalse(h.plot.isPlacementActive()); h.controller.startCircle(); h.click(50,40); h.click(60,40);
                assertEquals(65,h.reflectedSession().resultGeometry().getGeometryN(1).getNumPoints());
                return null;
            });
            var next=fx(() -> new Harness(new GeometryFactory().createGeometryCollection(),List.of()));
            try { fx(() -> { assertEquals("128",((TextField)next.panel.lookup("#geometry-curve-steps")).getText()); return null; }); }
            finally { fx(() -> { next.controller.cancel(); return null; }); }
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
