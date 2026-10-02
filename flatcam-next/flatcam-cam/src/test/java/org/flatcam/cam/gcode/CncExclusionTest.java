package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class CncExclusionTest {
    final GeometryFactory f=new GeometryFactory();
    CncExclusionArea area(double x1,double x2,double y1,double y2,CncExclusionArea.Strategy strategy,double height) {
        return CncExclusionArea.of(f.toGeometry(new Envelope(x1,x2,y1,y2)),strategy,height);
    }
    LineString line(double x1,double y1,double x2,double y2) {
        return f.createLineString(new Coordinate[]{new Coordinate(x1,y1),new Coordinate(x2,y2)});
    }
    @Test void overlappingAroundAreasAreRoutedAsUnionWithToolRadiusAndMargin() {
        var areas=List.of(area(4,6,-1,1,CncExclusionArea.Strategy.AROUND,0),area(5,7,0,3,CncExclusionArea.Strategy.AROUND,0));
        var planner=new CncExclusionPlanner("MM",areas,1,CancellationToken.none());
        var travel=planner.travel(new Coordinate(0,0),new Coordinate(10,0),3);
        assertTrue(travel.points().size()>2); assertEquals(3,travel.z());
        Geometry route=f.createLineString(travel.points().toArray(Coordinate[]::new));
        for(var area:areas) assertTrue(route.distance(area.geometry())>=.5999);
    }
    @Test void overUsesMaximumHeightAndNeverLowersTravelZ() {
        var planner=new CncExclusionPlanner("MM",List.of(area(4,6,-1,1,CncExclusionArea.Strategy.OVER,10),
                area(7,8,-1,1,CncExclusionArea.Strategy.OVER,20)),1,CancellationToken.none());
        var travel=planner.travel(new Coordinate(0,0),new Coordinate(10,0),3);
        assertEquals(2,travel.points().size()); assertEquals(20,travel.z());
        assertEquals(30,planner.travel(new Coordinate(0,0),new Coordinate(10,0),30).z());
        assertThrows(IllegalArgumentException.class,()->planner.travel(new Coordinate(0,0),new Coordinate(5,0),3));
        assertThrows(IllegalArgumentException.class,()->planner.validateCut(line(4,0,6,0)));
    }
    @Test void codeRoutesToolChangesPassReturnsAndParkingAndPreviewContainsDetour() {
        var around=area(4,6,-1,1,CncExclusionArea.Strategy.AROUND,0);
        var options=new GeometryJobOptions(null,.5,0.0,0.0,15.0,0.0,0.0).withExclusions(true,List.of(around));
        var p=new GeometryGCodeParameters(3,.2,true,.1,100,100,true).withJobOptions(options);
        var tools=List.of(new ToolGeometry(1,line(10,0,12,0)),new ToolGeometry(.5,line(10,2,12,2)));
        var job=GCodeGenerator.generateGeometryCncJob("MM",tools,p);
        assertFalse(job.travelGeometry().intersects(around.geometry()));
        assertTrue(job.travelGeometry().getEnvelopeInternal().getMinY()<-.5 || job.travelGeometry().getEnvelopeInternal().getMaxY()>2);
        String overCode=GCodeGenerator.generateGeometryCncJob("MM",tools,p.withJobOptions(options.withExclusions(true,
                List.of(area(4,6,-1,1,CncExclusionArea.Strategy.OVER,20))))).gcode();
        assertTrue(overCode.contains("G0 Z20.0000\nG0 X10.0000 Y0.0000\nG0 Z3.0000"),overCode);
        assertTrue(overCode.lastIndexOf("Z0.5000")>overCode.lastIndexOf("X0.0000 Y0.0000"));
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateGeometryCncJob("MM",tools,
                p.withJobOptions(new GeometryJobOptions(null,null,5.0,0.0,null,null,null).withExclusions(true,List.of(around)))));
    }
    @Test void incompatibleProfilesIntersectionsInactiveDraftsAndCancellationAreExplicit() {
        var areas=List.of(area(4,6,-1,1,CncExclusionArea.Strategy.AROUND,0));
        var p=new GeometryGCodeParameters(3,.2,false,1,100,100,false).withJobOptions(GeometryJobOptions.AUTOMATIC.withExclusions(true,areas));
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,line(4,0,8,0))),p));
        for(var profile:GCodePreprocessor.geometryProfiles()) if(profile.isLaser()||profile.isPlotter()||profile.isRoland()||profile.requiresProbe())
            assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateGeometryCncJob("MM",List.of(new ToolGeometry(1,line(10,0,12,0))),p,Map.of(),CancellationToken.none(),profile));
        var plain=new GeometryGCodeParameters(3,.2,false,1,100,100,false);
        var tools=List.of(new ToolGeometry(1,line(10,0,12,0)));
        assertEquals(GCodeGenerator.generateGeometryCncJob("MM",tools,plain).gcode(),
                GCodeGenerator.generateGeometryCncJob("MM",tools,plain.withJobOptions(GeometryJobOptions.AUTOMATIC.withExclusions(false,areas))).gcode());
        assertThrows(java.util.concurrent.CancellationException.class,()->new CncExclusionPlanner("MM",areas,1,()->true));
        assertThrows(IllegalArgumentException.class,()->new CncExclusionArea("LINESTRING (0 0,1 1)",CncExclusionArea.Strategy.AROUND,0));
        var inch=new CncExclusionPlanner("IN",List.of(area(.4,.6,-.1,.1,CncExclusionArea.Strategy.AROUND,0)),.1,CancellationToken.none());
        assertTrue(inch.travel(new Coordinate(0,0),new Coordinate(1,0),.2).points().size()>2);
    }
}
