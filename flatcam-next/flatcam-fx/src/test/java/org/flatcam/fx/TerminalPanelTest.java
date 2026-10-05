package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.tcl.TclInterpreter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class TerminalPanelTest {
    static <T> T fx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> { }); }
        catch (IllegalStateException alreadyRunning) { /* shared toolkit */ }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(25, TimeUnit.SECONDS);
    }

    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final TerminalPanel panel;
        Session() throws Exception {
            panel = fx(() -> {
                TerminalPanel created = new TerminalPanel(new TclInterpreter(), "versao de teste", jobs);
                new Scene(created);
                created.applyCss(); created.layout();
                return created;
            });
        }
        CompletableFuture<Void> start(String command) throws Exception {
            return fx(() -> {
                TextField input = input();
                input.setText(command);
                input.getOnAction().handle(new ActionEvent());
                return panel.whenIdle();
            });
        }
        void submit(String command) throws Exception { start(command).get(25, TimeUnit.SECONDS); }
        TextField input() { return (TextField) panel.lookup("#terminal-input"); }
        String text() throws Exception { return fx(() -> ((TextArea) panel.lookup("#terminal-output")).getText()); }
        @Override public void close() throws Exception {
            fx(() -> { panel.cancel(); return null; });
            panel.whenIdle().get(25, TimeUnit.SECONDS);
            jobs.shutdown();
        }
    }

    @Test void submittingACommandEchoesItAndShowsItsResult() throws Exception {
        try (Session session = new Session()) {
            session.submit("set x 5");
            assertTrue(session.text().contains("> set x 5\n5"));
            session.submit("expr $x + 1");
            assertTrue(session.text().contains("\n6\n"));
        }
    }

    @Test void anUndefinedCommandShowsAnErrorLineInsteadOfThrowing() throws Exception {
        try (Session session = new Session()) {
            session.submit("this_is_not_a_real_command");
            assertTrue(session.text().contains("ERRO: invalid command name"));
            assertFalse(fx(session.panel::isBusy));
            session.submit("set recovered 1");
            assertTrue(session.text().endsWith("1\n"));
        }
    }

    @Test void helpVersionAndClearShellAreBuiltIn() throws Exception {
        try (Session session = new Session()) {
            session.submit("version");
            assertTrue(session.text().endsWith("versao de teste\n"));
            session.submit("help");
            assertTrue(session.text().contains("puts"));
            assertTrue(session.text().contains("foreach"));
            session.submit("clear_shell");
            assertEquals("", session.text());
        }
    }

    @Test void upAndDownArrowsNavigateCommandHistory() throws Exception {
        try (Session session = new Session()) {
            session.submit("set a 1"); session.submit("set b 2");
            fx(() -> {
                TextField input = session.input(); input.setText("still typing");
                pressKey(input, KeyCode.UP); assertEquals("set b 2", input.getText());
                pressKey(input, KeyCode.UP); assertEquals("set a 1", input.getText());
                pressKey(input, KeyCode.DOWN); assertEquals("set b 2", input.getText());
                pressKey(input, KeyCode.DOWN); assertEquals("still typing", input.getText());
                return null;
            });
        }
    }

    @Test void registeringACommandThroughInterpreterMakesItAvailableFromTheInputLine() throws Exception {
        try (Session session = new Session()) {
            fx(() -> { session.panel.interpreter().register("open_gerber", (interp, args) -> "gerber_obj"); return null; });
            session.submit("open_gerber test.gbr -outname gerber_obj");
            assertTrue(session.text().endsWith("gerber_obj\n"));
        }
    }

    @Test void workerKeepsFxResponsiveAndRejectsConcurrentSubmission() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (Session session = new Session()) {
            fx(() -> {
                session.panel.interpreter().register("work", (interp, args) -> {
                    assertFalse(Platform.isFxApplicationThread());
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    TclExecution.progress("Etapa de teste").report(0.5);
                    return "feito";
                });
                return null;
            });
            CompletableFuture<Void> idle = session.start("work");
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> {
                assertTrue(session.panel.isBusy());
                assertTrue(session.input().isDisabled());
                session.input().setText("set concurrent 1");
                session.input().getOnAction().handle(new ActionEvent());
                assertEquals("set concurrent 1", session.input().getText());
                assertSame(idle, session.panel.whenIdle());
                assertFalse(idle.isDone());
                return null;
            });
            release.countDown(); idle.get(25, TimeUnit.SECONDS);
            assertTrue(session.text().endsWith("feito\n"));
            session.submit("set concurrent");
            assertTrue(session.text().contains("no such variable"));
        } finally { release.countDown(); }
    }

    @Test void cancelStopsNestedLoopsAndAllowsTheNextCommand() throws Exception {
        try (Session session = new Session()) {
            CountDownLatch entered = new CountDownLatch(1);
            fx(() -> { session.panel.interpreter().register("entered", (interp, args) -> { entered.countDown(); return ""; }); return null; });
            CompletableFuture<Void> idle = session.start("set x 0; entered; while {1} {incr x}");
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> { ((Button) session.panel.lookup("#terminal-cancel")).fire(); return null; });
            idle.get(25, TimeUnit.SECONDS);
            assertTrue(session.text().contains("Cancelado. Comandos ja concluidos nao sao desfeitos."));
            session.submit("set x");
            assertFalse(session.text().endsWith("no such variable\n"));
            assertFalse(fx(session.panel::isBusy));
        }
    }

    @Test void progressIsDeliveredOnFxAndOutputIsBounded() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (Session session = new Session()) {
            fx(() -> {
                session.panel.interpreter().register("progress_test", (interp, args) -> {
                    TclExecution.progress("Etapa de teste").report(0.5);
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    return "x".repeat(250_000);
                });
                return null;
            });
            CompletableFuture<Void> idle = session.start("progress_test");
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(0.5, fx(() -> ((ProgressBar) session.panel.lookup("#terminal-progress")).getProgress()));
            release.countDown(); idle.get(25, TimeUnit.SECONDS);
            assertEquals(200_000, session.text().length());
        } finally { release.countDown(); }
    }

    private static void pressKey(TextField input, KeyCode code) {
        input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
}
