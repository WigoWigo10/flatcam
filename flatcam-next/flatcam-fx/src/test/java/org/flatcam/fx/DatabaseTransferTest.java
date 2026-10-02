package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.flatcam.app.project.LegacyToolsDatabase;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class DatabaseTransferTest {
    @Test void geometryTransferIsExplicitAndCarriesVTip() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0), new Coordinate(1,1)});
            var db = new LegacyToolsDatabase.MillingTool("V", 0.3, ToolProfile.V,
                    new GeometryGCodeParameters(2, 0.2, true, 0.05, 180, 9000, false), new VTipSettings(0.1, 30));
            var result = new java.util.concurrent.atomic.AtomicReference<GeometryCncToolPanel.Result>();
            var root = GeometryCncToolPanel.build("MM", path, List.of(), null, null, () -> List.of(db), result::set, () -> {});
            ((Button) root.lookup("#cnc-db-load")).fire();
            assertNotEquals("180.0", ((TextField) root.lookup("#cnc-feed")).getText());
            ((Button) root.lookup("#cnc-db-apply")).fire();
            assertEquals("180.0", ((TextField) root.lookup("#cnc-feed")).getText());
            ((Button) root.lookup("#cnc-generate")).fire();
            assertNotNull(result.get(), ((Label) root.lookup("#cnc-error")).getText());
            assertEquals(ToolProfile.V, result.get().tools().getFirst().toolProfile());
            assertEquals(db.tip(), result.get().vTools().get(0));
            return null;
        });
        Platform.runLater(task); task.get(20, TimeUnit.SECONDS);
    }
}
