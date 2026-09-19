package org.flatcam.fx;

import javafx.scene.Scene;

/**
 * The two themes required by CONTEXTO_FLATCAM_FX.md, secao 6 ("temas claro e
 * escuro"). Deliberately just a stylesheet swap for now - no persisted user
 * preference yet (that belongs to the preferences system, not built here).
 */
public enum Theme {
    LIGHT("/org/flatcam/fx/theme-light.css"),
    DARK("/org/flatcam/fx/theme-dark.css");

    private final String stylesheetResource;

    Theme(String stylesheetResource) {
        this.stylesheetResource = stylesheetResource;
    }

    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(getClass().getResource(stylesheetResource).toExternalForm());
    }
}
