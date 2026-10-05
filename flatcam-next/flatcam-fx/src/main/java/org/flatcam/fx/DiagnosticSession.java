package org.flatcam.fx;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.json.JSONObject;

/** Local, best-effort diagnostics. Failures here must not prevent running CAM or recursively log. */
final class DiagnosticSession implements AutoCloseable {
    static final long JFR_MAX_BYTES = 64L * 1024 * 1024;
    static final int MAX_INCIDENTS = 5;
    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Handler appLog;
    private final Handler consoleLog;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final PrintStream teeOut;
    private final PrintStream teeErr;
    private final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
    private final Thread.UncaughtExceptionHandler handler = this::uncaught;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger incidents = new AtomicInteger();
    private final ThreadPoolExecutor writer;
    private final ScheduledExecutorService timer;
    private final Thread shutdownHook;
    private Recording recording;
    private UiWatchdog watchdog;
    private volatile String state = "running";

    static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("flatcam.diagnostics.enabled",
                System.getenv().getOrDefault("FLATCAM_FX_DIAGNOSTICS", "true")));
    }

    static DiagnosticSession start() {
        if (!enabled()) return null;
        try {
            String supplied = System.getProperty("flatcam.diagnostics.session");
            Path session;
            if (supplied != null && !supplied.isBlank()) {
                session = Path.of(supplied).toAbsolutePath().normalize();
            } else {
                String configured = System.getenv("FLATCAM_FX_DIAGNOSTICS_DIR");
                String local = System.getenv("LOCALAPPDATA");
                Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                        : local != null && !local.isBlank() ? Path.of(local, "FlatCAMFX", "diagnostics")
                        : Path.of(System.getProperty("user.home"), ".flatcam-fx", "diagnostics");
                Files.createDirectories(root);
                String id = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
                        .format(Instant.now()) + "-" + ProcessHandle.current().pid() + "-" + UUID.randomUUID().toString().substring(0, 8);
                session = Files.createDirectory(root.resolve("session-" + id)).toAbsolutePath().normalize();
            }
            return new DiagnosticSession(session, true);
        } catch (Exception failure) {
            System.err.println("[DIAGNOSTICS] unavailable: " + failure);
            return null;
        }
    }

    DiagnosticSession(Path directory, boolean enableJfr) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        // Do not let another process overwrite the same explicitly supplied session.
        lockChannel = FileChannel.open(this.directory.resolve("session.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException failure) { lockChannel.close(); throw failure; }
        if (acquired == null) { lockChannel.close(); throw new IOException("Diagnostic session already in use"); }
        lock = acquired;
        Handler first = null;
        Handler second = null;
        try {
            first = fileLog("app-%g.log", new SimpleFormatter());
            second = fileLog("console-%g.log", new Formatter() {
                @Override public String format(LogRecord record) { return record.getMessage(); }
            });
        } catch (IOException | RuntimeException failure) {
            if (first != null) first.close();
            if (second != null) second.close();
            lock.release(); lockChannel.close(); throw failure;
        }
        appLog = first; consoleLog = second;
        teeOut = new PrintStream(new TeeOutput(originalOut, consoleLog), true, StandardCharsets.UTF_8);
        teeErr = new PrintStream(new TeeOutput(originalErr, consoleLog), true, StandardCharsets.UTF_8);
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), task -> {
            Thread thread = new Thread(task, "flatcam-diagnostics-writer"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.DiscardPolicy());
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "flatcam-diagnostics-timer"); thread.setDaemon(true); return thread;
        });
        shutdownHook = new Thread(this::close, "flatcam-diagnostics-shutdown");
        System.setOut(teeOut); System.setErr(teeErr);
        Logger.getLogger("").addHandler(appLog);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        if (enableJfr) startRecording();
        configureHeapDump();
        bestEffort(() -> snapshot("system-start.json"));
        timer.scheduleWithFixedDelay(() -> writer.execute(() -> {
            bestEffort(() -> snapshot("system-latest.json"));
            checkpoint();
        }), 60, 60, TimeUnit.SECONDS);
        System.out.println("[DIAGNOSTICS] session=" + this.directory);
    }

    Path directory() { return directory; }

    private Handler fileLog(String pattern, Formatter formatter) throws IOException {
        String logPattern = directory.toString().replace("%", "%%") + java.io.File.separator + pattern;
        FileHandler log = new FileHandler(logPattern, 2 * 1024 * 1024, 4, true);
        log.setEncoding("UTF-8"); log.setFormatter(formatter); log.setLevel(Level.ALL); return log;
    }

    private void startRecording() {
        try {
            recording = new Recording(Configuration.getConfiguration("default"));
            recording.setName("FlatCAM-FX-diagnostics");
            recording.setToDisk(true); recording.setMaxSize(JFR_MAX_BYTES); recording.setMaxAge(Duration.ofMinutes(10));
            // Do not deliberately record all environment variables, JVM properties or every thrown exception.
            recording.disable("jdk.InitialEnvironmentVariable"); recording.disable("jdk.InitialSystemProperty");
            recording.disable("jdk.JavaExceptionThrow");
            recording.setDestination(directory.resolve("session.jfr")); recording.setDumpOnExit(true);
            recording.start();
        } catch (Exception | LinkageError unavailable) {
            if (recording != null) recording.close(); recording = null;
            originalErr.println("[DIAGNOSTICS] JFR unavailable: " + unavailable);
        }
    }

    private void configureHeapDump() {
        if (!Boolean.getBoolean("flatcam.diagnostics.heapDump")) return;
        bestEffort(() -> {
            var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            bean.setVMOption("HeapDumpPath", directory.resolve("heap.hprof").toString());
            bean.setVMOption("HeapDumpOnOutOfMemoryError", "true");
        });
    }

    synchronized void monitorUi(java.util.function.Consumer<Runnable> submit) {
        if (closed.get() || watchdog != null) return;
        watchdog = new UiWatchdog(submit, () -> capture("ui-unresponsive"), System::nanoTime, TimeUnit.SECONDS.toNanos(5));
        watchdog.start();
    }

    synchronized void stopUiMonitor() { if (watchdog != null) watchdog.close(); }

    void launchFailed(Throwable failure) {
        state = "launch-failed";
        Logger.getLogger(DiagnosticSession.class.getName()).log(Level.SEVERE, "Application launch failed", failure);
        capture("launch-failed");
    }

    private void uncaught(Thread thread, Throwable failure) {
        try {
            Logger.getLogger(DiagnosticSession.class.getName()).log(Level.SEVERE,
                    "Uncaught exception on " + thread.getName(), failure);
            capture("uncaught-exception");
        } catch (Throwable diagnosticFailure) {
            originalErr.println("[DIAGNOSTICS] could not capture uncaught exception");
        } finally {
            if (previousHandler != null) previousHandler.uncaughtException(thread, failure);
            else failure.printStackTrace(System.err);
        }
    }

    boolean capture(String reason) {
        if (closed.get()) return false;
        int number = incidents.incrementAndGet();
        if (number > MAX_INCIDENTS) return false;
        writer.execute(() -> {
            bestEffort(() -> {
                snapshot("incident-" + number + "-system.json");
                StringBuilder text = new StringBuilder("Reason: " + reason + "\nTime: " + Instant.now() + "\n");
                var bean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] threads = bean.dumpAllThreads(bean.isObjectMonitorUsageSupported(),
                        bean.isSynchronizerUsageSupported(), 64);
                int count = 0;
                for (ThreadInfo thread : threads) {
                    if (++count > 256) { text.append("\nRemaining platform threads omitted.\n"); break; }
                    text.append('\n').append(thread.getThreadName()).append(" #").append(thread.getThreadId())
                            .append(' ').append(thread.getThreadState()).append(" lock=").append(thread.getLockName())
                            .append(" owner=").append(thread.getLockOwnerName()).append('\n');
                    for (StackTraceElement frame : thread.getStackTrace()) text.append("    at ").append(frame).append('\n');
                    for (var monitor : thread.getLockedMonitors()) text.append("    monitor ").append(monitor).append('\n');
                    for (var synchronizer : thread.getLockedSynchronizers()) text.append("    synchronizer ").append(synchronizer).append('\n');
                }
                Files.writeString(directory.resolve("incident-" + number + "-threads.txt"), text, StandardCharsets.UTF_8);
            });
            checkpoint();
        });
        return true;
    }

    private void snapshot(String name) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        JSONObject info = new JSONObject().put("time", Instant.now().toString()).put("state", state)
                .put("pid", ProcessHandle.current().pid()).put("java", System.getProperty("java.runtime.version"))
                .put("javaVendor", System.getProperty("java.vendor")).put("os", System.getProperty("os.name"))
                .put("osVersion", System.getProperty("os.version")).put("architecture", System.getProperty("os.arch"))
                .put("logicalProcessors", runtime.availableProcessors()).put("heapMaxBytes", runtime.maxMemory())
                .put("heapCommittedBytes", runtime.totalMemory()).put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory())
                .put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime())
                .put("platformThreads", ManagementFactory.getThreadMXBean().getThreadCount())
                .put("requestedPrismOrder", System.getProperty("prism.order", "platform-default"));
        var version = new java.util.Properties();
        try (var input = DiagnosticSession.class.getResourceAsStream("build-info.properties")) {
            if (input != null) version.load(input);
        }
        for (String key : version.stringPropertyNames()) info.put(key, version.getProperty(key));
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            info.put("physicalMemoryBytes", extended.getTotalMemorySize()).put("freePhysicalMemoryBytes", extended.getFreeMemorySize())
                    .put("processCpuTimeNs", extended.getProcessCpuTime()).put("processCpuLoad", extended.getProcessCpuLoad())
                    .put("systemCpuLoad", extended.getCpuLoad());
        }
        var hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        if (hotspot != null) info.put("fatalErrorFile", hotspot.getVMOption("ErrorFile").getValue())
                .put("heapDumpEnabled", hotspot.getVMOption("HeapDumpOnOutOfMemoryError").getValue());
        info.put("jfrEnabled", recording != null);
        Path temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, info.toString(2), StandardCharsets.UTF_8);
        replace(temporary, directory.resolve(name));
    }

    private synchronized void checkpoint() {
        if (recording == null) return;
        bestEffort(() -> {
            Path temporary = directory.resolve("recent.jfr.tmp"); recording.dump(temporary);
            replace(temporary, directory.resolve("recent.jfr"));
        });
    }

    private static void replace(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private interface CheckedAction { void run() throws Exception; }
    private void bestEffort(CheckedAction action) {
        try { action.run(); }
        catch (Exception failure) { originalErr.println("[DIAGNOSTICS] capture unavailable: " + failure); }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        stopUiMonitor(); timer.shutdownNow(); writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                // Do not close files underneath a still-running diagnostic capture.
                originalErr.println("[DIAGNOSTICS] writer still busy; close deferred");
                deferClose(); return;
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); deferClose(); return; }
        finishClose();
    }

    private void deferClose() {
        Thread cleanup = new Thread(() -> {
            try { while (!writer.awaitTermination(1, TimeUnit.SECONDS)) { /* bounded queue drains */ } }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            finishClose();
        }, "flatcam-diagnostics-cleanup"); cleanup.setDaemon(true); cleanup.start();
    }

    private synchronized void finishClose() {
        if (!"launch-failed".equals(state)) state = "closed";
        bestEffort(() -> snapshot("system-final.json"));
        if (recording != null) {
            bestEffort(() -> {
                try { if (recording.getState() == jdk.jfr.RecordingState.RUNNING) recording.stop(); }
                finally { recording.close(); }
            });
            recording = null;
        }
        teeOut.flush(); teeErr.flush();
        if (System.out == teeOut) System.setOut(originalOut);
        if (System.err == teeErr) System.setErr(originalErr);
        if (Thread.getDefaultUncaughtExceptionHandler() == handler) Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        Logger.getLogger("").removeHandler(appLog); appLog.close(); consoleLog.close();
        bestEffort(() -> { lock.release(); lockChannel.close(); });
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException shutdownInProgress) { /* hook is already running */ }
    }

    /** Preserve terminal output; keep at most 8 KiB of a partial UTF-8 line in memory. */
    private static final class TeeOutput extends OutputStream {
        private final PrintStream terminal;
        private final Handler log;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        TeeOutput(PrintStream terminal, Handler log) { this.terminal = terminal; this.log = log; }
        @Override public synchronized void write(int value) { write(new byte[]{(byte) value}, 0, 1); }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            terminal.write(bytes, offset, length);
            for (int index = offset; index < offset + length; index++) {
                line.write(bytes[index]);
                if (bytes[index] == '\n' || line.size() >= 8192) publish();
            }
        }
        private void publish() {
            if (line.size() == 0) return;
            log.publish(new LogRecord(Level.INFO, line.toString(StandardCharsets.UTF_8))); line.reset(); log.flush();
        }
        @Override public synchronized void flush() { terminal.flush(); publish(); }
        // Never close the original terminal streams.
        @Override public synchronized void close() { flush(); }
    }
}
