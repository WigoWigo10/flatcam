package org.flatcam.cam.excellon;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.*;

/** New drill/slot arrays. Positions and slot endpoints are authoritative, not inferred from display polygons. */
public final class ExcellonArray {
    public enum Kind { LINEAR, CIRCULAR }
    public record Parameters(Kind kind, int count, double diameter, boolean slots,
                             double slotLength, double slotAngle, boolean rotateSlots,
                             double x, double y, double pitch, double linearAngle,
                             double radius, double startAngle, double stepAngle, boolean clockwise) {
        public Parameters {
            java.util.Objects.requireNonNull(kind);
            if(count<1 || count>10000) throw new IllegalArgumentException("Quantidade deve estar entre 1 e 10.000.");
            for(double v:new double[]{diameter,slotLength,slotAngle,x,y,pitch,linearAngle,radius,startAngle,stepAngle})
                if(!Double.isFinite(v)) throw new IllegalArgumentException("Valores do array devem ser finitos.");
            if(diameter<=0) throw new IllegalArgumentException("Diâmetro deve ser positivo.");
            if(slots && slotLength<=diameter) throw new IllegalArgumentException("Comprimento total do slot deve ser maior que o diâmetro.");
            if(kind==Kind.LINEAR && count>1 && pitch==0) throw new IllegalArgumentException("Passo linear não pode ser zero.");
            if(kind==Kind.CIRCULAR && (radius<=0 || stepAngle<=0 || count*stepAngle>360+1e-10))
                throw new IllegalArgumentException("Raio e passo angular devem ser positivos; quantidade × passo não pode exceder 360°.");
        }
    }
    public record Element(double x1,double y1,double x2,double y2) { }
    public record Prepared(Parameters parameters,List<Element> elements,Geometry geometry) {
        public Prepared { elements=List.copyOf(elements); }
    }
    private ExcellonArray() { }
    public static Prepared generate(Parameters p,CancellationToken cancel,ProgressCallback progress) {
        var f=new GeometryFactory(); var elements=new ArrayList<Element>(p.count()); var footprints=new ArrayList<Geometry>(p.count());
        double radians=Math.toRadians(Math.IEEEremainder(p.linearAngle(),360));
        for(int i=0;i<p.count();i++) {
            cancel.throwIfCancellationRequested();
            double sweep=(p.clockwise() ? -1 : 1)*p.stepAngle()*i;
            double a=Math.toRadians(Math.IEEEremainder(p.startAngle()+sweep,360));
            double x=p.kind()==Kind.LINEAR ? p.x()+i*p.pitch()*Math.cos(radians) : p.x()+p.radius()*Math.cos(a);
            double y=p.kind()==Kind.LINEAR ? p.y()+i*p.pitch()*Math.sin(radians) : p.y()+p.radius()*Math.sin(a);
            double angle=p.slotAngle()+(p.kind()==Kind.CIRCULAR && p.rotateSlots() ? p.startAngle()+sweep : 0);
            double half=p.slots() ? (p.slotLength()-p.diameter())/2 : 0;
            double dx=half*Math.cos(Math.toRadians(Math.IEEEremainder(angle,360))),dy=half*Math.sin(Math.toRadians(Math.IEEEremainder(angle,360)));
            var e=new Element(x-dx,y-dy,x+dx,y+dy);
            for(double v:new double[]{e.x1(),e.y1(),e.x2(),e.y2()})
                if(!Double.isFinite(v)) throw new IllegalArgumentException("Coordenadas do array excedem o intervalo válido.");
            if(p.slots() && e.x1()==e.x2() && e.y1()==e.y2()) throw new IllegalArgumentException("Slot pequeno demais para estas coordenadas.");
            elements.add(e);
            Geometry path=p.slots() ? f.createLineString(new Coordinate[]{new Coordinate(e.x1(),e.y1()),new Coordinate(e.x2(),e.y2())})
                    : f.createPoint(new Coordinate(x,y));
            footprints.add(path.buffer(p.diameter()/2,16));
            progress.report((i+1.0)/p.count());
        }
        cancel.throwIfCancellationRequested();
        return new Prepared(p,elements,f.buildGeometry(footprints));
    }
}
