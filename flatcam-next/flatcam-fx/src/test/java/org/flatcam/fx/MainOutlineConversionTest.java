package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.scene.control.*;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.*;
import org.flatcam.cam.convert.OutlineToArea;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.panel.Panelize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

/** Real conversion menu -> NCC/Cutout -> CNC/export -> native reopen, without Stage/preferences. */
@EnabledOnOs(OS.WINDOWS)
class MainOutlineConversionTest {
    @TempDir Path directory;
    private static final GeometryFactory F = new GeometryFactory();
    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(window,args);
    }
    private static TreeItem<String> add(MainCamFlowTest.Session s, GerberImage image) throws Exception {
        return add(s, "Edge_Cuts_panelized", image);
    }
    private static TreeItem<String> add(MainCamFlowTest.Session s, String name, GerberImage image) throws Exception {
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addGerberToProject",
                new Class<?>[]{String.class,Path.class,GerberImage.class}, name, null,image));
    }
    private static JobHandle<?> convert(MainCamFlowTest.Session s, TreeItem<String> item) throws Exception {
        return TerminalPanelTest.fx(() -> {
            var tree = (TreeView<String>)field("projectTree").get(s.window);
            tree.getSelectionModel().clearSelection(); tree.getSelectionModel().select(item);
            call(s.window,"convertOutlineToArea",new Class<?>[]{});
            return (JobHandle<?>)field("runningJob").get(s.window);
        });
    }
    private static void await(MainCamFlowTest.Session s, JobHandle<?> handle) throws Exception {
        assertNotNull(handle); handle.completion().get(180,TimeUnit.SECONDS);
        for (int i=0;i<300;i++) {
            if (TerminalPanelTest.fx(() -> field("runningJob").get(s.window)==null)) return;
            Thread.sleep(10);
        }
        fail("FX publication did not finish");
    }
    private static ProjectFile.GeometryEntry converted(MainCamFlowTest.Session s) throws Exception {
        return MainIsolationMachiningTest.snapshot(s.window).geometries().stream()
                .filter(g -> g.name().equals("Edge_Cuts_panelized_area")).findFirst().orElseThrow();
    }
    private static TreeItem<String> item(MainCamFlowTest.Session s, String map, String name) throws Exception {
        return TerminalPanelTest.fx(() -> ((Map<TreeItem<String>,?>)field(map).get(s.window)).keySet().stream()
                .filter(i -> i.getValue().equals(name)).findFirst().orElseThrow());
    }
    private static JobHandle<?> start(MainCamFlowTest.Session s,String method,Class<?>[] types,Object...args) throws Exception {
        return TerminalPanelTest.fx(() -> {
            call(s.window,method,types,args); return (JobHandle<?>)field("runningJob").get(s.window);
        });
    }
    private static int holes(Geometry geometry) {
        int count=0; for(int i=0;i<geometry.getNumGeometries();i++) count+=((Polygon)geometry.getGeometryN(i)).getNumInteriorRing();
        return count;
    }
    private void cnc(MainCamFlowTest.Session s, ProjectFile.GeometryEntry geometry,double diameter,String name) throws Exception {
        double u=geometry.units().equals("IN")?1/25.4:1;
        s.window.cncjob(geometry.name(),name,diameter,-.1*u,3*u,120*u,50*u,0);
        Path file=directory.resolve(name+".nc"); s.window.writeGcode(name,file,"","");
        var preview=GCodeToolpathParser.parse(Files.readString(file),()->false,f->{});
        assertTrue(preview.plotAvailable(),preview.warning()); assertEquals(geometry.units(),preview.units());
        assertEquals(geometry.geometry().getLength(),preview.cutCenterlines().getLength(),
                Math.max(.003*u,geometry.geometry().getLength()*.001));
    }

    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void panelizeFirstThenConvertPreservesEveryHoleForNccCutoutCncAndPersistence(String units) throws Exception {
        try (var s=new MainCamFlowTest.Session(units)) {
            s.release.countDown(); double u=s.unit;
            Geometry board=F.toGeometry(new Envelope(10,30,20,36))
                    .difference(F.toGeometry(new Envelope(24,27,29,32)));
            board=AffineTransformation.scaleInstance(u,u).transform(board);
            var layout=new Panelize.Layout(2,2,false,25*u,21*u);
            var panel=Panelize.gerber(GerberImage.of(units,Map.of(),board.getBoundary().buffer(.05*u),board.getBoundary(),Map.of()),layout);
            var outline=add(s,panel); await(s,convert(s,outline));
            var area=converted(s); assertEquals(4,area.geometry().getNumGeometries()); assertEquals(4,holes(area.geometry()));
            assertEquals(board.getArea()*4,area.geometry().getArea(),1e-4*u*u);
            var areaItem=item(s,"geometryByItem",area.name());
            Geometry copper=board.buffer(-1*u);
            var copperPanel=Panelize.gerber(GerberImage.of(units,Map.of(),copper,copper.getBoundary(),Map.of()),layout);
            var copperItem=add(s,"Copper_panelized",copperPanel);
            var params=new NccParameters(List.of(.5*u),.4,0,NccMethod.STANDARD,false,true,0,false,NccOrder.NONE,
                    new NccBoundary.ReferenceGeometry(area.geometry()),List.of());
            var ncc=new NccToolPanel.Result(new NccToolPanel.SourceCandidate(copperItem,"Copper_panelized",units,true,copperPanel.solidGeometry()),
                    params,false,Map.of(.5*u,ToolProfile.C1),new NccToolPanel.ReferenceCandidate(areaItem,area.name(),false,area.geometry()));
            await(s,start(s,"runNccGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,boolean.class,NccToolPanel.Result.class},
                    copperItem,units,copperPanel.solidGeometry(),true,ncc));
            var cleared=MainIsolationMachiningTest.snapshot(s.window).geometries().stream().filter(g->g.name().endsWith("_ncc")).findFirst().orElseThrow();
            assertTrue(cleared.geometry().difference(area.geometry().buffer(1e-7*u)).getLength()<1e-6*u);
            for(int i=0;i<4;i++) assertFalse(cleared.geometry().intersection(area.geometry().getGeometryN(i)).isEmpty());
            var machining=new GeometryGCodeParameters(3*u,1.6*u,false,1.6*u,120*u,0,false);
            var cutout=new CutoutToolPanel.Result(new CutoutParameters(.8*u,0,false,CutoutKind.PANEL,CutoutShape.FREEFORM,
                    2*u,GapPattern.FOUR,true),CutoutToolPanel.GapType.BRIDGE,0,0,List.of(),machining,null,ToolProfile.C1);
            await(s,start(s,"runCutoutGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,BooleanSupplier.class,CutoutToolPanel.Result.class},
                    areaItem,units,area.geometry(),(BooleanSupplier)()->true,cutout));
            var cut=MainIsolationMachiningTest.snapshot(s.window).geometries().stream().filter(g->g.name().endsWith("_cutout")).findFirst().orElseThrow();
            int closed=0; for(int i=0;i<cut.geometry().getNumGeometries();i++) if(((LineString)cut.geometry().getGeometryN(i)).isClosed()) closed++;
            assertEquals(4,closed,"One closed internal cut per converted board, with external bridges");
            cnc(s,cleared,.5*u,"converted-ncc"); cnc(s,cut,.8*u,"converted-cutout");
            var before=MainIsolationMachiningTest.snapshot(s.window);
            Path file=directory.resolve(units+".fcnproj"); s.window.saveProject(file); s.window.openProject(file);
            var reopened=converted(s); assertTrue(area.geometry().equalsExact(reopened.geometry(),1e-10*u),
                    "Use the existing native WKT precision tolerance, not bit-identical doubles");
            assertEquals(before.cncJobs(),MainIsolationMachiningTest.snapshot(s.window).cncJobs());
        }
    }

    @Test void mixedClosedAndOpenPanelDoesNotPublishAPartialArea() throws Exception {
        try (var s=new MainCamFlowTest.Session("MM")) {
            s.release.countDown();
            Geometry partial=F.buildGeometry(List.of(s.image.followGeometry(),
                    F.createLineString(new Coordinate[]{new Coordinate(30,0),new Coordinate(40,0)})));
            var source=add(s,GerberImage.of("MM",Map.of(),partial.buffer(.05),partial,Map.of()));
            await(s,convert(s,source)); s.assertNothingPublished();
        }
    }

    @ParameterizedTest @ValueSource(strings={"rename","remove","replace","project","cancel"})
    void staleOrCancelledConversionCannotPublish(String change) throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var h=convert(s,s.item);
            TerminalPanelTest.fx(() -> {
                var map=(Map<TreeItem<String>,GerberImage>)field("gerberByItem").get(s.window);
                switch(change) {
                    case "rename" -> s.item.setValue("changed");
                    case "remove" -> map.remove(s.item);
                    case "replace" -> map.put(s.item,GerberImage.of("MM",Map.of(),s.image.solidGeometry().copy(),s.image.followGeometry().copy(),Map.of()));
                    case "project" -> field("tclProjectEpoch").setLong(s.window,42);
                    default -> h.cancel();
                }
                return null;
            });
            s.release.countDown();
            if(change.equals("cancel")) assertThrows(java.util.concurrent.CancellationException.class,()->h.completion().get(10,TimeUnit.SECONDS));
            else h.completion().get(10,TimeUnit.SECONDS);
            s.assertNothingPublished();
        }
    }

    @Test void realPanelOutlineConvertsAllFourBoardsWithoutChangingPrivateProject() throws Exception {
        String fixture=System.getProperty("flatcam.python.project.fixture");
        assumeTrue(fixture!=null&&!fixture.isBlank(),"Read-only private fixture is opt-in");
        Path source=Path.of(fixture); byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        try(var s=new MainCamFlowTest.Session("MM")) {
            s.release.countDown();
            var edge=PythonProjectIO.load(source).gerbers().stream().filter(g->g.name().toLowerCase(Locale.ROOT).contains("edge_cuts")).findFirst().orElseThrow().image();
            var original=OutlineToArea.convert(edge.followGeometry()).area();
            var layout=Panelize.layout(edge.bounds(),2,2,5,5,Double.NaN,Double.NaN);
            var panel=Panelize.gerber(edge,layout);
            await(s,convert(s,add(s,panel))); var area=converted(s).geometry();
            assertEquals(4,area.getNumGeometries()); assertEquals(holes(original)*4,holes(area));
            Geometry expected=F.buildGeometry(layout.offsets().stream().map(o->AffineTransformation.translationInstance(o[0],o[1]).transform(original)).toList());
            // Each polygonization snaps to its own sub-micron grid: do not require bit-identical
            // vertices when comparing pre-conversion replication with post-panel conversion.
            assertTrue(expected.symDifference(area).getArea()<expected.getBoundary().getLength()*1e-6);
            assertTrue(org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance.distance(expected.getBoundary(),area.getBoundary())<1e-6);
        } finally { assertArrayEquals(hash,java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))); }
    }
}
