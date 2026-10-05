package org.flatcam.fx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyleSpans;
import org.reactfx.Subscription;

/** Virtualized editor/viewer; bounded background work and stale-result rejection. */
final class CodeEditor extends BorderPane implements AutoCloseable {
    private final CodeArea area = new CodeArea();
    private final CodeSyntax.Language language;
    private final Label mode = new Label(), position = new Label(), searchState = new Label();
    private final TextField search = new TextField();
    private final HBox searchBar;
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(80));
    private final ThreadPoolExecutor documents = worker("flatcam-code-document"), syntax = worker("flatcam-code-syntax");
    private final Map<Integer, String> coloured = new HashMap<>();
    private final Subscription changes;
    private Future<?> documentTask, syntaxTask;
    private long version, searchVersion, syntaxVersion;
    private boolean editable, replacing, closed;
    private Runnable onEdit = () -> { };
    private record Line(int index, String text) { }
    private record StyledLine(Line line, StyleSpans<Collection<String>> styles) { }

    CodeEditor(String text, CodeSyntax.Language language, boolean editable) {
        this.language = language; this.editable = editable;
        getStyleClass().add("code-editor"); setMinSize(0, 0);
        area.setId("code-area"); area.setWrapText(false);
        // Plain Text graphics avoid Modena Label lookups in Flowless's detached measurement cells.
        var paragraphCount = org.reactfx.collection.LiveList.sizeOf(area.getParagraphs());
        area.setParagraphGraphicFactory(index -> {
            var number = new javafx.scene.text.Text();
            number.getStyleClass().add("code-line-number");
            number.setFont(javafx.scene.text.Font.font("Monospaced", 13)); number.setFill(javafx.scene.paint.Color.GRAY);
            var gutter = new javafx.scene.layout.StackPane(number); gutter.getStyleClass().add("lineno");
            gutter.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
            number.textProperty().bind(paragraphCount.map(count -> String.format(java.util.Locale.ROOT,
                    "%" + Integer.toString(count).length() + "d", index + 1)).conditionOnShowing(gutter));
            return gutter;
        });
        installContextMenu();
        setCenter(new VirtualizedScrollPane<>(area));
        Label type = new Label(language.label); type.getStyleClass().add("code-language");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, type, spacer, mode, button("Buscar", "Buscar texto literal (Ctrl+F)", this::openSearch));
        header.getStyleClass().add("code-toolbar");
        search.setId("code-search"); search.setPromptText("Buscar texto literal (diferencia maiúsculas)");
        search.setMinWidth(60); HBox.setHgrow(search, Priority.ALWAYS);
        search.textProperty().addListener((observable, oldText, newText) -> { searchVersion++; searchState.setText(""); });
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) { find(event.isShiftDown()); event.consume(); }
        });
        searchBar = new HBox(6, search, button("↑", "Anterior (Shift+Enter / Shift+F3)", () -> find(true)),
                button("↓", "Próxima (Enter / F3)", () -> find(false)), searchState, button("×", "Fechar busca (Esc)", this::hideSearch));
        searchBar.getStyleClass().add("code-search-bar"); searchBar.setVisible(false); searchBar.setManaged(false);
        setTop(new javafx.scene.layout.VBox(header, searchBar));
        position.setId("code-position"); position.getStyleClass().add("code-status"); setBottom(position);
        area.caretPositionProperty().addListener((observable, oldValue, value) -> updatePosition());
        changes = area.plainTextChanges().subscribe(change -> {
            version++; searchVersion++; coloured.clear(); updatePosition(); scheduleSyntax();
            if (!replacing) onEdit.run();
        });
        area.getVisibleParagraphs().addListener((javafx.beans.InvalidationListener) observable -> scheduleSyntax());
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene == null) { syntaxVersion++; debounce.stop(); if (syntaxTask != null) syntaxTask.cancel(true); }
            else scheduleSyntax();
        });
        debounce.setOnFinished(event -> colourViewport());
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.F) { openSearch(); event.consume(); }
            else if (event.getCode() == KeyCode.F3) { openSearch(); find(event.isShiftDown()); event.consume(); }
            else if (event.getCode() == KeyCode.ESCAPE && searchBar.isVisible()) { hideSearch(); event.consume(); }
        });
        setText(text);
    }
    CodeArea area() { return area; }
    String getText() { return area.getText(); }
    boolean isEditable() { return editable; }
    boolean isLoading() { return loading.get(); }
    ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    void onEdit(Runnable action) { onEdit = action; }
    void focusCode() { area.requestFocus(); }
    void setEditable(boolean value) { editable = value; area.setEditable(value && !isLoading()); updateMode(); }
    static String normalize(String text) { return text.replace("\r\n", "\n").replace('\r', '\n'); }

    void setText(String text) {
        if (closed) return;
        long generation = ++version; searchVersion++; coloured.clear();
        if (documentTask != null) documentTask.cancel(true); documents.getQueue().clear();
        if (text.length() < 200_000) install(document(text), generation);
        else {
            setLoading(true);
            documentTask = documents.submit(() -> {
                var document = document(text); Platform.runLater(() -> install(document, generation));
            });
        }
    }
    void loadFile(Path path, Consumer<String> failed) {
        loadText(() -> Files.readString(path), failed);
    }
    void loadText(java.util.concurrent.Callable<String> read, Consumer<String> failed) {
        if (closed) return;
        long generation = ++version; searchVersion++; setLoading(true);
        if (documentTask != null) documentTask.cancel(true); documents.getQueue().clear();
        documentTask = documents.submit(() -> {
            try {
                var document = document(read.call()); Platform.runLater(() -> install(document, generation));
            } catch (Exception failure) {
                Platform.runLater(() -> {
                    if (!closed && generation == version) {
                        setLoading(false); mode.setText("Falha ao carregar fonte"); failed.accept(failure.getMessage());
                    }
                });
            }
        });
    }
    private ReadOnlyStyledDocument<Collection<String>, String, Collection<String>> document(String text) {
        // WKT is normally a single huge line; whitespace between coordinates is legal WKT.
        // This is a read-only generated Geometry view, not a rewrite of a Gerber/G-code file.
        String displayed = language == CodeSyntax.Language.GEOMETRY ? text.replace(",", ",\n") : text;
        return ReadOnlyStyledDocument.fromString(normalize(displayed), List.of(), List.of(), SegmentOps.styledTextOps());
    }
    private void install(ReadOnlyStyledDocument<Collection<String>, String, Collection<String>> document, long generation) {
        if (closed || generation != version) return;
        replacing = true;
        try { area.replace(0, area.getLength(), document); area.getUndoManager().forgetHistory(); area.moveTo(0); }
        finally { replacing = false; }
        setLoading(false); updatePosition(); scheduleSyntax();
    }
    private void setLoading(boolean value) { loading.set(value); area.setEditable(editable && !value); updateMode(); }
    private void updateMode() { mode.setText(isLoading() ? "Carregando…" : editable ? "Edição • rascunho" : "Somente leitura"); }
    private void updatePosition() {
        position.setText("Ln " + (area.getCurrentParagraph() + 1) + ", Col " + (area.getCaretColumn() + 1)
                + "   •   " + area.getParagraphs().size() + " linhas   •   " + language.label);
    }
    private void scheduleSyntax() { if (!closed && !isLoading() && getScene() != null) debounce.playFromStart(); }
    private void colourViewport() {
        if (closed || isLoading() || getScene() == null) return;
        List<Line> lines = new ArrayList<>(); Map<Integer, String> retained = new HashMap<>(); int budget = 65_536;
        for (int visible = 0; visible < Math.min(120, area.getVisibleParagraphs().size()); visible++) {
            int index = area.visibleParToAllParIndex(visible); String text = area.getText(index);
            if (text.equals(coloured.get(index))) retained.put(index, text);
            else if (budget > 0 && !text.isEmpty()) { lines.add(new Line(index, text)); budget -= Math.min(text.length(), 8192); }
        }
        coloured.clear(); coloured.putAll(retained); if (lines.isEmpty()) return;
        long generation = version, viewport = ++syntaxVersion;
        if (syntaxTask != null) syntaxTask.cancel(true); syntax.getQueue().clear();
        syntaxTask = syntax.submit(() -> {
            var styled = lines.stream().map(line -> new StyledLine(line, CodeSyntax.highlight(line.text(), language))).toList();
            Platform.runLater(() -> {
                if (closed || generation != version || viewport != syntaxVersion || getScene() == null) return;
                for (var line : styled) if (line.line().index() < area.getParagraphs().size()) {
                    coloured.put(line.line().index(), line.line().text()); area.setStyleSpans(line.line().index(), 0, line.styles());
                }
            });
        });
    }
    private void openSearch() { searchBar.setVisible(true); searchBar.setManaged(true); search.requestFocus(); }
    private void hideSearch() { searchVersion++; searchBar.setVisible(false); searchBar.setManaged(false); area.requestFocus(); }
    private void find(boolean backwards) {
        if (closed || isLoading() || search.getText().isEmpty()) return;
        String needle = search.getText(); int start = backwards ? area.getSelection().getStart() - 1 : area.getSelection().getEnd();
        long generation = ++searchVersion, contentVersion = version;
        var snapshot = area.getContent().snapshot(); // Immutable, unlike area.getDocument().
        searchState.setText("Buscando…");
        if (documentTask != null) documentTask.cancel(true); documents.getQueue().clear();
        documentTask = documents.submit(() -> {
            int match = findLiteral(snapshot.getText(), needle, start, backwards);
            Platform.runLater(() -> {
                if (closed || generation != searchVersion || contentVersion != version) return;
                searchState.setText(match < 0 ? "Não encontrado" : "Encontrado");
                if (match >= 0) { area.selectRange(match, match + needle.length()); area.requestFollowCaret(); }
            });
        });
    }
    static int findLiteral(String text, String needle, int start, boolean backwards) {
        if (needle.isEmpty()) return -1;
        int found = backwards ? text.lastIndexOf(needle, start) : text.indexOf(needle, start);
        return found >= 0 ? found : backwards ? text.lastIndexOf(needle) : text.indexOf(needle);
    }
    private static Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text); button.setTooltip(new Tooltip(tooltip)); button.setOnAction(event -> action.run()); return button;
    }
    private void installContextMenu() {
        var undo = menuItem("Desfazer", area::undo); var redo = menuItem("Refazer", area::redo);
        var cut = menuItem("Recortar", area::cut); var copy = menuItem("Copiar", area::copy);
        var paste = menuItem("Colar", area::paste); var all = menuItem("Selecionar tudo", area::selectAll);
        var menu = new javafx.scene.control.ContextMenu(undo, redo, new javafx.scene.control.SeparatorMenuItem(),
                cut, copy, paste, all, new javafx.scene.control.SeparatorMenuItem(), menuItem("Buscar…", this::openSearch));
        menu.setOnShowing(event -> {
            undo.setDisable(!area.isEditable() || !area.isUndoAvailable()); redo.setDisable(!area.isEditable() || !area.isRedoAvailable());
            cut.setDisable(!area.isEditable() || area.getSelection().getLength() == 0);
            copy.setDisable(area.getSelection().getLength() == 0); paste.setDisable(!area.isEditable());
        });
        area.setContextMenu(menu);
    }
    private static javafx.scene.control.MenuItem menuItem(String text, Runnable action) {
        var item = new javafx.scene.control.MenuItem(text); item.setOnAction(event -> action.run()); return item;
    }
    private static ThreadPoolExecutor worker(String name) {
        return new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }
    @Override public void close() {
        if (closed) return;
        closed = true; version++; searchVersion++; debounce.stop(); changes.unsubscribe();
        documents.shutdownNow(); syntax.shutdownNow(); coloured.clear(); area.dispose();
    }
}
