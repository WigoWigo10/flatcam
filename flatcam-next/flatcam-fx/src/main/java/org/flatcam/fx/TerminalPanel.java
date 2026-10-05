package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
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
    private final JobExecutor jobs;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
    private final Label activity = new Label("Pronto.");
    private final ProgressBar progress = new ProgressBar();
    private final Button cancel = new Button("Cancelar");
    private JobHandle<String> running;
    private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);
    private long executionId;
    private record Progress(long id, double fraction, String message) { }
    private final AtomicReference<Progress> pendingProgress = new AtomicReference<>();
    private final AtomicBoolean progressQueued = new AtomicBoolean();
    private static final int MAX_OUTPUT_CHARS = 200_000;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = 0; // == history.size() means "not browsing", i.e. the live draft
    private String draftBeforeBrowsing = "";

    TerminalPanel(TclInterpreter interpreter, String versionLabel, JobExecutor jobs) {
        this.interpreter = interpreter;
        this.jobs = jobs;
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
        input.disableProperty().bind(busy);

        interpreter.setOutputSink(text -> TclExecution.onFx(() -> { appendText(text); return null; }));
        activity.setId("terminal-activity");
        progress.setId("terminal-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.visibleProperty().bind(busy);
        progress.managedProperty().bind(busy);
        cancel.setId("terminal-cancel");
        cancel.disableProperty().bind(busy.not());
        cancel.setOnAction(event -> cancel());
        HBox state = new HBox(8, activity, progress, cancel);
        HBox.setHgrow(progress, Priority.ALWAYS);
        state.setPadding(new Insets(4, 0, 0, 0));
        setOnKeyPressed(event -> {
            if (busy.get() && event.getCode() == KeyCode.ESCAPE) {
                cancel(); event.consume();
            }
        });

        setCenter(output);
        setBottom(new VBox(4, input, state));
        BorderPane.setMargin(input, new Insets(4, 0, 0, 0));
        getStyleClass().add("terminal-panel");

        appendLine("FlatCAM FX - " + versionLabel + " - linha de comando Tcl (dialeto reduzido, nao e o Tcl completo do Python).");
        appendLine("Digite 'help' para comecar.\n");
    }

    TclInterpreter interpreter() {
        return interpreter;
    }

    boolean isBusy() { return busy.get(); }
    CompletableFuture<Void> whenIdle() { return idle; }

    void cancel() {
        if (running != null) {
            running.cancel();
            activity.setText("Cancelando...");
        }
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
        if (busy.get()) return;
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
        busy.set(true);
        activity.setText("Executando...");
        progress.setProgress(-1);
        long id = ++executionId;
        idle = new CompletableFuture<>();
        running = jobs.submit(context -> TclExecution.run(context,
                () -> interpreter.eval(command, context::isCancelled)),
                (fraction, message) -> reportProgress(new Progress(id, fraction, message)));
        running.completion().whenComplete((result, failure) -> Platform.runLater(() -> {
            if (id != executionId) return;
            pendingProgress.set(null);
            if (failure instanceof CancellationException) {
                appendLine("Cancelado. Comandos ja concluidos nao sao desfeitos.");
                activity.setText("Cancelado.");
            } else if (failure != null) {
                appendLine("ERRO: " + failure.getMessage());
                activity.setText("Falha.");
            } else {
                if (result != null && !result.isBlank()) appendLine(result);
                activity.setText("Concluido.");
            }
            running = null;
            busy.set(false);
            input.requestFocus();
            idle.complete(null);
        }));
    }

    private void appendLine(String text) {
        appendText(text + "\n");
    }

    private void appendText(String text) {
        if (text.length() > MAX_OUTPUT_CHARS) text = text.substring(text.length() - MAX_OUTPUT_CHARS);
        int excess = output.getLength() + text.length() - MAX_OUTPUT_CHARS;
        if (excess > 0) output.deleteText(0, excess);
        output.appendText(text);
    }

    private void reportProgress(Progress update) {
        pendingProgress.set(update);
        if (progressQueued.compareAndSet(false, true)) Platform.runLater(() -> {
            progressQueued.set(false);
            Progress value = pendingProgress.getAndSet(null);
            if (value != null && busy.get() && value.id() == executionId) {
                progress.setProgress(Double.isFinite(value.fraction()) ? value.fraction() : -1);
                activity.setText(value.message() + (Double.isFinite(value.fraction())
                        ? " (" + Math.round(value.fraction() * 100) + "%)" : ""));
            }
        });
    }

    private void registerShellCommands(String versionLabel) {
        interpreter.register("version", (interp, args) -> versionLabel);
        interpreter.register("help", (interp, args) -> {
            if (!args.isEmpty()) {
                String name = args.get(0);
                String details = TclFlatcamCommands.help(name);
                if (details != null && interp.commandNames().contains(name)) return details;
                return interp.commandNames().contains(name)
                        ? name + ": sem texto de ajuda detalhado ainda."
                        : "Comando desconhecido: " + name;
            }
            return "Comandos disponiveis:\n" + String.join("\n", new TreeSet<>(interp.commandNames()));
        });
        interpreter.register("clear_shell", (interp, args) -> {
            TclExecution.onFx(() -> { output.clear(); return null; });
            return "";
        });
    }
}
