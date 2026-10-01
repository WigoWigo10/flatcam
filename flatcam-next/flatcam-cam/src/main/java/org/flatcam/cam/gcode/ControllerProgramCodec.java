package org.flatcam.cam.gcode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;

/** Encodes generated linear moves; normalizes the supported ICP subset for the shared XY preview. */
final class ControllerProgramCodec {
    private static final Pattern WORD = Pattern.compile("([A-Za-z])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    private static final Pattern ICP_HEADER = Pattern.compile("(?m)^\\s*IMF_PBL\\s+[^\\r\\n]+$");
    private static final Pattern LINEAR_MOVE = Pattern.compile("G0*[01]\\s+.*");
    private static final Pattern SPINDLE = Pattern.compile("(CW|CCW)(?:\\s+RPM(\\d+))?", Pattern.CASE_INSENSITIVE);

    private ControllerProgramCodec() { }

    static boolean isIcp(String program) {
        return program != null && ICP_HEADER.matcher(program.startsWith("\uFEFF") ? program.substring(1) : program).find();
    }

    /** Retains modal coordinates and writes XYZ on every G0/G1, without the Python Y=X typo. */
    static String explicitXyz(String program) {
        StringBuilder result = new StringBuilder(program.length());
        double x = 0, y = 0, z = 0;
        for (String line : program.lines().toList()) {
            if (LINEAR_MOVE.matcher(line).matches()) {
                Map<Character, Double> words = words(line);
                x = words.getOrDefault('X', x);
                y = words.getOrDefault('Y', y);
                z = words.getOrDefault('Z', z);
                result.append(words.get('G') == 0 ? "G00" : "G01")
                        .append(" X").append(GCodeGenerator.fmt(x))
                        .append(" Y").append(GCodeGenerator.fmt(y))
                        .append(" Z").append(GCodeGenerator.fmt(z));
                if (words.containsKey('F')) result.append(" F").append(GCodeGenerator.fmt(words.get('F')));
                result.append('\n');
            } else result.append(line).append('\n');
        }
        return result.toString();
    }

    static String encodeIcp(String program) {
        StringBuilder result = new StringBuilder("IMF_PBL flatcam\n");
        for (String raw : program.lines().toList()) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith(";") || line.startsWith("(")) {
                result.append(line.startsWith(";") ? line : "; " + line.substring(1, line.length() - 1)).append('\n');
                continue;
            }
            Map<Character, Double> words = words(line);
            if (words.containsKey('G')) {
                double code = words.get('G');
                if (code == 21 || code == 90 || code == 94) continue;
                if (code == 4) {
                    result.append("WAIT ").append(scaled(words.getOrDefault('P', 0.0), 1000)).append('\n');
                } else if (code == 0 || code == 1) {
                    if (words.keySet().stream().anyMatch(key -> key != 'G' && key != 'X' && key != 'Y' && key != 'Z' && key != 'F'))
                        throw new IllegalArgumentException("Palavra de movimento nao suportada para ICP: " + line);
                    if (code == 1 && words.containsKey('F')) {
                        int velocity = scaled(words.get('F'), 1000.0 / 60);
                        if (velocity <= 0) throw new IllegalArgumentException("Feed ICP abaixo de 1 micrometro/s.");
                        result.append("VEL ").append(velocity).append('\n');
                    }
                    if (!words.containsKey('X') && !words.containsKey('Y') && !words.containsKey('Z')) {
                        if (code == 1 && words.containsKey('F')) continue;
                        throw new IllegalArgumentException("Movimento ICP sem eixos: " + line);
                    }
                    result.append(code == 0 ? "FASTABS" : "MOVEABS");
                    for (char axis : new char[]{'X', 'Y', 'Z'}) {
                        if (words.containsKey(axis)) result.append(' ').append(axis).append(scaled(words.get(axis), 1000));
                    }
                    result.append('\n');
                } else throw new IllegalArgumentException("Comando nao suportado para ICP: " + line);
            } else if (words.containsKey('T')) {
                result.append("GETTOOL ").append(scaled(words.get('T'), 1)).append('\n');
            } else if (words.containsKey('M')) {
                double code = words.get('M');
                if (code == 5) result.append("SPINDLE OFF\n");
                else if (code == 3 || code == 4) {
                    result.append(code == 3 ? "SPINDLE CW" : "SPINDLE CCW");
                    if (words.containsKey('S')) result.append(" RPM").append(scaled(words.get('S'), 1));
                    result.append('\n');
                } else throw new IllegalArgumentException("Comando nao suportado para ICP: " + line);
            } else throw new IllegalArgumentException("Comando nao suportado para ICP: " + line);
        }
        // Do not clear the work origin or force Z0/X0/Y0 as Python does: retain the selected safe end move.
        return result.append("PROGEND\n").toString();
    }

    static String normalizeIcp(String program, CancellationToken cancellation, ProgressCallback progress) {
        var lines = program.lines().toList();
        StringBuilder result = new StringBuilder("; Preprocessor: ISEL_ICP_CNC\nG21\nG90\nG94\n");
        boolean header = false, ended = false;
        for (int index = 0; index < lines.size(); index++) {
            cancellation.throwIfCancellationRequested();
            String line = lines.get(index).trim();
            if (index == 0 && line.startsWith("\uFEFF")) line = line.substring(1).trim();
            int comment = line.indexOf(';');
            if (comment >= 0) {
                result.append(line.substring(comment)).append('\n');
                line = line.substring(0, comment).trim();
            }
            if (line.isEmpty()) continue;
            if (ended) throw invalid(index, "Comando apos PROGEND");
            String[] parts = line.split("\\s+", 2);
            String command = parts[0].toUpperCase(Locale.ROOT);
            String argument = parts.length > 1 ? parts[1].trim() : "";
            if (command.equals("IMF_PBL")) {
                if (header || argument.isEmpty()) throw invalid(index, "Cabecalho ICP invalido");
                header = true;
            } else {
                if (!header) throw invalid(index, "Comando antes do cabecalho ICP");
                switch (command) {
                    case "FASTABS", "MOVEABS" -> {
                        Map<Character, Double> axes = words(argument);
                        if (axes.isEmpty() || axes.keySet().stream().anyMatch(key -> key != 'X' && key != 'Y' && key != 'Z'))
                            throw invalid(index, "Eixos ICP invalidos");
                        result.append(command.equals("FASTABS") ? "G0" : "G1");
                        for (var axis : axes.entrySet()) {
                            integer(axis.getValue(), index, false);
                            result.append(' ').append(axis.getKey()).append(GCodeGenerator.fmt(axis.getValue() / 1000));
                        }
                        result.append('\n');
                    }
                    case "VEL" -> result.append('F').append(GCodeGenerator.fmt(integer(argument, index, true) * 60.0 / 1000)).append('\n');
                    case "WAIT" -> result.append("G4 P").append(GCodeGenerator.fmt(integer(argument, index, false) / 1000.0)).append('\n');
                    case "GETTOOL" -> result.append('T').append(integer(argument, index, false)).append('\n');
                    case "SPINDLE" -> {
                        if (argument.equalsIgnoreCase("OFF")) result.append("M5\n");
                        else {
                            var spindle = SPINDLE.matcher(argument);
                            if (!spindle.matches()) throw invalid(index, "Comando SPINDLE invalido");
                            result.append(spindle.group(1).equalsIgnoreCase("CW") ? "M3" : "M4");
                            if (spindle.group(2) != null) result.append(" S").append(integer(spindle.group(2), index, false));
                            result.append('\n');
                        }
                    }
                    case "PROGEND" -> {
                        if (!argument.isEmpty()) throw invalid(index, "PROGEND nao aceita parametros");
                        result.append("M30\n");
                        ended = true;
                    }
                    default -> throw invalid(index, "Comando ICP nao modelado: " + command + "; previa indisponivel");
                }
            }
            if (index % 256 == 0 || index == lines.size() - 1) progress.report((index + 1.0) / lines.size());
        }
        if (!header) throw new IllegalArgumentException("Cabecalho ICP ausente");
        progress.report(1);
        return result.toString();
    }

    private static int scaled(double value, double factor) {
        double scaled = value * factor;
        if (!Double.isFinite(scaled) || scaled < Integer.MIN_VALUE || scaled > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Valor fora do limite inteiro ICP");
        return (int) scaled; // Python's int() truncates towards zero.
    }

    private static int integer(String text, int index, boolean positive) {
        try {
            int value = Integer.parseInt(text);
            if (value < 0 || (positive && value == 0)) throw invalid(index, "Valor ICP deve ser " + (positive ? "positivo" : "nao negativo"));
            return value;
        } catch (NumberFormatException bad) { throw invalid(index, "Inteiro ICP invalido"); }
    }

    private static void integer(double value, int index, boolean positive) {
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || value != Math.rint(value)
                || (positive && value <= 0)) throw invalid(index, "Coordenada ICP deve ser inteira e dentro do limite");
    }

    private static Map<Character, Double> words(String line) {
        Map<Character, Double> result = new LinkedHashMap<>();
        var matcher = WORD.matcher(line);
        int cursor = 0;
        while (matcher.find()) {
            if (!line.substring(cursor, matcher.start()).isBlank()) throw new IllegalArgumentException("Programa invalido: " + line);
            cursor = matcher.end();
            char letter = Character.toUpperCase(matcher.group(1).charAt(0));
            double value = Double.parseDouble(matcher.group(2));
            if (!Double.isFinite(value) || result.putIfAbsent(letter, value) != null)
                throw new IllegalArgumentException("Palavra invalida ou duplicada: " + line);
        }
        if (!line.substring(cursor).isBlank()) throw new IllegalArgumentException("Programa invalido: " + line);
        return result;
    }

    private static IllegalArgumentException invalid(int index, String message) {
        return new IllegalArgumentException(message + " na linha ICP " + (index + 1));
    }
}
