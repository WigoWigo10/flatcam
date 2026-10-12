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
