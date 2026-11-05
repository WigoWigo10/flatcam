package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.cutout.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class MainInternalCutoutTest {
    @TempDir Path folder;
    private static final GeometryFactory F=new GeometryFactory();
    @BeforeEach @AfterEach void defaults(){ToolDefaults.useStore(ToolDefaults.memoryStore());}
    private static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static Object call(MainWindow value,String name,Class<?>[] types,Object...args)throws Exception{
        var m=MainWindow.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(value,args);
    }
    private static Node panel(MainWindow window)throws Exception{return ((ScrollPane)((Tab)field(window,"toolTab")).getContent()).getContent();}
    private static void fire(Node root,String id){((Button)root.lookup("#"+id)).fire();}

    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void realModeButtonTransfersDraftAndProducesOnlyInternalGeometryThroughCncAndBothProjectFormats(String units)throws Exception{
        try(var s=new MainCamFlowTest.Session(units)){
            s.release.countDown();double u=s.unit;
            Geometry board=F.toGeometry(new Envelope(0,40*u,0,30*u)).difference(F.toGeometry(new Envelope(10*u,18*u,8*u,16*u)));
            String original=board.toText();
            var origin=TerminalPanelTest.fx(()->(TreeItem<String>)call(s.window,"addGeometryToProject",
                    new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},"Outline_area","",units,board,false));
            TerminalPanelTest.fx(()->{
                var entry=s.geometries().get(origin);
                call(s.window,"generateCutout",new Class<?>[]{TreeItem.class,entry.getClass()},origin,entry);
                Node root=panel(s.window);
                ((TextField)root.lookup("#cutout-tool-diameter")).setText(Double.toString(.1981234567*u));
                ((TextField)root.lookup("#cutout-margin")).setText("0");
                ((TextField)root.lookup("#cutout-cut-z")).setText(Double.toString(-1.6*u));
                ((CheckBox)root.lookup("#cutout-multi-depth")).setSelected(true);
                ((TextField)root.lookup("#cutout-depth-per-pass")).setText(Double.toString(.4*u));
                fire(root,"cutout-open-internal");return null;
            });
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<until){
                if(TerminalPanelTest.fx(()->((TableView<?>)panel(s.window).lookup("#cutout-internal-openings")).getItems().size()==1))break;
                Thread.sleep(10);
            }
            Node root=TerminalPanelTest.fx(()->panel(s.window));
            TerminalPanelTest.fx(()->{((TableView<?>)root.lookup("#cutout-internal-openings")).getSelectionModel().select(0);return null;});
            until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<until){
                if(TerminalPanelTest.fx(()->!root.lookup("#cutout-internal-generate").isDisabled()))break;
                Thread.sleep(10);
            }
            var job=TerminalPanelTest.fx(()->{
                var table=(TableView<?>)root.lookup("#cutout-internal-openings");assertEquals(1,table.getItems().size());
                assertEquals(.1981234567*u,Double.parseDouble(((TextField)root.lookup("#cutout-internal-diameter")).getText()));
                assertFalse(root.lookup("#cutout-internal-generate").isDisabled());fire(root,"cutout-internal-generate");
                return (JobHandle<?>)field(s.window,"runningJob");
            });
            assertNotNull(job);job.completion().get(10,TimeUnit.SECONDS);s.awaitUi();
            ProjectFile snapshot=MainIsolationMachiningTest.snapshot(s.window);
            var cut=snapshot.geometries().stream().filter(g->g.name().endsWith("_internal_cutout")).findFirst().orElseThrow();
            assertEquals(1,cut.geometry().getNumGeometries());assertTrue(((LineString)cut.geometry()).isClosed());
            assertEquals(10*u+.1981234567*u/2,cut.geometry().getEnvelopeInternal().getMinX(),1e-10);
            assertEquals(.1981234567*u,cut.tools().getFirst().toolDiameter());
            assertEquals(1.6*u,cut.cncDefaults().cutDepth());assertTrue(cut.cncDefaults().multiDepth());
            assertEquals(.4*u,cut.cncDefaults().depthPerPass());assertEquals(original,board.toText());
            TerminalPanelTest.fx(()->{assertSame(root,panel(s.window),"generation keeps the internal selection draft open");return null;});
            s.window.cncjob(cut.name(),"internal-cnc",.1981234567*u,-.1*u,3*u,100*u,50*u,0);
            Path code=folder.resolve(units+".nc");s.window.writeGcode("internal-cnc",code,"","");
            var parsed=GCodeToolpathParser.parse(Files.readString(code),()->false,f->{});
            assertTrue(parsed.plotAvailable(),parsed.warning());assertEquals(units,parsed.units());
            assertEquals(cut.geometry().getLength(),parsed.cutCenterlines().getLength(),.003*u);
            assertTrue(parsed.cutCenterlines().getEnvelopeInternal().getMinX()>9*u);
            for(String extension:List.of(".fcnproj",".FlatPrj")){
                Path project=folder.resolve(units+extension);s.window.saveProject(project);s.window.openProject(project);
                var reopened=MainIsolationMachiningTest.snapshot(s.window).geometries().stream().filter(g->g.name().equals(cut.name())).findFirst().orElseThrow();
                assertTrue(cut.geometry().equalsExact(reopened.geometry(),1e-9*u));assertEquals(cut.cncDefaults(),reopened.cncDefaults());
                assertEquals(.1981234567*u,reopened.tools().getFirst().toolDiameter());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings={"remove","replace","project","cancel"})
    void queuedInternalGenerationHonoursTheExistingCamPublicationGuards(String change)throws Exception{
        try(var s=new MainCamFlowTest.Session("MM")){
            var original=(CutoutToolPanel.Result)s.result("Cutout",false);
            var request=new CutoutToolPanel.Result(original.cutoutParams(),CutoutToolPanel.GapType.BRIDGE,0,0,List.of(),
                    original.machining(),null,original.profile(),original.jobDefaults(),List.of(F.toGeometry(new Envelope(2,8,2,6))));
            var job=TerminalPanelTest.fx(()->s.startFx("Cutout",request));assertNotNull(job);
            TerminalPanelTest.fx(()->{
                var map=(Map<TreeItem<String>,GerberImage>)field(s.window,"gerberByItem");
                switch(change){
                    case "remove" -> map.remove(s.item);
                    case "replace" -> map.put(s.item,GerberImage.of("MM",Map.of(),s.image.solidGeometry().copy(),s.image.followGeometry().copy(),Map.of()));
                    case "project" -> {var f=MainWindow.class.getDeclaredField("tclProjectEpoch");f.setAccessible(true);f.setLong(s.window,42);}
                    default -> job.cancel();
                }return null;
            });
            s.release.countDown();
            if(change.equals("cancel"))assertThrows(java.util.concurrent.CancellationException.class,()->job.completion().get(10,TimeUnit.SECONDS));
            else job.completion().get(10,TimeUnit.SECONDS);
            s.assertNothingPublished();
        }
    }
}
