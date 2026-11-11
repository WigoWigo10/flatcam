package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.*;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class SpiralPreviewUiTest {
    static javafx.scene.Node open(MainCamFlowTest.Session s)throws Exception {
        return TerminalPanelTest.fx(()->{
            ((TreeView<String>)field(s.window,"projectTree")).getSelectionModel().select(s.item);
            call(s.window,"openPaintTool",new Class<?>[]{});var form=panel(s).getContent();
            ((ComboBox<NccMethod>)form.lookup("#paint-method")).setValue(NccMethod.SPIRAL);
            assertTrue(form.lookup("#paint-angle").isDisabled());return form;
        });
    }
    static Object highlight(MainCamFlowTest.Session s)throws Exception {
        var plot=field(s.window,"plotAreaView");var f=plot.getClass().getDeclaredField("toolPreviewContents");f.setAccessible(true);
        var layers=(List<?>)f.get(plot);return layers.isEmpty()?null:layers.getFirst();
    }
    @Test void previewDoesNotCreateAnObjectAndChangingParametersClearsIt()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var form=open(s);int count=TerminalPanelTest.fx(()->((Map<?,?>)field(s.window,"geometryByItem")).size());
            TerminalPanelTest.fx(()->{((Button)form.lookup("#paint-preview-paths")).fire();return null;});finish(s,job(s));
            TerminalPanelTest.fx(()->{
                assertEquals(count,((Map<?,?>)field(s.window,"geometryByItem")).size());assertNotNull(highlight(s));
                assertTrue(((ComboBox<?>)form.lookup("#paint-method")).getAccessibleHelp().contains(I18n.text("cam.spiral.help")));
                ((TextField)form.lookup("#paint-overlap")).setText("35");assertNull(highlight(s));return null;
            });
        }
    }
    @Test void editingWhileAQueuedPreviewRunsCancelsItsPublication()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var form=open(s);TerminalPanelTest.fx(()->{((Button)form.lookup("#paint-preview-paths")).fire();return null;});
            var handle=job(s);assertNotNull(handle);
            TerminalPanelTest.fx(()->{((TextField)form.lookup("#paint-overlap")).setText("30");return null;});
            assertTrue(handle.isCancelled());s.release.countDown();handle.completion().handle((v,e)->null).get(10,TimeUnit.SECONDS);s.awaitUi();
            TerminalPanelTest.fx(()->{assertNull(highlight(s));return null;});
        }
    }
    @Test void generatingAfterPreviewCreatesGeometryWithSourceOrder()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var form=open(s);TerminalPanelTest.fx(()->{((Button)form.lookup("#paint-preview-paths")).fire();return null;});finish(s,job(s));
            int before=TerminalPanelTest.fx(()->((Map<?,?>)field(s.window,"geometryByItem")).size());
            TerminalPanelTest.fx(()->{((Button)form.lookup("#paint-generate")).fire();return null;});finish(s,job(s));
            TerminalPanelTest.fx(()->{
                var entries=(Map<?,?>)field(s.window,"geometryByItem");assertEquals(before+1,entries.size());
                boolean source=false;for(var value:entries.values()) {
                    var getter=value.getClass().getDeclaredMethod("cncDefaults");getter.setAccessible(true);var p=(GeometryGCodeParameters)getter.invoke(value);
                    if(p!=null&&p.jobOptions().routing().mode()==CncRouting.Mode.SOURCE)source=true;
                }
                assertTrue(source);return null;
            });
        }
    }
    @Test void spiralIsAlsoAvailableForNccWithAngleControlsDisabled()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(()->{
                call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,org.flatcam.cam.gerber.GerberImage.class},s.item,s.image);
                var form=panel(s).getContent();((TableView<?>)form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                ((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("1");
                ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.SPIRAL);
                assertTrue(form.lookup("#ncc-angle").isDisabled());
                ((Button)form.lookup("#ncc-generate")).fire();return null;
            });finish(s,job(s));
            TerminalPanelTest.fx(()->{assertEquals(2,((Map<?,?>)field(s.window,"geometryByItem")).size());return null;});
        }
    }
}
