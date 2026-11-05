package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import org.flatcam.app.job.*;
import org.flatcam.app.project.CncJobDefaults;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolProfile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class InternalCutoutUiTest {
    static final GeometryFactory F=new GeometryFactory();
    @BeforeEach @AfterEach void store() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    static InternalCutoutToolPanel.Settings settings() {
        return new InternalCutoutToolPanel.Settings("1","0","-1.6",true,".4",
                new GeometryGCodeParameters(3,1.6,true,.4,120,10000,false),ToolProfile.C1,CncJobDefaults.EMPTY);
    }
    static Node auditPanel() {
        return auditPanel(ThemeOption.CLASSIC_LIGHT);
    }
    private static Node auditPanel(ThemeOption theme) {
        return new InternalCutoutToolPanel("MM","Outline_area",settings(),new InternalCutoutToolPanel.Host() {
            public void load(Consumer<List<Geometry>> done,Consumer<String> failure) { done.accept(List.of()); }
            public void preview(List<Geometry> a,double d,double m,Consumer<InternalCutoutToolPanel.PreviewInfo> done,Consumer<InternalCutoutToolPanel.PreviewIssue> failure) { done.accept(new InternalCutoutToolPanel.PreviewInfo(0)); }
            public boolean pick(List<Geometry> a,IntConsumer selected,Consumer<String> failure,Runnable finished) { return false; }
            public boolean draw(InternalCutoutToolPanel.Shape s,Consumer<Geometry> accepted,Consumer<String> failure,Runnable cancelled) { return false; }
            public void stop() { } public void clear() { }
        },(name,size)->Icons.fromResource((theme.isDark()?"dark/":"")+name,size),result->{},()->{});
    }

    private static class Session implements AutoCloseable {
        final JobExecutor jobs=new JobExecutor(1);
        final AtomicBoolean available=new AtomicBoolean(true);
        final Geometry board=F.toGeometry(new Envelope(20,80,20,60)).difference(F.toGeometry(new Envelope(30,40,30,40)));
        final PlotAreaView plot;
        final InternalCutoutController controller;
        final InternalCutoutToolPanel panel;
        final List<CutoutToolPanel.Result> generated=new ArrayList<>();
        Session() throws Exception { this(ThemeOption.CLASSIC_LIGHT); }
        Session(ThemeOption theme) throws Exception {
            plot=TerminalPanelTest.fx(()->{var p=new PlotAreaView();p.resize(800,500);p.layout();p.setGridSnap(false,1,1);return p;});
            controller=new InternalCutoutController(plot,jobs,"MM",board,available::get);
            panel=TerminalPanelTest.fx(()->new InternalCutoutToolPanel("MM","Outline_area",settings(),controller,
                    (name,size)->Icons.fromResource((theme.isDark()?"dark/":"")+name,size),generated::add,controller::close));
            await(()->table().getItems().size()==1);
        }
        TableView<?> table() { return (TableView<?>)panel.lookup("#cutout-internal-openings"); }
        void button(String id) throws Exception { TerminalPanelTest.fx(()->{((Button)panel.lookup("#"+id)).fire();return null;}); }
        void await(BooleanSupplier condition) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<until) { if(TerminalPanelTest.fx(condition::getAsBoolean))return;Thread.sleep(10); }
            fail("Internal cutout UI did not reach expected state: "+TerminalPanelTest.fx(()->((Label)panel.lookup("#cutout-internal-error")).getText()));
        }
        void preview() throws Exception { await(()->{try{var p=(InternalCutoutPreview)reflect(plot,"internalCutoutPreview");return p!=null&&p.paths()!=null&&reflect(controller,"job")==null;}catch(Exception e){throw new RuntimeException(e);}}); }
        void click(double x,double y,MouseButton button) throws Exception {
            TerminalPanelTest.fx(()->{clickFx(x,y,button);return null;});
        }
        void clickFx(double x,double y,MouseButton button) {
                double sx=(x-50)*3+(plot.getWidth()-44)/2+44;
                double sy=(plot.getHeight()-20)/2-(y-40)*3+20;
                for(var type:List.of(MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED))
                    plot.fireEvent(new MouseEvent(type,sx,sy,sx,sy,button,1,false,false,false,false,
                            type==MouseEvent.MOUSE_PRESSED&&button==MouseButton.PRIMARY,false,
                            type==MouseEvent.MOUSE_PRESSED&&button==MouseButton.SECONDARY,false,false,true,null));
        }
        public void close() throws Exception {TerminalPanelTest.fx(()->{controller.close();plot.dispose();return null;});jobs.shutdown();}
    }

    @Test void plotDrawingPickingAndDeleteOnlyChangeTheDraftAndGenerationUsesOnlySelectedOpenings() throws Exception {
        try(var s=new Session()) {
            String source=s.board.toText();
            s.button("cutout-internal-draw-circle");s.click(55,45,MouseButton.PRIMARY);s.click(60,45,MouseButton.PRIMARY);
            s.await(()->s.table().getItems().size()==2);s.preview();
            s.button("cutout-internal-draw-rectangle");s.click(45,25,MouseButton.PRIMARY);s.click(50,30,MouseButton.PRIMARY);
            s.await(()->s.table().getItems().size()==3);s.preview();
            s.button("cutout-internal-draw-polygon");
            s.click(60,25,MouseButton.PRIMARY);s.click(70,25,MouseButton.PRIMARY);s.click(65,35,MouseButton.PRIMARY);
            s.click(65,35,MouseButton.SECONDARY);s.await(()->s.table().getItems().size()==4);s.preview();
            s.button("cutout-internal-all");s.preview();
            s.button("cutout-internal-pick");s.click(35,35,MouseButton.PRIMARY);
            s.await(()->s.table().getSelectionModel().getSelectedItems().size()==3);
            s.click(35,35,MouseButton.PRIMARY);s.await(()->s.table().getSelectionModel().getSelectedItems().size()==4);
            s.click(35,35,MouseButton.SECONDARY);
            s.preview();
            s.button("cutout-internal-generate");
            TerminalPanelTest.fx(()->{
                assertEquals(1,s.generated.size());var result=s.generated.getFirst();assertEquals(4,result.internalAreas().size());
                var paths=InternalCutoutGenerator.generate("MM",s.board,result.internalAreas(),result.cutoutParams().toolDiameter(),
                        result.cutoutParams().margin(),()->false);
                assertEquals(4,paths.geometry().getNumGeometries());assertTrue(paths.gapGeometry().isEmpty());
                assertEquals(source,s.board.toText());assertEquals(1.6,result.machining().cutDepth());
                s.table().getSelectionModel().clearAndSelect(0);
                s.table().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.DELETE,false,false,false,false));
                assertEquals(4,s.table().getItems().size(),"detected opening is never removed from source");
                s.table().getSelectionModel().clearAndSelect(1);
                s.table().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.DELETE,false,false,false,false));
                assertEquals(3,s.table().getItems().size());assertTrue(s.table().getSelectionModel().isEmpty());
                return null;
            });
        }
    }

    @Test void invalidParametersAndStaleOrClosedWorkersDoNotPublishPreview() throws Exception {
        try(var s=new Session()) {
            TerminalPanelTest.fx(()->{
                ((TextField)s.panel.lookup("#cutout-internal-diameter")).setText("-1");
                s.table().getSelectionModel().select(0);return null;
            });
            s.await(()->!((Label)s.panel.lookup("#cutout-internal-error")).getText().isBlank());
            TerminalPanelTest.fx(()->{
                assertNull(reflect(s.plot,"internalCutoutPreview"));assertTrue(s.generated.isEmpty());
                s.controller.preview(InternalCutoutGenerator.openings(s.board,()->false),1,0,info->fail("stale preview"),message->{});
                var h=(JobHandle<?>)reflect(s.controller,"job");h.completion().get(10,TimeUnit.SECONDS);
                s.available.set(false);return null;
            });
            s.await(()->{try{return reflect(s.controller,"job")==null;}catch(Exception e){throw new RuntimeException(e);}});
            TerminalPanelTest.fx(()->{
                assertNull(reflect(s.plot,"internalCutoutPreview"));
                s.available.set(true);
                var opening=InternalCutoutGenerator.openings(s.board,()->false).getFirst();
                s.controller.preview(List.of(opening),1,0,info->fail("closed preview"),message->{});
                var h=(JobHandle<?>)reflect(s.controller,"job");h.completion().get(10,TimeUnit.SECONDS);s.controller.close();return null;
            });
            TerminalPanelTest.fx(()->{assertNull(reflect(s.plot,"internalCutoutPreview"));return null;});
        }
    }

    @Test void stoppingPickDiscardsACompletedHitWaitingForTheFxThread() throws Exception {
        try(var s=new Session()) {
            s.button("cutout-internal-pick");
            TerminalPanelTest.fx(()->{
                s.clickFx(35,35,MouseButton.PRIMARY);
                var hit=(JobHandle<?>)reflect(s.controller,"job");
                hit.completion().get(10,TimeUnit.SECONDS);
                s.controller.stop();
                return null;
            });
            s.await(()->{try{return reflect(s.controller,"job")==null;}catch(Exception e){throw new RuntimeException(e);}});
            TerminalPanelTest.fx(()->{
                assertTrue(s.table().getSelectionModel().isEmpty(),"an ended gesture cannot change selection later");
                assertFalse(s.plot.isPlacementActive()); return null;
            });
        }
    }

    @Test void previewShowsOnlyTheCutterStripAndBlocksGenerationUntilValidationAndDepthAreValid() throws Exception {
        try(var s=new Session()) {
            TerminalPanelTest.fx(()->{
                assertTrue(s.panel.lookup("#cutout-internal-generate").isDisabled());
                s.table().getSelectionModel().select(0);
                assertTrue(s.panel.lookup("#cutout-internal-generate").isDisabled(),"debounce and worker are pending");return null;
            });
            s.preview();
            TerminalPanelTest.fx(()->{
                var preview=(InternalCutoutPreview)reflect(s.plot,"internalCutoutPreview");
                assertEquals(36,preview.paths().getLength(),1e-8);
                assertEquals(35.7854,preview.swept().getArea(),.02);
                assertFalse(preview.swept().covers(F.createPoint(new Coordinate(35,35))),"waste interior is not shown as pocketed");
                assertTrue(preview.swept().covers(F.createPoint(new Coordinate(30.2,35))));
                assertFalse(s.panel.lookup("#cutout-internal-generate").isDisabled());
                assertTrue(((Label)s.panel.lookup("#cutout-internal-summary")).getText().contains("-1.6"));
                assertTrue(((Label)s.panel.lookup("#cutout-internal-length")).getText().contains("36"));
                ((TextField)s.panel.lookup("#cutout-internal-step")).setText("0");
                assertTrue(s.panel.lookup("#cutout-internal-generate").isDisabled());
                assertTrue(s.panel.lookup("#cutout-internal-step").getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("cutout-invalid")));
                ((TextField)s.panel.lookup("#cutout-internal-step")).setText(".4");
                assertFalse(s.panel.lookup("#cutout-internal-generate").isDisabled());return null;
            });
            s.button("cutout-internal-frame");
            TerminalPanelTest.fx(()->{assertTrue((double)reflect(s.plot,"scale")>3);return null;});
        }
    }

    @Test void invalidOpeningIsIdentifiedInTheTableAndPlotWithoutAllowingGeneration() throws Exception {
        try(var s=new Session()) {
            s.button("cutout-internal-draw-circle");s.click(90,45,MouseButton.PRIMARY);s.click(93,45,MouseButton.PRIMARY);
            s.await(()->((Label)s.panel.lookup("#cutout-internal-error")).getText().contains("#2"));
            TerminalPanelTest.fx(()->{
                assertTrue(s.panel.lookup("#cutout-internal-generate").isDisabled());
                var preview=(InternalCutoutPreview)reflect(s.plot,"internalCutoutPreview");
                assertNotNull(preview.invalid());assertNull(preview.paths());
                assertEquals(90,preview.invalid().getCentroid().getX(),1e-7);
                s.table().getSelectionModel().clearAndSelect(0);return null;
            });
            s.preview();
            TerminalPanelTest.fx(()->{
                assertTrue(((Label)s.panel.lookup("#cutout-internal-error")).getText().isEmpty());
                assertFalse(s.panel.lookup("#cutout-internal-generate").isDisabled());return null;
            });
        }
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void newPanelFitsTheSidebarAndKeepsHelpInEveryTheme(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(()->{
            var panel=auditPanel(theme);var scroll=new ScrollPane(panel);scroll.setFitToWidth(true);
            var scene=new Scene(scroll,390,850);theme.applyTo(scene);scroll.applyCss();scroll.layout();
            assertTrue(panel.getLayoutBounds().getWidth()<=390);
            for(var node:panel.lookupAll(".button"))assertNotNull(node.getAccessibleHelp());
            for(var node:panel.lookupAll(".text-field"))assertNotNull(node.getAccessibleHelp());
            var image=scroll.snapshot(null,null);Path folder=Path.of("target/internal-cutout-review");Files.createDirectories(folder);
            var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            javax.imageio.ImageIO.write(output,"png",folder.resolve(theme.name()+".png").toFile());return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void selectedCutoutsRenderWithLegendAndCutterFootprintInEveryTheme(ThemeOption theme) throws Exception {
        try(var s=new Session(theme)) {
            s.button("cutout-internal-draw-circle");s.click(55,45,MouseButton.PRIMARY);s.click(60,45,MouseButton.PRIMARY);
            s.await(()->s.table().getItems().size()==2);s.button("cutout-internal-all");s.preview();
            var root=TerminalPanelTest.fx(()->{
                s.panel.getStyleClass().add("tool-panel");
                var sidebar=new ScrollPane(s.panel);sidebar.setFitToWidth(true);sidebar.setPrefWidth(390);sidebar.setMinWidth(390);sidebar.setMaxWidth(390);
                var plotFrame=new javafx.scene.layout.BorderPane(s.plot);plotFrame.setTop(new Label("Plot Area"));
                var pane=new javafx.scene.layout.HBox(sidebar,plotFrame);javafx.scene.layout.HBox.setHgrow(plotFrame,javafx.scene.layout.Priority.ALWAYS);
                var scene=new Scene(pane,1260,1000);theme.applyTo(scene);s.plot.applyTheme(theme);
                pane.applyCss();pane.layout();
                s.plot.putLayer("board",PlotAreaView.LayerCategory.GEOMETRY,s.board,
                        javafx.scene.paint.Color.web(theme.isDark()?"#7E89962C":"#8DAEB52E"),
                        javafx.scene.paint.Color.web(theme.isDark()?"#9AA7B3":"#607985"),false);
                s.plot.fitToBounds(s.board.getEnvelopeInternal());return pane;
            });
            s.await(s.plot::renderReady);
            TerminalPanelTest.fx(()->{
                assertFalse(s.panel.lookup("#cutout-internal-generate").isDisabled());
                assertTrue(s.panel.getWidth()<=390);assertNotNull(s.panel.lookup("#cutout-internal-legend"));
                save(root,Path.of("target/internal-cutout-ux-review/"+theme.name()+".png"));
                var tip=(TooltipContent)s.panel.lookup("#cutout-internal-margin").getProperties().get(FluidTooltips.CONTENT_KEY);
                assertTrue(tip.spans().stream().anyMatch(span->span.style()==TooltipContent.Style.ACCENT));
                assertTrue(tip.spans().stream().anyMatch(span->span.style()==TooltipContent.Style.NOTE));
                var flow=new javafx.scene.text.TextFlow();flow.setPrefWidth(360);flow.setLineSpacing(3);tip.renderInto(flow,theme);
                var heading=new Label(I18n.text("cam.recipe.margin"));heading.setStyle("-fx-font-weight: bold;");
                var help=new javafx.scene.layout.VBox(10,heading,flow);help.setPadding(new javafx.geometry.Insets(14));
                new Scene(help,390,350);theme.applyTo(help.getScene());help.applyCss();help.layout();
                save(help,Path.of("target/internal-cutout-ux-review/"+theme.name()+"-tooltip.png"));
                return null;
            });
        }
    }
    private static void save(Node node,Path path)throws Exception {
        Files.createDirectories(path.getParent());var image=node.snapshot(null,null);
        var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[output.getWidth()*output.getHeight()];
        image.getPixelReader().getPixels(0,0,output.getWidth(),output.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,output.getWidth());
        output.setRGB(0,0,output.getWidth(),output.getHeight(),pixels,0,output.getWidth());
        javax.imageio.ImageIO.write(output,"png",path.toFile());
    }
    private static Object reflect(Object value,String name)throws Exception {var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
}
