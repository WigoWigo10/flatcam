package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class CanvasRasterPresenterTest {
    private static DenseRenderer.View view(double scale, double ox, double oy) {
        return new DenseRenderer.View(new GeometryFactory().createPoint(), scale, 0, 0, ox, oy, 4, 4, 1, 0, 0, 1, 1.5);
    }

    @Test void publicationNeverOverwritesAnOverviewInEitherTransferModeAndScratchIsCopied() throws Exception {
        TerminalPanelTest.fx(() -> {
            var presenter = new CanvasRasterPresenter();
            for (boolean pixelBuffer : new boolean[]{true, false}) {
                int[] red = new int[16]; java.util.Arrays.fill(red, 0xffff0000);
                int[] green = new int[16]; java.util.Arrays.fill(green, 0xff00ff00);
                var first = presenter.publish(new DenseRenderer.Frame(view(1, 0, 4), red), pixelBuffer);
                var second = presenter.publish(new DenseRenderer.Frame(view(2, 0, 4), green), pixelBuffer);
                assertNotSame(first.image(), second.image());
                assertEquals(0xffff0000, first.image().getPixelReader().getArgb(0, 0));
                assertEquals(0xff00ff00, second.image().getPixelReader().getArgb(0, 0));
                if (pixelBuffer) assertThrows(UnsupportedOperationException.class, first.image()::getPixelWriter);
            }
            int[] scratch = new int[16]; java.util.Arrays.fill(scratch, 0x80402010);
            var copied = presenter.copyScratch(null, view(1, 0, 4), scratch);
            scratch[0] = 0;
            int[] pixels = new int[16];
            copied.image().getPixelReader().getPixels(0, 0, 4, 4, PixelFormat.getIntArgbPreInstance(), pixels, 0, 4);
            assertEquals(0x80402010, pixels[0]);
            var reused = presenter.copyScratch(copied, view(1, 0, 4), scratch);
            assertSame(copied.image(), reused.image());
            return null;
        });
    }

    @Test void currentAndTransformedPreviewMatchFrozenCanvasPresentationPixelsAndRestoreGraphicsState() throws Exception {
        TerminalPanelTest.fx(() -> {
            var presenter = new CanvasRasterPresenter();
            int[] pixels = {0, 0xffff0000, 0x80402010, 0xff00ff00,
                    0xffff0000, 0, 0xff0000ff, 0xffff0000, 0xff00ff00, 0xffff0000, 0, 0x80402010,
                    0xff0000ff, 0xff00ff00, 0xffff0000, 0};
            var frame = presenter.publish(new DenseRenderer.Frame(view(1, 0, 4), pixels), true);
            var camera = new PlotCamera(0, 0, 1, 12, 12, 3, 2);
            for (double k : new double[]{.5, 1, 2}) for (double shift : new double[]{0, .25, 2}) {
                var a = new Canvas(24, 24); var b = new Canvas(24, 24);
                var gc = a.getGraphicsContext2D(); gc.setImageSmoothing(false); gc.setLineWidth(7); gc.setStroke(Color.BLUE);
                var now = view(k, shift, 4 * k + shift);
                presenter.drawPreview(gc, frame, now, camera);
                frozen(b.getGraphicsContext2D(), frame, now, camera);
                compare(a, b);
                assertFalse(gc.isImageSmoothing());
                assertEquals(7, gc.getLineWidth()); assertEquals(Color.BLUE, gc.getStroke());
                // Also ensures the clip does not leak out of the presenter.
                gc.setFill(Color.BLUE); gc.fillRect(20, 20, 2, 2);
                assertEquals(Color.BLUE, a.snapshot(null, null).getPixelReader().getColor(20, 20));
            }
            var a = new Canvas(24, 24); var b = new Canvas(24, 24);
            var gc = a.getGraphicsContext2D(); gc.setImageSmoothing(true);
            presenter.drawCurrent(gc, frame, camera);
            b.getGraphicsContext2D().setImageSmoothing(false);
            b.getGraphicsContext2D().drawImage(frame.image(), 3, 2);
            compare(a, b); assertTrue(gc.isImageSmoothing());
            return null;
        });
    }

    private static void frozen(GraphicsContext gc, CanvasRasterPresenter.Frame frame, DenseRenderer.View now, PlotCamera camera) {
        var old = frame.view();
        double k = now.scale() / old.scale();
        double tx = now.offsetX() - old.offsetX() * k, ty = now.offsetY() - old.offsetY() * k;
        gc.save(); gc.beginPath(); gc.rect(camera.insetX(), camera.insetY(), camera.width(), camera.height()); gc.clip();
        gc.setImageSmoothing(k != 1 || tx != Math.rint(tx) || ty != Math.rint(ty));
        gc.drawImage(frame.image(), camera.insetX() + tx, camera.insetY() + ty, old.width() * k, old.height() * k);
        gc.restore();
    }

    private static void compare(Canvas a, Canvas b) {
        var first = a.snapshot(null, null).getPixelReader(); var second = b.snapshot(null, null).getPixelReader();
        for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) assertEquals(second.getArgb(x, y), first.getArgb(x, y));
    }
}
