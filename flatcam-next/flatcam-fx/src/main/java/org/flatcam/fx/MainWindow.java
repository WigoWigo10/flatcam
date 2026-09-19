package org.flatcam.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationResult;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

/**
 * Shell shape taken from the legacy app, not from CONTEXTO_FLATCAM_FX.md's
 * secao 6 sketch - see UI_INVENTORY.md. FlatCAM/PyQt5 barely opens separate
 * windows: a left tab strip alternates Project/Properties/Tool in the same
 * space, and a center tab strip has a non-closable "Plot Area" (viewport)
 * plus auxiliary tabs (Preferences, Tools Database, editors, ...) opened on
 * demand and reused rather than duplicated. This class replicates that
 * shape; the two auxiliary tab actions here are placeholders demonstrating
 * the open/reuse/focus pattern, not real Preferences/Tools Database screens.
 */
final class MainWindow {

    private static final Color IDLE_COLOR = Color.web("#4caf50");
    private static final Color RUNNING_COLOR = Color.web("#f0ad4e");
    private static final Color CANCELLED_COLOR = Color.web("#9e9e9e");
    private static final Color ERROR_COLOR = Color.web("#e53935");
    private static final Color GERBER_FILL = Color.web("#e1a339");
    private static final Color GERBER_STROKE = Color.web("#a06f1f");
    private static final Color DRILL_FILL = Color.web("#c9c9c9");
    private static final Color DRILL_STROKE = Color.web("#8a8a8a");
    private static final Color ISOLATION_COLOR = Color.web("#28d0d0");
    private static final Color MARK_COLOR = Color.web("#ff2fd6", 0.65);

    private final JobExecutor jobExecutor;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Pronto.");
    private final Circle statusDot = new Circle(5, Color.web("#4caf50"));
    private final Label unitsLabel = new Label("Unidades: -");
    private final Button runDemoJobButton = new Button();
    private final Button cancelJobButton = new Button();
    private final TextArea console = new TextArea();
    private final TabPane centerTabs = new TabPane();
    private final StackPane propertiesContainer = new StackPane();
    private final Label propertiesPlaceholder = new Label("Selecione um objeto\npara ver seus parametros.");

    /** A generated G-code file, tracked in the "CNC Jobs" tree category once GCodeGenerator writes one. */
    private record CncJobEntry(String sourceName, Path outputFile, String gcode) {
    }

    /**
     * PlotAreaView layer key for an isolation preview, distinct from the
     * Gerber's own key (its TreeItem) so the toolpath layer sits alongside
     * the copper layer instead of replacing it. Re-generating isolation for
     * the same Gerber item produces an equal key, so the preview layer is
     * replaced rather than duplicated.
     */
    private record IsolationLayerKey(TreeItem<String> gerberItem) {
    }

    /** PlotAreaView layer key for the apertures table's "Mark" highlight overlay - see {@link GerberAperturesTable}. */
    private record MarkLayerKey(TreeItem<String> gerberItem) {
    }

    /** Files opened/generated so far, keyed by their tree item - back the Properties tab and the item context menu. */
    private final Map<TreeItem<String>, GerberImage> gerberByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, ExcellonImage> excellonByItem = new LinkedHashMap<>();
    private final Map<TreeItem<String>, CncJobEntry> cncJobByItem = new LinkedHashMap<>();
    /** Original file path for Gerber/Excellon items - what gets written to a saved project file. */
    private final Map<TreeItem<String>, Path> sourcePathByItem = new LinkedHashMap<>();

    private final PlotAreaView plotAreaView = new PlotAreaView();

    private Scene scene;
    private SplitPane horizontalSplit;
    private SplitPane verticalSplit;
    private TreeView<String> projectTree;
    private TabPane leftTabs;
    private Tab propertiesTab;
    private Tab toolTab;
    private TreeItem<String> gerbersNode;
    private TreeItem<String> excellonNode;
    private TreeItem<String> cncJobsNode;
    private VBox bottomPanel;
    private double dividerBeforeConsoleCollapse = 0.75;
    private boolean consoleCollapsed = !AppPreferences.loadConsoleOpen(true);
    private ThemeOption currentTheme = AppPreferences.loadTheme(ThemeOption.CUSTOM_LIGHT);
    private JobHandle<?> runningJob;

    MainWindow(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
    }

    Scene createScene() {
        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        root.setCenter(buildMainSplit());
        root.setBottom(buildStatusBar());

        scene = new Scene(root);
        currentTheme.applyTo(scene);
        return scene;
    }

    /** Called by MainApp on window close - split-divider positions have no natural "save now" moment otherwise. */
    void saveSplitPositions() {
        if (consoleCollapsed) {
            return; // the collapsed (~1.0) position is not a real layout preference - see toggleConsole().
        }
        AppPreferences.saveSplitPositions(horizontalSplit.getDividerPositions()[0], verticalSplit.getDividerPositions()[0]);
    }

    /**
     * Collapses the resizable progress/console panel, or restores it. Moving the
     * divider to 1.0 alone does not reach a true 100% collapse - the console
     * TextArea has a nonzero intrinsic min-height, so a sliver always stayed
     * visible. Removing the pane from the SplitPane's items entirely (and
     * re-adding it to restore) has no such floor.
     */
    private void toggleConsole(boolean show) {
        if (!show) {
            dividerBeforeConsoleCollapse = verticalSplit.getDividerPositions()[0];
            consoleCollapsed = true;
            verticalSplit.getItems().remove(bottomPanel);
        } else {
            consoleCollapsed = false;
            verticalSplit.getItems().add(bottomPanel);
            verticalSplit.setDividerPositions(dividerBeforeConsoleCollapse);
            attachVerticalDividerSaveListener(); // re-adding creates a new Divider instance.
        }
        AppPreferences.saveConsoleOpen(show);
    }

    private MenuBar buildMenuBar() {
        Menu fileMenu = new Menu("Arquivo");
        MenuItem openProjectItem = new MenuItem("Abrir Projeto...");
        openProjectItem.setOnAction(e -> openProject());
        MenuItem saveProjectItem = new MenuItem("Salvar Projeto...");
        saveProjectItem.setOnAction(e -> saveProject());
        MenuItem openGerberItem = new MenuItem("Abrir Gerber (prototipo Fase 3)");
        openGerberItem.setOnAction(e -> openGerberPrototype());
        MenuItem openExcellonItem = new MenuItem("Abrir Excellon (prototipo Fase 4)");
        openExcellonItem.setOnAction(e -> openExcellonPrototype());
        MenuItem runDemoJob = new MenuItem("Executar job de demonstracao");
        runDemoJob.setOnAction(e -> runDemoJob());
        MenuItem exitItem = new MenuItem("Sair");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(
                openProjectItem, saveProjectItem, new SeparatorMenuItem(),
                openGerberItem, openExcellonItem, runDemoJob, new SeparatorMenuItem(), exitItem);

        Menu editMenu = new Menu("Editar");
        MenuItem preferencesItem = new MenuItem("Preferencias");
        preferencesItem.setOnAction(e -> openAuxiliaryTab("Preferencias", this::buildPreferencesPlaceholder));
        editMenu.getItems().add(preferencesItem);

        Menu optionsMenu = new Menu("Opcoes");
        MenuItem toolsDbItem = new MenuItem("Tools Database");
        toolsDbItem.setOnAction(e -> openAuxiliaryTab("Tools Database", this::buildToolsDbPlaceholder));
        optionsMenu.getItems().add(toolsDbItem);

        Menu viewMenu = new Menu("Exibir");
        viewMenu.getItems().add(buildThemeMenu());

        Menu helpMenu = new Menu("Ajuda");
        MenuItem aboutItem = new MenuItem("Sobre");
        aboutItem.setOnAction(e -> appendConsole("FlatCAM Next - esqueleto Fase 1 (CONTEXTO_FLATCAM_FX.md)"));
        helpMenu.getItems().add(aboutItem);

        return new MenuBar(fileMenu, editMenu, optionsMenu, viewMenu, helpMenu);
    }

    /**
     * Both theme families side by side (CSS puro / AtlantaFX), light and
     * dark in each, one ToggleGroup shared across both submenus so only one
     * option is ever selected at a time.
     */
    private Menu buildThemeMenu() {
        ToggleGroup themeGroup = new ToggleGroup();

        Menu customMenu = new Menu("CSS puro");
        customMenu.getItems().addAll(
                themeItem(ThemeOption.CUSTOM_LIGHT, themeGroup),
                themeItem(ThemeOption.CUSTOM_DARK, themeGroup)
        );

        Menu atlantaFxMenu = new Menu("AtlantaFX");
        atlantaFxMenu.getItems().addAll(
                themeItem(ThemeOption.ATLANTAFX_LIGHT, themeGroup),
                themeItem(ThemeOption.ATLANTAFX_DARK, themeGroup)
        );

        Menu themeMenu = new Menu("Tema");
        themeMenu.getItems().addAll(customMenu, atlantaFxMenu);
        return themeMenu;
    }

    /** Selection reflects (and, on change, persists) {@link #currentTheme} - loaded from AppPreferences at construction. */
    private RadioMenuItem themeItem(ThemeOption option, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(option.label());
        item.setToggleGroup(group);
        item.setSelected(option == currentTheme);
        item.setOnAction(e -> {
            option.applyTo(scene);
            currentTheme = option;
            AppPreferences.saveTheme(option);
        });
        return item;
    }

    /** Icon-only buttons with tooltips, matching the legacy app's toolbar convention (UI_INVENTORY.md section 1). */
    private ToolBar buildToolBar() {
        Button openGerberButton = new Button(null, Icons.folderOpen(16));
        openGerberButton.setTooltip(new Tooltip("Abrir Gerber (prototipo Fase 3)"));
        openGerberButton.setOnAction(e -> openGerberPrototype());

        Button openExcellonButton = new Button(null, Icons.drill(16));
        openExcellonButton.setTooltip(new Tooltip("Abrir Excellon (prototipo Fase 4)"));
        openExcellonButton.setOnAction(e -> openExcellonPrototype());

        runDemoJobButton.setGraphic(Icons.play(16));
        runDemoJobButton.setTooltip(new Tooltip("Executar job de demonstracao"));
        runDemoJobButton.setOnAction(e -> runDemoJob());

        cancelJobButton.setGraphic(Icons.stop(16));
        cancelJobButton.setTooltip(new Tooltip("Cancelar"));
        cancelJobButton.setDisable(true);
        cancelJobButton.setOnAction(e -> cancelDemoJob());

        return new ToolBar(openGerberButton, openExcellonButton, new Separator(), runDemoJobButton, cancelJobButton);
    }

    /**
     * A fixed status bar at the very bottom of the window (appGUI/MainGUI.py's
     * statusBar(), UI_INVENTORY.md section 1) - separate from the resizable
     * job progress/console panel, always visible regardless of which tabs are
     * open. The units label reflects the last-opened Gerber's real units;
     * everything else the legacy status bar shows (grid, workspace size, HUD
     * toggles) needs state this phase doesn't have yet.
     *
     * <p>The console/shell toggle lives here, not the top toolbar - confirmed
     * against appGUI/MainGUI.py: shell_status_label is a clickable icon added
     * to status_toolbar, which is added to infobar (the bottom status bar),
     * not any top toolbar.
     */
    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleButton consoleToggle = new ToggleButton(null, Icons.terminal(14));
        consoleToggle.setTooltip(new Tooltip("Mostrar/ocultar console"));
        consoleToggle.getStyleClass().add("status-bar-toggle");
        consoleToggle.setSelected(!consoleCollapsed);
        consoleToggle.setOnAction(e -> toggleConsole(consoleToggle.isSelected()));

        HBox bar = new HBox(6, statusDot, statusLabel, spacer, consoleToggle, unitsLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void setStatus(String text, Color dotColor) {
        statusLabel.setText(text);
        statusDot.setFill(dotColor);
    }

    private SplitPane buildMainSplit() {
        horizontalSplit = new SplitPane(buildLeftTabs(), buildCenterTabs());
        bottomPanel = buildBottomPanel();
        // Starts without the bottom panel at all (not just visually collapsed) if the
        // console was closed last session - same "remove from items" mechanism toggleConsole()
        // uses, just applied before the first layout instead of via a later user click.
        verticalSplit = consoleCollapsed ? new SplitPane(horizontalSplit) : new SplitPane(horizontalSplit, bottomPanel);
        verticalSplit.setOrientation(Orientation.VERTICAL);

        // SplitPane does not reliably honor a divider position set before its first
        // layout pass - measured drift on a loaded 0.55: it settled at ~0.51 by the next
        // launch, because the save listener (attached below) caught that settling as if
        // it were a real change and persisted it. Deferring the initial set past the
        // first layout, then attaching the listener, avoids that drift entirely.
        Platform.runLater(() -> {
            horizontalSplit.setDividerPositions(AppPreferences.loadSplitHorizontal(0.22));
            horizontalSplit.getDividers().get(0).positionProperty()
                    .addListener((obs, oldVal, newVal) -> saveSplitPositions());
            if (!consoleCollapsed) {
                verticalSplit.setDividerPositions(AppPreferences.loadSplitVertical(0.75));
                attachVerticalDividerSaveListener();
            } else {
                // No bottom panel means no divider to restore a position on yet - toggleConsole()
                // reads this the next time the console is actually reopened.
                dividerBeforeConsoleCollapse = AppPreferences.loadSplitVertical(0.75);
            }
        });
        return verticalSplit;
    }

    /**
     * SplitPane rebuilds its Divider list whenever its items change - after
     * toggleConsole() removes/re-adds the bottom panel, the old Divider
     * instance a listener was attached to is discarded, so this must be
     * called again each time the panel is restored, not just once at startup.
     */
    private void attachVerticalDividerSaveListener() {
        verticalSplit.getDividers().get(0).positionProperty()
                .addListener((obs, oldVal, newVal) -> saveSplitPositions());
    }

    /**
     * Project / Properties / Tool sharing one tab strip - see UI_INVENTORY.md
     * section 1 (appGUI/MainGUI.py's self.notebook - project_tab/
     * properties_tab/tool_tab). The Tool tab is where a running tool's own
     * form lives (see {@link #openToolPanel}) - appTools/ToolIsolation.py
     * and ToolDrilling.py both switch this same tab to their own UI via
     * app.ui.notebook.setCurrentWidget(app.ui.tool_tab) rather than opening
     * a separate window.
     */
    private TabPane buildLeftTabs() {
        Tab projectTab = new Tab("Projeto", buildProjectTree());
        propertiesPlaceholder.setTextAlignment(TextAlignment.CENTER);
        propertiesContainer.getChildren().add(propertiesPlaceholder);
        propertiesTab = new Tab("Propriedades", propertiesContainer);
        toolTab = new Tab("Ferramenta", centeredPlaceholder("Nenhuma ferramenta ativa."));

        leftTabs = new TabPane(projectTab, propertiesTab, toolTab);
        leftTabs.getTabs().forEach(tab -> tab.setClosable(false));
        leftTabs.getStyleClass().add("side-panel");
        leftTabs.setMinWidth(160);
        return leftTabs;
    }

    /** Switches the left sidebar to the Tool tab and loads {@code content} into it, replacing whatever was there. */
    private void openToolPanel(String label, Node content) {
        toolTab.setText(label);
        toolTab.setContent(content);
        leftTabs.getSelectionModel().select(toolTab);
    }

    /**
     * Restores the Tool tab's placeholder and switches back to Properties -
     * matching ToolIsolation.py/ToolDrilling.py's own
     * app.ui.notebook.setCurrentWidget(app.ui.properties_tab) once a tool
     * finishes (or is closed without finishing).
     */
    private void closeToolPanel() {
        toolTab.setText("Ferramenta");
        toolTab.setContent(centeredPlaceholder("Nenhuma ferramenta ativa."));
        leftTabs.getSelectionModel().select(propertiesTab);
    }

    /**
     * A hidden root (the "Projeto" tab title already says that - an explicit
     * "Projeto" row too was redundant) with one category node per object
     * kind, matching appObjects/ObjectCollection.py's grouping. Category
     * nodes are inert; file nodes underneath (added as Gerber/Excellon files
     * are opened, or as drilling G-code is generated - Geometry has no Java
     * generator yet) carry a {@link GerberImage}, {@link ExcellonImage} or
     * {@link CncJobEntry} in {@link #gerberByItem}/{@link #excellonByItem}/
     * {@link #cncJobByItem} and get a context menu.
     *
     * <p>Multi-selection (Ctrl/Shift-click, ObjectCollection.py's
     * ExtendedSelection) is enabled: right-clicking with more than one row
     * selected shows a shared "Ativar/Desativar/Remover" menu applying to
     * the whole selection instead of a single object's menu - matching
     * appObjects/ObjectCollection.py's on_menu_request(), which always pops
     * the same menuproject regardless of how many rows are selected, and
     * app_Main.py's on_enable_sel_plots()/on_disable_sel_plots()/on_delete(),
     * which all iterate self.collection.get_selected(). The per-object menu
     * (Set Color, Edit, Copy, Save from UI_INVENTORY.md section 1) still
     * needs subsystems (editors, project format) this phase doesn't have.
     */
    private TreeView<String> buildProjectTree() {
        TreeItem<String> root = new TreeItem<>("Projeto");
        root.setExpanded(true);
        gerbersNode = new TreeItem<>("Gerbers");
        gerbersNode.setExpanded(true);
        excellonNode = new TreeItem<>("Excellon");
        excellonNode.setExpanded(true);
        cncJobsNode = new TreeItem<>("CNC Jobs");
        cncJobsNode.setExpanded(true);
        root.getChildren().addAll(
                gerbersNode,
                excellonNode,
                new TreeItem<>("Geometry"),
                cncJobsNode
        );

        projectTree = new TreeView<>(root);
        projectTree.setShowRoot(false);
        projectTree.setEditable(true);
        projectTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        projectTree.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> showProperties(selected));
        // Category rows (Gerbers/Excellon/Geometry/CNC Jobs) are just visual grouping, not
        // real objects - they have no Properties panel and no context menu, and shouldn't be
        // selectable at all. A per-row click handler can't fully prevent that: Shift-click or
        // Ctrl+A range-selects across whatever rows fall in between, category headers included,
        // regardless of any click handling on those specific rows - so this reactively drops
        // one the instant it lands in the selection, however it got there.
        projectTree.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<String>>) change -> {
            while (change.next()) {
                if (!change.wasAdded()) {
                    continue;
                }
                for (TreeItem<String> added : change.getAddedSubList()) {
                    if (added != null && !isProjectObject(added)) {
                        int row = projectTree.getRow(added);
                        if (row >= 0) {
                            projectTree.getSelectionModel().clearSelection(row);
                        }
                    }
                }
            }
        });
        // Renaming in-place commits by just updating the TreeItem's own value - same as
        // the Properties panel's Name field (see nameRow()), just triggered from the tree.
        projectTree.setOnEditCommit(event -> event.getTreeItem().setValue(event.getNewValue()));
        projectTree.setOnKeyPressed(event -> {
            TreeItem<String> selected = projectTree.getSelectionModel().getSelectedItem();
            switch (event.getCode()) {
                // Double-click or Enter on a row switches the sidebar to Properties - matches
                // ObjectCollection.py's on_item_activated()/on_row_activated(), which both call
                // build_ui() then app.ui.notebook.setCurrentWidget(app.ui.properties_tab).
                case ENTER -> {
                    if (selected != null && isProjectObject(selected)) {
                        leftTabs.getSelectionModel().select(propertiesTab);
                    }
                }
                // F2 is the conventional cross-app inline-rename key (Qt's own default
                // "EditKeyPressed" trigger) - kept distinct from double-click/Enter above,
                // which this port already dedicates to "show Properties".
                case F2 -> {
                    if (selected != null && isProjectObject(selected)) {
                        projectTree.edit(selected);
                    }
                }
                case DELETE -> {
                    List<TreeItem<String>> selectedItems = projectTree.getSelectionModel().getSelectedItems().stream()
                            .filter(Objects::nonNull).distinct().toList();
                    if (!selectedItems.isEmpty()) {
                        removeSelectionFromProject(selectedItems);
                    }
                }
                default -> {
                }
            }
        });
        projectTree.setCellFactory(view -> {
            // Everything renders as a single graphic Node (never the Cell's own text
            // property) - Labeled's separate text+graphic layout did not vertically
            // center a custom graphic against the text baseline reliably inside a
            // TreeCell. An HBox fully owns its own children's alignment instead.
            TextField editField = new TextField();
            Label textLabel = new Label();
            StackPane iconHolder = new StackPane();
            iconHolder.setAlignment(Pos.CENTER);
            iconHolder.setMinSize(16, 16);
            iconHolder.setPrefSize(16, 16);
            HBox displayBox = new HBox(6, iconHolder, textLabel);
            displayBox.setAlignment(Pos.CENTER_LEFT);

            TreeCell<String> cell = new TreeCell<>() {
                @Override
                public void startEdit() {
                    if (getTreeItem() == null || !isProjectObject(getTreeItem())) {
                        return;
                    }
                    super.startEdit();
                    editField.setText(getItem());
                    setText(null);
                    setGraphic(editField);
                    editField.selectAll();
                    editField.requestFocus();
                }

                @Override
                public void cancelEdit() {
                    super.cancelEdit();
                    refreshDisplay(getItem(), getTreeItem());
                }

                @Override
                protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(null);
                    if (empty || value == null) {
                        setGraphic(null);
                    } else if (isEditing()) {
                        editField.setText(value);
                        setGraphic(editField);
                    } else {
                        refreshDisplay(value, getTreeItem());
                    }
                }

                private void refreshDisplay(String value, TreeItem<String> item) {
                    textLabel.setText(value);
                    boolean isObject = isProjectObject(item);
                    // Category rows (Gerbers/Excellon/Geometry/CNC Jobs) get no icon but do
                    // get a bold label, so they read as group headers rather than objects.
                    textLabel.setStyle(isObject ? null : "-fx-font-weight: bold;");
                    Node icon = iconShapeFor(item);
                    iconHolder.getChildren().setAll(icon == null ? List.of() : List.of(icon));
                    setGraphic(displayBox);
                }
            };
            editField.setOnAction(e -> cell.commitEdit(editField.getText()));
            editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused && cell.isEditing()) {
                    cell.commitEdit(editField.getText());
                }
            });
            editField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    cell.cancelEdit();
                }
            });
            // An event FILTER on MOUSE_PRESSED, not a MOUSE_CLICKED handler: TreeView.
            // setEditable(true) (needed for F2 rename) also wires the cell's own default
            // double-click-to-edit behavior, which triggers from the cell's own
            // MOUSE_PRESSED handler (before MOUSE_CLICKED is ever dispatched) - a filter
            // on MOUSE_CLICKED ran too late to stop it. Filters run before handlers at
            // the same node, so consuming the press here reliably suppresses it.
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() == 2 && isProjectObject(cell.getTreeItem())) {
                    leftTabs.getSelectionModel().select(propertiesTab);
                    event.consume();
                }
            });
            // Populating a ContextMenu's items inside its own setOnShowing (instead of
            // before the initial show() call) left the popup sized to zero the first
            // time - it never appeared. Building the menu synchronously in response to
            // the actual right-click, then calling show() ourselves, avoids that.
            cell.setOnContextMenuRequested(event -> {
                TreeItem<String> item = cell.getTreeItem();
                if (item == null) {
                    return;
                }
                ContextMenu menu = buildContextMenuFor(item);
                if (!menu.getItems().isEmpty()) {
                    menu.show(cell, event.getScreenX(), event.getScreenY());
                }
                event.consume();
            });
            return cell;
        });
        return projectTree;
    }

    /** Category rows (Gerbers/Excellon/Geometry/CNC Jobs) aren't real objects - only actual Gerber/Excellon/CNC Job items are. */
    private boolean isProjectObject(TreeItem<String> item) {
        return gerberByItem.containsKey(item) || excellonByItem.containsKey(item) || cncJobByItem.containsKey(item);
    }

    /**
     * One icon per object kind, copied straight from the legacy app's own assets
     * (ObjectCollection.py's icon_files: flatcam_icon16.png/drill16.png/cnc16.png) -
     * see Icons.fromResource(). A plain ImageView's layout bounds are exactly its own
     * pixel box, which centers predictably in the fixed-size iconHolder; the earlier
     * Group+Scale vector glyphs did not.
     */
    private Node iconShapeFor(TreeItem<String> item) {
        if (gerberByItem.containsKey(item)) {
            return Icons.fromResource("gerber16.png", 16);
        }
        if (excellonByItem.containsKey(item)) {
            return Icons.fromResource("drill16.png", 16);
        }
        if (cncJobByItem.containsKey(item)) {
            return Icons.fromResource("cnc16.png", 16);
        }
        return null;
    }

    /**
     * Built fresh on every right-click (not once per row) so it always
     * reflects the CURRENT selection at click time, same as the legacy
     * app's shared menuproject: with more than one row selected, show the
     * bulk Ativar/Desativar/Remover menu; otherwise the rich single-object
     * menu for whichever kind {@code item} is.
     */
    private ContextMenu buildContextMenuFor(TreeItem<String> item) {
        List<TreeItem<String>> selected = projectTree.getSelectionModel().getSelectedItems().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (selected.size() > 1 && selected.contains(item)) {
            return new ContextMenu(buildBulkContextMenuItems(selected).toArray(new MenuItem[0]));
        }
        GerberImage gerberImage = gerberByItem.get(item);
        ExcellonImage excellonImage = excellonByItem.get(item);
        CncJobEntry cncJob = cncJobByItem.get(item);
        if (gerberImage != null) {
            return new ContextMenu(gerberContextMenuItems(item, gerberImage).toArray(new MenuItem[0]));
        } else if (excellonImage != null) {
            return new ContextMenu(excellonContextMenuItems(item, excellonImage).toArray(new MenuItem[0]));
        } else if (cncJob != null) {
            return new ContextMenu(cncJobContextMenuItems(item, cncJob).toArray(new MenuItem[0]));
        }
        return new ContextMenu();
    }

    /**
     * "Ativar Plot"/"Desativar Plot" (skipping CNC Jobs, which have no plot
     * layer) and "Remover" for the whole current selection - the multi-
     * select counterpart of app_Main.py's on_enable_sel_plots()/
     * on_disable_sel_plots()/on_delete(), each of which loops over
     * self.collection.get_selected() instead of a single object.
     */
    private List<MenuItem> buildBulkContextMenuItems(List<TreeItem<String>> selected) {
        long plottable = selected.stream().filter(this::isPlottable).count();

        MenuItem enableItem = new MenuItem("Ativar Plot (" + plottable + ")");
        enableItem.setDisable(plottable == 0);
        enableItem.setOnAction(e -> selected.stream().filter(this::isPlottable)
                .forEach(i -> plotAreaView.setLayerVisible(i, true)));

        MenuItem disableItem = new MenuItem("Desativar Plot (" + plottable + ")");
        disableItem.setDisable(plottable == 0);
        disableItem.setOnAction(e -> selected.stream().filter(this::isPlottable)
                .forEach(i -> plotAreaView.setLayerVisible(i, false)));

        MenuItem removeItem = new MenuItem("Remover (" + selected.size() + ")");
        removeItem.setOnAction(e -> removeSelectionFromProject(selected));

        return List.of(enableItem, disableItem, removeItem);
    }

    private boolean isPlottable(TreeItem<String> item) {
        return gerberByItem.containsKey(item) || excellonByItem.containsKey(item);
    }

    private void removeSelectionFromProject(List<TreeItem<String>> items) {
        for (TreeItem<String> item : items) {
            if (gerberByItem.containsKey(item)) {
                removeFromProject(item, gerberByItem);
            } else if (excellonByItem.containsKey(item)) {
                removeFromProject(item, excellonByItem);
            } else if (cncJobByItem.containsKey(item)) {
                removeFromProject(item, cncJobByItem);
            }
        }
    }

    /**
     * "Exibir no Plot Area", "Ativar/Desativar Plot", "Definir Cor...",
     * "Gerar Isolamento..." and "Remover" for a single Gerber tree item -
     * the visibility toggle and color picker mirror the legacy per-object
     * menu's Enable/Disable Plot and Set Color (UI_INVENTORY.md section 1),
     * now that each object is its own PlotAreaView layer instead of one
     * replacing another.
     */
    private List<MenuItem> gerberContextMenuItems(TreeItem<String> item, GerberImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem visibilityItem = new MenuItem(plotAreaView.isLayerVisible(item) ? "Desativar Plot" : "Ativar Plot");
        visibilityItem.setOnAction(e -> plotAreaView.setLayerVisible(item, !plotAreaView.isLayerVisible(item)));

        MenuItem colorItem = new MenuItem("Definir Cor...");
        colorItem.setOnAction(e -> editLayerColor(item));

        MenuItem isolationItem = new MenuItem("Gerar Isolamento...");
        isolationItem.setOnAction(e -> generateIsolation(item, image));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, gerberByItem));

        return List.of(showItem, visibilityItem, colorItem, isolationItem, removeItem);
    }

    /** Same as {@link #gerberContextMenuItems}, minus isolation, plus "Gerar G-code de furacao". */
    private List<MenuItem> excellonContextMenuItems(TreeItem<String> item, ExcellonImage image) {
        MenuItem showItem = new MenuItem("Exibir no Plot Area");
        showItem.setOnAction(e -> focusLayer(item));

        MenuItem visibilityItem = new MenuItem(plotAreaView.isLayerVisible(item) ? "Desativar Plot" : "Ativar Plot");
        visibilityItem.setOnAction(e -> plotAreaView.setLayerVisible(item, !plotAreaView.isLayerVisible(item)));

        MenuItem colorItem = new MenuItem("Definir Cor...");
        colorItem.setOnAction(e -> editLayerColor(item));

        MenuItem gcodeItem = new MenuItem("Gerar G-code de furacao...");
        gcodeItem.setOnAction(e -> generateDrillGCode(item, image));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, excellonByItem));

        return List.of(showItem, visibilityItem, colorItem, gcodeItem, removeItem);
    }

    private void focusLayer(TreeItem<String> item) {
        plotAreaView.setLayerVisible(item, true);
        plotAreaView.bringToFront(item);
        plotAreaView.fitToLayer(item);
        centerTabs.getSelectionModel().select(0);
    }

    /** Only a fill color is asked for - the legacy dialog doesn't expose a separate outline color either. */
    private void editLayerColor(TreeItem<String> item) {
        Color[] current = plotAreaView.layerColors(item);
        Color currentFill = current != null ? current[0] : GERBER_FILL;
        LayerColorDialog.show(currentFill)
                .ifPresent(fill -> plotAreaView.setLayerColors(item, fill, fill.darker()));
    }

    private List<MenuItem> cncJobContextMenuItems(TreeItem<String> item, CncJobEntry entry) {
        MenuItem viewItem = new MenuItem("Ver G-code");
        viewItem.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));

        MenuItem removeItem = new MenuItem("Remover");
        removeItem.setOnAction(e -> removeFromProject(item, cncJobByItem));

        return List.of(viewItem, removeItem);
    }

    private TextArea buildGCodeViewer(String gcode) {
        TextArea area = new TextArea(gcode);
        area.setEditable(false);
        area.setStyle("-fx-font-family: monospace;");
        return area;
    }

    private void addCncJobToProject(String outputFileName, String sourceName, Path outputFile, String gcode) {
        TreeItem<String> item = new TreeItem<>(outputFileName);
        cncJobByItem.put(item, new CncJobEntry(sourceName, outputFile, gcode));
        cncJobsNode.getChildren().add(item);
    }

    /**
     * Loads DrillGCodeToolPanel into the Tool tab (appTools/ToolDrilling.py's
     * run() switches app.ui.tool_tab to its own UI the same way - see
     * {@link #openToolPanel}), and writes plain drill G-code (GCodeGenerator)
     * to a file the user picks once "Gerar" is clicked. Runs on the FX
     * thread directly - string-building over a few hundred/thousand points
     * is not the kind of work secao 4.3 is about; move this to JobExecutor
     * if a pathological input ever makes it worth it.
     */
    private void generateDrillGCode(TreeItem<String> item, ExcellonImage image) {
        openToolPanel("Drilling Tool", DrillGCodeToolPanel.build(image,
                result -> runDrillGCodeGeneration(item, image, result), this::closeToolPanel));
    }

    private void runDrillGCodeGeneration(TreeItem<String> item, ExcellonImage image, DrillGCodeToolPanel.Result result) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de furacao");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_drill.nc");
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        try {
            String gcode = GCodeGenerator.generateDrillGCode(image, result.params(), result.selectedToolIds());
            Files.writeString(outFile.toPath(), gcode);
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de furacao salvo em " + outFile + " (" + gcode.lines().count() + " linhas).");
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), gcode);
            closeToolPanel();
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code: " + e.getMessage());
        }
    }

    /**
     * Loads IsolationToolPanel into the Tool tab (appTools/ToolIsolation.py's
     * run() switches app.ui.tool_tab to its own UI the same way - see
     * {@link #openToolPanel}). Once "Gerar" is clicked, generates an
     * isolation toolpath around the Gerber's copper (IsolationGenerator,
     * ported from appTools/ToolIsolation.py + camlib.py's
     * Gerber.isolation_geometry() - see its class doc for exactly what was
     * and wasn't carried over), shows it as its own cyan stroke-only layer
     * alongside the copper's own layer (the copper stays visible, unlike
     * opening a different file), then writes G-code the same way
     * runDrillGCodeGeneration() does.
     */
    private void generateIsolation(TreeItem<String> item, GerberImage image) {
        openToolPanel("Isolation Tool", IsolationToolPanel.build(image.units(),
                params -> runIsolationGeneration(item, image, params), this::closeToolPanel));
    }

    private void runIsolationGeneration(TreeItem<String> item, GerberImage image, IsolationToolPanel.Result params) {
        IsolationResult isolation = IsolationGenerator.generate(image.units(), image.solidGeometry(), params.geometryParams());
        if (isolation.isEmpty()) {
            appendConsole("Isolamento nao gerou nenhum anel (geometria de cobre vazia?).");
            return;
        }

        plotAreaView.putLayer(new IsolationLayerKey(item), PlotAreaView.LayerCategory.OVERLAY,
                isolation.geometry(), ISOLATION_COLOR, ISOLATION_COLOR, true);
        centerTabs.getSelectionModel().select(0);
        appendConsole(String.format("Isolamento: %d aneis, comprimento total=%.4f, bounds=%s",
                isolation.ringCount(), isolation.totalLength(), Arrays.toString(isolation.bounds())));

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar G-code de isolamento");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("G-code", "*.nc", "*.gcode", "*.tap"));
        chooser.setInitialFileName(item.getValue().replaceFirst("\\.[^.]+$", "") + "_isolation.nc");
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File outFile = chooser.showSaveDialog(scene.getWindow());
        if (outFile == null) {
            return;
        }

        try {
            String gcode = GCodeGenerator.generateIsolationGCode(isolation, params.gcodeParams());
            Files.writeString(outFile.toPath(), gcode);
            AppPreferences.saveLastCamDirectory(outFile.getParentFile().getAbsolutePath());
            appendConsole("G-code de isolamento salvo em " + outFile + " (" + gcode.lines().count() + " linhas).");
            addCncJobToProject(outFile.getName(), item.getValue(), outFile.toPath(), gcode);
            closeToolPanel();
        } catch (Exception e) {
            appendConsole("Falha ao gerar/salvar G-code de isolamento: " + e.getMessage());
        }
    }

    private void removeFromProject(TreeItem<String> item, Map<TreeItem<String>, ?> byItem) {
        item.getParent().getChildren().remove(item);
        byItem.remove(item);
        sourcePathByItem.remove(item);
        plotAreaView.removeLayer(item);
        plotAreaView.removeLayer(new IsolationLayerKey(item));
        plotAreaView.removeLayer(new MarkLayerKey(item));
        appendConsole("Removido do projeto: " + item.getValue());
    }

    /**
     * Swaps in a per-object panel matching appGUI/ObjectUI.py's GerberObjectUI/
     * ExcellonObjectUI (shown via ObjectCollection.on_list_selection_change ->
     * FlatCAMObj.build_ui() swapping the object's own persistent UI widget
     * into the Properties scroll area). Rebuilt from scratch on every
     * selection change instead of one persistent widget per object - this
     * app doesn't keep a live UI instance per object the way the legacy one
     * does, but the effect (the right panel for whatever is selected) is the
     * same. Editor/NCC Tool/Cutout Tool/Utilities/Transformations from the
     * legacy panel are deliberately left out - those need subsystems
     * (an object editor, non-copper clearing, board cutout, geometry
     * transforms) this phase doesn't have.
     */
    private void showProperties(TreeItem<String> item) {
        Node content;
        GerberImage gerberImage = item == null ? null : gerberByItem.get(item);
        ExcellonImage excellonImage = item == null ? null : excellonByItem.get(item);
        CncJobEntry cncJob = item == null ? null : cncJobByItem.get(item);
        if (gerberImage != null) {
            content = buildGerberPropertiesPanel(item, gerberImage);
        } else if (excellonImage != null) {
            content = buildExcellonPropertiesPanel(item, excellonImage);
        } else if (cncJob != null) {
            content = buildCncJobPropertiesPanel(item, cncJob);
        } else {
            content = propertiesPlaceholder;
        }
        propertiesContainer.getChildren().setAll(content);
    }

    /** "Gerber Object" header, Plot Options (Solid/Multi-Color), Name, Plot, Properties, Isolation Routing - see ObjectUI.py's GerberObjectUI. */
    private Node buildGerberPropertiesPanel(TreeItem<String> item, GerberImage image) {
        VBox box = objectPropertiesHeader("Gerber Object", GERBER_FILL);

        CheckBox solidCb = new CheckBox("Solid");
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox("Multi-Color");
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow("Plot Options:", solidCb, multicolorCb));

        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> plotAreaView.setLayerVisible(item, plotCb.isSelected()));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button isolationButton = new Button("Isolation Routing");
        isolationButton.setMaxWidth(Double.MAX_VALUE);
        isolationButton.setOnAction(e -> generateIsolation(item, image));
        box.getChildren().add(isolationButton);

        box.getChildren().add(new Label("Apertures Table:"));
        box.getChildren().add(buildAperturesTableSection(item, image));

        box.getChildren().add(propertiesSection(String.format(
                "Unidades: %s%nAperturas: %d%nArea total: %.4f%nBounds: %s",
                image.units(), image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())
        )));
        return box;
    }

    /**
     * Wires GerberAperturesTable's per-row "Mark" checkboxes to a highlight
     * overlay layer - purely visual, matching the confirmed Python behavior
     * (no tool reads mark state, see GerberAperturesTable's class doc).
     * Resets any marks left over from a previous view of this same object's
     * panel first, so the checkboxes (which always start unchecked, since
     * the panel is rebuilt from scratch on every selection) never disagree
     * with what's actually highlighted.
     */
    private Node buildAperturesTableSection(TreeItem<String> item, GerberImage image) {
        MarkLayerKey markKey = new MarkLayerKey(item);
        plotAreaView.removeLayer(markKey);
        Set<String> markedCodes = new LinkedHashSet<>();
        return GerberAperturesTable.build(image, (code, marked) -> {
            if (marked) {
                markedCodes.add(code);
            } else {
                markedCodes.remove(code);
            }
            if (markedCodes.isEmpty()) {
                plotAreaView.removeLayer(markKey);
                return;
            }
            List<Geometry> shapes = markedCodes.stream()
                    .map(c -> image.apertureGeometry().get(c))
                    .filter(Objects::nonNull)
                    .toList();
            Geometry union = shapes.size() == 1 ? shapes.get(0) : UnaryUnionOp.union(shapes);
            plotAreaView.putLayer(markKey, PlotAreaView.LayerCategory.OVERLAY, union, MARK_COLOR, MARK_COLOR, false);
        });
    }

    /** Same shape as {@link #buildGerberPropertiesPanel}, minus isolation, plus drilling G-code - see ObjectUI.py's ExcellonObjectUI. */
    private Node buildExcellonPropertiesPanel(TreeItem<String> item, ExcellonImage image) {
        VBox box = objectPropertiesHeader("Excellon Object", DRILL_FILL);

        CheckBox solidCb = new CheckBox("Solid");
        solidCb.setSelected(plotAreaView.isLayerFilled(item));
        solidCb.setOnAction(e -> plotAreaView.setLayerFilled(item, solidCb.isSelected()));
        CheckBox multicolorCb = new CheckBox("Multi-Color");
        multicolorCb.setSelected(plotAreaView.isLayerMulticolor(item));
        multicolorCb.setOnAction(e -> plotAreaView.setLayerMulticolor(item, multicolorCb.isSelected()));
        box.getChildren().add(labeledRow("Plot Options:", solidCb, multicolorCb));

        box.getChildren().add(nameRow(item));

        CheckBox plotCb = new CheckBox();
        plotCb.setSelected(plotAreaView.isLayerVisible(item));
        plotCb.setOnAction(e -> plotAreaView.setLayerVisible(item, plotCb.isSelected()));
        box.getChildren().add(labeledRow("Plot:", plotCb));

        Button gcodeButton = new Button("Gerar G-code de furacao...");
        gcodeButton.setMaxWidth(Double.MAX_VALUE);
        gcodeButton.setOnAction(e -> generateDrillGCode(item, image));
        box.getChildren().add(gcodeButton);

        // Read-only per-tool breakdown - ObjectUI.py's tools_table, minus the per-tool
        // "P" plot-visibility checkbox (needs per-tool sub-layers our renderer doesn't
        // have yet) and the milling-conversion buttons (no milling tool ported yet).
        box.getChildren().add(new Label("Tools Table:"));
        box.getChildren().add(DrillGCodeToolPanel.buildToolsTableView(image));

        box.getChildren().add(propertiesSection(String.format(
                "Unidades: %s%nFuros totais: %d%nSlots totais: %d%nBounds: %s",
                image.units(), image.totalDrills(), image.totalSlots(), Arrays.toString(image.bounds())
        )));
        return box;
    }

    private Node buildCncJobPropertiesPanel(TreeItem<String> item, CncJobEntry entry) {
        VBox box = objectPropertiesHeader("CNC Job Object", ISOLATION_COLOR);
        box.getChildren().add(nameRow(item));
        Button viewButton = new Button("Ver G-code");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setOnAction(e -> openAuxiliaryTab(item.getValue(), () -> buildGCodeViewer(entry.gcode())));
        box.getChildren().add(viewButton);
        box.getChildren().add(propertiesSection(String.format(
                "Origem: %s%nArquivo: %s%nLinhas: %d",
                entry.sourceName(), entry.outputFile(), entry.gcode().lines().count()
        )));
        return box;
    }

    private VBox objectPropertiesHeader(String title, Color swatchColor) {
        Rectangle swatch = new Rectangle(14, 14, swatchColor);
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        HBox header = new HBox(6, swatch, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, header);
        box.setPadding(new Insets(10));
        return box;
    }

    private HBox labeledRow(String label, Node... controls) {
        HBox row = new HBox(10, new Label(label));
        row.getChildren().addAll(controls);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** "Name:" + an editable field - renaming just changes the TreeItem's own value (its tree label), matching FlatCAMObj.on_name_activate(). */
    private HBox nameRow(TreeItem<String> item) {
        TextField nameField = new TextField(item.getValue());
        HBox.setHgrow(nameField, Priority.ALWAYS);
        Runnable commit = () -> {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(item.getValue())) {
                item.setValue(newName);
            }
        };
        nameField.setOnAction(e -> commit.run());
        nameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commit.run();
            }
        });
        return labeledRow("Name:", nameField);
    }

    /** The legacy "PROPERTIES" toggle button + inline stats frame, done here as a collapsible section. */
    private TitledPane propertiesSection(String text) {
        Label label = new Label(text);
        TitledPane pane = new TitledPane("PROPERTIES", label);
        pane.setExpanded(false);
        return pane;
    }

    /**
     * Plot Area (viewport) is the one tab that can never be closed;
     * everything else - Preferences, Tools Database, editors - opens here on
     * demand via {@link #openAuxiliaryTab}, matching appGUI/MainGUI.py's
     * plot_tab_area instead of the separate right-hand panel the skeleton
     * used to have.
     */
    private TabPane buildCenterTabs() {
        plotAreaView.getStyleClass().add("viewport-placeholder");
        Tab plotAreaTab = new Tab("Plot Area", plotAreaView);
        plotAreaTab.setClosable(false);
        centerTabs.getTabs().add(plotAreaTab);
        return centerTabs;
    }

    /**
     * Opens {@code title} in the center tab strip, or focuses it if already
     * open - the legacy app never duplicates these auxiliary tabs either.
     */
    private void openAuxiliaryTab(String title, java.util.function.Supplier<javafx.scene.Node> content) {
        for (Tab tab : centerTabs.getTabs()) {
            if (title.equals(tab.getText())) {
                centerTabs.getSelectionModel().select(tab);
                return;
            }
        }
        Tab tab = new Tab(title, content.get());
        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
    }

    private StackPane buildPreferencesPlaceholder() {
        return centeredPlaceholder("Preferencias\n(placeholder - ver UI_INVENTORY.md secao 4)");
    }

    private StackPane buildToolsDbPlaceholder() {
        return centeredPlaceholder("Tools Database\n(placeholder - ver UI_INVENTORY.md secao 6)");
    }

    private StackPane centeredPlaceholder(String text) {
        Label label = new Label(text);
        label.setTextAlignment(TextAlignment.CENTER);
        return new StackPane(label);
    }

    private VBox buildBottomPanel() {
        HBox progressRow = new HBox(8, progressBar);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(4));
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        console.setEditable(false);
        console.setPrefRowCount(6);
        VBox.setVgrow(console, Priority.ALWAYS);

        VBox panel = new VBox(progressRow, console);
        panel.getStyleClass().add("bottom-panel");
        return panel;
    }

    private void runDemoJob() {
        if (runningJob != null) {
            return;
        }
        runDemoJobButton.setDisable(true);
        cancelJobButton.setDisable(false);
        progressBar.setProgress(0);
        setStatus("Executando...", RUNNING_COLOR);
        appendConsole("Job de demonstracao iniciado (nao bloqueia a UI - tente redimensionar a janela).");

        JobHandle<Void> handle = jobExecutor.submit(new DemoJob(), (fraction, message) ->
                Platform.runLater(() -> {
                    progressBar.setProgress(fraction);
                    statusLabel.setText(message);
                }));
        runningJob = handle;

        handle.completion()
                .thenAccept(result -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    appendConsole("Job de demonstracao concluido.");
                    onJobFinished();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        if (isCancellation(error)) {
                            setStatus("Cancelado.", CANCELLED_COLOR);
                            appendConsole("Job de demonstracao cancelado pelo usuario.");
                        } else {
                            setStatus("Falhou.", ERROR_COLOR);
                            appendConsole("Job de demonstracao falhou: " + error.getMessage());
                        }
                        onJobFinished();
                    });
                    return null;
                });
    }

    /**
     * Fase 3 vertical slice, minimal: open -> parse (flatcam-cam) -> display
     * (a throwaway Canvas render - see PlotAreaView). Reuses the same
     * progress bar/cancel button/console as the demo job, processing any
     * number of selected files one at a time (matching the legacy app's
     * multi-select file-open dialogs).
     */
    private void openGerberPrototype() {
        List<File> files = pickCamFiles("Abrir Gerber (prototipo)",
                new FileChooser.ExtensionFilter("Gerber", "*.gbr", "*.cmp", "*.gtl", "*.gbl", "*.gm1", "*.txt"));
        if (!files.isEmpty()) {
            openGerberQueue(files, 0);
        }
    }

    private void openGerberQueue(List<File> files, int index) {
        if (index >= files.size()) {
            onJobFinished();
            return;
        }
        File file = files.get(index);
        beginJob("Analisando " + file.getName() + "...");
        JobHandle<GerberImage> handle = jobExecutor.submit(context -> new GerberParser().parse(file.toPath()), null);
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    progressBar.setProgress(1);
                    unitsLabel.setText("Unidades: " + image.units());
                    appendConsole(String.format(
                            "Gerber OK: %d aperturas, area=%.4f, bounds=%s",
                            image.apertures().size(), image.totalArea(), Arrays.toString(image.bounds())));
                    TreeItem<String> item = addGerberToProject(file, image);
                    plotAreaView.fitToLayer(item);
                    openGerberQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Gerber " + file.getName() + ": ");
                        openGerberQueue(files, index + 1);
                    });
                    return null;
                });
    }

    /** Fase 4 vertical slice - same shape as {@link #openGerberPrototype()}, see flatcam-cam's ExcellonParser. */
    private void openExcellonPrototype() {
        List<File> files = pickCamFiles("Abrir Excellon (prototipo)",
                new FileChooser.ExtensionFilter("Excellon", "*.drl", "*.exc", "*.txt", "*.xln"));
        if (!files.isEmpty()) {
            openExcellonQueue(files, 0);
        }
    }

    private void openExcellonQueue(List<File> files, int index) {
        if (index >= files.size()) {
            onJobFinished();
            return;
        }
        File file = files.get(index);
        beginJob("Analisando " + file.getName() + "...");
        JobHandle<ExcellonImage> handle = jobExecutor.submit(context -> new ExcellonParser().parse(file.toPath()), null);
        runningJob = handle;

        handle.completion()
                .thenAccept(image -> Platform.runLater(() -> {
                    setStatus("Concluido.", IDLE_COLOR);
                    progressBar.setProgress(1);
                    unitsLabel.setText("Unidades: " + image.units());
                    appendConsole(String.format(
                            "Excellon OK: %d ferramentas, %d furos, %d slots, bounds=%s",
                            image.toolDiameters().size(), image.totalDrills(), image.totalSlots(),
                            Arrays.toString(image.bounds())));
                    TreeItem<String> item = addExcellonToProject(file, image);
                    plotAreaView.fitToLayer(item);
                    openExcellonQueue(files, index + 1);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportJobError(error, "Falha ao abrir Excellon " + file.getName() + ": ");
                        openExcellonQueue(files, index + 1);
                    });
                    return null;
                });
    }

    /** Shared "pick fabrication files" flow (multi-select): remembers the last folder across both Gerber and Excellon. */
    private List<File> pickCamFiles(String title, FileChooser.ExtensionFilter filter) {
        if (runningJob != null) {
            appendConsole("Ja ha um job em andamento.");
            return List.of();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        String fallbackDir = Path.of("tests/gerber_files").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastCamDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        List<File> files = chooser.showOpenMultipleDialog(scene.getWindow());
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        AppPreferences.saveLastCamDirectory(files.get(0).getParentFile().getAbsolutePath());
        return files;
    }

    private void beginJob(String statusText) {
        runDemoJobButton.setDisable(true);
        cancelJobButton.setDisable(false);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus(statusText, RUNNING_COLOR);
        appendConsole(statusText);
    }

    private void reportJobError(Throwable error, String failurePrefix) {
        if (isCancellation(error)) {
            setStatus("Cancelado.", CANCELLED_COLOR);
            appendConsole("Operacao cancelada.");
        } else {
            setStatus("Falhou.", ERROR_COLOR);
            appendConsole(failurePrefix + error.getMessage());
        }
    }

    /**
     * Writes which Gerber/Excellon files are open and which G-code jobs were
     * generated - see ProjectFile's doc for why this is a list of paths to
     * re-parse, not a geometry snapshot like the legacy .FlatPrj.
     */
    private void saveProject() {
        List<String> gerberPaths = gerberByItem.keySet().stream()
                .map(sourcePathByItem::get).map(Path::toString).toList();
        List<String> excellonPaths = excellonByItem.keySet().stream()
                .map(sourcePathByItem::get).map(Path::toString).toList();
        List<ProjectFile.CncJobRecord> jobs = cncJobByItem.values().stream()
                .map(entry -> new ProjectFile.CncJobRecord(entry.sourceName(), entry.outputFile().toString()))
                .toList();
        ProjectFile project = new ProjectFile(gerberPaths, excellonPaths, jobs);

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Projeto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto FlatCAM Next", "*.fcnproj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showSaveDialog(scene.getWindow());
        if (file == null) {
            return;
        }

        try {
            ProjectFileIO.save(project, file.toPath());
            AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());
            appendConsole("Projeto salvo em " + file);
        } catch (IOException e) {
            appendConsole("Falha ao salvar projeto: " + e.getMessage());
        }
    }

    /** Clears the current project, then re-parses every file the loaded ProjectFile references. */
    private void openProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir Projeto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Projeto FlatCAM Next", "*.fcnproj"));
        String fallbackDir = Path.of("").toAbsolutePath().toString();
        Path lastDir = Path.of(AppPreferences.loadLastProjectDirectory(fallbackDir));
        if (Files.isDirectory(lastDir)) {
            chooser.setInitialDirectory(lastDir.toFile());
        }
        File file = chooser.showOpenDialog(scene.getWindow());
        if (file == null) {
            return;
        }

        ProjectFile project;
        try {
            project = ProjectFileIO.load(file.toPath());
        } catch (IOException e) {
            appendConsole("Falha ao abrir projeto: " + e.getMessage());
            return;
        }
        AppPreferences.saveLastProjectDirectory(file.getParentFile().getAbsolutePath());

        clearProject();
        for (String path : project.gerberPaths()) {
            loadGerberFileSync(new File(path));
        }
        for (String path : project.excellonPaths()) {
            loadExcellonFileSync(new File(path));
        }
        for (ProjectFile.CncJobRecord job : project.cncJobs()) {
            Path outputPath = Path.of(job.outputPath());
            if (!Files.exists(outputPath)) {
                appendConsole("Aviso: G-code nao encontrado (arquivo movido/apagado?): " + outputPath);
                continue;
            }
            try {
                String gcode = Files.readString(outputPath);
                addCncJobToProject(outputPath.getFileName().toString(), job.sourceName(), outputPath, gcode);
            } catch (IOException e) {
                appendConsole("Aviso: nao foi possivel ler G-code " + outputPath + ": " + e.getMessage());
            }
        }
        appendConsole("Projeto aberto: " + file);
    }

    private void clearProject() {
        gerbersNode.getChildren().clear();
        excellonNode.getChildren().clear();
        cncJobsNode.getChildren().clear();
        gerberByItem.clear();
        excellonByItem.clear();
        cncJobByItem.clear();
        sourcePathByItem.clear();
        plotAreaView.clearLayers();
    }

    /**
     * Synchronous (not via JobExecutor) re-parse used only by openProject() -
     * loading a handful of files at project-open time is a batch operation,
     * not the single cancellable action the JobExecutor-based open flows are
     * built around, and parsing every fixture/real file checked so far takes
     * well under a second. Revisit if a pathological project makes this show.
     */
    private void loadGerberFileSync(File file) {
        try {
            GerberImage image = new GerberParser().parse(file.toPath());
            addGerberToProject(file, image);
        } catch (Exception e) {
            appendConsole("Falha ao reabrir Gerber " + file + ": " + e.getMessage());
        }
    }

    private void loadExcellonFileSync(File file) {
        try {
            ExcellonImage image = new ExcellonParser().parse(file.toPath());
            addExcellonToProject(file, image);
        } catch (Exception e) {
            appendConsole("Falha ao reabrir Excellon " + file + ": " + e.getMessage());
        }
    }

    /** Adds the tree item and, since every opened object gets its own layer now, its plot too - visible immediately. */
    private TreeItem<String> addGerberToProject(File file, GerberImage image) {
        TreeItem<String> item = new TreeItem<>(file.getName());
        gerberByItem.put(item, image);
        sourcePathByItem.put(item, file.toPath());
        gerbersNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.GERBER, image.solidGeometry(), GERBER_FILL, GERBER_STROKE, false);
        return item;
    }

    private TreeItem<String> addExcellonToProject(File file, ExcellonImage image) {
        TreeItem<String> item = new TreeItem<>(file.getName());
        excellonByItem.put(item, image);
        sourcePathByItem.put(item, file.toPath());
        excellonNode.getChildren().add(item);
        plotAreaView.putLayer(item, PlotAreaView.LayerCategory.EXCELLON, image.solidGeometry(), DRILL_FILL, DRILL_STROKE, false);
        return item;
    }

    private void cancelDemoJob() {
        if (runningJob != null) {
            runningJob.cancel();
        }
    }

    private static boolean isCancellation(Throwable error) {
        return error instanceof CancellationException || error.getCause() instanceof CancellationException;
    }

    private void onJobFinished() {
        runningJob = null;
        runDemoJobButton.setDisable(false);
        cancelJobButton.setDisable(true);
    }

    private void appendConsole(String line) {
        console.appendText(line + System.lineSeparator());
    }
}
