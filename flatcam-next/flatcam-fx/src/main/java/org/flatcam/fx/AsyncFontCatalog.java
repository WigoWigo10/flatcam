package org.flatcam.fx;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.flatcam.cam.geometry.TextGeometry;

/** One lazy, shared font enumeration; never enumerate Windows fonts on the FX thread. */
final class AsyncFontCatalog {
    static final AsyncFontCatalog SYSTEM = new AsyncFontCatalog(TextGeometry::fontFamilies,
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "flatcam-font-catalog");
                thread.setDaemon(true);
                return thread;
            }));

    private final Supplier<List<String>> loader;
    private final Executor worker;
    private CompletableFuture<List<String>> pending;

    AsyncFontCatalog(Supplier<List<String>> loader, Executor worker) {
        this.loader = loader;
        this.worker = worker;
    }

    synchronized CompletableFuture<List<String>> load() {
        if (pending == null || pending.isCompletedExceptionally()) {
            pending = CompletableFuture.supplyAsync(() -> {
                long start = System.nanoTime();
                var fonts = List.copyOf(loader.get());
                if (Boolean.getBoolean(PlotAreaPerformance.ENABLED_PROPERTY)) {
                    System.err.printf(java.util.Locale.ROOT,
                            "[PLOT-PROFILE] font enumeration (worker)=%.1fms families=%d%n",
                            (System.nanoTime() - start) / 1_000_000.0, fonts.size());
                }
                return fonts;
            }, worker);
        }
        return pending;
    }
}
