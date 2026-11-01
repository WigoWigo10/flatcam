package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import org.flatcam.cam.excellon.*;
import org.flatcam.app.project.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledOnOs(OS.WINDOWS)
class ExcellonDiameterTableTest {
    @TempDir Path directory;
    static Object get(MainWindow window,String name) throws Exception {
        var field=MainWindow.class.getDeclaredField(name);field.setAccessible(true);return field.get(window);
    }
    static Object call(MainWindow window,String name,Class<?>[] types,Object...args) throws Exception {
        var method=MainWindow.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(window,args);
    }
    static ExcellonImage image(String units) {
        return new ExcellonParser().parse(List.of("M48",units.equals("MM") ? "METRIC" : "INCH","T1C2.2","T2C0.68","%",
                "T1","X1.0Y1.0","X2.0Y3.0G85X4.0Y3.0","T2","X8.0Y1.0","M30"));
    }
    @SuppressWarnings("unchecked")
    static TreeItem<String> add(MainWindow window,ExcellonImage image) throws Exception {
        return (TreeItem<String>)call(window,"addExcellonToProject",new Class<?>[]{String.class,Path.class,ExcellonImage.class},"holes",null,image);
    }
    static ExcellonImage current(MainWindow window,TreeItem<String> item) throws Exception {
        return (ExcellonImage)((Map<?,?>)get(window,"excellonByItem")).get(item);
    }
    static void key(Node node,KeyCode key,boolean control) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",key,false,control,false,false));
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void realPropertiesTableEditsResizesPlotUndoesAndPersistsWithoutMovingHits(String units) throws Exception {
        try(var session=new MainCamFlowTest.Session(units)) {
            var original=image(units);
            var item=TerminalPanelTest.fx(()->add(session.window,original));
            var form=TerminalPanelTest.fx(()->{
                var node=(Parent)call(session.window,"buildExcellonPropertiesPanel",new Class<?>[]{TreeItem.class,ExcellonImage.class},item,original);
                new Scene(node,440,900);node.applyCss();node.layout();return node;
            });
            TerminalPanelTest.fx(()->{
                var table=(TableView<?>)form.lookup("#excellon-object-tools");table.getSelectionModel().selectFirst();
                key(table,KeyCode.F2,false);form.applyCss();form.layout();
                var input=(TextField)table.lookup("#tool-diameter-editor");assertNotNull(input);
                assertEquals("2.2",input.getText());input.setText("0,1981234567");input.fireEvent(new javafx.event.ActionEvent());
                assertNotNull(get(session.window,"runningJob"));assertFalse(table.isEditable());assertSame(original,current(session.window,item));
                return null;
            });
            session.release.countDown();await(session);
            var updated=TerminalPanelTest.fx(()->current(session.window,item));
            assertEquals(.1981234567,updated.toolDiameters().get(1));assertEquals(original.drills(),updated.drills());assertEquals(original.slots(),updated.slots());
            TerminalPanelTest.fx(()->{
                var plot=(PlotAreaView)get(session.window,"plotAreaView");
                var field=PlotAreaView.class.getDeclaredField("layers");field.setAccessible(true);
                var layer=(PlotAreaView.RenderLayer)((Map<?,?>)field.get(plot)).get(item);
                assertSame(updated.solidGeometry(),layer.geometry());
                key(form.lookup("#excellon-object-tools"),KeyCode.Z,true);return null;
            });await(session);
            assertEquals(2.2,TerminalPanelTest.fx(()->current(session.window,item)).toolDiameters().get(1));
            TerminalPanelTest.fx(()->{key(form.lookup("#excellon-object-tools"),KeyCode.Y,true);return null;});await(session);
            assertEquals(.1981234567,TerminalPanelTest.fx(()->current(session.window,item)).toolDiameters().get(1));
            for(String ext:List.of("fcnproj","FlatPrj")) {
                Path file=directory.resolve("holes-"+units+"."+ext);session.window.saveProject(file);
                var restored=ext.equals("FlatPrj") ? PythonProjectIO.load(file) : ProjectFileIO.load(file);
                assertEquals(.1981234567,restored.excellons().getFirst().image().toolDiameters().get(1));
                assertEquals(original.drills(),restored.excellons().getFirst().image().drills());
                assertEquals(original.slots(),restored.excellons().getFirst().image().slots());
            }
        }
    }
    static void await(MainCamFlowTest.Session session) throws Exception {
        for(int i=0;i<1000;i++) {
            if(TerminalPanelTest.fx(()->get(session.window,"runningJob")==null))return;
            Thread.sleep(5);
        }fail("Diameter update timed out");
    }
    @Test void invalidInputStaysEditableEscapeRestoresAndTotalsNeverEdit() throws Exception {
        TerminalPanelTest.fx(()->{
            var changes=new ArrayList<Double>();
            var view=ExcellonObjectToolsTable.editable(image("MM"),new ExcellonObjectToolsTable.Host() {
                public boolean change(int id,double value){changes.add(value);return true;}
                public boolean history(boolean redo){return false;}public boolean canUndo(){return false;}public boolean canRedo(){return false;}
            });
            var root=(Parent)view.node();new Scene(root,400,450);root.applyCss();root.layout();
            var table=(TableView<?>)root.lookup("#excellon-object-tools");table.getSelectionModel().selectFirst();key(table,KeyCode.F2,false);root.layout();
            var input=(TextField)table.lookup("#tool-diameter-editor");assertNotNull(input);
            for(String bad:List.of("0","-1","NaN","Infinity","")) {
                input.setText(bad);input.fireEvent(new javafx.event.ActionEvent());assertTrue(changes.isEmpty());assertFalse(view.finish());
            }
            key(input,KeyCode.ESCAPE,false);assertTrue(view.finish());assertTrue(changes.isEmpty());
            table.getSelectionModel().clearAndSelect(table.getItems().size()-1);key(table,KeyCode.F2,false);
            assertNull(table.lookup("#tool-diameter-editor"));
            table.getSelectionModel().clearAndSelect(0);key(table,KeyCode.F2,false);root.layout();
            var sameDiameter=(TextField)table.lookup("#tool-diameter-editor");assertNotNull(sameDiameter);
            sameDiameter.setText("0,68");sameDiameter.fireEvent(new javafx.event.ActionEvent());
            assertEquals(List.of(.68),changes,"Equal diameters must not merge tools or be rejected");
            assertEquals(4,table.getItems().size());return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"cancel","replace","project"})
    void cancelledOrStaleWorkersCannotOverwriteTheObject(String change) throws Exception {
        try(var session=new MainCamFlowTest.Session("MM")) {
            var original=image("MM");var item=TerminalPanelTest.fx(()->add(session.window,original));
            TerminalPanelTest.fx(()->{
                assertTrue(session.window.editExcellonDiameter(item,original,1,.2));
                if(change.equals("cancel"))((org.flatcam.app.job.JobHandle<?>)get(session.window,"runningJob")).cancel();
                else if(change.equals("replace"))((Map)get(session.window,"excellonByItem")).put(item,image("MM"));
                else {var field=MainWindow.class.getDeclaredField("tclProjectEpoch");field.setAccessible(true);field.setLong(session.window,42);}
                return null;
            });session.release.countDown();await(session);
            assertEquals(2.2,TerminalPanelTest.fx(()->current(session.window,item)).toolDiameters().get(1));
            assertFalse(TerminalPanelTest.fx(()->session.window.travelExcellonDiameter(item,false)));
        }
    }
}
