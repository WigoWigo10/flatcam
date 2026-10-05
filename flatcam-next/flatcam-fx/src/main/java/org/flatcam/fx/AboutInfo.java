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
            if (input != null) values.load(input);
        } catch (IOException failure) { /* Display an honest unavailable value; do not fail the dialog. */ }
        return values;
    }

    static String technicalSummary() {
        return technicalSummary(null, null);
    }

    static String technicalSummary(SystemHardwareInfo hardware, GraphicsRuntimeInfo graphics) {
        Properties info = build();
        return "FlatCAM FX " + info.getProperty("appVersion", "versão não disponível") + " — em desenvolvimento\n"
                + "Build (UTC): " + info.getProperty("buildTimeUtc", "não disponível") + "\n"
                + "Java: " + System.getProperty("java.runtime.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "JavaFX: " + info.getProperty("javafxVersion", System.getProperty("javafx.runtime.version", "não disponível")) + "\n"
                + "Sistema: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
                + "Arquitetura: " + System.getProperty("os.arch") + "\n"
                + "\n" + (graphics == null ? "Renderização JavaFX: consultando pipeline ativo…\n" : graphics.summary())
                + "\n" + (hardware == null ? "Hardware: consultando CPU e memória…\n" : hardware.summary())
                + "\n"
                + "Diagnósticos: " + (FlatCamLauncher.diagnosticDirectory() == null ? "indisponíveis nesta execução"
                        : FlatCamLauncher.diagnosticDirectory()) + "\n"
                + "Referência do legado: FlatCAM Python 8.994 BETA (2020/11/7).\n"
                + "A versão FX tem implementação independente; não representa paridade completa com o legado.";
    }
}
