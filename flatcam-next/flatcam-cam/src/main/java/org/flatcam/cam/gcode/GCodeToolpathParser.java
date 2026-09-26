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
 * G2/G3 arcs in the XY plane, with absolute/relative coordinates, are
 * supported. Unknown coordinate-changing G commands suppress the preview
 * rather than leaving a misleading old plot.
 * This is not a machine-controller validator or a tool-diameter simulation.
 */
public final class GCodeToolpathParser {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final Pattern WORD = Pattern.compile("([A-Za-z])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))");
    private static final int MAX_PREVIEW_SEGMENTS = 50_000;

    public record Result(Geometry travelGeometry, Geometry cutGeometry, String warning,
                         int lineCount, String units) {
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
        boolean absoluteArcCenter = false;
        boolean metric = true;
        Boolean lastMetric = null;
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
            if (index == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
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
            Double arcI = null;
            Double arcJ = null;
            Double arcR = null;
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
                        if (value == 90.1) {
                            absoluteArcCenter = true;
                        } else if (value == 91.1) {
                            absoluteArcCenter = false;
                        } else if (value != code) {
                            warning = "G-code com comando G fracionario: pre-visualizacao indisponivel.";
                        } else if (code >= 0 && code <= 3) {
                            motion = code;
                        } else if (code == 20) {
                            metric = false;
                            if (lastMetric != null && lastMetric && havePosition) {
                                warning = "Troca de unidades no mesmo programa: pre-visualizacao indisponivel.";
                            }
                            lastMetric = false;
                        } else if (code == 21) {
                            metric = true;
                            if (lastMetric != null && !lastMetric && havePosition) {
                                warning = "Troca de unidades no mesmo programa: pre-visualizacao indisponivel.";
                            }
                            lastMetric = true;
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
                    case 'I' -> arcI = value;
                    case 'J' -> arcJ = value;
                    case 'R' -> arcR = value;
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
            if ((motion == 2 || motion == 3) && !havePosition
                    && (movesXY || arcI != null || arcJ != null || arcR != null)) {
                warning = "Arco sem posicao XY inicial: pre-visualizacao indisponivel.";
            }
            boolean arcMove = (motion == 2 || motion == 3) && havePosition
                    && (movesXY || arcI != null || arcJ != null || arcR != null);
            boolean lateral = movesXY && havePosition && (nextX != x || nextY != y)
                    && motion >= 0 && motion <= 1;
            boolean plunge = !movesXY && newZ != null && motion == 1 && nextZ < 0 && havePosition;
            double radius = metric ? 0.01 : 0.0004;
            if (warning == null && (lateral || plunge || arcMove)
                    && travel.size() + cut.size() >= MAX_PREVIEW_SEGMENTS) {
                warning = "Programa muito grande para pre-visualizacao detalhada.";
                travel.clear();
                cut.clear();
            }
            if (warning == null && arcMove) {
                if ((z >= 0) != (nextZ >= 0)) {
                    warning = "Arco cruzando Z=0: pre-visualizacao indisponivel.";
                } else {
                    try {
                        Geometry path = arcPath(x, y, nextX, nextY, arcI, arcJ, arcR,
                                absoluteArcCenter, motion == 2, radius);
                        (nextZ >= 0 ? travel : cut).add(path);
                    } catch (IllegalArgumentException invalidArc) {
                        warning = "Arco invalido na linha " + (index + 1) + ": " + invalidArc.getMessage();
                    }
                }
            } else if (warning == null && lateral) {
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
            return new Result(null, null, warning, lines.size(), metric ? "MM" : "IN");
        }
        return new Result(FACTORY.createGeometryCollection(travel.toArray(Geometry[]::new)),
                FACTORY.createGeometryCollection(cut.toArray(Geometry[]::new)), null,
                lines.size(), metric ? "MM" : "IN");
    }

    private static Geometry arcPath(double startX, double startY, double endX, double endY,
                                    Double i, Double j, Double r, boolean absoluteCenter,
                                    boolean clockwise, double strokeRadius) {
        if (r != null && (i != null || j != null) || r == null && i == null && j == null) {
            throw new IllegalArgumentException("informe I/J ou R, nao ambos");
        }
        double cx;
        double cy;
        if (r != null) {
            double chord = Math.hypot(endX - startX, endY - startY);
            double magnitude = Math.abs(r);
            if (chord == 0 || magnitude == 0 || chord > 2 * magnitude + 1e-9) {
                throw new IllegalArgumentException("raio R incompatível com os extremos");
            }
            double half = chord / 2;
            double height = Math.sqrt(Math.max(0, magnitude * magnitude - half * half));
            double midX = (startX + endX) / 2;
            double midY = (startY + endY) / 2;
            double normalX = -(endY - startY) / chord;
            double normalY = (endX - startX) / chord;
            double ax = midX + normalX * height;
            double ay = midY + normalY * height;
            double bx = midX - normalX * height;
            double by = midY - normalY * height;
            double sweepA = Math.abs(sweep(startX, startY, endX, endY, ax, ay, clockwise));
            double sweepB = Math.abs(sweep(startX, startY, endX, endY, bx, by, clockwise));
            boolean chooseA = r > 0 ? sweepA <= sweepB : sweepA >= sweepB;
            cx = chooseA ? ax : bx;
            cy = chooseA ? ay : by;
        } else {
            if (absoluteCenter && (i == null || j == null)) {
                throw new IllegalArgumentException("centro absoluto exige I e J");
            }
            cx = absoluteCenter ? i : startX + (i == null ? 0 : i);
            cy = absoluteCenter ? j : startY + (j == null ? 0 : j);
        }
        double startRadius = Math.hypot(startX - cx, startY - cy);
        double endRadius = Math.hypot(endX - cx, endY - cy);
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(startRadius)
                || startRadius <= 0 || Math.abs(startRadius - endRadius) > Math.max(1e-5, startRadius * 1e-4)) {
            throw new IllegalArgumentException("centro e extremos nao definem o mesmo raio");
        }
        double startAngle = Math.atan2(startY - cy, startX - cx);
        double angle = sweep(startX, startY, endX, endY, cx, cy, clockwise);
        int steps = Math.max(8, (int) Math.ceil(Math.abs(angle) / (Math.PI / 36)));
        Coordinate[] points = new Coordinate[steps + 1];
        for (int step = 0; step <= steps; step++) {
            double radians = startAngle + angle * step / steps;
            points[step] = new Coordinate(cx + startRadius * Math.cos(radians),
                    cy + startRadius * Math.sin(radians));
        }
        points[0] = new Coordinate(startX, startY);
        points[steps] = new Coordinate(endX, endY);
        return FACTORY.createLineString(points).buffer(strokeRadius, 4);
    }

    private static double sweep(double startX, double startY, double endX, double endY,
                                double cx, double cy, boolean clockwise) {
        if (startX == endX && startY == endY) {
            return clockwise ? -2 * Math.PI : 2 * Math.PI;
        }
        double start = Math.atan2(startY - cy, startX - cx);
        double end = Math.atan2(endY - cy, endX - cx);
        double delta = end - start;
        if (clockwise) {
            if (delta >= 0) {
                delta -= 2 * Math.PI;
            }
        } else if (delta <= 0) {
            delta += 2 * Math.PI;
        }
        return delta;
    }
}
