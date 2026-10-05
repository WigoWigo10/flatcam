package org.flatcam.fx;

import java.util.Arrays;
import javafx.application.Application;

/** Plain Java entry point: diagnostics must start before JavaFX startup, not inside Application.start. */
public final class FlatCamLauncher {
    private static DiagnosticSession diagnostics;
    private FlatCamLauncher() { }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--no-diagnostics")) System.setProperty("flatcam.diagnostics.enabled", "false");
        diagnostics = DiagnosticSession.start();
        try {
            if (Arrays.asList(args).contains("--diagnostics-probe")) {
                if (diagnostics == null) throw new IllegalStateException("Diagnostics are unavailable");
                System.out.println("FlatCAM FX diagnostics probe: log UTF-8 — informações.");
                Thread probe = new Thread(() -> { throw new IllegalStateException("intentional diagnostics probe"); }, "diagnostics-probe");
                probe.start(); probe.join(); diagnostics.capture("probe");
            } else {
                Application.launch(MainApp.class, args);
            }
        } catch (Throwable failure) {
            if (diagnostics != null) diagnostics.launchFailed(failure);
            throw failure;
        } finally {
            if (diagnostics != null) diagnostics.close();
        }
    }

    static void monitorUi() { if (diagnostics != null) diagnostics.monitorUi(javafx.application.Platform::runLater); }
    static void stopUiMonitor() { if (diagnostics != null) diagnostics.stopUiMonitor(); }
    static java.nio.file.Path diagnosticDirectory() { return diagnostics == null ? null : diagnostics.directory(); }
    static boolean captureNow() { return diagnostics != null && diagnostics.capture("manual"); }
    static void openDiagnosticDirectory(java.util.function.Consumer<String> feedback) {
        java.nio.file.Path directory = diagnosticDirectory();
        if (directory == null) return;
        Thread open = new Thread(() -> {
            String message;
            try {
                if (!java.awt.Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Desktop unavailable");
                java.awt.Desktop.getDesktop().open(directory.toFile());
                message = "Diagnosticos: " + directory;
            } catch (Exception failure) {
                message = "Abra a pasta de diagnosticos manualmente: " + directory + " (" + failure + ")";
            }
            String result = message;
            javafx.application.Platform.runLater(() -> feedback.accept(result));
        }, "flatcam-open-diagnostics");
        open.setDaemon(true); open.start();
    }
}
