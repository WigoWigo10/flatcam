package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.*;
import org.locationtech.jts.geom.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A Rest cutter may overlap cleared stock; only the original copper/boundary forbids its motion. */
class LinesRestAccessTest {
    @ParameterizedTest @CsvSource({"MM,0","MM,37.5","IN,0","IN,90"})
    void smallerCutterReachesCornersEvenWhenRemainingStockCannotAdmitItsWholeDisk(String units,double angle) {
        double s=units.equals("IN")?1/25.4:1;var f=new GeometryFactory();
        Geometry board=f.toGeometry(new Envelope(0,10*s,0,4*s));
        Geometry copper=f.createPoint(new Coordinate(5*s,2*s)).buffer(.3*s,64);String before=copper.toText();
        var tools=List.of(.8*s,.4*s);Point witness=f.createPoint(new Coordinate(.09*s,.09*s));
        for(var method:List.of(NccMethod.LINES,NccMethod.COMBO,NccMethod.REGION_LINES))for(boolean connect:List.of(false,true))for(boolean paint:List.of(false,true)) {
            var nccSettings=new HashMap<Double,NccToolSettings>();var paintSettings=new HashMap<Double,PaintToolSettings>();
            for(double d:tools){nccSettings.put(d,new NccToolSettings(.4,method,connect,true,0,NccSeedPolicy.STABLE,angle));paintSettings.put(d,new PaintToolSettings(.4,0,method,connect,true,angle));}
            var result=paint?NccGenerator.paint(units,board.difference(copper),new PaintParameters(tools,.4,0,method,connect,true,NccOrder.NONE,true,paintSettings),CancellationToken.NONE,ProgressCallback.none())
                    :NccGenerator.generate(units,copper,new NccParameters(tools,.4,0,method,connect,true,0,true,NccOrder.NONE,new NccBoundary.Area(board),List.of(),nccSettings));
            var small=result.toolResults().get(1);
            assertFalse(small.isEmpty(),"Rest must use original cutter access, not erode only the narrow remaining corner");
            assertTrue(small.geometry().distance(witness)<.199*s,"Smaller cutter must actually remove the reachable corner");
            assertTrue(small.geometry().distance(copper)>=.2*s-.0001*s,"Overlap with cleared stock must never become overlap with copper");
            assertTrue(small.geometry().distance(board.getBoundary())>=.2*s-.0001*s);
            assertEquals(before,copper.toText());
        }
    }
}
