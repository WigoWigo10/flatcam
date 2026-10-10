package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class NccSeedPolicyPanelTest {
    @Test @SuppressWarnings("unchecked")
    void choiceBelongsToEachToolAndDisablesWhenSeedIsNotUsed() throws Exception {
        TerminalPanelTest.fx(() -> {
            Geometry copper = new GeometryFactory().toGeometry(new Envelope(0,10,0,10));
            var source = new NccToolPanel.SourceCandidate(new TreeItem<>("board"),"board","MM",true,copper);
            var result = new AtomicReference<NccToolPanel.Result>();
            Parent root = (Parent)NccToolPanel.build(List.of(source),source,List.of(),(chosen,shape,done,cancel) -> false,
                    () -> {},List::of,result::set,() -> {});
            new Scene(root,410,1200);
            root.applyCss(); root.layout();
            var table = (TableView<?>)root.lookup(".table-view");
            var policy = (ComboBox<NccSeedPolicy>)root.lookup("#ncc-seed-policy");
            var method = (ComboBox<NccMethod>)root.lookup("#ncc-method");
            table.getSelectionModel().clearAndSelect(0);
            assertFalse(policy.isDisabled());
            assertEquals(NccSeedPolicy.STABLE,policy.getValue());
            assertTrue(policy.getTooltip().getText().contains("Pequenas diferencas"));
            policy.setValue(NccSeedPolicy.PYTHON);
            ((Button)root.lookup("#ncc-generate")).fire();
            assertNotNull(result.get());
            assertEquals(NccSeedPolicy.PYTHON,result.get().parameters().settingsFor(1).seedPolicy());
            table.getSelectionModel().clearAndSelect(1);
            assertEquals(NccSeedPolicy.STABLE,policy.getValue());
            table.getSelectionModel().clearAndSelect(0);
            assertEquals(NccSeedPolicy.PYTHON,policy.getValue());
            method.setValue(NccMethod.STANDARD);
            assertTrue(policy.isDisabled());
            method.setValue(NccMethod.COMBO);
            assertFalse(policy.isDisabled());
            table.getSelectionModel().selectAll();
            assertTrue(policy.isDisabled());
            return null;
        });
    }
}
