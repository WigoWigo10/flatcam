package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class AboutDialogTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void tabsThemeLayoutAndCopyUseTheRealDialogWithoutExternalActions(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            List<String> opened = new ArrayList<>(); List<String> copied = new ArrayList<>();
            AboutDialog dialog = new AboutDialog(null, theme, opened::add, copied::add);
            try {
                var pane = dialog.getDialogPane();
                pane.applyCss(); pane.layout();
                var tabs = (TabPane) pane.lookup("#about-tabs"); assertNotNull(tabs);
                assertEquals(List.of("Apresentação", "Programadores", "Tradutores", "Licença", "Atribuições", "Sistema"),
                        tabs.getTabs().stream().map(javafx.scene.control.Tab::getText).toList());
                assertTrue(dialog.isResizable()); assertNotNull(pane.lookup("#about-logo"));
                assertEquals(theme.isDark(), pane.lookup("#about-logo").getEffect() != null);
                assertTrue(pane.getScene().getStylesheets().stream().anyMatch(url -> url.contains(theme.isDark() ? "dark" : "light")));
                for (double width : new double[]{360, 480, 700}) {
                    pane.resize(width, 510);
                    for (int index = 0; index < tabs.getTabs().size(); index++) {
                        tabs.getSelectionModel().select(index); pane.applyCss(); pane.layout();
                        var content = tabs.getTabs().get(index).getContent();
                        assertTrue(content.getLayoutBounds().getWidth() > 0);
                        assertTrue(content.getLayoutBounds().getWidth() <= width, "tab must stay within resized dialog");
                        var description = (javafx.scene.control.Label) pane.lookup("#about-description");
                        assertTrue(description.getHeight() + 0.5 >= description.prefHeight(description.getWidth()),
                                "wrapped header must not lose text to vertical ellipsis");
                    }
                }
                tabs.getSelectionModel().select(1); pane.applyCss(); pane.layout();
                assertEquals(33, ((TableView<?>) pane.lookup("#about-programmers")).getItems().size());
                tabs.getSelectionModel().select(2); pane.applyCss(); pane.layout();
                assertEquals(8, ((TableView<?>) pane.lookup("#about-translators")).getItems().size());
                tabs.getSelectionModel().select(3); pane.applyCss(); pane.layout();
                TextArea license = (TextArea) pane.lookup("#about-license"); assertFalse(license.isEditable());
                assertEquals(AboutInfo.resource("LICENSE").replace("\r\n", "\n"), license.getText());
                tabs.getSelectionModel().select(5); pane.applyCss(); pane.layout();
                ((Button) pane.lookupButton(AboutDialog.COPY)).fire();
                assertEquals(List.of(((TextArea) pane.lookup("#about-system")).getText()), copied);
                assertFalse(dialog.isShowing()); // the copy action does not invoke Dialog.show/close.
                tabs.getSelectionModel().selectFirst(); pane.applyCss(); pane.layout();
                var links = pane.lookupAll(".hyperlink").stream().map(node -> (Hyperlink) node).toList();
                assertEquals(9, links.size()); assertTrue(opened.isEmpty());
                links.forEach(Hyperlink::fire);
                assertEquals(9, opened.size());
                assertTrue(opened.contains("http://flatcam.org/"));
                assertTrue(opened.stream().allMatch(url -> url.startsWith("http")));
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    pane.resize(700, 510); pane.applyCss(); pane.layout();
                    snapshot(pane, "about-" + theme + "-presentation.png");
                    tabs.getSelectionModel().select(1); pane.applyCss(); pane.layout();
                    snapshot(pane, "about-" + theme + "-credits.png");
                }
            } finally { dialog.close(); }
            return null;
        });
    }

    @org.junit.jupiter.api.Test void copyingDoesNotCloseTheShownDialogAndCloseStillWorks() throws Exception {
        TerminalPanelTest.fx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            List<String> copied = new ArrayList<>();
            AboutDialog dialog = new AboutDialog(null, ThemeOption.CLASSIC_DARK, ignored -> { }, copied::add);
            try {
                dialog.show(); assertTrue(dialog.isShowing());
                ((Button) dialog.getDialogPane().lookupButton(AboutDialog.COPY)).fire();
                assertTrue(dialog.isShowing()); assertEquals(1, copied.size());
                dialog.close(); assertFalse(dialog.isShowing());
            } finally { dialog.close(); }
            return null;
        });
    }

    @org.junit.jupiter.api.Test void shownSystemTabLoadsHardwareAsynchronouslyAndCopyIncludesTheResult() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(1);
        List<String> copied = new ArrayList<>();
        AboutDialog dialog = TerminalPanelTest.fx(() -> {
            javafx.application.Platform.setImplicitExit(false);
            AboutDialog created = new AboutDialog(null, ThemeOption.CLASSIC_DARK, ignored -> { }, copied::add);
            var tabs = (TabPane) created.getDialogPane().lookup("#about-tabs");
            tabs.getSelectionModel().select(5);
            TextArea system = (TextArea) tabs.getTabs().get(5).getContent();
            system.textProperty().addListener((observable, oldText, text) -> {
                if (text.contains("RAM física total:") && !text.contains("consultando")) ready.countDown();
            });
            created.show();
            assertTrue(system.getText().contains("consultando")); // completion cannot block or run inline on FX.
            return created;
        });
        try {
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS), "system information did not arrive");
            TerminalPanelTest.fx(() -> {
                TextArea system = (TextArea) dialog.getDialogPane().lookup("#about-system");
                assertTrue(system.getText().contains("CPU:"));
                assertTrue(system.getText().contains("RAM física disponível:"));
                assertTrue(system.getText().contains("Heap Java usado:"));
                assertTrue(system.getText().contains("Pipeline Prism ativo: com.sun.prism."));
                ((Button) dialog.getDialogPane().lookupButton(AboutDialog.COPY)).fire();
                assertEquals(List.of(system.getText()), copied); assertTrue(dialog.isShowing());
                if (Boolean.getBoolean("flatcam.tests.snapshots")) snapshot(dialog.getDialogPane(), "about-system-dark.png");
                return null;
            });
        } finally { TerminalPanelTest.fx(() -> { dialog.close(); return null; }); }
    }

    @org.junit.jupiter.api.Test void graphicsInfoUsesTheShownOwnerWindowRatherThanAssumingTheDefaultAdapter() throws Exception {
        javafx.stage.Stage owner = TerminalPanelTest.fx(() -> {
            var stage = new javafx.stage.Stage();
            var canvas = new javafx.scene.canvas.Canvas(100, 80);
            stage.setScene(new javafx.scene.Scene(new javafx.scene.Group(canvas))); stage.show();
            canvas.snapshot(null, null); return stage;
        });
        try {
            var future = TerminalPanelTest.fx(() -> GraphicsRuntimeInfo.query(owner));
            var report = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(report.pipeline().startsWith("com.sun.prism."), report.summary());
            if (report.pipeline().endsWith("D3DPipeline")) {
                assertFalse(report.adapter().contains("não identificada"), report.summary());
                assertTrue(report.scope().contains("janela principal"), report.summary());
            }
        } finally { TerminalPanelTest.fx(() -> { owner.close(); return null; }); }
    }

    private static void snapshot(javafx.scene.Node node, String name) throws Exception {
        var image = node.snapshot(null, null);
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", java.nio.file.Path.of("target", name).toFile());
    }
}
