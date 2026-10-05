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
    static final ButtonType COPY = new ButtonType("Copiar informações", ButtonBar.ButtonData.LEFT);
    private final Label feedback = label("");
    private int systemGeneration;

    AboutDialog(Window owner, ThemeOption theme) {
        this(owner, theme, null, text -> {
            ClipboardContent content = new ClipboardContent(); content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    AboutDialog(Window owner, ThemeOption theme, Consumer<String> openLink, Consumer<String> copy) {
        setTitle("Sobre o FlatCAM FX"); setResizable(true);
        if (owner != null) initOwner(owner);
        setHeaderText(null);
        var pane = getDialogPane(); pane.setId("about-dialog");
        pane.setPrefSize(700, 510); pane.setMinSize(360, 340);
        var build = AboutInfo.build();
        Label title = label("FlatCAM FX"); title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label version = label("Versão " + build.getProperty("appVersion", "não disponível") + " • Em desenvolvimento");
        Label description = label("Fabricação de placas de circuito impresso assistida por computador.\n"
                + "Reimplementação JavaFX do FlatCAM Python.");
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
                label("O FX mantém o fluxo Gerber/Excellon → Geometry → CNC Job, com migração ainda em andamento."),
                label("Links históricos do FlatCAM Python (não são downloads ou suporte específicos do FX):"),
                link("Site do FlatCAM", "http://flatcam.org/", openLink),
                link("Desenvolvimento do Python", "https://bitbucket.org/jpcgt/flatcam/src/Beta/", openLink),
                link("Downloads do Python", "https://bitbucket.org/jpcgt/flatcam/downloads/", openLink),
                link("Issue tracker do Python", "https://bitbucket.org/jpcgt/flatcam/issues?status=new&status=open", openLink),
                label("Referência deste checkout: FlatCAM Python 8.994 BETA — 2020/11/7.\n"
                        + "Destinos externos históricos podem ter mudado."));
        VBox programmers = new VBox(10,
                label("Créditos preservados do FlatCAM Python. Não representam automaticamente os autores do port FX.\n"
                        + "O desenvolvimento do FX está registrado no histórico Git deste projeto."),
                creditsTable("programmers.tsv", "about-programmers", "Programador", "Função no legado", "E-mail"));
        VBox translators = new VBox(10,
                label("Créditos das traduções do Python; não significam que o FX ofereça todos esses idiomas."),
                creditsTable("translators.tsv", "about-translators", "Idioma", "Tradutor", "Correções", "E-mail"));
        VBox.setVgrow(programmers.getChildren().getLast(), Priority.ALWAYS);
        VBox.setVgrow(translators.getChildren().getLast(), Priority.ALWAYS);
        TextArea licenseText = readOnly(AboutInfo.resource("LICENSE")); licenseText.setId("about-license");
        VBox license = new VBox(8, label("Licença MIT — texto original do LICENSE deste repositório."),
                link("MIT — Open Source Initiative", "http://www.opensource.org/licenses/mit-license.php", openLink), licenseText);
        VBox.setVgrow(licenseText, Priority.ALWAYS);
        TextArea editorNotices = readOnly(AboutInfo.resource("editor-licenses.txt")); editorNotices.setPrefRowCount(7);
        var editorLicenses = new javafx.scene.control.TitledPane("Bibliotecas do editor — avisos BSD 2-Clause", editorNotices);
        editorLicenses.setExpanded(false); license.getChildren().add(editorLicenses);
        VBox attributions = new VBox(10, label("O FX reutiliza os recursos gráficos do legado, que atribui ícones a:"),
                link("Freepik / Flaticon", "https://www.flaticon.com/authors/freepik", openLink),
                link("Icons8", "https://icons8.com", openLink),
                link("oNline Web Fonts", "http://www.onlinewebfonts.com", openLink),
                link("Pixel perfect / Flaticon", "https://www.flaticon.com/authors/pixel-perfect", openLink),
                label("Os ícones vetoriais do FX também incluem o desenho de pasta do Feather Icons (MIT)."),
                label("O FX usa OpenJFX, RichTextFX, JTS, org.json e ZXing. Componentes e recursos têm seus próprios avisos e licenças; "
                        + "a licença do aplicativo não substitui os termos dessas dependências."));
        TextArea system = readOnly(AboutInfo.technicalSummary()); system.setId("about-system");
        tabs.getTabs().addAll(tab("Apresentação", scroll(presentation)), tab("Programadores", programmers),
                tab("Tradutores", translators), tab("Licença", license), tab("Atribuições", scroll(attributions)), tab("Sistema", system));
        for (Tab tab : tabs.getTabs()) if (tab.getContent() instanceof VBox box) box.setPadding(new Insets(12));
        VBox content = new VBox(12, header, tabs, feedback); content.setMinSize(0, 0);
        VBox.setVgrow(tabs, Priority.ALWAYS); pane.setContent(content);
        pane.getButtonTypes().addAll(COPY, new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button copyButton = (Button) pane.lookupButton(COPY);
        copyButton.setGraphic(Icons.fromResource(theme.isDark() ? "dark/copy32.png" : "copy32.png", 16));
        copyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try { copy.accept(system.getText()); feedback.setText("Informações técnicas copiadas."); }
            catch (RuntimeException unavailable) { feedback.setText("Não foi possível copiar as informações."); }
        });
        setOnShowing(event -> {
            theme.applyTo(pane.getScene());
            if (pane.getScene().getWindow() instanceof Stage stage) {
                var resource = getClass().getResource("icons/flatcam_icon32.png");
                if (resource != null) stage.getIcons().setAll(new javafx.scene.image.Image(resource.toExternalForm()));
            }
            loadSystemInfo(system, owner);
        });
        setOnHidden(event -> systemGeneration++);
        // The dialog owns a scene even before showing; use it for correct initial styles and tests.
        if (pane.getScene() != null) theme.applyTo(pane.getScene());
    }

    private void loadSystemInfo(TextArea system, Window owner) {
        int generation = ++systemGeneration;
        system.setText(AboutInfo.technicalSummary());
        var hardware = new java.util.concurrent.CompletableFuture<SystemHardwareInfo>();
        Thread worker = new Thread(() -> {
            try { hardware.complete(SystemHardwareInfo.collect()); }
            catch (RuntimeException unavailable) { hardware.complete(SystemHardwareInfo.unavailable()); }
        }, "flatcam-about-hardware");
        worker.setDaemon(true); worker.start();
        hardware.completeOnTimeout(SystemHardwareInfo.unavailable(), 4, java.util.concurrent.TimeUnit.SECONDS)
                .thenCombine(GraphicsRuntimeInfo.query(owner), (cpu, graphics) ->
                AboutInfo.technicalSummary(cpu, graphics)).thenAccept(summary -> Platform.runLater(() -> {
                    if (generation == systemGeneration && isShowing()) system.setText(summary);
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
                else browse(url, () -> feedback.setText("Abra o link manualmente: " + url));
            }
            catch (RuntimeException unavailable) { feedback.setText("Abra o link manualmente: " + url); }
        }); return link;
    }
    private static TableView<List<String>> creditsTable(String resource, String id, String... headers) {
        TableView<List<String>> table = new TableView<>(); table.setId(id); table.setMinSize(0, 0);
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
