package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class MainReviewedExportTest {
    @TempDir Path directory;
    static void set(MainWindow window,String name,Object value) throws Exception {
        var field=MainWindow.class.getDeclaredField(name); field.setAccessible(true); field.set(window,value);
    }
    static MainWindow.PendingCncProgram program(String text,Consumer<Path> saved) {
        assertFalse(Platform.isFxApplicationThread(),"Generation/analysis must not run on FX");
        return new MainWindow.PendingCncProgram(text,null,"Dispensação, não fresagem",saved);
    }
    static Object get(MainWindow window,String name) throws Exception {
        var field=MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field.get(window);
    }
    @SuppressWarnings("unchecked")
    static javafx.scene.control.TreeItem<String> startEditor(MainWindow window,Path source,String text) throws Exception {
        var add=MainWindow.class.getDeclaredMethod("addCncJobToProject",String.class,String.class,Path.class,String.class,
                org.locationtech.jts.geom.Geometry.class,org.locationtech.jts.geom.Geometry.class);
        add.setAccessible(true);
        var item=(javafx.scene.control.TreeItem<String>)add.invoke(window,"fixture.nc","fixture",source,text,null,null);
        ((GCodeEditorController)get(window,"gcodeEditor")).start(item,text,MainWindow.originalGCodeFile(source));
        return item;
    }
    @Test void reviewPrecedesDestinationAndPublishesOnlyTheReviewedDraft() throws Exception {
        try(var session=new MainCamFlowTest.Session("MM")) {
            var reviewed=new AtomicBoolean(); var published=new AtomicBoolean(); var done=new CompletableFuture<String>();
            Path destination=directory.resolve("draft.nc"); Files.writeString(destination,"old");
            String text="G21\nG90\nG0 Z3\nM5\n";
            TerminalPanelTest.fx(()->{
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->{
                    assertTrue(Platform.isFxApplicationThread()); assertEquals("Rascunho",report.source());
                    assertTrue(report.issues().stream().anyMatch(i->i.text().contains("Dispensação")));
                    reviewed.set(true); return true;
                });
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,actual)->{
                    assertTrue(reviewed.get()); assertEquals(text,actual); assertEquals("draft.nc",name); return destination.toFile();
                });
                set(session.window,"cncExportRemember",(Consumer<Path>)path->{}); // no real user preference writes
                assertTrue(session.window.saveReviewedProgram("Rascunho","draft.nc",()->program(text,path->{
                    assertTrue(Platform.isFxApplicationThread()); published.set(true);
                }),()->true,done::complete)); return null;
            });
            session.release.countDown(); assertTrue(done.get(15,TimeUnit.SECONDS).contains("Arquivo salvo"));
            assertTrue(published.get()); assertEquals(text,Files.readString(destination));
            assertEquals(1,session.geometries().size(),"Saving a draft cannot replace the CAM source");
        }
    }
    @Test void decliningReviewNeverOpensDestinationOrCreatesAFile() throws Exception {
        try(var session=new MainCamFlowTest.Session("MM")) {
            var done=new CompletableFuture<String>();
            TerminalPanelTest.fx(()->{
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->false);
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->{ fail("Review declined"); return null; });
                session.window.saveReviewedProgram("Calibration","verify.nc",()->program("G21",path->fail()),()->true,done::complete);
                return null;
            });
            session.release.countDown(); assertTrue(done.get(15,TimeUnit.SECONDS).contains("cancelada"));
            try(var files=Files.list(directory)) { assertEquals(0,files.count()); }
        }
    }
    @Test void actualEditorSaveEntryPointRequiresReviewBeforeChoosingAFile() throws Exception {
        try(var session=new MainCamFlowTest.Session("MM")) {
            var review=new CompletableFuture<CncPreflight.Report>();
            TerminalPanelTest.fx(()->{
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->{review.complete(report);return false;});
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->{fail("Declined");return null;});
                var item=startEditor(session.window,Path.of("embedded.nc"),"G21\nG90\nM5\n");
                assertTrue(session.window.saveGCodeDraft(item,"G21\nG90\nM5\n",null,true,result->{})); return null;
            });
            session.release.countDown(); var report=review.get(15,TimeUnit.SECONDS);
            assertEquals("Rascunho do editor",report.source());
            assertTrue(report.issues().stream().anyMatch(issue->issue.text().toLowerCase().contains("não aplica")));
            session.awaitUi();
        }
    }
    @Test void staleSourceAfterAnalysisAndDuringFileDialogIsDiscarded() throws Exception {
        for(boolean changeInDialog:new boolean[]{false,true}) try(var session=new MainCamFlowTest.Session("MM")) {
            var valid=new AtomicBoolean(true); var done=new CompletableFuture<String>();
            Path destination=directory.resolve("paste.nc"); Files.writeString(destination,"old");
            TerminalPanelTest.fx(()->{
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->{ assertTrue(changeInDialog); return true; });
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->{
                    valid.set(false); return destination.toFile();
                });
                session.window.saveReviewedProgram("SolderPaste","paste.nc",()->program("G21",path->fail()),valid::get,done::complete);
                if(!changeInDialog) valid.set(false); return null;
            });
            session.release.countDown(); assertTrue(done.get(15,TimeUnit.SECONDS).contains("descartad"));
            assertEquals("old",Files.readString(destination));
        }
    }
    @Test void editorSaveOverwritesOriginalAndSaveAsWritesOnlyTheCopy() throws Exception {
        for (boolean copy : new boolean[]{false,true}) try(var session=new MainCamFlowTest.Session("MM")) {
            Path original=directory.resolve(copy ? "original-copy-case.nc" : "original-save-case.nc");
            Path destination=copy ? directory.resolve("copy.nc") : original;
            String old="G21\nG90\nM5\n", draft="G21\nG90\nG0 Z3\nM5\n";
            Files.writeString(original,old);
            var done=new CompletableFuture<GCodeEditorController.SaveResult>(); var chosen=new AtomicInteger();
            var item=TerminalPanelTest.fx(()->{
                var editing=startEditor(session.window,original,old);
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->true);
                set(session.window,"cncExportRemember",(Consumer<Path>)path->{});
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->{
                    assertTrue(copy,"Save must not ask for another destination");
                    assertTrue(name.contains("_copy")); chosen.incrementAndGet(); return destination.toFile();
                });
                assertTrue(session.window.saveGCodeDraft(editing,draft,copy ? null : original,copy,done::complete));
                return editing;
            });
            session.release.countDown(); assertEquals(destination,done.get(15,TimeUnit.SECONDS).file());
            assertEquals(draft,Files.readString(destination)); assertEquals(copy ? old : draft,Files.readString(original));
            assertEquals(copy ? 1 : 0,chosen.get());
            TerminalPanelTest.fx(()->{
                var entry=((java.util.Map<?,?>)get(session.window,"cncJobByItem")).get(item);
                var gcode=entry.getClass().getDeclaredMethod("gcode"); gcode.setAccessible(true);
                assertEquals(old,gcode.invoke(entry),"Saving files does not apply to the in-memory CNC Job");
                ((GCodeEditorController)get(session.window,"gcodeEditor")).cancel(); return null;
            });
        }
    }
    @Test void embeddedJobFirstSaveAssociatesOnlyTheChosenFile() throws Exception {
        try(var session=new MainCamFlowTest.Session("MM")) {
            Path destination=directory.resolve("first-save.nc"); var done=new CompletableFuture<GCodeEditorController.SaveResult>();
            var item=TerminalPanelTest.fx(()->{
                var editing=startEditor(session.window,Path.of("embedded.nc"),"G21\nM5\n");
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->true);
                set(session.window,"cncExportRemember",(Consumer<Path>)path->{});
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->destination.toFile());
                assertTrue(session.window.saveGCodeDraft(editing,"G21\nM5\n",null,false,done::complete)); return editing;
            });
            session.release.countDown(); assertEquals(destination,done.get(15,TimeUnit.SECONDS).file());
            TerminalPanelTest.fx(()->{
                var entry=((java.util.Map<?,?>)get(session.window,"cncJobByItem")).get(item);
                var path=entry.getClass().getDeclaredMethod("outputFile"); path.setAccessible(true);
                assertEquals(destination,path.invoke(entry));
                ((GCodeEditorController)get(session.window,"gcodeEditor")).cancel(); return null;
            });
        }
    }
    @Test void saveAsCannotOverwriteTheAssociatedOriginal() throws Exception {
        Path original=directory.resolve("original.nc"); Files.writeString(original,"G21\nM5\n");
        try(var session=new MainCamFlowTest.Session("MM")) {
            var done=new CompletableFuture<GCodeEditorController.SaveResult>();
            TerminalPanelTest.fx(()->{
                var item=startEditor(session.window,original,"G21\nM5\n");
                set(session.window,"cncExportDecision",(Predicate<CncPreflight.Report>)report->true);
                set(session.window,"cncExportDestination",(BiFunction<String,String,File>)(name,text)->original.toFile());
                assertTrue(session.window.saveGCodeDraft(item,"G21\nG0 Z3\nM5\n",null,true,done::complete)); return null;
            });
            session.release.countDown(); assertNull(done.get(15,TimeUnit.SECONDS).file());
            assertEquals("G21\nM5\n",Files.readString(original));
            TerminalPanelTest.fx(()->{ ((GCodeEditorController)get(session.window,"gcodeEditor")).cancel(); return null; });
        }
    }
    @Test void projectPathsAndEmbeddedPlaceholdersAreNeverOriginalGcodeFiles() throws Exception {
        Path project=directory.resolve("board.FlatPrj"); Files.writeString(project,"project");
        assertNull(MainWindow.originalGCodeFile(project)); assertNull(MainWindow.originalGCodeFile(Path.of("embedded.nc")));
        assertNull(MainWindow.originalGCodeFile(directory.resolve("missing.nc")));
        Path original=directory.resolve("real.nc"); Files.writeString(original,"G21\n");
        assertEquals(original,MainWindow.originalGCodeFile(original));
    }
}
