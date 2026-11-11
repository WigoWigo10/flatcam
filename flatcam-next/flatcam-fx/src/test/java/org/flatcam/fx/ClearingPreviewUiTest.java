package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.util.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.cam.ncc.NccMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class ClearingPreviewUiTest {
    static Node ncc(MainCamFlowTest.Session s)throws Exception {
        return TerminalPanelTest.fx(()->{
            call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,org.flatcam.cam.gerber.GerberImage.class},s.item,s.image);
            var form=panel(s).getContent();((TableView<?>)form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
            ((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("1");
            ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.SPIRAL);return form;
        });
    }
    static List<?> layers(MainCamFlowTest.Session s)throws Exception {var p=field(s.window,"plotAreaView");var f=p.getClass().getDeclaredField("toolPreviewContents");f.setAccessible(true);return (List<?>)f.get(p);}
    @Test void nccPreviewCreatesNoObjectAndLegendChangesOnlyTheDrawing()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var form=ncc(s);int before=TerminalPanelTest.fx(()->((Map<?,?>)field(s.window,"geometryByItem")).size());
            TerminalPanelTest.fx(()->{((Button)form.lookup("#ncc-preview-paths")).fire();return null;});finish(s,job(s));
            TerminalPanelTest.fx(()->{
                assertEquals(before,((Map<?,?>)field(s.window,"geometryByItem")).size());assertEquals(3,layers(s).size());
                assertTrue(form.lookup("#clearing-preview-summary").isVisible());
                var summary=(Label)form.lookup("#clearing-preview-metrics");assertTrue(summary.getText().contains("XY"));
                assertTrue(summary.getAccessibleHelp().contains(I18n.text("cam.preview.summary.help")));
                ((CheckBox)form.lookup("#clearing-preview-rapid")).fire();assertEquals(2,layers(s).size());
                assertNull(job(s));assertTrue(form.lookup("#clearing-preview-summary").isVisible(),"Legend is not a recipe edit");
                ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.LINES);
                assertTrue(layers(s).isEmpty());assertFalse(form.lookup("#clearing-preview-summary").isVisible());return null;
            });
        }
    }
    @Test void nccEditsCancelQueuedPreviewAndCannotPublishStaleStatistics()throws Exception {
        try(var s=new MainCamFlowTest.Session("IN")) {
            var form=ncc(s);TerminalPanelTest.fx(()->{((Button)form.lookup("#ncc-preview-paths")).fire();return null;});var handle=job(s);assertNotNull(handle);
            TerminalPanelTest.fx(()->{((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("2");return null;});assertTrue(handle.isCancelled());
            s.release.countDown();handle.completion().handle((v,e)->null).get(10,java.util.concurrent.TimeUnit.SECONDS);s.awaitUi();
            TerminalPanelTest.fx(()->{assertTrue(layers(s).isEmpty());assertFalse(form.lookup("#clearing-preview-summary").isVisible());return null;});
        }
    }
    @Test void changingToolsCancelsNccPreviewWithoutChangingTheNewToolsOverlay()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var form=ncc(s);TerminalPanelTest.fx(()->{((Button)form.lookup("#ncc-preview-paths")).fire();return null;});var handle=job(s);
            TerminalPanelTest.fx(()->{call(s.window,"openPaintTool",new Class<?>[]{});return null;});assertTrue(handle.isCancelled());
            s.release.countDown();handle.completion().handle((v,e)->null).get(10,java.util.concurrent.TimeUnit.SECONDS);s.awaitUi();
            TerminalPanelTest.fx(()->{assertTrue(layers(s).isEmpty());assertNotNull(panel(s).getContent().lookup("#paint-preview-paths"));return null;});
            var resumed=ncc(s);assertSame(form,resumed,"Switching tools must preserve the NCC draft");
            TerminalPanelTest.fx(()->{((Button)resumed.lookup("#ncc-preview-paths")).fire();return null;});finish(s,job(s));
            TerminalPanelTest.fx(()->{assertEquals(3,layers(s).size());return null;});
        }
    }
}
