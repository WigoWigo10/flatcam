package org.flatcam.app.job;

/**
 * A unit of work meant to run off the UI thread (parsing, geometry
 * generation, G-code export, ...). See {@link JobExecutor}.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface Job<T> {
    T run(JobContext context) throws Exception;
}
