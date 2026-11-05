package org.flatcam.fx;

import java.util.Locale;
import org.flatcam.cam.cutout.InternalCutoutGenerator;

final class InternalCutoutMessages {
    private InternalCutoutMessages() { }
    static String error(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        if (error instanceof InternalCutoutGenerator.InvalidOpening invalid) {
            String message = I18n.text("cutout.internal.error." + invalid.problem().name().toLowerCase(Locale.ROOT));
            return invalid.opening() > 0 ? message.formatted(invalid.opening()) : message;
        }
        return I18n.text("cutout.internal.failed").formatted(String.valueOf(error.getMessage()));
    }
}
