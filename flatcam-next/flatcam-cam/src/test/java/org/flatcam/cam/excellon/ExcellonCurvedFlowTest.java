package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.transform.TransformOp;
import org.flatcam.cam.panel.Panelize;
import org.flatcam.cam.merge.ExcellonJoin;
import org.flatcam.cam.convert.ObjectConversion;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class ExcellonCurvedFlowTest {
    private static final GeometryFactory F=new GeometryFactory();
    private ExcellonImage image() { return ExcellonAdvancedRoutingTest.quarter(true); }

    @Test void editMoveCopyResizeAndHistoryKeepEveryVertexAndToolAssociation() {
        var source=image(); var original=source.slots().getFirst();
        var editing=new ExcellonEditSession(source);
        assertEquals("Routed slot",editing.rows().getFirst().type());
        editing.selectIds(List.of(editing.rows().getFirst().id()));
        assertTrue(editing.moveSelected(3,4));
        var moved=editing.resultImage().slots().getFirst();
        assertEquals(original.path().size(),moved.path().size());
        for(int i=0;i<original.path().size();i++) {
            assertEquals(original.path().get(i).x()+3,moved.path().get(i).x());
            assertEquals(original.path().get(i).y()+4,moved.path().get(i).y());
        }
        assertTrue(editing.copySelected(5,0)); assertEquals(2,editing.resultImage().totalSlots());
        editing.resizeSelected(.4); assertEquals(.4,editing.resultImage().toolDiameters().get(editing.resultImage().slots().getLast().toolId()));
        assertEquals(original.path().size(),editing.resultImage().slots().getLast().path().size());
        assertTrue(editing.undo()); assertTrue(editing.undo()); assertTrue(editing.undo());
        assertEquals(source.slots(),editing.resultImage().slots()); assertTrue(editing.redo());
        assertEquals(original,source.slots().getFirst());
    }

    @Test void transformPanelizeAndJoinRetainTheWholeCurveRatherThanItsChord() {
        var source=image();
        var rotated=source.transformed(new TransformOp.Rotate(90,new Coordinate(0,0)));
        for(var p:rotated.slots().getFirst().path()) assertEquals(1,Math.hypot(p.x(),p.y()),1e-12);
        var panel=Panelize.excellon(source,new Panelize.Layout(2,2,false,5,4));
        assertEquals(4,panel.totalSlots());
        for(var slot:panel.slots()) assertEquals(source.slots().getFirst().path().size(),slot.path().size());
        var joined=ExcellonJoin.join(List.of(source,panel),true);
        assertEquals(5,joined.totalSlots()); assertTrue(joined.slots().stream().allMatch(ExcellonImage.Slot::hasPath));
        var converted=ObjectConversion.excellonToGerber(source);
        assertEquals(source.solidGeometry().getArea(),converted.solidGeometry().getArea(),1e-8);
        var buffered=source.transformed(new TransformOp.Buffer(.1,false,true));
        assertTrue(buffered.solidGeometry().covers(F.createPoint(new Coordinate(Math.sqrt(.5),-Math.sqrt(.5)))));
        assertFalse(buffered.solidGeometry().covers(F.createPoint(new Coordinate(.5,-.5))));
    }

    @Test void routedExportContainsAllVerticesButG85IsRefusedBeforeWritingLossyData() {
        var source=image(); var exporter=new ExcellonExporter();
        var format=new ExcellonExporter.Format("MM",true,3,6,true,ExcellonExporter.SlotStyle.ROUTED);
        String text=exporter.export(source,format);
        assertEquals(source.slots().getFirst().path().size()-1,text.lines().filter(l->l.startsWith("G01")).count());
        assertEquals(1,text.lines().filter(l->l.equals("M15")).count());
        var read=new ExcellonParser().parse(text.lines().toList());
        double areaDifference=source.solidGeometry().symDifference(read.solidGeometry()).getArea();
        double coordinateRoundingBound=(source.solidGeometry().getBoundary().getLength()+read.solidGeometry().getBoundary().getLength())*1e-6;
        assertTrue(areaDifference<coordinateRoundingBound,"area difference "+areaDifference+" exceeds the 6-decimal export bound "+coordinateRoundingBound);
        assertThrows(IllegalArgumentException.class,()->exporter.export(source));
    }

    @Test void cncFollowsTheCurveWithOnePlungePerPassAndReparsedCodeHasTheSameFootprint() {
        var source=image(); var params=new DrillGCodeParameters(3,1,100,0,false);
        var job=GCodeGenerator.generateDrillCncJob(source,params,null);
        assertEquals(1,job.gcode().lines().filter(l->l.matches("G0?1 Z-.*")).count());
        assertEquals(source.slots().getFirst().path().size()-1,job.gcode().lines().filter(l->l.matches("G0?1 X.*")).count());
        var parsed=GCodeToolpathParser.parse(job.gcode(),CancellationToken.none(),ProgressCallback.none());
        // Reloaded CNC previews are multipart buffered moves with overlapping caps, not a normalized MultiPolygon.
        Geometry parsedFootprint=org.locationtech.jts.operation.overlayng.OverlayNGRobust.union(parsed.cutGeometry());
        assertTrue(parsedFootprint.covers(F.createPoint(new Coordinate(Math.sqrt(.5),-Math.sqrt(.5)))));
        assertFalse(parsedFootprint.covers(F.createPoint(new Coordinate(.5,-.5))));
        assertTrue(source.solidGeometry().symDifference(job.cutGeometry()).getArea()<1e-9);
    }

    @Test void exclusionsValidateTheArcInsteadOfTheEmptySpaceAlongItsChord() {
        var source=new ExcellonParser().parse(ExcellonAdvancedRoutingTest.file("METRIC","G00X10.0Y0.0","M15","G02X0.0Y-10.0A10.0","M16"));
        var params=new DrillGCodeParameters(3,1,100,0,false);
        var options=new GCodeGenerator.DrillJobOptions(false,15,.5,null,null);
        var onCurve=CncExclusionArea.of(F.toGeometry(new Envelope(6.95,7.2,-7.2,-6.95)),CncExclusionArea.Strategy.OVER,5);
        assertThrows(IllegalArgumentException.class,()->GCodeGenerator.generateDrillCncJob(source,Map.of(1,params),List.of(1),options.withExclusions(true,List.of(onCurve))));
        var onChord=CncExclusionArea.of(F.toGeometry(new Envelope(4.95,5.05,-5.05,-4.95)),CncExclusionArea.Strategy.OVER,5);
        assertDoesNotThrow(()->GCodeGenerator.generateDrillCncJob(source,Map.of(1,params),List.of(1),options.withExclusions(true,List.of(onChord))));
    }

    @Test void drillSlotsInterpolateAlongTheCurveAndMillingConversionUsesTheCurvedBoundary() {
        var source=image(); var hits=new ArrayList<ExcellonImage.Drill>();
        new DrillSlotParameters(true,0,true).emit(source.slots().getFirst(),.2,CancellationToken.none(),ProgressCallback.none(),hits::add);
        assertTrue(hits.size()>=8);
        for(var hit:hits) assertEquals(1,Math.hypot(hit.x(),hit.y()),.00101);
        assertEquals(new ExcellonImage.Drill(1,0,-1),hits.getLast());
        Geometry milling=ExcellonMillingGenerator.generate(source,Set.of(1),.1,ExcellonMillingGenerator.Kind.SLOTS,CancellationToken.none());
        assertTrue(milling.distance(F.createPoint(new Coordinate(Math.sqrt(.5),-Math.sqrt(.5))))<.051);
        assertTrue(milling.distance(F.createPoint(new Coordinate(.5,-.5)))>.2);
    }
}
