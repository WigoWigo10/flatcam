package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.geometry.Rectangle2D;
import javafx.stage.*;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

class WindowsDpiTest {
    @Test void recordedRestoredDragUsesNativePixelsAndDoesNotMaximize(){
        var result=WindowsDpi.fit(new WindowsDpi.Rect(-617,892,3826,1588),new WindowsDpi.Rect(0,0,2880,1800),
            new Rectangle2D(0,0,1440,900),new Rectangle2D(0,0,1440,852),2,2,false);
        assertFalse(result.maximized());assertEquals(1424,result.width());assertEquals(794,result.height());
        assertEquals(8,result.x());assertEquals(50,result.y());
    }
    @Test void negativeMonitorOriginDoesNotMixAwtAndGlassCoordinates(){
        var result=WindowsDpi.fit(new WindowsDpi.Rect(-1700,600,900,650),new WindowsDpi.Rect(-1920,522,1920,1080),
            new Rectangle2D(-1920,-9,1920,1080),new Rectangle2D(-1920,-9,1920,1032),1,1,false);
        assertEquals(-1700,result.x());assertEquals(69,result.y());assertEquals(900,result.width());assertEquals(650,result.height());
    }
    @Test void fractionalDpiPreservesLogicalSizeAndMaximizedIntent(){
        var result=WindowsDpi.fit(new WindowsDpi.Rect(150,75,1200,750),new WindowsDpi.Rect(0,0,2400,1350),
            new Rectangle2D(0,0,1600,900),new Rectangle2D(0,0,1600,868),1.5,1.5,true);
        assertTrue(result.maximized());assertEquals(100,result.x());assertEquals(50,result.y());
        assertEquals(800,result.width());assertEquals(500,result.height());
    }
    @Test void invalidNativeGeometryCannotBecomeAPlacement(){
        for(double scale:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->WindowsDpi.fit(new WindowsDpi.Rect(0,0,100,100),new WindowsDpi.Rect(0,0,1920,1080),new Rectangle2D(0,0,1920,1080),new Rectangle2D(0,0,1920,1040),scale,1,false));
    }
    @Test @EnabledOnOs(OS.WINDOWS) void nativeBridgeReadsEachConnectedWindowWithoutUsingTheMouse()throws Exception{
        int count=TerminalPanelTest.fx(()->Screen.getScreens().size());
        for(int i=0;i<count;i++){
            final int index=i;
            Stage stage=TerminalPanelTest.fx(()->{
                javafx.application.Platform.setImplicitExit(false);
                var target=Screen.getScreens().get(index).getVisualBounds();var s=new Stage();
                s.setOpacity(0);s.setScene(new Scene(new Pane(),400,280));s.setX(target.getMinX()+45);s.setY(target.getMinY()+45);s.show();return s;
            });
            try{TerminalPanelTest.fx(()->{
                var sample=WindowsDpi.sample(stage);assertNotNull(sample,"Win32 query must be live, not silently fall back");
                var expected=Screen.getScreens().get(index);assertEquals(expected.getBounds(),sample.screen().getBounds());
                assertEquals(expected.getOutputScaleX(),sample.scaleX());assertTrue(sample.physical().valid());
                assertFalse(sample.deviceId().isBlank());return null;
            });}finally{TerminalPanelTest.fx(()->{stage.hide();return null;});}
        }
    }
}
