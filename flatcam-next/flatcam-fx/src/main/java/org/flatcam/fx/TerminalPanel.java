package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;

/**
 * The FX port of Python FlatCAM's TCL Shell ({@code appTools/ToolShell.py}'s {@code TermWidget}):
 * a read-only output scrollback plus a single-line input with command history (Up/Down), driving
 * a {@link TclInterpreter}. Unlike Python's real embedded Tcl 8.6, the interpreter here is the
 * deliberately reduced dialect documented on {@link TclInterpreter} - a script leaning on more of
 * real Tcl than that (procs, real list quoting, {@code catch}/{@code switch}/{@code string}) will
 * not run here. {@code help}/{@code version}/{@code clear_shell} are registered directly on this
 * panel, the same way Python's {@code TclCommandHelp}/{@code TclCommandVersion}/
 * {@code TclCommandClearShell} are shell-level utilities rather than CAM operations; the
 * FlatCAM-specific commands (open_gerber, isolate, cutout, ...) are registered from outside via
 * {@link #interpreter()}.
 */
final class TerminalPanel extends BorderPane {
    private final TextArea output = new TextArea();
    private final TextField input = new TextField();
    private final TclInterpreter interpreter;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = 0; // == history.size() means "not browsing", i.e. the live draft
    private String draftBeforeBrowsing = "";

    TerminalPanel(TclInterpreter interpreter, String versionLabel) {
        this.interpreter = interpreter;
        registerShellCommands(versionLabel);

        output.setId("terminal-output");
        output.setEditable(false);
        output.setWrapText(true);
        output.setStyle("-fx-font-family: 'Consolas', 'Monospaced';");
        output.setFocusTraversable(false);

        input.setId("terminal-input");
        input.setStyle("-fx-font-family: 'Consolas', 'Monospaced';");
        input.setPromptText("Digite um comando (help para comecar)...");
        input.setOnKeyPressed(this::onKeyPressed);
        input.setOnAction(event -> submit());

        interpreter.setOutputSink(output::appendText);

        setCenter(output);
        setBottom(input);
        BorderPane.setMargin(input, new Insets(4, 0, 0, 0));
        getStyleClass().add("terminal-panel");

        appendLine("FlatCAM FX - " + versionLabel + " - linha de comando Tcl (dialeto reduzido, nao e o Tcl completo do Python).");
        appendLine("Digite 'help' para comecar.\n");
    }

    TclInterpreter interpreter() {
        return interpreter;
    }

    void focusInput() {
        input.requestFocus();
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.UP) {
            browseHistory(-1);
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            browseHistory(1);
            event.consume();
        }
    }

    private void browseHistory(int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == history.size()) {
            draftBeforeBrowsing = input.getText();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setText(historyIndex == history.size() ? draftBeforeBrowsing : history.get(historyIndex));
        input.positionCaret(input.getText().length());
    }

    private void submit() {
        String command = input.getText();
        input.clear();
        if (command.isBlank()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(command)) {
            history.add(command);
        }
        historyIndex = history.size();
        appendLine("> " + command);
        try {
            String result = interpreter.eval(command);
            if (!result.isBlank()) {
                appendLine(result);
            }
        } catch (TclException error) {
            appendLine("ERRO: " + error.getMessage());
        } catch (RuntimeException error) {
            appendLine("ERRO inesperado: " + error.getMessage());
        }
    }

    private void appendLine(String text) {
        output.appendText(text + "\n");
    }

    private void registerShellCommands(String versionLabel) {
        interpreter.register("version", (interp, args) -> versionLabel);
        interpreter.register("help", (interp, args) -> {
            if (!args.isEmpty()) {
                // No per-command structured help text yet (Python's get_decorated_help()) -
                // at least confirm the name exists rather than silently doing nothing.
                String name = args.get(0);
                return interp.commandNames().contains(name)
                        ? name + ": sem texto de ajuda detalhado ainda."
                        : "Comando desconhecido: " + name;
            }
            return "Comandos disponiveis:\n" + String.join("\n", new TreeSet<>(interp.commandNames()));
        });
        interpreter.register("clear_shell", (interp, args) -> {
            output.clear();
            return "";
        });
    }
}
