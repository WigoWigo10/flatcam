package org.flatcam.fx;

import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/** One application policy, also applied to virtualized native cell tooltips. */
final class TooltipTiming {
    static final Duration INITIAL = Duration.seconds(1);
    static final Duration RESHOW = Duration.millis(300);
    private static final long WARM_WINDOW_NANOS = 700_000_000L;

    private TooltipTiming() { }

    static Duration delay(boolean alreadyOpen, long sinceLastCloseNanos) {
        return alreadyOpen || sinceLastCloseNanos >= 0 && sinceLastCloseNanos < WARM_WINDOW_NANOS
                ? RESHOW : INITIAL;
    }

    static void configureNative(Tooltip tooltip) {
        tooltip.setShowDelay(INITIAL);
        // Keep the authored reading duration and native hide/reshow behaviour.
    }
}
