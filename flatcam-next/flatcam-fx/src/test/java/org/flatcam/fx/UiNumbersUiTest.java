package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class UiNumbersUiTest {
    @Test void startupSettingsChangeReadoutsButNotImportsOrMachineOutput() throws Exception {
        Path root=Path.of("target/numeric-review").toAbsolutePath(); Files.createDirectories(root);
        for(String mode:new String[]{"default","custom"}) {
            Path directory=root.resolve(mode); Files.createDirectories(directory);
            Path log=directory.resolve("probe.log");
            Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "--enable-native-access=ALL-UNNAMED","-XX:+UnlockDiagnosticVMOptions","-XX:DisableIntrinsic=_indexOfUL",
                    "-Djava.util.prefs.PreferencesFactory="+StartupSplashTest.MemoryFactory.class.getName(),
                    "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    UiNumbersProbe.class.getName(),mode,directory.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(35,TimeUnit.SECONDS),"numeric probe timed out");
                assertEquals(0,child.exitValue(),()->{ try{return Files.readString(log);}catch(Exception e){return e.toString();} });
            } finally { if(child.isAlive()){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);} }
        }
        assertArrayEquals(Files.readAllBytes(root.resolve("default/job.nc")),Files.readAllBytes(root.resolve("custom/job.nc")));
        assertArrayEquals(Files.readAllBytes(root.resolve("default/job.csv")),Files.readAllBytes(root.resolve("custom/job.csv")));
    }
}
