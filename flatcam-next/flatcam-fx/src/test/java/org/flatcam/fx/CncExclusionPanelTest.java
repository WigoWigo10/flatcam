package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class CncExclusionPanelTest {
    @Test void savedAreasCanBeEditedDisabledAndIncompatibleProfilesCannotSilentlyIgnoreThem() throws Exception {
        try { Platform.startup(()->{}); } catch(IllegalStateException running) { }
        var task=new FutureTask<Void>(() -> {
            var f=new GeometryFactory(); var path=f.createLineString(new Coordinate[]{new Coordinate(10,0),new Coordinate(12,0)});
            var area=CncExclusionArea.of(f.toGeometry(new Envelope(4,6,-1,1)),CncExclusionArea.Strategy.OVER,20);
            var p=new GeometryGCodeParameters(3,.1,false,1,100,100,false).withJobOptions(GeometryJobOptions.AUTOMATIC.withExclusions(true,List.of(area)));
            var result=new AtomicReference<GeometryCncToolPanel.Result>();
            var root=(Parent)GeometryCncToolPanel.build("MM",path,List.of(new ToolGeometry(1,path)),p,result::set,()->{});
            new Scene(root); root.applyCss(); root.layout();
            var table=(TableView<CncExclusionArea>)root.lookup("#cnc-exclusions");
            assertEquals(List.of(area),table.getItems()); table.getSelectionModel().select(0);
            ((TextField)root.lookup("#cnc-exclusion-over-z")).setText("30");
            ((Button)root.lookup("#cnc-exclusion-apply")).fire();
            ((Button)root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get()); assertEquals(30,result.get().parameters().jobOptions().exclusions().getFirst().overZ());
            ((ComboBox<GCodePreprocessor>)root.lookup("#cnc-preprocessor")).setValue(GCodePreprocessor.HPGL);
            result.set(null); ((Button)root.lookup("#cnc-generate")).fire(); assertNull(result.get());
            ((CheckBox)root.lookup("#cnc-exclusions-enabled")).setSelected(false);
            ((Button)root.lookup("#cnc-generate")).fire(); assertNotNull(result.get());
            assertFalse(result.get().parameters().jobOptions().exclusionsEnabled());
            assertEquals(1,result.get().parameters().jobOptions().exclusions().size());
            return null;
        }); Platform.runLater(task); task.get(25,TimeUnit.SECONDS);
    }
}
