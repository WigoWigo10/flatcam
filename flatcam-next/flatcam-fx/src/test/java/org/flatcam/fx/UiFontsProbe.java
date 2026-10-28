package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.flatcam.app.job.JobExecutor;

/** Isolated, offscreen startup: tests never change the user's registry or the shared test font state. */
public final class UiFontsProbe {
    public static void main(String[] args) {
        int exit=0;
        try {
            Path output=Path.of(args[1]);String mode=args[0];
            var selected=switch(mode) {
                case "large" -> new UiFonts.Settings(20,24,28,22);
                case "maximum" -> new UiFonts.Settings(40,40,40,40);
                case "hidden" -> new UiFonts.Settings(16,18,0,14);
                default -> UiFonts.Settings.DEFAULT;
            };
            assertTrue(System.getProperty("java.util.prefs.PreferencesFactory","").contains("MemoryFactory"));
            assertEquals(UiFonts.Settings.DEFAULT,AppPreferences.loadFontSettings());
            var prefs=java.util.prefs.Preferences.userNodeForPackage(AppPreferences.class);
            prefs.putInt("uiFontSize",0);prefs.putInt("axisFontSize",-1);prefs.put("textFontSize","bad");prefs.putInt("hudFontSize",41);
            assertEquals(UiFonts.Settings.DEFAULT,AppPreferences.loadFontSettings());
            AppPreferences.saveFontSettings(selected);assertEquals(selected,AppPreferences.loadFontSettings());
            UiFonts.initialize(AppPreferences.loadFontSettings());UiFonts.initialize(UiFonts.Settings.DEFAULT);
            assertEquals(selected,UiFonts.settings());
            AppPreferences.saveFontSettings(UiFonts.Settings.DEFAULT);assertEquals(selected,UiFonts.settings());
            I18n.initialize(mode.equals("default") ? UiLanguage.PT_BR : UiLanguage.EN);
            var jobs=new JobExecutor(1);
            try {
                TerminalPanelTest.fx(()-> {
                    var window=new MainWindow(jobs);
                    var code=new CodeEditor("G21\nG90\nG1 X1.2345 Y2.3456\n(Área de trabalho)\n",CodeSyntax.Language.MACHINE,true);
                    try {
                        Scene scene=window.createScene();Parent root=scene.getRoot();root.resize(1280,900);
                        var input=new TextField("1.23456789");var label=new Label("Interface Árvore 0123");var text=new TextArea("Área de trabalho 0123");
                        var plot=new PlotAreaView();plot.setPrefHeight(300);plot.setMinHeight(200);
                        plot.setGridVisible(false);plot.setAxisVisible(false);plot.setWorkspaceVisible(false);plot.setHudVisible(true);
                        VBox sample=new VBox(8,label,input,text,code,plot);sample.setPrefSize(1280,1000);code.setPrefHeight(300);text.setPrefHeight(100);
                        Scene samples=new Scene(sample,1280,1000);
                        for(ThemeOption theme:ThemeOption.values()) {
                            theme.applyTo(samples);sample.applyCss();sample.resize(1280,1000);sample.layout();
                            plot.applyTheme(theme);
                            assertEquals(selected.interfaceSize(),label.getFont().getSize(),.01);
                            assertEquals(selected.interfaceSize(),input.getFont().getSize(),.01);
                            assertEquals(selected.textSize(),text.getFont().getSize(),.01);
                            var hud=(Label)plot.lookup(".plot-coord-label");assertEquals(selected.hudSize(),hud.getFont().getSize(),.01);
                            var glyphs=code.area().lookupAll(".text").stream().filter(n->n instanceof Text t && !t.getText().isBlank()).map(n->(Text)n).toList();
                            assertFalse(glyphs.isEmpty(),"real virtualized code glyphs missing");
                            for(Text glyph:glyphs) assertEquals(selected.textSize(),glyph.getFont().getSize(),.01,glyph.getText());
                            var number=(Text)code.lookup(".code-line-number");assertNotNull(number);assertEquals(selected.textSize(),number.getFont().getSize(),.01);
                            assertEquals("G21\nG90\nG1 X1.2345 Y2.3456\n(Área de trabalho)\n",code.getText());
                            var camera=plot.cameraSnapshot();assertEquals(1.23456789,camera.worldX(camera.screenX(1.23456789)),1e-9);
                            assertEquals(-2.34567891,camera.worldY(camera.screenY(-2.34567891)),1e-9);
                            assertTrue(camera.insetY()>=Math.max(20,selected.axisSize()+7));
                            if(selected.axisSize()>13)assertTrue(camera.insetX()>44);
                            var canvas=(javafx.scene.canvas.Canvas)plot.getChildrenUnmodifiable().stream()
                                    .filter(n->n instanceof javafx.scene.canvas.Canvas).findFirst().orElseThrow();
                            var image=canvas.snapshot(null,null);var pixels=image.getPixelReader();int background=pixels.getArgb(0,0),ink=0;
                            for(int y=0;y<(int)image.getHeight();y++) for(int x=0;x<(int)image.getWidth();x++)
                                if((y<camera.insetY() || x<camera.insetX()) && pixels.getArgb(x,y)!=background) ink++;
                            if(selected.axisSize()==0)assertEquals(0,ink,"zero hides actual rendered ruler labels");
                            else assertTrue(ink>10,"axis labels must be rendered, not just configured");
                            save(sample,output.resolve(theme.name()+".png"));
                        }
                        ThemeOption.CLASSIC_LIGHT.applyTo(scene);root.applyCss();root.layout();
                        for(Node node:root.lookupAll(".tree-cell")) if(node instanceof TreeCell<?> cell && !cell.isEmpty()) {
                            assertEquals(selected.interfaceSize(),cell.getFont().getSize(),.01);
                            assertTrue(cell.getHeight()>=cell.getFont().getSize(),"tree rows must accommodate the chosen font");
                        }
                        var column=new TableColumn<String,String>("#");var diameter=new TableColumn<String,String>("Diameter");var profile=new TableColumn<String,String>("TT");
                        var table=new TableView<String>();table.getColumns().setAll(column,diameter,profile);CompactToolsTable.configure(table,column,diameter,profile);
                        assertTrue(table.getFixedCellSize()>=selected.interfaceSize()*1.4+6);
                        code.area().appendText("M5\n");code.area().undo();assertTrue(code.getText().endsWith("(Área de trabalho)\n"));
                        assertEquals("1.23456789",input.getText());
                        plot.dispose();
                    } finally {code.close();window.disposeViewport();}
                    return null;
                });
            } finally {jobs.shutdown();}
            System.out.println("PASS "+mode+": persisted fonts, controls, glyphs/gutter, HUD, rulers, themes, camera and unchanged text");
        } catch(Throwable error){error.printStackTrace();exit=1;}
        finally {Platform.exit();}
        System.exit(exit);
    }
    private static void save(Node node,Path path) throws Exception {
        var image=node.snapshot(null,null);int width=(int)image.getWidth(),height=(int)image.getHeight();
        int[] pixels=new int[width*height];image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        var buffered=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);buffered.setRGB(0,0,width,height,pixels,0,width);
        javax.imageio.ImageIO.write(buffered,"png",path.toFile());
    }
}
