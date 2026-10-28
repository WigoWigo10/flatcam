package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class UiFontsUiTest {
    @Test void fontsReachRealControlsCodeHudAndRulersAcrossThemes() throws Exception {
        Path root=Path.of("target/font-review").toAbsolutePath();Files.createDirectories(root);
        for(String mode:new String[]{"default","large","maximum","hidden"}) {
            Path output=root.resolve(mode);Files.createDirectories(output);Path log=output.resolve("probe.log");
            Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "--enable-native-access=ALL-UNNAMED","-XX:+UnlockDiagnosticVMOptions","-XX:DisableIntrinsic=_indexOfUL",
                    "-Djava.util.prefs.PreferencesFactory="+StartupSplashTest.MemoryFactory.class.getName(),
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    UiFontsProbe.class.getName(),mode,output.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(50,TimeUnit.SECONDS),"font probe timed out: "+mode);
                assertEquals(0,child.exitValue(),()->{try{return Files.readString(log);}catch(Exception e){return e.toString();}});
                String text=Files.readString(log);
                assertFalse(text.contains("Could not resolve") || text.contains("Error parsing CSS"),text);
            } finally {if(child.isAlive()){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}}
        }
    }
}
