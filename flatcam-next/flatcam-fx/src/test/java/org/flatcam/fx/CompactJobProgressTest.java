package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class CompactJobProgressTest {
    @Test void progressAndCancellationTrackTheSharedJobState() throws Exception {
        TerminalPanelTest.fx(() -> {
            var fraction = new SimpleDoubleProperty(0);
            var disabled = new SimpleBooleanProperty(true);
            var calls = new AtomicInteger();
            var compact = new CompactJobProgress(fraction, disabled, calls::incrementAndGet);
            ProgressBar bar = (ProgressBar) compact.lookup("#compact-job-bar");
            Label label = (Label) compact.lookup("#compact-job-percent");
            Button cancel = (Button) compact.lookup("#compact-job-cancel");
            assertEquals("0%", label.getText());
            assertTrue(cancel.isDisabled()); assertFalse(cancel.isVisible()); assertFalse(cancel.isManaged());
            cancel.fire(); assertEquals(0, calls.get());
            fraction.set(0.37); disabled.set(false);
            assertEquals(0.37, bar.getProgress()); assertEquals("37%", label.getText());
            assertTrue(cancel.isVisible()); assertTrue(cancel.isManaged());
            cancel.fire(); assertEquals(1, calls.get());
            fraction.set(1); disabled.set(true);
            assertEquals("100%", label.getText()); assertFalse(cancel.isVisible());
            return null;
        });
    }

    @Test void unknownProgressIsNotPresentedAsAnInventedPercentage() throws Exception {
        TerminalPanelTest.fx(() -> {
            var fraction = new SimpleDoubleProperty();
            var compact = new CompactJobProgress(fraction, new SimpleBooleanProperty(true), () -> { });
            ProgressBar bar = (ProgressBar) compact.lookup("#compact-job-bar");
            Label label = (Label) compact.lookup("#compact-job-percent");
            for (double unknown : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                fraction.set(unknown);
                assertEquals(-1, bar.getProgress()); assertEquals("...", label.getText());
            }
            fraction.set(1.5);
            assertEquals(1, bar.getProgress()); assertEquals("100%", label.getText());
            compact.setVisible(false); assertFalse(compact.isManaged());
            compact.setVisible(true); assertTrue(compact.isManaged());
            return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void compactLayoutAndPercentageRemainReadableInEveryTheme(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            var fraction = new SimpleDoubleProperty(0.37);
            var compact = new CompactJobProgress(fraction, new SimpleBooleanProperty(false), () -> { });
            Label status = new Label("Carregando projeto...");
            status.setMinWidth(0); status.setMaxWidth(Double.MAX_VALUE);
            HBox feedback = new HBox(6, status, compact);
            feedback.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            feedback.setMinWidth(0);
            HBox.setHgrow(feedback, Priority.ALWAYS);
            HBox row = new HBox(6, feedback, new Label("X: -   Y: -   [mm]"));
            row.getStyleClass().add("status-bar");
            VBox root = new VBox(row);
            Scene scene = new Scene(root, 640, 28);
            theme.applyTo(scene);
            root.resize(640, 28); root.applyCss(); root.layout();
            Label label = (Label) compact.lookup("#compact-job-percent");
            assertEquals(Color.WHITE, label.getTextFill(), theme.toString());
            assertTrue(compact.getWidth() <= 160, "progress and cancel must stay short: " + compact.getWidth());
            assertTrue(compact.getBoundsInParent().getMinX() < 250, "keep compact progress next to the feedback on the left");
            assertTrue(compact.getHeight() <= 22, "must not enlarge the status bar: " + compact.getHeight());
            assertTrue(label.getWidth() >= label.prefWidth(-1), "percentage must not be truncated");
            Button cancel = (Button) compact.lookup("#compact-job-cancel");
            assertTrue(cancel.getBoundsInParent().getMinX() > 100, "cancel follows the bar");
            assertTrue(cancel.getWidth() >= 20);
            assertGraphicCentered(cancel);
            for (String state : new String[]{"hover", "armed", "focused"}) {
                PseudoClass pseudo = PseudoClass.getPseudoClass(state);
                cancel.pseudoClassStateChanged(pseudo, true);
                root.applyCss(); root.layout();
                assertGraphicCentered(cancel);
                assertTrue(cancel.getWidth() <= 22); assertTrue(cancel.getHeight() <= 20);
                cancel.pseudoClassStateChanged(pseudo, false);
            }
            root.applyCss(); root.layout();
            fraction.set(1); root.layout();
            assertEquals("100%", label.getText());
            assertTrue(label.getWidth() >= label.prefWidth(-1));
            if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                var image = root.snapshot(null, null);
                var pixels = image.getPixelReader();
                var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
                    output.setRGB(x, y, pixels.getArgb(x, y));
                javax.imageio.ImageIO.write(output, "png", Path.of("target", "compact-progress-" + theme + ".png").toFile());
            }
            return null;
        });
    }

    @Test void mainWindowSwitchesPresentationsWithoutLosingProgressOrCancellation() throws Exception {
        JobExecutor jobs = new JobExecutor(1);
        CountDownLatch release = new CountDownLatch(1);
        var handle = jobs.submit(context -> { release.await(10, TimeUnit.SECONDS); return null; }, null);
        try {
            TerminalPanelTest.fx(() -> {
                MainWindow window = new MainWindow(jobs);
                invoke(window, "buildToolBar");
                field("consoleCollapsed").setBoolean(window, true);
                VBox bottom = (VBox) invoke(window, "buildBottomPanel");
                HBox status = (HBox) invoke(window, "buildStatusBar");
                VBox root = new VBox(bottom, status);
                Scene scene = new Scene(root, 980, 100);
                ThemeOption.CLASSIC_DARK.applyTo(scene);
                root.resize(980, 100);
                root.applyCss(); root.layout();
                field("runningJob").set(window, handle);
                invoke(window, "beginJob", new Class<?>[]{String.class}, "Carregando...");
                invoke(window, "updateProgress", new Class<?>[]{double.class}, 0.42);
                Label full = (Label) bottom.lookup("#console-job-percent");
                Label compact = (Label) status.lookup("#compact-job-percent");
                assertEquals("42%", full.getText()); assertEquals(full.getText(), compact.getText());
                CompactJobProgress indicator = (CompactJobProgress) status.lookup("#compact-job-progress");
                assertTrue(indicator.isVisible()); assertTrue(indicator.isManaged());
                Button compactCancel = (Button) status.lookup("#compact-job-cancel");
                assertTrue(compactCancel.isVisible());
                compactCancel.fire(); assertTrue(handle.isCancelled());
                field("consoleCollapsed").setBoolean(window, false);
                invoke(window, "updateConsoleProgressVisibility");
                assertFalse(indicator.isVisible()); assertFalse(indicator.isManaged());
                assertEquals("42%", full.getText());
                Button fullCancel = (Button) bottom.lookup("#console-job-cancel");
                assertSame(full.getParent().getParent(), fullCancel.getParent(), "full cancel sits beside full progress");
                root.applyCss(); root.layout();
                assertGraphicCentered(fullCancel);
                assertTrue(fullCancel.getStyleClass().contains("job-cancel-button"));
                invoke(window, "updateProgress", new Class<?>[]{double.class}, 1d);
                field("consoleCollapsed").setBoolean(window, true);
                invoke(window, "updateConsoleProgressVisibility");
                assertEquals("100%", compact.getText()); assertTrue(indicator.isVisible());
                invoke(window, "onJobFinished");
                assertFalse(compactCancel.isVisible()); assertFalse(fullCancel.isVisible());
                assertEquals("100%", compact.getText());
                return null;
            });
        } finally { handle.cancel(); release.countDown(); jobs.shutdown(); }
    }

    private static Field field(String name) throws Exception {
        Field field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static void assertGraphicCentered(Button button) {
        var buttonBounds = button.localToScene(button.getLayoutBounds());
        var graphicBounds = button.getGraphic().localToScene(button.getGraphic().getBoundsInLocal());
        assertEquals(buttonBounds.getCenterX(), graphicBounds.getCenterX(), 0.75, "cancel icon horizontal alignment");
        assertEquals(buttonBounds.getCenterY(), graphicBounds.getCenterY(), 0.75, "cancel icon vertical alignment");
    }

    private static Object invoke(MainWindow window, String name) throws Exception {
        return invoke(window, name, new Class<?>[]{});
    }

    private static Object invoke(MainWindow window, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = MainWindow.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(window, arguments);
    }
}
