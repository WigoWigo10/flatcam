package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;
import java.util.prefs.Preferences;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class RecentPrintUiTest {
    @org.junit.jupiter.api.io.TempDir Path directory;

    private static Object field(MainWindow window,String name) throws Exception {
        var field=MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field.get(window);
    }
    private static Object invoke(MainWindow window,String name,Class<?>[] types,Object... args) throws Exception {
        var method=MainWindow.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(window,args);
    }
    private static void awaitIdle(MainWindow window) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (TerminalPanelTest.fx(() -> field(window,"runningJob"))!=null) {
            if (System.nanoTime()>deadline) fail("recent import did not finish"); Thread.sleep(20);
        }
    }

    @Test void actualMenuIsEnabledAndRecentImportsPublishOnlySuccessfulFiles() throws Exception {
        var preferences=Preferences.userRoot().node("flatcam-fx-tests/flows-"+UUID.randomUUID());
        var history=new RecentFiles(preferences); var jobs=new org.flatcam.app.job.JobExecutor(1);
        MainWindow window=TerminalPanelTest.fx(() -> { var w=new MainWindow(jobs,history); w.createScene(); return w; });
        try {
            Path gerber=directory.resolve("board.gbr");
            Files.writeString(gerber,"%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,1.0*%\nD10*\nX0Y0D03*\nM02*\n");
            TerminalPanelTest.fx(() -> {
                var row=(javafx.scene.Node)invoke(window,"buildMenuRow",new Class<?>[]{});
                var bar=(MenuBar)row.lookup(".menu-bar"); assertNotNull(bar);
                var file=bar.getMenus().getFirst();
                assertTrue(file.getItems().stream().anyMatch(item -> "recent-files-menu".equals(item.getId()) && !item.isDisable()));
                var print=file.getItems().stream().filter(item -> "print-pdf-menu-item".equals(item.getId())).findFirst().orElseThrow();
                assertFalse(print.isDisable()); assertNotNull(print.getOnAction()); assertNotNull(print.getAccelerator());
                invoke(window,"openRecent",new Class<?>[]{RecentFiles.Entry.class},new RecentFiles.Entry(RecentFiles.Kind.GERBER,gerber)); return null;
            });
            awaitIdle(window); assertEquals(1,history.entries(false).size());
            assertEquals(gerber.toAbsolutePath(),history.entries(false).getFirst().path());
            TerminalPanelTest.fx(() -> {
                invoke(window,"openRecent",new Class<?>[]{RecentFiles.Entry.class},new RecentFiles.Entry(RecentFiles.Kind.GERBER,directory.resolve("missing.gbr"))); return null;
            });
            awaitIdle(window); assertEquals(1,history.entries(false).size());
            TerminalPanelTest.fx(() -> {
                assertEquals(1,((java.util.Map<?,?>)field(window,"gerberByItem")).size());
                invoke(window,"openRecent",new Class<?>[]{RecentFiles.Entry.class},new RecentFiles.Entry(RecentFiles.Kind.PROJECT,directory.resolve("missing.FlatPrj"))); return null;
            });
            awaitIdle(window);
            TerminalPanelTest.fx(() -> { assertEquals(1,((java.util.Map<?,?>)field(window,"gerberByItem")).size()); return null; });
            assertTrue(history.entries(true).isEmpty());
        } finally {
            TerminalPanelTest.fx(() -> { ((FluidTooltips)((javafx.scene.Scene)field(window,"scene")).getProperties().get("fx.tip.handler")).closeNow(); window.disposeViewport(); return null; });
            jobs.shutdown(); preferences.removeNode(); preferences.flush();
        }
    }

    @Test void printSnapshotIncludesSelectedHiddenObjectsButNotOtherLayers() throws Exception {
        var preferences=Preferences.userRoot().node("flatcam-fx-tests/selection-"+UUID.randomUUID());
        var jobs=new org.flatcam.app.job.JobExecutor(1);
        TerminalPanelTest.fx(() -> {
            var window=new MainWindow(jobs,new RecentFiles(preferences)); window.createScene();
            try {
                var geometry=new org.locationtech.jts.geom.GeometryFactory().toGeometry(new org.locationtech.jts.geom.Envelope(0,10,0,10));
                @SuppressWarnings("unchecked") var item=(TreeItem<String>)invoke(window,"addGeometryToProject",
                        new Class<?>[]{String.class,String.class,String.class,org.locationtech.jts.geom.Geometry.class,boolean.class},"one","source","IN",geometry,false);
                invoke(window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,org.locationtech.jts.geom.Geometry.class,boolean.class},"two","source","MM",geometry,false);
                var plot=(PlotAreaView)field(window,"plotAreaView"); plot.setLayerVisible(item,false);
                @SuppressWarnings("unchecked") var tree=(TreeView<String>)field(window,"projectTree"); tree.getSelectionModel().select(item);
                Object selection=invoke(window,"capturePrintSelection",new Class<?>[]{});
                var accessor=selection.getClass().getDeclaredMethod("layers"); accessor.setAccessible(true);
                @SuppressWarnings("unchecked") var layers=(java.util.List<org.flatcam.cam.pdf.PrintPdfExporter.Layer>)accessor.invoke(selection);
                assertEquals(1,layers.size()); assertEquals("IN",layers.getFirst().units()); assertSame(geometry,layers.getFirst().geometry());
                assertTrue(layers.getFirst().filled()); assertFalse(plot.isLayerVisible(item)); return null;
            } finally { ((FluidTooltips)((javafx.scene.Scene)field(window,"scene")).getProperties().get("fx.tip.handler")).closeNow(); window.disposeViewport(); }
        });
        jobs.shutdown(); preferences.removeNode(); preferences.flush();
    }
    @Test void recentMenuHasTypesFullPathHelpCallbacksAndIndependentClear() throws Exception {
        var preferences = Preferences.userRoot().node("flatcam-fx-tests/menu-"+UUID.randomUUID());
        try {
            var history = new RecentFiles(preferences);
            history.remember(RecentFiles.Kind.GERBER,Path.of("board.gbr"));
            history.remember(RecentFiles.Kind.PROJECT,Path.of("board.FlatPrj"));
            TerminalPanelTest.fx(() -> {
                var picked = new java.util.ArrayList<RecentFiles.Entry>();
                Menu root = RecentFilesMenu.build(history,picked::add,(file,size) -> new Label(file));
                Menu projects = (Menu)root.getItems().getFirst(), files=(Menu)root.getItems().get(1);
                assertFalse(root.isDisable()); assertEquals("Projetos recentes",projects.getText());
                MenuItem item = files.getItems().getFirst(); assertTrue(item.getText().startsWith("Gerber — "));
                assertTrue(item.getProperties().get(FluidTooltips.TEXT_KEY).toString().contains(Path.of("board.gbr").toAbsolutePath().toString()));
                item.fire(); assertEquals(history.entries(false),picked);
                files.getItems().getLast().fire(); assertTrue(history.entries(false).isEmpty()); assertEquals(1,history.entries(true).size());
                assertTrue(files.getItems().getFirst().isDisable()); return null;
            });
        } finally { preferences.removeNode(); preferences.flush(); }
    }
    @Test void printDialogOffersRealScalePaperOrientationAndHelpWithoutShowing() throws Exception {
        TerminalPanelTest.fx(() -> {
            var owner = new javafx.stage.Stage();
            owner.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox()));
            ThemeOption.ICE_DARK.applyTo(owner.getScene());
            var dialog = new PrintPdfDialog(owner,2);
            try {
                assertEquals(owner.getScene().getStylesheets(),dialog.getDialogPane().getScene().getStylesheets());
                @SuppressWarnings("unchecked") var paper=(ComboBox<String>)dialog.getDialogPane().lookup("#print-pdf-paper");
                var landscape=(CheckBox)dialog.getDialogPane().lookup("#print-pdf-landscape");
                var mono=(CheckBox)dialog.getDialogPane().lookup("#print-pdf-monochrome");
                assertEquals("A4",paper.getValue()); assertTrue(mono.isSelected());
                assertNotNull(paper.getAccessibleHelp()); assertTrue(dialog.getHeaderText().contains("1:1"));
                landscape.setSelected(true);
                var print=dialog.getDialogPane().getButtonTypes().getFirst();
                assertFalse(dialog.getResultConverter().call(print).portrait());
                paper.setValue("Bounds"); assertTrue(landscape.isDisabled());
                assertNull(dialog.getResultConverter().call(ButtonType.CANCEL)); return null;
            } finally { dialog.close(); owner.close(); }
        });
    }
}
