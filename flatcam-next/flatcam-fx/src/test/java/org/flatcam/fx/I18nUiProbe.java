package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.app.job.JobContext;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.locationtech.jts.geom.*;

/** Offscreen language audit, with an explicitly isolated preferences factory. */
public class I18nUiProbe {
    public static void main(String[] args) {
        int exit = 0;
        try {
            Locale original = Locale.getDefault();
            UiLanguage language = UiLanguage.fromTag(args[0]); Path output = Path.of(args[1]);
            assertTrue(System.getProperty("java.util.prefs.PreferencesFactory", "").contains("MemoryFactory"));
            assertEquals(UiLanguage.PT_BR, AppPreferences.loadLanguage());
            java.util.prefs.Preferences.userNodeForPackage(AppPreferences.class).put("uiLanguage","unsupported");
            assertEquals(UiLanguage.PT_BR, AppPreferences.loadLanguage());
            AppPreferences.saveLanguage(language); assertEquals(language, AppPreferences.loadLanguage());
            I18n.initialize(AppPreferences.loadLanguage());
            I18n.initialize(language == UiLanguage.EN ? UiLanguage.PT_BR : UiLanguage.EN);
            assertEquals(language, I18n.language(), "initialization must not live-switch the catalogs");
            new TooltipAuditTest().inventory("-"+language.tag, roots -> {
                if (language == UiLanguage.EN) {
                    var issues = new ArrayList<String>();
                    roots.forEach((context,root) -> inspect(context,root,issues));
                    try { Files.write(output.resolve("untranslated-en.txt"),issues); }
                    catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    assertEquals(List.of(), issues, "Portuguese captions still displayed in English");
                }
            });
            TerminalPanelTest.fx(() -> {
                for (ThemeOption theme : ThemeOption.values()) {
                    var view = PreferencesPaneTest.view(theme);
                    PreferencesPaneTest.select(view, "general");
                    @SuppressWarnings("unchecked") var languageChoice = (ComboBox<UiLanguage>)view.pane().lookup("#preferences-language");
                    languageChoice.setValue(language);
                    assertNotNull(view.pane().lookup("#preferences-language"));
                    for(String section:List.of("general","numbers")) {
                        PreferencesPaneTest.select(view,section);
                        view.pane().setPrefSize(1024,800); view.pane().resize(1024,800); view.pane().applyCss(); view.pane().layout();
                        var image = view.pane().snapshot(null,null);
                        int width=(int)image.getWidth(),height=(int)image.getHeight();
                        int[] pixels=new int[width*height];
                        image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
                        var buffered=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        buffered.setRGB(0,0,width,height,pixels,0,width);
                        javax.imageio.ImageIO.write(buffered,"png",output.resolve(language.tag+"-"+theme.name()
                                +(section.equals("general") ? "" : "-numbers")+".png").toFile());
                    }
                }
                var jobs = new JobExecutor(1); var window = new MainWindow(jobs);
                try {
                    var scene = window.createScene(); scene.getRoot().applyCss();
                    for (var node : scene.getRoot().lookupAll(".menu-bar")) if(node instanceof MenuBar bar)
                        for (var menu : bar.getMenus()) inspectMenu(menu);
                } finally { window.disposeViewport(); jobs.shutdown(); }
                var pane = new DialogPane(); pane.getButtonTypes().setAll(ButtonType.CANCEL,ButtonType.CLOSE);
                I18n.dialogButtons(pane);
                assertEquals(I18n.caption("Cancelar"), ((Button)pane.lookupButton(ButtonType.CANCEL)).getText());
                assertEquals(I18n.caption("Fechar"), ((Button)pane.lookupButton(ButtonType.CLOSE)).getText());
                assertEquals("copy32.png", ToolPanelIcons.buttonIcon(I18n.caption("Copiar")));
                assertNotNull(CommandHelpCatalog.help(I18n.caption("Salvar Projeto")));
                assertNotNull(PanelTooltips.help("Milling Tool", I18n.caption("Diâmetro da ferramenta (")+"MM):"));
                assertNotNull(PanelTooltips.help(I18n.caption("Editor Gerber"), I18n.caption("Adicionar abertura ")+"C"));
                return null;
            });
            var geometry = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(10.125,2.5)});
            var result = GeometryCncGeneration.generate("MM",List.of(new ToolGeometry(.5,geometry,ToolProfile.C2)),
                    new GeometryGCodeParameters(2,.2,true,.1,100,10000,false),Map.of(),Map.of(),GCodePreprocessor.GRBL_11_NO_M6,
                    output.resolve(language.tag+".nc"),new JobContext() {
                        public boolean isCancelled() { return false; }
                        public void reportProgress(double fraction,String text) { }
                    }, () -> {});
            Files.writeString(output.resolve(language.tag+".csv"),CncStepCsv.toCsv(result.preview().stats().steps(),"MM"));
            assertEquals(original,Locale.getDefault(),"UI language must not change process-wide numeric locale");
            System.out.println("PASS " + language.tag + ": catalog, preferences, panels, tooltips, menus, four themes and machine output");
        } catch(Throwable failure) { failure.printStackTrace(); exit=1; }
        finally { Platform.exit(); }
        System.exit(exit);
    }

    private static void inspectMenu(MenuItem item) {
        if (item instanceof Menu menu) menu.getItems().forEach(I18nUiProbe::inspectMenu);
        if (item instanceof SeparatorMenuItem) return;
        assertNotNull(item.getText());
        if (I18n.language() == UiLanguage.EN) {
            var problems = new ArrayList<String>(); check("Menu",item.getText(),problems);
            assertEquals(List.of(),problems);
        }
    }
    private static void check(String context,String text,List<String> issues) {
        if (text == null || text.isBlank()) return;
        var pt=I18n.catalog(UiLanguage.PT_BR); var en=I18n.catalog(UiLanguage.EN);
        for (var entry:pt.entrySet()) if(entry.getValue().equals(text) && !text.equals(en.get(entry.getKey()))) {
            issues.add(context+" | "+text); break;
        }
    }
    private static void inspect(String context,Node node,List<String> issues) {
        if(node==null)return;
        if(node instanceof Labeled label) check(context,label.getText(),issues);
        if(node instanceof TextInputControl input) check(context,input.getPromptText(),issues);
        if(node.getProperties().get(FluidTooltips.TEXT_KEY) instanceof String text) check(context,text,issues);
        if(node instanceof Control control && control.getTooltip()!=null)check(context,control.getTooltip().getText(),issues);
        if(node instanceof TableView<?> table) for(var column:table.getColumns())check(context,column.getText(),issues);
        if(node instanceof TitledPane pane) { check(context,pane.getText(),issues); inspect(context,pane.getContent(),issues); }
        else if(node instanceof ScrollPane pane) inspect(context,pane.getContent(),issues);
        else if(node instanceof Accordion panes) panes.getPanes().forEach(p -> inspect(context,p,issues));
        else if(node instanceof TabPane panes) panes.getTabs().forEach(tab -> { check(context,tab.getText(),issues); inspect(context,tab.getContent(),issues); });
        else if(node instanceof SplitPane panes)panes.getItems().forEach(p -> inspect(context,p,issues));
        else if(node instanceof ToolBar toolbar)toolbar.getItems().forEach(p -> inspect(context,p,issues));
        else if(node instanceof javafx.scene.layout.Pane pane)List.copyOf(pane.getChildren()).forEach(p -> inspect(context,p,issues));
    }
}
