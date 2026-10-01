package org.flatcam.cam.gcode;

import java.util.Locale;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;

/** Strict linear HPGL subset for CNC preview, separate from the permissive Geometry importer. */
final class HpglProgramCodec {
    private static final Pattern HEADER = Pattern.compile(
            "(?is)^\\s*(?:CO\\s*\"[^\"]*\"\\s*;\\s*)*IN\\s*;");
    private static final Pattern PEN = Pattern.compile("FCFX PEN P(\\d+) D(\\d*\\.?\\d+)");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    private HpglProgramCodec() { }

    static boolean isHpgl(String program) {
        return program != null && HEADER.matcher(withoutBom(program)).find();
    }

    private static String withoutBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** Python rounds to a 0.025 mm grid. Reject overflow rather than clipping and deforming the path. */
    static int coordinate(double value, String units) {
        double scaled = value * ("IN".equalsIgnoreCase(units) ? 1016 : 40);
        double rounded = Math.rint(scaled);
        if (!Double.isFinite(rounded) || rounded < -32767 || rounded > 32768)
            throw new IllegalArgumentException("Coordenada HPGL fora da faixa -32767..32768 (passo 0,025 mm).");
        return (int) rounded;
    }

    static String normalize(String program, CancellationToken cancellation, ProgressCallback progress) {
        String text = withoutBom(program);
        StringBuilder result = new StringBuilder("; Preprocessor: HPGL\n; FCFX PLOTTER\nG21\nG90\nM5\n");
        int cursor = 0;
        boolean initialized = false, down = false, absolute = true, inches = false, moved = false;
        double x = 0, y = 0;
        int pen = 1;
        java.util.Map<Integer, Double> widths = new java.util.HashMap<>();
        progress.report(0);
        while (cursor < text.length()) {
            cancellation.throwIfCancellationRequested();
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
            if (cursor == text.length()) break;
            int start = cursor;
            if (cursor + 2 > text.length()) throw invalid(start, "Comando incompleto");
            String command = text.substring(cursor, cursor + 2).toUpperCase(Locale.ROOT);
            cursor += 2;
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
            String argument;
            if (command.equals("CO")) {
                if (cursor == text.length() || text.charAt(cursor++) != '"') throw invalid(start, "CO exige texto entre aspas");
                int end = text.indexOf('"', cursor);
                if (end < 0) throw invalid(start, "Comentario CO incompleto");
                argument = text.substring(cursor, end);
                cursor = end + 1;
                while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
                if (cursor == text.length() || text.charAt(cursor++) != ';') throw invalid(start, "CO sem terminador");
            } else {
                int end = text.indexOf(';', cursor);
                if (end < 0) throw invalid(start, "Comando sem terminador ;");
                argument = text.substring(cursor, end).trim();
                cursor = end + 1;
            }
            if (!initialized && !command.equals("IN") && !command.equals("CO"))
                throw invalid(start, "Comando antes de IN");
            switch (command) {
                case "IN" -> {
                    if (initialized || !argument.isEmpty()) throw invalid(start, "Reset IN adicional ou com parametros nao modelado");
                    initialized = true;
                }
                case "CO" -> {
                    if (argument.startsWith("FCFX HPGL UNITS ")) {
                        if (moved) throw invalid(start, "Unidades declaradas depois dos movimentos");
                        String units = argument.substring("FCFX HPGL UNITS ".length());
                        if (!units.equals("MM") && !units.equals("IN")) throw invalid(start, "Unidades invalidas");
                        inches = units.equals("IN");
                        result.append(inches ? "G20\n" : "G21\n");
                    }
                    var marker = PEN.matcher(argument);
                    if (marker.matches()) {
                        int id = penNumber(marker.group(1), start);
                        double width = Double.parseDouble(marker.group(2));
                        if (!Double.isFinite(width) || width <= 0) throw invalid(start, "Largura de caneta invalida");
                        widths.put(id, width);
                        if (id == pen) result.append(';').append(GCodeToolpathParser.millMarker(width)).append('\n');
                    }
                }
                case "SP" -> {
                    if (down) throw invalid(start, "Selecao de caneta exige PU antes de SP");
                    pen = argument.isEmpty() ? 0 : penNumber(argument, start);
                    result.append('T').append(pen).append('\n');
                    // An unmarked external pen must not inherit the preceding pen's width.
                    if (pen != 0) result.append(';').append(GCodeToolpathParser.millMarker(widths.getOrDefault(pen, 0.0))).append('\n');
                }
                case "PA", "PR", "PU", "PD" -> {
                    if (command.equals("PA")) absolute = true;
                    if (command.equals("PR")) absolute = false;
                    if (command.equals("PU") || command.equals("PD")) {
                        down = command.equals("PD");
                        if (down && pen == 0) throw invalid(start, "PD sem caneta selecionada");
                        result.append(down ? "M3\n" : "M5\n");
                    }
                    if (!argument.isEmpty()) {
                        String[] numbers = argument.split(",", -1);
                        if (numbers.length % 2 != 0) throw invalid(start, "Coordenadas exigem pares X,Y");
                        for (int i = 0; i < numbers.length; i += 2) {
                            cancellation.throwIfCancellationRequested();
                            double nx = number(numbers[i], start), ny = number(numbers[i + 1], start);
                            x = absolute ? nx : x + nx;
                            y = absolute ? ny : y + ny;
                            if (x < -32767 || x > 32768 || y < -32767 || y > 32768)
                                throw invalid(start, "Coordenadas fora da faixa HPGL");
                            double scale = inches ? 1016 : 40;
                            result.append(down ? "G1" : "G0").append(" X").append(x / scale)
                                    .append(" Y").append(y / scale).append('\n');
                            moved = true;
                        }
                    }
                }
                default -> throw invalid(start, "Comando HPGL nao modelado: " + command + "; previa indisponivel");
            }
            progress.report((double) cursor / text.length());
        }
        if (!initialized) throw invalid(0, "IN ausente");
        cancellation.throwIfCancellationRequested();
        progress.report(1);
        return result.toString();
    }

    private static double number(String text, int position) {
        if (!NUMBER.matcher(text.trim()).matches()) throw invalid(position, "Coordenada invalida");
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value)) throw invalid(position, "Coordenada nao finita");
        return value;
    }

    private static int penNumber(String text, int position) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 0) throw invalid(position, "Numero de caneta negativo");
            return value;
        } catch (NumberFormatException bad) { throw invalid(position, "Numero de caneta invalido"); }
    }

    private static IllegalArgumentException invalid(int position, String message) {
        return new IllegalArgumentException(message + " na posicao HPGL " + position);
    }
}
