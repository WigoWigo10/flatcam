package org.flatcam.fx;

import java.nio.IntBuffer;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** Published worker pixels are immutable: no per-pixel conversion/copy on the FX thread. */
final class DensityFrameImage {
    private DensityFrameImage() { }

    static WritableImage create(int width, int height, int[] premultipliedArgb) {
        if (premultipliedArgb.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("Pixel count does not match the frame dimensions");
        }
        return new WritableImage(new PixelBuffer<>(width, height, IntBuffer.wrap(premultipliedArgb),
                PixelFormat.getIntArgbPreInstance()));
    }
}
