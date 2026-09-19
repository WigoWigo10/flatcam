package org.flatcam.fx;

import org.flatcam.app.job.Job;
import org.flatcam.app.job.JobContext;

/**
 * Stands in for a real workload (Gerber parsing, geometry generation, ...)
 * until flatcam-cam exists (Fase 3+). Exists only to prove, end to end, that
 * a job can run off the JavaFX Application Thread while reporting progress
 * and honoring cancellation - see JobExecutor and MainWindow's "Job de
 * demonstracao" button.
 */
final class DemoJob implements Job<Void> {

    private static final int STEPS = 40;
    private static final long STEP_MILLIS = 100;

    @Override
    public Void run(JobContext context) throws InterruptedException {
        for (int step = 1; step <= STEPS; step++) {
            context.checkCancelled();
            Thread.sleep(STEP_MILLIS);
            double fraction = (double) step / STEPS;
            context.reportProgress(fraction, "Processando item " + step + " de " + STEPS + "...");
        }
        return null;
    }
}
