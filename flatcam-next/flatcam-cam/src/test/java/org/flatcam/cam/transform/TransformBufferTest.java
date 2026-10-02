package org.flatcam.cam.transform;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class TransformBufferTest {
    final GeometryFactory f=new GeometryFactory();
    @Test void distanceAndLocalFactorHaveDifferentMeaning() {
        var left=f.toGeometry(new Envelope(0,2,0,2)); var right=f.toGeometry(new Envelope(10,12,0,2));
        var shapes=f.buildGeometry(List.of(left,right));
        var enlarged=new TransformOp.Buffer(2,true,true).apply(shapes);
        assertEquals(-1,enlarged.getGeometryN(0).getEnvelopeInternal().getMinX());
        assertEquals(9,enlarged.getGeometryN(1).getEnvelopeInternal().getMinX());
        assertEquals(1,enlarged.getGeometryN(0).getEnvelopeInternal().centre().x);
        var expanded=new TransformOp.Buffer(.5,false,false).apply(left);
        assertEquals(9,expanded.getArea(),1e-6);
        assertEquals(1,new TransformOp.Buffer(-.5,false,false).apply(left).getArea(),1e-6);
        assertThrows(IllegalArgumentException.class,()->new TransformOp.Buffer(Double.NaN,false,true));
        assertThrows(IllegalArgumentException.class,()->new TransformOp.Buffer(0,true,true));
    }
    @Test void excellonChangesDiameterNotPositionsOrSlotsAndRebuildsFootprint() {
        var image=ExcellonImage.of("MM",Map.of(1,1.0),List.of(new ExcellonImage.Drill(1,5,6)),
                List.of(new ExcellonImage.Slot(1,10,6,12,6)),f.createGeometryCollection());
        var result=image.transformed(new TransformOp.Buffer(.5,false,true));
        assertEquals(1.5,result.toolDiameters().get(1)); assertEquals(image.drills(),result.drills());
        assertEquals(image.slots(),result.slots()); assertEquals(4.25,result.bounds()[0],1e-9);
        assertThrows(IllegalArgumentException.class,()->image.transformed(new TransformOp.Buffer(-1,false,true)));
        assertEquals(1.1,image.transformed(new TransformOp.Buffer(1.1,true,true)).toolDiameters().get(1),1e-9);
    }
    @Test void gerberRetainsFollowAndBufferedGeometrySurvivesRegionExport() {
        var solid=f.toGeometry(new Envelope(0,2,0,2)); var follow=f.createPoint(new Coordinate(1,1));
        var image=GerberImage.of("MM",Map.of("10",Aperture.rectangle(2,2)),solid,follow,Map.of("10",solid));
        var result=image.transformed(new TransformOp.Buffer(.5,false,false));
        assertSame(follow,result.followGeometry()); assertEquals(9,result.solidGeometry().getArea(),1e-6);
        assertEquals(4,image.solidGeometry().getArea(),1e-6);
    }
}
