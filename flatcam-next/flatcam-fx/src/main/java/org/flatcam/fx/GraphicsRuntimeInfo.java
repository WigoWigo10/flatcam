package org.flatcam.fx;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.stage.Window;

/** Optional bridge to Prism internals, isolated here and checked against JavaFX 25.0.4.
 * No pipeline/device is created and no GPU selection setting is modified.
 * All Prism/native driver reads run on the QuantumRenderer thread, never on the FX thread.
 */
record GraphicsRuntimeInfo(String pipeline, String mode, String adapter, String driver, String scope, String screen) {
    static GraphicsRuntimeInfo unavailable(String reason) {
        return unavailable(reason, "não identificada");
    }

    private static GraphicsRuntimeInfo unavailable(String reason, String screen) {
        return new GraphicsRuntimeInfo("não disponível", "não identificado", "não identificada", "não disponível", reason, screen);
    }

    /** Call on the FX thread while the owner is shown. Null owner means the offscreen/default factory. */
    static CompletableFuture<GraphicsRuntimeInfo> query(Window owner) {
        CompletableFuture<GraphicsRuntimeInfo> result = new CompletableFuture<>();
        if (!Platform.isFxApplicationThread()) {
            result.complete(unavailable("Consulta deve ser solicitada na thread JavaFX.")); return result;
        }
        String screen = describeWindowScreen(owner);
        try {
            int ordinal = -1;
            if (owner != null) {
                Object peer = Class.forName("com.sun.javafx.stage.WindowHelper")
                        .getMethod("getPeer", Window.class).invoke(null, owner);
                if (peer != null) {
                    Object glassWindow = Class.forName("com.sun.javafx.tk.quantum.WindowStage")
                            .getMethod("getPlatformWindow").invoke(peer);
                    Object glassScreen = Class.forName("com.sun.glass.ui.Window").getMethod("getScreen").invoke(glassWindow);
                    ordinal = (int) Class.forName("com.sun.glass.ui.Screen").getMethod("getAdapterOrdinal").invoke(glassScreen);
                }
            }
            final int windowOrdinal = ordinal;
            final boolean hasOwner = owner != null;
            Runnable read = () -> result.complete(readOnRenderer(windowOrdinal, hasOwner, screen));
            Class<?> jobClass = Class.forName("com.sun.javafx.tk.RenderJob");
            Object job = jobClass.getConstructor(Runnable.class).newInstance(read);
            Class<?> toolkitClass = Class.forName("com.sun.javafx.tk.Toolkit");
            Object toolkit = toolkitClass.getMethod("getToolkit").invoke(null);
            toolkitClass.getMethod("addRenderJob", jobClass).invoke(toolkit, job);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            result.complete(unavailable("Acesso ao Prism indisponível nesta execução; não foi inferido pela configuração solicitada.", screen));
        }
        return result.completeOnTimeout(unavailable("O renderizador não respondeu em 3 segundos.", screen), 3, TimeUnit.SECONDS);
    }

    private static GraphicsRuntimeInfo readOnRenderer(int ordinal, boolean hasOwner, String screen) {
        String name;
        try {
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            if (pipeline == null) return unavailable("Prism ainda não inicializado.", screen);
            name = pipeline.getClass().getName();
            if (name.equals("com.sun.prism.sw.SWPipeline")) {
                return new GraphicsRuntimeInfo(name, "Compatibilidade por software (CPU / Prism SW)",
                        "não utilizada pelo pipeline Prism SW", "não se aplica", "Pipeline ativo, não apenas solicitado.", screen);
            }
            if (!name.equals("com.sun.prism.d3d.D3DPipeline")) {
                return new GraphicsRuntimeInfo(name, "Pipeline ativo: " + pipeline.getClass().getSimpleName(),
                        "não identificada neste backend", "não disponível", "Identificação de GPU implementada para Direct3D no Windows.", screen);
            }
            try {
                Class<?> type = pipeline.getClass();
                var factoriesField = type.getDeclaredField("factories"); factoriesField.setAccessible(true);
                var defaultField = type.getDeclaredField("_default"); defaultField.setAccessible(true);
                int selected = selectedAdapter((Object[]) factoriesField.get(null), defaultField.get(pipeline), ordinal, hasOwner);
                if (selected < 0) throw new IllegalStateException("No initialized factory for this window");
                Class<?> infoClass = Class.forName("com.sun.prism.d3d.D3DDriverInformation");
                var constructor = infoClass.getDeclaredConstructor(); constructor.setAccessible(true);
                Method read = type.getDeclaredMethod("nGetDriverInformation", int.class, infoClass); read.setAccessible(true);
                Object info = read.invoke(null, selected, constructor.newInstance());
                if (info == null) throw new IllegalStateException("Driver information unavailable");
                Method version = infoClass.getMethod("getDriverVersion"); version.setAccessible(true);
                return new GraphicsRuntimeInfo(name, "Aceleração gráfica Direct3D (GPU)",
                        field(info, "deviceDescription"), field(info, "driverName") + " — " + version.invoke(info),
                        (hasOwner ? "Adaptador da janela principal" : "Adaptador padrão / renderização offscreen")
                                + " (Prism #" + selected + ", " + field(info, "deviceName") + ").", screen);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
                return new GraphicsRuntimeInfo(name, "Aceleração gráfica Direct3D (GPU)", "não identificada", "não disponível",
                        "Pipeline confirmado; não foi possível identificar o adaptador da janela.", screen);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            return unavailable("Consulta ao pipeline ativo não disponível nesta versão/execução.", screen);
        }
    }

    /** Public JavaFX screens describe the window, not the GPU ordinal or Windows DISPLAY numbering. */
    private static String describeWindowScreen(Window owner) {
        if (owner == null) return "consulta sem janela (nenhuma tela selecionada)";
        Screen best = null;
        double bestArea = 0;
        int intersecting = 0;
        for (Screen candidate : Screen.getScreens()) {
            var bounds = candidate.getBounds();
            double width = Math.max(0, Math.min(owner.getX() + owner.getWidth(), bounds.getMaxX())
                    - Math.max(owner.getX(), bounds.getMinX()));
            double height = Math.max(0, Math.min(owner.getY() + owner.getHeight(), bounds.getMaxY())
                    - Math.max(owner.getY(), bounds.getMinY()));
            double area = width * height;
            if (area > 0) intersecting++;
            if (area > bestArea) { bestArea = area; best = candidate; }
        }
        if (best == null) return "não identificada (janela fora das telas conectadas)";
        var bounds = best.getBounds();
        return screenLabel(best.equals(Screen.getPrimary()), bounds.getWidth(), bounds.getHeight(),
                best.getOutputScaleX(), best.getOutputScaleY())
                + (intersecting > 1 ? " — onde está a maior parte da janela" : "");
    }

    static String screenLabel(boolean primary, double width, double height, double scaleX, double scaleY) {
        String scale = Math.round(scaleX * 100) + "%";
        if (scaleX != scaleY) scale += " × " + Math.round(scaleY * 100) + "%";
        return (primary ? "Tela principal" : "Tela secundária") + " — "
                + Math.round(width * scaleX) + " × " + Math.round(height * scaleY)
                + " pixels; escala da interface: " + scale;
    }

    String friendlySummary() {
        String graphics;
        switch (pipeline) {
            case "com.sun.prism.d3d.D3DPipeline" -> graphics = "Modo gráfico: acelerado pela placa de vídeo (Direct3D).\n"
                    + "Placa de vídeo em uso: " + adapter + ".\n";
            case "com.sun.prism.sw.SWPipeline" -> graphics = "Modo gráfico: compatibilidade por software.\n"
                    + "O desenho é processado pela CPU, sem aceleração da placa de vídeo.\n";
            default -> graphics = "Modo gráfico: não foi possível identificar a aceleração em uso.\n";
        }
        return graphics + "Tela da janela: " + screen + ".\n";
    }

    /** Identity in the initialized factory array proves the default ordinal; never assume GPU zero. */
    static int selectedAdapter(Object[] factories, Object defaultFactory, int ordinal, boolean hasOwner) {
        if (factories == null) return -1;
        if (hasOwner) return ordinal >= 0 && ordinal < factories.length && factories[ordinal] != null ? ordinal : -1;
        if (defaultFactory != null) for (int index = 0; index < factories.length; index++)
            if (factories[index] == defaultFactory) return index;
        return -1;
    }

    private static String field(Object info, String name) throws ReflectiveOperationException {
        var field = info.getClass().getField(name); field.setAccessible(true);
        Object value = field.get(info); return value == null ? "não disponível" : value.toString();
    }

    String summary() {
        return "Renderização JavaFX: " + mode + "\nPipeline Prism ativo: " + pipeline
                + "\nPlaca de vídeo usada pelo Prism: " + adapter + "\nDriver: " + driver + "\n" + scope
                + "\nTela da janela: " + screen
                + "\nOrdem gráfica solicitada (não prova o modo ativo): " + System.getProperty("prism.order", "padrão da plataforma") + "\n";
    }
}
