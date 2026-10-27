package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

public class StartupSplashTest {
    @TempDir Path temporary;
    @Test void optOutOverridesPreferencesAndProbesNeverShowASplash() {
        assertTrue(StartupSplash.enabled(new String[0],true,null,false));
        assertFalse(StartupSplash.enabled(new String[0],false,null,false));
        assertTrue(StartupSplash.enabled(new String[0],false,"true",false));
        assertFalse(StartupSplash.enabled(new String[0],true,"false",false));
        for(String flag:new String[]{"--probe","--diagnostics-probe","--no-splash"})
            assertFalse(StartupSplash.enabled(new String[]{flag},true,"true",false));
        assertFalse(StartupSplash.enabled(new String[0],true,"true",true));
    }
    @Test void placementFitsSmallScreensAndUsesTheirOwnCoordinateSpace() {
        for(var usable:new Rectangle[]{new Rectangle(0,0,1920,1040),new Rectangle(-1920,0,1920,1040),new Rectangle(2880,-900,1100,800),new Rectangle(0,0,400,220)}) {
            var bounds=StartupSplash.centeredBounds(usable);
            assertTrue(usable.contains(bounds));
            assertEquals(usable.getCenterX(),bounds.getCenterX(),1);
            assertEquals(usable.getCenterY(),bounds.getCenterY(),1);
            assertEquals(StartupSplash.LOGICAL_WIDTH/(double)StartupSplash.LOGICAL_HEIGHT,bounds.width/(double)bounds.height,.02);
        }
    }
    @Test void artworkRendersAtBothDpiScalesInAllFourThemesWithReadableText() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            try {
                Path output=Path.of("target"); Files.createDirectories(output);
                for(var theme:ThemeOption.values()) {
                    var art=new StartupSplash.Artwork(theme,"0.0.1-SNAPSHOT");
                    art.message="Preparando os painéis e o Plot Area…";
                    for(var background:new Color[]{art.palette.background(),art.palette.end()}) {
                        assertTrue(contrast(art.palette.text(),background)>=4.5,theme+" title contrast");
                        assertTrue(contrast(art.palette.secondary(),background)>=4.5,theme+" secondary contrast");
                    }
                    for(int scale:new int[]{1,2}) {
                        art.setSize(StartupSplash.LOGICAL_WIDTH,StartupSplash.LOGICAL_HEIGHT);
                        var image=new BufferedImage(StartupSplash.LOGICAL_WIDTH*scale,StartupSplash.LOGICAL_HEIGHT*scale,BufferedImage.TYPE_INT_RGB);
                        var graphics=image.createGraphics(); graphics.scale(scale,scale); art.paint(graphics); graphics.dispose();
                        if(theme==ThemeOption.CLASSIC_LIGHT || theme==ThemeOption.CLASSIC_DARK) {
                            int pure=(theme==ThemeOption.CLASSIC_LIGHT ? Color.WHITE : Color.BLACK).getRGB();
                            assertEquals(pure,image.getRGB(10*scale,10*scale));
                            assertEquals(pure,image.getRGB(610*scale,290*scale));
                        } else assertNotEquals(image.getRGB(10*scale,10*scale),image.getRGB(610*scale,290*scale));
                        assertTrue(countPixels(image,art.palette.text(),100*scale,40*scale,400*scale,100*scale)>500*scale*scale,
                                theme+" must draw a readable wordmark, not just the background");
                        assertTrue(countPixels(image,art.palette.text(),30*scale,210*scale,580*scale,240*scale)>100*scale*scale,
                                theme+" must draw the startup phase");
                        ImageIO.write(image,"png",output.resolve("startup-splash-"+theme.name().toLowerCase()+"-"+scale+"x.png").toFile());
                    }
                }
            } catch(Exception error) { throw new AssertionError(error); }
        });
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void closeBeforeTheEdtShowsTheWindowPreventsLateSplashWindows() throws Exception {
        var result=new AtomicReference<StartupSplash>();
        SwingUtilities.invokeAndWait(()-> {
            var splash=StartupSplash.open(ThemeOption.ICE_LIGHT,null);
            splash.message("Já terminou"); splash.close(); splash.close(); result.set(splash);
        });
        SwingUtilities.invokeAndWait(()-> {
            assertNull(field(result.get(),"window")); assertNull(field(result.get(),"timer"));
        });
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void windowDoesNotTakeKeyboardFocusAndClosingStopsAnimationAndDisposesIt() throws Exception {
        var splash=StartupSplash.open(ThemeOption.ICE_DARK,null);
        try {
            splash.message("Preparando os painéis e o Plot Area…");
            SwingUtilities.invokeAndWait(()-> {
                var window=(JWindow)field(splash,"window"); assertNotNull(window);
                assertTrue(window.isShowing()); assertFalse(window.getFocusableWindowState()); assertFalse(window.isAlwaysOnTop());
                assertTrue(((Timer)field(splash,"timer")).isRunning());
                assertEquals("Preparando os painéis e o Plot Area…",((StartupSplash.Artwork)field(splash,"artwork")).message);
            });
        } finally {
            splash.close(); splash.message("Too late");
            SwingUtilities.invokeAndWait(()-> { assertNull(field(splash,"window")); assertNull(field(splash,"timer")); });
        }
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void realLauncherClosesSplashAndExitsWhenJavaFxCannotInitialize() throws Exception {
        Path output=temporary.resolve("failure.log");
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process child=new ProcessBuilder(java,"--enable-native-access=ALL-UNNAMED","-Dglass.platform=FlatcamSplashFailure",
                "-Dflatcam.splash.enabled=true","-Djava.util.prefs.PreferencesFactory="+MemoryFactory.class.getName(),
                "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                FlatCamLauncher.class.getName(),"--no-diagnostics").redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(25,TimeUnit.SECONDS),"failed launcher left a splash/EDT alive");
            assertNotEquals(0,child.exitValue());
            assertTrue(Files.readString(output).contains("FlatcamSplashFailure"));
        } finally { if(child.isAlive()) { child.destroyForcibly(); child.waitFor(5,TimeUnit.SECONDS); } }
    }
    private static Object field(Object object,String name) {
        try { var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static int countPixels(BufferedImage image,Color color,int left,int top,int right,int bottom) {
        int count=0;
        for(int y=top;y<bottom;y++) for(int x=left;x<right;x++)
            if(image.getRGB(x,y)==color.getRGB()) count++;
        return count;
    }
    private static double contrast(Color a,Color b) {
        double x=luminance(a),y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
    private static double luminance(Color color) {
        return .2126*linear(color.getRed()/255.0)+.7152*linear(color.getGreen()/255.0)+.0722*linear(color.getBlue()/255.0);
    }
    private static double linear(double value) { return value<=.04045 ? value/12.92 : Math.pow((value+.055)/1.055,2.4); }

    /** Isolated child launch preferences; no writes to the user's registry. */
    public static final class MemoryFactory implements PreferencesFactory {
        private final Preferences root=new Memory(null,"");
        public MemoryFactory() { }
        public Preferences userRoot() { return root; }
        public Preferences systemRoot() { return root; }
    }
    private static final class Memory extends AbstractPreferences {
        final Map<String,String> values=new HashMap<>();
        final Map<String,Memory> children=new HashMap<>();
        Memory(Memory parent,String name) { super(parent,name); }
        protected void putSpi(String key,String value) { values.put(key,value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); children.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String name) { return children.computeIfAbsent(name,k->new Memory(this,k)); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
}
