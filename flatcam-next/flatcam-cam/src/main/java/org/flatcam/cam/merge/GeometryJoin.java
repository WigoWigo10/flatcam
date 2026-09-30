package org.flatcam.cam.merge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

/**
 * Edit > Join Objects > "Join Geo/Gerber/Exc -> Geo" (Python's {@code on_edit_join} and
 * {@code GeometryObject.merge}): the shapes of every selected object become one new Geometry.
 * Geometry objects are either single (no per-tool split) or multi-tool; Python refuses to mix the two
 * because the G-code of a converted job may not be what was expected. Gerbers and Excellons count as
 * single. With {@code fuseTools} (Python's {@code geometry_merge_fuse_tools}, on by default) the tools of
 * multi-tool Geometries that share diameter and profile become one tool.
 */
public final class GeometryJoin {

    /** One selected object: its shapes and, for a multi-tool Geometry, the per-tool split. */
    public record Source(String units, Geometry geometry, boolean strokeOnly, List<ToolGeometry> tools) {
        public Source {
            tools = List.copyOf(tools);
        }
    }

    public record Joined(Geometry geometry, boolean strokeOnly, List<ToolGeometry> tools) {
    }

    private GeometryJoin() {
    }

    public static Joined join(List<Source> sources, boolean fuseTools) {
        if (sources.size() < 2) {
            throw new IllegalArgumentException("At least two objects are required to join them");
        }
        String units = sources.get(0).units();
        boolean anyMulti = false;
        boolean anySingle = false;
        for (Source source : sources) {
            if (!units.equals(source.units())) {
                throw new IllegalArgumentException("Cannot join objects with different units ("
                        + units + " and " + source.units() + "); convert them first");
            }
            if (source.tools().isEmpty()) {
                anySingle = true;
            } else {
                anyMulti = true;
            }
        }
        if (anyMulti && anySingle) {
            throw new IllegalArgumentException("The Geometry objects are of different types: at least one is "
                    + "multi-tool and another is single. Convert one to the other and retry");
        }
        GeometryFactory factory = sources.get(0).geometry().getFactory();
        boolean strokeOnly = sources.stream().allMatch(Source::strokeOnly);
        if (!anyMulti) {
            List<Geometry> parts = new ArrayList<>();
            for (Source source : sources) {
                collectParts(source.geometry(), parts);
            }
            return new Joined(factory.buildGeometry(parts), strokeOnly, List.of());
        }

        Map<String, List<Geometry>> grouped = new LinkedHashMap<>();
        Map<String, ToolGeometry> first = new LinkedHashMap<>();
        List<ToolGeometry> all = new ArrayList<>();
        for (Source source : sources) {
            for (ToolGeometry tool : source.tools()) {
                String key = fuseTools ? tool.toolDiameter() + "/" + tool.toolProfile() : Integer.toString(all.size());
                first.putIfAbsent(key, tool);
                collectParts(tool.geometry(), grouped.computeIfAbsent(key, k -> new ArrayList<>()));
                all.add(tool);
            }
        }
        List<ToolGeometry> tools = new ArrayList<>();
        List<Geometry> everything = new ArrayList<>();
        for (Map.Entry<String, List<Geometry>> entry : grouped.entrySet()) {
            ToolGeometry model = first.get(entry.getKey());
            tools.add(new ToolGeometry(model.toolDiameter(), factory.buildGeometry(entry.getValue()),
                    model.toolProfile() == null ? ToolProfile.C1 : model.toolProfile()));
            everything.addAll(entry.getValue());
        }
        return new Joined(factory.buildGeometry(everything), strokeOnly, tools);
    }

    private static void collectParts(Geometry geometry, List<Geometry> parts) {
        if (geometry == null || geometry.isEmpty()) {
            return;
        }
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            parts.add(geometry.getGeometryN(i));
        }
    }
}
