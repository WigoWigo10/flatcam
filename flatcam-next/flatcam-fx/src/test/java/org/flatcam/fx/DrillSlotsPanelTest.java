package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.*;
import javafx.scene.control.*;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class DrillSlotsPanelTest {
    @Test void savedSettingsDraftsMultipleSelectionAndPreflightReflectActualOperation() throws Exception {
        TerminalPanelTest.fx(() -> {
            var image = ExcellonImage.of("MM",Map.of(1,1.0,2,2.0),List.of(),
                    List.of(new ExcellonImage.Slot(1,10,0,12,0),new ExcellonImage.Slot(2,20,0,24,0)),new GeometryFactory().createPolygon());
            var saved = new DrillGCodeParameters(3,1,100,10000,false).withSlots(new DrillSlotParameters(true,25,false));
            var source = new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("slots"),image,Map.of(1,saved));
            var result = new AtomicReference<DrillGCodeToolPanel.Result>();
            Parent root = (Parent)DrillGCodeToolPanel.build(List.of(source),source,List::of,result::set,()->{});
            new Scene(root,450,1700); root.applyCss(); root.layout();
            TableView<?> table = (TableView<?>)root.lookup("#drill-tools");
            CheckBox mode = (CheckBox)root.lookup("#drill-slots"), last = (CheckBox)root.lookup("#drill-slot-last-hole");
            TextField overlap = (TextField)root.lookup("#drill-slot-overlap");
            table.getSelectionModel().clearAndSelect(0);
            assertTrue(mode.isSelected()); assertFalse(last.isSelected()); assertEquals("25.0",overlap.getText());
            overlap.setText("bad");
            table.getSelectionModel().clearAndSelect(1);
            assertFalse(mode.isSelected()); assertTrue(overlap.isDisabled());
            table.getSelectionModel().clearAndSelect(0); assertEquals("bad",overlap.getText());
            ((Button)root.lookup("#drill-generate")).fire(); assertNull(result.get());
            overlap.setText("25,0");
            ((Button)root.lookup("#drill-generate")).fire(); assertNotNull(result.get());
            assertEquals(new DrillSlotParameters(true,25,false),result.get().settingsByTool().get(1).slots());
            var report = CncPreflight.drilling(result.get());
            assertTrue(report.issues().stream().anyMatch(issue -> issue.text().contains("Drill slots")));
            assertFalse(report.issues().stream().anyMatch(issue -> issue.text().contains("rasgos são fresados")));
            table.getSelectionModel().selectAll();
            ((CheckBox)root.lookup("#drill-tool-change")).setSelected(true);
            mode.setSelected(false); mode.setSelected(true); overlap.setText("50"); last.setSelected(true);
            result.set(null);
            ((Button)root.lookup("#drill-generate")).fire();
            assertNotNull(result.get(), ((Label)root.lookup("#drill-error")).getText());
            assertEquals(2,result.get().settingsByTool().size());
            assertTrue(result.get().settingsByTool().values().stream().allMatch(p -> p.slots().equals(new DrillSlotParameters(true,50,true))));
            overlap.setText("100"); result.set(null);
            ((Button)root.lookup("#drill-generate")).fire(); assertNull(result.get());
            assertTrue(((Label)root.lookup("#drill-error")).getText().contains("Sobreposição"));
            return null;
        });
    }
}
