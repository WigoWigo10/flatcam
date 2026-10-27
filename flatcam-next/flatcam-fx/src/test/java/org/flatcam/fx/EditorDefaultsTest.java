package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.flatcam.cam.excellon.ExcellonImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class EditorDefaultsTest {
    @BeforeEach @AfterEach void reset() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    private static Object field(Object target,String name) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void fx(java.util.concurrent.Callable<Void> work) throws Exception {
        try { Platform.startup(()->{}); } catch (IllegalStateException started) { }
        var task=new FutureTask<>(work); Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
    private static Object stub(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->null);
    }
    @Test void gerberDefaultsUseObjectUnitsAndDoNotChangeOpenDrafts() throws Exception {
        ToolDefaults.set("editor.gerber.size",false,"0.05");
        ToolDefaults.set("editor.gerber.count",true,"8");
        ToolDefaults.set("editor.gerber.type",true,"R");
        fx(()-> {
            var ctor=GerberEditToolPanel.class.getDeclaredConstructors()[0];
            Object[] args=new Object[ctor.getParameterCount()]; var types=ctor.getParameterTypes();
            for(int i=0;i<types.length;i++) args[i]=types[i]==String.class ? (i==0 ? "Fixture" : "IN")
                    : types[i]==boolean.class ? false : types[i]==Map.class ? Map.of() : stub(types[i]);
            var panel=(GerberEditToolPanel)ctor.newInstance(args);
            assertEquals("0.05",((TextField)field(panel,"apertureWidthField")).getText());
            assertEquals("8",((TextField)field(panel,"arrayCountField")).getText());
            assertEquals("R",((ComboBox<?>)field(panel,"apertureTypeBox")).getValue());
            ToolDefaults.set("editor.gerber.size",false,"0.1");
            assertEquals("0.05",((TextField)field(panel,"apertureWidthField")).getText());
            return null;
        });
    }
    @Test void excellonPreservesSelectedToolAndUsesConfiguredArrayAndEmptyToolDiameter() throws Exception {
        ToolDefaults.set("editor.excellon.diameter",false,"0.04");
        ToolDefaults.set("editor.excellon.pitchx",false,"0.3");
        ToolDefaults.set("editor.excellon.rows",true,"4");
        fx(()-> {
            var controller=new ExcellonEditorController(new PlotAreaView(),(ExcellonEditorController.Host)stub(ExcellonEditorController.Host.class));
            for(boolean existing:List.of(true,false)) {
                var image=ExcellonImage.of("IN",existing ? Map.of(1,0.02) : Map.of(),List.of(),List.of(),new GeometryFactory().createPolygon());
                controller.start(new TreeItem<>("Holes"),image);
                assertEquals(existing ? "0.02" : "0.04",((TextField)field(controller,"diameterField")).getText());
                assertEquals("0.3",((TextField)field(controller,"pitchXField")).getText());
                assertEquals("4",((TextField)field(controller,"rowsField")).getText());
                controller.cancel();
            }
            return null;
        });
    }
    @Test void editorDefaultsRejectInvalidDimensionsAndPersistBothUnits() {
        for(String key:List.of("editor.gerber.size","editor.excellon.diameter","editor.gerber.scale"))
            assertThrows(IllegalArgumentException.class,()->ToolDefaults.set(key,true,"0"));
        assertThrows(IllegalArgumentException.class,()->ToolDefaults.set("editor.geometry.selectionlimit",true,"0"));
        ToolDefaults.set("editor.gerber.size",true,"1.2"); ToolDefaults.set("editor.gerber.size",false,"0.06");
        var saved=ToolDefaults.export(); ToolDefaults.resetAll(); ToolDefaults.importFrom(saved);
        assertEquals("1.2",ToolDefaults.text("editor.gerber.size",true));
        assertEquals("0.06",ToolDefaults.text("editor.gerber.size",false));
    }
}
