package org.flatcam.cam.convert;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * app_Main.py's Edit &gt; Conversion: Any to Geometry / Gerber / Excellon, and Single to Multi-Geo and back.
 *
 * <p>Differences from Python: a Geometry made only of lines becomes copper in the Gerber by stroking each line with
 * its tool's diameter (Python stores the bare lines as "solid", which has no area); tools are told apart at four
 * decimals everywhere (Python mixes exact and rounded comparisons); and Single to Multi-Geo needs the tool diameter
 * from the caller because a single-tool Geometry here does not carry one.
 */
public final class ObjectConversion {

    private static final int STEPS = 16;
    private static final double DECIMALS = 1e4;
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private ObjectConversion() {
    }

    // --- to Geometry ----------------------------------------------------------------------------------------------

    /** The copper (or holes) of a Gerber or Excellon as filled polygons. */
    public static Geometry solidToGeometry(Geometry solid) {
        if (solid == null || solid.isEmpty()) {
            throw new IllegalArgumentException("O objeto nao tem geometria para converter");
        }
        List<Geometry> parts = new ArrayList<>();
        flatten(solid, parts);
        return FACTORY.buildGeometry(parts);
    }

    // --- to Gerber ----------------------------------------------------------------------------------------------

    /** Each tool becomes a round aperture (codes from 10); drills are flashes and slots are strokes of that aperture. */
    public static GerberImage excellonToGerber(ExcellonImage excellon) {
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        Map<Integer, String> codeByTool = new LinkedHashMap<>();
        int code = 10;
        for (Map.Entry<Integer, Double> tool : new java.util.TreeMap<>(excellon.toolDiameters()).entrySet()) {
            apertures.put(String.valueOf(code), Aperture.circle(tool.getValue()));
            codeByTool.put(tool.getKey(), String.valueOf(code));
            code++;
        }
        List<GerberShape> shapes = new ArrayList<>();
        for (ExcellonImage.Drill drill : excellon.drills()) {
            Double diameter = excellon.toolDiameters().get(drill.toolId());
            if (diameter == null) {
                continue;
            }
            Point centre = FACTORY.createPoint(new Coordinate(drill.x(), drill.y()));
            shapes.add(new GerberShape(codeByTool.get(drill.toolId()), centre.buffer(diameter / 2, STEPS), false, centre));
        }
        for (ExcellonImage.Slot slot : excellon.slots()) {
            Double diameter = excellon.toolDiameters().get(slot.toolId());
            if (diameter == null) {
                continue;
            }
            LineString path = FACTORY.createLineString(new Coordinate[] {new Coordinate(slot.x1(), slot.y1()),
                    new Coordinate(slot.x2(), slot.y2())});
            shapes.add(new GerberShape(codeByTool.get(slot.toolId()), path.buffer(diameter / 2, STEPS), false, path));
        }
        return build(excellon.units(), apertures, shapes, "O Excellon nao tem furos nem slots");
    }

    /**
     * Polygons become regions; lines become strokes of a round aperture as wide as the tool they belong to (a
     * Geometry without tools cannot say how wide, so its lines are skipped).
     */
    public static GerberImage geometryToGerber(String units, Geometry geometry, List<ToolGeometry> tools) {
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        List<GerberShape> shapes = new ArrayList<>();
        if (tools.isEmpty()) {
            addShapes(geometry, 0, apertures, shapes);
        } else {
            for (ToolGeometry tool : tools) {
                addShapes(tool.geometry(), tool.toolDiameter(), apertures, shapes);
            }
        }
        return build(units, apertures, shapes,
                "A Geometry so tem linhas e nao ha diametro de ferramenta: nada vira cobre");
    }

    private static void addShapes(Geometry geometry, double lineWidth, Map<String, Aperture> apertures,
                                  List<GerberShape> shapes) {
        List<Geometry> parts = new ArrayList<>();
        flatten(geometry, parts);
        for (Geometry part : parts) {
            if (part instanceof Polygon polygon && !polygon.isEmpty()) {
                shapes.add(new GerberShape(GerberShape.REGION_APERTURE, polygon, false, polygon.getExteriorRing()));
            } else if (part instanceof LineString line && !line.isEmpty() && lineWidth > 0) {
                String code = null;
                for (Map.Entry<String, Aperture> entry : apertures.entrySet()) {
                    if (entry.getValue().kind == ApertureKind.CIRCLE
                            && Math.abs(entry.getValue().width - lineWidth) < 1 / DECIMALS) {
                        code = entry.getKey();
                        break;
                    }
                }
                if (code == null) {
                    code = String.valueOf(10 + apertures.size());
                    apertures.put(code, Aperture.circle(lineWidth));
                }
                shapes.add(new GerberShape(code, line.buffer(lineWidth / 2, STEPS), false, line));
            }
        }
    }

    private static GerberImage build(String units, Map<String, Aperture> apertures, List<GerberShape> shapes,
                                     String emptyMessage) {
        if (shapes.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        GerberImage empty = GerberImage.of(units, Map.of(), FACTORY.createPolygon(), FACTORY.createGeometryCollection(),
                Map.of(), List.of());
        return empty.withEditedShapes(shapes, apertures, CancellationToken.none(), ProgressCallback.none());
    }

    // --- to Excellon --------------------------------------------------------------------------------------------

    /**
     * Each closed shape becomes a drill at its centre, as wide as the smaller side of its bounding box. Lines are
     * ignored.
     */
    public static ExcellonImage geometryToExcellon(String units, Geometry geometry) {
        Holes holes = new Holes();
        List<Geometry> parts = new ArrayList<>();
        flatten(geometry, parts);
        for (Geometry part : parts) {
            Geometry shape = part instanceof LineString line && line.isClosed() && line.getNumPoints() >= 4
                    ? FACTORY.createPolygon(line.getCoordinates()) : part;
            if (shape instanceof Polygon polygon && !polygon.isEmpty()) {
                holes.drill(polygon);
            }
        }
        return holes.image(units, "A Geometry nao tem formas fechadas para virarem furos");
    }

    /**
     * Flashes become drills (centre and smaller side of the pad); a stroke of exactly two points becomes a slot as wide
     * as its aperture. Other shapes are ignored.
     */
    public static ExcellonImage gerberToExcellon(GerberImage gerber) {
        if (gerber.shapes().isEmpty()) {
            throw new IllegalArgumentException("O Gerber nao tem formas individuais (flashes) para virarem furos");
        }
        Holes holes = new Holes();
        for (GerberShape shape : gerber.shapes()) {
            if (shape.clear() || shape.geometry().isEmpty()) {
                continue;
            }
            if (shape.followGeometry() instanceof Point) {
                holes.drill(shape.geometry());
            } else if (shape.followGeometry() instanceof LineString path && path.getNumPoints() == 2) {
                Aperture aperture = gerber.apertures().get(shape.apertureCode());
                if (aperture != null && aperture.width > 0) {
                    holes.slot(path.getCoordinateN(0), path.getCoordinateN(1), aperture.width);
                }
            }
        }
        return holes.image(gerber.units(), "O Gerber nao tem flashes nem tracos de 2 pontos para virarem furos");
    }

    /** Collects drills and slots, sharing one tool per diameter (four decimals, ids from 1). */
    private static final class Holes {
        private final Map<Long, Integer> toolByDiameter = new LinkedHashMap<>();
        private final Map<Integer, Double> diameters = new LinkedHashMap<>();
        private final List<ExcellonImage.Drill> drills = new ArrayList<>();
        private final List<ExcellonImage.Slot> slots = new ArrayList<>();
        private final List<Geometry> discs = new ArrayList<>();

        private int tool(double diameter) {
            return toolByDiameter.computeIfAbsent(Math.round(diameter * DECIMALS), key -> {
                int id = diameters.size() + 1;
                // Stored at the four decimals the tools are told apart by: no 1.8000000000000114 from float noise.
                diameters.put(id, key / DECIMALS);
                return id;
            });
        }

        void drill(Geometry shape) {
            Envelope box = shape.getEnvelopeInternal();
            double diameter = Math.min(box.getWidth(), box.getHeight());
            if (!(diameter > 0)) {
                return;
            }
            Coordinate centre = shape.getCentroid().getCoordinate();
            drills.add(new ExcellonImage.Drill(tool(diameter), centre.x, centre.y));
            discs.add(FACTORY.createPoint(centre).buffer(diameter / 2, STEPS));
        }

        void slot(Coordinate from, Coordinate to, double diameter) {
            slots.add(new ExcellonImage.Slot(tool(diameter), from.x, from.y, to.x, to.y));
            discs.add(FACTORY.createLineString(new Coordinate[] {from, to}).buffer(diameter / 2, STEPS));
        }

        ExcellonImage image(String units, String emptyMessage) {
            if (drills.isEmpty() && slots.isEmpty()) {
                throw new IllegalArgumentException(emptyMessage);
            }
            Geometry solid = discs.size() == 1 ? discs.get(0) : OverlayNGRobust.union(discs);
            return ExcellonImage.of(units, diameters, drills, slots, solid);
        }
    }

    // --- Single <-> Multi-Geo --------------------------------------------------------------------------------------

    /** A single-tool Geometry becomes a multi-tool one with its geometry under one tool of {@code diameter}. */
    public static List<ToolGeometry> singleToMulti(Geometry geometry, double diameter) {
        if (!(diameter > 0) || !Double.isFinite(diameter)) {
            throw new IllegalArgumentException("O diametro da ferramenta deve ser positivo");
        }
        if (geometry == null || geometry.isEmpty()) {
            throw new IllegalArgumentException("A Geometry esta vazia");
        }
        return List.of(new ToolGeometry(diameter, geometry));
    }

    /** All tools' geometry together as one geometry (the tool information is lost, as in Python). */
    public static Geometry multiToSingle(List<ToolGeometry> tools) {
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("A Geometry nao e multi-ferramenta");
        }
        List<Geometry> parts = new ArrayList<>();
        for (ToolGeometry tool : tools) {
            flatten(tool.geometry(), parts);
        }
        return FACTORY.buildGeometry(parts);
    }

    /** The simple parts (polygons, lines, points) of any nesting of collections and multi-geometries. */
    private static void flatten(Geometry geometry, List<Geometry> parts) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        if (geometry instanceof org.locationtech.jts.geom.GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                flatten(geometry.getGeometryN(i), parts);
            }
        } else {
            parts.add(geometry);
        }
    }
}
