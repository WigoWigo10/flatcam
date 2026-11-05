package org.flatcam.fx;

import javafx.scene.paint.Color;
import org.locationtech.jts.geom.Geometry;

/** Immutable, worker-prepared preview. Swept area is the cutter footprint, not a cleared pocket. */
record InternalCutoutPreview(Geometry openings, Geometry paths, Geometry swept, Geometry invalid) {
    Geometry[] geometries() { return new Geometry[]{openings,paths,swept,invalid}; }
    static Color opening(boolean dark) { return Color.web(dark ? "#5DE0D4" : "#007E82"); }
    static Color path(boolean dark) { return Color.web(dark ? "#FFE29A" : "#753800"); }
    static Color swept(boolean dark) { return Color.web(dark ? "#FFB34770" : "#D97A0060"); }
    static Color invalid(boolean dark) { return Color.web(dark ? "#FF8585" : "#B42318"); }
}
