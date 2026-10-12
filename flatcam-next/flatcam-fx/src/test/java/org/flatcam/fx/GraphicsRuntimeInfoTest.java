package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class GraphicsRuntimeInfoTest {
    @Test void defaultAdapterIsResolvedByFactoryIdentityNotFirstInstalledGpu() {
        Object first = new Object(), second = new Object();
        assertEquals(1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first, second}, second, -1, false));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first, second}, new Object(), -1, false));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first, second}, null, -1, false));
    }

    @Test void windowAdapterDoesNotFallBackToTheDefaultGpuOrAnUninitializedDevice() {
        Object first = new Object(), second = new Object();
        assertEquals(1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first, second}, first, 1, true));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first, null}, first, 1, true));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first}, first, -1, true));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(null, first, 0, true));
        assertEquals(-1, GraphicsRuntimeInfo.selectedAdapter(new Object[]{first}, first, 2, true));
    }

    @Test void unavailableDoesNotClaimHardwareOrSoftwareFromRequestedOrder() {
        var info = GraphicsRuntimeInfo.unavailable("fixture");
        assertEquals("não identificado", info.mode()); assertEquals("não identificada", info.adapter());
        assertTrue(info.summary().contains("não prova o modo ativo"));
        assertTrue(GraphicsRuntimeInfo.query(null).join().scope().contains("thread JavaFX"));
    }

    @Test void friendlyHardwareSummaryKeepsInternalIdentifiersInDiagnosticsOnly() {
        var info = new GraphicsRuntimeInfo("com.sun.prism.d3d.D3DPipeline", "Aceleração gráfica Direct3D (GPU)",
                "Intel(R) Arc(TM) Graphics", "igd9trinity64.dll — 32.0.101.8991",
                "Adaptador da janela principal (Prism #0, \\\\.\\DISPLAY1).",
                GraphicsRuntimeInfo.screenLabel(true, 1706.6666667, 960, 1.5, 1.5));
        String friendly = info.friendlySummary();
        assertTrue(friendly.contains("acelerado pela placa de vídeo (Direct3D)"));
        assertTrue(friendly.contains("Placa de vídeo em uso: Intel(R) Arc(TM) Graphics"));
        assertTrue(friendly.contains("Tela principal — 2560 × 1440 pixels; escala da interface: 150%"));
        assertFalse(friendly.contains("Prism")); assertFalse(friendly.contains("DISPLAY1"));
        assertFalse(friendly.contains(".dll")); assertFalse(friendly.contains("solicitada"));
        assertTrue(info.summary().contains(info.driver())); assertTrue(info.summary().contains(info.scope()));
        assertTrue(info.summary().contains("não prova o modo ativo"));
    }

    @Test void friendlySoftwareAndUnknownSummariesDoNotClaimAnActiveGpu() {
        var software = new GraphicsRuntimeInfo("com.sun.prism.sw.SWPipeline", "fixture", "GPU instalada",
                "fixture", "fixture", "Tela secundária");
        assertTrue(software.friendlySummary().contains("compatibilidade por software"));
        assertTrue(software.friendlySummary().contains("CPU, sem aceleração da placa de vídeo"));
        assertFalse(software.friendlySummary().contains("GPU instalada"));
        assertTrue(GraphicsRuntimeInfo.unavailable("fixture").friendlySummary().contains("não foi possível identificar"));
        assertEquals("Tela secundária — 1920 × 1080 pixels; escala da interface: 100%",
                GraphicsRuntimeInfo.screenLabel(false, 1920, 1080, 1, 1));
        assertEquals("Tela secundária — 2000 × 1800 pixels; escala da interface: 125% × 150%",
                GraphicsRuntimeInfo.screenLabel(false, 1600, 1200, 1.25, 1.5));
    }

    @Test @EnabledOnOs(OS.WINDOWS) void realOffscreenPrismQueryUsesRendererWithoutBlockingFx() throws Exception {
        var future = TerminalPanelTest.fx(() -> {
            new javafx.scene.canvas.Canvas(16, 16).snapshot(null, null);
            return GraphicsRuntimeInfo.query(null);
        });
        var info = future.get(5, TimeUnit.SECONDS);
        assertTrue(info.pipeline().startsWith("com.sun.prism."), info.summary());
        assertTrue(info.friendlySummary().contains("consulta sem janela"));
        if (info.pipeline().endsWith("D3DPipeline")) {
            assertFalse(info.adapter().contains("não identificada"), info.summary());
            assertTrue(info.scope().contains("offscreen"));
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void separateJvmReportsActualSoftwareEvenWhenGpuIsInstalled() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process process = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED", "-Dprism.order=sw",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"), LauncherProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(25, TimeUnit.SECONDS), "software probe timed out");
            String output = new String(process.getInputStream().readNBytes(32768), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("Compatibilidade por software (CPU / Prism SW)"), output);
            assertTrue(output.contains("Pipeline Prism ativo: com.sun.prism.sw.SWPipeline"), output);
            assertTrue(output.contains("não utilizada pelo pipeline Prism SW"), output);
        } finally { if (process.isAlive()) process.destroyForcibly(); process.getInputStream().close(); }
    }
}
