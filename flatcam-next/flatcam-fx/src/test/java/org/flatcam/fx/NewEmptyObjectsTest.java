package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.app.project.ProjectFileIO;
import org.flatcam.app.project.PythonProjectIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** File > New > Geometry / Excellon (Python new_geometry_object / new_excellon_object). */
@EnabledOnOs(OS.WINDOWS)
class NewEmptyObjectsTest {
    @TempDir Path directory;

    private static Object field(MainWindow window, String name) throws Exception {
        Field f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f.get(window);
    }

    @Test void emptyObjectsAreCreatedSelectedEditableAndSurviveBothProjectFormats() throws Exception {
        Preferences prefs = Preferences.userRoot().node("flatcam-fx-tests/new-objects-" + UUID.randomUUID());
        JobExecutor jobs = new JobExecutor(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            MainWindow window = TerminalPanelTest.fx(() -> { var w = new MainWindow(jobs, new RecentFiles(prefs)); w.createScene(); return w; });
            TerminalPanelTest.fx(() -> {
                TreeItem<String> geo = window.newEmptyGeometryObject();
                TreeItem<String> geo2 = window.newEmptyGeometryObject();
                TreeItem<String> exc = window.newEmptyExcellonObject();
                assertEquals("new_geo", geo.getValue());
                assertEquals("new_geo_2", geo2.getValue());
                assertEquals("new_exc", exc.getValue());
                @SuppressWarnings("unchecked") TreeView<String> tree = (TreeView<String>) field(window, "projectTree");
                assertEquals(List.of(exc), List.copyOf(tree.getSelectionModel().getSelectedItems()), "the new object is selected");
                assertEquals(TclFlatcamHost.Kind.EXCELLON, window.find("new_exc").orElseThrow().kind());

                Method edit = MainWindow.class.getDeclaredMethod("editSelectedObject");
                edit.setAccessible(true);
                edit.invoke(window);
                ExcellonEditorController excellonEditor = (ExcellonEditorController) field(window, "excellonEditor");
                assertTrue(excellonEditor.isActive(), "an empty Excellon can be opened in the editor");
                excellonEditor.cancel();
                tree.getSelectionModel().clearAndSelect(tree.getRow(geo));
                edit.invoke(window);
                GeometryEditorController geometryEditor = (GeometryEditorController) field(window, "geometryEditor");
                assertTrue(geometryEditor.isActive(), "an empty Geometry can be opened in the editor");
                geometryEditor.cancel();
                return null;
            });
            for (String file : List.of("empty.fcnproj", "empty.FlatPrj")) {
                Path path = directory.resolve(file);
                worker.submit(() -> { window.saveProject(path); return null; }).get(20, TimeUnit.SECONDS);
                ProjectFile loaded = file.endsWith(".fcnproj") ? ProjectFileIO.load(path) : PythonProjectIO.load(path);
                assertEquals(List.of("new_geo", "new_geo_2"), loaded.geometries().stream().map(ProjectFile.GeometryEntry::name).toList(), file);
                assertEquals(List.of("new_exc"), loaded.excellons().stream().map(ProjectFile.ExcellonEntry::name).toList(), file);
            }
            TerminalPanelTest.fx(() -> { window.disposeViewport(); return null; });
        } finally {
            worker.shutdownNow(); jobs.shutdown(); prefs.removeNode(); prefs.flush();
        }
    }
}
