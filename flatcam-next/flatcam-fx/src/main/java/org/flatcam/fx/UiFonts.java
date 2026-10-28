package org.flatcam.fx;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Startup-only font sizes in JavaFX logical pixels; no document/geometry changes. */
final class UiFonts {
    record Settings(int interfaceSize, int textSize, int axisSize, int hudSize) {
        static final Settings DEFAULT = new Settings(12,13,13,11);
        Settings {
            if (interfaceSize<8 || interfaceSize>40 || textSize<8 || textSize>40 || axisSize<0 || axisSize>40 || hudSize<8 || hudSize>40)
                throw new IllegalArgumentException("Font sizes must be 8..40, or 0..40 for axis labels");
        }
    }
    private static volatile Settings active=Settings.DEFAULT;
    private static String stylesheet=encode(active);
    private static boolean initialized;
    private UiFonts() { }
    static Settings settings() { return active; }
    static synchronized void initialize(Settings saved) {
        if (!initialized) { active=Objects.requireNonNull(saved); stylesheet=encode(saved); initialized=true; }
    }
    static String stylesheet() { return stylesheet; }
    static double rowHeight(double normal) { return Math.max(normal,Math.ceil(active.interfaceSize()*1.4+6)); }
    static double rulerTop() { return Math.max(20,active.axisSize()+7); }
    static double rulerLeft() { return Math.max(44,Math.ceil(44*active.axisSize()/13.0)); }
    private static String encode(Settings fonts) {
        // Literal values avoid unresolved CSS lookups in detached RichTextFX measurement cells.
        String css=".root { -fx-font-size: "+fonts.interfaceSize()+"px; }\n"
                +".text-area, .code-editor .code-area, .code-editor .code-area .text, "
                +".code-editor .code-area .lineno, .code-editor .code-area .lineno .code-line-number { -fx-font-size: "+fonts.textSize()+"px; }\n"
                +".plot-coord-label { -fx-font-size: "+fonts.hudSize()+"px; }\n";
        return "data:text/css;base64,"+Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
