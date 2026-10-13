package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

class MachiningCoverageTest {
    final GeometryFactory factory = new GeometryFactory();
    final GeometryGCodeParameters params = new GeometryGCodeParameters(3, .1, false, 1, 100, 10000, true);
    Geometry line(double y) { return factory.createLineString(new Coordinate[]{new Coordinate(0,y), new Coordinate(10,y)}); }
    @Test void individualAndAccumulatedRemainingAreDifferentAndSourceIsNotMutated() {
        var area = factory.toGeometry(new Envelope(0,10,0,2));
        var tools = List.of(new ToolGeometry(1, line(.5)), new ToolGeometry(1, line(1.5)));
        var target = new MachiningTarget("NCC", "MM", area, Map.of(0,area,1,area), MachiningTarget.signature(tools, CancellationToken.none()));
        var before = tools.stream().map(tool -> tool.geometry().toText()).toList();
        var result = MachiningCoverage.calculate(target, "MM", tools, params, Map.of(), CancellationToken.none(), fraction -> {});
        assertEquals(20, result.targetArea()); assertEquals(10, result.steps().getFirst().ownRemaining().getArea(), 1e-8);
        assertEquals(10, result.steps().getLast().ownRemaining().getArea(), 1e-8);
        assertTrue(result.finalRemaining().isEmpty()); assertEquals(0, result.steps().getLast().cumulativeRemaining().getArea());
        assertEquals(before, tools.stream().map(tool -> tool.geometry().toText()).toList()); assertEquals(20, area.getArea());
    }
    @Test void polygonPathsDoNotFalselyClearTheirFilledInterior() throws Exception {
        var polygon = new WKTReader().read("POLYGON ((0 0,10 0,10 10,0 10,0 0),(4 4,6 4,6 6,4 6,4 4))");
        var tools = List.of(new ToolGeometry(.4, polygon));
        var target = new MachiningTarget("NCC", "MM", polygon, Map.of(0,polygon), MachiningTarget.signature(tools, CancellationToken.none()));
        var result = MachiningCoverage.calculate(target, "MM", tools, params, Map.of(), CancellationToken.none(), fraction -> {});
        assertTrue(result.finalRemaining().getArea() > 70, "Only the boundary strips, not the entire filled polygon, were cut");
        assertFalse(result.finalRemaining().contains(factory.createPoint(new Coordinate(5,5))), "The target's hole is not unmachined material");
    }
    @Test void compensationUnitsStaleBindingsAndCancellationAreChecked() {
        var area = factory.toGeometry(new Envelope(0,10,0,2));
        var tools = List.of(new ToolGeometry(1,line(.5)));
        var target = new MachiningTarget("NCC","MM",area,Map.of(0,area),MachiningTarget.signature(tools,CancellationToken.none()));
        var plain = MachiningCoverage.calculate(target,"MM",tools,params,Map.of(),CancellationToken.none(),fraction -> {});
        var offset = MachiningCoverage.calculate(target,"MM",tools,params,Map.of(0,params.withCompensation(ToolPathOffset.CUSTOM,.2)),CancellationToken.none(),fraction -> {});
        assertTrue(offset.finalRemaining().getArea() < plain.finalRemaining().getArea());
        assertThrows(IllegalArgumentException.class, () -> MachiningCoverage.calculate(target,"IN",tools,params,Map.of(),CancellationToken.none(),fraction -> {}));
        assertThrows(IllegalArgumentException.class, () -> MachiningCoverage.calculate(target,"MM",List.of(new ToolGeometry(1,line(1))),params,Map.of(),CancellationToken.none(),fraction -> {}));
        assertThrows(java.util.concurrent.CancellationException.class, () -> MachiningCoverage.calculate(target,"MM",tools,params,Map.of(),() -> true,fraction -> {}));
    }
    @Test void isoCanContributeWithoutClaimingAnIndividualClearingTarget() {
        var area = factory.toGeometry(new Envelope(0,10,0,2));
        var tools = List.of(new ToolGeometry(1,line(.5)), new ToolGeometry(1,line(1.5)));
        var target = new MachiningTarget("NCC","MM",area,Map.of(1,area),MachiningTarget.signature(tools,CancellationToken.none()));
        var result = MachiningCoverage.calculate(target,"MM",tools,params,Map.of(),CancellationToken.none(),fraction -> {});
        assertFalse(result.steps().getFirst().hasIndividualTarget()); assertTrue(result.finalRemaining().isEmpty());
    }
}
