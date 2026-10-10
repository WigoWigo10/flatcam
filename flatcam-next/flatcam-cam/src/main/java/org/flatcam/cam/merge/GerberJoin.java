package org.flatcam.cam.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.gerber.Aperture;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.gerber.LazyApertureGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.flatcam.cam.geometry.ParallelGeometry;

/**
 * Edit > Join Objects > "Join Gerber(s) -> Gerber" (Python's {@code GerberObject.merge}): the copper
 * of every selected Gerber becomes one new Gerber. Apertures keep their D-code unless another Gerber
 * already uses that code for a different aperture, in which case the newcomer gets the next free code
 * (Python renumbers every duplicate; identical apertures are shared here instead). The solid is the
 * union of the sources' solids, so each source's own clear-polarity shapes stay applied.
 */
public final class GerberJoin {

    private GerberJoin() {
    }

    public static GerberImage join(List<GerberImage> images) {
        return join(images, false);
    }

    /**
     * ToolPanelize keeps a list of translated solids, not an eager union.
     * Keep that representation so subsequent NCC can apply the legacy union
     * order once, without losing the original endpoints. Ordinary Join Objects
     * retains its resolved union policy.
     */
    public static GerberImage panelCopies(List<GerberImage> images) {
        return join(images, true);
    }

    private static GerberImage join(List<GerberImage> images, boolean preserveCopies) {
        if (images.size() < 2) {
            throw new IllegalArgumentException("At least two Gerber objects are required to join them");
        }
        String units = images.get(0).units();
        for (GerberImage image : images) {
            if (!units.equals(image.units())) {
                throw new IllegalArgumentException("Cannot join Gerbers with different units ("
                        + units + " and " + image.units() + "); convert them first");
            }
        }
        Map<String, Aperture> apertures = new LinkedHashMap<>();
        Map<String, List<Geometry>> aperturePieces = new LinkedHashMap<>();
        List<GerberShape> shapes = new ArrayList<>();
        List<Geometry> solids = new ArrayList<>();
        List<Geometry> follows = new ArrayList<>();
        // The editor replays shapes in order: a later Gerber's clear shapes would then also erase the
        // earlier Gerbers' copper, unlike the joined solid. Such a join is left without editable shapes.
        boolean editable = true;
        GeometryFactory factory = images.get(0).solidGeometry().getFactory();

        for (int index = 0; index < images.size(); index++) {
            GerberImage image = images.get(index);
            Map<String, String> codes = new HashMap<>();
            for (Map.Entry<String, Aperture> entry : image.apertures().entrySet()) {
                codes.put(entry.getKey(), placeAperture(apertures, entry.getKey(), entry.getValue()));
            }
            if (image.shapes().isEmpty() && !image.solidGeometry().isEmpty()) {
                editable = false;
            }
            for (GerberShape shape : image.shapes()) {
                if (index > 0 && shape.clear()) {
                    editable = false;
                }
                shapes.add(new GerberShape(codes.getOrDefault(shape.apertureCode(), shape.apertureCode()),
                        shape.geometry(), shape.clear(), shape.followGeometry()));
            }
            for (Map.Entry<String, Geometry> entry : image.apertureGeometry().entrySet()) {
                aperturePieces.computeIfAbsent(codes.getOrDefault(entry.getKey(), entry.getKey()),
                        key -> new ArrayList<>()).add(entry.getValue());
            }
            if (!image.solidGeometry().isEmpty()) {
                solids.add(image.solidGeometry());
            }
            if (image.followGeometry() != null && !image.followGeometry().isEmpty()) {
                follows.add(image.followGeometry());
            }
        }
        Geometry solid = preserveCopies ? factory.createGeometryCollection(solids.toArray(Geometry[]::new))
                : solids.isEmpty() ? factory.createGeometryCollection()
                : solids.size() == 1 ? solids.get(0) : ParallelGeometry.unionGrouped(solids);
        Geometry follow = factory.buildGeometry(follows);
        return GerberImage.of(units, apertures, solid, follow, new LazyApertureGeometry(aperturePieces),
                editable ? shapes : List.of());
    }

    /** The code {@code aperture} goes by in the joined Gerber. */
    private static String placeAperture(Map<String, Aperture> target, String code, Aperture aperture) {
        Aperture existing = target.get(code);
        if (existing == null) {
            target.put(code, aperture);
            return code;
        }
        if (same(existing, aperture)) {
            return code;
        }
        for (Map.Entry<String, Aperture> entry : target.entrySet()) {
            if (same(entry.getValue(), aperture)) {
                return entry.getKey();
            }
        }
        int next = 10;
        for (String key : target.keySet()) {
            try {
                next = Math.max(next, Integer.parseInt(key) + 1);
            } catch (NumberFormatException ignored) {
                // not a numeric D-code
            }
        }
        String fresh = Integer.toString(next);
        target.put(fresh, aperture);
        return fresh;
    }

    private static boolean same(Aperture a, Aperture b) {
        if (a == b) {
            return true;
        }
        if (a.kind != b.kind || a.kind == ApertureKind.MACRO) {
            return false;
        }
        return a.width == b.width && a.height == b.height && a.polygonVertices() == b.polygonVertices()
                && a.polygonRotation() == b.polygonRotation();
    }
}
