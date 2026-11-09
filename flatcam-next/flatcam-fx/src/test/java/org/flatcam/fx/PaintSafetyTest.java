package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.ncc.PaintParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class PaintSafetyTest {
    private static final GeometryFactory F=new GeometryFactory();
    static final class Host implements PaintToolPanel.Host {
        final TreeItem<String> mm=new TreeItem<>("mm"),inch=new TreeItem<>("inch"),reference=new TreeItem<>("reference");
        final Map<TreeItem<String>,Geometry> shapes=new HashMap<>();final TreeItem<String> root=new TreeItem<>("root");
        Consumer<Coordinate> pick;PaintParameters generated;TreeItem<String> initial=mm;boolean inchFirst;
        Host(){root.getChildren().addAll(mm,inch,reference);shapes.put(mm,F.toGeometry(new Envelope(0,10,0,8)));shapes.put(reference,shapes.get(mm).copy());shapes.put(inch,F.toGeometry(new Envelope(0,1,0,1)));}
        public List<TreeItem<String>> sources(){return inchFirst?List.of(inch,mm,reference):List.of(mm,inch,reference);}public TreeItem<String> initialSource(){return initial;}
        public String units(TreeItem<String> item){return item==inch?"IN":"MM";}public Geometry polygons(TreeItem<String> item){return shapes.get(item);}
        public void pickPoint(Consumer<Coordinate> callback){pick=callback;}public void cancelPick(){pick=null;}
        public void selectArea(Geometry area,boolean polygon,Consumer<Geometry> done,Runnable cancel){}public void cancelAreaSelection(){}
        public void preview(Geometry geometry){}public void paint(TreeItem<String> item,String units,Geometry area,PaintParameters parameters){generated=parameters;}
    }
    static Node form(Host host){var form=PaintToolPanel.build(host,()->{});var root=new VBox(form);new Scene(root,540,1500);root.applyCss();root.layout();return form;}
    static List<Node> nodes(Node root){var result=new ArrayList<Node>();result.add(root);if(root instanceof Parent p)for(var child:p.getChildrenUnmodifiable())result.addAll(nodes(child));return result;}
    static void choose(Node form,Host host,String key){
        nodes(form).stream().filter(n->n instanceof RadioButton r && I18n.text(key).equals(r.getText())).map(RadioButton.class::cast).findFirst().orElseThrow().setSelected(true);
        nodes(form).stream().filter(n->n instanceof Button b && I18n.text("ui.6a57618c2593").equals(b.getText())).map(Button.class::cast).findFirst().orElseThrow().fire();
        if(host.pick!=null)host.pick.accept(new Coordinate(3,3));
    }
    @ParameterizedTest @ValueSource(strings={"remove","replace"})
    void pickedPolygonsCannotOutliveTheSource(String change)throws Exception {
        TerminalPanelTest.fx(()->{
            var host=new Host();var form=form(host);choose(form,host,"ui.ba89e6f82dfb");
            if(change.equals("remove")){host.root.getChildren().remove(host.mm);host.shapes.remove(host.mm);}else host.shapes.put(host.mm,host.shapes.get(host.mm).copy());
            ((Button)form.lookup("#paint-generate")).fire();assertNull(host.generated,"must reject stale picked polygons");
            assertFalse(((Label)form.lookup("#paint-error")).getText().isBlank());return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"remove","replace"})
    void pickedReferenceCannotOutliveItsGeometry(String change)throws Exception {
        TerminalPanelTest.fx(()->{
            var host=new Host();var form=form(host);
            var picker=(ComboBox<TreeItem<String>>)nodes(form).stream().filter(n->n instanceof ComboBox<?> c && c.visibleProperty().isBound()).findFirst().orElseThrow();picker.setValue(host.reference);
            choose(form,host,"ui.a8e4c7c07fd3");
            if(change.equals("remove")){host.root.getChildren().remove(host.reference);host.shapes.remove(host.reference);}else host.shapes.put(host.reference,host.shapes.get(host.reference).copy());
            ((Button)form.lookup("#paint-generate")).fire();assertNull(host.generated);return null;
        });
    }
    @Test void sourceAndReferencePickersKeepOneUnitAndRejectForeignProgrammaticValues()throws Exception {
        TerminalPanelTest.fx(()->{
            var host=new Host();var form=form(host);
            for(var node:nodes(form))if(node instanceof ComboBox<?> combo && combo.getItems().contains(host.mm))assertFalse(combo.getItems().contains(host.inch),"incompatible units must not be offered");
            var source=(ComboBox<TreeItem<String>>)nodes(form).stream().filter(n->n instanceof ComboBox<?> c && c.getValue()==host.mm).findFirst().orElseThrow();
            ((TextField)form.lookup("#paint-diameters")).setText("0,1981234567");source.setValue(host.inch);
            ((Button)form.lookup("#paint-generate")).fire();assertNull(host.generated,"foreign units must not reinterpret an existing cutter");return null;
        });
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void inchPanelUsesTheActualFallbackSourceUnits(boolean noSelection)throws Exception {
        TerminalPanelTest.fx(()->{
            var host=new Host();host.inchFirst=true;host.initial=noSelection?null:host.inch;var form=form(host);
            for(var node:nodes(form))if(node instanceof ComboBox<?> combo && combo.getItems().contains(host.inch))assertFalse(combo.getItems().contains(host.mm));
            ((Button)form.lookup("#paint-generate")).fire();assertNotNull(host.generated);return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"remove","replace"})
    void backgroundGenerationRevalidatesAReferenceAndAcceptsASourceSubset(String change)throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            var handle=TerminalPanelTest.fx(()->{
                var parameters=new PaintParameters(List.of(.5),.4,0,org.flatcam.cam.ncc.NccMethod.LINES,false,true,org.flatcam.cam.ncc.NccOrder.NONE,false);
                var subset=F.toGeometry(new Envelope(1,3,1,3));
                MainToolPersistenceTest.call(s.window,"runPaintGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,PaintParameters.class,TreeItem.class,Geometry.class},s.item,"MM",subset,parameters,s.reference,s.referenceGeometry);
                var job=MainToolPersistenceTest.job(s);assertNotNull(job,"a valid subset must not be confused with the full source identity");
                if(change.equals("remove"))s.geometries().remove(s.reference);else s.geometries().put(s.reference,null);return job;
            });
            s.release.countDown();handle.completion().get(10,java.util.concurrent.TimeUnit.SECONDS);s.awaitUi();
            TerminalPanelTest.fx(()->{assertTrue(s.geometries().keySet().stream().allMatch(i->i==s.reference));return null;});
        }
    }
    @Test void nestedUnitCollectionsKeepFilledPolygonsAndIgnoreLines() {
        var polygon=(Polygon)F.toGeometry(new Envelope(0,10,0,8));
        var nested=F.createGeometryCollection(new Geometry[]{F.createGeometryCollection(new Geometry[]{polygon})});
        assertEquals(List.of(polygon),PaintToolPanel.polygonsOf(nested));
        assertEquals(List.of(polygon),PaintToolPanel.polygonsOf(F.createGeometryCollection(new Geometry[]{F.createMultiPolygon(new Polygon[]{polygon}),polygon.getBoundary(),F.createGeometryCollection()})));
    }
    @Test void replacingPaintCannotCancelTheNextToolsPlacementWhenItsOldSourceIsRemoved()throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(()->{
                MainToolPersistenceTest.call(s.window,"openPaintTool",new Class<?>[]{});
                var oldPanel=MainToolPersistenceTest.panel(s);
                MainToolPersistenceTest.call(s.window,"openToolPanel",new Class<?>[]{String.class,Node.class},"Other",new Label("other"));
                var plot=(PlotAreaView)MainToolPersistenceTest.field(s.window,"plotAreaView");
                plot.beginAreaRectanglePlacement(new PlotAreaView.PlacementHandler(){public void onAnchorChosen(double x,double y){}public void onCommit(double x,double y){}public void onCancel(){}});
                assertTrue(plot.isPlacementActive());
                MainToolPersistenceTest.call(s.window,"removeSelectionFromProject",new Class<?>[]{List.class},List.of(s.item));
                assertTrue(plot.isPlacementActive(),"the detached Paint panel must not cancel another tool's gesture");
                assertNotSame(oldPanel,((Tab)MainToolPersistenceTest.field(s.window,"toolTab")).getContent());return null;
            });
        }
    }
}
