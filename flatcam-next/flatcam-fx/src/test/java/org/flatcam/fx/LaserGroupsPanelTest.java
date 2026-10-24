package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.*;
import javafx.scene.control.*;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class LaserGroupsPanelTest {
    @Test void joiningLaserObjectsPreservesEachSourceGroupConfiguration() {
        var f=new GeometryFactory();
        var line=f.createLineString(new Coordinate[]{new Coordinate(10,7),new Coordinate(12,7)});
        var a=new GeometryGCodeParameters(6,1,false,1,200,64,false,900);
        var b=new GeometryGCodeParameters(2,1,false,1,350,200,false,900);
        var entries=new ArrayList<org.flatcam.app.project.ProjectFile.GeometryEntry>();
        for(var p:List.of(a,b)) entries.add(new org.flatcam.app.project.ProjectFile.GeometryEntry("Laser","","MM",line,true,
                List.of(new ToolGeometry(.4,line)),null,null,true,p,new GeometryCncSettings(GCodePreprocessor.Z_LASER,null,Map.of())));
        var joined=TclObjectJoin.geometry(entries);
        assertEquals(2,joined.joined().tools().size());
        assertEquals(a,joined.settings().parametersByTool().get(0));
        assertEquals(b,joined.settings().parametersByTool().getOrDefault(1,joined.defaults()));
    }
    @Test void restoresEachGroupRejectsInvalidHiddenEditsAndCanApplyToAll() throws Exception {
        TerminalPanelTest.fx(()->{
            var f=new GeometryFactory();
            var line=f.createLineString(new Coordinate[]{new Coordinate(10,7),new Coordinate(12,7)});
            for(var profile:List.of(GCodePreprocessor.GRBL_LASER,GCodePreprocessor.Z_LASER,
                    GCodePreprocessor.MARLIN_LASER_FAN_PIN,GCodePreprocessor.MARLIN_LASER_SPINDLE_PIN)) {
                var a=new GeometryGCodeParameters(6,1,false,1,200,64,false,900);
                var b=new GeometryGCodeParameters(profile==GCodePreprocessor.GRBL_LASER ? 6 : 2,1,false,1,350,200,false,900);
                var settings=new GeometryCncSettings(profile,null,Map.of(),Map.of(0,a,1,b));
                var result=new AtomicReference<GeometryCncToolPanel.Result>();
                var root=(Parent)GeometryCncToolPanel.build("MM",line,List.of(new ToolGeometry(.4,line),new ToolGeometry(.8,line)),a,settings,result::set,()->{});
                new Scene(root); root.applyCss(); root.layout();
                var table=(TableView<?>)root.lookup("#cnc-tools");
                var power=(TextField)root.lookup("#cnc-power"); var feed=(TextField)root.lookup("#cnc-feed");
                var focus=(TextField)root.lookup("#cnc-safe-z"); var generate=(Button)root.lookup("#cnc-generate");
                table.getSelectionModel().select(1);
                assertEquals("200",power.getText()); assertEquals("350.0",feed.getText());
                assertEquals(Double.toString(b.safeZ()),focus.getText());
                feed.setText("invalid"); table.getSelectionModel().select(0); generate.fire();
                assertNull(result.get()); assertFalse(((Label)root.lookup("#cnc-error")).getText().isBlank());
                table.getSelectionModel().select(1); assertEquals("invalid",feed.getText()); feed.setText("350");
                if(profile==GCodePreprocessor.MARLIN_LASER_FAN_PIN) {
                    power.setText("256"); table.getSelectionModel().select(0); generate.fire(); assertNull(result.get());
                    table.getSelectionModel().select(1); power.setText("200");
                }
                table.getSelectionModel().select(0); generate.fire();
                assertNotNull(result.get(),((Label)root.lookup("#cnc-error")).getText());
                assertEquals(200,result.get().parametersByTool().get(1).spindleSpeedRpm());
                assertEquals(350,result.get().parametersByTool().get(1).feedRate());
                assertEquals(64,result.get().parametersByTool().get(0).spindleSpeedRpm());
                assertTrue(CncPreflight.geometry("Laser","MM",result.get()).canContinue());
                ((Button)root.lookup("#cnc-apply-all")).fire(); generate.fire();
                assertTrue(result.get().parametersByTool().isEmpty());
            }
            return null;
        });
    }
}
