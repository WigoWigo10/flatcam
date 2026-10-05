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
