package org.flatcam.cam.gcode;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

/** Mirrors Python's buffer(join_style=2), converting closed lines to polygons first. */
final class GeometryPathCompensation {
    private GeometryPathCompensation() {}
    static Geometry apply(Geometry source, double distance, CancellationToken cancellation) {
        if (distance == 0 || source.isEmpty()) return source;
        List<Geometry> parts = new ArrayList<>();
        compensate(source, distance, parts, cancellation);
        return source.getFactory().buildGeometry(parts);
    }
    private static void compensate(Geometry source, double distance, List<Geometry> parts, CancellationToken cancellation) {
        cancellation.throwIfCancellationRequested();
        if (source.isEmpty()) return;
        if (source instanceof GeometryCollection) {
            for (int i = 0; i < source.getNumGeometries(); i++)
                compensate(source.getGeometryN(i), distance, parts, cancellation);
            return;
        }
        Geometry shape = source;
        if (source instanceof LineString line && line.isClosed()) {
            if (line.getNumPoints() < 4) throw new IllegalArgumentException("Offset exige um anel fechado valido.");
            shape = source.getFactory().createPolygon(line.getCoordinates());
        }
        if (!shape.isValid()) throw new IllegalArgumentException("Offset exige geometria valida; corrija a Geometry antes de gerar.");
        var parameters = new BufferParameters(16, BufferParameters.CAP_ROUND, BufferParameters.JOIN_MITRE, 5);
        Geometry result = BufferOp.bufferOp(shape, distance, parameters);
        cancellation.throwIfCancellationRequested();
        if (result.isEmpty()) throw new IllegalArgumentException("Offset elimina um caminho. Reduza a compensacao; In em caminhos abertos nao e suportado pelo buffer Python.");
        for (Coordinate c : result.getCoordinates()) {
            cancellation.throwIfCancellationRequested();
            if (!Double.isFinite(c.x) || !Double.isFinite(c.y))
                throw new IllegalArgumentException("Offset produziu coordenadas invalidas.");
        }
        parts.add(result);
    }
}
