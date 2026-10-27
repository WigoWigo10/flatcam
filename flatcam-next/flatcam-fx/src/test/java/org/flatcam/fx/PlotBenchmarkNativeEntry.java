package org.flatcam.fx;

/** Test-only JNI entry: same benchmark/assertions as Surefire, with an Optimus-capable process host. */
public final class PlotBenchmarkNativeEntry {
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("flatcam.plot.benchmark")) throw new IllegalStateException("Benchmark opt-in is required");
        try { new MainPlotRenderBenchmarkTest().runVisibleBenchmark(); }
        finally { javafx.application.Platform.exit(); }
    }
}
