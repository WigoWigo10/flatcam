package org.flatcam.cam.gcode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.isolation.IsolationResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * Generates GRBL-compatible drilling G-code from a parsed {@link ExcellonImage}:
 * absolute positioning, one rapid+plunge+retract per hole and per slot
 * (a straight plunge-cut-retract, single pass - no stepped/pecking cycles),
 * grouped and ordered by tool. Deliberately avoids canned cycles (G81/G82) -
 * GRBL (the firmware on common hobby routers, e.g. the Genmitsu 3030) does
 * not support them - so this is plain G0/G1 rather than the shorter but
 * less portable canned-cycle form.
 *
 * <p>This is new functionality, not a port of legacy behavior - there is no
 * Python oracle to diff against here (see the Fase 0 baseline tests for
 * that pattern elsewhere in this codebase). Correctness is covered by a
 * plain unit test (GCodeGeneratorTest) instead.
 */
public final class GCodeGenerator {

    private GCodeGenerator() {
    }

    public static String generateDrillGCode(ExcellonImage image, DrillGCodeParameters params) {
        return generateDrillGCode(image, params, null);
    }

    /**
     * @param selectedToolIds which tools to include, or {@code null}/empty for all of them - mirrors
     *                        appTools/ToolDrilling.py's own tools table, where row SELECTION (not a
     *                        checkbox) decides which tools' drills/slots go into the generated G-code.
     */
    public static String generateDrillGCode(ExcellonImage image, DrillGCodeParameters params, Set<Integer> selectedToolIds) {
        Map<Integer, List<ExcellonImage.Drill>> drillsByTool =
                image.drills().stream().collect(Collectors.groupingBy(ExcellonImage.Drill::toolId));
        Map<Integer, List<ExcellonImage.Slot>> slotsByTool =
                image.slots().stream().collect(Collectors.groupingBy(ExcellonImage.Slot::toolId));

        SortedSet<Integer> toolIds = new TreeSet<>();
        toolIds.addAll(drillsByTool.keySet());
        toolIds.addAll(slotsByTool.keySet());
        if (selectedToolIds != null && !selectedToolIds.isEmpty()) {
            toolIds.retainAll(selectedToolIds);
        }

        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - furacao");
        line(gcode, "; Unidades do arquivo de origem: %s", image.units());
        line(gcode, image.units().equals("MM") ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));

        boolean firstTool = true;
        for (int toolId : toolIds) {
            if (!firstTool) {
                if (params.spindleSpeedRpm() > 0) {
                    line(gcode, "M5");
                }
                if (params.pauseForToolChange()) {
                    Double diameter = image.toolDiameters().get(toolId);
                    line(gcode, "M0 ; troque para a ferramenta T%d (diametro %s %s) e continue",
                            toolId, diameter != null ? fmt(diameter) : "?", image.units());
                }
            }
            firstTool = false;

            if (params.spindleSpeedRpm() > 0) {
                line(gcode, "M3 S%d", params.spindleSpeedRpm());
            }

            for (ExcellonImage.Drill drill : drillsByTool.getOrDefault(toolId, List.of())) {
                line(gcode, "G0 X%s Y%s", fmt(drill.x()), fmt(drill.y()));
                line(gcode, "G1 Z-%s F%s", fmt(params.drillDepth()), fmt(params.feedRate()));
                line(gcode, "G0 Z%s", fmt(params.safeZ()));
            }
            for (ExcellonImage.Slot slot : slotsByTool.getOrDefault(toolId, List.of())) {
                line(gcode, "G0 X%s Y%s", fmt(slot.x1()), fmt(slot.y1()));
                line(gcode, "G1 Z-%s F%s", fmt(params.drillDepth()), fmt(params.feedRate()));
                line(gcode, "G1 X%s Y%s F%s", fmt(slot.x2()), fmt(slot.y2()), fmt(params.feedRate()));
                line(gcode, "G0 Z%s", fmt(params.safeZ()));
            }
        }
        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        return gcode.toString();
    }

    /**
     * Generates isolation-routing G-code: one rapid+plunge+follow-ring+retract
     * per ring in the result (see IsolationGenerator) - the tool traces the
     * ring itself (already closed, first coordinate == last), no separate
     * closing move needed. A single tool per job, unlike drilling's tool
     * table - isolation is normally cut with one bit - so no tool-change
     * pause here.
     */
    public static String generateIsolationGCode(IsolationResult result, IsolationGCodeParameters params) {
        StringBuilder gcode = new StringBuilder();
        line(gcode, "; Gerado por FlatCAM Next (prototipo) - isolamento");
        line(gcode, "; Unidades do arquivo de origem: %s", result.units());
        line(gcode, result.units().equals("MM") ? "G21" : "G20");
        line(gcode, "G90");
        line(gcode, "G94");
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M3 S%d", params.spindleSpeedRpm());
        }

        Geometry geometry = result.geometry();
        int count = geometry.getNumGeometries();
        for (int i = 0; i < count; i++) {
            Coordinate[] coordinates = ringCoordinates(geometry.getGeometryN(i));
            if (coordinates == null || coordinates.length == 0) {
                continue;
            }
            line(gcode, "G0 X%s Y%s", fmt(coordinates[0].x), fmt(coordinates[0].y));
            line(gcode, "G1 Z-%s F%s", fmt(params.cutDepth()), fmt(params.feedRate()));
            for (int p = 1; p < coordinates.length; p++) {
                line(gcode, "G1 X%s Y%s F%s", fmt(coordinates[p].x), fmt(coordinates[p].y), fmt(params.feedRate()));
            }
            line(gcode, "G0 Z%s", fmt(params.safeZ()));
        }

        if (params.spindleSpeedRpm() > 0) {
            line(gcode, "M5");
        }
        line(gcode, "G0 Z%s", fmt(params.safeZ()));
        line(gcode, "M30");
        return gcode.toString();
    }

    private static Coordinate[] ringCoordinates(Geometry geometry) {
        return switch (geometry) {
            case LineString line -> line.getCoordinates();
            case Polygon polygon -> polygon.getExteriorRing().getCoordinates();
            default -> null;
        };
    }

    private static void line(StringBuilder sb, String format, Object... args) {
        sb.append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
