package org.flatcam.fx;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javax.imageio.ImageIO;

/**
 * File > Exportar > PNG - app_Main.py's on_file_exportpng: the plot area exactly
 * as shown (objects, grid, axes), opaque like Python's _screenshot(alpha=False).
 * Captured at the screen's output scale so a HiDPI display gives a full-resolution
 * image instead of the logical size.
 */
final class PlotPngExporter {

    /** Opaque RGB pixels captured on the JavaFX thread; encoding can then run elsewhere. */
    record Capture(int width, int height, int[] argb) {
    }

    private PlotPngExporter() {
    }

    static Capture capture(Node plot, double outputScale) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(Transform.scale(outputScale, outputScale));
        WritableImage image = plot.snapshot(parameters, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return new Capture(width, height, argb);
    }

    /** Publishes a complete file without truncating an existing destination on failure. */
    static void write(Capture capture, Path path) throws IOException {
        BufferedImage image = new BufferedImage(capture.width(), capture.height(), BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, capture.width(), capture.height(), capture.argb(), 0, capture.width());
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("No PNG encoder available");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
