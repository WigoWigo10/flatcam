package org.flatcam.fx;

import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Local, read-only information. OS queries must run outside the JavaFX thread. */
record SystemHardwareInfo(String cpu, int logicalProcessors, long ramTotal, long ramAvailable,
                          long heapUsed, long heapCommitted, long heapMax, int javaThreads) {
    static SystemHardwareInfo unavailable() {
        return new SystemHardwareInfo("não disponível", Runtime.getRuntime().availableProcessors(), -1, -1, -1, -1, -1, -1);
    }
    static SystemHardwareInfo collect() {
        long total = -1, available = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            total = os.getTotalMemorySize(); available = os.getFreeMemorySize();
        }
        var runtime = Runtime.getRuntime();
        return new SystemHardwareInfo(cpuModel(), runtime.availableProcessors(), total, available,
                runtime.totalMemory() - runtime.freeMemory(), runtime.totalMemory(), runtime.maxMemory(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    private static String cpuModel() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.startsWith("windows")) {
                String root = System.getenv("SystemRoot");
                if (root != null) {
                    Path reg = Path.of(root, "System32", "reg.exe");
                    if (Files.isRegularFile(reg)) {
                        Process process = new ProcessBuilder(reg.toString(), "query",
                                "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                                "/v", "ProcessorNameString").redirectErrorStream(true).start();
                        try {
                            if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
                                String output = new String(process.getInputStream().readNBytes(8192), Charset.defaultCharset());
                                String model = registryCpu(output);
                                if (!model.isBlank()) return model;
                            }
                        } finally {
                            if (process.isAlive()) process.destroyForcibly();
                            process.getInputStream().close();
                        }
                    }
                }
            } else if (os.startsWith("linux")) {
                try (var lines = Files.lines(Path.of("/proc/cpuinfo"))) {
                    return lines.limit(512).filter(line -> line.startsWith("model name") || line.startsWith("Hardware"))
                            .map(line -> line.substring(line.indexOf(':') + 1).strip()).findFirst().orElse("não disponível");
                }
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (java.io.IOException | RuntimeException unavailable) { /* Hardware discovery is optional. */ }
        return "não disponível";
    }

    static String registryCpu(String output) {
        return output.lines().filter(line -> line.stripLeading().startsWith("ProcessorNameString"))
                .map(line -> line.split("REG_SZ\\s+", 2)).filter(parts -> parts.length == 2)
                .map(parts -> parts[1].strip()).findFirst().orElse("");
    }

    static String memory(long bytes) {
        return bytes < 0 ? "não disponível" : String.format(Locale.ROOT, "%.2f GiB (%d MiB)",
                bytes / (1024.0 * 1024 * 1024), bytes / (1024 * 1024));
    }

    String summary() {
        return "CPU: " + cpu + "\nProcessadores lógicos disponíveis ao Java: " + logicalProcessors
                + "\nRAM física total: " + memory(ramTotal) + "\nRAM física disponível: " + memory(ramAvailable)
                + "\nHeap Java usado: " + memory(heapUsed) + "\nHeap Java reservado: " + memory(heapCommitted)
                + "\nHeap Java máximo: " + memory(heapMax) + "\nThreads Java (plataforma): "
                + (javaThreads < 0 ? "não disponível" : javaThreads)
                + "\nMemória e threads são uma amostra ao abrir esta janela; heap não é a RAM total.\n";
    }
}
