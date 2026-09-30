package org.flatcam.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.flatcam.cam.gcode.GCodeToolpathParser.PathStep;

/** The route of a CNC Job as a spreadsheet: one row per leg, in machining order. */
final class CncStepCsv {

    static final String HEADER = "passo,tipo,marca_inicio,marca_fim,x_inicio,y_inicio,x_fim,y_fim,comprimento,unidade,"
            + "duracao_s,acumulado_s";

    private CncStepCsv() {
    }

    static String toCsv(List<PathStep> steps, String units) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        double previous = 0;
        for (PathStep step : steps) {
            double[] xy = step.xy();
            int last = xy.length - 2;
            String kind = step.travel() ? "deslocamento" : xy.length == 2 ? "furo" : "corte";
            double cumulative = step.endMinutes() * 60;
            double duration = cumulative - previous;
            previous = Double.isNaN(cumulative) ? previous : cumulative;
            csv.append(step.index() + 1).append(',').append(kind).append(',')
                    .append(step.fromMark() > 0 ? Integer.toString(step.fromMark()) : "").append(',')
                    .append(step.toMark() > 0 ? Integer.toString(step.toMark()) : "").append(',')
                    .append(number(xy[0], 4)).append(',').append(number(xy[1], 4)).append(',')
                    .append(number(xy[last], 4)).append(',').append(number(xy[last + 1], 4)).append(',')
                    .append(number(step.length(), 4)).append(',').append(units.toLowerCase(Locale.ROOT)).append(',')
                    .append(number(duration, 2)).append(',').append(number(cumulative, 2)).append('\n');
        }
        return csv.toString();
    }

    static void write(List<PathStep> steps, String units, Path destination) throws IOException {
        Files.writeString(destination, toCsv(steps, units), StandardCharsets.UTF_8);
    }

    private static String number(double value, int decimals) {
        return Double.isNaN(value) ? "" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
