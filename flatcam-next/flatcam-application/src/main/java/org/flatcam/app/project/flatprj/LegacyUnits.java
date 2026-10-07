package org.flatcam.app.project.flatprj;

import java.util.Locale;

/** Normalize labels only: coordinates already use the object's current units. */
public final class LegacyUnits {
    private LegacyUnits() { }
    public static String normalize(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "MM", "METRIC" -> "MM";
            case "IN", "INCH" -> "IN";
            default -> throw new IllegalArgumentException("Unidade de projeto Python nao suportada: " + value);
        };
    }
}
