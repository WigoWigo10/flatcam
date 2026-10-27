package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.awt.GraphicsEnvironment;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class NativeSplashTransitionTest {
    @Test void jniLauncherKeepsPhysicalSplashBoundsStableAcrossGlassStartup() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize(), output=root.resolve("target/native-splash-transition");
        Path compiler=Path.of("C:/msys64/ucrt64/bin/g++.exe");
        assumeTrue(Files.isRegularFile(compiler),"native transition requires the Windows build toolchain");
        Files.createDirectories(output); Files.createDirectories(root.resolve("target/native"));
        String cpp=Files.readString(root.resolve("native-launcher/FlatCAMFX.cpp"));
        // Compile the production JNI host with only the Java entrypoint/classpath adapted for this fixture.
        // Resolve test runtime jars from Maven so this also works before build-native has copied dependencies.
        var classpath=new ArrayList<String>(); var modules=new ArrayList<String>();
        for(String path:System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            String name=Path.of(path).getFileName().toString();
            if(name.startsWith("javafx-") && name.endsWith("-win.jar")) modules.add(path);
            else if(!name.startsWith("javafx-")) classpath.add(path);
        }
        assertTrue(modules.size()>=3,"Windows JavaFX runtime modules missing from test classpath");
        cpp=replaceOnce(cpp,"const RuntimePaths paths = runtimePaths(root);",
                "const RuntimePaths paths = {"+wideLiteral(String.join(";",classpath))+","+wideLiteral(String.join(";",modules))+"};");
        cpp=replaceOnce(cpp,"env->FindClass(\"org/flatcam/fx/FlatCamLauncher\")","env->FindClass(\"org/flatcam/fx/StartupSplashNativeTransitionProbe\")");
        String observer;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/native-splash-observer.cpp.inc"))) {
            observer=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        cpp=replaceOnce(cpp,"int wmain(int argc, wchar_t** argv) {",observer+"\nint wmain(int argc, wchar_t** argv) {");
        cpp=replaceOnce(cpp,"jclass applicationClass = env->FindClass", "SplashObserver observer; std::thread watcher([&] { observer.watch(); });\n    jclass applicationClass = env->FindClass");
        cpp=replaceOnce(cpp,"vm->DestroyJavaVM();", "observer.stop=true; watcher.join();\n    if(observer.initial.empty() || observer.changed) failed=true;\n    else std::cout << \"PASS native physical bounds stable\" << std::endl;\n    vm->DestroyJavaVM();");
        // A fresh host root deliberately has no copied runtime/dependencies of its own.
        Path host=output.resolve("isolated-root/target/native"); Files.createDirectories(host);
        Path source=output.resolve("launcher.cpp"), executable=host.resolve("FlatCAMFXSplashTransition.exe");
        Files.writeString(source,cpp);
        var command=new ArrayList<>(List.of(compiler.toString(),"-std=c++17","-O2","-Wall","-Wextra","-municode","-static",
                "-I"+Path.of(System.getProperty("java.home"),"include"),"-I"+Path.of(System.getProperty("java.home"),"include/win32"),source.toString()));
        Path manifest=root.resolve("native-launcher/FlatCAMFX.manifest");
        if(Files.isRegularFile(manifest) && !Boolean.getBoolean("flatcam.test.omitDpiManifest")) {
            Path resource=output.resolve("dpi-resource.o");
            run(List.of(compiler.resolveSibling("windres.exe").toString(),"-I",root.resolve("native-launcher").toString(),"-i",root.resolve("native-launcher/FlatCAMFX.rc").toString(),"-o",resource.toString()),root,output.resolve("resource.log"),Map.of());
            command.add(resource.toString());
        }
        command.addAll(List.of("-o",executable.toString()));
        run(command,root,output.resolve("compile.log"),Map.of());
        int screens=GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
        for(boolean software:new boolean[]{false,true}) for(int index=0;index<screens;index++) {
            String tag=(software ? "software" : "gpu")+"-screen-"+index;
            Path signal=output.resolve(tag+"-"+UUID.randomUUID()+".signal");
            var args=new ArrayList<>(List.of(executable.toString(),"--no-diagnostics","--screen="+index));
            if(software) args.add("--software");
            run(args,root,output.resolve(tag+".log"),Map.of("FLATCAM_SPLASH_PROBE_READY",signal.toString()));
            String log=Files.readString(output.resolve(tag+".log"));
            assertTrue(log.contains("PASS native physical bounds stable"),log);
        }
    }
    private static String replaceOnce(String source,String target,String replacement) {
        assertEquals(1,source.split(java.util.regex.Pattern.quote(target),-1).length-1,"launcher fixture anchor drift: "+target);
        return source.replace(target,replacement);
    }
    private static String wideLiteral(String value) { return "L\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    private static void run(List<String> command,Path root,Path log,Map<String,String> environment) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("PATH",Path.of("C:/msys64/ucrt64/bin")+";"+builder.environment().getOrDefault("PATH",""));
        builder.environment().putAll(environment);
        builder.environment().put("FLATCAM_FX_JAVA_HOME",System.getProperty("java.home"));
        Process child=builder.start();
        try {
            assertTrue(child.waitFor(35,TimeUnit.SECONDS),"native fixture timed out: "+log);
            assertEquals(0,child.exitValue(),()-> { try { return Files.readString(log); } catch(Exception error) { return error.toString(); } });
        } finally { if(child.isAlive()) { child.destroyForcibly(); child.waitFor(5,TimeUnit.SECONDS); } }
    }
}
