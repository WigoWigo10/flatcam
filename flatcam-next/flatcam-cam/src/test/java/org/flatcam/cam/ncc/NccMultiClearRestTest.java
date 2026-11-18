package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.CutterSweep;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.*;

/** Yago's 18/11 report: one ISO plus several CLEAR cutters must share remaining stock.
 * Reporter build/method unknown; this deterministic fixture tests the current source. */
class NccMultiClearRestTest {
    @ParameterizedTest @EnumSource(NccMethod.class)
    void clearCuttersDoNotRepeatTheAlreadyClearedInterior(NccMethod method) {
        var f=new GeometryFactory();
        Geometry board=f.toGeometry(new Envelope(0,12,0,8));
        Geometry copper=f.createPoint(new Coordinate(6,4)).buffer(1,64);
        // Deliberately unsorted: Rest must process the physical diameters largest first.
        var tools=List.of(.4,1.2,.8);
        var rest=NccGenerator.generate("MM",copper,new NccParameters(tools,.4,0,method,
                false,true,0,true,NccOrder.NONE,new NccBoundary.Area(board),List.of(.2)));
        // This strip is fully swept by the largest cutter, far from pad and board corners.
        Geometry clearedInterior=f.toGeometry(new Envelope(2,10,.7,1.5));
        assertEquals(List.of(.2,1.2,.8,.4),rest.toolResults().stream().map(NccToolResult::toolDiameter).toList());
        assertEquals(NccOperation.ISO,rest.toolResults().getFirst().operation());
        for(int i=2;i<rest.toolResults().size();i++) {
            var cutter=rest.toolResults().get(i);
            double repeated=cutter.geometry().intersection(clearedInterior).getLength();
            System.out.printf(java.util.Locale.ROOT,"REST-CHECK %s diameter=%.1f path=%.6f stock=%.6f repeated=%.6f%n",
                    method.name(),cutter.toolDiameter(),cutter.geometry().getLength(),cutter.targetArea().getArea(),repeated);
            assertEquals(0,repeated,1e-8,"A smaller cutter must not mill the already cleared interior: "+method);
        }
    }

    @ParameterizedTest @EnumSource(NccMethod.class)
    void nextCutterReceivesStockAfterBothEarlierClearCuttersWithAndWithoutConnect(NccMethod method) {
        var f=new GeometryFactory();
        for(double scale:List.of(1.,1/25.4))for(boolean connect:List.of(false,true)) {
            Geometry board=f.toGeometry(new Envelope(0,12*scale,0,8*scale));
            Geometry copper=f.createPoint(new Coordinate(6*scale,4*scale)).buffer(scale,64);
            var result=NccGenerator.generate(scale==1?"MM":"IN",copper,new NccParameters(
                    List.of(.4*scale,1.2*scale,.8*scale),.4,0,method,connect,true,0,true,
                    NccOrder.FORWARD,new NccBoundary.Area(board),List.of(.2*scale)));
            assertHandoffs(result,scale,method+" connect="+connect);
        }
    }

    @RepeatedTest(10) void handoffOracleAcceptsRestAndRejectsIndependentFullAreaPaths() {
        var f=new GeometryFactory();Geometry board=f.toGeometry(new Envelope(0,12,0,8));
        Geometry copper=f.createPoint(new Coordinate(6,4)).buffer(1,64);
        var rest=NccGenerator.generate("MM",copper,new NccParameters(List.of(1.2,.8,.4),.4,0,
                NccMethod.LINES,false,true,0,true,NccOrder.REVERSE,new NccBoundary.Area(board),List.of(.2)));
        assertHandoffs(rest,1,"Rest enabled");
        var independent=NccGenerator.generate("MM",copper,new NccParameters(List.of(1.2,.8,.4),.4,0,
                NccMethod.LINES,false,true,0,false,NccOrder.REVERSE,new NccBoundary.Area(board),List.of(.2)));
        // Reproduce the reported symptom deliberately with Rest OFF. This is a negative oracle control,
        // not evidence that the reporter's unspecified build was reproduced with Rest ON.
        assertThrows(AssertionError.class,()->assertHandoffs(independent,1,"Rest disabled"));
    }

    @Test @EnabledIfSystemProperty(named="flatcam.rest.audit.public",matches="true")
    void publicPowerSupplyBoardsPassTheSameHandoffOracle()throws Exception {
        for(String file:List.of(
                "single-sided-5v/fabrication/Single-Sided 5V Power Supply-B_Cu.gbr",
                "breadboard-power-supply/fabrication/Gerber/BreadboardPowerSupply_v2-B.Cu.gbl")) {
            var image=new GerberParser().parse(Path.of("../samples/community/boards").resolve(file));
            for(var method:NccMethod.values()) {
                var result=NccGenerator.generate(image.units(),image.solidGeometry(),new NccParameters(
                        List.of(.4,1.2,.8),.4,1,method,true,true,0,true,NccOrder.NONE,
                        new NccBoundary.Itself(),List.of(.2)));
                assertHandoffs(result,1,file+" "+method);
                System.out.println("REST-PUBLIC "+file+" "+method.name()+" areas="+result.toolResults().stream()
                        .filter(t->t.operation()==NccOperation.CLEAR).map(t->t.targetArea().getArea()).toList());
            }
        }
    }

    private static void assertHandoffs(NccResult result,double scale,String description) {
        var f=result.geometry().getFactory();
        var clear=result.toolResults().stream().filter(t->t.operation()==NccOperation.CLEAR).toList();
        assertEquals(3,clear.size(),"The audit must actually exercise three CLEAR cutters");
        for(int i=0;i<clear.size()-1;i++) {
            var previous=clear.get(i);var next=clear.get(i+1);
            var sweptParts=new ArrayList<Geometry>();
            for(int j=0;j<previous.geometry().getNumGeometries();j++)sweptParts.add(CutterSweep.stroke(
                    previous.geometry().getGeometryN(j),previous.toolDiameter()/2,64,CancellationToken.none()));
            Geometry swept=sweptParts.isEmpty()?f.createPolygon():OverlayNGRobust.union(sweptParts);
            double physicallyRemoved=previous.targetArea().intersection(swept).getArea();
            double handedDown=previous.targetArea().getArea()-next.targetArea().getArea();
            // Legacy Seed uses 16-segment arcs for stock subtraction; the independent physical
            // sweep uses 64. Permit 1% area discrepancy, not a reset to the original stock.
            assertTrue(handedDown+1e-6*scale*scale>=.99*physicallyRemoved,
                    ()->description+": next cutter ignored earlier cleared stock");
            assertTrue(previous.targetArea().buffer(1e-9*scale).covers(next.targetArea()),
                    "Rest must never reset to the whole original clearing area");
        }
    }
}
