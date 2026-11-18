package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Selected table rows -> Rest -> published Geometry -> project -> CNC form, per cutter. */
@EnabledOnOs(OS.WINDOWS)
class NccMultiClearRestPanelTest {
    @TempDir Path directory;
    @ParameterizedTest @EnumSource(NccMethod.class)
    @SuppressWarnings({"unchecked","rawtypes"})
    void threeClearRowsAndOneIsoKeepTheirOwnRestPathsThroughPublication(NccMethod method)throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var submission=new AtomicReference<NccToolPanel.Result>();
            TerminalPanelTest.fx(()->{
                var source=new NccToolPanel.SourceCandidate(s.item,"board","MM",true,s.image.solidGeometry());
                Parent panel=(Parent)NccToolPanel.build(List.of(source),source,List.of(),
                        (chosen,shape,done,cancel)->false,()->{},List::of,submission::set,()->{});
                new Scene(panel,410,1400);panel.applyCss();panel.layout();
                var table=(TableView)panel.lookup("#ncc-tools");
                var rowClass=Class.forName("org.flatcam.fx.NccToolPanel$ToolRow");
                var ctor=rowClass.getDeclaredConstructor(double.class);ctor.setAccessible(true);
                var rows=new ArrayList<>();
                for(double diameter:List.of(.4,1.2,.2,.8)) {
                    Object row=ctor.newInstance(diameter);
                    set(row,"method",method);set(row,"operation",diameter==.2?NccOperation.ISO:NccOperation.CLEAR);
                    set(row,"overlapPercent","40");rows.add(row);
                }
                table.getItems().setAll(rows);table.getSelectionModel().selectAll();
                ((Spinner<Double>)panel.lookup("#ncc-margin")).getValueFactory().setValue(2.0);
                var rest=panel.lookupAll(".check-box").stream().filter(n->n instanceof CheckBox c&&c.getText().equals("Rest Machining"))
                        .map(CheckBox.class::cast).findFirst().orElseThrow();rest.setSelected(true);
                ((CheckBox)panel.lookup("#ncc-rest-connect")).setSelected(true);
                ((CheckBox)panel.lookup("#ncc-rest-contour")).setSelected(true);
                ((Button)panel.lookup("#ncc-generate")).fire();
                assertNotNull(submission.get());assertTrue(submission.get().parameters().restMachining());
                assertEquals(List.of(.4,1.2,.8),submission.get().parameters().toolDiameters());
                assertEquals(List.of(.2),submission.get().parameters().isolationToolDiameters());return null;
            });
            var submitted=submission.get();
            var expected=NccGenerator.generate("MM",s.image.solidGeometry(),submitted.parameters()).toolResults().stream().filter(t->!t.isEmpty()).toList();
            var job=TerminalPanelTest.fx(()->s.startFx("NCC",submitted));s.release.countDown();
            job.completion().get(15,TimeUnit.SECONDS);s.awaitUi();
            Path file=directory.resolve("multi-rest.fcnproj");s.window.saveProject(file);s.window.openProject(file);
            var entry=MainIsolationMachiningTest.snapshot(s.window).geometries().stream().filter(g->!g.name().equals("boundary")).findFirst().orElseThrow();
            var cnc=MainIsolationMachiningTest.cnc(entry);
            assertEquals(expected.size(),entry.tools().size());assertEquals(expected.size(),cnc.tools().size());
            for(int i=0;i<expected.size();i++) {
                var tool=expected.get(i);assertEquals(tool.toolDiameter(),entry.tools().get(i).toolDiameter());
                var actual=entry.tools().get(i).geometry();
                double error=maxCoordinateError(tool.geometry(),actual);
                System.out.printf(java.util.Locale.ROOT,"REST-PANEL %s diameter=%.1f length=%.9f coordinateError=%.3g%n",
                        method.name(),tool.toolDiameter(),actual.getLength(),error);
                // WKT persistence rounds floating-point text at the final decimal places.
                assertTrue(tool.geometry().equalsExact(actual,1e-12),"Publishing/saving must retain this cutter's paths; error="+error);
                assertTrue(tool.geometry().equalsExact(cnc.tools().get(i).geometry(),1e-12),"CNC must not replace a Rest cutter with the combined paths");
            }
        }
    }
    private static void set(Object row,String name,Object value)throws Exception {
        var field=row.getClass().getDeclaredField(name);field.setAccessible(true);field.set(row,value);
    }
    private static double maxCoordinateError(org.locationtech.jts.geom.Geometry a,org.locationtech.jts.geom.Geometry b) {
        var x=a.getCoordinates();var y=b.getCoordinates();if(x.length!=y.length)return Double.POSITIVE_INFINITY;
        double error=0;for(int i=0;i<x.length;i++)error=Math.max(error,x[i].distance(y[i]));return error;
    }
}
