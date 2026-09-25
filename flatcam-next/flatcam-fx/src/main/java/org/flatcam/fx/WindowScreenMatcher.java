package org.flatcam.fx;

import java.util.List;

/** Matches the last JavaFX screen bounds to currently connected screens. */
final class WindowScreenMatcher {

    record Bounds(double x, double y, double width, double height) {
        boolean valid() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(width)
                    && Double.isFinite(height) && width > 0 && height > 0;
        }
    }

    private WindowScreenMatcher() {
    }

    static int bestMatch(List<Bounds> screens, Bounds saved) {
        if (saved == null || !saved.valid()) {
            return -1;
        }
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < screens.size(); i++) {
            Bounds current = screens.get(i);
            if (!current.valid()) {
                continue;
            }
            double score = Math.abs(current.x() - saved.x()) / saved.width()
                    + Math.abs(current.y() - saved.y()) / saved.height()
                    + Math.abs(current.width() - saved.width()) / saved.width()
                    + Math.abs(current.height() - saved.height()) / saved.height();
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
