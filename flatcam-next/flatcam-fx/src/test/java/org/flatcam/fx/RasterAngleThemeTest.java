package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.ncc.NccMethod;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class RasterAngleThemeTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void angleControlsFitANarrowSidebarAndHaveHelpInEveryTheme(ThemeOption theme)throws Exception {
        TerminalPanelTest.fx(()->{
            var method=new ComboBox<NccMethod>();method.getItems().setAll(NccMethod.values());method.setValue(NccMethod.LINES);
            var input=new RasterAngleInput("review",method.valueProperty());input.draft(new RasterAngleInput.Draft(false,"37,5"));
            var root=new VBox(8,method,input);root.setPadding(new Insets(12));
            var scene=new Scene(root,310,190);theme.applyTo(scene);root.applyCss();root.layout();
            assertFalse(input.degrees.isDisabled());assertNotNull(input.degrees.getAccessibleHelp());assertNotNull(input.automatic.getAccessibleHelp());
            for(var control:java.util.List.of(input.degrees,input.automatic)) {
                var bounds=root.sceneToLocal(control.localToScene(control.getBoundsInLocal()));
                assertTrue(bounds.getMinX()>=0);assertTrue(bounds.getMaxX()<=310,"must wrap rather than overflow the sidebar");
            }
            Path folder=Path.of("target/lines-angle-review");Files.createDirectories(folder);
            var image=root.snapshot(null,null);var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            javax.imageio.ImageIO.write(output,"png",folder.resolve(theme.name()+".png").toFile());return null;
        });
    }
}
