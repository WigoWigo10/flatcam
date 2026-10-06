package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncFontCatalogTest {
    @Test void enumerationIsLazyQueuedOnceAndSharedAcrossEditors() {
        var queue = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var catalog = new AsyncFontCatalog(() -> {
            calls.incrementAndGet();
            return List.of("SansSerif", "Arial");
        }, queue::add);
        assertEquals(0, calls.get());
        var first = catalog.load();
        assertSame(first, catalog.load());
        assertEquals(0, calls.get(), "enumeration is not performed by the caller");
        assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals(List.of("SansSerif", "Arial"), first.join());
        assertSame(first, catalog.load());
        assertEquals(1, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> first.join().add("another"));
    }

    @Test void failedEnumerationCanBeRetriedWithoutPoisoningTheEditor() {
        var queue = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        var catalog = new AsyncFontCatalog(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("test font discovery failure");
            return List.of("SansSerif");
        }, queue::add);
        var failed = catalog.load();
        queue.remove().run();
        assertTrue(failed.isCompletedExceptionally());
        var retry = catalog.load();
        assertNotSame(failed, retry);
        queue.remove().run();
        assertEquals(List.of("SansSerif"), retry.join());
    }
}
