package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.*;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class ClearingRegionFeedbackTest {
    private static final GeometryFactory F=new GeometryFactory();
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void summaryFitsANarrowSidebarInEveryTheme(ThemeOption theme) throws Exception {
        var area=F.createPoint(new Coordinate()).buffer(.21,64).union(F.createPoint(new Coordinate(2,0)).buffer(.1,64));
        var result=NccGenerator.paint("MM",area,new PaintParameters(List.of(.4),.4,0,NccMethod.LINES,false,false,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        TerminalPanelTest.fx(()->{
            var form=new VBox();form.getStyleClass().add("tool-panel");form.setPadding(new javafx.geometry.Insets(8));
            var feedback=new ClearingRegionFeedback(form);form.getChildren().add(feedback);feedback.complete(result,g->{});
            var scroll=new ScrollPane(form);scroll.setFitToWidth(true);var scene=new javafx.scene.Scene(scroll,340,900);
            theme.applyTo(scene);scroll.applyCss();scroll.layout();
            assertTrue(feedback.isExpanded());assertTrue(form.getLayoutBounds().getWidth()<=340);
            for(var node:feedback.getContent().lookupAll(".button"))assertNotNull(node.getAccessibleHelp());
            var image=scroll.snapshot(null,null);var directory=java.nio.file.Path.of("target/clearing-region-review");java.nio.file.Files.createDirectories(directory);
            var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            javax.imageio.ImageIO.write(output,"png",directory.resolve(theme.name()+".png").toFile());return null;
        });
    }
    @Test void radialRetryNoticeIsVisibleExpandedAndHasHelp() throws Exception {
        var empty=F.createPolygon();var path=F.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(1,0)});
        var tool=new NccToolResult(.4,path,0,NccOperation.CLEAR,empty,new NccRegionDiagnostics(empty,empty,empty,true));
        var constructor=NccResult.class.getDeclaredConstructor(String.class,Geometry.class,Geometry.class,List.class);
        constructor.setAccessible(true);var result=constructor.newInstance("MM",path,empty,List.of(tool));
        TerminalPanelTest.fx(()->{
            var form=new VBox();var feedback=new ClearingRegionFeedback(form);form.getChildren().add(feedback);
            feedback.complete(result,g->{});assertTrue(feedback.isVisible());assertTrue(feedback.isExpanded());
            var notice=(Label)feedback.getContent().lookup("#spiral-radial-fallback-1");
            assertNotNull(notice);assertTrue(notice.isWrapText());assertNotNull(notice.getAccessibleHelp());
            assertInstanceOf(TooltipContent.class,notice.getProperties().get(FluidTooltips.CONTENT_KEY));
            feedback.started();assertFalse(feedback.isVisible());return null;
        });
    }
    @Test void regionButtonsShowTheRequestedHistoricalCategoryAndResetForANewRun() throws Exception {
        var area=F.createPoint(new Coordinate()).buffer(.21,64)
                .union(F.createPoint(new Coordinate(2,0)).buffer(.1,64));
        var result=NccGenerator.paint("MM",area,new PaintParameters(List.of(.4),.4,0,NccMethod.LINES,false,false,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        TerminalPanelTest.fx(()->{
            var form=new VBox();var feedback=new ClearingRegionFeedback(form);form.getChildren().add(feedback);
            var highlight=new Geometry[1];feedback.complete(result,g->highlight[0]=g);
            assertTrue(feedback.isVisible());var content=(VBox)feedback.getContent();
            var unreachable=(Button)content.lookup("#clearing-region-unreachable-1");
            var supplemented=(Button)content.lookup("#clearing-region-supplemented-1");
            var unserved=(Button)content.lookup("#clearing-region-unserved-1");
            assertFalse(unreachable.isDisabled());assertFalse(supplemented.isDisabled());assertTrue(unserved.isDisabled());
            assertInstanceOf(TooltipContent.class,supplemented.getProperties().get(FluidTooltips.CONTENT_KEY));
            assertTrue(unreachable.getAccessibleHelp().contains("\n"));
            supplemented.fire();assertSame(result.toolResults().getFirst().regionDiagnostics().supplementedArea(),highlight[0]);
            unreachable.fire();assertSame(result.toolResults().getFirst().regionDiagnostics().unreachableArea(),highlight[0]);
            ((Button)content.lookup("#clearing-regions-hide")).fire();assertNull(highlight[0]);
            feedback.started();assertFalse(feedback.isVisible());assertNull(feedback.getContent());return null;
        });
    }
    @ParameterizedTest @ValueSource(strings={"NCC","Paint"})
    void realGenerationPublishesTheSummaryAndToolCleanupClearsItsHighlight(String operation) throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(()->{
                if(operation.equals("NCC")) {
                    call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,GerberImage.class},s.item,s.image);
                    var form=panel(s).getContent();
                    ((TableView<?>)form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                    ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.LINES);
                    ((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("1");
                    ((Button)form.lookup("#ncc-generate")).fire();
                } else {
                    call(s.window,"openPaintTool",new Class<?>[]{});
                    var parameters=new PaintParameters(List.of(.4),.4,0,NccMethod.LINES,false,false,NccOrder.NONE,false);
                    call(s.window,"runPaintGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,PaintParameters.class},s.item,"MM",s.image.solidGeometry(),parameters);
                }
                assertFalse(ClearingRegionFeedback.of(panel(s)).isVisible());return null;
            });
            finish(s,TerminalPanelTest.fx(()->job(s)));
            TerminalPanelTest.fx(()->{
                assertTrue(ClearingRegionFeedback.of(panel(s)).isVisible());
                var plot=(PlotAreaView)field(s.window,"plotAreaView");
                plot.setCoverageResidual(s.referenceGeometry);
                call(s.window,"clearToolOverlays",new Class<?>[]{});
                var geometry=PlotAreaView.class.getDeclaredField("editorHighlightGeometry");geometry.setAccessible(true);
                assertNull(geometry.get(plot));return null;
            });
        }
    }
}
