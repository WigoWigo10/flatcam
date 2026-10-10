package org.flatcam.fx;

import java.util.Objects;
import java.util.function.Consumer;

/** Explicit opt-in ablation, never a display preference or optimization. An omitted pass is NOT visual parity.
 * Only the visible benchmark installs this probe. Normal application rendering has no probe. */
final class PlotCncProbe {
    enum Pass { BODY, PASS_LINES, DECORATIONS }
    enum Mode { FULL, OMIT_BODY, OMIT_WIDE_BODY, OMIT_PASS_LINES, OMIT_DECORATIONS, OMIT_CNC }
    record Sample(Pass pass, boolean raster, double lineWidth) { }
    private final Mode mode;
    private final Consumer<Sample> observer;

    PlotCncProbe(Mode mode, Consumer<Sample> observer) {
        this.mode = Objects.requireNonNull(mode);
        this.observer = Objects.requireNonNull(observer);
    }

    boolean includes(Pass pass) {
        return switch (mode) {
            case FULL -> true;
            case OMIT_WIDE_BODY -> true; // Width-dependent body omission is evaluated separately.
            case OMIT_BODY -> pass != Pass.BODY;
            case OMIT_PASS_LINES -> pass != Pass.PASS_LINES;
            case OMIT_DECORATIONS -> pass != Pass.DECORATIONS;
            case OMIT_CNC -> false;
        };
    }

    boolean includesBody(double lineWidth) {
        return includes(Pass.BODY) && (mode != Mode.OMIT_WIDE_BODY || lineWidth <= 2.5);
    }

    void record(Pass pass, boolean raster, double lineWidth) { observer.accept(new Sample(pass, raster, lineWidth)); }
}
