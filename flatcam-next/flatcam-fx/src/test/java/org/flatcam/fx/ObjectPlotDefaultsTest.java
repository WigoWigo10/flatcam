package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.gerber.GerberImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class ObjectPlotDefaultsTest {
    @BeforeEach @AfterEach void store() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    static Field field(String name) throws Exception { var f=MainWindow.class.getDeclaredField(name); f.setAccessible(true); return f; }
    static Object invoke(MainWindow host, String name, Class<?>[] types, Object... args) throws Exception {
        var method=MainWindow.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(host,args);
    }
    static class Session implements AutoCloseable {
        final JobExecutor jobs=new JobExecutor(1);
        final MainWindow host;
        final PlotAreaView plot;
        Session() throws Exception {
            host=TerminalPanelTest.fx(()->{
                var window=new MainWindow(jobs); var root=new TreeItem<String>("root"); root.setExpanded(true);
                for(String name:new String[]{"gerbersNode","excellonNode","geometryNode","cncJobsNode"}) {
                    var category=new TreeItem<String>(name); category.setExpanded(true); field(name).set(window,category); root.getChildren().add(category);
                }
                field("projectTree").set(window,new TreeView<>(root)); field("unitsLabel").set(window,new Label("MM"));
                return window;
            });
            plot=(PlotAreaView)field("plotAreaView").get(host);
        }
        @SuppressWarnings("unchecked") TreeItem<String> item(String kind,String name) throws Exception {
            return ((Map<TreeItem<String>,?>)field(kind+"ByItem").get(host)).keySet().stream().filter(i->i.getValue().equals(name)).findFirst().orElseThrow();
        }
        TreeItem<String> geometry() throws Exception {
            return TerminalPanelTest.fx(()->{
                @SuppressWarnings("unchecked") var item=(TreeItem<String>)invoke(host,"addGeometryToProject",
                        new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},
                        "route","","MM",new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1,1),new Coordinate(3,4)}),true);
                return item;
            });
        }
        @Override public void close() throws Exception { jobs.shutdown(); TerminalPanelTest.fx(()->{host.disposeViewport();return null;}); }
    }
    static Path copper(Path directory) throws Exception {
        Path path=directory.resolve("copper.gbr"); Files.writeString(path,"%FSLAX24Y24*%\n%MOMM*%\n%ADD10R,10.0X10.0*%\nD10*\nX0Y0D03*\nM02*\n"); return path;
    }
    static Path drills(Path directory) throws Exception {
        Path path=directory.resolve("holes.drl"); Files.writeString(path,"M48\nMETRIC,TZ\nT01C0.8\n%\nT01\nX001000Y001000\nM30\n"); return path;
    }
    static void style(String kind,String stroke,boolean visible) {
        ToolDefaults.set("display."+kind+".stroke",true,stroke);
        if(!kind.equals("geometry")) {
            ToolDefaults.set("display."+kind+".fill",true,"#12345680");
            ToolDefaults.set("display."+kind+".solid",true,"false");
        }
        ToolDefaults.set("display."+kind+".multicolor",true,"true");
        ToolDefaults.set("display."+kind+".plot",true,Boolean.toString(visible));
    }

    @Test void importedAndCreatedObjectsConsumeIndependentStylesWithoutChangingExistingLayers(@TempDir Path directory) throws Exception {
        style("gerber","#AABBCC40",false); style("excellon","#778899FF",true); style("geometry","#22446688",false);
        try(var session=new Session()) {
            session.host.openGerber(copper(directory),"copper"); session.host.openExcellon(drills(directory),"holes"); var route=session.geometry();
            TerminalPanelTest.fx(()->{
                var gerber=session.item("gerber","copper"); var drill=session.item("excellon","holes");
                assertArrayEquals(new Color[]{Color.web("#12345680"),Color.web("#AABBCC40")},session.plot.layerColors(gerber));
                assertFalse(session.plot.isLayerVisible(gerber)); assertFalse(session.plot.isLayerFilled(gerber)); assertTrue(session.plot.isLayerMulticolor(gerber));
                assertTrue(session.plot.isLayerVisible(drill)); assertEquals(Color.web("#778899FF"),session.plot.layerColors(drill)[1]);
                assertFalse(session.plot.isLayerFilled(drill)); assertTrue(session.plot.isLayerMulticolor(drill));
                assertFalse(session.plot.isLayerVisible(route)); assertTrue(session.plot.isLayerMulticolor(route));
                assertEquals(Color.web("#22446688"),session.plot.layerColors(route)[1]);
                style("gerber","#FF0000",true);
                assertFalse(session.plot.isLayerVisible(gerber)); assertEquals(Color.web("#AABBCC40"),session.plot.layerColors(gerber)[1]);
                return null;
            });
            session.host.openGerber(copper(directory),"second");
            TerminalPanelTest.fx(()->{ assertTrue(session.plot.isLayerVisible(session.item("gerber","second"))); return null; });
        }
    }

    @Test void followKeepsColorsAndCopyKeepsTheSourceModeRatherThanTheCurrentDefault(@TempDir Path directory) throws Exception {
        style("gerber","#AABBCC40",false);
        try(var session=new Session()) {
            session.host.openGerber(copper(directory),"copper");
            TerminalPanelTest.fx(()->{
                var original=session.item("gerber","copper"); var image=((Map<?,?>)field("gerberByItem").get(session.host)).get(original);
                ToolDefaults.set("display.gerber.follow",true,"true");
                @SuppressWarnings("unchecked") var copy=(TreeItem<String>)invoke(session.host,"copyObject",new Class<?>[]{TreeItem.class},original);
                assertFalse(((java.util.Set<?>)field("gerberFollowItems").get(session.host)).contains(copy));
                assertArrayEquals(session.plot.layerColors(original),session.plot.layerColors(copy)); assertFalse(session.plot.isLayerVisible(copy));
                Node properties=(Node)invoke(session.host,"buildGerberPropertiesPanel",new Class<?>[]{TreeItem.class,GerberImage.class},original,image);
                var root=new VBox(properties); new Scene(root); root.applyCss(); root.layout();
                CheckBox follow=root.lookupAll(".check-box").stream().filter(n->n instanceof CheckBox c && c.getText().equals("Follow")).map(n->(CheckBox)n).findFirst().orElseThrow();
                follow.fire(); assertTrue(((java.util.Set<?>)field("gerberFollowItems").get(session.host)).contains(original));
                assertEquals(Color.web("#AABBCC40"),session.plot.layerColors(original)[1]); assertFalse(session.plot.isLayerVisible(original));
                follow.fire(); assertFalse(((java.util.Set<?>)field("gerberFollowItems").get(session.host)).contains(original));
                assertEquals(Color.web("#12345680"),session.plot.layerColors(original)[0]);
                return null;
            });
        }
    }

    @ParameterizedTest @ValueSource(strings={".fcnproj",".FlatPrj"})
    void reopenedProjectsWinOverChangedDefaultsAndNewObjectsStillUseThem(String extension,@TempDir Path directory) throws Exception {
        style("gerber","#AABBCC40",true); style("excellon","#778899FF",true); style("geometry","#22446688",true);
        ToolDefaults.set("display.gerber.follow",true,"true");
        try(var session=new Session()) {
            session.host.openGerber(copper(directory),"copper"); session.host.openExcellon(drills(directory),"holes"); session.geometry();
            Path saved=directory.resolve("styles"+extension); session.host.saveProject(saved);
            byte[] before=Files.readAllBytes(saved);
            for(String kind:new String[]{"gerber","excellon","geometry"}) style(kind,"#FF0000",false);
            ToolDefaults.set("display.gerber.follow",true,"false"); session.host.openProject(saved,true);
            TerminalPanelTest.fx(()->{
                assertTrue(session.plot.isLayerVisible(session.item("gerber","copper")));
                assertTrue(session.plot.isLayerVisible(session.item("excellon","holes")));
                assertTrue(session.plot.isLayerVisible(session.item("geometry","route")));
                assertEquals(Color.web("#AABBCC40"),session.plot.layerColors(session.item("gerber","copper"))[1]);
                assertTrue(((java.util.Set<?>)field("gerberFollowItems").get(session.host)).contains(session.item("gerber","copper")));
                assertEquals(Color.web("#778899FF"),session.plot.layerColors(session.item("excellon","holes"))[1]);
                assertEquals(Color.web("#22446688"),session.plot.layerColors(session.item("geometry","route"))[1]);
                assertTrue(session.plot.isLayerMulticolor(session.item("geometry","route")));
                assertFalse(session.plot.isLayerFilled(session.item("gerber","copper"))); return null;
            });
            session.host.openGerber(copper(directory),"new");
            TerminalPanelTest.fx(()->{ assertFalse(session.plot.isLayerVisible(session.item("gerber","new"))); return null; });
            assertArrayEquals(before,Files.readAllBytes(saved));
        }
    }

    @Test void apertureExpansionDefaultIsAppliedToNewPanelsOnly(@TempDir Path directory) throws Exception {
        try(var session=new Session()) {
            session.host.openGerber(copper(directory),"copper");
            TerminalPanelTest.fx(()->{
                var item=session.item("gerber","copper"); var image=((Map<?,?>)field("gerberByItem").get(session.host)).get(item);
                VBox first=new VBox((Node)invoke(session.host,"buildGerberPropertiesPanel",new Class<?>[]{TreeItem.class,GerberImage.class},item,image));
                new Scene(first); first.applyCss(); first.layout();
                TitledPane table=(TitledPane)first.lookup("#gerber-apertures-pane"); assertFalse(table.isExpanded());
                ToolDefaults.set("display.gerber.aperturetable",true,"true");
                VBox second=new VBox((Node)invoke(session.host,"buildGerberPropertiesPanel",new Class<?>[]{TreeItem.class,GerberImage.class},item,image));
                new Scene(second); second.applyCss(); second.layout();
                assertTrue(((TitledPane)second.lookup("#gerber-apertures-pane")).isExpanded()); assertFalse(table.isExpanded());
                table.setExpanded(true); assertTrue(table.isExpanded()); return null;
            });
        }
    }

    @Test void standardColorActionUsesConfiguredDefaultsWithoutChangingVisibility(@TempDir Path directory) throws Exception {
        try(var session=new Session()) {
            session.host.openGerber(copper(directory),"copper");
            style("gerber","#AABBCC40",false);
            TerminalPanelTest.fx(()->{
                var item=session.item("gerber","copper");
                var image=((Map<?,?>)field("gerberByItem").get(session.host)).get(item);
                @SuppressWarnings("unchecked") var menuItems=(java.util.List<MenuItem>)invoke(session.host,"gerberContextMenuItems",new Class<?>[]{TreeItem.class,GerberImage.class},item,image);
                Menu colors=menuItems.stream().filter(m->m instanceof Menu && m.getText().equals("Definir Cor")).map(m->(Menu)m).findFirst().orElseThrow();
                colors.getItems().stream().filter(m->m.getText()!=null && m.getText().equals("Padrao")).findFirst().orElseThrow().fire();
                assertEquals(Color.web("#AABBCC40"),session.plot.layerColors(item)[1]);
                assertEquals(Color.web("#12345680"),session.plot.layerColors(item)[0]);
                assertTrue(session.plot.isLayerVisible(item),"changing a default does not hide the existing object");
                return null;
            });
        }
    }
}
