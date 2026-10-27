package org.flatcam.cam.excellon;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;

/** CNC-7 / XNC circular routs, at most 180 degrees, sampled with a physical chord-error bound. */
final class ExcellonRouteArc {
    static final int MAX_SEGMENTS = 16384;
    static final double CHORD_ERROR_MM = .001;
    record Spec(Double radius, Double i, Double j) { }

    static ExcellonImage.Slot build(int tool, double x1, double y1, double x2, double y2,
                                    boolean clockwise, Spec spec, String units, CancellationToken cancellation) {
        if (spec==null) throw new ExcellonParseException("Circular rout requires A radius or I/J centre offsets");
        double dx=x2-x1, dy=y2-y1, chord=Math.hypot(dx,dy);
        if (!Double.isFinite(chord) || chord==0) throw new ExcellonParseException("Circular rout requires distinct, finite endpoints; split a circle into two arcs");
        double cx,cy,radius;
        double tolerance="IN".equals(units) ? CHORD_ERROR_MM/25.4 : CHORD_ERROR_MM;
        if (spec.radius()!=null) {
            radius=spec.radius();
            if (!Double.isFinite(radius) || radius<=0 || chord/2>radius*(1+1e-12))
                throw new ExcellonParseException("Circular rout radius cannot reach the endpoint");
            double half=chord/2;
            double height=radius*Math.sqrt(Math.max(0,(1-half/radius)*(1+half/radius)));
            double side=clockwise ? -1 : 1;
            cx=x1+dx/2-side*dy/chord*height;
            cy=y1+dy/2+side*dx/chord*height;
        } else {
            // Published CNC-7 text measures centre -> start; other producers use start -> centre.
            // Distinct endpoints must select exactly one of those circles; never guess an ambiguous one.
            if (spec.i()==null || spec.j()==null) throw new ExcellonParseException("Circular rout requires both I and J");
            radius=Math.hypot(spec.i(),spec.j());
            if (!Double.isFinite(radius) || radius<=0)
                throw new ExcellonParseException("Circular rout requires finite nonzero I/J offsets");
            double allowed=Math.max(1e-10,tolerance*.01);
            boolean centreToStart=Math.abs(Math.hypot(x2-(x1-spec.i()),y2-(y1-spec.j()))-radius)<=allowed;
            boolean startToCentre=Math.abs(Math.hypot(x2-(x1+spec.i()),y2-(y1+spec.j()))-radius)<=allowed;
            if(centreToStart && startToCentre)
                throw new ExcellonParseException("Ambiguous circular rout I/J direction; specify A radius instead");
            if (!centreToStart && !startToCentre)
                throw new ExcellonParseException("Circular rout I/J centre does not match both endpoints");
            cx=x1+(centreToStart ? -spec.i() : spec.i()); cy=y1+(centreToStart ? -spec.j() : spec.j());
        }
        if (!Double.isFinite(cx) || !Double.isFinite(cy)) throw new ExcellonParseException("Circular rout centre overflow");
        double magnitude=Math.max(Math.abs(cx)+radius,Math.abs(cy)+radius);
        if(Math.ulp(magnitude)>tolerance/16)
            throw new ExcellonParseException("Circular rout coordinates are too large for the requested physical precision");
        double start=Math.atan2(y1-cy,x1-cx), end=Math.atan2(y2-cy,x2-cx);
        double sweep=end-start;
        if (clockwise) { while(sweep>=0) sweep-=2*Math.PI; }
        else { while(sweep<=0) sweep+=2*Math.PI; }
        if (Math.abs(sweep)>Math.PI+1e-9) throw new ExcellonParseException("Excellon circular rout spans more than 180 degrees; split it into smaller arcs");
        // Reserve half of the error budget for floating-point evaluation and endpoint consistency.
        double maxAngle=Math.min(Math.PI/2,4*Math.asin(Math.sqrt(Math.min(1,tolerance/(4*radius)))));
        double needed=Math.ceil(Math.abs(sweep)/maxAngle);
        if (!Double.isFinite(needed) || needed>MAX_SEGMENTS)
            throw new ExcellonParseException("Circular rout exceeds "+MAX_SEGMENTS+" segments at 0.001 mm chord error");
        int segments=Math.max(1,(int)needed);
        List<ExcellonImage.RoutePoint> points=new ArrayList<>(segments+1);
        points.add(new ExcellonImage.RoutePoint(x1,y1));
        for (int k=1;k<segments;k++) {
            cancellation.throwIfCancellationRequested();
            double angle=start+sweep*k/segments;
            points.add(new ExcellonImage.RoutePoint(cx+radius*Math.cos(angle),cy+radius*Math.sin(angle)));
        }
        points.add(new ExcellonImage.RoutePoint(x2,y2));
        return new ExcellonImage.Slot(tool,x1,y1,x2,y2,points);
    }
}
