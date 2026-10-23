package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import javafx.scene.control.TreeItem;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class CncPreflightTest {
    @Test void iselReviewDistinguishesRemoteStopDwellUnitsAndToolChangeMacros() {
        var result=geometry();
        var report=CncPreflight.geometry("ISEL","MM",new GeometryCncToolPanel.Result(result.tools(),result.parameters(),
                result.vTools(),GCodePreprocessor.ISEL_CNC,result.parametersByTool()));
        assertTrue(report.canContinue());
        for(String token:List.of("milissegundos","M01","Remote","M30","T1–T128","macro"))
            assertTrue(report.issues().stream().anyMatch(i->i.text().contains(token)),token);
        assertFalse(CncPreflight.geometry("GRBL","MM",result).issues().stream().anyMatch(i->i.text().contains("ISEL")));
    }
    @Test void marlinReviewExplainsFirmwarePreconditionsWithoutClaimingPhysicalRpmOrRapidSpeed() {
        var original=geometry();
        var geometryReport=CncPreflight.geometry("Marlin","MM",new GeometryCncToolPanel.Result(
                original.tools(),original.parameters(),original.vTools(),GCodePreprocessor.MARLIN,original.parametersByTool()));
        var drill=drilling();
        var drillReport=CncPreflight.drilling(new DrillGCodeToolPanel.Result(drill.source(),drill.settingsByTool(),
                drill.orderedToolIds(),drill.options(),GCodePreprocessor.MARLIN,drill.toolOrder()));
        for(var report:List.of(geometryReport,drillReport)) {
            assertTrue(report.canContinue());
            for(String setting:List.of("CUTTER_POWER_UNIT=RPM","Tn/M6","G0_FEEDRATE","INCH_MODE_SUPPORT","parada física"))
                assertTrue(report.issues().stream().anyMatch(issue->issue.text().contains(setting)),setting);
        }
        assertFalse(CncPreflight.geometry("GRBL","MM",original).issues().stream().anyMatch(i->i.text().contains("G0_FEEDRATE")));
    }
    @Test void closedContinuousReviewDoesNotClaimThatDirectionAlternates() {
        var base=params(3,.6,200,75,10000).withPassStrategy(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION);
        var report=CncPreflight.geometry("Ring","MM",new GeometryCncToolPanel.Result(List.of(new ToolGeometry(1,path())),base,Map.of(),GCodePreprocessor.GRBL_11_NO_M6,Map.of()));
        assertTrue(report.tools().getFirst().details().stream().anyMatch(value->value.label().equals("Estratégia")&&value.text().contains("mesma direção (contornos fechados)")));
        assertTrue(report.issues().stream().anyMatch(issue->issue.text().contains("exigem contornos fechados")));
        assertFalse(report.issues().stream().anyMatch(issue->issue.text().contains("invertem o sentido")));
    }
    static final GeometryFactory FACTORY = new GeometryFactory();
    @Test void manualReviewShowsTheActualPerToolAdjustmentFeedsAndCustomExplainsItsMacro() {
        var original=geometry();
        var manual=CncPreflight.geometry("Board","MM",new GeometryCncToolPanel.Result(original.tools(),original.parameters(),
                original.vTools(),GCodePreprocessor.TOOLCHANGE_MANUAL,original.parametersByTool()));
        for(var tool:manual.tools()) assertTrue(tool.details().stream().anyMatch(v->v.label().equals("Ajuste manual em Z0")
                && v.text().contains("Feed Z "+tool.feedZ()) && v.target().field().equals("cnc-feed-z")));
        assertTrue(manual.issues().stream().anyMatch(i->!i.blocking() && i.text().contains("três pausas M0") && i.text().contains("G90")));
        var drill=drilling();
        var drillReport=CncPreflight.drilling(new DrillGCodeToolPanel.Result(drill.source(),drill.settingsByTool(),drill.orderedToolIds(),
                drill.options(),GCodePreprocessor.TOOLCHANGE_MANUAL,drill.toolOrder()));
        for(var tool:drillReport.tools()) assertTrue(tool.details().stream().anyMatch(v->v.label().equals("Ajuste manual em Z0")
                && v.text().contains("Feed Z "+tool.feedZ()) && v.target().field().equals("drill-feed-z")));
        var custom=CncPreflight.geometry("Board","MM",new GeometryCncToolPanel.Result(original.tools(),original.parameters(),
                original.vTools(),GCodePreprocessor.TOOLCHANGE_CUSTOM,original.parametersByTool()));
        assertTrue(custom.issues().stream().anyMatch(i->!i.blocking() && i.text().contains("Tn/M6") && i.text().contains("sem M0 adicional")));
    }
    static Geometry path() { return FACTORY.createLineString(new Coordinate[]{new Coordinate(10, 0), new Coordinate(12, 0)}); }
    static GeometryGCodeParameters params(double z, double depth, double feed, double feedZ, int rpm) {
        return new GeometryGCodeParameters(z, depth, true, .2, feed, rpm, true, 1500, null, feedZ, true, .5, false, 0);
    }
    static GeometryCncToolPanel.Result geometry() {
        var base = params(3, .3, 200, 70, 10000).withJobOptions(new GeometryJobOptions(null, .5, 25., 2., 15., -2., -3.));
        var second = params(4, .55, 150, 50, 12000).withJobOptions(base.jobOptions());
        return new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4, path()), new ToolGeometry(.8, path())),
                base, Map.of(), GCodePreprocessor.GRBL_11_NO_M6, Map.of(1, second));
    }
    @Test void perToolValuesCommonClearanceAndPositionsAreNotConflated() {
        var report = CncPreflight.geometry("Board", "MM", geometry());
        assertTrue(report.canContinue()); assertEquals("mm", report.units());
        assertEquals(List.of("T1", "T2"), report.tools().stream().map(CncPreflight.Tool::name).toList());
        var tool = report.tools().get(1);
        assertEquals("-0.55", tool.cutZ()); assertEquals("4", tool.travelZ());
        assertEquals("150", tool.feedXY()); assertEquals("50", tool.feedZ()); assertEquals("12000", tool.power());
        assertTrue(tool.details().stream().anyMatch(value -> value.text().contains("3 passe(s)")));
        assertTrue(report.common().stream().anyMatch(value -> value.label().equals("Altura dos deslocamentos XY") && value.text().startsWith("4 ")));
        assertTrue(report.tools().getFirst().details().stream().anyMatch(value -> value.label().equals("Retração / Travel Z efetivo")
                && value.text().startsWith("4 mm") && value.text().contains("nesta fresa: 3")
                && value.target().equals(new CncPreflight.Target(0,"cnc-safe-z"))));
        assertTrue(report.common().stream().anyMatch(value -> value.label().equals("Tool change X,Y") && value.text().equals("-2; -3")));
        assertTrue(report.common().stream().anyMatch(value -> value.label().equals("Avanço rápido") && value.text().contains("controlador")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("Estacionamento XY em Z 4")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("Cobertura não calculada")));
    }
    @Test void individualTravelReviewReportsEffectiveHeightsAndLastActiveParking() {
        var original=geometry();
        var options=original.parameters().jobOptions().withTravelClearance(GeometryTravelClearance.PER_TOOL);
        var first=params(4,.3,200,70,10000).withJobOptions(options);
        var second=params(3,.55,150,50,12000).withJobOptions(options);
        var report=CncPreflight.geometry("Board","MM",new GeometryCncToolPanel.Result(original.tools(),first,Map.of(),
                original.preprocessor(),Map.of(1,second)));
        assertTrue(report.tools().getFirst().details().stream().anyMatch(value -> value.label().equals("Retração / Travel Z efetivo")&&value.text().startsWith("4 mm")));
        assertTrue(report.tools().get(1).details().stream().anyMatch(value -> value.label().equals("Retração / Travel Z efetivo")&&value.text().startsWith("3 mm")));
        assertTrue(report.common().stream().anyMatch(value -> value.label().equals("Política de Travel Z")
                &&value.target().field().equals("cnc-travel-clearance")&&value.text().contains("cada ferramenta")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("Estacionamento XY em Z 3")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().startsWith("Travel Z individual")&&!issue.blocking()));
    }
    @Test void vTipUsesTheCalculatedDepthNotTheUnusedDraftAndInchValuesAreNotConvertedTwice() {
        var p = params(.1, .02, 4, 2, 10000);
        var tip = new VTipSettings(.001, 60);
        var result = new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.01, path(), ToolProfile.V)),
                p, Map.of(0, tip), GCodePreprocessor.GRBL_11_NO_M6, Map.of());
        var report = CncPreflight.geometry("IN board", "IN", result);
        assertEquals("in", report.units()); assertEquals("0.01", report.tools().getFirst().diameter());
        assertEquals(CncPreflight.number(-tip.cutDepth(.01)), report.tools().getFirst().cutZ());
        assertNotEquals("-0.02", report.tools().getFirst().cutZ());
    }
    @Test void differentDiametersWithoutChangeAreBlockedAndEmptyToolsAreReported() {
        var result = geometry(); var p = new GeometryGCodeParameters(3, .1, false, 1, 100, 0, false);
        var blocked = CncPreflight.geometry("Board", "MM", new GeometryCncToolPanel.Result(result.tools(), p, Map.of(), result.preprocessor(), Map.of()));
        assertFalse(blocked.canContinue());
        assertTrue(blocked.issues().stream().anyMatch(issue -> issue.blocking() && issue.target().field().equals("cnc-tool-change")));
        var empty = CncPreflight.geometry("Empty", "MM", new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4, FACTORY.createPolygon())), p, Map.of(), result.preprocessor(), Map.of()));
        assertFalse(empty.canContinue()); assertTrue(empty.tools().getFirst().name().contains("vazia"));
        var spindleOff = new GeometryGCodeParameters(3, .3, true, .1, 100, 0, false, 0, null, 50, true, 1, false, 0);
        var inactive = CncPreflight.geometry("Board", "MM", new GeometryCncToolPanel.Result(List.of(result.tools().getFirst()),
                spindleOff, Map.of(), result.preprocessor(), Map.of()));
        assertTrue(inactive.tools().getFirst().details().stream().anyMatch(value -> value.label().equals("Dwell") && value.text().startsWith("Inativo")));
        assertTrue(inactive.issues().stream().anyMatch(issue -> issue.text().contains("RPM zero")&&issue.text().contains("parada final")));
    }
    static DrillGCodeToolPanel.Result drilling() {
        var image = ExcellonImage.of("MM", Map.of(2, .8, 7, 1.2, 11, 1.5),
                List.of(new ExcellonImage.Drill(2, 10, 0), new ExcellonImage.Drill(7, 20, 0), new ExcellonImage.Drill(11, 30, 0)),
                List.of(new ExcellonImage.Slot(7, 20, 2, 22, 2)), FACTORY.createPolygon());
        var p = new DrillGCodeParameters(3, 1, 100, 10000, true, true, .4, true, .5, .2);
        return new DrillGCodeToolPanel.Result(new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("Drills"), image, Map.of()),
                Map.of(2, p, 7, p), List.of(7, 2), new GCodeGenerator.DrillJobOptions(true, 15, .5, 0., 0.),
                GCodePreprocessor.GRBL_11_NO_M6, DrillCncSettings.ToolOrder.REVERSE);
    }
    @Test void drillOrderEffectiveOffsetSlotsAndUnselectedHitsAreExplicit() {
        var report = CncPreflight.drilling(drilling());
        assertEquals(List.of("T7", "T2"), report.tools().stream().map(CncPreflight.Tool::name).toList());
        assertEquals("-1.2", report.tools().getFirst().cutZ());
        assertTrue(report.tools().getFirst().details().stream().anyMatch(value -> value.label().equals("Furos / rasgos") && value.text().equals("1 / 1")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("1 furo(s)/rasgo(s)")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("rasgos são fresados")));
        assertEquals(new CncPreflight.Target(7, "drill-cut-z"), report.tools().getFirst().details().get(1).target());
        var initial = drilling();
        var settings = new java.util.HashMap<>(initial.settingsByTool());
        settings.put(11, new DrillGCodeParameters(12, 1, 100, 10000, true));
        var image = ExcellonImage.of("MM", initial.source().image().toolDiameters(),
                initial.source().image().drills().stream().filter(hit -> hit.toolId() != 11).toList(), List.of(), FACTORY.createPolygon());
        var withEmpty = CncPreflight.drilling(new DrillGCodeToolPanel.Result(
                new DrillGCodeToolPanel.SourceCandidate(initial.source().item(), image, Map.of()), settings,
                List.of(2, 11), initial.options(), initial.preprocessor(), initial.toolOrder()));
        assertTrue(withEmpty.tools().getLast().name().contains("vazia"));
        assertTrue(withEmpty.issues().stream().anyMatch(issue -> issue.text().contains("Estacionamento XY em Z 3")));
        assertFalse(withEmpty.issues().stream().anyMatch(issue -> issue.text().contains("Estacionamento XY em Z 12")));
    }
    @Test void probingPositionsAndLaserFocusDoNotMasqueradeAsNormalMillingPositions() {
        var probe = new ProbeToolChangeParameters(15, -5, 50, .5, 2., 3.);
        var p = new GeometryGCodeParameters(3, .1, false, 1, 100, 10000, true, 0, probe);
        var report = CncPreflight.geometry("Probe", "MM", new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4, path())),
                p, Map.of(), GCodePreprocessor.TOOLCHANGE_PROBE_MACH3, Map.of()));
        assertTrue(report.common().stream().anyMatch(value -> value.label().equals("Tool change Z (sonda)") && value.text().equals("15")));
        assertTrue(report.common().stream().anyMatch(value -> value.target().field().equals("probe-xy") && value.text().equals("2; 3")));
        var laser = CncPreflight.geometry("Laser", "MM", new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4, path())),
                new GeometryGCodeParameters(3, .1, false, 1, 100, 100, false), Map.of(), GCodePreprocessor.Z_LASER, Map.of()));
        assertEquals("—", laser.tools().getFirst().cutZ()); assertEquals("3", laser.tools().getFirst().travelZ());
        assertTrue(laser.tools().getFirst().details().stream().anyMatch(value -> value.label().equals("Foco / posição Z") && value.text().equals("3")));
        assertFalse(laser.common().stream().anyMatch(value -> value.label().equals("End Z")));
    }
    @Test void lineXyzExplainsVerticalBootstrapAndUnknownMacroPosition() {
        var report = CncPreflight.geometry("Board", "MM", new GeometryCncToolPanel.Result(
                List.of(new ToolGeometry(.4, path())), params(3, .3, 100, 50, 10000), Map.of(),
                GCodePreprocessor.LINE_XYZ, Map.of()));
        assertTrue(report.issues().stream().anyMatch(issue -> !issue.blocking()
                && issue.text().contains("preambulo vertical") && issue.text().contains("M6")));
    }
    @Test void icpExplainsIntegerResolutionAutomaticMacroAndRapidTimingLimits() {
        var report = CncPreflight.geometry("Board", "MM", new GeometryCncToolPanel.Result(
                List.of(new ToolGeometry(.4, path())), params(3, .3, 100, 50, 10000), Map.of(),
                GCodePreprocessor.ISEL_ICP_CNC, Map.of()));
        assertTrue(report.issues().stream().anyMatch(issue -> !issue.blocking() && issue.text().contains("FASTVEL")));
        assertTrue(report.issues().stream().anyMatch(issue -> !issue.blocking() && issue.text().contains("GETTOOL")
                && issue.text().contains("WPCLEAR")));
    }
    @Test void startHeightDoesNotMasqueradeAsTheFirstChangeHeightAndOverExplainsItsTransitions() {
        var area=CncExclusionArea.of(FACTORY.toGeometry(new Envelope(4,6,-1,1)),CncExclusionArea.Strategy.OVER,8);
        var positions=new GeometryJobOptions(12.,5.,null,null,15.,null,null).withExclusions(true,List.of(area));
        var base=params(3,.3,100,50,10000).withJobOptions(positions);
        var result=new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.4,path())),base,Map.of(),GCodePreprocessor.GRBL_11_NO_M6,Map.of());
        var report=CncPreflight.geometry("Board","MM",result);
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().startsWith("Start Z 12")
                && issue.text().contains("Tool change Z 15") && issue.target().field().equals("cnc-change-z") && !issue.blocking()));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.text().startsWith("Over sobe")
                && issue.target().field().equals("cnc-exclusions-pane")));
        var drill=drilling();
        var options=drill.options().withPositions(12.,null,null).withExclusions(true,List.of(area));
        var reviewed=CncPreflight.drilling(new DrillGCodeToolPanel.Result(drill.source(),drill.settingsByTool(),
                drill.orderedToolIds(),options,drill.preprocessor(),drill.toolOrder()));
        assertTrue(reviewed.issues().stream().anyMatch(issue -> issue.text().startsWith("Start Z 12")
                && issue.target().field().equals("probe-change-z") && !issue.blocking()));
        var same=params(3,.3,100,50,10000).withJobOptions(new GeometryJobOptions(15.,5.,null,null,15.,null,null));
        var equal=CncPreflight.geometry("Board","MM",new GeometryCncToolPanel.Result(result.tools(),same,Map.of(),result.preprocessor(),Map.of()));
        assertFalse(equal.issues().stream().anyMatch(issue -> issue.text().startsWith("Start Z")));
    }
}
