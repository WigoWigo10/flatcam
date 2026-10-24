package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class LaserPositionsTest {
    @Test void finalXyOccursOffAtClearanceBeforeLowerFinalZInBothUnits() {
        var factory=new GeometryFactory();
        for(var profile:List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.Z_LASER,
                GCodePreprocessor.MARLIN_LASER_FAN_PIN,GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) for(String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var line=factory.createLineString(new Coordinate[]{new Coordinate(10*s,7*s),new Coordinate(12*s,7*s)});
            var p=new GeometryGCodeParameters(3*s,s,false,s,200*s,128,false,900*s)
                    .withJobOptions(new GeometryJobOptions(null,.5*s,25*s,9*s,null,null,null));
            String code=GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(.4*s,line)),p,Map.of(),CancellationToken.none(),profile).gcode();
            int park=code.lastIndexOf(" X"+GCodeGenerator.fmt(25*s)+" Y"+GCodeGenerator.fmt(9*s));
            int lower=code.lastIndexOf(" Z"+GCodeGenerator.fmt(.5*s));
            assertTrue(park>0,code); assertTrue(lower>park,code);
            String before=code.substring(0,park);
            assertTrue(before.lastIndexOf(profile.spindleOff())>before.lastIndexOf(profile.spindleOn()+" S"),code);
            assertTrue(before.contains(" Z"+GCodeGenerator.fmt(3*s)),code);
            assertNull(GCodeToolpathParser.parse(code,CancellationToken.none(),ignored->{}).warning());
        }
    }
    @Test void unsupportedStartAndMechanicalChangesStayRejected() {
        for(var p:List.of(new GeometryJobOptions(2.,null,null,null,null,null,null),
                new GeometryJobOptions(null,null,null,null,15.,null,null),
                new GeometryJobOptions(null,null,null,null,null,7.,11.)))
            assertThrows(IllegalArgumentException.class,()->p.validate(GCodePreprocessor.GRBL_LASER,3,false));
    }
}
