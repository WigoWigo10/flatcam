package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.*;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class MainAppDpiTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void recreateRetainsSceneAndDraftWithoutForcingRestoredWindowToMaximize(boolean maximize)throws Exception{
        var jobs=new JobExecutor(1);var app=new MainApp();
        try{TerminalPanelTest.fx(()->{
            javafx.application.Platform.setImplicitExit(false);
            var window=new MainWindow(jobs);Scene scene=window.createScene();
            Stage old=new Stage();old.setOpacity(0);old.setTitle("DPI test");old.setScene(scene);old.setWidth(1000);old.setHeight(700);old.show();
            scene.getRoot().applyCss();scene.getRoot().layout();
            TextField draft=(TextField)scene.getRoot().lookup(".text-field");assertNotNull(draft);draft.setText("0.1981234567");
            field(app,"currentStage",old);field(app,"mainWindow",window);field(app,"jobExecutor",jobs);
            var target=Screen.getScreens().getLast();var b=target.getVisualBounds();
            String id=WindowsDpi.screenId(target);assertNotNull(id);
            var method=MainApp.class.getDeclaredMethod("recreateStage",MainWindow.class,WindowsDpi.Placement.class,String.class);method.setAccessible(true);
            method.invoke(app,window,new WindowsDpi.Placement(b.getMinX()+35,b.getMinY()+35,900,650,maximize),id);
            Stage fresh=(Stage)get(app,"currentStage");
            assertNotSame(old,fresh);assertFalse(old.isShowing());assertSame(scene,fresh.getScene());
            assertSame(draft,scene.getRoot().lookup(".text-field"));assertEquals("0.1981234567",draft.getText());
            assertEquals(maximize,fresh.isMaximized());assertEquals(id,window.currentScreenId());
            var sample=WindowsDpi.sample(fresh);assertNotNull(sample);assertEquals(target.getBounds(),sample.screen().getBounds());
            assertEquals(target.getOutputScaleX(),fresh.getOutputScaleX());fresh.hide();return null;
        });}finally{TerminalPanelTest.fx(()->{app.stop();return null;});jobs.shutdown();}
    }
    private static void field(MainApp app,String name,Object value)throws Exception{var f=MainApp.class.getDeclaredField(name);f.setAccessible(true);f.set(app,value);}
    private static Object get(MainApp app,String name)throws Exception{var f=MainApp.class.getDeclaredField(name);f.setAccessible(true);return f.get(app);}
}
