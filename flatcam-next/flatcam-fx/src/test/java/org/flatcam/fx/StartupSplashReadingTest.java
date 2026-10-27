package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class StartupSplashReadingTest {
    @Test void graphicsLabelUsesActiveBackendAndNeverCallsEveryGpuDedicated() throws Exception {
        var hardware=StartupSplash.GraphicsStatus.of(info("com.sun.prism.d3d.D3DPipeline","GPU de Joa\u0303o — edição gráfica"));
        assertEquals("Modo gráfico: GPU (aceleração gráfica)",hardware.mode());
        assertEquals("Placa em uso: GPU de João — edição gráfica",hardware.device());
        assertFalse(hardware.mode().contains("dedicada"));
        var software=StartupSplash.GraphicsStatus.of(info("com.sun.prism.sw.SWPipeline","GPU instalada"));
        assertTrue(software.mode().contains("Software")); assertEquals("Processamento gráfico pela CPU",software.device());
        assertFalse(software.device().contains("GPU instalada"));
        assertTrue(StartupSplash.GraphicsStatus.of(null).mode().contains("indisponível"));
        SwingUtilities.invokeAndWait(()-> {
            try {
                Path output=Path.of("target/startup-splash-reading"); Files.createDirectories(output);
                for(var theme:ThemeOption.values()) for(var status:new StartupSplash.GraphicsStatus[]{hardware,software}) {
                    var art=new StartupSplash.Artwork(theme,"0.0.1-SNAPSHOT — Edição de avaliação"); art.setSize(620,300);
                    art.graphicsStatus=status; art.message="Área de trabalho pronta."; art.footer="Clique para continuar • fecha em 5 s";
                    var image=new BufferedImage(620,300,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics(); art.paint(graphics); graphics.dispose();
                    String kind=status==hardware ? "gpu" : "software";
                    javax.imageio.ImageIO.write(image,"png",output.resolve(theme.name().toLowerCase(Locale.ROOT)+"-"+kind+".png").toFile());
                }
            } catch(java.io.IOException error) { throw new AssertionError(error); }
        });
    }

    @Test void accentsNormalizeAndTruncationKeepsWholeUnicodeCharacters() {
        String accents="ÁÀÂÃÄ ÉÈÊË ÍÌÎÏ ÓÒÔÕÖ ÚÙÛÜ Ç áàâãä éèêë íìîï óòôõö úùûü ç";
        assertEquals("Ação — São Luís",StartupSplash.displayText("Ac\u0327a\u0303o — Sa\u0303o Lui\u0301s"));
        assertEquals("GPU de João versão 2",StartupSplash.displayText("GPU de João\nversão 2"));
        assertEquals(-1,StartupSplash.Artwork.textFont(Font.PLAIN,14,accents).canDisplayUpTo(accents));
        var image=new BufferedImage(620,300,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics();
        try {
            graphics.setFont(StartupSplash.Artwork.textFont(Font.PLAIN,14,accents));
            var metrics=graphics.getFontMetrics(); String text="Ação 👩‍🔧 — São Luís, edição e aceleração gráfica";
            var clusters=BreakIterator.getCharacterInstance(Locale.ROOT); clusters.setText(text);
            for(int width=0;width<metrics.stringWidth(text);width++) {
                String fitted=StartupSplash.Artwork.fit(metrics,text,width);
                assertTrue(metrics.stringWidth(fitted)<=width);
                if(!fitted.isEmpty()) {
                    assertTrue(fitted.endsWith("…")); String prefix=fitted.substring(0,fitted.length()-1);
                    assertTrue(text.startsWith(prefix)); assertTrue(clusters.isBoundary(prefix.length()),"split Unicode cluster: "+fitted);
                }
            }
        } finally { graphics.dispose(); }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void readingTimeStartsWithTheReadyPaintRatherThanTheStartOfLoading() throws Exception {
        var clock=new AtomicLong(1_000_000_000L); var splash=StartupSplash.open(ThemeOption.ICE_DARK,null,clock::get);
        try {
            SwingUtilities.invokeAndWait(()->{});
            clock.addAndGet(TimeUnit.SECONDS.toNanos(40));
            SwingUtilities.invokeAndWait(()-> { tick(splash); assertNotNull(field(splash,"window")); });
            splash.message("Ac\u0327a\u0303o e aceleração gráfica");
            SwingUtilities.invokeAndWait(()->assertEquals("Ação e aceleração gráfica",((StartupSplash.Artwork)field(splash,"artwork")).message));
            splash.ready(info("com.sun.prism.d3d.D3DPipeline","Intel(R) Arc(TM) Graphics"));
            SwingUtilities.invokeAndWait(()-> {
                var art=(StartupSplash.Artwork)field(splash,"artwork");
                var image=new BufferedImage(art.getWidth(),art.getHeight(),BufferedImage.TYPE_INT_RGB);
                var graphics=image.createGraphics(); art.paint(graphics); graphics.dispose();
                assertEquals(clock.get(),field(splash,"firstReadyPaint"));
                assertTrue(art.getAccessibleContext().getAccessibleDescription().contains("Arc(TM)"));
            });
            clock.addAndGet(StartupSplash.READING_NANOS-1);
            SwingUtilities.invokeAndWait(()-> { tick(splash); assertNotNull(field(splash,"window")); });
            clock.incrementAndGet(); SwingUtilities.invokeAndWait(()->tick(splash));
            SwingUtilities.invokeAndWait(()-> { assertNull(field(splash,"window")); assertNull(field(splash,"timer")); });
        } finally { splash.close(); SwingUtilities.invokeAndWait(()->{}); }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void clickSkipsReadingOnlyAfterTheWorkspaceIsReady() throws Exception {
        var splash=StartupSplash.open(ThemeOption.ICE_LIGHT,null);
        try {
            SwingUtilities.invokeAndWait(()-> { click(splash); assertNotNull(field(splash,"window")); });
            splash.ready(info("com.sun.prism.sw.SWPipeline","unused GPU"));
            SwingUtilities.invokeAndWait(()->click(splash));
            SwingUtilities.invokeAndWait(()->assertNull(field(splash,"window")));
        } finally { splash.close(); SwingUtilities.invokeAndWait(()->{}); }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void actualLauncherHandoffReportsGpuAndSoftwareAndKeepsFxResponsiveWhileReading() throws Exception {
        Path output=Path.of("target/startup-splash-reading"); Files.createDirectories(output);
        for(String backend:new String[]{"d3d,sw","sw"}) {
            Path log=output.resolve(backend.equals("sw") ? "software.log" : "gpu.log");
            Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),
                    "--enable-native-access=ALL-UNNAMED","-XX:+UnlockDiagnosticVMOptions","-XX:DisableIntrinsic=_indexOfUL",
                    "-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dprism.order="+backend,
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    NativeReadingProbe.class.getName(),backend).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(25,TimeUnit.SECONDS),"splash handoff process did not exit");
                String text=Files.readString(log,StandardCharsets.UTF_8);
                assertEquals(0,child.exitValue(),text); assertTrue(text.contains("PASS readMs="),text);
                if(backend.equals("sw")) assertTrue(text.contains("Software (compatibilidade)"),text);
            } finally { if(child.isAlive()) { child.destroyForcibly(); child.waitFor(5,TimeUnit.SECONDS); } }
        }
    }

    private static GraphicsRuntimeInfo info(String pipeline,String adapter) { return new GraphicsRuntimeInfo(pipeline,"fixture",adapter,"fixture","fixture","fixture"); }
    private static Object field(Object object,String name) {
        try { var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void tick(StartupSplash splash) {
        try { var tick=StartupSplash.class.getDeclaredMethod("tick"); tick.setAccessible(true); tick.invoke(splash); }
        catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void click(StartupSplash splash) {
        var art=(StartupSplash.Artwork)field(splash,"artwork");
        art.dispatchEvent(new MouseEvent(art,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,100,100,1,false));
    }

    /** Isolated real JavaFX window and launcher handoff; no MainApp/user preferences. */
    public static final class NativeReadingProbe {
        public static void main(String[] args) throws Exception {
            var splash=StartupSplash.open(ThemeOption.ICE_DARK,null);
            var heartbeat=new AtomicInteger(); var failure=new AtomicReference<Throwable>();
            try {
                javafx.application.Platform.startup(()-> {
                    try {
                        var field=FlatCamLauncher.class.getDeclaredField("splash"); field.setAccessible(true); field.set(null,splash);
                        var stage=new javafx.stage.Stage(); stage.setTitle("FlatCAM FX — Verificação da splash");
                        stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(),620,300));
                        stage.show(); FlatCamLauncher.mainWindowShown(stage);
                    } catch(Throwable error) { failure.set(error); }
                });
                var firstPaint=new AtomicLong(Long.MIN_VALUE); var closed=new AtomicBoolean();
                var status=new AtomicReference<StartupSplash.GraphicsStatus>(); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
                while(!closed.get() && failure.get()==null && System.nanoTime()<deadline) {
                    SwingUtilities.invokeAndWait(()-> {
                        long at=(Long)field(splash,"firstReadyPaint"); if(at!=Long.MIN_VALUE) firstPaint.compareAndSet(Long.MIN_VALUE,at);
                        var art=(StartupSplash.Artwork)field(splash,"artwork"); if(art!=null && (Boolean)field(splash,"ready")) status.set(art.graphicsStatus);
                        closed.set(field(splash,"window")==null && firstPaint.get()!=Long.MIN_VALUE);
                    });
                    javafx.application.Platform.runLater(heartbeat::incrementAndGet); Thread.sleep(20);
                }
                assertNull(failure.get()); assertTrue(closed.get(),"splash never completed reading");
                long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-firstPaint.get());
                assertTrue(millis>=5000,"reading ended early: "+millis); assertTrue(heartbeat.get()>20,"FX was blocked");
                assertNotNull(status.get());
                assertFalse(status.get().mode().contains("indisponível"),status.get().toString());
                if(args[0].equals("sw")) assertTrue(status.get().mode().contains("Software"));
                System.out.println("PASS readMs="+millis+" fxCallbacks="+heartbeat.get()+" "+status.get());
            } finally { splash.close(); javafx.application.Platform.exit(); }
        }
    }
}
