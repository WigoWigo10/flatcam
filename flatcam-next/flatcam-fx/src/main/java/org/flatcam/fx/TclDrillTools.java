package org.flatcam.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.tcl.TclException;

/**
 * Tool selection of Python's {@code drillcncjob} ({@code -drilled_dias}/{@code -diatol}) followed by
 * {@code generate_from_excellon_by_tool(order='fwd')}: only tools that actually carry drills or slots,
 * in ascending diameter order (ties by tool number). Without {@code -diatol} the diameters are compared
 * rounded to four decimals - Python rounds to the object's own {@code decimals}, four by default -
 * and with it Python's {@code math.isclose(a, b, rel_tol)}. Every requested diameter must match at
 * least one tool, as Python's "not actual tool diameters" error requires.
 */
final class TclDrillTools {

    private TclDrillTools() {
    }

    static List<Integer> select(ExcellonImage image, List<Double> requested, Double relativeTolerance)
            throws TclException {
        Set<Integer> used = new LinkedHashSet<>();
        image.drills().forEach(drill -> used.add(drill.toolId()));
        image.slots().forEach(slot -> used.add(slot.toolId()));
        Map<Integer, Double> diameters = image.toolDiameters();
        Set<Integer> chosen = new LinkedHashSet<>();
        if (requested.isEmpty()) {
            chosen.addAll(used);
        } else {
            for (double wanted : requested) {
                boolean matched = false;
                for (int id : used) {
                    Double dia = diameters.get(id);
                    if (dia != null && same(dia, wanted, relativeTolerance)) {
                        chosen.add(id);
                        matched = true;
                    }
                }
                if (!matched) {
                    throw new TclException("Diameter " + wanted + " is not an actual tool diameter with drills or "
                            + "slots in the Excellon object.");
                }
            }
        }
        List<Integer> ordered = new ArrayList<>(chosen);
        ordered.sort(Comparator.<Integer>comparingDouble(id -> diameters.getOrDefault(id, 0.0))
                .thenComparingInt(id -> id));
        return ordered;
    }

    private static boolean same(double a, double b, Double relativeTolerance) {
        double ra = Math.round(a * 1e4) / 1e4;
        double rb = Math.round(b * 1e4) / 1e4;
        if (relativeTolerance == null) return ra == rb;
        return Math.abs(ra - rb) <= relativeTolerance * Math.max(Math.abs(ra), Math.abs(rb));
    }
}
