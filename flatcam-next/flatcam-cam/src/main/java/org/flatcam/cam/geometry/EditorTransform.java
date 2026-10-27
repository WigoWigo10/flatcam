package org.flatcam.cam.geometry;

/** Immutable XY transform around a separate pivot. Translation is applied after the linear part. */
public record EditorTransform(double xx, double xy, double yx, double yy, double dx, double dy) {
    public EditorTransform {
        for (double value : new double[]{xx,xy,yx,yy,dx,dy})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Parâmetros da transformação devem ser finitos.");
        double determinant=xx*yy-xy*yx;
        if (!Double.isFinite(determinant) || Math.abs(determinant)<=1e-14*(Math.abs(xx*yy)+Math.abs(xy*yx)))
            throw new IllegalArgumentException("A transformação não pode achatar as formas em uma linha ou ponto.");
        if (xx==1 && xy==0 && yx==0 && yy==1 && dx==0 && dy==0)
            throw new IllegalArgumentException("Use valores que alterem as formas.");
    }
    public static EditorTransform scale(double x,double y) { return new EditorTransform(x,0,0,y,0,0); }
    public static EditorTransform offset(double x,double y) { return new EditorTransform(1,0,0,1,x,y); }
    /** Simultaneous X/Y shear; angles are measured in degrees, as in the Python editor. */
    public static EditorTransform skew(double xDegrees,double yDegrees) {
        if (!Double.isFinite(xDegrees) || !Double.isFinite(yDegrees) || Math.abs(xDegrees)>=90 || Math.abs(yDegrees)>=90)
            throw new IllegalArgumentException("Inclinações devem ficar entre -90 e 90 graus, sem os extremos.");
        return new EditorTransform(1,Math.tan(Math.toRadians(xDegrees)),Math.tan(Math.toRadians(yDegrees)),1,0,0);
    }
}
