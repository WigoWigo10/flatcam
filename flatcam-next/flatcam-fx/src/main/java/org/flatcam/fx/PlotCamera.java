package org.flatcam.fx;

import org.locationtech.jts.geom.Envelope;

/** Immutable logical-pixel camera. Ruler insets are not part of world coordinates.
 * Physical framebuffer scaling belongs to presentation, not to CAM coordinates. */
record PlotCamera(double centerX, double centerY, double scale, double width, double height,
                  double insetX, double insetY) {
    PlotCamera {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(scale) || scale <= 0
                || !Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0
                || !Double.isFinite(insetX) || !Double.isFinite(insetY))
            throw new IllegalArgumentException("Invalid plot camera");
    }

    double offsetX() { return insetX + width / 2.0 - centerX * scale; }
    double offsetY() { return insetY + height / 2.0 + centerY * scale; }
    double screenX(double x) { return (x - centerX) * scale + width / 2.0 + insetX; }
    double screenY(double y) { return height / 2.0 - (y - centerY) * scale + insetY; }
    double worldX(double x) { return (x - insetX - width / 2.0) / scale + centerX; }
    double worldY(double y) { return centerY - (y - insetY - height / 2.0) / scale; }

    Envelope visibleBounds() {
        double margin = 2.0 / scale;
        return new Envelope(centerX - width / (2 * scale) - margin, centerX + width / (2 * scale) + margin,
                centerY - height / (2 * scale) - margin, centerY + height / (2 * scale) + margin);
    }
}
