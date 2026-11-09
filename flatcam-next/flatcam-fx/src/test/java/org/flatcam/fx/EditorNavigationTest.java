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
