package org.flatcam.cam.gcode;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;

/** Native linear RML-1 used by Roland_MDX_20.py. No invented controller comments or tool changes. */
final class RolandProgramCodec {
    private static final Pattern HEADER = Pattern.compile("(?is)^(?:\\s*;)*\\s*\\^IN\\s*;");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    private RolandProgramCodec() { }

    static boolean isRoland(String program) {
        return program != null && HEADER.matcher(withoutBom(program)).find();
    }

    private static String withoutBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    static double velocity(double feedPerMinute) {
        double perSecond = feedPerMinute / 60;
        if (!Double.isFinite(perSecond) || perSecond < 0.1 || perSecond > 15)
            throw new IllegalArgumentException("Avanco Roland deve estar entre 6 e 900 mm/min (0,1..15 mm/s).");
        return Math.rint(perSecond * 10) / 10;
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static double coordinate(double value) {
        // Preserve the Python XY text resolution (one decimal in 1/40 mm units), also for Z.
        double scaled = Math.rint(value * 400) / 10;
        if (!Double.isFinite(scaled) || scaled < Integer.MIN_VALUE || scaled > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Coordenada Roland fora do limite numerico.");
        return scaled;
    }

    static String encode(String program, double rapidFeed) {
        return encode(program, rapidFeed, CancellationToken.none());
    }

    static String encode(String program, double rapidFeed, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        double rapid = velocity(rapidFeed == 0 ? 900 : rapidFeed);
        StringBuilder result = new StringBuilder(";;^IN;\n^PA;\n");
        double x = 0, y = 0, z = 0, feed = 0;
        for (var lines = program.lines().iterator(); lines.hasNext();) {
            cancellation.throwIfCancellationRequested();
            String raw = lines.next();
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("(")) continue;
            Map<Character, Double> words = ControllerProgramCodec.words(line);
            if (words.containsKey('G')) {
                double code = words.get('G');
                if (code == 21 || code == 90 || code == 94) continue;
                if (code != 0 && code != 1) throw unsupported(line);
                if (words.keySet().stream().anyMatch(key -> key != 'G' && key != 'X' && key != 'Y' && key != 'Z' && key != 'F'))
                    throw unsupported(line);
                x = words.getOrDefault('X', x);
                y = words.getOrDefault('Y', y);
                z = words.getOrDefault('Z', z);
                feed = words.getOrDefault('F', feed);
                boolean axes = words.containsKey('X') || words.containsKey('Y') || words.containsKey('Z');
                if (!axes) throw unsupported(line);
                double speed = code == 0 ? rapid : velocity(feed);
                double nativeZ = coordinate(z);
                if (z < 0 && nativeZ >= 0 || z > 0 && nativeZ <= 0)
                    throw new IllegalArgumentException("Altura/profundidade menor que a resolucao textual Roland.");
                result.append('V').append(oneDecimal(speed)).append(";\nZ")
                        .append(oneDecimal(coordinate(x))).append(',').append(oneDecimal(coordinate(y)))
                        .append(',').append(oneDecimal(nativeZ)).append(";\n");
            } else if (words.containsKey('M')) {
                double code = words.get('M');
                if (words.keySet().stream().anyMatch(key -> key != 'M' && key != 'S') || code != 3 && code != 5)
                    throw unsupported(line);
                result.append(code == 3 ? "!MC1;\n" : "!MC0;\n");
            } else throw unsupported(line);
        }
        // No home/reset at the end: the preceding generated move sets the chosen safe Z/XY.
        return result.toString();
    }

    static String normalize(String program, CancellationToken cancellation, ProgressCallback progress) {
        String text = withoutBom(program);
        StringBuilder result = new StringBuilder("; Preprocessor: ROLAND_MDX_20\nG21\nG90\n");
        boolean initialized = false, positioned = false, absolute = false;
        double speed = 0;
        int cursor = 0;
        progress.report(0);
        while (cursor < text.length()) {
            cancellation.throwIfCancellationRequested();
            while (cursor < text.length() && (Character.isWhitespace(text.charAt(cursor)) || text.charAt(cursor) == ';')) cursor++;
            if (cursor == text.length()) break;
            int start = cursor;
            if (text.regionMatches(true, cursor, "!MC", 0, 3)) {
                if (!initialized || cursor + 3 >= text.length()) throw invalid(start, "Motor antes de ^IN ou incompleto");
                char state = text.charAt(cursor + 3);
                if (state != '0' && state != '1') throw invalid(start, "Motor deve ser !MC0 ou !MC1");
                cursor += 4;
                if (cursor < text.length() && text.charAt(cursor) != ';' && !Character.isWhitespace(text.charAt(cursor)))
                    throw invalid(start, "Comando de motor invalido");
                result.append(state == '1' ? "M3\n" : "M5\n");
            } else {
                int end = text.indexOf(';', cursor);
                if (end < 0) throw invalid(start, "Comando sem terminador ;");
                String command = text.substring(cursor, end).trim().toUpperCase(Locale.ROOT);
                cursor = end + 1;
                if (command.equals("^IN")) {
                    if (initialized) throw invalid(start, "Reset ^IN adicional nao modelado");
                    initialized = true;
                } else {
                    if (!initialized) throw invalid(start, "Comando antes de ^IN");
                    if (command.equals("^PA")) {
                        absolute = true;
                    } else if (command.startsWith("V")) {
                        speed = number(command.substring(1), start);
                        if (speed < 0.1 || speed > 15) throw invalid(start, "V fora de 0,1..15 mm/s");
                    } else if (command.startsWith("Z")) {
                        if (!absolute || speed <= 0) throw invalid(start, "Z exige ^PA e velocidade V antes do movimento");
                        String[] axes = command.substring(1).split(",", -1);
                        if (axes.length != 3) throw invalid(start, "Z exige X,Y,Z");
                        double x = number(axes[0], start) / 40, y = number(axes[1], start) / 40, z = number(axes[2], start) / 40;
                        if (!positioned) {
                            // The machine's initial XY is unknown: establish it before a pure-Z plunge.
                            result.append("G0 X").append(x).append(" Y").append(y).append('\n');
                            positioned = true;
                        }
                        result.append("G1 X").append(x).append(" Y").append(y).append(" Z").append(z)
                                .append(" F").append(speed * 60).append('\n');
                    } else throw invalid(start, "Comando RML nao modelado: " + command + "; previa indisponivel");
                }
            }
            progress.report((double) cursor / text.length());
        }
        if (!initialized) throw invalid(0, "^IN ausente");
        cancellation.throwIfCancellationRequested();
        progress.report(1);
        return result.toString();
    }

    private static double number(String text, int position) {
        if (!NUMBER.matcher(text.trim()).matches()) throw invalid(position, "Numero invalido");
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw invalid(position, "Numero fora do limite");
        return value;
    }

    private static IllegalArgumentException unsupported(String line) {
        return new IllegalArgumentException("Comando nao suportado para Roland: " + line);
    }

    private static IllegalArgumentException invalid(int position, String message) {
        return new IllegalArgumentException(message + " na posicao RML " + position);
    }
}
