package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class PaintDatabaseTransferTest {
    @Test void databaseUpdatesOnlyItsRowAndInvalidDraftsSurviveSelection() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var item = new TreeItem<>("board");
            var geometry = new GeometryFactory().toGeometry(new Envelope(0,20,0,20));
            var output = new AtomicReference<PaintParameters>();
            var db = new LegacyToolsDatabase.PaintTool("fine",
                    new PaintParameters(List.of(0.5),0.6,1,NccMethod.LINES,false,true,NccOrder.NONE,true));
            var root = PaintToolPanel.build(new PaintToolPanel.Host() {
                public List<TreeItem<String>> sources() { return List.of(item); }
                public TreeItem<String> initialSource() { return item; }
                public String units(TreeItem<String> ignored) { return "MM"; }
                public Geometry polygons(TreeItem<String> ignored) { return geometry; }
                public void pickPoint(Consumer<Coordinate> callback) { }
                public void cancelPick() { }
                public void selectArea(Geometry source, boolean polygon, Consumer<Geometry> callback, Runnable cancel) { }
                public void cancelAreaSelection() { }
                public void preview(Geometry shape) { }
                public void paint(TreeItem<String> source, String units, Geometry polygons, PaintParameters p) { output.set(p); }
                public List<LegacyToolsDatabase.PaintTool> databaseTools() { return List.of(db); }
            }, () -> {});
            var overlap = (TextField) root.lookup("#paint-overlap");
            var table = (TableView<?>) root.lookup("#paint-tools");
            overlap.setText("35");
            ((Button) root.lookup("#paint-db-load")).fire();
            assertEquals(1,table.getItems().size());
            ((Button) root.lookup("#paint-db-apply")).fire();
            assertEquals(List.of(0.3,0.5),table.getItems());
            assertNull(output.get());
            ((Button) root.lookup("#paint-generate")).fire();
            assertNotNull(output.get());
            assertEquals(0.35, output.get().settingsFor(0.3).overlapFraction());
            assertEquals(0.6, output.get().settingsFor(0.5).overlapFraction());
            assertEquals(NccOrder.REVERSE,output.get().order());
            assertFalse(output.get().restMachining());
            overlap.setText("invalid"); table.getSelectionModel().select(0);
            output.set(null); ((Button) root.lookup("#paint-generate")).fire(); assertNull(output.get());
            table.getSelectionModel().select(1); assertEquals("invalid",overlap.getText());
            overlap.setText("45"); ((Button) root.lookup("#paint-apply-all")).fire();
            ((Button) root.lookup("#paint-generate")).fire();
            assertEquals(0.45,output.get().settingsFor(0.3).overlapFraction());
            assertEquals(0.45,output.get().settingsFor(0.5).overlapFraction());
            return null;
        });
        Platform.runLater(task); task.get(20,TimeUnit.SECONDS);
    }
}
