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

    @Test @EnabledOnOs(OS.WINDOWS) void realOffscreenPrismQueryUsesRendererWithoutBlockingFx() throws Exception {
        var future = TerminalPanelTest.fx(() -> {
            new javafx.scene.canvas.Canvas(16, 16).snapshot(null, null);
            return GraphicsRuntimeInfo.query(null);
        });
        var info = future.get(5, TimeUnit.SECONDS);
        assertTrue(info.pipeline().startsWith("com.sun.prism."), info.summary());
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
