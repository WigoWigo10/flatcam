package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/** Geometry simulation is separate from native per-monitor/forced-DPI checks. */
public class StartupSplashDisplayTest {
    private static final double[] SCALES={1,1.25,1.5,1.75,2,2.5,3};
    private static final Path OUTPUT=Path.of("target/startup-splash-displays");

    static Stream<Arguments> displays() {
        return Stream.of(new int[]{800,600},new int[]{1024,768},new int[]{1280,720},
                new int[]{1366,768},new int[]{1920,1080},new int[]{2560,1440},
                new int[]{2880,1800},new int[]{3840,2160},new int[]{1080,1920},new int[]{7680,4320})
                .flatMap(size->java.util.Arrays.stream(SCALES).mapToObj(scale->Arguments.of(size[0],size[1],scale)));
    }

    @ParameterizedTest(name="{0}x{1} at {2}x") @MethodSource("displays")
    void placementFitsResolutionScaleTaskbarAndNegativeDesktopOrigins(int pixelsX,int pixelsY,double dpi) {
        for(var origin:new Point[]{new Point(0,0),new Point(-1920,522),new Point(2880,-1080),new Point(-3840,-2160)}) {
            var screen=new Rectangle(origin.x,origin.y,(int)Math.floor(pixelsX/dpi),(int)Math.floor(pixelsY/dpi));
            for(var insets:new Insets[]{new Insets(0,0,48,0),new Insets(48,0,0,0),new Insets(0,80,0,0),new Insets(0,0,0,80)}) {
                Rectangle usable=usable(screen,insets), bounds=StartupSplash.centeredBounds(usable);
                assertTrue(usable.contains(bounds),()->"clipped: "+usable+" / "+bounds);
                assertEquals(usable.getCenterX(),bounds.getCenterX(),.5);
                assertEquals(usable.getCenterY(),bounds.getCenterY(),.5);
                assertTrue(bounds.width<=620 && bounds.height<=300,"must not double-apply DPI scaling");
                assertTrue(bounds.width<=usable.width-31 && bounds.height<=usable.height-31,"screen margin");
                assertEquals(620.0/300,bounds.width/(double)bounds.height,.025,"proportions on small screens");
                var shape=StartupSplash.windowShape(bounds.width,bounds.height);
                double sx=bounds.width/620.0,sy=bounds.height/300.0;
                assertFalse(shape.contains(0,0),"outside corner must be cut");
                assertTrue(shape.contains(9*sx,2*sy),"top rounded border must remain inside window mask");
                assertTrue(shape.contains(2*sx,9*sy),"left rounded border must remain inside window mask");
            }
        }
    }

    @Test void fractionalScalesAndReducedCardsKeepTitleAndStatusInsideTheArtwork() throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(()-> {
            try {
                for(var theme:ThemeOption.values()) for(double dpi:SCALES) {
                    // Full card and the reduced card required by 800x600 at 300%, with taskbar.
                    for(var size:new Dimension[]{new Dimension(620,300),new Dimension(234,113)}) {
                        var art=new StartupSplash.Artwork(theme,"0.0.1-SNAPSHOT");
                        art.graphicsStatus=StartupSplash.GraphicsStatus.of(new GraphicsRuntimeInfo("com.sun.prism.d3d.D3DPipeline","fixture","GPU de João — edição gráfica","fixture","fixture","fixture"));
                        art.message="Preparando os painéis e o Plot Area…";
                        art.setSize(size);
                        var image=new BufferedImage((int)Math.ceil(size.width*dpi),(int)Math.ceil(size.height*dpi),BufferedImage.TYPE_INT_ARGB);
                        var graphics=image.createGraphics(); graphics.scale(dpi,dpi); graphics.clip(StartupSplash.windowShape(size.width,size.height)); art.paint(graphics); graphics.dispose();
                        double sx=size.width*dpi/620, sy=size.height*dpi/300;
                        assertTrue(textPixels(image,art.palette.text(),100,40,400,100,sx,sy)>Math.max(20,350*sx*sy),theme+" title at "+dpi);
                        String name=theme.name().toLowerCase(Locale.ROOT)+"-"+(int)(dpi*100)+"pct-"+size.width+".png";
                        ImageIO.write(image,"png",OUTPUT.resolve(name).toFile());
                        // Small antialiased letters need not contain a pixel of the exact text colour.
                        art.message="";
                        art.graphicsStatus=new StartupSplash.GraphicsStatus("","");
                        var background=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
                        var bg=background.createGraphics(); bg.scale(dpi,dpi); bg.clip(StartupSplash.windowShape(size.width,size.height)); art.paint(bg); bg.dispose();
                        assertTrue(changedPixels(image,background,30,210,580,240,sx,sy)>Math.max(10,60*sx*sy),theme+" phase at "+dpi+" / "+size);
                        assertTrue(changedPixels(image,background,30,172,580,189,sx,sy)>Math.max(10,60*sx*sy),theme+" graphics mode at "+dpi+" / "+size);
                        assertTrue(changedPixels(image,background,30,190,580,209,sx,sy)>Math.max(10,60*sx*sy),theme+" accented GPU name at "+dpi+" / "+size);
                    }
                }
            } catch(java.io.IOException error) { throw new AssertionError(error); }
        });
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void nativeWindowsFitEveryConnectedScreenAtActualAndForcedDpi() throws Exception {
        Files.createDirectories(OUTPUT);
        String java=Path.of(System.getProperty("java.home"),"bin/java.exe").toString();
        for(String scale:new String[]{"actual","1","1.25","1.5","1.75","2","2.5","3"}) {
            var command=new java.util.ArrayList<String>();
            command.add(java); command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-XX:+UnlockDiagnosticVMOptions"); command.add("-XX:DisableIntrinsic=_indexOfUL");
            if(!scale.equals("actual")) command.add("-Dsun.java2d.uiScale="+scale);
            command.add("-cp"); command.add(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")));
            command.add(NativeProbe.class.getName()); command.add(scale);
            Path log=OUTPUT.resolve("native-"+scale+".log");
            Process child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(25,TimeUnit.SECONDS),"native splash did not exit at DPI "+scale);
                assertEquals(0,child.exitValue(),()->"DPI "+scale+": "+read(log));
                assertTrue(read(log).contains("PASS fallback"),()->read(log));
            } finally { if(child.isAlive()) { child.destroyForcibly(); child.waitFor(5,TimeUnit.SECONDS); } }
        }
    }

    private static String read(Path log) {
        try { return Files.readString(log); } catch(java.io.IOException error) { return error.toString(); }
    }
    private static Rectangle usable(Rectangle screen,Insets insets) {
        return new Rectangle(screen.x+insets.left,screen.y+insets.top,screen.width-insets.left-insets.right,screen.height-insets.top-insets.bottom);
    }
    private static int textPixels(BufferedImage image,Color text,int left,int top,int right,int bottom,double sx,double sy) {
        int count=0;
        for(int y=(int)(top*sy);y<Math.min(image.getHeight(),(int)Math.ceil(bottom*sy));y++)
            for(int x=(int)(left*sx);x<Math.min(image.getWidth(),(int)Math.ceil(right*sx));x++)
                if(image.getRGB(x,y)==text.getRGB()) count++;
        return count;
    }
    private static int changedPixels(BufferedImage image,BufferedImage background,int left,int top,int right,int bottom,double sx,double sy) {
        int count=0;
        for(int y=(int)(top*sy);y<Math.min(image.getHeight(),(int)Math.ceil(bottom*sy));y++)
            for(int x=(int)(left*sx);x<Math.min(image.getWidth(),(int)Math.ceil(right*sx));x++) {
                int a=image.getRGB(x,y),b=background.getRGB(x,y);
                if(Math.abs((a&255)-(b&255))>24 || Math.abs(((a>>8)&255)-((b>>8)&255))>24 || Math.abs(((a>>16)&255)-((b>>16)&255))>24) count++;
            }
        return count;
    }

    /** Real JWindow probes only; does not load MainApp or touch user preferences/display settings. */
    public static final class NativeProbe {
        public static void main(String[] args) throws Exception {
            var environment=GraphicsEnvironment.getLocalGraphicsEnvironment();
            for(var device:environment.getScreenDevices()) for(var theme:new ThemeOption[]{ThemeOption.ICE_LIGHT,ThemeOption.ICE_DARK})
                check(device,device.getIDstring(),theme,args[0]);
            check(environment.getDefaultScreenDevice(),"disconnected-monitor-test",ThemeOption.ICE_LIGHT,args[0]);
            System.out.println("PASS fallback");
        }
        private static void check(GraphicsDevice expected,String saved,ThemeOption theme,String forced) throws Exception {
            var splash=StartupSplash.open(theme,saved);
            try {
                SwingUtilities.invokeAndWait(()->{}); // creation and native show have returned
                SwingUtilities.invokeAndWait(()-> {
                    try {
                        var field=StartupSplash.class.getDeclaredField("window"); field.setAccessible(true);
                        var window=(JWindow)field.get(splash); assertNotNull(window,"splash failed to show");
                        var configuration=expected.getDefaultConfiguration();
                        var usable=usable(configuration.getBounds(),Toolkit.getDefaultToolkit().getScreenInsets(configuration));
                        assertTrue(window.isShowing());
                        assertEquals(expected.getIDstring(),window.getGraphicsConfiguration().getDevice().getIDstring(),"wrong monitor");
                        assertEquals(StartupSplash.centeredBounds(usable),window.getBounds(),"native window was moved/resized unexpectedly");
                        assertEquals(window.getLocation(),window.getLocationOnScreen(),"native and logical positions disagree");
                        assertTrue(usable.contains(window.getBounds()),"native window clipped by screen/taskbar");
                        assertEquals(configuration.getDefaultTransform(),window.getGraphicsConfiguration().getDefaultTransform(),"wrong DPI");
                        if(window.getShape()!=null) {
                            double sx=window.getWidth()/620.0,sy=window.getHeight()/300.0;
                            assertTrue(window.getShape().contains(9*sx,2*sy),"native mask clips top border");
                            assertTrue(window.getShape().contains(2*sx,9*sy),"native mask clips left border");
                        }
                        assertFalse(window.getFocusableWindowState()); assertFalse(window.isAlwaysOnTop());
                        var mode=expected.getDisplayMode(); var transform=configuration.getDefaultTransform();
                        System.out.printf(Locale.ROOT,"PASS forced=%s screen=%s pixels=%dx%d scale=%.2fx%.2f usable=%s splash=%s theme=%s%n",
                                forced,expected.getIDstring(),mode.getWidth(),mode.getHeight(),transform.getScaleX(),transform.getScaleY(),usable,window.getBounds(),theme);
                    } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
                });
            } finally { splash.close(); SwingUtilities.invokeAndWait(()->{}); }
        }
    }
}
