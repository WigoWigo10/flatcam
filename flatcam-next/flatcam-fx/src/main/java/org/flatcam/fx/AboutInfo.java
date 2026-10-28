package org.flatcam.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/** Packaged credits: historical Python contributors are not relabelled as FX contributors. */
final class AboutInfo {
    private AboutInfo() { }

    static String resource(String name) {
        try (var input = AboutInfo.class.getResourceAsStream("about/" + name)) {
            if (input == null) throw new IllegalStateException("Missing About resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read About resource: " + name, failure); }
    }

    static List<List<String>> credits(String name, int columns) {
        return resource(name).lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> {
                    List<String> row = java.util.Arrays.stream(line.split("\t", -1))
                            .map(value -> "-".equals(value) ? "" : value).toList();
                    if (row.size() != columns) throw new IllegalStateException("Invalid credit row: " + line);
                    return row;
                }).toList();
    }

    static Properties build() {
        Properties values = new Properties();
        try (var input = AboutInfo.class.getResourceAsStream("build-info.properties")) {
            if (input != null) values.load(new java.io.InputStreamReader(input,StandardCharsets.UTF_8));
        } catch (IOException failure) { /* Display an honest unavailable value; do not fail the dialog. */ }
        return values;
    }

    static String version() { return build().getProperty("appVersion", I18n.text("ui.d25d2f15d8b7")); }
    static String channel(String version) {
        if(version.toLowerCase(java.util.Locale.ROOT).contains("-beta.")) return I18n.text("release.beta");
        return I18n.text(version.endsWith("-SNAPSHOT") ? "release.development" : "release.published");
    }

    static String technicalSummary() {
        return technicalSummary(null, null);
    }

    static String technicalSummary(SystemHardwareInfo hardware, GraphicsRuntimeInfo graphics) {
        return systemSummary(hardware, graphics)
                + (graphics == null ? "" : I18n.text("ui.56151f0d10e8") + graphics.summary());
    }

    static String systemSummary(SystemHardwareInfo hardware, GraphicsRuntimeInfo graphics) {
        Properties info = build();
        return "FlatCAM FX " + info.getProperty("appVersion", I18n.text("ui.60d46c71d964")) + " — " + channel(version()) + "\n"
                + "Commit: " + info.getProperty("sourceRevision", "development") + "\n"
                + "Build (UTC): " + info.getProperty("buildTimeUtc", I18n.text("ui.d25d2f15d8b7")) + "\n"
                + "Java: " + System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "JavaFX: " + info.getProperty("javafxVersion", System.getProperty("javafx.runtime.version", I18n.text("ui.d25d2f15d8b7"))) + "\n"
                + "Sistema: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
                + "Arquitetura: " + System.getProperty("os.arch") + "\n"
                + "\n" + (graphics == null ? I18n.text("ui.071c586ebaf8") : graphics.friendlySummary())
                + "\n" + (hardware == null ? I18n.text("ui.0ed90f7ac4b8") : hardware.summary())
                + I18n.text("ui.278b80cadca8") + (FlatCamLauncher.diagnosticDirectory() == null ? I18n.text("ui.3cc0e7d1e034")
                        : FlatCamLauncher.diagnosticDirectory()) + I18n.text("ui.9e95a2c16702");
    }
}
