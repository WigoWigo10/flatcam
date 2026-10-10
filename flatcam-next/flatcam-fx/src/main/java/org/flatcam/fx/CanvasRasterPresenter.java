package org.flatcam.fx;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** FX presentation only: converts owned ARGB_PRE frames and places them on Canvas.
 * No raster computation, geometry, queues or layer cache is owned here. This is not
 * texture interop: Prism can still upload/convert the image when presenting it. */
final class CanvasRasterPresenter {
    /** Published images are borrowed for drawing only, never overwritten by another worker frame. */
    record Frame(DenseRenderer.View view, WritableImage image) { }

    Frame publish(DenseRenderer.Frame pixels, boolean pixelBuffer) {
        var view = pixels.view();
        // DenseRenderer transfers a completed array and never recycles/mutates it.
        // PixelBuffer borrows that array; the copy path must allocate a fresh image,
        // because an overview can still reference the preceding image.
        WritableImage image = pixelBuffer ? DensityFrameImage.create(view.width(), view.height(), pixels.pixels())
                : copy(null, view.width(), view.height(), pixels.pixels());
        return new Frame(view, image);
    }

    /** Only the synchronous scratch path may reuse an image; it has no published overview aliases. */
    Frame copyScratch(Frame previous, DenseRenderer.View view, int[] scratch) {
        return new Frame(view, copy(previous, view.width(), view.height(), scratch));
    }

    private static WritableImage copy(Frame previous, int width, int height, int[] pixels) {
        WritableImage image = previous != null && previous.view().width() == width && previous.view().height() == height
                ? previous.image() : new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbPreInstance(), pixels, 0, width);
        return image;
    }

    void drawCurrent(GraphicsContext gc, Frame frame, PlotCamera camera) {
        boolean smoothing = gc.isImageSmoothing();
        try {
            // Avoid bilinear blur on exact 1:1 coverage; restore the caller's state.
            gc.setImageSmoothing(false);
            gc.drawImage(frame.image(), camera.insetX(), camera.insetY());
        } finally { gc.setImageSmoothing(smoothing); }
    }

    void drawPreview(GraphicsContext gc, Frame frame, DenseRenderer.View now, PlotCamera camera) {
        DenseRenderer.View old = frame.view();
        double k = now.scale() / old.scale();
        double tx = now.offsetX() - old.offsetX() * k;
        double ty = now.offsetY() - old.offsetY() * k;
        gc.save();
        try {
            gc.beginPath();
            gc.rect(camera.insetX(), camera.insetY(), camera.width(), camera.height());
            gc.clip();
            // Unchanged-camera edits remain nearest-neighbour; transformed previews may smooth.
            gc.setImageSmoothing(k != 1 || tx != Math.rint(tx) || ty != Math.rint(ty));
            gc.drawImage(frame.image(), camera.insetX() + tx, camera.insetY() + ty,
                    old.width() * k, old.height() * k);
        } finally { gc.restore(); }
    }
}
