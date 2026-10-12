package org.flatcam.fx;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectChangeTrackerTest {
    @Test void additionVersionNameStyleSettingsAndRemovalAreTrackedWithoutGeometrySerialization() {
        var tracker=new ProjectChangeTracker(); Object key=new Object(),version=new Object();
        var clean=new ProjectChangeTracker.State(1,List.of(new ProjectChangeTracker.Item(key,"one",version,"visible")),List.of("settings"));
        tracker.saved(clean); assertFalse(tracker.changed(clean));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(1,List.of(new ProjectChangeTracker.Item(key,"renamed",version,"visible")),clean.settings())));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(1,List.of(new ProjectChangeTracker.Item(key,"one",new Object(),"visible")),clean.settings())));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(1,List.of(new ProjectChangeTracker.Item(key,"one",version,"hidden")),clean.settings())));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(1,clean.items(),List.of("changed"))));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(1,List.of(),clean.settings())));
        assertTrue(tracker.changed(new ProjectChangeTracker.State(2,clean.items(),clean.settings())));
    }
    @Test void saveOfAnEarlierSnapshotDoesNotClearLaterChangesAndUndoToTheSavedVersionIsClean() {
        var tracker=new ProjectChangeTracker(); Object key=new Object(),version=new Object();
        var saved=new ProjectChangeTracker.State(0,List.of(new ProjectChangeTracker.Item(key,"one",version,0)),List.of());
        var edited=new ProjectChangeTracker.State(0,List.of(new ProjectChangeTracker.Item(key,"one",new Object(),0)),List.of());
        tracker.saved(saved); assertTrue(tracker.changed(edited)); assertFalse(tracker.changed(saved));
        tracker.saved(edited); assertFalse(tracker.changed(edited));
    }
}
