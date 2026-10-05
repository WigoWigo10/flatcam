package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AboutInfoTest {
    @Test void packagedLicenseIsTheActualRepositoryLicenseIncludingCopyright() throws Exception {
        assertEquals(Files.readString(Path.of("..", "..", "LICENSE")).replace("\r\n", "\n"),
                AboutInfo.resource("LICENSE").replace("\r\n", "\n"));
        assertTrue(AboutInfo.resource("LICENSE").contains("Copyright (c) 2014-2019 Juan Pablo Caram"));
    }
    @Test void everyLegacyProgrammerIsPreservedInSourceOrder() throws Exception {
        String source = Files.readString(Path.of("..", "..", "app_Main.py"));
        var matcher = Pattern.compile("self\\.prog_grid_lay\\.addWidget\\(QtWidgets\\.QLabel\\('%s' % \"([^\"]+)\"\\), \\d+, 0\\)").matcher(source);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        while (matcher.find()) names.add(matcher.group(1));
        assertEquals(33, names.size());
        assertEquals(names, AboutInfo.credits("programmers.tsv", 3).stream().map(List::getFirst).toList());
    }
    @Test void translatorsCorrectionsAndContactsArePreserved() throws Exception {
        var rows = AboutInfo.credits("translators.tsv", 4); assertEquals(8, rows.size());
        String source = Files.readString(Path.of("..", "..", "app_Main.py"));
        for (var row : rows) {
            assertTrue(source.contains('"' + row.get(1) + '"'));
            for (String value : row.subList(2, 4)) if (!value.isEmpty()) assertTrue(source.contains(value));
        }
        assertEquals("Carlos Stein", rows.getFirst().get(1));
        assertEquals("Olivier Cornet", rows.get(1).get(2));
    }
    @Test void buildAndTechnicalDetailsAreRealAndDoNotClaimParity() {
        var build = AboutInfo.build();
        assertFalse(build.getProperty("appVersion").contains("${"));
        assertFalse(build.getProperty("buildTimeUtc").contains("${"));
        String summary = AboutInfo.technicalSummary();
        assertTrue(summary.contains(System.getProperty("java.runtime.version")));
        assertTrue(summary.contains(System.getProperty("os.arch")));
        assertTrue(summary.contains("não representa paridade completa"));
    }
}
