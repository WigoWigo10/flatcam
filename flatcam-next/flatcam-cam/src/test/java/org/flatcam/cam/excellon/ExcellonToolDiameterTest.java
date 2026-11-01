package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

class ExcellonToolDiameterTest {
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void changingOneToolKeepsIdsCentresSlotsAndFullPrecision(String units) {
        var factory=new GeometryFactory();
        var hits=List.of(new ExcellonImage.Drill(7,10,10),new ExcellonImage.Drill(2,1,2));
        var routes=List.of(new ExcellonImage.Slot(7,20,10,22,10),new ExcellonImage.Slot(2,1,3,2,4,
                List.of(new ExcellonImage.RoutePoint(1,3),new ExcellonImage.RoutePoint(1.2,3.7),new ExcellonImage.RoutePoint(2,4))));
        var original=ExcellonImage.of(units,Map.of(7,2.2,2,.68),hits,routes,factory.createGeometryCollection());
        double diameter=.1981234567;
        var updated=original.withToolDiameter(7,diameter,CancellationToken.none(),ProgressCallback.none());
        assertEquals(diameter,updated.toolDiameters().get(7));assertEquals(.68,updated.toolDiameters().get(2));
        assertEquals(original.drills(),updated.drills());assertEquals(original.slots(),updated.slots());
        assertEquals(original.units(),updated.units());assertEquals(4,updated.solidGeometry().getNumGeometries());
        assertEquals(diameter,updated.solidGeometry().getGeometryN(0).getEnvelopeInternal().getWidth(),1e-12);
        assertEquals(2+diameter,updated.solidGeometry().getGeometryN(2).getEnvelopeInternal().getWidth(),1e-12);
        assertEquals(2.2,original.toolDiameters().get(7));
        var equal=updated.withToolDiameter(7,.68,CancellationToken.none(),ProgressCallback.none());
        assertEquals(Set.of(2,7),equal.toolDiameters().keySet());assertEquals(original.drills(),equal.drills());
        assertEquals(.68,equal.toolDiameters().get(7));
    }
    @Test void invalidDiameterAndCancellationCannotModifyTheSource() {
        var hits=new ArrayList<ExcellonImage.Drill>();for(int i=0;i<256;i++)hits.add(new ExcellonImage.Drill(1,i,0));
        var original=ExcellonImage.of("MM",Map.of(1,1.0),hits,List.of(),new GeometryFactory().createGeometryCollection());
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->original.withToolDiameter(1,value,CancellationToken.none(),ProgressCallback.none()));
        assertThrows(IllegalArgumentException.class,()->original.withToolDiameter(9,.2,CancellationToken.none(),ProgressCallback.none()));
        var cancelled=new AtomicBoolean();
        assertThrows(CancellationException.class,()->original.withToolDiameter(1,.2,cancelled::get,f->cancelled.set(true)));
        assertEquals(1.0,original.toolDiameters().get(1));assertEquals(256,original.totalDrills());
    }
}
