package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import java.util.*;
import javafx.scene.control.*;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.gcode.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class RegionLinesUiTest {
    @Test void paintOffersTheModeAndPublishesPreservedCncOrder() throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(()->{
                ((TreeView<String>)field(s.window,"projectTree")).getSelectionModel().select(s.item);
                call(s.window,"openPaintTool",new Class<?>[]{});
                var form=panel(s).getContent();var method=(ComboBox<NccMethod>)form.lookup("#paint-method");
                assertTrue(method.getItems().contains(NccMethod.REGION_LINES));method.setValue(NccMethod.REGION_LINES);
                var angle=(RasterAngleInput)form.lookup("#paint-angle");assertFalse(angle.isDisabled());angle.draft(new RasterAngleInput.Draft(false,"90"));
                ((Button)form.lookup("#paint-generate")).fire();return null;
            });
            finish(s,job(s));
            TerminalPanelTest.fx(()->{
                var entries=(Map<?,?>)field(s.window,"geometryByItem");
                assertTrue(entries.values().stream().anyMatch(entry->{
                    try {var getter=entry.getClass().getDeclaredMethod("cncDefaults");getter.setAccessible(true);var p=(GeometryGCodeParameters)getter.invoke(entry);
                        return p!=null&&p.jobOptions().routing().mode()==CncRouting.Mode.SOURCE;
                    }catch(Exception e){throw new AssertionError(e);}
                }));return null;
            });
        }
    }
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void experimentalChoiceFitsTheSidebarInEveryTheme(ThemeOption theme)throws Exception {
        TerminalPanelTest.fx(()->{
            var method=new ComboBox<NccMethod>();method.getItems().setAll(NccMethod.values());RegionLinesUi.configure(method);method.setValue(NccMethod.REGION_LINES);
            var angle=new RasterAngleInput("regions",method.valueProperty());angle.draft(new RasterAngleInput.Draft(false,"37,5"));
            var note=new Label(I18n.text("cam.regionLines.help"));note.setWrapText(true);note.setMinWidth(0);
            var root=new javafx.scene.layout.VBox(8,method,angle,note);root.setPadding(new javafx.geometry.Insets(12));
            var scene=new javafx.scene.Scene(root,310,420);theme.applyTo(scene);root.applyCss();root.layout();
            assertFalse(angle.isDisabled());assertTrue(method.getBoundsInParent().getMaxX()<=310);assertTrue(note.getBoundsInParent().getMaxX()<=310);
            java.nio.file.Path directory=java.nio.file.Path.of("target/region-lines-review");java.nio.file.Files.createDirectories(directory);
            var image=root.snapshot(null,null);var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            javax.imageio.ImageIO.write(output,"png",directory.resolve(theme.name()+".png").toFile());return null;
        });
    }
    @Test void nccKeepsTheExperimentalMethodAngleAndCncOrderAfterGeneration() throws Exception {
        try(var s=new MainCamFlowTest.Session("MM")) {
            TerminalPanelTest.fx(()->{
                call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,org.flatcam.cam.gerber.GerberImage.class},s.item,s.image);
                var form=panel(s).getContent();((TableView<?>)form.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                var method=(ComboBox<NccMethod>)form.lookup("#ncc-method");method.setValue(NccMethod.REGION_LINES);
                assertTrue(method.getAccessibleHelp().contains(I18n.text("cam.regionLines.help")));
                var angle=(RasterAngleInput)form.lookup("#ncc-angle");assertFalse(angle.isDisabled());angle.draft(new RasterAngleInput.Draft(false,"37,5"));
                ((Button)form.lookup("#ncc-generate")).fire();return null;
            });
            finish(s,job(s));
            TerminalPanelTest.fx(()->{
                var entries=(Map<?,?>)field(s.window,"geometryByItem");
                boolean found=false;
                for(var entry:entries.values()) {
                    var method=entry.getClass().getDeclaredMethod("cncDefaults");method.setAccessible(true);
                    var defaults=(GeometryGCodeParameters)method.invoke(entry);
                    if(defaults!=null){assertEquals(CncRouting.Mode.SOURCE,defaults.jobOptions().routing().mode());assertTrue(defaults.jobOptions().routing().preserveDirection());found=true;}
                }
                assertTrue(found);assertEquals(NccMethod.REGION_LINES,((ComboBox<?>)panel(s).getContent().lookup("#ncc-method")).getValue());return null;
            });
        }
    }
    @Test void routingSuggestionPreservesPositionsExclusionsAndExplicitChoices() {
        var p=new GeometryGCodeParameters(3,.1,false,0,300,10000,false).withJobOptions(
                new GeometryJobOptions(4.,5.,2.,3.,6.,7.,8.));
        var source=RegionLinesUi.sourceOrder("MM",2,p);
        assertEquals(p.jobOptions().startZ(),source.jobOptions().startZ());assertEquals(p.jobOptions().toolChangeX(),source.jobOptions().toolChangeX());
        assertEquals(CncRouting.Mode.SOURCE,source.jobOptions().routing().mode());
        var custom=p.withJobOptions(p.jobOptions().withRouting(new CncRouting(CncRouting.Mode.NEAREST,true,true,List.of(),0)));
        assertSame(custom,RegionLinesUi.sourceOrder("MM",2,custom));
    }
    @Test void labelsAndExperimentalHelpExistInAllFourLanguages() {
        for(var language:UiLanguage.values()) {
            var catalog=I18n.catalog(language);assertFalse(catalog.get("cam.regionLines.label").isBlank());
            assertTrue(catalog.get("cam.regionLines.help").contains("\n\n"));assertFalse(catalog.get("cam.regionLines.order").isBlank());
        }
    }
}
