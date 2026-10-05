package org.flatcam.fx;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.stage.Window;

/** Optional bridge to Prism internals, isolated here and checked against JavaFX 25.0.4.
 * No pipeline/device is created and no GPU selection setting is modified.
 * All Prism/native driver reads run on the QuantumRenderer thread, never on the FX thread.
 */
record GraphicsRuntimeInfo(String pipeline, String mode, String adapter, String driver, String scope) {
    static GraphicsRuntimeInfo unavailable(String reason) {
        return new GraphicsRuntimeInfo("não disponível", "não identificado", "não identificada", "não disponível", reason);
    }

    /** Call on the FX thread while the owner is shown. Null owner means the offscreen/default factory. */
    static CompletableFuture<GraphicsRuntimeInfo> query(Window owner) {
        CompletableFuture<GraphicsRuntimeInfo> result = new CompletableFuture<>();
        if (!Platform.isFxApplicationThread()) {
            result.complete(unavailable("Consulta deve ser solicitada na thread JavaFX.")); return result;
        }
        try {
            int ordinal = -1;
            if (owner != null) {
                Object peer = Class.forName("com.sun.javafx.stage.WindowHelper")
                        .getMethod("getPeer", Window.class).invoke(null, owner);
                if (peer != null) {
                    Object glassWindow = Class.forName("com.sun.javafx.tk.quantum.WindowStage")
                            .getMethod("getPlatformWindow").invoke(peer);
                    Object screen = Class.forName("com.sun.glass.ui.Window").getMethod("getScreen").invoke(glassWindow);
                    ordinal = (int) Class.forName("com.sun.glass.ui.Screen").getMethod("getAdapterOrdinal").invoke(screen);
                }
            }
            final int windowOrdinal = ordinal;
            final boolean hasOwner = owner != null;
            Runnable read = () -> result.complete(readOnRenderer(windowOrdinal, hasOwner));
            Class<?> jobClass = Class.forName("com.sun.javafx.tk.RenderJob");
            Object job = jobClass.getConstructor(Runnable.class).newInstance(read);
            Class<?> toolkitClass = Class.forName("com.sun.javafx.tk.Toolkit");
            Object toolkit = toolkitClass.getMethod("getToolkit").invoke(null);
            toolkitClass.getMethod("addRenderJob", jobClass).invoke(toolkit, job);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            result.complete(unavailable("Acesso ao Prism indisponível nesta execução; não foi inferido pela configuração solicitada."));
        }
        return result.completeOnTimeout(unavailable("O renderizador não respondeu em 3 segundos."), 3, TimeUnit.SECONDS);
    }

    private static GraphicsRuntimeInfo readOnRenderer(int ordinal, boolean hasOwner) {
        String name;
        try {
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            if (pipeline == null) return unavailable("Prism ainda não inicializado.");
            name = pipeline.getClass().getName();
            if (name.equals("com.sun.prism.sw.SWPipeline")) {
                return new GraphicsRuntimeInfo(name, "Compatibilidade por software (CPU / Prism SW)",
                        "não utilizada pelo pipeline Prism SW", "não se aplica", "Pipeline ativo, não apenas solicitado.");
            }
            if (!name.equals("com.sun.prism.d3d.D3DPipeline")) {
                return new GraphicsRuntimeInfo(name, "Pipeline ativo: " + pipeline.getClass().getSimpleName(),
                        "não identificada neste backend", "não disponível", "Identificação de GPU implementada para Direct3D no Windows.");
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
                                + " (Prism #" + selected + ", " + field(info, "deviceName") + ").");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
                return new GraphicsRuntimeInfo(name, "Aceleração gráfica Direct3D (GPU)", "não identificada", "não disponível",
                        "Pipeline confirmado; não foi possível identificar o adaptador da janela.");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            return unavailable("Consulta ao pipeline ativo não disponível nesta versão/execução.");
        }
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
                + "\nOrdem gráfica solicitada (não prova o modo ativo): " + System.getProperty("prism.order", "padrão da plataforma") + "\n";
    }
}
