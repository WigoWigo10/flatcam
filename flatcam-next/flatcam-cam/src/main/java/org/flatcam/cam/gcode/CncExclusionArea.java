package org.flatcam.cam.gcode;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

/** Immutable keep-out area; WKT prevents callers mutating a saved safety setting. */
public record CncExclusionArea(String wkt, Strategy strategy, double overZ) {
    public enum Strategy { AROUND, OVER }
    public CncExclusionArea {
        java.util.Objects.requireNonNull(strategy);
        Geometry shape=parse(wkt);
        if (!(shape instanceof Polygon) || shape.isEmpty() || !shape.isValid() || shape.getArea() <= 0 || shape.getNumPoints()>512)
            throw new IllegalArgumentException("Exclusao exige poligono simples valido, ate 512 pontos.");
        for (var point:shape.getCoordinates()) if (!Double.isFinite(point.x) || !Double.isFinite(point.y))
            throw new IllegalArgumentException("Coordenadas da exclusao devem ser finitas.");
        if (!Double.isFinite(overZ) || overZ<0 || strategy==Strategy.OVER && overZ==0)
            throw new IllegalArgumentException("Over Z deve ser positivo e finito para Over.");
        wkt=new WKTWriter().write(shape);
    }
    public static CncExclusionArea of(Geometry shape,Strategy strategy,double overZ) {
        return new CncExclusionArea(new WKTWriter().write(shape),strategy,overZ);
    }
    public Geometry geometry() { return parse(wkt); }
    private static Geometry parse(String wkt) {
        try { return new WKTReader().read(wkt); }
        catch (Exception error) { throw new IllegalArgumentException("Poligono WKT de exclusao invalido.",error); }
    }
}
