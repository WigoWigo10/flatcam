package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Rebuilds a conservative XY preview from edited machine code. G0/G1 and
 * absolute/relative coordinates are supported; unknown coordinate-changing
 * G commands suppress the preview rather than leaving a misleading old plot.
 * This is not a machine-controller validator or a tool-diameter simulation.
 */
public final class GCodeToolpathParser {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final Pattern WORD = Pattern.compile("([A-Za-z])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    private static final int MAX_PREVIEW_SEGMENTS = 50_000;

    public record Result(Geometry travelGeometry, Geometry cutGeometry, String warning, int lineCount) {
        public boolean plotAvailable() {
            return warning == null;
        }
    }

    private GCodeToolpathParser() {
    }

    public static Result parse(String gcode, CancellationToken cancellation, ProgressCallback progress) {
        if (gcode == null || gcode.isBlank()) {
            throw new IllegalArgumentException("O G-code nao pode estar vazio.");
        }
        List<String> lines = gcode.lines().toList();
        List<Geometry> travel = new ArrayList<>();
        List<Geometry> cut = new ArrayList<>();
        boolean absolute = true;
        boolean metric = true;
        boolean havePosition = false;
        int motion = -1;
        double x = 0;
        double y = 0;
        double z = 0;
        String warning = null;
        progress.report(0);
        for (int index = 0; index < lines.size(); index++) {
            cancellation.throwIfCancellationRequested();
            String line = lines.get(index).replaceAll("\\([^)]*\\)", "");
            int semicolon = line.indexOf(';');
            if (semicolon >= 0) {
                line = line.substring(0, semicolon);
            }
            line = line.trim();
            if (line.isEmpty() || line.equals("%")) {
                continue;
            }
            Matcher matcher = WORD.matcher(line);
            int cursor = 0;
            Double newX = null;
            Double newY = null;
            Double newZ = null;
            while (matcher.find()) {
                String between = line.substring(cursor, matcher.start());
                if (!between.isBlank()) {
                    throw new IllegalArgumentException("G-code invalido na linha " + (index + 1) + ": " + between.trim());
                }
                cursor = matcher.end();
                double value;
                try {
                    value = Double.parseDouble(matcher.group(2));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Numero invalido na linha " + (index + 1), exception);
                }
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Numero fora do limite na linha " + (index + 1));
                }
                switch (Character.toUpperCase(matcher.group(1).charAt(0))) {
                    case 'G' -> {
                        int code = (int) value;
                        if (value != code) {
                            warning = "G-code com comando G fracionario: pre-visualizacao indisponivel.";
                        } else if (code == 0 || code == 1) {
                            motion = code;
                        } else if (code == 20) {
                            metric = false;
                        } else if (code == 21) {
                            metric = true;
                        } else if (code == 90) {
                            absolute = true;
                        } else if (code == 91) {
                            absolute = false;
                        } else if (code != 4 && code != 17 && code != 40 && code != 49
                                && code != 54 && code != 94) {
                            warning = "G-code com G" + code + ": pre-visualizacao indisponivel para este comando.";
                        }
                    }
                    case 'X' -> newX = value;
                    case 'Y' -> newY = value;
                    case 'Z' -> newZ = value;
                    default -> { /* Feed, spindle, tool, M code and line number do not change XY. */ }
                }
            }
            if (!line.substring(cursor).isBlank()) {
                throw new IllegalArgumentException("G-code invalido na linha " + (index + 1) + ": "
                        + line.substring(cursor).trim());
            }
            double nextX = newX == null ? x : absolute ? newX : x + newX;
            double nextY = newY == null ? y : absolute ? newY : y + newY;
            double nextZ = newZ == null ? z : absolute ? newZ : z + newZ;
            if (!Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextZ)) {
                throw new IllegalArgumentException("Coordenada fora do limite na linha " + (index + 1));
            }
            boolean movesXY = newX != null || newY != null;
            boolean lateral = movesXY && havePosition && (nextX != x || nextY != y) && motion >= 0;
            boolean plunge = !movesXY && newZ != null && motion == 1 && nextZ < 0 && havePosition;
            double radius = metric ? 0.01 : 0.0004;
            if (warning == null && (lateral || plunge)
                    && travel.size() + cut.size() >= MAX_PREVIEW_SEGMENTS) {
                warning = "Programa muito grande para pre-visualizacao detalhada.";
                travel.clear();
                cut.clear();
            }
            if (warning == null && lateral) {
                Geometry path = FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(x, y), new Coordinate(nextX, nextY)}).buffer(radius, 4);
                (motion == 0 || nextZ >= 0 ? travel : cut).add(path);
            } else if (warning == null && plunge) {
                cut.add(FACTORY.createPoint(new Coordinate(x, y)).buffer(radius, 4));
            }
            x = nextX;
            y = nextY;
            z = nextZ;
            havePosition |= movesXY;
            if (index % 256 == 0 || index == lines.size() - 1) {
                progress.report((index + 1.0) / lines.size());
            }
        }
        cancellation.throwIfCancellationRequested();
        progress.report(1);
        if (warning != null) {
            return new Result(null, null, warning, lines.size());
        }
        return new Result(FACTORY.createGeometryCollection(travel.toArray(Geometry[]::new)),
                FACTORY.createGeometryCollection(cut.toArray(Geometry[]::new)), null, lines.size());
    }
}
