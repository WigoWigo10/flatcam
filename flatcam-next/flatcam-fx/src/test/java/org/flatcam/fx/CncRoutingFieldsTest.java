package org.flatcam.fx;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javafx.scene.control.*;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class CncRoutingFieldsTest {
    @Test void laserAllowsOnlyEndZAndXyAndPublishesTheSharedParkingSettings() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=new GeometryFactory();
            var line=f.createLineString(new Coordinate[]{new Coordinate(10,7),new Coordinate(12,7)});
            for(var profile:List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.Z_LASER,
                    GCodePreprocessor.MARLIN_LASER_FAN_PIN,GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) {
                var p=new GeometryGCodeParameters(3,1,false,1,200,128,false,900);
                var result=new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
                var root=(javafx.scene.Parent)GeometryCncToolPanel.build("MM",line,List.of(),p,result::set,()->{});
                new javafx.scene.Scene(root);root.applyCss();root.layout();
                @SuppressWarnings("unchecked") var profiles=(ComboBox<GCodePreprocessor>)root.lookup("#cnc-preprocessor");
                profiles.setValue(profile);
                assertTrue(root.lookup("#cnc-start-z").isDisabled());
                assertTrue(root.lookup("#cnc-change-z").isDisabled());
                assertFalse(root.lookup("#cnc-end-z").isDisabled());
                assertFalse(root.lookup("#cnc-end-xy").isDisabled());
                ((TextField)root.lookup("#cnc-end-z")).setText("0.5");
                ((TextField)root.lookup("#cnc-end-xy")).setText("25;9");
                ((Button)root.lookup("#cnc-generate")).fire();
                assertNotNull(result.get(),((Label)root.lookup("#cnc-error")).getText());
                assertEquals(.5,result.get().parameters().jobOptions().endZ());
                assertEquals(25,result.get().parameters().jobOptions().endX());
                assertEquals(9,result.get().parameters().jobOptions().endY());
                assertNull(result.get().parameters().jobOptions().startZ());
            }
            return null;
        });
    }
    @Test void closedContinuousChoicePublishesAndAllowsPreservingDirection() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=new GeometryFactory();
            var ring=f.createLinearRing(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0),new Coordinate(12,2),new Coordinate(10,2),new Coordinate(10,0)});
            var defaults=new GeometryGCodeParameters(3,.6,true,.2,200,10000,true).withPassStrategy(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION);
            var result=new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var root=(javafx.scene.Parent)GeometryCncToolPanel.build("MM",ring,List.of(),defaults,result::set,()->{});
            new javafx.scene.Scene(root);root.applyCss();root.layout();
            @SuppressWarnings("unchecked") var modes=(ComboBox<GeometryPassStrategy>)root.lookup("#cnc-pass-strategy");
            assertEquals(3,modes.getItems().size());assertEquals(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION,modes.getValue());
            assertTrue(modes.getConverter().toString(modes.getValue()).contains("fechados"));
            ((CheckBox)root.lookup("#cnc-route-direction")).setSelected(true);
            ((Button)root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(),((Label)root.lookup("#cnc-error")).getText());
            assertEquals(GeometryPassStrategy.CONTINUOUS_SAME_DIRECTION,result.get().parameters().passStrategy());
            assertTrue(result.get().parameters().jobOptions().routing().preserveDirection());
            return null;
        });
    }
    @Test void travelPolicyDefaultsRestoresPublishesAndCannotBeIgnoredInUnsupportedProfiles() throws Exception {
        TerminalPanelTest.fx(() -> {
            var path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
            var result=new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var defaults=new GeometryGCodeParameters(3,.6,true,.2,200,10000,true).withJobOptions(
                    GeometryJobOptions.AUTOMATIC.withTravelClearance(GeometryTravelClearance.PER_TOOL));
            var root=(javafx.scene.Parent)GeometryCncToolPanel.build("MM",path,List.of(),defaults,result::set,()->{});
            new javafx.scene.Scene(root); root.applyCss(); root.layout();
            @SuppressWarnings("unchecked") var policy=(ComboBox<GeometryTravelClearance>)root.lookup("#cnc-travel-clearance");
            assertEquals(GeometryTravelClearance.PER_TOOL,policy.getValue()); assertFalse(policy.getAccessibleHelp().isBlank());
            ((Button)root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(),((Label)root.lookup("#cnc-error")).getText());
            assertEquals(GeometryTravelClearance.PER_TOOL,result.get().parameters().jobOptions().travelClearance());
            @SuppressWarnings("unchecked") var pp=(ComboBox<GCodePreprocessor>)root.lookup("#cnc-preprocessor");
            result.set(null); pp.setValue(GCodePreprocessor.HPGL); ((Button)root.lookup("#cnc-generate")).fire();
            assertTrue(policy.isDisabled()); assertNull(result.get()); assertFalse(((Label)root.lookup("#cnc-error")).getText().isBlank());
            pp.setValue(GCodePreprocessor.FX_PORTABLE); policy.setValue(GeometryTravelClearance.JOB_MAXIMUM);
            ((Button)root.lookup("#cnc-generate")).fire(); assertNotNull(result.get());
            assertEquals(GeometryTravelClearance.JOB_MAXIMUM,result.get().parameters().jobOptions().travelClearance());
            return null;
        });
    }
    @Test void continuousPassChoiceIsPublishedAndDirectionConflictIsReported() throws Exception {
        TerminalPanelTest.fx(() -> {
            Geometry path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
            var result=new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var root=(javafx.scene.Parent)GeometryCncToolPanel.build("MM",path,List.of(),result::set,() -> {});
            new javafx.scene.Scene(root); root.applyCss(); root.layout();
            ((CheckBox)root.lookup("#cnc-multi-depth")).setSelected(true);
            @SuppressWarnings("unchecked") var mode=(ComboBox<GeometryPassStrategy>)root.lookup("#cnc-pass-strategy");
            mode.setValue(GeometryPassStrategy.CONTINUOUS_ALTERNATING); ((Button)root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(),((Label)root.lookup("#cnc-error")).getText()); assertEquals(GeometryPassStrategy.CONTINUOUS_ALTERNATING,result.get().parameters().passStrategy());
            @SuppressWarnings("unchecked") var profile=(ComboBox<GCodePreprocessor>)root.lookup("#cnc-preprocessor");
            profile.setValue(GCodePreprocessor.HPGL); ((Button)root.lookup("#cnc-generate")).fire();
            assertEquals(GeometryPassStrategy.RETRACT_SAME_DIRECTION,result.get().parameters().passStrategy());
            profile.setValue(GCodePreprocessor.FX_PORTABLE); assertEquals(GeometryPassStrategy.CONTINUOUS_ALTERNATING,mode.getValue());
            result.set(null); ((CheckBox)root.lookup("#cnc-route-direction")).setSelected(true); ((Button)root.lookup("#cnc-generate")).fire();
            assertNull(result.get()); assertTrue(((Label)root.lookup("#cnc-error")).getText().contains("Preservar direção")); return null;
        });
    }
    @Test void boardReferenceUnitsAndSavedSnapshotAreExplicit() throws Exception {
        TerminalPanelTest.fx(() -> {
            Geometry area=new GeometryFactory().toGeometry(new Envelope(0,10,0,10));
            var fields=new CncRoutingFields("MM",CncRouting.LEGACY,List.of(new CncRoutingFields.Reference("board","MM",area),new CncRoutingFields.Reference("inch","IN",area)));
            new javafx.scene.Scene(fields.view()); fields.view().applyCss(); fields.view().layout();
            @SuppressWarnings("unchecked") var mode=(ComboBox<CncRouting.Mode>)fields.view().lookup("#cnc-route-mode");
            @SuppressWarnings("unchecked") var ref=(ComboBox<CncRoutingFields.Reference>)fields.view().lookup("#cnc-route-reference");
            assertEquals(1,ref.getItems().size()); assertTrue(ref.isDisabled());
            mode.setValue(CncRouting.Mode.BOARDS); assertFalse(ref.isDisabled()); assertThrows(IllegalArgumentException.class,fields::options);
            ref.setValue(ref.getItems().getFirst());
            ((CheckBox)fields.view().lookup("#cnc-route-direction")).setSelected(true);
            var options=fields.options(); assertEquals(List.of(area.toText()),options.boardAreas()); assertTrue(options.preserveDirection());
            var restored=new CncRoutingFields("MM",options,List.of()); assertEquals(options,restored.options()); return null;
        });
    }
    @Test void realGeometryPanelPublishesChosenPolicy() throws Exception {
        TerminalPanelTest.fx(() -> {
            Geometry path=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(0,0)});
            var output=new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var panel=GeometryCncToolPanel.build("MM",path,List.of(),output::set,() -> {});
            var parent=(javafx.scene.Parent)panel; new javafx.scene.Scene(parent); parent.applyCss(); parent.layout();
            ((CheckBox)panel.lookup("#cnc-route-direction")).setSelected(true);
            ((CheckBox)panel.lookup("#cnc-route-inner-first")).setSelected(true);
            ((Button)panel.lookup("#cnc-generate")).fire(); assertNotNull(output.get(),((Label)panel.lookup("#cnc-error")).getText());
            assertTrue(output.get().parameters().jobOptions().routing().preserveDirection());
            assertTrue(output.get().parameters().jobOptions().routing().innerFirst()); return null;
        });
    }
}
