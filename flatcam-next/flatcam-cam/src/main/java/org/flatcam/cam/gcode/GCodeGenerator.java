package org.flatcam.cam.gcode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.flatcam.cam.excellon.ExcellonImage;

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
        Map<Integer, List<ExcellonImage.Drill>> drillsByTool =
                image.drills().stream().collect(Collectors.groupingBy(ExcellonImage.Drill::toolId));
        Map<Integer, List<ExcellonImage.Slot>> slotsByTool =
                image.slots().stream().collect(Collectors.groupingBy(ExcellonImage.Slot::toolId));

        SortedSet<Integer> toolIds = new TreeSet<>();
        toolIds.addAll(drillsByTool.keySet());
        toolIds.addAll(slotsByTool.keySet());

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

    private static void line(StringBuilder sb, String format, Object... args) {
        sb.append(String.format(Locale.ROOT, format, args)).append('\n');
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
