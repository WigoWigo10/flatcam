package org.flatcam.cam.gcode;

import java.util.function.Consumer;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;

/** Explicit alternative to slot milling. Linear spacing uses the source tool's real diameter. */
public record DrillSlotParameters(boolean enabled, double overlapPercent, boolean addLastHole) {
    /** Resource guard, not a geometric tolerance; may be configurable in a future release. */
    public static final int MAX_HOLES = 1_000_000;

    public DrillSlotParameters {
        if (!Double.isFinite(overlapPercent) || overlapPercent < 0 || overlapPercent >= 100)
            throw new IllegalArgumentException("Sobreposição de Drill slots deve estar entre 0 (inclusive) e 100% (exclusive).");
    }

    public static DrillSlotParameters milling() { return new DrillSlotParameters(false, 0, true); }

    /** Mirrors the original Python interpolation and its spacing/10 last-hole threshold. No deduplication. */
    public void emit(ExcellonImage.Slot slot, double diameter, CancellationToken cancellation,
                     ProgressCallback progress, Consumer<ExcellonImage.Drill> output) {
        if (!enabled) throw new IllegalStateException("Drill slots desativado.");
        cancellation.throwIfCancellationRequested();
        if (slot.hasPath()) { emitPath(slot,diameter,cancellation,progress,output); return; }
        double spacing = diameter * (1 - overlapPercent / 100);
        double dx = slot.x2() - slot.x1(), dy = slot.y2() - slot.y1();
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(diameter) || diameter <= 0 || !Double.isFinite(spacing) || spacing <= 0
                || !Double.isFinite(length) || !Double.isFinite(slot.x1()) || !Double.isFinite(slot.y1()))
            throw new IllegalArgumentException("Drill slots exige diâmetro positivo e coordenadas finitas (T" + slot.toolId() + ").");
        if (length / spacing > MAX_HOLES - 2)
            throw new IllegalArgumentException("Drill slots excede o limite de " + MAX_HOLES + " furos por rasgo; reduza a sobreposição.");
        output.accept(new ExcellonImage.Drill(slot.toolId(), slot.x1(), slot.y1()));
        double lastX = slot.x1(), lastY = slot.y1();
        for (int i = 1; spacing * i < length; i++) {
            cancellation.throwIfCancellationRequested();
            double fraction = spacing * i / length;
            double x = slot.x1() + dx * fraction, y = slot.y1() + dy * fraction;
            // Interpolation can round to the endpoint before target reaches length (notably in IN).
            // Python's Point.within(LineString) excludes that point; retain the prior centre for last-hole.
            if (x == slot.x2() && y == slot.y2()) break;
            lastX = x; lastY = y;
            output.accept(new ExcellonImage.Drill(slot.toolId(), lastX, lastY));
            if ((i & 1023) == 0) progress.report(fraction);
        }
        cancellation.throwIfCancellationRequested();
        if (addLastHole && Math.hypot(slot.x2() - lastX, slot.y2() - lastY) >= spacing / 10)
            output.accept(new ExcellonImage.Drill(slot.toolId(), slot.x2(), slot.y2()));
        progress.report(1);
    }

    /** Curved routs use arc length along the stored polyline, never the endpoint chord. */
    private void emitPath(ExcellonImage.Slot slot,double diameter,CancellationToken cancellation,
                          ProgressCallback progress,Consumer<ExcellonImage.Drill> output) {
        double spacing=diameter*(1-overlapPercent/100);
        if (!Double.isFinite(diameter) || diameter<=0 || !Double.isFinite(spacing) || spacing<=0)
            throw new IllegalArgumentException("Drill slots exige diâmetro e passo positivos.");
        var points=slot.path(); double[] distances=new double[points.size()];
        for (int i=1;i<points.size();i++) {
            cancellation.throwIfCancellationRequested();
            distances[i]=distances[i-1]+Math.hypot(points.get(i).x()-points.get(i-1).x(),points.get(i).y()-points.get(i-1).y());
        }
        double length=distances[distances.length-1];
        if (!Double.isFinite(length) || length/spacing>MAX_HOLES-2)
            throw new IllegalArgumentException("Drill slots excede o limite de "+MAX_HOLES+" furos por rasgo.");
        output.accept(new ExcellonImage.Drill(slot.toolId(),slot.x1(),slot.y1()));
        int segment=1; double lastDistance=0;
        for (int i=1;spacing*i<length;i++) {
            cancellation.throwIfCancellationRequested();
            double distance=spacing*i;
            while(segment<distances.length-1 && distances[segment]<distance) segment++;
            double fraction=(distance-distances[segment-1])/(distances[segment]-distances[segment-1]);
            var a=points.get(segment-1); var b=points.get(segment);
            output.accept(new ExcellonImage.Drill(slot.toolId(),a.x()+(b.x()-a.x())*fraction,a.y()+(b.y()-a.y())*fraction));
            lastDistance=distance;
            if ((i&1023)==0) progress.report(distance/length);
        }
        cancellation.throwIfCancellationRequested();
        if (addLastHole && length-lastDistance>=spacing/10)
            output.accept(new ExcellonImage.Drill(slot.toolId(),slot.x2(),slot.y2()));
        progress.report(1);
    }
}
