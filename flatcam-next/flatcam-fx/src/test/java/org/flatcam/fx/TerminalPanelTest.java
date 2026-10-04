package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.flatcam.cam.tcl.TclInterpreter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class TerminalPanelTest {

    @Test
    void submittingACommandEchoesItAndShowsItsResult() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine - a previous test in this run already started the toolkit
        }
        var task = new FutureTask<Void>(() -> {
            TerminalPanel panel = new TerminalPanel(new TclInterpreter(), "em desenvolvimento");
            new Scene((Parent) panel);
            panel.applyCss();
            panel.layout();
            TextArea output = (TextArea) panel.lookup("#terminal-output");
            TextField input = (TextField) panel.lookup("#terminal-input");

            submit(input, "set x 5");
            assertTrue(output.getText().contains("> set x 5"));
            assertTrue(output.getText().contains("5"));

            submit(input, "expr $x + 1");
            assertTrue(output.getText().contains("6"));
            return null;
        });
        Platform.runLater(task);
        task.get(25, TimeUnit.SECONDS);
    }

    @Test
    void anUndefinedCommandShowsAnErrorLineInsteadOfThrowing() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }
        var task = new FutureTask<Void>(() -> {
            TerminalPanel panel = new TerminalPanel(new TclInterpreter(), "em desenvolvimento");
            new Scene((Parent) panel);
            panel.applyCss();
            panel.layout();
            TextArea output = (TextArea) panel.lookup("#terminal-output");
            TextField input = (TextField) panel.lookup("#terminal-input");

            submit(input, "this_is_not_a_real_command");
            assertTrue(output.getText().contains("ERRO"));
            assertTrue(output.getText().contains("this_is_not_a_real_command"));
            return null;
        });
        Platform.runLater(task);
        task.get(25, TimeUnit.SECONDS);
    }

    @Test
    void helpVersionAndClearShellAreBuiltIn() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }
        var task = new FutureTask<Void>(() -> {
            TerminalPanel panel = new TerminalPanel(new TclInterpreter(), "versao de teste");
            new Scene((Parent) panel);
            panel.applyCss();
            panel.layout();
            TextArea output = (TextArea) panel.lookup("#terminal-output");
            TextField input = (TextField) panel.lookup("#terminal-input");

            submit(input, "version");
            assertTrue(output.getText().contains("versao de teste"));

            submit(input, "help");
            assertTrue(output.getText().contains("puts"));
            assertTrue(output.getText().contains("foreach"));

            submit(input, "clear_shell");
            assertEquals("", output.getText());
            return null;
        });
        Platform.runLater(task);
        task.get(25, TimeUnit.SECONDS);
    }

    @Test
    void upAndDownArrowsNavigateCommandHistory() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }
        var task = new FutureTask<Void>(() -> {
            TerminalPanel panel = new TerminalPanel(new TclInterpreter(), "em desenvolvimento");
            new Scene((Parent) panel);
            panel.applyCss();
            panel.layout();
            TextField input = (TextField) panel.lookup("#terminal-input");

            submit(input, "set a 1");
            submit(input, "set b 2");
            input.setText("still typing");

            pressKey(input, KeyCode.UP);
            assertEquals("set b 2", input.getText());
            pressKey(input, KeyCode.UP);
            assertEquals("set a 1", input.getText());
            pressKey(input, KeyCode.DOWN);
            assertEquals("set b 2", input.getText());
            pressKey(input, KeyCode.DOWN);
            assertEquals("still typing", input.getText());
            return null;
        });
        Platform.runLater(task);
        task.get(25, TimeUnit.SECONDS);
    }

    @Test
    void registeringACommandThroughInterpreterMakesItAvailableFromTheInputLine() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }
        var task = new FutureTask<Void>(() -> {
            TerminalPanel panel = new TerminalPanel(new TclInterpreter(), "em desenvolvimento");
            panel.interpreter().register("open_gerber", (interp, args) -> "gerber_obj");
            new Scene((Parent) panel);
            panel.applyCss();
            panel.layout();
            TextArea output = (TextArea) panel.lookup("#terminal-output");
            TextField input = (TextField) panel.lookup("#terminal-input");

            submit(input, "open_gerber test.gbr -outname gerber_obj");
            assertTrue(output.getText().contains("gerber_obj"));
            return null;
        });
        Platform.runLater(task);
        task.get(25, TimeUnit.SECONDS);
    }

    private static void submit(TextField input, String command) {
        input.setText(command);
        input.getOnAction().handle(new ActionEvent());
    }

    private static void pressKey(TextField input, KeyCode code) {
        input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
}
