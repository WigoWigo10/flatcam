package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javafx.scene.control.TreeItem;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class CncNativePrecisionTest {
    private static final GeometryFactory F=new GeometryFactory();
    @Test void geometryReviewShowsRequestedAndEncodedRolandValuesAndRapidMode() {
        var line=F.createLineString(new Coordinate[]{new Coordinate(10.00125,7),new Coordinate(12,7)});
        var p=new GeometryGCodeParameters(3,.6006,false,1,125,0,false,0,null,75,false,0,false,0);
        var report=CncPreflight.geometry("Roland","MM",new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.8,line)),p,Map.of(),GCodePreprocessor.ROLAND_MDX_20,Map.of()));
        assertTrue(report.canContinue());
        assertEquals("125 → 126",report.tools().getFirst().feedXY());
        assertEquals("75 → 72",report.tools().getFirst().feedZ());
        assertEquals("-0.6006 → -0.6",report.tools().getFirst().cutZ());
        assertTrue(report.common().stream().anyMatch(v->v.label().equals("Avanço rápido") && v.text().contains("900") && !v.text().contains("G0")));
        assertTrue(report.issues().stream().anyMatch(i->!i.blocking() && i.target().field().equals("cnc-feed") && i.text().contains("126")));
        assertTrue(report.tools().getFirst().details().stream().anyMatch(v->v.text().contains("10.00125 → 10.0025")));
    }
    @Test void hpglReviewIncludesNumericLimitsAndLabeledSamplesInMmAndInches() {
        for(String units:List.of("MM","IN")) {
            double s=units.equals("MM") ? 1 : 1/25.4;
            var line=F.createLineString(new Coordinate[]{new Coordinate(10.013*s,7*s),new Coordinate(12*s,7*s)});
            var p=new GeometryGCodeParameters(3*s,s,false,s,100*s,0,false);
            var report=CncPreflight.geometry("HPGL",units,new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4*s,line)),p,Map.of(),GCodePreprocessor.HPGL,Map.of()));
            assertTrue(report.canContinue());
            assertTrue(report.common().stream().anyMatch(v->v.label().contains("Grade XY") && v.text().contains("0,025 mm")));
            assertTrue(report.common().stream().anyMatch(v->v.text().contains("Não são os limites físicos")));
            assertTrue(report.common().stream().anyMatch(v->v.text().contains("Não é uma varredura")));
            assertTrue(report.issues().stream().anyMatch(i->!i.blocking() && i.text().contains("arredondada")));
            assertEquals("—",report.tools().getFirst().feedXY());
        }
    }
    @Test void drillingReviewIncludesEffectiveOffsetDepthFeedAndEndPositions() {
        var image=ExcellonImage.of("MM",Map.of(1,.8),List.of(new ExcellonImage.Drill(1,10.013,7)),List.of(),F.createPolygon());
        var source=new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("Drills"),image,Map.of());
        var p=new DrillGCodeParameters(3,.5006,125,0,false,false,1,false,0,.1);
        var options=new GCodeGenerator.DrillJobOptions(false,15,3.00125,25.013,9.,0,null);
        var report=CncPreflight.drilling(new DrillGCodeToolPanel.Result(source,Map.of(1,p),List.of(1),options,GCodePreprocessor.ROLAND_MDX_20,org.flatcam.app.project.DrillCncSettings.ToolOrder.REVERSE));
        assertTrue(report.canContinue());
        assertEquals("125 → 126",report.tools().getFirst().feedZ());
        assertEquals("-0.6006 → -0.6",report.tools().getFirst().cutZ());
        assertTrue(report.common().stream().anyMatch(v->v.label().equals("End Z solicitado → arquivo") && v.text().contains("3.0025")),
                () -> "Unexpected native End Z values: " + report.common());
    }
    @Test void sampledOverflowAndUnrepresentableRolandHeightBlockReview() {
        var line=F.createLineString(new Coordinate[]{new Coordinate(820,7),new Coordinate(822,7)});
        var p=new GeometryGCodeParameters(3,.00001,false,1,120,0,false);
        var hpgl=CncPreflight.geometry("Overflow","MM",new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4,line)),p,Map.of(),GCodePreprocessor.HPGL,Map.of()));
        assertFalse(hpgl.canContinue());
        var roland=CncPreflight.geometry("Tiny depth","MM",new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4,line)),p,Map.of(),GCodePreprocessor.ROLAND_MDX_20,Map.of()));
        assertFalse(roland.canContinue());
    }
}
