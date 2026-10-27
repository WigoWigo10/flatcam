package org.flatcam.fx;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;

/** Same AWT-before-Glass sequence as the launcher; no MainApp/preferences/project data. */
public final class StartupSplashNativeTransitionProbe {
    public static void main(String[] args) throws Exception {
        int index=0;
        for(String arg:args) if(arg.startsWith("--screen=")) index=Integer.parseInt(arg.substring(9));
        var device=GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[index];
        var splash=StartupSplash.open(ThemeOption.DEFAULT,device.getIDstring());
        var bounds=new AtomicReference<Rectangle>();
        var failure=new AtomicReference<Throwable>();
        var rendered=new CompletableFuture<GraphicsRuntimeInfo>();
        try {
            SwingUtilities.invokeAndWait(()->bounds.set(window(splash).getBounds()));
            String marker=System.getenv("FLATCAM_SPLASH_PROBE_READY");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(marker!=null && !Files.exists(Path.of(marker)) && System.nanoTime()<deadline) Thread.sleep(10);
            if(marker!=null && !Files.exists(Path.of(marker))) throw new AssertionError("native observer did not see the splash");
            System.out.println("AWT before Glass: "+bounds.get()+" scale="+device.getDefaultConfiguration().getDefaultTransform());
            Platform.startup(()-> {
                try {
                    // Glass startup already triggers the defect. No foreground test Stage is needed.
                    new Canvas(16,16).snapshot(null,null);
                    new AnimationTimer() {
                        int frames;
                        @Override public void handle(long now) {
                            if(++frames>=12) { stop(); GraphicsRuntimeInfo.query(null).whenComplete((info,error)-> {
                                if(error==null) rendered.complete(info); else rendered.completeExceptionally(error);
                            }); }
                        }
                    }.start();
                } catch(Throwable error) { rendered.completeExceptionally(error); }
            });
            while(!rendered.isDone()) {
                SwingUtilities.invokeAndWait(()-> {
                    if(!bounds.get().equals(window(splash).getBounds())) failure.compareAndSet(null,new AssertionError("AWT bounds changed after Glass startup: "+window(splash).getBounds()));
                });
                if(System.nanoTime()>deadline+TimeUnit.SECONDS.toNanos(8)) throw new AssertionError("Glass startup timed out");
                Thread.sleep(10);
            }
            var graphics=rendered.get(1,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(()-> {
                if(!bounds.get().equals(window(splash).getBounds())) failure.compareAndSet(null,new AssertionError("AWT bounds changed: "+window(splash).getBounds()));
            });
            if(failure.get()!=null) throw new AssertionError(failure.get());
            System.out.println("PASS Java transition: screen="+device.getIDstring()+" bounds="+bounds.get()+" pipeline="+graphics.pipeline());
        } finally { splash.close(); Platform.exit(); SwingUtilities.invokeAndWait(()->{}); }
    }
    private static JWindow window(StartupSplash splash) {
        try { var field=StartupSplash.class.getDeclaredField("window"); field.setAccessible(true); return (JWindow)field.get(splash); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
