package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class GeometryAdvancedPanelTest {
    private void onFx(Runnable action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException running) { }
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task); task.get(25,TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked")
    @Test void offsetsAreIndividualButPositionsAreCommonAndSurviveRowSwitches() throws Exception {
        onFx(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(10,0),
                    new Coordinate(10,10),new Coordinate(0,10),new Coordinate(0,0)});
            var positions = new GeometryJobOptions(5.0,0.5,20.0,30.0,15.0,0.0,0.0);
            var p = new GeometryGCodeParameters(2,0.1,false,1,100,100,true).withJobOptions(positions);
            var q = p.withCompensation(ToolPathOffset.CUSTOM,-0.1);
            var settings = new GeometryCncSettings(GCodePreprocessor.DEFAULT_NO_M6,null,Map.of(),Map.of(0,p,1,q));
            AtomicReference<GeometryCncToolPanel.Result> result = new AtomicReference<>();
            Parent root = (Parent) GeometryCncToolPanel.build("MM",path,List.of(new ToolGeometry(0.4,path),new ToolGeometry(0.8,path)),
                    p,settings,result::set,() -> {});
            new Scene(root); root.applyCss(); root.layout();
            var table = (TableView<?>) root.lookup("#cnc-tools");
            var offset = (ComboBox<ToolPathOffset>) root.lookup("#cnc-offset");
            var custom = (TextField) root.lookup("#cnc-custom-offset");
            var end = (TextField) root.lookup("#cnc-end-xy");
            assertEquals("20.0, 30.0",end.getText());
            assertTrue(custom.isDisabled()); table.getSelectionModel().select(1);
            assertEquals(ToolPathOffset.CUSTOM,offset.getValue()); assertEquals("-0.1",custom.getText());
            assertFalse(custom.isDisabled()); custom.setText("invalid"); table.getSelectionModel().select(0);
            assertEquals(ToolPathOffset.PATH,offset.getValue());
            ((Button) root.lookup("#cnc-generate")).fire(); assertNull(result.get());
            assertTrue(((Label) root.lookup("#cnc-error")).getText().contains("Custom Offset"));
            table.getSelectionModel().select(1); assertEquals("invalid",custom.getText());
            custom.setText("-0,25"); end.setText("21,5;30,5");
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(),((Label) root.lookup("#cnc-error")).getText());
            assertEquals(-0.25,result.get().parametersByTool().get(1).customOffset());
            assertEquals(21.5,result.get().parametersByTool().get(0).jobOptions().endX());
            assertEquals(result.get().parameters().jobOptions(),result.get().parametersByTool().get(0).jobOptions());
            table.getSelectionModel().select(0);
            ((Button) root.lookup("#cnc-apply-all")).fire();
            ((Button) root.lookup("#cnc-generate")).fire(); assertTrue(result.get().parametersByTool().isEmpty());
        });
    }
    @SuppressWarnings("unchecked")
    @Test void invalidPositionsBlockGenerationAndUnsupportedProfilesDoNotDiscardSettings() throws Exception {
        onFx(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,1)});
            var p = new GeometryGCodeParameters(2,0.1,false,1,100,100,true);
            AtomicReference<GeometryCncToolPanel.Result> result = new AtomicReference<>();
            Parent root = (Parent) GeometryCncToolPanel.build("MM",path,List.of(),p,result::set,() -> {});
            new Scene(root); root.applyCss(); root.layout();
            TextField end = (TextField) root.lookup("#cnc-end-xy"), change = (TextField) root.lookup("#cnc-change-z");
            Button generate = (Button) root.lookup("#cnc-generate"); Label error = (Label) root.lookup("#cnc-error");
            change.setText("1"); generate.fire(); assertNull(result.get()); assertTrue(error.getText().contains("Travel Z"));
            change.setText("None"); end.setText("1"); generate.fire(); assertNull(result.get()); assertTrue(error.getText().contains("End X,Y"));
            end.setText("NaN,2"); generate.fire(); assertNull(result.get()); assertTrue(error.getText().contains("finitas"));
            end.setText("10,20");
            var profiles = (ComboBox<GCodePreprocessor>) root.lookup("#cnc-preprocessor");
            profiles.setValue(GCodePreprocessor.GRBL_LASER);
            assertTrue(end.isDisabled()); generate.fire(); assertNull(result.get()); assertTrue(error.getText().contains("Posicoes"));
            profiles.setValue(GCodePreprocessor.DEFAULT_NO_M6);
            assertFalse(end.isDisabled()); assertEquals("10,20",end.getText()); generate.fire();
            assertNotNull(result.get(),error.getText()); assertEquals(10,result.get().parameters().jobOptions().endX());
        });
    }
}
