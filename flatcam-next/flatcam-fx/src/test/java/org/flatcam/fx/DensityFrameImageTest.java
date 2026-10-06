package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.image.PixelFormat;
import org.junit.jupiter.api.Test;

class DensityFrameImageTest {
    @Test void immutableWorkerPixelsKeepExactPremultipliedColorsIncludingTransparency() {
        int[] pixels = {0, 0xffff0000, 0x80402010, 0xff00ff00};
        var image = DensityFrameImage.create(2, 2, pixels);
        int[] read = new int[4];
        image.getPixelReader().getPixels(0, 0, 2, 2, PixelFormat.getIntArgbPreInstance(), read, 0, 2);
        assertArrayEquals(pixels, read);
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertThrows(UnsupportedOperationException.class, image::getPixelWriter,
                "published PixelBuffer images cannot be overwritten by a later worker generation");
    }

    @Test void mismatchedFrameDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DensityFrameImage.create(2, 2, new int[3]));
    }
}
