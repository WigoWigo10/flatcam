package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class CncOverTravelTest {
    private final GeometryFactory f = new GeometryFactory();
    private CncExclusionArea over(double a,double b,double z) {
        return CncExclusionArea.of(f.toGeometry(new Envelope(a,b,-1,1)),CncExclusionArea.Strategy.OVER,z);
    }
    private List<CncExclusionPlanner.TravelLeg> legs(List<CncExclusionArea> areas, double safe) {
        return new CncExclusionPlanner("MM",areas,1,CancellationToken.none())
                .travelLegs(new Coordinate(0,0),new Coordinate(20,0),safe);
    }
    @Test void singleAreaRaisesAndLowersAtBufferedEntryAndExit() {
        var result=legs(List.of(over(4,6,8)),3);
        assertEquals(List.of(3.,8.,3.),result.stream().map(CncExclusionPlanner.TravelLeg::z).toList());
        assertEquals(3.4,result.get(0).to().x,1e-12);
        assertEquals(6.6,result.get(1).to().x,1e-12);
        assertEquals(20,result.get(2).to().x);
        verify(result,List.of(over(4,6,8)),3,1);
    }
    @Test void overlappingAreasUseTheHighestRequiredHeightWithoutPrematureDescent() {
        var a=over(4,8,8); var b=over(6,10,12);
        var result=legs(List.of(a,b),3);
        assertEquals(List.of(3.,8.,12.,3.),result.stream().map(CncExclusionPlanner.TravelLeg::z).toList());
        assertEquals(5.4,result.get(1).to().x,1e-12);
        assertEquals(10.6,result.get(2).to().x,1e-12);
        assertEquals(result,legs(List.of(b,a),3));
        verify(result,List.of(a,b),3,1);
    }
    @Test void separatedAreasReturnToSafeZInTheGapAndLowOverZNeverLowersSafeZ() {
        var areas=List.of(over(4,6,8),over(10,12,12));
        var result=legs(areas,3);
        assertEquals(List.of(3.,8.,3.,12.,3.),result.stream().map(CncExclusionPlanner.TravelLeg::z).toList());
        verify(result,areas,3,1);
        var high=legs(areas,20);
        assertEquals(1,high.size()); assertEquals(20,high.getFirst().z());
    }
    @Test void polygonHolesAreNotTreatedAsSolidAndAReverseTripKeepsProtection() {
        var shell=f.createLinearRing(f.toGeometry(new Envelope(4,16,-4,4)).getCoordinates());
        var hole=f.createLinearRing(f.toGeometry(new Envelope(8,12,-2,2)).getCoordinates());
        var area=CncExclusionArea.of(f.createPolygon(shell,new LinearRing[]{hole}),CncExclusionArea.Strategy.OVER,8);
        var result=legs(List.of(area),3);
        assertEquals(List.of(3.,8.,3.,8.,3.),result.stream().map(CncExclusionPlanner.TravelLeg::z).toList());
        verify(result,List.of(area),3,1);
        var reverse=new CncExclusionPlanner("MM",List.of(area),1,CancellationToken.none())
                .travelLegs(new Coordinate(20,0),new Coordinate(0,0),3);
        verify(reverse,List.of(area),3,1);
        assertEquals(0,reverse.getLast().to().x);
    }
    @Test void mixedAroundAndOverAreasProtectEveryDetourLeg() {
        var around=CncExclusionArea.of(f.toGeometry(new Envelope(4,6,-1,1)),CncExclusionArea.Strategy.AROUND,0);
        var over=CncExclusionArea.of(f.toGeometry(new Envelope(3,7,-3,3)),CncExclusionArea.Strategy.OVER,12);
        var result=legs(List.of(around,over),3);
        assertTrue(result.size()>3);
        verify(result,List.of(around,over),3,1);
    }
    @Test void inchStationsUseConvertedMarginAndInvalidRequestsCancelOrFail() {
        var area=CncExclusionArea.of(f.toGeometry(new Envelope(4/25.4,6/25.4,-1/25.4,1/25.4)),CncExclusionArea.Strategy.OVER,8/25.4);
        var planner=new CncExclusionPlanner("IN",List.of(area),1/25.4,CancellationToken.none());
        var result=planner.travelLegs(new Coordinate(0,0),new Coordinate(20/25.4,0),3/25.4);
        assertEquals(3.4/25.4,result.getFirst().to().x,1e-12);
        verify(result,List.of(area),3/25.4,1/25.4);
        assertThrows(IllegalArgumentException.class,()->planner.travelLegs(new Coordinate(0,0),new Coordinate(5/25.4,0),3/25.4));
        assertThrows(IllegalArgumentException.class,()->planner.travelLegs(new Coordinate(0,0),new Coordinate(20/25.4,0),Double.NaN));
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var cancellable=new CncExclusionPlanner("MM",List.of(over(4,6,8)),1,cancelled::get);
        cancelled.set(true);
        assertThrows(java.util.concurrent.CancellationException.class,()->cancellable.travelLegs(new Coordinate(0,0),new Coordinate(20,0),3));
    }
    @Test void emittedDrillingAndGeometryXyzProtectOverlapsAcrossUnitsAndMillingProfiles() {
        for (String units:List.of("MM","IN")) for (var profile:List.of(GCodePreprocessor.FX_PORTABLE,
                GCodePreprocessor.GRBL_11_NO_M6,GCodePreprocessor.DEFAULT_NO_M6,GCodePreprocessor.MARLIN)) {
            double s=units.equals("IN") ? 1/25.4 : 1;
            var areas=List.of(CncExclusionArea.of(f.toGeometry(new Envelope(4*s,8*s,-s,s)),CncExclusionArea.Strategy.OVER,8*s),
                    CncExclusionArea.of(f.toGeometry(new Envelope(6*s,10*s,-s,s)),CncExclusionArea.Strategy.OVER,12*s));
            var drill=ExcellonImage.of(units,Map.of(1,s),List.of(new ExcellonImage.Drill(1,14*s,0)),List.of(),f.createPolygon());
            var dp=new DrillGCodeParameters(3*s,s,100*s,10000,true);
            var options=new GCodeGenerator.DrillJobOptions(true,15*s,.5*s,0.,0.).withExclusions(true,areas);
            checkEmitted(GCodeGenerator.generateDrillCncJob(drill,Map.of(1,dp),List.of(1),options,profile).gcode(),areas,s);
            var path=f.createLineString(new Coordinate[]{new Coordinate(14*s,0),new Coordinate(16*s,0)});
            var gp=new GeometryGCodeParameters(3*s,.6*s,true,.2*s,200*s,10000,true).withJobOptions(
                    new GeometryJobOptions(null,.5*s,0.,0.,15*s,0.,0.).withExclusions(true,areas));
            checkEmitted(GCodeGenerator.generateGeometryCncJob(units,List.of(new ToolGeometry(s,path)),gp,Map.of(),
                    CancellationToken.none(),profile).gcode(),areas,s);
        }
    }
    private void checkEmitted(String code,List<CncExclusionArea> areas,double diameter) {
        double x=0,y=0,z=0; int checked=0;
        for (String raw:code.lines().toList()) {
            var line=raw.split("[;(]",2)[0].trim();
            if (!line.matches("G0?[01](?: .*|$)")) continue;
            double nx=x,ny=y,nz=z;
            for (String word:line.split("\\s+")) {
                if (word.startsWith("X")) nx=Double.parseDouble(word.substring(1));
                if (word.startsWith("Y")) ny=Double.parseDouble(word.substring(1));
                if (word.startsWith("Z")) nz=Double.parseDouble(word.substring(1));
            }
            if (line.matches("G0?0(?: .*|$)")) {
                var motion=(x==nx && y==ny) ? f.createPoint(new Coordinate(x,y))
                        : f.createLineString(new Coordinate[]{new Coordinate(x,y),new Coordinate(nx,ny)});
                for (var area:areas) if (motion.intersects(area.geometry().buffer(diameter/2))) {
                    checked++;
                    assertTrue(Math.min(z,nz)>=area.overZ()-.000051,"Emitted movement below Over Z: "+raw+"\n"+code);
                }
            }
            x=nx;y=ny;z=nz;
        }
        assertTrue(checked>=4,code); // Outbound and parking traverse both protected regions.
    }
    private void verify(List<CncExclusionPlanner.TravelLeg> result,List<CncExclusionArea> areas,double safe,double diameter) {
        for (int i=0;i<result.size();i++) {
            var leg=result.get(i);
            assertTrue(leg.z()>=safe);
            if (i>0) assertTrue(result.get(i-1).to().equals2D(leg.from()));
            var path=f.createLineString(new Coordinate[]{leg.from(),leg.to()});
            for (var area:areas) {
                // Actual cutter footprint, independently of the planner's additional 0.1 mm margin.
                var protectedArea=area.geometry().buffer(diameter/2);
                if (area.strategy()==CncExclusionArea.Strategy.AROUND) assertFalse(path.intersects(protectedArea));
                else if (path.intersects(protectedArea)) assertTrue(leg.z()>=area.overZ(),"Under-height Over leg: "+leg);
                if (i>0 && leg.z()<result.get(i-1).z() && protectedArea.covers(f.createPoint(leg.from())))
                    assertTrue(leg.z()>=area.overZ(),"Descent inside the protected cutter footprint");
            }
        }
    }
}
