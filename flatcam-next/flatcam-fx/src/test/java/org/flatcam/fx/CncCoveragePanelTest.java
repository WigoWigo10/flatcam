package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class CncCoveragePanelTest {
    @Test void selectionSwitchesBetweenIndividualAccumulatedAndFinalWithoutRecalculating() throws Exception {
        var factory = new GeometryFactory(); var area = factory.toGeometry(new Envelope(0,10,0,2));
        var tools = List.of(new ToolGeometry(1,factory.createLineString(new Coordinate[]{new Coordinate(0,.5),new Coordinate(10,.5)})),
                new ToolGeometry(1,factory.createLineString(new Coordinate[]{new Coordinate(0,1.5),new Coordinate(10,1.5)})));
        var target = new MachiningTarget("NCC","MM",area,Map.of(0,area,1,area),MachiningTarget.signature(tools,CancellationToken.none()));
        var p = new GeometryGCodeParameters(3,.1,false,1,100,10000,true);
        var result = MachiningCoverage.calculate(target,"MM",tools,p,Map.of(),CancellationToken.none(),fraction -> {});
        TerminalPanelTest.fx(() -> {
            var shown = new AtomicReference<Geometry>();
            var panel = new CncCoveragePanel("NCC","MM",tools,shown::set,() -> {},() -> {});
            new Scene(panel,400,600); panel.applyCss(); panel.layout();
            assertTrue(panel.lookup("#cnc-coverage-back").isDisabled()); panel.complete(result);
            assertTrue(shown.get().isEmpty());
            ((ComboBox<?>) panel.lookup("#cnc-coverage-scope")).getSelectionModel().select(1);
            assertEquals(10,shown.get().getArea(),1e-8);
            ((Button) panel.lookup("#cnc-coverage-final")).fire(); assertTrue(shown.get().isEmpty());
            assertFalse(panel.lookup("#cnc-coverage-back").isDisabled()); assertTrue(panel.lookup("#cnc-coverage-cancel").isDisabled());
            return null;
        });
    }
    @Test void coverageButtonOnlyAppearsWithAStoredTargetAndDoesNotContinueExport() throws Exception {
        TerminalPanelTest.fx(() -> {
            var report = CncPreflight.geometry("NCC","MM",CncPreflightTest.geometry());
            var dialog = new CncReviewDialog(null,ThemeOption.CLASSIC_DARK,report,true);
            try {
                ((Button) dialog.getDialogPane().lookupButton(CncReviewDialog.COVERAGE)).fire();
                assertEquals(new CncReviewDialog.Decision(false,new CncPreflight.Target(-1,"__coverage")),dialog.getResult());
            } finally { dialog.close(); }
            var old = new CncReviewDialog(null,ThemeOption.ICE_LIGHT,report);
            try { assertFalse(old.getDialogPane().getButtonTypes().contains(CncReviewDialog.COVERAGE)); }
            finally { old.close(); }
            return null;
        });
    }
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void realOverlayUsesOrangeInEveryThemeAndDoesNotBecomeAProjectLayer(ThemeOption theme) throws Exception {
        var factory = new GeometryFactory(); var area = factory.toGeometry(new Envelope(0,10,0,2));
        var tools = List.of(new ToolGeometry(1,factory.createLineString(new Coordinate[]{new Coordinate(0,.5),new Coordinate(10,.5)})),
                new ToolGeometry(1,factory.createLineString(new Coordinate[]{new Coordinate(0,1.5),new Coordinate(10,1.5)})));
        var target = new MachiningTarget("NCC","MM",area,Map.of(0,area,1,area),MachiningTarget.signature(tools,CancellationToken.none()));
        var result = MachiningCoverage.calculate(target,"MM",tools,new GeometryGCodeParameters(3,.1,false,1,100,10000,true),Map.of(),CancellationToken.none(),fraction -> {});
        TerminalPanelTest.fx(() -> {
            var plot = new PlotAreaView();
            try {
                plot.applyTheme(theme);
                plot.putLayer("target",PlotAreaView.LayerCategory.GEOMETRY,area,javafx.scene.paint.Color.TRANSPARENT,javafx.scene.paint.Color.GRAY,true);
                var panel = new CncCoveragePanel("NCC","MM",tools,plot::setCoverageResidual,()->{},()->{});
                var scroll = new ScrollPane(panel); scroll.setFitToWidth(true); scroll.setPrefWidth(340); scroll.setMinWidth(340); scroll.setMaxWidth(340);
                var root = new javafx.scene.layout.HBox(scroll,plot); javafx.scene.layout.HBox.setHgrow(plot,javafx.scene.layout.Priority.ALWAYS);
                var scene = new Scene(root,1050,620); theme.applyTo(scene); root.resize(1050,620); root.applyCss(); root.layout();
                panel.complete(result); ((TableView<?>) panel.lookup("#cnc-coverage-tools")).getSelectionModel().selectFirst();
                plot.fitToBounds(area.getEnvelopeInternal());
                var image = root.snapshot(null,null); int orange = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 350; x < image.getWidth(); x++) {
                    var color = image.getPixelReader().getColor(x,y);
                    if(color.getRed() > .65 && color.getGreen() > .2 && color.getGreen() < .8 && color.getBlue() < .21) orange++;
                }
                assertTrue(orange > 100, "Residual overlay was not drawn in " + theme);
                assertEquals(1,plot.visibleSelectableLayers().size(),"The overlay is not a selectable project object");
                if(Boolean.getBoolean("flatcam.tests.snapshots")) {
                    var png = new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
                    javax.imageio.ImageIO.write(png,"png",java.nio.file.Path.of("target","cnc-coverage-"+theme+".png").toFile());
                }
                plot.setEditorHighlight(null,false);
            } finally { plot.dispose(); }
            return null;
        });
    }
}
