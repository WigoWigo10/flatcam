package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.*;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class MachiningZUiTest {
    @BeforeEach @AfterEach void defaults() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }

    @Test void geometryUsesSignedCutZLikeDrilling() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new AtomicReference<GeometryCncToolPanel.Result>();
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,0)});
            var form = GeometryCncToolPanel.build("MM", path, List.of(), output::set, () -> {});
            ((TextField) form.lookup("#cnc-cut-depth")).setText("-0,2");
            ((Button) form.lookup("#cnc-generate")).fire();
            assertNotNull(output.get(), ((Label) form.lookup("#cnc-error")).getText());
            assertEquals(.2, output.get().parameters().cutDepth()); return null;
        });
    }
    @Test void standardDrillingConvertsPositiveCutZInsteadOfRejectingIt() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new AtomicReference<DrillGCodeToolPanel.Result>();
            var image = new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y0.0","M30"));
            var source = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("board"),image,Map.of());
            var form = DrillGCodeToolPanel.build(List.of(source),source,List::of,output::set,()->{});
            ((TextField) form.lookup("#drill-cut-z")).setText("0,2");
            ((Button) form.lookup("#drill-generate")).fire();
            assertNotNull(output.get(), ((Label) form.lookup("#drill-error")).getText());
            assertEquals(.2, output.get().settingsByTool().get(1).drillDepth()); return null;
        });
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> coordinates() {
        var cases=new ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for(String tool:List.of("Geometry","Drilling","Cutout")) for(String units:List.of("MM","IN"))
            for(boolean machinist:List.of(false,true)) for(double z:new double[]{-.2,0,.2})
                cases.add(org.junit.jupiter.params.provider.Arguments.of(tool,units,machinist,z));
        return cases.stream();
    }
    @ParameterizedTest @MethodSource("coordinates")
    void everyPanelUsesTheChosenPolicyAndReportsTheExecutedZ(String tool,String units,boolean machinist,double z) throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("cnc.machinist",true,Boolean.toString(machinist));
            var output=new AtomicReference<Object>(); var factory=new GeometryFactory();
            var path=factory.createLineString(new Coordinate[]{new Coordinate(1,0),new Coordinate(2,0)});
            javafx.scene.Node form; String cutId,buttonId,errorId,noteId;
            if(tool.equals("Geometry")) {
                form=GeometryCncToolPanel.build(units,path,List.of(),output::set,()->{});
                cutId="cnc-cut-depth"; buttonId="cnc-generate"; errorId="cnc-error"; noteId="cnc-z-policy-note";
                ((TextField)form.lookup("#cnc-safe-z")).setText("-0,5");
            } else if(tool.equals("Drilling")) {
                var image=new ExcellonParser().parse(List.of("M48",units.equals("MM") ? "METRIC" : "INCH","T1C1.0","%","T1","X1.0Y0.0","M30"));
                var source=new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("board"),image,Map.of());
                form=DrillGCodeToolPanel.build(List.of(source),source,List::of,output::set,()->{});
                cutId="drill-cut-z"; buttonId="drill-generate"; errorId="drill-error"; noteId="drill-z-policy-note";
                ((TextField)form.lookup("#drill-travel-z")).setText("-0,5");
            } else {
                form=CutoutToolPanel.build(units,(polygon,done,cancel)->false,()->{},output::set,()->{});
                cutId="cutout-cut-z"; buttonId="cutout-generate-freeform"; errorId="cutout-error"; noteId="cutout-z-policy-note";
            }
            var field=(TextField)form.lookup("#"+cutId); field.setText(Double.toString(z).replace('.',','));
            ((Button)form.lookup("#"+buttonId)).fire();
            if(!machinist && z==0) { assertNull(output.get()); assertTrue(((Label)form.lookup("#"+errorId)).getText().contains("zero")); return null; }
            assertNotNull(output.get(),((Label)form.lookup("#"+errorId)).getText());
            double target=machinist ? z : -Math.abs(z); double actual;
            if(output.get() instanceof GeometryCncToolPanel.Result r) {
                actual=r.parameters().cutZ(); var report=CncPreflight.geometry("board",units,r);
                assertEquals(CncPreflight.number(target),report.tools().getFirst().cutZ()); assertTrue(report.canContinue());
                assertEquals(machinist ? -.5 : .5,r.parameters().safeZ());
                var code=GCodeGenerator.generateGeometryCncJob(units,path,r.parameters(),1,CancellationToken.none()).gcode();
                assertTrue(code.contains("G1 Z"+String.format(java.util.Locale.ROOT,"%.4f",target==0 ? 0 : target)),code);
            } else if(output.get() instanceof DrillGCodeToolPanel.Result r) {
                actual=r.settingsByTool().get(1).cutZ(); var report=CncPreflight.drilling(r);
                assertEquals(CncPreflight.number(target),report.tools().getFirst().cutZ()); assertTrue(report.canContinue());
                assertEquals(machinist ? -.5 : .5,r.settingsByTool().get(1).safeZ());
            } else actual=((CutoutToolPanel.Result)output.get()).machining().cutZ();
            assertEquals(target,actual); assertEquals(target,Double.parseDouble(field.getText().replace(',','.')));
            if(!machinist && z>0) assertTrue(((Label)form.lookup("#"+noteId)).getText().contains(I18n.text("machining.z.converted")));
            return null;
        });
    }

    @Test void changingThePreferenceAffectsAlreadyOpenPanelsAndLegacyMagnitudeDefaultsAreMigrated() throws Exception {
        TerminalPanelTest.fx(() -> {
            var store=ToolDefaults.memoryStore(); store.put("geometry.cutdepth@mm","0.1981234567"); ToolDefaults.useStore(store);
            assertEquals("-0.1981234567",ToolDefaults.text("geometry.cutz",true));
            var output=new AtomicReference<GeometryCncToolPanel.Result>();
            var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1,0),new Coordinate(2,0)});
            var form=GeometryCncToolPanel.build("MM",path,List.of(),output::set,()->{});
            ((TextField)form.lookup("#cnc-cut-depth")).setText("0");
            ToolDefaults.set("cnc.machinist",true,"true");
            ((Button)form.lookup("#cnc-generate")).fire(); assertEquals(0,output.get().parameters().cutZ());
            output.set(null); ToolDefaults.set("cnc.machinist",true,"false");
            ((Button)form.lookup("#cnc-generate")).fire(); assertNull(output.get()); return null;
        });
    }
    @Test void thinBridgesRespectCoordinateOrderWithPositiveMachinistZ() throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("cnc.machinist",true,"true");
            var output=new AtomicReference<CutoutToolPanel.Result>();
            var form=CutoutToolPanel.build("MM",(polygon,done,cancel)->false,()->{},output::set,()->{});
            @SuppressWarnings("unchecked")
            var type=(ComboBox<CutoutToolPanel.GapType>)form.lookup("#cutout-gap-type");
            type.setValue(CutoutToolPanel.GapType.THIN);
            ((TextField)form.lookup("#cutout-cut-z")).setText("0.2");
            var thin=(TextField)form.lookup("#cutout-thin-z"); thin.setText("0.5");
            var generate=(Button)form.lookup("#cutout-generate-freeform"); generate.fire();
            assertNotNull(output.get(),((Label)form.lookup("#cutout-error")).getText());
            assertEquals(.2,output.get().machining().cutZ()); assertEquals(.5,output.get().thinMachining().cutZ());
            output.set(null); thin.setText("0.1"); generate.fire(); assertNull(output.get());
            assertEquals(I18n.text("ui.60538e9ad822"),((Label)form.lookup("#cutout-error")).getText());
            return null;
        });
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void geometryToolSnapshotsKeepIndividualSignedTargetsAndPrecision(boolean machinist) throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("cnc.machinist",true,Boolean.toString(machinist));
            var output=new AtomicReference<GeometryCncToolPanel.Result>();
            var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1,0),new Coordinate(2,0)});
            var tools=List.of(new org.flatcam.cam.geometry.ToolGeometry(.2,path),new org.flatcam.cam.geometry.ToolGeometry(.4,path));
            var form=GeometryCncToolPanel.build("MM",path,tools,output::set,()->{});
            var table=(TableView<?>)form.lookup("#cnc-tools");
            table.getSelectionModel().select(0);
            var cut=(TextField)form.lookup("#cnc-cut-depth"); cut.setText("0.1981234567");
            table.getSelectionModel().select(1); cut.setText("-0.4");
            ((Button)form.lookup("#cnc-generate")).fire();
            assertNotNull(output.get(),((Label)form.lookup("#cnc-error")).getText());
            assertEquals(machinist ? .1981234567 : -.1981234567,output.get().parametersByTool().get(0).cutZ());
            assertEquals(-.4,output.get().parametersByTool().get(1).cutZ());
            table.getSelectionModel().select(0);
            assertEquals(machinist ? .1981234567 : -.1981234567,Double.parseDouble(cut.getText()));
            return null;
        });
    }
    @Test void nativeRolandReviewExplainsPreviewOriginLimitationWithoutChangingCoordinates() throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("cnc.machinist",true,"true");
            var output=new AtomicReference<GeometryCncToolPanel.Result>();
            var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1,0),new Coordinate(2,0)});
            var form=GeometryCncToolPanel.build("MM",path,List.of(),output::set,()->{});
            @SuppressWarnings("unchecked")
            var profiles=(ComboBox<GCodePreprocessor>)form.lookup("#cnc-preprocessor");
            profiles.setValue(GCodePreprocessor.ROLAND_MDX_20);
            ((TextField)form.lookup("#cnc-cut-depth")).setText("0.2");
            ((Button)form.lookup("#cnc-generate")).fire();
            assertNotNull(output.get(),((Label)form.lookup("#cnc-error")).getText());
            assertEquals(.2,output.get().parameters().cutZ());
            var review=CncPreflight.geometry("board","MM",output.get());
            assertTrue(review.canContinue());
            assertTrue(review.issues().stream().anyMatch(issue->issue.text().equals(I18n.text("machining.z.roland.preview"))));
            return null;
        });
    }
    @Test void machinistCanBeSavedThroughTheRealPreferencesControlAndOldFilesMigrateWithoutReversingCuts() throws Exception {
        TerminalPanelTest.fx(() -> {
            var prefs=(javafx.scene.Parent)ToolDefaultsPane.build(List.of("CNC Job (geral)"),null);
            new javafx.scene.Scene(prefs,520,700); prefs.applyCss(); prefs.layout();
            var checkbox=(CheckBox)prefs.lookup("#tool-default-cnc-machinist"); assertNotNull(checkbox);
            checkbox.setSelected(true); ((Button)prefs.lookup("#tool-defaults-save")).fire();
            assertEquals(MachiningZPolicy.MACHINIST,MachiningZInput.currentPolicy());
            var legacy=new java.util.Properties(); legacy.setProperty("geometry.cutdepth@in","0.00781234567");
            assertEquals(1,ToolDefaults.importFrom(legacy)[0]);
            assertEquals("-0.00781234567",ToolDefaults.text("geometry.cutz",false));
            ToolDefaults.resetAll(); assertEquals("-0.004",ToolDefaults.text("geometry.cutz",false));
            assertEquals(MachiningZPolicy.STANDARD,MachiningZInput.currentPolicy()); return null;
        });
    }
}
