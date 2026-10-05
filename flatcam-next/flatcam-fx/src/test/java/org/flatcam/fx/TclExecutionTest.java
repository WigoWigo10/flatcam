package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class TclExecutionTest {
    @Test void aCancelledQueuedFxMutationNeverRuns() throws Exception {
        TerminalPanelTest.fx(() -> null);
        CountDownLatch fxBlocked = new CountDownLatch(1), releaseFx = new CountDownLatch(1);
        CountDownLatch queued = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean(), mutated = new AtomicBoolean();
        JobExecutor jobs = new JobExecutor(1);
        Platform.runLater(() -> {
            fxBlocked.countDown();
            try { assertTrue(releaseFx.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
        });
        try {
            assertTrue(fxBlocked.await(10, TimeUnit.SECONDS));
            JobContext context = new JobContext() {
                @Override public void reportProgress(double fraction, String message) { }
                @Override public boolean isCancelled() { queued.countDown(); return cancelled.get(); }
            };
            var job = jobs.submit(ignored -> TclExecution.run(context,
                    () -> TclExecution.onFx(() -> { mutated.set(true); return "published"; })), null);
            assertTrue(queued.await(10, TimeUnit.SECONDS));
            cancelled.set(true);
            assertThrows(CancellationException.class, () -> job.completion().get(10, TimeUnit.SECONDS));
        } finally { releaseFx.countDown(); jobs.shutdown(); }
        TerminalPanelTest.fx(() -> { assertFalse(mutated.get()); return null; });
    }

    @Test void fxFailuresAreUnwrappedAndFxActionsRunOnFx() throws Exception {
        TerminalPanelTest.fx(() -> null);
        assertTrue(TclExecution.onFx(Platform::isFxApplicationThread));
        IllegalArgumentException failure = new IllegalArgumentException("test failure");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> TclExecution.onFx(() -> { throw failure; })));
    }
}
