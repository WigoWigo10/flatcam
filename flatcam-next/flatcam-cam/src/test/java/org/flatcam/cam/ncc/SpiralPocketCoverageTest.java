package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import org.flatcam.cam.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class SpiralPocketCoverageTest {
    static Stream<Arguments> cases(){var cases=new ArrayList<Arguments>();for(String unit:List.of("MM","IN"))for(String op:List.of("Paint","NCC"))for(boolean rest:List.of(false,true))for(double overlap:List.of(0.,.4,.8))cases.add(Arguments.of(unit,op,rest,overlap));return cases.stream();}
    @ParameterizedTest @MethodSource("cases")
    void closesLocallyVanishingLobesAndKeepsNominalClearance(String units,String operation,boolean rest,double overlap){
        var f=new GeometryFactory();double s=units.equals("IN")?1/25.4:1;
        Geometry area=f.toGeometry(new Envelope(0,8,0,6)).union(f.toGeometry(new Envelope(8,12,2.58,3.42)));
        area=AffineTransformation.scaleInstance(s,s).transform(area);String before=area.toText();
        Geometry board=f.toGeometry(new Envelope(-s,14*s,-s,7*s)),copper=board.difference(area);String copperBefore=copper.toText();
        var diameters=rest?List.of(.8*s,.4*s):List.of(.4*s);
        NccResult result=operation.equals("Paint")
                ?NccGenerator.paint(units,area,new PaintParameters(diameters,overlap,0,NccMethod.SPIRAL,true,true,NccOrder.NONE,rest),CancellationToken.NONE,ProgressCallback.none())
                :NccGenerator.generate(units,copper,new NccParameters(diameters,overlap,0,NccMethod.SPIRAL,true,true,0,rest,NccOrder.NONE,new NccBoundary.Area(board)),CancellationToken.NONE,ProgressCallback.none());
        var tools=result.toolResults().stream().filter(t->!t.isEmpty()).map(t->new ToolGeometry(t.toolDiameter(),t.geometry())).toList();assertFalse(tools.isEmpty());
        for(var tool:tools)assertTrue(area.buffer(-tool.toolDiameter()*.499).covers(tool.geometry()),"No cut may borrow clearance by reducing the cutter");
        var witness=f.createPoint(new Coordinate(10.5*s,3*s));
        assertTrue(tools.stream().anyMatch(t->t.geometry().distance(witness)<=t.toolDiameter()/2),"The local lobe cannot disappear merely because the main pocket survives erosion");
        var own=new HashMap<Integer,Geometry>();for(int i=0;i<tools.size();i++)own.put(i,area);
        var target=new MachiningTarget(operation.toUpperCase(java.util.Locale.ROOT),units,area,own,MachiningTarget.signature(tools,CancellationToken.NONE));
        var params=new GeometryGCodeParameters(3*s,.1*s,false,0,300*s,10000,rest);
        var coverage=MachiningCoverage.calculate(target,units,tools,params,Map.of(),CancellationToken.NONE,ProgressCallback.none());
        var access=MachiningReachability.calculate(area,.4*s,CancellationToken.NONE);
        var residual=MachiningReachability.classify(coverage.finalRemaining(),access,.00075*s,CancellationToken.NONE);
        assertTrue(residual.stableCore().getArea()<1e-8*s*s,"Stable residual core: "+residual.stableCore().getArea());
        assertEquals(before,area.toText());assertEquals(copperBefore,copper.toText());
    }
}
