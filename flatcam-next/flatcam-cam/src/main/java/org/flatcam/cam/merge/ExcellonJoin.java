package org.flatcam.cam.merge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.flatcam.cam.geometry.ParallelGeometry;

/**
 * Edit > Join Objects > "Join Excellon(s) -> Excellon" (Python's {@code ExcellonObject.merge}): the
 * drills and slots of every selected Excellon go into one new Excellon. Tools are identified by their
 * diameter (tool numbers of the sources mean nothing across files); with {@code fuseTools}, Python's
 * {@code excellon_merge_fuse_tools} default, tools whose diameters agree to four decimals become one
 * tool, otherwise every source tool stays its own.
 */
public final class ExcellonJoin {

    private static final int DIAMETER_DECIMALS = 4;

    private ExcellonJoin() {
    }

    public static ExcellonImage join(List<ExcellonImage> images, boolean fuseTools) {
        if (images.size() < 2) {
            throw new IllegalArgumentException("At least two Excellon objects are required to join them");
        }
        String units = images.get(0).units();
        for (ExcellonImage image : images) {
            if (!units.equals(image.units())) {
                throw new IllegalArgumentException("Cannot join Excellons with different units ("
                        + units + " and " + image.units() + "); convert them first");
            }
        }
        Map<Integer, Double> tools = new LinkedHashMap<>();
        Map<Long, Integer> byDiameter = new LinkedHashMap<>();
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();
        List<Geometry> solids = new ArrayList<>();
        for (ExcellonImage image : images) {
            Map<Integer, Integer> ids = new LinkedHashMap<>();
            for (Map.Entry<Integer, Double> tool : new java.util.TreeMap<>(image.toolDiameters()).entrySet()) {
                long key = Math.round(tool.getValue() * Math.pow(10, DIAMETER_DECIMALS));
                Integer fused = fuseTools ? byDiameter.get(key) : null;
                if (fused == null) {
                    fused = tools.size() + 1;
                    tools.put(fused, fuseTools ? key / Math.pow(10, DIAMETER_DECIMALS) : tool.getValue());
                    byDiameter.put(key, fused);
                }
                ids.put(tool.getKey(), fused);
            }
            for (ExcellonImage.Drill drill : image.drills()) {
                drills.add(new ExcellonImage.Drill(ids.getOrDefault(drill.toolId(), drill.toolId()), drill.x(), drill.y()));
            }
            for (ExcellonImage.Slot slot : image.slots()) {
                slots.add(new ExcellonImage.Slot(ids.getOrDefault(slot.toolId(), slot.toolId()),
                        slot.x1(), slot.y1(), slot.x2(), slot.y2()));
            }
            if (image.solidGeometry() != null && !image.solidGeometry().isEmpty()) {
                solids.add(image.solidGeometry());
            }
        }
        GeometryFactory factory = images.get(0).solidGeometry().getFactory();
        Geometry solid = solids.isEmpty() ? factory.createGeometryCollection()
                : solids.size() == 1 ? solids.get(0) : ParallelGeometry.unionGrouped(solids);
        return ExcellonImage.of(units, tools, drills, slots, solid);
    }
}
