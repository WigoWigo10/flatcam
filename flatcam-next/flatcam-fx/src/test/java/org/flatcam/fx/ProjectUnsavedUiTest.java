package org.flatcam.fx;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import javafx.scene.control.*;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class ProjectUnsavedUiTest {
    @TempDir Path directory;
    static Object invoke(MainWindow window,String name,Class<?>[] types,Object... args) throws Exception {
        var method=MainWindow.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(window,args);
    }
    static Object field(MainWindow window,String name) throws Exception { var f=MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f.get(window); }
    final class Session implements AutoCloseable {
        final Preferences prefs=Preferences.userRoot().node("flatcam-fx-tests/unsaved-"+UUID.randomUUID());
        final JobExecutor jobs=new JobExecutor(1);
        final ExecutorService worker=Executors.newSingleThreadExecutor();
        final MainWindow window;
        final TreeItem<String> item;
        Session() throws Exception {
            window=TerminalPanelTest.fx(() -> { var w=new MainWindow(jobs,new RecentFiles(prefs)); w.createScene(); return w; });
            item=TerminalPanelTest.fx(() -> {
                @SuppressWarnings("unchecked") var result=(TreeItem<String>)invoke(window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},
                        "board","source","MM",new GeometryFactory().toGeometry(new Envelope(0,10,0,10)),false); return result;
            });
        }
        void save(Path path) throws Exception { worker.submit(() -> { window.saveProject(path); return null; }).get(15,TimeUnit.SECONDS); }
        public void close() throws Exception {
            TerminalPanelTest.fx(() -> { ((FluidTooltips)((javafx.scene.Scene)field(window,"scene")).getProperties().get("fx.tip.handler")).closeNow(); window.disposeViewport(); return null; });
            worker.shutdownNow(); jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }
    @Test void realSaveResetsOnlyTheSavedVersionAndCloseChoicesProtectTheCurrentObjects() throws Exception {
        try(var s=new Session()) {
            assertTrue(TerminalPanelTest.fx(s.window::hasUnsavedProjectChanges));
            s.save(directory.resolve("saved.fcnproj")); assertFalse(TerminalPanelTest.fx(s.window::hasUnsavedProjectChanges));
            TerminalPanelTest.fx(() -> {
                var closed=new java.util.concurrent.atomic.AtomicInteger();
                s.window.setProjectDecisionProvider(draft -> { fail("clean close must not ask"); return UnsavedChangesGuard.Choice.CANCEL; });
                s.window.requestClose(closed::incrementAndGet); assertEquals(1,closed.get());
                s.item.setValue("renamed"); assertTrue(s.window.hasUnsavedProjectChanges());
                s.window.setProjectDecisionProvider(draft -> UnsavedChangesGuard.Choice.CANCEL);
                s.window.requestClose(closed::incrementAndGet); assertEquals(1,closed.get());
                assertEquals("renamed",s.item.getValue());
                s.window.setProjectDecisionProvider(draft -> UnsavedChangesGuard.Choice.SAVE);
                s.window.setGuardedProjectSave(done -> done.accept(false));
                s.window.requestClose(closed::incrementAndGet); assertEquals(1,closed.get());
                s.window.setProjectDecisionProvider(draft -> UnsavedChangesGuard.Choice.DISCARD);
                s.window.requestClose(closed::incrementAndGet); assertEquals(2,closed.get());
                assertEquals(1,((Map<?,?>)field(s.window,"geometryByItem")).size()); return null;
            });
        }
    }
    @Test void appearanceIsProjectDataButCameraAndSelectionAreNot() throws Exception {
        try(var s=new Session()) {
            s.save(directory.resolve("appearance.fcnproj"));
            TerminalPanelTest.fx(() -> {
                var plot=(PlotAreaView)field(s.window,"plotAreaView"); plot.fitAllVisible();
                @SuppressWarnings("unchecked") var tree=(TreeView<String>)field(s.window,"projectTree"); tree.getSelectionModel().select(s.item);
                assertFalse(s.window.hasUnsavedProjectChanges()); plot.setLayerVisible(s.item,false); assertTrue(s.window.hasUnsavedProjectChanges());
                plot.setLayerVisible(s.item,true); assertFalse(s.window.hasUnsavedProjectChanges()); return null;
            });
        }
    }
    @Test void terminalCannotReplaceAnUnsavedProjectAndMissingTargetDoesNotDiscardCurrentData() throws Exception {
        try(var s=new Session()) {
            Path missing=directory.resolve("missing.fcnproj");
            var failure=assertThrows(ExecutionException.class,() -> s.worker.submit(() -> { s.window.openProject(missing); return null; }).get(15,TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("save_project"));
            TerminalPanelTest.fx(() -> {
                s.window.setProjectDecisionProvider(draft -> UnsavedChangesGuard.Choice.CANCEL);
                invoke(s.window,"openProjectFromMenu",new Class<?>[]{Path.class},missing);
                assertNull(field(s.window,"runningJob")); assertEquals("board",s.item.getValue()); return null;
            });
        }
    }
    @Test void actualEditorDraftMustBeAppliedOrExplicitlyDiscardedBeforeClose() throws Exception {
        try(var s=new Session()) {
            s.save(directory.resolve("draft.fcnproj"));
            TerminalPanelTest.fx(() -> {
                var editor=(GeometryEditorController)field(s.window,"geometryEditor");
                Geometry shape=new GeometryFactory().toGeometry(new Envelope(0,10,0,10));
                editor.start(s.item,shape,List.of(),false,"MM");
                var selection=GeometryEditorController.class.getDeclaredField("shapeTable"); selection.setAccessible(true);
                ((TableView<?>)selection.get(editor)).getSelectionModel().select(0); editor.deleteFromShortcut();
                assertTrue(editor.hasUnappliedChanges()); var closed=new java.util.concurrent.atomic.AtomicInteger();
                s.window.setProjectDecisionProvider(draft -> { assertTrue(draft); return UnsavedChangesGuard.Choice.CANCEL; });
                s.window.requestClose(closed::incrementAndGet); assertEquals(0,closed.get()); assertTrue(editor.hasUnappliedChanges());
                s.window.setProjectDecisionProvider(draft -> { assertTrue(draft); return UnsavedChangesGuard.Choice.SAVE; });
                s.window.requestClose(closed::incrementAndGet); assertEquals(0,closed.get()); assertTrue(editor.hasUnappliedChanges());
                s.window.setProjectDecisionProvider(draft -> { assertTrue(draft); return UnsavedChangesGuard.Choice.DISCARD; });
                s.window.requestClose(closed::incrementAndGet); assertEquals(1,closed.get()); assertFalse(editor.isActive());
                assertFalse(s.window.hasUnsavedProjectChanges()); assertEquals(1,((Map<?,?>)field(s.window,"geometryByItem")).size()); return null;
            });
        }
    }
    @Test void forceLoadIsExplicitAndStillKeepsMalformedTargetsFromReplacingObjects() throws Exception {
        try(var s=new Session()) {
            Path missing=directory.resolve("missing.fcnproj");
            assertThrows(ExecutionException.class,() -> s.worker.submit(() -> { s.window.openProject(missing,true); return null; }).get(15,TimeUnit.SECONDS));
            TerminalPanelTest.fx(() -> { assertEquals("board",s.item.getValue()); assertTrue(s.window.hasUnsavedProjectChanges()); return null; });
        }
    }
    @Test void deferredExitUsesTheCurrentWindowActionAfterASave() throws Exception {
        try(var s=new Session()) {
            var old=new java.util.concurrent.atomic.AtomicInteger(); var current=new java.util.concurrent.atomic.AtomicInteger();
            var done=new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Boolean>>();
            TerminalPanelTest.fx(() -> {
                s.window.setApplicationClose(old::incrementAndGet);
                s.window.setProjectDecisionProvider(draft -> UnsavedChangesGuard.Choice.SAVE); s.window.setGuardedProjectSave(done::set);
                s.window.requestApplicationClose(); assertNotNull(done.get()); assertEquals(0,old.get()); return null;
            });
            s.save(directory.resolve("current-window.fcnproj"));
            TerminalPanelTest.fx(() -> {
                s.window.setApplicationClose(current::incrementAndGet); done.get().accept(true);
                assertEquals(0,old.get()); assertEquals(1,current.get()); return null;
            });
        }
    }
}
