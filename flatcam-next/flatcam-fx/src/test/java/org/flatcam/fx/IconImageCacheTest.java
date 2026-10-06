package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.Test;

class IconImageCacheTest {
    @Test void repeatedIconsSharePixelsButKeepIndependentSceneNodesAndSizes() {
        var first = (ImageView) Icons.fromResource("flatcam_icon16.png", 16);
        var second = (ImageView) Icons.fromResource("flatcam_icon16.png", 24);
        assertNotSame(first, second);
        assertSame(first.getImage(), second.getImage());
        assertEquals(16, first.getFitWidth());
        assertEquals(24, second.getFitWidth());
        assertFalse(first.getImage().isError());
    }
}
