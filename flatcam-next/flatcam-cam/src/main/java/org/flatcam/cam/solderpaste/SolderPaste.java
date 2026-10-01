package org.flatcam.cam.solderpaste;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.flatcam.cam.geometry.ToolGeometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * appTools/ToolSolderPaste.py + camlib's generate_gcode_from_solderpaste_geo + the Paste_1 preprocessor: dispensing
 * paths for the pads of a solder-paste mask Gerber, and the G-code that drives a paste dispenser along them.
 *
 * <p>Each pad gets one straight line, from the widest nozzle that fits it: along the long side of a rectangular pad,
 * or along its longer diagonal, shrunk by half the nozzle. Pads that no nozzle fits are counted as unserved.
 *
 * <p>Differences from Python: a pad too small for a nozzle no longer inherits the previous pad's line (a Python
 * variable that leaks between iterations), the dispense parameters are one set for all nozzles, the dwell and spindle
 * speeds are written whenever they are above zero, and the program ends by lifting to the tool-change height only.
 */
public final class SolderPaste {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** Z heights, feed rates and dispenser timing; Python's defaults are in {@link #defaults()}. */
    public record Parameters(double zStart, double zDispense, double zStop, double zTravel, double zToolchange,
                             double xToolchange, double yToolchange, double feedXY, double feedZ,
                             double feedZDispense, double speedForward, double dwellForward, double speedReverse,
                             double dwellReverse) {
    }

    public static Parameters defaults() {
        return new Parameters(0.05, 0.1, 0.05, 0.1, 1.0, 0.0, 0.0, 150, 150, 1.0, 300, 1, 200, 1);
    }

    /** The dispensing lines of each nozzle (widest first) and how many pads no nozzle could serve. */
    public record Generated(List<ToolGeometry> tools, int unserved, int pads) {
    }

    private SolderPaste() {
    }

    public static Generated generateGeometry(Geometry mask, List<Double> nozzles, String units) {
        if (nozzles == null || nozzles.stream().noneMatch(n -> n != null && n > 0)) {
            throw new IllegalArgumentException("Nao ha ferramentas de bico na tabela");
        }
        List<Polygon> pads = new ArrayList<>();
        collectPads(mask, pads);
        if (pads.isEmpty()) {
            throw new IllegalArgumentException("Cancelado: o arquivo esta vazio, nao tem geometria");
        }
        int decimals = "IN".equalsIgnoreCase(units) ? 2 : 1;
        List<Double> sorted = nozzles.stream().filter(n -> n != null && n > 0).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        List<ToolGeometry> tools = new ArrayList<>();
        List<Polygon> work = pads;
        for (double nozzle : sorted) {
            double offset = nozzle / 2;
            List<Geometry> lines = new ArrayList<>();
            List<Polygon> rest = new ArrayList<>();
            for (Polygon pad : work) {
                Geometry line = lineFor(pad, offset, decimals);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                } else {
                    rest.add(pad);
                }
            }
            if (!lines.isEmpty()) {
                tools.add(new ToolGeometry(nozzle, FACTORY.buildGeometry(lines)));
            }
            work = rest;
            if (work.isEmpty()) {
                break;
            }
        }
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("Nenhum pad cabe em nenhum dos bicos: use bicos mais finos");
        }
        return new Generated(tools, work.size(), pads.size());
    }

    private static void collectPads(Geometry geometry, List<Polygon> pads) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof Polygon polygon) {
                if (!polygon.isEmpty()) {
                    // Python expands polygons to their exteriors, so holes are ignored.
                    pads.add(FACTORY.createPolygon(polygon.getExteriorRing().getCoordinates()));
                }
            } else if (part != geometry) {
                collectPads(part, pads);
            }
        }
    }

    /** The dispensing line for one pad and a nozzle radius, or null when the pad is too small for the nozzle. */
    private static Geometry lineFor(Polygon pad, double offset, int decimals) {
        Envelope box = pad.getEnvelopeInternal();
        Geometry diagonalUp = intersect(line(box.getMinX(), box.getMinY(), box.getMaxX(), box.getMaxY()), pad);
        Geometry diagonalDown = intersect(line(box.getMinX(), box.getMaxY(), box.getMaxX(), box.getMinY()), pad);
        double up = round(diagonalUp.getLength(), decimals);
        double down = round(diagonalDown.getLength(), decimals);
        Geometry candidate;
        if (up == down) {
            double width = box.getWidth();
            double height = box.getHeight();
            if (offset >= width / 2 || offset >= height / 2) {
                return null;
            }
            double midY = box.getMinY() + height / 2;
            double midX = box.getMinX() + width / 2;
            candidate = width > height ? line(box.getMinX(), midY, box.getMaxX(), midY)
                    : line(midX, box.getMinY(), midX, box.getMaxY());
        } else {
            candidate = up > down ? diagonalUp : diagonalDown;
        }
        Geometry eroded = pad.buffer(-offset);
        if (eroded.isEmpty()) {
            return null;
        }
        return intersect(candidate, eroded);
    }

    private static Geometry intersect(Geometry a, Geometry b) {
        if (a.isEmpty() || b.isEmpty()) {
            return FACTORY.createLineString();
        }
        return OverlayNGRobust.overlay(a, b, OverlayNG.INTERSECTION);
    }

    private static LineString line(double x0, double y0, double x1, double y1) {
        return FACTORY.createLineString(new Coordinate[] {new Coordinate(x0, y0), new Coordinate(x1, y1)});
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    // --- G-code -------------------------------------------------------------------------------------------------

    /** One nozzle's program and the paths in the order they are dispensed (for the preview). */
    public record Program(String gcode, List<LineString> paths) {
    }

    /** G-code of every nozzle, one after the other, each with the header and tool change of Paste_1. */
    public static Program generateGCode(List<ToolGeometry> tools, Parameters p, String units, double[] bounds) {
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("Nao ha dados de ferramenta na geometria de pasta");
        }
        StringBuilder gcode = new StringBuilder();
        List<LineString> ordered = new ArrayList<>();
        int toolNumber = 0;
        for (ToolGeometry tool : tools) {
            toolNumber++;
            List<LineString> paths = new ArrayList<>();
            flatten(tool.geometry(), paths);
            if (paths.isEmpty()) {
                continue;
            }
            header(gcode, tool.toolDiameter(), p, units, bounds);
            gcode.append("M05\n");
            toolChange(gcode, toolNumber, tool.toolDiameter(), p);
            double[] current = {0, 0};
            List<LineString> remaining = new ArrayList<>(paths);
            while (!remaining.isEmpty()) {
                int best = 0;
                boolean reverse = false;
                double bestDistance = Double.MAX_VALUE;
                for (int i = 0; i < remaining.size(); i++) {
                    Coordinate first = remaining.get(i).getCoordinateN(0);
                    Coordinate last = remaining.get(i).getCoordinateN(remaining.get(i).getNumPoints() - 1);
                    double toFirst = Math.hypot(first.x - current[0], first.y - current[1]);
                    double toLast = Math.hypot(last.x - current[0], last.y - current[1]);
                    // Python prefers the first point when both ends are equally near.
                    if (toFirst < bestDistance) {
                        bestDistance = toFirst;
                        best = i;
                        reverse = false;
                    }
                    if (toLast < bestDistance) {
                        bestDistance = toLast;
                        best = i;
                        reverse = true;
                    }
                }
                LineString path = remaining.remove(best);
                if (reverse) {
                    path = path.reverse();
                }
                ordered.add(path);
                dispense(gcode, path, p);
                Coordinate end = path.getCoordinateN(path.getNumPoints() - 1);
                current = new double[] {end.x, end.y};
            }
            gcode.append("G00 Z").append(f4(p.zTravel())).append('\n');
            gcode.append("G00 Z").append(f2(p.zToolchange())).append("\n\n");
        }
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("Cancelado: a geometria nao tem caminhos");
        }
        return new Program(gcode.toString(), ordered);
    }

    private static void flatten(Geometry geometry, List<LineString> paths) {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part instanceof LineString line) {
                if (!line.isEmpty() && line.getNumPoints() >= 2) {
                    paths.add(line);
                }
            } else if (part instanceof Polygon polygon) {
                paths.add(polygon.getExteriorRing());
            } else if (part != geometry) {
                flatten(part, paths);
            }
        }
    }

    private static void header(StringBuilder out, double nozzle, Parameters p, String units, double[] b) {
        String unit = " " + ("IN".equalsIgnoreCase(units) ? "in" : "mm");
        out.append("(TOOL DIAMETER: ").append(nozzle).append(unit).append(")\n");
        out.append("(Feedrate_XY: ").append(num(p.feedXY())).append(unit).append("/min)\n");
        out.append("(Feedrate_Z: ").append(num(p.feedZ())).append(unit).append("/min)\n");
        out.append("(Feedrate_Z_Dispense: ").append(num(p.feedZDispense())).append(unit).append("/min)\n");
        out.append("(Z_Dispense_Start: ").append(num(p.zStart())).append(unit).append(")\n");
        out.append("(Z_Dispense: ").append(num(p.zDispense())).append(unit).append(")\n");
        out.append("(Z_Dispense_Stop: ").append(num(p.zStop())).append(unit).append(")\n");
        out.append("(Z_Travel: ").append(num(p.zTravel())).append(unit).append(")\n");
        out.append("(Z Toolchange: ").append(num(p.zToolchange())).append(unit).append(")\n");
        out.append(String.format(Locale.ROOT, "(X,Y Toolchange: %.4f, %.4f%s)%n", p.xToolchange(), p.yToolchange(),
                unit));
        out.append("(Preprocessor SolderPaste Dispensing Geometry: Paste_1)\n\n");
        if (b != null) {
            out.append(String.format(Locale.ROOT, "(X range: %9.4f ... %9.4f %s)%n", b[0], b[2], unit));
            out.append(String.format(Locale.ROOT, "(Y range: %9.4f ... %9.4f %s)%n%n", b[1], b[3], unit));
        }
        out.append("(Spindle Speed FWD: ").append(num(p.speedForward())).append(" RPM)\n");
        out.append("(Spindle Speed REV: ").append(num(p.speedReverse())).append(" RPM)\n");
        out.append("(Dwell FWD: ").append(num(p.dwellForward())).append(" RPM)\n");
        out.append("(Dwell REV: ").append(num(p.dwellReverse())).append(" RPM)\n");
        out.append("IN".equalsIgnoreCase(units) ? "G20\n" : "G21\n");
        out.append("G90\nG94\n");
    }

    private static void toolChange(StringBuilder out, int tool, double nozzle, Parameters p) {
        out.append("\nG00 Z").append(f4(p.zToolchange())).append('\n');
        out.append("G00 X").append(f4(p.xToolchange())).append(" Y").append(f4(p.yToolchange())).append('\n');
        out.append('T').append(tool).append("\nM6\n");
        out.append("(MSG, Change to Tool with Nozzle Dia = ").append(String.format(Locale.ROOT, "%.4f", nozzle))
                .append(")\nM0\n");
        out.append("G00 Z").append(f4(p.zToolchange())).append('\n');
    }

    private static void dispense(StringBuilder out, LineString path, Parameters p) {
        Coordinate first = path.getCoordinateN(0);
        out.append("G00 X").append(f4(first.x)).append(" Y").append(f4(first.y)).append('\n');
        out.append("G00 Z").append(f4(p.zTravel())).append('\n');
        out.append("G01 F").append(f2(p.feedZ())).append('\n');
        out.append("G01 Z").append(f4(p.zStart())).append('\n');
        spindle(out, "M03", p.speedForward());
        dwell(out, p.dwellForward());
        out.append("G01 F").append(f2(p.feedZDispense())).append('\n');
        out.append("G01 Z").append(f4(p.zDispense())).append('\n');
        out.append("G01 F").append(f2(p.feedXY())).append('\n');
        for (int i = 1; i < path.getNumPoints(); i++) {
            Coordinate c = path.getCoordinateN(i);
            out.append("G01 X").append(f4(c.x)).append(" Y").append(f4(c.y)).append('\n');
        }
        out.append("M05\n");
        spindle(out, "M04", p.speedReverse());
        out.append("G01 Z").append(f4(p.zStop())).append('\n');
        out.append("M05\n");
        dwell(out, p.dwellReverse());
        out.append("G01 F").append(f2(p.feedZ())).append('\n');
        out.append("G00 Z").append(f4(p.zTravel())).append('\n');
    }

    private static void spindle(StringBuilder out, String code, double speed) {
        out.append(code);
        if (speed > 0) {
            out.append(" S").append(String.format(Locale.ROOT, "%.1f", speed));
        }
        out.append('\n');
    }

    private static void dwell(StringBuilder out, double seconds) {
        if (seconds > 0) {
            out.append("G4 P").append(String.format(Locale.ROOT, "%.1f", seconds)).append('\n');
        }
    }

    private static String f4(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String f2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String num(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e12 ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
