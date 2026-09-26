package org.flatcam.cam.gcode;

import org.locationtech.jts.geom.Geometry;

/**
 * A generated G-code job's text plus its own toolpath geometry, split into
 * travel (rapid, non-cutting G0 moves) and cut (feed, G1 moves - or, for a
 * drilled hole, a circle at the tool diameter marking where it plunges,
 * since a drill doesn't move laterally while cutting) - ports the two
 * "Plot Kind" categories appObjects/FlatCAMCNCJob.py's CNCjob.plot2() draws
 * a CNCJob object's toolpath in.
 *
 * <p>Generation builds exact tool-width geometry alongside the text. The
 * G-Code Editor uses {@link GCodeToolpathParser} to reconstruct a conservative
 * centerline preview after textual edits; it cannot recover original tool
 * widths or plot unsupported motion commands and planes.
 *
 * <p>Both geometries are already buffered by the relevant tool's diameter
 * into filled "ribbon" polygons (matching camlib.py's own
 * geo['geom'].buffer(tooldia/1.99999999)), so they render with
 * PlotAreaView's existing filled-polygon layer path with no renderer
 * changes needed.
 */
public record CncJobResult(String gcode, Geometry travelGeometry, Geometry cutGeometry) {
}
