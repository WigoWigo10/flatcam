package org.flatcam.fx;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Two theme families, each with a light and a dark variant, kept side by
 * side on purpose (CONTEXTO_FLATCAM_FX.md, secao 6 - "temas claro e
 * escuro"):
 *
 * <ul>
 *   <li>{@code CUSTOM_*} - plain JavaFX (Modena) with a small hand-written
 *       palette, same spirit as UGS-FX's own root.css: no third-party theme
 *       library, just -fx-base/-fx-accent plus our own -fc-* variables.</li>
 *   <li>{@code ATLANTAFX_*} - AtlantaFX's Primer theme supplies the full
 *       user-agent stylesheet (replaces Modena outright); our -fc-* palette
 *       only covers the panes AtlantaFX doesn't know about (viewport
 *       placeholder, side/bottom panels).</li>
 * </ul>
 *
 * Either way, structure lives in theme/components.css and only the palette
 * (theme/vars-*.css) changes - see that file for the -fc-* properties both
 * families must define.
 */
public enum ThemeOption {
    CUSTOM_LIGHT("CSS puro - Claro", null, "vars-custom-light.css"),
    CUSTOM_DARK("CSS puro - Escuro", null, "vars-custom-dark.css"),
    ATLANTAFX_LIGHT("AtlantaFX - Claro (Primer)", new PrimerLight(), "vars-atlantafx-light.css"),
    ATLANTAFX_DARK("AtlantaFX - Escuro (Primer)", new PrimerDark(), "vars-atlantafx-dark.css");

    private final String label;
    private final atlantafx.base.theme.Theme atlantaFxTheme;
    private final String varsResource;

    ThemeOption(String label, atlantafx.base.theme.Theme atlantaFxTheme, String varsResource) {
        this.label = label;
        this.atlantaFxTheme = atlantaFxTheme;
        this.varsResource = varsResource;
    }

    public String label() {
        return label;
    }

    public void applyTo(Scene scene) {
        Application.setUserAgentStylesheet(atlantaFxTheme != null ? atlantaFxTheme.getUserAgentStylesheet() : null);
        scene.getStylesheets().setAll(
                resource(varsResource),
                resource("components.css")
        );
    }

    private String resource(String name) {
        return getClass().getResource("theme/" + name).toExternalForm();
    }
}
