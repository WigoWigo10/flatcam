package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordingFile;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticSessionTest {
    @TempDir Path temporary;

    @Test void capturesUtf8OutputAndSystemLoggerAndRestoresGlobalState() throws Exception {
        var out = System.out; var err = System.err; var handler = Thread.getDefaultUncaughtExceptionHandler();
        try (var session = new DiagnosticSession(temporary.resolve("sessão % teste"), false)) {
            System.out.println("diagnostic stdout informações"); System.err.println("diagnostic stderr ação");
            System.getLogger("flatcam-diagnostic-test").log(System.Logger.Level.INFO, "diagnostic JUL bridge");
        }
        assertSame(out, System.out); assertSame(err, System.err); assertSame(handler, Thread.getDefaultUncaughtExceptionHandler());
        String console = readLogs(temporary.resolve("sessão % teste"), "console-");
        assertTrue(console.contains("stdout informações")); assertTrue(console.contains("stderr ação"));
        assertTrue(readLogs(temporary.resolve("sessão % teste"), "app-").contains("diagnostic JUL bridge"));
        var info = new JSONObject(Files.readString(temporary.resolve("sessão % teste/system-final.json")));
        assertEquals("closed", info.getString("state")); assertTrue(info.getInt("logicalProcessors") >= 1);
        assertTrue(info.getLong("heapMaxBytes") > 0); assertTrue(info.has("javafxVersion"));
        assertFalse(info.has("environment")); assertFalse(info.has("user.home"));
    }

    @Test void uncaughtThreadExceptionIsLoggedAndPreviousHandlerStillRuns() throws Exception {
        var original = Thread.getDefaultUncaughtExceptionHandler();
        var forwarded = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> forwarded.set(failure));
        try {
            IllegalStateException failure = new IllegalStateException("intentional diagnostic test");
            try (var session = new DiagnosticSession(temporary, false)) {
                Thread worker = new Thread(() -> { throw failure; }, "diagnostic-test-worker");
                worker.start(); worker.join(3000); assertFalse(worker.isAlive());
            }
            assertSame(failure, forwarded.get());
            assertTrue(readLogs(temporary, "app-").contains("intentional diagnostic test"));
            assertTrue(Files.readString(temporary.resolve("incident-1-threads.txt")).contains("uncaught-exception"));
        } finally { Thread.setDefaultUncaughtExceptionHandler(original); }
    }

    @Test void incidentCaptureIsCappedAndCloseIsIdempotent() throws Exception {
        var session = new DiagnosticSession(temporary, false);
        for (int index = 0; index < 50; index++) session.capture("test");
        session.close(); session.close(); session.capture("after-close");
        try (var files = Files.list(temporary)) {
            long dumps = files.filter(path -> path.toString().endsWith("-threads.txt")).count();
            assertTrue(dumps >= 1); assertTrue(dumps <= DiagnosticSession.MAX_INCIDENTS);
        }
        assertFalse(Files.exists(temporary.resolve("incident-6-threads.txt")));
    }

    @Test void aSessionCannotBeOpenedTwiceOrDamageTheFirstHandlers() throws Exception {
        try (var first = new DiagnosticSession(temporary, false)) {
            var out = System.out;
            assertThrows(java.nio.channels.OverlappingFileLockException.class, () -> new DiagnosticSession(temporary, false));
            assertSame(out, System.out);
            System.out.println("first session remains functional");
        }
        assertTrue(readLogs(temporary, "console-").contains("remains functional"));
        // All locks/handlers are released on normal close.
        try (var reopened = new DiagnosticSession(temporary, false)) { assertEquals(temporary, reopened.directory()); }
    }

    @Test void invalidDirectoryDoesNotInstallHandlers() throws Exception {
        Path file = temporary.resolve("file"); Files.writeString(file, "owned test fixture");
        var out = System.out; var handler = Thread.getDefaultUncaughtExceptionHandler();
        assertThrows(java.io.IOException.class, () -> new DiagnosticSession(file, false));
        assertSame(out, System.out); assertSame(handler, Thread.getDefaultUncaughtExceptionHandler());
    }

    @Name("flatcam.TestDiagnosticEvent")
    static class TestEvent extends Event { }

    @Test void jfrIsBoundedReadableAndClosedWithoutRecordingSensitiveInitialEvents() throws Exception {
        Recording recording;
        try (var session = new DiagnosticSession(temporary, true)) {
            Field field = DiagnosticSession.class.getDeclaredField("recording"); field.setAccessible(true);
            recording = (Recording) field.get(session); assertNotNull(recording);
            assertEquals(DiagnosticSession.JFR_MAX_BYTES, recording.getMaxSize());
            assertEquals(java.time.Duration.ofMinutes(10), recording.getMaxAge());
            assertEquals("false", recording.getSettings().get("jdk.InitialEnvironmentVariable#enabled"));
            assertEquals("false", recording.getSettings().get("jdk.InitialSystemProperty#enabled"));
            recording.enable(TestEvent.class); new TestEvent().commit(); session.capture("jfr-test");
        }
        assertEquals(RecordingState.CLOSED, recording.getState());
        assertTrue(RecordingFile.readAllEvents(temporary.resolve("session.jfr")).stream()
                .anyMatch(event -> event.getEventType().getName().equals("flatcam.TestDiagnosticEvent")));
        assertTrue(Files.size(temporary.resolve("recent.jfr")) > 0);
    }

    @Test void logFilesRotateInsteadOfGrowingWithoutBound() throws Exception {
        try (var session = new DiagnosticSession(temporary, false)) {
            // Do not fill the user's terminal; exercise the actual rotating handler directly.
            Field field = DiagnosticSession.class.getDeclaredField("consoleLog"); field.setAccessible(true);
            var log = (java.util.logging.Handler) field.get(session);
            String block = "x".repeat(8192);
            for (int index = 0; index < 1200; index++) log.publish(new java.util.logging.LogRecord(java.util.logging.Level.INFO, block));
        }
        try (var files = Files.list(temporary)) {
            var logs = files.filter(path -> path.getFileName().toString().startsWith("console-")).toList();
            assertEquals(4, logs.size());
            for (var path : logs) assertTrue(Files.size(path) <= 2 * 1024 * 1024 + 8192);
        }
    }

    @Test void launchFailureIsRecordedInFinalStatus() throws Exception {
        try (var session = new DiagnosticSession(temporary, false)) {
            session.launchFailed(new IllegalStateException("intentional launch failure"));
        }
        assertEquals("launch-failed", new JSONObject(Files.readString(temporary.resolve("system-final.json"))).getString("state"));
    }

    @Test void diagnosticsCanBeDisabledWithoutTouchingTheFilesystem() {
        String previous = System.getProperty("flatcam.diagnostics.enabled");
        try {
            System.setProperty("flatcam.diagnostics.enabled", "false");
            assertNull(DiagnosticSession.start());
            try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
        } finally {
            if (previous == null) System.clearProperty("flatcam.diagnostics.enabled");
            else System.setProperty("flatcam.diagnostics.enabled", previous);
        }
    }

    /** Separate JVM: tests shutdown hooks without terminating the test runner or the user's app. */
    public static class ExitProbe {
        public static void main(String[] args) {
            DiagnosticSession session = DiagnosticSession.start();
            if (session == null) throw new IllegalStateException("missing diagnostic session");
            System.out.println("child shutdown probe informações");
            System.exit(0);
        }
    }

    @Test void shutdownHookSavesAReadableRecordingInAnIsolatedJvm() throws Exception {
        Path session = temporary.resolve("child session");
        Path output = temporary.resolve("child-output.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Dflatcam.diagnostics.enabled=true",
                "-Dflatcam.diagnostics.session=" + session,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ExitProbe.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(output); }
                catch (Exception failure) { return failure.toString(); }
            });
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
        assertEquals("closed", new JSONObject(Files.readString(session.resolve("system-final.json"))).getString("state"));
        assertFalse(RecordingFile.readAllEvents(session.resolve("session.jfr")).isEmpty());
        assertTrue(readLogs(session, "console-").contains("child shutdown probe informações"));
    }

    private static String readLogs(Path directory, String prefix) throws Exception {
        StringBuilder text = new StringBuilder();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith(prefix)
                    && path.toString().endsWith(".log")).toList()) text.append(Files.readString(file));
        }
        return text.toString();
    }
}
