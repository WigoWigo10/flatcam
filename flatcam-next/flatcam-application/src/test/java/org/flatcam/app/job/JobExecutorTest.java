package org.flatcam.app.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobExecutorTest {

    private JobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobExecutor(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void runsJobOffCallingThread() throws Exception {
        Thread callingThread = Thread.currentThread();
        JobHandle<Thread> handle = executor.submit(context -> Thread.currentThread(), null);

        Thread jobThread = handle.completion().get(2, TimeUnit.SECONDS);

        assertTrue(jobThread != callingThread, "job must not run on the caller's thread");
    }

    @Test
    void reportsProgressInOrder() throws Exception {
        List<Double> fractions = new CopyOnWriteArrayList<>();
        Job<Void> job = context -> {
            for (int i = 1; i <= 5; i++) {
                context.reportProgress(i / 5.0, "step " + i);
            }
            return null;
        };

        JobHandle<Void> handle = executor.submit(job, (fraction, message) -> fractions.add(fraction));
        handle.completion().get(2, TimeUnit.SECONDS);

        assertEquals(List.of(0.2, 0.4, 0.6, 0.8, 1.0), fractions);
    }

    @Test
    void cancellationStopsTheJobAndCompletesExceptionally() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Job<Void> job = context -> {
            started.countDown();
            while (true) {
                context.checkCancelled();
                Thread.sleep(10);
            }
        };

        JobHandle<Void> handle = executor.submit(job, null);
        assertTrue(started.await(2, TimeUnit.SECONDS));

        handle.cancel();

        try {
            handle.completion().get(2, TimeUnit.SECONDS);
        } catch (CancellationException expected) {
            return;
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("expected CancellationException, got " + e, e);
        }
        throw new AssertionError("expected CancellationException, job completed normally");
    }

    @Test
    void exceptionInJobCompletesExceptionally() {
        Job<Void> job = context -> {
            throw new IllegalStateException("boom");
        };

        JobHandle<Void> handle = executor.submit(job, null);

        ExecutionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                ExecutionException.class,
                () -> handle.completion().get(2, TimeUnit.SECONDS)
        );
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }
}
