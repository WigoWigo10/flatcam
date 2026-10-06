package org.flatcam.fx;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;

/** Startup check for launchers: load Glass/Prism and render without opening a window or preferences. */
public final class LauncherProbe {
    private LauncherProbe() {
    }

    public static void run() throws Exception {
        FutureTask<java.util.concurrent.CompletableFuture<GraphicsRuntimeInfo>> render = new FutureTask<>(() -> {
            Canvas canvas = new Canvas(16, 16);
            canvas.getGraphicsContext2D().setFill(Color.CORNFLOWERBLUE);
            canvas.getGraphicsContext2D().fillRect(0, 0, 16, 16);
            var image = canvas.snapshot(null, null);
            if (image.getPixelReader().getArgb(8, 8) != 0xff6495ed) {
                throw new IllegalStateException("JavaFX launcher probe did not render the expected pixel");
            }
            int[] densityPixels = new int[16];
            java.util.Arrays.fill(densityPixels, 0xffff0000);
            densityPixels[5] = 0xff00ff00;
            // Match the viewport's exact-size density rendering; filtering blends adjacent pixels.
            canvas.getGraphicsContext2D().setImageSmoothing(false);
            canvas.getGraphicsContext2D().drawImage(DensityFrameImage.create(4, 4, densityPixels), 0, 0);
            var densitySnapshot = canvas.snapshot(null, null);
            if (densitySnapshot.getPixelReader().getArgb(1, 1) != 0xff00ff00
                    || densitySnapshot.getPixelReader().getArgb(2, 2) != 0xffff0000) {
                throw new IllegalStateException(String.format(
                        "PixelBuffer density frame probe did not render expected pixels: %08x, %08x",
                        densitySnapshot.getPixelReader().getArgb(1, 1),
                        densitySnapshot.getPixelReader().getArgb(2, 2)));
            }
            try (CodeEditor editor = new CodeEditor("(probe)\nG21\nG1 X1 F100\n", CodeSyntax.Language.MACHINE, false)) {
                var scene = new javafx.scene.Scene(editor, 600, 220); ThemeOption.CLASSIC_DARK.applyTo(scene);
                editor.resize(600, 220); editor.applyCss(); editor.layout(); editor.snapshot(null, null);
                if (!editor.getText().contains("G1 X1") || editor.lookup(".lineno") == null) {
                    throw new IllegalStateException("Code editor dependencies/layout probe failed");
                }
            }
            return GraphicsRuntimeInfo.query(null);
        });
        try {
            // Wait until startup completes before allowing the toolkit to shut down.
            Platform.startup(() -> Platform.runLater(render));
            var graphics = render.get(20, TimeUnit.SECONDS).get(4, TimeUnit.SECONDS);
            // Match the application's diagnostic output even when Windows stdout uses a legacy code page.
            var output = new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8);
            output.println("FlatCAM FX: JavaFX native libraries and offscreen rendering OK.");
            output.println(graphics.summary());
            output.println(SystemHardwareInfo.collect().summary());
        } finally {
            Platform.exit();
        }
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
