package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.GapPattern;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/** Worker-backed draft: no project object is changed until the ordinary Generate action. */
final class CutoutGapPlacementController implements AutoCloseable {
    private final PlotAreaView plot;
    private final JobExecutor jobs;
    private final String units;
    private final Geometry source;
    private final BooleanSupplier sourceAvailable;
    private JobHandle<Geometry> job;
    private long revision;
    private boolean active;
    private boolean closed;
    private Consumer<String> feedback = message -> { };

    CutoutGapPlacementController(PlotAreaView plot, JobExecutor jobs, String units, Geometry source,
                                 BooleanSupplier sourceAvailable) {
        this.plot = plot; this.jobs = jobs; this.units = units; this.source = source;
        this.sourceAvailable = sourceAvailable;
    }

    static Geometry cursorMask(GeometryFactory factory, double x, double y, double diameter, double gap) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(diameter)
                || !Double.isFinite(gap) || diameter <= 0 || gap <= 0)
            throw new IllegalArgumentException("Diâmetro e largura da ponte devem ser positivos e finitos.");
        // ToolCutOut.cutting_geo: the removed centerline includes the tool diameter.
        double half = (diameter + gap) / 2;
        if (!Double.isFinite(half) || !Double.isFinite(x - half) || !Double.isFinite(x + half)
                || !Double.isFinite(y - half) || !Double.isFinite(y + half))
            throw new IllegalArgumentException("Área do gap fora do intervalo válido.");
        return factory.toGeometry(new Envelope(x - half, x + half, y - half, y + half));
    }

    boolean begin(CutoutParameters params, List<Geometry> existing, Consumer<Geometry> accepted,
                  Consumer<String> status) {
        if (closed || !sourceAvailable.getAsBoolean()) {
            status.accept("Origem alterada; reabra Cutout."); return false;
        }
        cursorMask(source.getFactory(), 0, 0, params.toolDiameter(), params.gapSize());
        cancel();
        feedback = status;
        long current = ++revision;
        var masks = new ArrayList<>(existing);
        var remaining = new Geometry[1];
        // Exterior only: the gesture must not put bridges on opt-in internal cuts.
        var outlineParams = new CutoutParameters(params.toolDiameter(), params.margin(), params.convexShape(),
                params.kind(), params.shape(), params.gapSize(), GapPattern.NONE, false);
        active = plot.beginCursorPlacement(new PlotAreaView.CursorPlacementHandler() {
            public Geometry preview(double x, double y) {
                return cursorMask(source.getFactory(), x, y, params.toolDiameter(), params.gapSize());
            }
            public void onClick(double x, double y) {
                if (!sourceAvailable.getAsBoolean()) { cancel(); status.accept("Origem alterada; reabra Cutout."); return; }
                if (job != null || remaining[0] == null) { status.accept("Aguarde o cálculo da prévia."); return; }
                Geometry mask = preview(x, y);
                var candidate = new ArrayList<>(masks); candidate.add(mask);
                status.accept("Calculando ponte...");
                JobHandle<Geometry> handle = jobs.submit(context -> CutoutGenerator.generate(units, source,
                        outlineParams, candidate, context::isCancelled).geometry(), null);
                job = handle;
                handle.completion().whenComplete((paths, error) -> Platform.runLater(() -> {
                    if (!isCurrent(current, handle)) return;
                    job = null;
                    if (error != null) { status.accept("Falha na prévia: " + cause(error)); return; }
                    if (paths.isEmpty()) { status.accept("Gap recusado: eliminaria todo o caminho externo."); return; }
                    double removed = remaining[0].getLength() - paths.getLength();
                    if (removed <= Math.max(1e-12, params.toolDiameter() * 1e-9)) {
                        status.accept("Clique sobre um trecho externo ainda não recortado."); return;
                    }
                    masks.add(mask); remaining[0] = paths;
                    accepted.accept(mask);
                    show(paths, masks);
                    status.accept(masks.size() + " gap(s) manuais. Clique para adicionar; botão direito ou Esc conclui.");
                }));
            }
            public void onFinish() {
                active = false; ++revision;
                boolean pending = job != null;
                if (job != null) job.cancel(); job = null;
                status.accept((pending ? "Cálculo pendente cancelado. " : "Posicionamento concluído. ")
                        + "Gaps confirmados mantidos; gere o recorte para aplicar.");
            }
        });
        if (!active) { status.accept("Não foi possível iniciar o posicionamento no Plot Area."); return false; }
        status.accept("Preparando contorno para os gaps...");
        JobHandle<Geometry> handle = jobs.submit(context -> CutoutGenerator.generate(units, source,
                outlineParams, masks, context::isCancelled).geometry(), null);
        job = handle;
        handle.completion().whenComplete((paths, error) -> Platform.runLater(() -> {
            if (!isCurrent(current, handle)) return;
            job = null;
            if (error != null || paths.isEmpty()) {
                cancel(); status.accept(error == null ? "Contorno externo vazio." : "Falha na prévia: " + cause(error)); return;
            }
            remaining[0] = paths; show(paths, masks);
            status.accept("Clique sobre o contorno para abrir pontes. Botão direito ou Esc conclui; Gerar aplica.");
        }));
        return true;
    }

    private boolean isCurrent(long expected, JobHandle<Geometry> handle) {
        if (closed || !active || revision != expected || job != handle || handle.isCancelled()) return false;
        if (!sourceAvailable.getAsBoolean()) { cancel(); feedback.accept("Origem alterada; reabra Cutout."); return false; }
        return true;
    }

    private void show(Geometry paths, List<Geometry> masks) {
        plot.setEditorReference(paths);
        plot.setEditorHighlight(masks.isEmpty() ? null : source.getFactory().buildGeometry(masks), false);
    }

    void cancel() {
        ++revision; active = false;
        if (job != null) job.cancel(); job = null;
        plot.cancelPlacement();
        plot.setEditorReference(null); plot.setEditorHighlight(null, false);
    }

    boolean isBusy() { return job != null; }

    @Override public void close() { cancel(); closed = true; }

    private static String cause(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage();
    }
}
