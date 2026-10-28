package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.excellon.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Completion must preserve the same editable draft, not recreate a panel with default values. */
@EnabledOnOs(OS.WINDOWS)
class MainToolPersistenceTest {
    @TempDir Path directory;
    static Object field(MainWindow window, String name) throws Exception {
        var f = MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f.get(window);
    }
    static Object call(MainWindow window, String name, Class<?>[] types, Object... values) throws Exception {
        var m = MainWindow.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(window, values);
    }
    static ScrollPane panel(MainCamFlowTest.Session s) throws Exception {
        return (ScrollPane) ((Tab) field(s.window, "toolTab")).getContent();
    }
    static void assertPanel(MainCamFlowTest.Session s, Node content) throws Exception {
        TerminalPanelTest.fx(() -> {
            var tool = (Tab) field(s.window, "toolTab");
            assertSame(content, tool.getContent());
            assertSame(tool, ((TabPane) field(s.window, "leftTabs")).getSelectionModel().getSelectedItem());
            return null;
        });
    }
    static void finish(MainCamFlowTest.Session s, JobHandle<?> job) throws Exception {
        assertNotNull(job); s.release.countDown(); job.completion().get(10, TimeUnit.SECONDS); s.awaitUi();
    }
    static JobHandle<?> job(MainCamFlowTest.Session s) throws Exception {
        return (JobHandle<?>) field(s.window, "runningJob");
    }

    @Test void nccSeedCanBeChangedToStandardAfterGenerationWithoutReconfiguringTheForm() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var content = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var scroll = panel(s); var form = scroll.getContent();
                ((TableView<?>) form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().setText("1.7");
                ((Spinner<?>) form.lookup("#ncc-overlap")).getEditor().setText("37.5");
                ((CheckBox) form.lookup("#ncc-connect")).setSelected(false);
                ((ComboBox<NccMethod>) form.lookup("#ncc-method")).setValue(NccMethod.SEED);
                return scroll;
            });
            var form = TerminalPanelTest.fx(content::getContent);
            Object selectedTool = TerminalPanelTest.fx(() ->
                    ((TableView<?>) form.lookup("#ncc-tools")).getSelectionModel().getSelectedItem());
            for (var method : List.of(NccMethod.SEED, NccMethod.STANDARD)) {
                var handle = TerminalPanelTest.fx(() -> {
                    ((ComboBox<NccMethod>) form.lookup("#ncc-method")).setValue(method);
                    ((Button) form.lookup("#ncc-generate")).fire(); return job(s);
                });
                finish(s, handle); assertPanel(s, content);
                TerminalPanelTest.fx(() -> {
                    var tabs = (TabPane) field(s.window, "leftTabs");
                    tabs.getSelectionModel().select((Tab) field(s.window, "propertiesTab"));
                    tabs.getSelectionModel().select((Tab) field(s.window, "toolTab"));
                    assertSame(form, panel(s).getContent());
                    assertEquals(method, ((ComboBox<?>) form.lookup("#ncc-method")).getValue());
                    assertEquals("1.7", ((Spinner<?>) form.lookup("#ncc-margin")).getEditor().getText());
                    assertEquals("37.5", ((Spinner<?>) form.lookup("#ncc-overlap")).getEditor().getText());
                    assertFalse(((CheckBox) form.lookup("#ncc-connect")).isSelected());
                    assertSame(selectedTool, ((TableView<?>) form.lookup("#ncc-tools")).getSelectionModel().getSelectedItem());
                    assertSame(s.item, ((NccToolPanel.SourceCandidate) ((ComboBox<?>) form.lookup("#ncc-source-object")).getValue()).item());
                    var project = (org.flatcam.app.project.ProjectFile) call(s.window, "snapshotProject", new Class<?>[]{});
                    var output = project.geometries().getLast();
                    var expected = NccGenerator.generate("MM", s.image.solidGeometry(),
                            new NccParameters(1, .375, 1.7, method, false, true, 0),
                            org.flatcam.cam.CancellationToken.none(), org.flatcam.cam.ProgressCallback.none());
                    assertFalse(output.geometry().isEmpty());
                    assertTrue(expected.geometry().equalsExact(output.geometry(), 1e-8), "Output must use the edited method");
                    return null;
                });
            }
            TerminalPanelTest.fx(() -> { assertEquals(3, s.geometries().size()); return null; });
        }
    }

    @ParameterizedTest @ValueSource(strings={"Isolation", "NCC", "Cutout"})
    void camSuccessKeepsRealFormAndCanGenerateAgain(String tool) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var content = TerminalPanelTest.fx(() -> {
                call(s.window, "generate" + (tool.equals("NCC") ? "Ncc" : tool),
                        new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                return panel(s);
            });
            var draft = TerminalPanelTest.fx(() -> content.getContent());
            for (int run = 0; run < 2; run++) {
                finish(s, s.start(tool, false));
                assertPanel(s, content);
                TerminalPanelTest.fx(() -> { assertSame(draft, panel(s).getContent()); return null; });
            }
            TerminalPanelTest.fx(() -> { assertEquals(3, s.geometries().size()); return null; });
            TerminalPanelTest.fx(() -> {
                call(s.window, "closeToolPanel", new Class<?>[]{});
                assertNotSame(content, ((Tab) field(s.window, "toolTab")).getContent()); return null;
            });
        }
    }

    @Test void panelizeCreateButtonKeepsColumnsAndOriginalSourceForNextRun() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var content = TerminalPanelTest.fx(() -> {
                ((TreeView<String>) field(s.window, "projectTree")).getSelectionModel().select(s.item);
                call(s.window, "openPanelizeTool", new Class<?>[]{});
                var scroll = panel(s);
                ((TextField) scroll.getContent().lookup("#panelize-columns")).setText("2");
                ((TextField) scroll.getContent().lookup("#panelize-rows")).setText("2");
                return scroll;
            });
            for (int run = 0; run < 2; run++) {
                var handle = TerminalPanelTest.fx(() -> {
                    ((Button) content.getContent().lookup("#panelize-create")).fire(); return job(s);
                });
                finish(s, handle); assertPanel(s, content);
                TerminalPanelTest.fx(() -> {
                    assertEquals("2", ((TextField) content.getContent().lookup("#panelize-columns")).getText());
                    return null;
                });
            }
            TerminalPanelTest.fx(() -> {
                var images = (Map<TreeItem<String>, GerberImage>) field(s.window, "gerberByItem");
                assertEquals(3, images.size());
                assertEquals(2, images.entrySet().stream().filter(e -> e.getKey() != s.item)
                        .filter(e -> e.getValue().solidGeometry().getArea() == s.image.solidGeometry().getArea() * 4).count());
                ((Button) content.getContent().lookup("#panelize-close")).fire(); return null;
            });
        }
    }

    @ParameterizedTest @ValueSource(booleans={false, true})
    void paintKeepsFormAndDoesNotCloseAnotherToolOnLateCompletion(boolean replaced) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var content = TerminalPanelTest.fx(() -> {
                call(s.window, "openPaintTool", new Class<?>[]{}); return panel(s);
            });
            for (int run = 0; run < (replaced ? 1 : 2); run++) {
                var handle = TerminalPanelTest.fx(() -> {
                    call(s.window, "runPaintGeneration", new Class<?>[]{TreeItem.class, String.class,
                            org.locationtech.jts.geom.Geometry.class, PaintParameters.class}, s.item, "MM",
                            s.image.solidGeometry(), new PaintParameters(List.of(.5), .2, 0, NccMethod.STANDARD,
                                    true, true, NccOrder.FORWARD, false));
                    return job(s);
                });
                Node expected = replaced ? TerminalPanelTest.fx(() -> {
                    call(s.window, "openToolPanel", new Class<?>[]{String.class, Node.class}, "Other tool", new Label("draft"));
                    return ((Tab) field(s.window, "toolTab")).getContent();
                }) : content;
                finish(s, handle); assertPanel(s, expected);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"Isolation", "NCC", "Cutout"})
    void cancellationKeepsFormAvailableForRetry(String tool) throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var content = TerminalPanelTest.fx(() -> {
                call(s.window, "generate" + (tool.equals("NCC") ? "Ncc" : tool),
                        new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image); return panel(s);
            });
            var handle = s.start(tool, false); handle.cancel(); s.release.countDown();
            assertThrows(java.util.concurrent.CancellationException.class, () -> handle.completion().get(10, TimeUnit.SECONDS));
            s.awaitUi(); assertPanel(s, content);
            finish(s, s.start(tool, false)); assertPanel(s, content);
        }
    }

    @Test void cncKeepsFormAndAdvancesOnlyItsOwnSuccessfulSourceForRepeatGeneration() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var expected = new AtomicReference<Object>();
            var content = TerminalPanelTest.fx(() -> {
                var entry = s.geometries().get(s.reference); expected.set(entry);
                call(s.window, "generateGeometryCncJob", new Class<?>[]{TreeItem.class, entry.getClass()}, s.reference, entry);
                return panel(s);
            });
            var parameters = new GeometryGCodeParameters(2, .1, false, .1, 100, 10000, false);
            var result = new GeometryCncToolPanel.Result(List.of(new ToolGeometry(.5, s.referenceGeometry.getBoundary())),
                    parameters, Map.of(), GCodePreprocessor.GRBL_11_NO_M6, Map.of());
            Object original = expected.get();
            for (int run = 0; run < 2; run++) {
                Path output = directory.resolve("cnc-" + run + ".nc");
                var handle = TerminalPanelTest.fx(() -> {
                    call(s.window, "startGeometryCncGeneration", new Class<?>[]{TreeItem.class, expected.get().getClass(),
                            GeometryCncToolPanel.Result.class, Path.class, Consumer.class}, s.reference, expected.get(), result,
                            output, (Consumer<Object>) expected::set); return job(s);
                });
                finish(s, handle); assertPanel(s, content); assertTrue(Files.size(output) > 0);
                TerminalPanelTest.fx(() -> { assertSame(s.geometries().get(s.reference), expected.get()); return null; });
            }
            assertNotSame(original, expected.get());
            TerminalPanelTest.fx(() -> {
                assertThrows(java.lang.reflect.InvocationTargetException.class, () -> call(s.window, "startGeometryCncGeneration",
                        new Class<?>[]{TreeItem.class, original.getClass(), GeometryCncToolPanel.Result.class, Path.class},
                        s.reference, original, result, directory.resolve("stale.nc")));
                return null;
            });
            assertFalse(Files.exists(directory.resolve("stale.nc")));
        }
    }

    @Test void drillingKeepsRealFormAndCanExportAgainAfterSavingDefaults() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y2.0", "M30"));
            var source = TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addExcellonToProject",
                    new Class<?>[]{String.class, Path.class, ExcellonImage.class}, "holes", null, image));
            var content = TerminalPanelTest.fx(() -> {
                call(s.window, "generateDrillGCode", new Class<?>[]{TreeItem.class, ExcellonImage.class}, source, image);
                return panel(s);
            });
            var result = new DrillGCodeToolPanel.Result(new DrillGCodeToolPanel.SourceCandidate(source, image, Map.of()),
                    Map.of(1, new DrillGCodeParameters(2, 1, 100, 10000, false)), List.of(1),
                    new GCodeGenerator.DrillJobOptions(false, 15, 1, 0.0, 0.0),
                    GCodePreprocessor.GRBL_11_NO_M6, org.flatcam.app.project.DrillCncSettings.ToolOrder.NO);
            for (int run = 0; run < 2; run++) {
                Path output = directory.resolve("drill-" + run + ".nc");
                var handle = TerminalPanelTest.fx(() -> {
                    call(s.window, "startDrillGCodeGeneration", new Class<?>[]{TreeItem.class, ExcellonImage.class,
                            DrillGCodeToolPanel.Result.class, Path.class}, source, image, result, output); return job(s);
                });
                finish(s, handle); assertPanel(s, content); assertTrue(Files.size(output) > 0);
            }
        }
    }
}
