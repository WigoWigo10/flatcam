package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class TransformToolPanelTest {
    @Test void objectPivotAndBufferInputsReachHostAndInvalidValuesDoNot() throws Exception {
        try { Platform.startup(()->{}); } catch (IllegalStateException already) { }
        var task=new FutureTask<Void>(() -> {
            var selected=new TreeItem<>("target"); var reference=new TreeItem<>("reference");
            var output=new AtomicReference<TransformOp>();
            var panel=TransformToolPanel.build(items -> new org.locationtech.jts.geom.Coordinate(0,0),
                    () -> List.of(reference),item -> new double[]{10,20,14,26},
                    factory -> output.set(factory.apply(List.of(selected))),()->{});
            var mode=(ComboBox<String>)panel.lookup("#transform-reference"); mode.setValue("Object");
            // Button IDs only needed for newly implemented Buffer; find Rotate by its visible label.
            var button=findButton(panel,"Rotate"); button.fire();
            assertEquals(12,((TransformOp.Rotate)output.get()).pivot().x); assertEquals(23,((TransformOp.Rotate)output.get()).pivot().y);
            ((Button)panel.lookup("#transform-buffer-apply")).fire(); assertInstanceOf(TransformOp.Buffer.class,output.get());
            var percent=(TextField)panel.lookup("#transform-buffer-percent"); percent.setText("-100");
            output.set(null); ((Button)panel.lookup("#transform-buffer-factor-apply")).fire(); assertNull(output.get());
            percent.setText("10"); ((Button)panel.lookup("#transform-buffer-factor-apply")).fire();
            assertEquals(1.1,((TransformOp.Buffer)output.get()).value(),1e-9);
            return null;
        }); Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    private Button findButton(javafx.scene.Node node,String text) {
        if (node instanceof Button button && text.equals(button.getText())) return button;
        if (node instanceof javafx.scene.Parent parent) for (var child:parent.getChildrenUnmodifiable()) {
            var found=findButton(child,text); if(found!=null)return found;
        }
        return null;
    }
}
