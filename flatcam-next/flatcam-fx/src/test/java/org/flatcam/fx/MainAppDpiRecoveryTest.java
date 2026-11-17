package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.*;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

/** Injects the stale scale notification observed in the real drag, while Win32 remains authoritative. */
@EnabledOnOs(OS.WINDOWS)
class MainAppDpiRecoveryTest {
    @Test void restoresScaleWithoutMaximizingOrReplacingTheScene()throws Exception{
        try(var s=new Session()){
            s.corruptScale();s.awaitRepair();
            TerminalPanelTest.fx(()->{
                Stage fresh=s.stage();assertFalse(fresh.isMaximized());assertSame(s.scene,fresh.getScene());
                assertEquals("0.1981234567",s.draft.getText());assertSame(s.draft,s.scene.getRoot().lookup(".text-field"));
                assertEquals(WindowsDpi.sample(fresh).scaleX(),fresh.getOutputScaleX());return null;
            });
        }
    }
    @Test void ownedDialogDraftSurvivesAndRepairWaitsUntilItCloses()throws Exception{
        try(var s=new Session()){
            TextField draft=new TextField("owned draft");
            Stage dialog=TerminalPanelTest.fx(()->{var stage=new Stage();stage.initOwner(s.original);stage.setOpacity(0);stage.setScene(new Scene(new Pane(draft),250,150));stage.show();return stage;});
            try{
                s.corruptScale();Thread.sleep(2600);
                TerminalPanelTest.fx(()->{assertSame(s.original,s.stage());assertTrue(dialog.isShowing());assertEquals("owned draft",draft.getText());dialog.hide();s.focus();return null;});
                s.awaitRepair();
            }finally{TerminalPanelTest.fx(()->{dialog.hide();return null;});}
        }
    }
    private static final class Session implements AutoCloseable {
        final MainApp app=new MainApp();final JobExecutor jobs=new JobExecutor(1);
        final Stage original;final Scene scene;final TextField draft;
        Session()throws Exception{
            original=TerminalPanelTest.fx(()->{
                javafx.application.Platform.setImplicitExit(false);
                var window=new MainWindow(jobs);Scene scene=window.createScene();var stage=new Stage();
                stage.setOpacity(0);stage.setScene(scene);stage.setWidth(900);stage.setHeight(650);stage.setX(45);stage.setY(45);stage.show();
                scene.getRoot().applyCss();scene.getRoot().layout();
                field(app,"currentStage",stage);field(app,"mainWindow",window);field(app,"jobExecutor",jobs);
                var install=MainApp.class.getDeclaredMethod("installDpiRescaleWorkaround",MainWindow.class);install.setAccessible(true);install.invoke(app,window);
                return stage;
            });
            scene=TerminalPanelTest.fx(original::getScene);
            draft=TerminalPanelTest.fx(()->{var t=(TextField)scene.getRoot().lookup(".text-field");t.setText("0.1981234567");return t;});
        }
        void focus()throws Exception{Class.forName("com.sun.javafx.stage.WindowHelper").getMethod("setFocused",Window.class,boolean.class).invoke(null,original,true);}
        void corruptScale()throws Exception{TerminalPanelTest.fx(()->{
            var sample=WindowsDpi.sample(original);assertNotNull(sample);
            double wrong=sample.scaleX()==1?2:1;
            Class.forName("com.sun.javafx.stage.WindowHelper").getMethod("notifyScaleChanged",Window.class,double.class,double.class).invoke(null,original,wrong,wrong);
            focus();assertEquals(wrong,original.getOutputScaleX());assertFalse(original.isMaximized());return null;
        });}
        Stage stage()throws Exception{var f=MainApp.class.getDeclaredField("currentStage");f.setAccessible(true);return (Stage)f.get(app);}
        void awaitRepair()throws Exception{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(System.nanoTime()<deadline){if(TerminalPanelTest.fx(()->stage()!=original))return;Thread.sleep(50);}
            fail("A restored stale-scale window was never repaired");
        }
        public void close()throws Exception{TerminalPanelTest.fx(()->{stage().hide();app.stop();return null;});jobs.shutdown();}
    }
    private static void field(MainApp app,String name,Object value)throws Exception{var f=MainApp.class.getDeclaredField(name);f.setAccessible(true);f.set(app,value);}
}
