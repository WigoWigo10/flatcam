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
 * <p>Deliberately NOT built the way Python does (camlib.py's gcode_parse():
 * a real G-code text interpreter re-deriving geometry from the generated
 * text). This is built directly alongside the text in the same pass that
 * generates it instead - GCodeGenerator already knows exactly which move is
 * a rapid and which is a cut as it writes each line, so re-deriving that
 * from the text afterward would just be redundant parsing of our own
 * output, with no behavioral difference (this app has no G-code import/
 * editor feature that would ever hand this class text it didn't generate
 * itself).
 *
 * <p>Both geometries are already buffered by the relevant tool's diameter
 * into filled "ribbon" polygons (matching camlib.py's own
 * geo['geom'].buffer(tooldia/1.99999999)), so they render with
 * PlotAreaView's existing filled-polygon layer path with no renderer
 * changes needed.
 */
public record CncJobResult(String gcode, Geometry travelGeometry, Geometry cutGeometry) {
}
