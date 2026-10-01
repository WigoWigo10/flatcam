package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class ToolsDatabaseDialogContrastTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void okButtonInheritsCurrentThemeAndHasReadableContrastInEveryState(ThemeOption theme) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
        FutureTask<Void> test = new FutureTask<>(() -> {
            JobExecutor jobs = new JobExecutor(1);
            Stage owner = new Stage(); owner.setScene(new Scene(new VBox(), 400, 240)); theme.applyTo(owner.getScene());
            try {
                var panel = new ToolsDatabasePanel(jobs, () -> owner, path -> {}, file -> new Label());
                Alert alert = panel.confirmationDialog("Excluir 1 ferramenta(s)?", "A base no disco so sera alterada ao salvar.");
                DialogPane pane = alert.getDialogPane();
                assertEquals(owner.getScene().getStylesheets(), pane.getStylesheets());
                Button ok = (Button) pane.lookupButton(ButtonType.OK);
                assertTrue(ok.isDefaultButton());
                pane.resize(400, 200); pane.applyCss(); pane.layout();
                for (String state : new String[]{"normal", "hover", "armed", "focused"}) {
                    ok.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), state.equals("hover"));
                    ok.pseudoClassStateChanged(PseudoClass.getPseudoClass("armed"), state.equals("armed"));
                    ok.pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), state.equals("focused"));
                    pane.applyCss();
                    Color background = (Color) ok.getBackground().getFills().getFirst().getFill();
                    Color foreground = (Color) ok.getTextFill();
                    double ratio = contrast(background, foreground);
                    assertTrue(ratio >= 4.5, theme + " / " + state + ": " + ratio);
                    assertEquals(theme.isDark(), luminance(foreground) < luminance(background));
                }
                if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                    var image = pane.snapshot(null, null); var pixels = image.getPixelReader();
                    var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getArgb(x, y));
                    javax.imageio.ImageIO.write(output, "png", Path.of("target", "tools-db-dialog-" + theme + ".png").toFile());
                }
                alert.close();
            } finally { owner.close(); jobs.shutdown(); }
            return null;
        });
        Platform.runLater(test); test.get(15, TimeUnit.SECONDS);
    }
    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }
    private static double channel(double value) { return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4); }
    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }
}
