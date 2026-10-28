package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Restart-only catalogs are exercised in fresh JVMs, never mixed into the shared test toolkit. */
@EnabledOnOs(OS.WINDOWS)
class I18nUiTest {
    @Test void bothLanguagesKeepTheirHelpActionsAndMachineOutput() throws Exception {
        Path output = Path.of("target/i18n-review").toAbsolutePath(); Files.createDirectories(output);
        for (UiLanguage language : UiLanguage.values()) {
            Path log = output.resolve(language.tag + ".log");
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-XX:+UnlockDiagnosticVMOptions", "-XX:DisableIntrinsic=_indexOfUL",
                    "-Djava.util.prefs.PreferencesFactory=" + StartupSplashTest.MemoryFactory.class.getName(),
                    "-cp", System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    I18nUiProbe.class.getName(), language.tag, output.toString())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(55,TimeUnit.SECONDS), "UI language probe timed out: " + log);
                assertEquals(0,child.exitValue(), () -> { try { return Files.readString(log); } catch(Exception e) { return e.toString(); } });
            } finally { if(child.isAlive()) { child.destroyForcibly(); child.waitFor(5,TimeUnit.SECONDS); } }
        }
        assertArrayEquals(Files.readAllBytes(output.resolve("pt-BR.nc")),Files.readAllBytes(output.resolve("en.nc")), "UI language must not rewrite machine code");
        assertArrayEquals(Files.readAllBytes(output.resolve("pt-BR.csv")),Files.readAllBytes(output.resolve("en.csv")), "CSV schema and numbers stay stable");
    }
}
