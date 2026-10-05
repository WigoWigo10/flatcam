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

    /** Colours of a tooltip: background, border and text (hex), one set per theme. */
    record TooltipPalette(String background, String border, String text) {
    }

    /**
     * Tooltips live in a popup scene of their own, which does not see the theme's stylesheet, so each theme spells its
     * colours out. Each set keeps the theme's own character: a neutral grey for the classic themes (lighter than the
     * panels so the tip stands out) and the blue-grey of the ice themes.
     */
    TooltipPalette tooltipPalette() {
        return switch (this) {
            case CLASSIC_DARK -> new TooltipPalette("#3d3d3d", "#6a6a6a", "#e8e8e8");
            case CLASSIC_LIGHT -> new TooltipPalette("#ffffff", "#b3b3b3", "#202020");
            case ICE_DARK -> new TooltipPalette("#263746", "#607383", "#f7fafc");
            case ICE_LIGHT -> new TooltipPalette("#ffffff", "#aabac7", "#172b3c");
        };
    }

    /** The tooltip look as an inline style (also used by the native tooltips of the project tree). */
    String objectTooltipStyle() {
        TooltipPalette palette = tooltipPalette();
        return "-fx-background-color: " + palette.background() + "; -fx-text-fill: " + palette.text() + "; "
                + "-fx-border-color: " + palette.border() + "; -fx-border-radius: 5; "
                + "-fx-background-radius: 5; -fx-padding: 5 8 5 8;";
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
                resource(componentsResource), resource("code-editor.css")
        );
    }

    private String resource(String name) {
        return getClass().getResource("theme/" + name).toExternalForm();
    }
}
