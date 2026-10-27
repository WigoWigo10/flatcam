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
                var method=MainWindow.class.getDeclaredMethod("saveGCodeDraftAs",String.class,Consumer.class); method.setAccessible(true);
                assertEquals(true,method.invoke(session.window,"G21\nG90\nM5\n",(Consumer<String>)message->{})); return null;
            });
            session.release.countDown(); var report=review.get(15,TimeUnit.SECONDS);
            assertEquals("Rascunho do editor",report.source());
            assertTrue(report.issues().stream().anyMatch(issue->issue.text().contains("não aplica")));
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
}
