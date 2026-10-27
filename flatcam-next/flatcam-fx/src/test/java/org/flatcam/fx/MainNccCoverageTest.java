package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.*;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class MainNccCoverageTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void paintPublishesItsActualClearingAreaAndPersistsKnownCoverage() throws Exception {
        try (var session=new MainCamFlowTest.Session("MM")) {
            var params=new org.flatcam.cam.ncc.PaintParameters(List.of(1.0),.2,0,
                    org.flatcam.cam.ncc.NccMethod.STANDARD,false,true,org.flatcam.cam.ncc.NccOrder.NONE,false);
            var handle=TerminalPanelTest.fx(()->{
                call(session.window,"runPaintGeneration",new Class<?>[]{TreeItem.class,String.class,org.locationtech.jts.geom.Geometry.class,org.flatcam.cam.ncc.PaintParameters.class},
                        session.reference,"MM",session.referenceGeometry,params);
                return (JobHandle<?>)field(session.window,"runningJob");
            });
            session.release.countDown(); handle.completion().get(15,TimeUnit.SECONDS); session.awaitUi();
            var snapshot=TerminalPanelTest.fx(()->{
                var entry=session.geometries().entrySet().stream().filter(e->e.getKey().getValue().endsWith("_paint")).findFirst().orElseThrow().getValue();
                var target=(MachiningTarget)component(entry,"machiningTarget"); assertNotNull(target);
                assertEquals("PAINT",target.operation()); assertEquals(session.referenceGeometry.getArea(),target.area().getArea(),1e-8);
                var saved=(ProjectFile)call(session.window,"snapshotProject",new Class<?>[]{});
                assertTrue(saved.geometries().stream().anyMatch(e->e.machiningTarget()!=null && e.machiningTarget().operation().equals("PAINT")));
                return saved;
            });
            var file=directory.resolve("paint.fcnproj"); org.flatcam.app.project.ProjectFileIO.save(snapshot,file,true);
            var loaded=org.flatcam.app.project.ProjectFileIO.load(file);
            assertTrue(loaded.geometries().stream().anyMatch(e->e.machiningTarget()!=null && e.machiningTarget().operation().equals("PAINT")));
        }
    }
    static Object component(Object record, String name) throws Exception {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(window,args);
    }
    static Object field(MainWindow window, String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field.get(window);
    }
    @Test void realNccPublicationPreservesTargetAnalysisIsReadOnlyAndAffineTransportRetainsIt() throws Exception {
        try (var session = new MainCamFlowTest.Session("MM")) {
            var ncc = session.start("NCC",true); session.release.countDown(); ncc.completion().get(15,TimeUnit.SECONDS); session.awaitUi();
            var generated = TerminalPanelTest.fx(() -> session.geometries().keySet().stream().filter(item -> item != session.reference).findFirst().orElseThrow());
            Object entry = TerminalPanelTest.fx(() -> session.geometries().get(generated));
            var target = (MachiningTarget) component(entry,"machiningTarget"); assertNotNull(target);
            var tools = (List<ToolGeometry>) component(entry,"tools");
            var draft = new GeometryCncToolPanel.Result(tools, CncPreflightTest.params(3,.2,234,67,12345),Map.of(),GCodePreprocessor.GRBL_11_NO_M6,Map.of());
            var analysis = TerminalPanelTest.fx(() -> {
                call(session.window,"showCncCoverage",new Class<?>[]{TreeItem.class,entry.getClass(),GeometryCncToolPanel.Result.class},generated,entry,draft);
                return (JobHandle<?>) field(session.window,"runningJob");
            });
            analysis.completion().get(15,TimeUnit.SECONDS); session.awaitUi();
            TerminalPanelTest.fx(() -> {
                assertSame(entry,session.geometries().get(generated),"Analyzing cannot replace or dirty the source entry");
                var saved = (ProjectFile) call(session.window,"snapshotProject",new Class<?>[]{});
                assertNotNull(saved.geometries().stream().filter(value -> value.name().equals(generated.getValue())).findFirst().orElseThrow().machiningTarget());
                var tab = (Tab) field(session.window,"toolTab");
                var pane = (ScrollPane) tab.getContent(); var root = pane.getContent();
                ((Button) root.lookup("#cnc-coverage-back")).fire();
                root = ((ScrollPane) tab.getContent()).getContent();
                assertEquals("234.0",((TextField) root.lookup("#cnc-feed")).getText());
                assertEquals("12345",((TextField) root.lookup("#cnc-power")).getText());
                call(session.window,"applyTransformToItem",new Class<?>[]{TreeItem.class,TransformOp.class},generated,new TransformOp.Offset(1,2));
                var moved=(MachiningTarget)component(session.geometries().get(generated),"machiningTarget");
                assertNotNull(moved);
                assertEquals(target.area().getArea(),moved.area().getArea(),1e-8);
                assertEquals(target.area().getEnvelopeInternal().getMinX()+1,moved.area().getEnvelopeInternal().getMinX(),1e-8);
                call(session.window,"applyTransformToItem",new Class<?>[]{TreeItem.class,TransformOp.class},generated,new TransformOp.Buffer(.01,false,true));
                assertNull(component(session.geometries().get(generated),"machiningTarget"),"Buffer is not affine target transport");
                return null;
            });
        }
    }
}
