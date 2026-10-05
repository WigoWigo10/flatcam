package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class DiagnosticsFxTest {
    @TempDir Path temporary;

    @Test void anActualFxCallbackExceptionReachesTheDiagnosticHandlerAndFxKeepsRunning() throws Exception {
        TerminalPanelTest.fx(() -> null);
        var previous = Thread.getDefaultUncaughtExceptionHandler();
        var forwarded = new AtomicReference<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> forwarded.set(error));
        try {
            try (var diagnostics = new DiagnosticSession(temporary, false)) {
                Platform.runLater(() -> { throw new IllegalStateException("intentional FX callback failure"); });
                assertTrue(TerminalPanelTest.fx(() -> Platform.isFxApplicationThread()));
            }
            assertNotNull(forwarded.get()); assertEquals("intentional FX callback failure", forwarded.get().getMessage());
            assertTrue(Files.readString(temporary.resolve("app-0.log")).contains("JavaFX Application Thread"));
            assertTrue(Files.exists(temporary.resolve("incident-1-threads.txt")));
        } finally { Thread.setDefaultUncaughtExceptionHandler(previous); }
    }

    @Test void aRealBlockedFxThreadIsReportedByTheBackgroundWatchdog() throws Exception {
        TerminalPanelTest.fx(() -> null);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var diagnostics = new DiagnosticSession(temporary, false)) {
            diagnostics.monitorUi(Platform::runLater);
            Platform.runLater(() -> {
                blocked.countDown();
                try { release.await(20, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                Path report = temporary.resolve("incident-1-threads.txt");
                while (!Files.exists(report) && System.nanoTime() < deadline) Thread.sleep(50);
                assertTrue(Files.exists(report), "diagnostic writer must run while FX is unavailable");
            } finally { release.countDown(); }
            assertTrue(TerminalPanelTest.fx(() -> Platform.isFxApplicationThread()));
        } finally { release.countDown(); }
        String dump = Files.readString(temporary.resolve("incident-1-threads.txt"));
        assertTrue(dump.contains("ui-unresponsive")); assertTrue(dump.contains("JavaFX Application Thread"));
        assertTrue(dump.contains("CountDownLatch.await"));
    }
}
