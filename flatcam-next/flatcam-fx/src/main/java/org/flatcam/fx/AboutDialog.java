package org.flatcam.fx;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Python's five About tabs plus a copyable technical summary; no WebView or startup network requests. */
final class AboutDialog extends Dialog<Void> {
    static final ButtonType COPY = new ButtonType(I18n.text("ui.8a8d84e4afb6"), ButtonBar.ButtonData.LEFT);
    private final Label feedback = label("");
    private int systemGeneration;

    AboutDialog(Window owner, ThemeOption theme) {
        this(owner, theme, null, text -> {
            ClipboardContent content = new ClipboardContent(); content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    AboutDialog(Window owner, ThemeOption theme, Consumer<String> openLink, Consumer<String> copy) {
        setTitle(I18n.text("ui.c60bd173627b")); setResizable(true);
        if (owner != null) initOwner(owner);
        setHeaderText(null);
        var pane = getDialogPane(); pane.setId("about-dialog");
        pane.setPrefSize(700, 510); pane.setMinSize(360, 340);
        var build = AboutInfo.build();
        Label title = label("FlatCAM FX"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label version = label(I18n.text("ui.9f8f7a58141b") + build.getProperty("appVersion", I18n.text("ui.d25d2f15d8b7")) + " • " + AboutInfo.channel(AboutInfo.version()));
        Label description = label(I18n.text("ui.b3ef5361775d"));
        description.setId("about-description");
        VBox identity = new VBox(5, title, version, description); identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        Node logo = Icons.fromResource("flatcam_icon256.png", 84); logo.setId("about-logo");
        if (theme.isDark()) {
            var outline = new javafx.scene.effect.DropShadow(javafx.scene.effect.BlurType.GAUSSIAN,
                    javafx.scene.paint.Color.web("#d9e2ef"), 1.8, 1.0, 0, 0);
            logo.setEffect(outline);
        }
        HBox header = new HBox(14, logo, identity);

        TabPane tabs = new TabPane(); tabs.setId("about-tabs"); tabs.setMinSize(0, 0);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox presentation = new VBox(10,
                label(I18n.text("ui.af4401f273a3")),
                label(I18n.text("ui.6cc87721c6b1")),
                link(I18n.text("ui.0df442958948"), "http://flatcam.org/", openLink),
                link(I18n.text("ui.dbc6f8f707cb"), "https://bitbucket.org/jpcgt/flatcam/src/Beta/", openLink),
                link(I18n.text("ui.187260665528"), "https://bitbucket.org/jpcgt/flatcam/downloads/", openLink),
                link(I18n.text("ui.0205433a68ad"), "https://bitbucket.org/jpcgt/flatcam/issues?status=new&status=open", openLink),
                label(I18n.text("ui.725d6f9515bd")));
        VBox programmers = new VBox(10,
                label(I18n.text("ui.36bf3daa763f")),
                creditsTable("programmers.tsv", "about-programmers", I18n.text("ui.5a99efe7fd7c"), I18n.text("ui.b5583cbdfcdd"), "E-mail"));
        VBox translators = new VBox(10,
                label(I18n.text("ui.2d49d61a89ac")),
                creditsTable("translators.tsv", "about-translators", I18n.text("ui.7ae9d418353b"), I18n.text("ui.26504ce4da18"), I18n.text("ui.6cc50224004f"), "E-mail"));
        VBox.setVgrow(programmers.getChildren().getLast(), Priority.ALWAYS);
        VBox.setVgrow(translators.getChildren().getLast(), Priority.ALWAYS);
        TextArea licenseText = readOnly(AboutInfo.resource("LICENSE")); licenseText.setId("about-license");
        VBox license = new VBox(8, label(I18n.text("ui.707c671d5907")),
                link("MIT — Open Source Initiative", "http://www.opensource.org/licenses/mit-license.php", openLink), licenseText);
        VBox.setVgrow(licenseText, Priority.ALWAYS);
        TextArea editorNotices = readOnly(AboutInfo.resource("editor-licenses.txt")); editorNotices.setPrefRowCount(7);
        var editorLicenses = new javafx.scene.control.TitledPane(I18n.text("ui.be92bda03b09"), editorNotices);
        editorLicenses.setExpanded(false); license.getChildren().add(editorLicenses);
        VBox attributions = new VBox(10, label(I18n.text("ui.6386f3b3cee6")),
                link("Freepik / Flaticon", "https://www.flaticon.com/authors/freepik", openLink),
                link("Icons8", "https://icons8.com", openLink),
                link("oNline Web Fonts", "http://www.onlinewebfonts.com", openLink),
                link("Pixel perfect / Flaticon", "https://www.flaticon.com/authors/pixel-perfect", openLink),
                label(I18n.text("ui.c0ae1cb85a18")),
                label(I18n.text("ui.76a644cb04fc")));
        TextArea system = readOnly(AboutInfo.systemSummary(null, null)); system.setId("about-system");
        TextArea graphicsDetails = readOnly(I18n.text("ui.b966f2846b46")); graphicsDetails.setId("about-graphics-details");
        graphicsDetails.setPrefRowCount(7);
        var details = new javafx.scene.control.TitledPane(I18n.text("ui.89c8a4f7e3a1"), graphicsDetails);
        details.setId("about-graphics-details-pane"); details.setExpanded(false);
        VBox systemContent = new VBox(8, system, details); VBox.setVgrow(system, Priority.ALWAYS);
        tabs.getTabs().addAll(tab(I18n.text("ui.90ae4241af1e"), scroll(presentation)), tab(I18n.text("ui.88ec6b34e85e"), programmers),
                tab(I18n.text("ui.565c6dd71ccf"), translators), tab(I18n.text("ui.70dfbb512f68"), license), tab(I18n.text("ui.81ef6bb70fe9"), scroll(attributions)), tab(I18n.text("ui.f150afd3c599"), systemContent));
        for (Tab tab : tabs.getTabs()) if (tab.getContent() instanceof VBox box) box.setPadding(new Insets(12));
        VBox content = new VBox(12, header, tabs, feedback); content.setMinSize(0, 0);
        VBox.setVgrow(tabs, Priority.ALWAYS); pane.setContent(content);
        pane.getButtonTypes().addAll(COPY, new ButtonType(I18n.text("ui.0f2bd88ef0ac"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Button copyButton = (Button) pane.lookupButton(COPY);
        ToolDescriptions.apply(copyButton,I18n.text("ui.cff2372bebc5"), I18n.text("ui.0ca6fa33a2c7"));
        ToolDescriptions.apply(system,I18n.text("ui.054fae562c81"), I18n.text("ui.b88683d8cba2"));
        ToolDescriptions.apply(details,I18n.text("ui.fa1333fa8146"), I18n.text("ui.a39b9e99bb95"));
        copyButton.setGraphic(Icons.fromResource(theme.isDark() ? "dark/copy32.png" : "copy32.png", 16));
        copyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try { copy.accept(system.getText() + I18n.text("ui.56151f0d10e8") + graphicsDetails.getText()); feedback.setText(I18n.text("ui.3645db7a4b90")); }
            catch (RuntimeException unavailable) { feedback.setText(I18n.text("ui.584e8d52f977")); }
        });
        setOnShowing(event -> {
            theme.applyTo(pane.getScene());
            if (pane.getScene().getWindow() instanceof Stage stage) {
                var resource = getClass().getResource("icons/flatcam_icon32.png");
                if (resource != null) stage.getIcons().setAll(new javafx.scene.image.Image(resource.toExternalForm()));
            }
            loadSystemInfo(system, graphicsDetails, owner);
        });
        setOnHidden(event -> systemGeneration++);
        // The dialog owns a scene even before showing; use it for correct initial styles and tests.
        if (pane.getScene() != null) theme.applyTo(pane.getScene());
        FluidTooltips.install(this, I18n.text("ui.97a0fa0947ad"));
    }

    private void loadSystemInfo(TextArea system, TextArea graphicsDetails, Window owner) {
        int generation = ++systemGeneration;
        system.setText(AboutInfo.systemSummary(null, null));
        graphicsDetails.setText(I18n.text("ui.b966f2846b46"));
        var hardware = new java.util.concurrent.CompletableFuture<SystemHardwareInfo>();
        Thread worker = new Thread(() -> {
            try { hardware.complete(SystemHardwareInfo.collect()); }
            catch (RuntimeException unavailable) { hardware.complete(SystemHardwareInfo.unavailable()); }
        }, "flatcam-about-hardware");
        worker.setDaemon(true); worker.start();
        hardware.completeOnTimeout(SystemHardwareInfo.unavailable(), 4, java.util.concurrent.TimeUnit.SECONDS)
                .thenCombine(GraphicsRuntimeInfo.query(owner), (cpu, graphics) ->
                List.of(AboutInfo.systemSummary(cpu, graphics), graphics.summary())).thenAccept(summary -> Platform.runLater(() -> {
                    if (generation == systemGeneration && isShowing()) {
                        system.setText(summary.get(0)); graphicsDetails.setText(summary.get(1));
                    }
                }));
    }

    private static Label label(String text) {
        Label label = new Label(text); label.setWrapText(true); label.setMinWidth(0);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE); return label;
    }
    private static TextArea readOnly(String text) { TextArea area = new TextArea(text); area.setEditable(false); area.setWrapText(true); area.setMinSize(0, 0); return area; }
    private static Tab tab(String title, Node content) { return new Tab(title, content); }
    private static ScrollPane scroll(VBox box) {
        box.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(box); scroll.setFitToWidth(true); scroll.setMinSize(0, 0); return scroll;
    }
    private Hyperlink link(String title, String url, Consumer<String> open) {
        Hyperlink link = new Hyperlink(title); link.setWrapText(true); link.setMinWidth(0); link.setUserData(url);
        link.setTooltip(new javafx.scene.control.Tooltip(url));
        link.setOnAction(event -> {
            try {
                if (open != null) open.accept(url);
                else browse(url, () -> feedback.setText(I18n.text("ui.d07498f62c5b") + url));
            }
            catch (RuntimeException unavailable) { feedback.setText(I18n.text("ui.d07498f62c5b") + url); }
        }); return link;
    }
    private static TableView<List<String>> creditsTable(String resource, String id, String... headers) {
        TableView<List<String>> table = new TableView<>(); table.setId(id); table.setMinSize(0, 0);
        ToolDescriptions.apply(table,I18n.text("ui.b44865793555"), I18n.text("ui.4567b6ff87f6"));
        table.getItems().setAll(AboutInfo.credits(resource, headers.length));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int index = 0; index < headers.length; index++) {
            int columnIndex = index; TableColumn<List<String>, String> column = new TableColumn<>(headers[index]);
            column.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().get(columnIndex)));
            column.setMinWidth(80); column.setPrefWidth(index == headers.length - 1 ? 185 : 155); column.setSortable(false);
            table.getColumns().add(column);
        }
        return table;
    }
    private static void browse(String url, Runnable failed) {
        Thread browser = new Thread(() -> {
            try { java.awt.Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception unavailable) {
                System.getLogger(AboutDialog.class.getName()).log(System.Logger.Level.WARNING, "Cannot open external link: " + url, unavailable);
                Platform.runLater(failed);
            }
        }, "flatcam-about-link"); browser.setDaemon(true); browser.start();
    }
}
