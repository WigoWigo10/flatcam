package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecentFilesTest {
    final Preferences preferences = Preferences.userRoot().node("flatcam-fx-tests/recent-"+UUID.randomUUID());
    final RecentFiles history = new RecentFiles(preferences);
    @AfterEach void cleanup() throws Exception { preferences.removeNode(); preferences.flush(); }
    @Test void mostRecentFirstDeduplicatesNormalizesAndSurvivesNewInstance() {
        Path first = Path.of("board.gbr"), second = Path.of("drills.drl");
        history.remember(RecentFiles.Kind.GERBER,first);
        history.remember(RecentFiles.Kind.EXCELLON,second);
        history.remember(RecentFiles.Kind.GERBER,Path.of("sub/../board.gbr"));
        var entries = new RecentFiles(preferences).entries(false);
        assertEquals(2,entries.size()); assertEquals(first.toAbsolutePath().normalize(),entries.getFirst().path());
        assertEquals(RecentFiles.Kind.EXCELLON,entries.get(1).kind());
    }
    @Test void projectsAndFilesHaveIndependentLimitsAndClearNeverDeletesFiles() {
        for (int i=0; i<14; i++) {
            history.remember(RecentFiles.Kind.GERBER,Path.of("board"+i+".gbr"));
            history.remember(RecentFiles.Kind.PROJECT,Path.of("project"+i+".fcnproj"));
        }
        assertEquals(10,history.entries(false).size()); assertEquals(10,history.entries(true).size());
        assertEquals("board13.gbr",history.entries(false).getFirst().path().getFileName().toString());
        history.clear(false); assertTrue(history.entries(false).isEmpty()); assertEquals(10,history.entries(true).size());
    }
    @Test void importIntentIsPartOfTheIdentityAndInvalidOldEntriesAreSkipped() {
        history.remember(RecentFiles.Kind.SVG_GEOMETRY,Path.of("board.svg"));
        history.remember(RecentFiles.Kind.SVG_GERBER,Path.of("board.svg"));
        assertEquals(2,history.entries(false).size());
        preferences.put("files.2.kind","REMOVED_KIND"); preferences.put("files.2.path","bad.svg");
        preferences.put("files.3.kind","PROJECT"); preferences.put("files.3.path","wrong-list.fcnproj");
        assertEquals(2,history.entries(false).size());
    }
}
