package org.flatcam.fx;

import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Four first-party themes on JavaFX Modena. CLASSIC retains the pre-refresh
 * white/charcoal interface; ICE uses the newer pale-blue/navy visual system.
 */
public enum ThemeOption {
    CLASSIC_LIGHT("Branco", "vars-classic-light.css", "components-classic.css"),
    CLASSIC_DARK("Preto", "vars-classic-dark.css", "components-classic.css"),
    ICE_LIGHT("Branco gelo", "vars-ice-light.css", "components.css"),
    ICE_DARK("Preto gelo", "vars-ice-dark.css", "components.css");

    private final String label;
    private final String varsResource;
    private final String componentsResource;

    ThemeOption(String label, String varsResource, String componentsResource) {
        this.label = label;
        this.varsResource = varsResource;
        this.componentsResource = componentsResource;
    }

    public String label() {
        return label;
    }

    public boolean isDark() {
        return this == CLASSIC_DARK || this == ICE_DARK;
    }

    static ThemeOption fromSavedName(String name, ThemeOption fallback) {
        if (name == null) return fallback;
        return switch (name) {
            case "CUSTOM_LIGHT" -> ICE_LIGHT;
            case "CUSTOM_DARK" -> ICE_DARK;
            case "ATLANTAFX_LIGHT" -> CLASSIC_LIGHT;
            case "ATLANTAFX_DARK" -> CLASSIC_DARK;
            default -> {
                try {
                    yield valueOf(name);
                } catch (IllegalArgumentException e) {
                    yield fallback;
                }
            }
        };
    }

    public void applyTo(Scene scene) {
        if (!Application.STYLESHEET_MODENA.equals(Application.getUserAgentStylesheet())) {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        }
        scene.getStylesheets().setAll(
                resource(varsResource),
                resource(componentsResource)
        );
    }

    private String resource(String name) {
        return getClass().getResource("theme/" + name).toExternalForm();
    }
}
