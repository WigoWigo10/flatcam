package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.Path;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class EditorNavigationTest {
    static Object field(Object target,String name)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static Object call(MainWindow window,String name,Class<?>[] types,Object... args)throws Exception {
        var m=MainWindow.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(window,args);
    }
    static java.util.stream.Stream<Arguments> editors() {
        return java.util.stream.IntStream.range(0,Integer.getInteger("flatcam.test.editorNavigationRepeats",1))
                .boxed().flatMap(i->java.util.stream.Stream.of("Geometry","Gerber","Excellon","GCode").map(kind->Arguments.of(kind,i)));
    }
    static java.util.stream.Stream<Arguments> editorPairs() {
        var kinds=List.of("Geometry","Gerber","Excellon","GCode");
        return kinds.stream().flatMap(from->kinds.stream().filter(to->!to.equals(from)).map(to->Arguments.of(from,to)));
    }
    @ParameterizedTest @MethodSource("editorPairs")
    void contextMenusKeepTheOriginalEditorAndItsExitCommands(String from,String to)throws Exception {
        ToolDefaults.useStore(ToolDefaults.memoryStore());var jobs=new JobExecutor(1);
        var window=TerminalPanelTest.fx(()->{var w=new MainWindow(jobs);w.createScene();return w;});
        try {
            TerminalPanelTest.fx(()->{
                var f=new GeometryFactory();var shape=f.toGeometry(new Envelope(0,10,0,8));var items=new HashMap<String,TreeItem<String>>();
                items.put("Geometry",(TreeItem<String>)call(window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},"shape","","MM",shape,false));
                items.put("Gerber",(TreeItem<String>)call(window,"addGerberToProject",new Class<?>[]{String.class,Path.class,GerberImage.class},"copper",null,GerberImage.of("MM",Map.of(),shape,shape.getBoundary(),Map.of())));
                var drill=new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y1.0","M30"));
                items.put("Excellon",(TreeItem<String>)call(window,"addExcellonToProject",new Class<?>[]{String.class,Path.class,drill.getClass()},"holes",null,drill));
                items.put("GCode",(TreeItem<String>)call(window,"addCncJobToProject",new Class<?>[]{String.class,String.class,Path.class,String.class,Geometry.class,Geometry.class},"code","",null,"G21\nG90\nG0 X1 Y1\nM5\n",null,null));
                call(window,"selectProjectItem",new Class<?>[]{TreeItem.class},items.get(from));call(window,"editSelected"+from,new Class<?>[]{});
                var tab=(Tab)field(window,"toolTab");var panel=tab.getContent();var title=tab.getText();
                var owner=field(window,from.equals("GCode")?"gcodeEditor":from.toLowerCase(Locale.ROOT)+"Editor");
                org.flatcam.cam.geometry.GeometryEditSession session=null;
                if(from.equals("Geometry")){session=(org.flatcam.cam.geometry.GeometryEditSession)field(owner,"session");session.addRectangle(20,20,22,22,-1);}
                call(window,"selectProjectItem",new Class<?>[]{TreeItem.class},items.get(to));
                var menu=(ContextMenu)call(window,"buildContextMenuFor",new Class<?>[]{TreeItem.class},items.get(to));
                String key=switch(to){case "Gerber"->"ui.2eba946b2e1e";case "Excellon"->"ui.56ea8270adb2";case "GCode"->"ui.fb1b561e4268";default->"ui.aa5faa2dd7cd";};
                var edit=menu.getItems().stream().filter(item->I18n.text(key).equals(item.getText())).findFirst().orElseThrow();
                if(!edit.isDisable())edit.fire();
                assertSame(panel,tab.getContent(),"context edit must preserve the active editor panel");assertEquals(title,tab.getText());
                assertSame(tab,((TabPane)field(window,"leftTabs")).getSelectionModel().getSelectedItem());
                int active=0;for(String name:List.of("geometryEditor","gerberEditor","excellonEditor","gcodeEditor")){
                    var controller=field(window,name);var method=controller.getClass().getDeclaredMethod("isActive");method.setAccessible(true);if((boolean)method.invoke(controller))active++;
                }
                assertEquals(1,active);if(session!=null){assertSame(session,field(owner,"session"));assertTrue(session.canUndo());}
                var cancel=owner.getClass().getDeclaredMethod("cancel");cancel.setAccessible(true);cancel.invoke(owner);
                assertNotSame(panel,tab.getContent(),"the original exit route must remain usable");return null;
            });
        }finally{
            TerminalPanelTest.fx(()->{for(String name:List.of("geometryEditor","gerberEditor","excellonEditor","gcodeEditor")){
                var controller=field(window,name);var cancel=controller.getClass().getDeclaredMethod("cancel");cancel.setAccessible(true);cancel.invoke(controller);
            }window.disposeViewport();return null;});jobs.shutdown();
        }
    }
    @ParameterizedTest @MethodSource("editors")
    void openingAnotherToolNeverStrandsAnActiveEditor(String kind,int repetition)throws Exception {
        ToolDefaults.useStore(ToolDefaults.memoryStore());var jobs=new JobExecutor(1);
        var window=TerminalPanelTest.fx(()->{var w=new MainWindow(jobs);w.createScene();return w;});
        try {
            TerminalPanelTest.fx(()-> {
                var f=new GeometryFactory();var area=f.toGeometry(new Envelope(0,10,0,8));
                TreeItem<String> item;
                String method,controller;
                switch(kind){
                    case "Gerber" -> {item=(TreeItem<String>)call(window,"addGerberToProject",new Class<?>[]{String.class,Path.class,GerberImage.class},"copper",null,GerberImage.of("MM",Map.of(),area,area.getBoundary(),Map.of()));method="editSelectedGerber";controller="gerberEditor";}
                    case "Excellon" -> {var image=new ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y1.0","M30"));item=(TreeItem<String>)call(window,"addExcellonToProject",new Class<?>[]{String.class,Path.class,image.getClass()},"drills",null,image);method="editSelectedExcellon";controller="excellonEditor";}
                    case "GCode" -> {item=(TreeItem<String>)call(window,"addCncJobToProject",new Class<?>[]{String.class,String.class,Path.class,String.class,Geometry.class,Geometry.class},"code","",null,"G21\nG90\nG0 X1 Y1\nM5\n",null,null);method="editSelectedGCode";controller="gcodeEditor";}
                    default -> {item=(TreeItem<String>)call(window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},"shape","","MM",area.getBoundary(),true);method="editSelectedGeometry";controller="geometryEditor";}
                }
                call(window,"selectProjectItem",new Class<?>[]{TreeItem.class},item);
                call(window,method,new Class<?>[]{});
                var editor=field(window,controller);var active=editor.getClass().getDeclaredMethod("isActive");active.setAccessible(true);
                assertEquals(true,active.invoke(editor));
                org.flatcam.cam.geometry.GeometryEditSession session=null;String edited=null;
                if(kind.equals("Geometry")) {
                    session=(org.flatcam.cam.geometry.GeometryEditSession)field(editor,"session");
                    session.addRectangle(20,20,22,22,-1);edited=session.resultGeometry().toText();assertTrue(session.canUndo());
                }
                var tab=(Tab)field(window,"toolTab");var original=tab.getContent();String title=tab.getText();
                ((Runnable)call(window,"toolAction",new Class<?>[]{String.class},"calculators")).run();
                assertSame(original,tab.getContent(),"another tool must not replace the only route back to the editor");
                assertEquals(title,tab.getText());assertEquals(true,active.invoke(editor));
                for(String direct:List.of("openPaintTool","openPanelizeTool","openDoubleSidedTool","openTransformTool")) {
                    call(window,direct,new Class<?>[]{});assertSame(original,tab.getContent(),direct);
                }
                call(window,"openToolPanel",new Class<?>[]{String.class,javafx.scene.Node.class},"Other",new VBox());
                assertSame(original,tab.getContent(),"direct panel navigation must also preserve the editor");
                if(session!=null){assertSame(session,field(editor,"session"));assertEquals(edited,session.resultGeometry().toText());assertTrue(session.canUndo());}
                var cancel=editor.getClass().getDeclaredMethod("cancel");cancel.setAccessible(true);cancel.invoke(editor);
                assertEquals(false,active.invoke(editor));
                assertNotSame(original,tab.getContent(),"cancel must actually remove the editor panel");
                ((Runnable)call(window,"toolAction",new Class<?>[]{String.class},"calculators")).run();
                assertNotSame(original,tab.getContent(),"normal tools are available after leaving the editor");
                return null;
            });
        } finally {TerminalPanelTest.fx(()->{window.disposeViewport();return null;});jobs.shutdown();ToolDefaults.useStore(ToolDefaults.memoryStore());}
    }
}
