package org.flatcam.fx;

/** Test-only JNI entry: same benchmark/assertions as Surefire, with an Optimus-capable process host. */
public final class PlotBenchmarkNativeEntry {
    private static native long counterNanos();
    private static native long counterFrequency();
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("flatcam.plot.benchmark")) throw new IllegalStateException("Benchmark opt-in is required");
        long bestSpan = Long.MAX_VALUE, bestOffset = 0;
        for (int sample = 0; sample < 8; sample++) {
            long before = System.nanoTime(), counter = counterNanos(), after = System.nanoTime();
            if (after - before < bestSpan) { bestSpan = after - before; bestOffset = counter - (before + (after - before) / 2); }
        }
        System.setProperty("flatcam.plot.benchmark.qpcMinusNanoTime", Long.toString(bestOffset));
        long frequency = counterFrequency();
        long uncertainty = Math.max(bestSpan / 2, (long) Math.ceil(1e9 / frequency));
        System.setProperty("flatcam.plot.benchmark.qpcUncertainty", Long.toString(uncertainty));
        System.setProperty("flatcam.plot.benchmark.qpcFrequency", Long.toString(counterFrequency()));
        String gate = System.getProperty("flatcam.plot.benchmark.captureGate");
        if (gate != null) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while (!java.nio.file.Files.isRegularFile(java.nio.file.Path.of(gate))) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("External frame capture did not release the benchmark gate");
                Thread.sleep(20);
            }
        }
        try { new MainPlotRenderBenchmarkTest().runVisibleBenchmark(); }
        finally { javafx.application.Platform.exit(); }
    }
}
