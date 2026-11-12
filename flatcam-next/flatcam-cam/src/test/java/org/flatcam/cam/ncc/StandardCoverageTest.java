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

class StandardCoverageTest {
    private static final GeometryFactory F=new GeometryFactory();
    static Stream<Arguments> cases(){var cases=new ArrayList<Arguments>();for(String shape:List.of("thin","lobe"))for(String units:List.of("MM","IN"))for(String operation:List.of("PAINT","NCC"))for(boolean connect:List.of(false,true))for(boolean rest:List.of(false,true))cases.add(Arguments.of(shape,units,operation,connect,rest));return cases.stream();}
    @ParameterizedTest @MethodSource("cases")
    void disappearingOffsetDoesNotLeaveReachableStock(String shape,String units,String operation,boolean connect,boolean rest){
        double scale=units.equals("IN")?1/25.4:1;
        Geometry area=F.toGeometry(new Envelope(0,4,0,.84));Coordinate witness=new Coordinate(2,.42);
        if(shape.equals("lobe")){area=F.toGeometry(new Envelope(0,8,0,6)).union(F.toGeometry(new Envelope(8,12,2.58,3.42)));witness=new Coordinate(10.5,3);}
        area=AffineTransformation.scaleInstance(scale,scale).transform(area);String before=area.toText();
        var result=run(area,units,operation,connect,rest,.4,scale);
        var point=F.createPoint(new Coordinate(witness.x*scale,witness.y*scale));
        assertTrue(result.toolResults().stream().anyMatch(t->!t.isEmpty()&&t.geometry().distance(point)<=t.toolDiameter()/2),"Reachable witness must be cut; Standard cannot stop with a sub-stepover core");
        checkCoverage(area,result,operation,scale);assertEquals(before,area.toText());
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void zeroOverlapDoesNotLeaveGapsBetweenOffsetCorners(String units){
        double s=units.equals("IN")?1/25.4:1;Geometry area=F.toGeometry(new Envelope(0,4*s,0,4*s));
        var result=run(area,units,"PAINT",false,false,0,s);
        var witness=F.createPoint(new Coordinate(.4*s,.4*s));assertTrue(result.geometry().distance(witness)<=.2*s);
        checkCoverage(area,result,"PAINT",s);
    }
    private static NccResult run(Geometry area,String units,String operation,boolean connect,boolean rest,double overlap,double scale){
        var diameters=rest?List.of(.8*scale,.4*scale):List.of(.4*scale);
        if(operation.equals("PAINT"))return NccGenerator.paint(units,area,new PaintParameters(diameters,overlap,0,NccMethod.STANDARD,connect,true,NccOrder.NONE,rest),CancellationToken.NONE,ProgressCallback.none());
        Geometry board=F.toGeometry(new Envelope(-scale,14*scale,-scale,7*scale));
        return NccGenerator.generate(units,board.difference(area),new NccParameters(diameters,overlap,0,NccMethod.STANDARD,connect,true,0,rest,NccOrder.NONE,new NccBoundary.Area(board)),CancellationToken.NONE,ProgressCallback.none());
    }
    private static void checkCoverage(Geometry area,NccResult result,String operation,double scale){
        var tools=result.toolResults().stream().filter(t->!t.isEmpty()).map(t->new ToolGeometry(t.toolDiameter(),t.geometry())).toList();assertFalse(tools.isEmpty());
        for(var tool:tools)assertTrue(area.buffer(-tool.toolDiameter()*.499).covers(tool.geometry()),"Never borrow clearance by reducing the nominal cutter");
        var own=new HashMap<Integer,Geometry>();for(int i=0;i<tools.size();i++)own.put(i,area);
        var target=new MachiningTarget(operation,result.units(),area,own,MachiningTarget.signature(tools,CancellationToken.NONE));
        var coverage=MachiningCoverage.calculate(target,result.units(),tools,new GeometryGCodeParameters(3*scale,.1*scale,false,0,300*scale,10000,false),Map.of(),CancellationToken.NONE,ProgressCallback.none());
        var access=MachiningReachability.calculate(area,.4*scale,CancellationToken.NONE);
        var residual=MachiningReachability.classify(coverage.finalRemaining(),access,.00075*scale,CancellationToken.NONE);
        assertTrue(residual.stableCore().getArea()<1e-8*scale*scale,"Stable uncut core "+residual.stableCore().getArea());
    }
}
