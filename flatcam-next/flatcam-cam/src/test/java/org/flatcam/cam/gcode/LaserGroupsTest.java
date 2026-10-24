package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.regex.Pattern;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class LaserGroupsTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static final Pattern WORD = Pattern.compile("([XYZFS])([+-]?[0-9.]+)");
    private ToolGeometry path(double x,double scale) {
        return new ToolGeometry(.4*scale,F.createLineString(new Coordinate[]{new Coordinate(x*scale,7*scale),new Coordinate((x+2)*scale,7*scale)}));
    }
    private GeometryGCodeParameters parameters(double focus,double feed,int power,double scale) {
        return new GeometryGCodeParameters(focus*scale,scale,false,scale,feed*scale,power,false,900*scale);
    }
    @Test void groupsHaveTheirOwnFeedPowerAndFocusWithUnpoweredClearanceTransitions() {
        for (var profile:List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.Z_LASER,
                GCodePreprocessor.MARLIN_LASER_FAN_PIN,GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) for (String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            boolean focused=profile!=GCodePreprocessor.GRBL_LASER;
            var positions=new GeometryJobOptions(null,.5*s,30*s,5*s,null,null,null);
            var a=parameters(6,200,64,s).withJobOptions(positions);
            var b=parameters(focused ? 2 : 6,350,200,s).withJobOptions(positions);
            var job=GCodeGenerator.generateGeometryCncJob(units,List.of(path(10,s),path(20,s)),a,Map.of(),Map.of(1,b),CancellationToken.none(),profile);
            boolean on=false; int power=0, cuts=0; double x=0,y=0,z=0;
            for (String line:job.gcode().split("\n")) {
                if (line.equals("M5") || line.equals("M05") || line.equals("M107")) on=false;
                if (line.startsWith(profile.spindleOn()+" S")) { on=true; power=Integer.parseInt(line.substring(line.indexOf('S')+1)); }
                boolean rapid=line.startsWith(profile.rapid()+" "), linear=line.startsWith(profile.linear()+" ");
                if (!rapid && !linear) continue;
                var values=new HashMap<String,Double>(); var words=WORD.matcher(line);
                while(words.find()) values.put(words.group(1),Double.valueOf(words.group(2)));
                if(rapid) assertFalse(on,"Powered positioning: "+line);
                if(rapid && values.containsKey("X") && Math.abs(values.get("X")-20*s)<.000051 && focused)
                    assertEquals(6*s,z,.000051,"Cross-group XY must precede lowering focus");
                x=values.getOrDefault("X",x); y=values.getOrDefault("Y",y); z=values.getOrDefault("Z",z);
                if(linear) {
                    assertTrue(on); cuts++;
                    boolean first=Math.abs(x-12*s)<.000051;
                    assertEquals(first ? 64 : 200,power);
                    assertEquals((first ? 200 : 350)*s,values.get("F"),.000051);
                    assertEquals(focused ? (first ? 6 : 2)*s : 0,z,.000051);
                }
            }
            assertEquals(2,cuts); assertFalse(on);
            assertEquals(30*s,x,.000051); assertEquals(5*s,y,.000051); assertEquals(.5*s,z,.000051);
            assertFalse(job.gcode().lines().anyMatch(l->l.matches("T[0-9]+|M0|M6|M06")));
            var preview=GCodeToolpathParser.parse(job.gcode(),CancellationToken.none(),ignored->{});
            assertNull(preview.warning(),job.gcode());
            assertEquals(4*s,preview.cutCenterlines().getLength(),.0001);
            assertTrue(job.travelGeometry().getEnvelopeInternal().getMaxX()>=30*s);
            if(profile.usesRapidFeed()) assertTrue(job.gcode().endsWith("M400\n"));
        }
    }
    @Test void automaticFinalZUsesLastNonemptyGroupAndEmptyFocusDoesNotRaiseClearance() {
        var a=parameters(6,200,64,1); var b=parameters(2,350,200,1);
        var empty=new ToolGeometry(.4,F.createPolygon());
        String code=GCodeGenerator.generateGeometryCncJob("MM",List.of(path(10,1),path(20,1),empty),a,Map.of(),
                Map.of(1,b,2,parameters(99,100,50,1)),CancellationToken.none(),GCodePreprocessor.Z_LASER).gcode();
        assertTrue(code.endsWith("G00 Z2.0000\n"),code);
        assertFalse(code.contains("Z99"));
    }
    @Test void unsupportedGroupChangesAndFanOverflowAreRejected() {
        var a=parameters(6,200,64,1);
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.validateLaserGroups(GCodePreprocessor.GRBL_LASER,a,Map.of(1,parameters(2,350,200,1))));
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.validateLaserGroups(GCodePreprocessor.MARLIN_LASER_FAN_PIN,a,Map.of(1,parameters(2,350,256,1))));
        var dwell=new GeometryGCodeParameters(2,1,false,1,100,64,false,900,null,100,true,1,false,0);
        var rapid=new GeometryGCodeParameters(2,1,false,1,100,64,false,800);
        for(var invalid:List.of(dwell,rapid)) assertThrows(IllegalArgumentException.class,
                ()->GCodeGenerator.validateLaserGroups(GCodePreprocessor.Z_LASER,a,Map.of(1,invalid)));
    }
}
