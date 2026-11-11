package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

/** Opt-in artifact test uses saved public community data; no window or user preferences are changed. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named="flatcam.spiral.visual",matches="true")
class SpiralCommunityPreviewTest {
    @Test void capturesTheRealPaintPreviewOnTheBreadboardCommunityBoard()throws Exception {
        var inventory=new JSONObject(Files.readString(Path.of("../samples/community/validation/fx-import.json")));
        JSONObject board=null;for(Object b:inventory.getJSONArray("boards"))if(((JSONObject)b).getString("id").equals("breadboard-power-supply"))board=(JSONObject)b;
        assertNotNull(board);Geometry copper=new WKTReader().read(board.getJSONObject("preview").getString("wkt"));
        Geometry area=copper.getFactory().toGeometry(copper.getEnvelopeInternal()).difference(copper);
        try(var s=new MainCamFlowTest.Session("MM")) {
            var root=TerminalPanelTest.fx(()->{
                var plot=(PlotAreaView)field(s.window,"plotAreaView");plot.setLayerVisible(s.item,false);plot.setLayerVisible(s.reference,false);
                var gerber=(TreeItem<String>)call(s.window,"addGerberToProject",new Class<?>[]{String.class,Path.class,GerberImage.class},
                        "Breadboard Power Supply v2 — cobre",null,GerberImage.of("MM",Map.of(),copper,copper.getBoundary(),Map.of()));
                var item=(TreeItem<String>)call(s.window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},
                        "Área livre — Breadboard", "", "MM", area,false);plot.setLayerVisible(item,false);
                ((TreeView<String>)field(s.window,"projectTree")).getSelectionModel().select(item);
                call(s.window,"openPaintTool",new Class<?>[]{});var form=panel(s).getContent();
                ((TextField)form.lookup("#paint-diameters")).setText("0.4");((TextField)form.lookup("#paint-overlap")).setText("40");
                ((ComboBox<NccMethod>)form.lookup("#paint-method")).setValue(NccMethod.SPIRAL);
                ((CheckBox)form.lookup("#paint-connect")).setSelected(false);
                ((CheckBox)form.lookup("#paint-contour")).setSelected(true);
                var theme=MainWindow.class.getDeclaredField("currentTheme");theme.setAccessible(true);theme.set(s.window,ThemeOption.CLASSIC_DARK);
                var sidebar=(TabPane)field(s.window,"leftTabs");sidebar.setMinWidth(330);sidebar.setPrefWidth(330);sidebar.setMaxWidth(330);
                plot.setLayerColors(gerber,javafx.scene.paint.Color.web("#95CC62"),javafx.scene.paint.Color.web("#50753E"));
                var view=new BorderPane(plot);view.setLeft(sidebar);
                var title=new Label("Espiral — Breadboard Power Supply v2  |  Fresa 0,4 mm  |  Overlap 40%");title.setStyle("-fx-padding: 10; -fx-font-size: 16px; -fx-font-weight: bold;");view.setTop(title);
                var status=new Label();status.textProperty().bind(((Label)field(s.window,"statusLabel")).textProperty());
                var legend=new Label("Cobre original em verde • Trajetos em lilás • Limite de seleção: caixa do cobre • Linhas = centros da fresa");
                var footer=new VBox(4,status,legend);footer.setStyle("-fx-padding: 8;");view.setBottom(footer);
                new Scene(view,1536,960);ThemeOption.CLASSIC_DARK.applyTo(view.getScene());plot.applyTheme(ThemeOption.CLASSIC_DARK);view.resize(1536,960);view.applyCss();view.layout();plot.fitToLayer(gerber);panel(s).setVvalue(1);
                ((Button)form.lookup("#paint-preview-paths")).fire();return view;
            });
            finish(s,job(s));
            Path folder=Path.of("target/spiral-preview-review");Files.createDirectories(folder);
            awaitDrawing(s);
            TerminalPanelTest.fx(()->{save(root,folder.resolve("breadboard-spiral-preview.png"));return null;});
            var preview=(PlotAreaView.PreviewLayer)TerminalPanelTest.fx(()->SpiralPreviewUiTest.highlight(s));
            LineString longest=null;for(int i=0;i<preview.geometry().getNumGeometries();i++)if(preview.geometry().getGeometryN(i) instanceof LineString line&&!line.isClosed()&&(longest==null||line.getNumPoints()>longest.getNumPoints()))longest=line;
            assertNotNull(longest);Envelope detail=new Envelope();var coords=longest.getCoordinates();
            for(int i=0;i<Math.min(260,coords.length);i++)detail.expandToInclude(coords[i]);detail.expandBy(.7);
            TerminalPanelTest.fx(()->{((PlotAreaView)field(s.window,"plotAreaView")).fitToBounds(detail);return null;});awaitDrawing(s);
            TerminalPanelTest.fx(()->{save(root,folder.resolve("breadboard-spiral-detail.png"));return null;});
            assertNotNull(SpiralPreviewUiTest.highlight(s));
        }
    }
    private static void awaitDrawing(MainCamFlowTest.Session s)throws Exception {
        long deadline=System.nanoTime()+20_000_000_000L;
        while(System.nanoTime()<deadline) {
            boolean drawn=TerminalPanelTest.fx(()->{
                var plot=field(s.window,"plotAreaView");
                // No visible Stage in this artifact test: explicitly pulse the ready renderer.
                var draw=plot.getClass().getDeclaredMethod("drawEditorHighlight");draw.setAccessible(true);draw.invoke(plot);
                var f=plot.getClass().getDeclaredField("editorHighlightCanvas");f.setAccessible(true);var canvas=(Canvas)f.get(plot);
                var parameters=new SnapshotParameters();parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
                var image=canvas.snapshot(parameters,null);int nonempty=0;
                for(int y=40;y<image.getHeight();y+=3)for(int x=60;x<image.getWidth();x+=3)if((image.getPixelReader().getArgb(x,y)>>>24)!=0)nonempty++;
                return nonempty>80;
            });if(drawn)return;Thread.sleep(50);
        }
        fail("Preview drawing did not complete");
    }
    private static void save(Parent root,Path file)throws Exception {
        var image=root.snapshot(null,null);var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(output,"png",file.toFile());
    }
}
