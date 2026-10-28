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
        return unavailable(reason, I18n.text("ui.230be04124ca"));
    }

    private static GraphicsRuntimeInfo unavailable(String reason, String screen) {
        return new GraphicsRuntimeInfo(I18n.text("ui.d25d2f15d8b7"), I18n.text("ui.f7908086ae43"), I18n.text("ui.230be04124ca"), I18n.text("ui.d25d2f15d8b7"), reason, screen);
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
            result.complete(unavailable(I18n.text("ui.556cf97b040c"), screen));
        }
        return result.completeOnTimeout(unavailable(I18n.text("ui.7bd3a401418f"), screen), 3, TimeUnit.SECONDS);
    }

    private static GraphicsRuntimeInfo readOnRenderer(int ordinal, boolean hasOwner, String screen) {
        String name;
        try {
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            if (pipeline == null) return unavailable(I18n.text("ui.ce1ef2ccf34e"), screen);
            name = pipeline.getClass().getName();
            if (name.equals("com.sun.prism.sw.SWPipeline")) {
                return new GraphicsRuntimeInfo(name, I18n.text("ui.99c44ff141be"),
                        I18n.text("ui.7b651a3be41f"), I18n.text("ui.d490920d1843"), I18n.text("ui.f74907e9cc31"), screen);
            }
            if (!name.equals("com.sun.prism.d3d.D3DPipeline")) {
                return new GraphicsRuntimeInfo(name, I18n.text("ui.c619007f5b13") + pipeline.getClass().getSimpleName(),
                        I18n.text("ui.bfd770cd31c4"), I18n.text("ui.d25d2f15d8b7"), I18n.text("ui.b2256d93d084"), screen);
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
                return new GraphicsRuntimeInfo(name, I18n.text("ui.52c104a38498"),
                        field(info, "deviceDescription"), field(info, "driverName") + " — " + version.invoke(info),
                        (hasOwner ? I18n.text("ui.8c34b4c0d731") : I18n.text("ui.56f1612a7719"))
                                + " (Prism #" + selected + ", " + field(info, "deviceName") + ").", screen);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
                return new GraphicsRuntimeInfo(name, I18n.text("ui.52c104a38498"), I18n.text("ui.230be04124ca"), I18n.text("ui.d25d2f15d8b7"),
                        I18n.text("ui.d1675a4d3f15"), screen);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            return unavailable(I18n.text("ui.931294e959f7"), screen);
        }
    }

    /** Public JavaFX screens describe the window, not the GPU ordinal or Windows DISPLAY numbering. */
    private static String describeWindowScreen(Window owner) {
        if (owner == null) return I18n.text("ui.288a278e744a");
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
        if (best == null) return I18n.text("ui.e9664e8ac678");
        var bounds = best.getBounds();
        return screenLabel(best.equals(Screen.getPrimary()), bounds.getWidth(), bounds.getHeight(),
                best.getOutputScaleX(), best.getOutputScaleY())
                + (intersecting > 1 ? I18n.text("ui.d2ec383beb0d") : "");
    }

    static String screenLabel(boolean primary, double width, double height, double scaleX, double scaleY) {
        String scale = Math.round(scaleX * 100) + "%";
        if (scaleX != scaleY) scale += " × " + Math.round(scaleY * 100) + "%";
        return (primary ? I18n.text("ui.437c9026b338") : I18n.text("ui.c2db680e6f4a")) + " — "
                + Math.round(width * scaleX) + " × " + Math.round(height * scaleY)
                + I18n.text("ui.e39f20a01368") + scale;
    }

    String friendlySummary() {
        String graphics;
        switch (pipeline) {
            case "com.sun.prism.d3d.D3DPipeline" -> graphics = I18n.text("ui.b379f832c90b") + adapter + ".\n";
            case "com.sun.prism.sw.SWPipeline" -> graphics = I18n.text("ui.9de2f1e6c7b6");
            default -> graphics = I18n.text("ui.5e1353f9d3b5");
        }
        return graphics + I18n.text("ui.35cf54d7567f") + screen + ".\n";
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
        Object value = field.get(info); return value == null ? I18n.text("ui.d25d2f15d8b7") : value.toString();
    }

    String summary() {
        return I18n.text("ui.adf10535bd83") + mode + I18n.text("ui.c53dd1ad5390") + pipeline
                + I18n.text("ui.310a44731b01") + adapter + "\nDriver: " + driver + "\n" + scope
                + I18n.text("ui.908600ee4f5d") + screen
                + I18n.text("ui.cdbf252c9f87") + System.getProperty("prism.order", I18n.text("ui.3a05c8d8ed67")) + "\n";
    }
}
