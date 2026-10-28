package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class PreferencesPaneTest {
    static class Host implements PreferencesPane.Host {
        ThemeOption theme = ThemeOption.ICE_DARK;
        AppPreferences.PlotStatusSettings settings = new AppPreferences.PlotStatusSettings(true, true, 1, 1, true, true, true, false);
        int themeWrites, plotWrites;
        boolean splash=true;
        int splashWrites;
        UiLanguage language = UiLanguage.PT_BR;
        int languageWrites;
        UiNumbers.Settings numbers = UiNumbers.Settings.DEFAULT;
        int numberWrites;
        @Override public UiNumbers.Settings numericSettings() { return numbers; }
        @Override public void applyNumericSettings(UiNumbers.Settings settings) { numbers=settings; numberWrites++; }
        @Override public UiLanguage language() { return language; }
        @Override public void applyLanguage(UiLanguage selected) { language = selected; languageWrites++; }
        @Override public boolean splashEnabled() { return splash; }
        @Override public void applySplash(boolean enabled) { splash=enabled; splashWrites++; }
        @Override public ThemeOption theme() { return theme; }
        @Override public AppPreferences.PlotStatusSettings plotSettings() { return settings; }
        @Override public void applyTheme(ThemeOption value) { theme = value; themeWrites++; }
        @Override public void applyPlot(AppPreferences.PlotStatusSettings value) { settings = value; plotWrites++; }
    }
    @BeforeEach @AfterEach void store() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }
    record View(Host host, PreferencesPane pane, Scene scene) { }
    static View view() {
        return view(ThemeOption.ICE_DARK);
    }
    static View view(ThemeOption theme) {
        Host host = new Host(); host.theme = theme;
        PreferencesPane pane = new PreferencesPane(host, name -> Icons.fromResource((theme.isDark() ? "dark/" : "") + name, 16));
        Scene scene = new Scene(pane, 1024, 800); theme.applyTo(scene); pane.applyCss(); pane.layout();
        return new View(host, pane, scene);
    }
    @SuppressWarnings("unchecked") static TreeView<PreferencesPane.Section> tree(View view) {
        return (TreeView<PreferencesPane.Section>) view.pane.lookup("#preferences-navigation");
    }
    static TreeItem<PreferencesPane.Section> find(TreeItem<PreferencesPane.Section> node, String id) {
        if (node.getValue() != null && node.getValue().id().equals(id)) return node;
        for (var child : node.getChildren()) { var match = find(child, id); if (match != null) return match; }
        return null;
    }
    static void select(View view, String id) {
        var item = find(tree(view).getRoot(), id); assertNotNull(item, id);
        tree(view).getSelectionModel().select(item); view.pane.applyCss(); view.pane.layout();
    }
    static TextField search(View view) { return (TextField)view.pane.lookup("#preferences-search"); }
    static TextField field(View view, String id) { return (TextField)view.pane.lookup("#" + id); }
    static Button button(View view, String id) { return (Button)view.pane.lookup("#" + id); }

    @Test void everyRegisteredDefaultIsRoutedExactlyOnceAndNoNavigationWritesSettings() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view();
            assertEquals(ToolDefaults.byTool().keySet(), view.pane.sections().stream().filter(s -> s.group() != null)
                    .map(PreferencesPane.Section::group).collect(java.util.stream.Collectors.toSet()));
            assertEquals(ToolDefaults.byTool().size() + 3, view.pane.sections().size());
            for (var section : view.pane.sections()) select(view, section.id());
            assertEquals(0, view.host.themeWrites); assertEquals(0, view.host.plotWrites);
            return null;
        });
    }

    @Test void languageDraftPersistsOnlyOnApplyAndRequiresRestart() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); select(view, "general");
            @SuppressWarnings("unchecked") var language = (ComboBox<UiLanguage>) view.pane.lookup("#preferences-language");
            language.setValue(UiLanguage.EN);
            select(view, "plot"); select(view, "general");
            assertSame(language, view.pane.lookup("#preferences-language"));
            assertEquals(0, view.host.languageWrites);
            button(view, "preferences-general-apply").fire();
            assertEquals(UiLanguage.EN, view.host.language);
            assertEquals(UiLanguage.PT_BR, I18n.language(), "language stays unchanged until next startup");
            button(view, "preferences-general-reset").fire();
            assertEquals(UiLanguage.PT_BR, language.getValue());
            assertEquals(UiLanguage.EN, view.host.language, "reset is still only a draft");
            button(view, "preferences-general-apply").fire();
            assertEquals(UiLanguage.PT_BR, view.host.language);
            return null;
        });
    }

    @Test void numberValidationIsAtomicDraftsSurviveAndResetDoesNotSave() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); select(view,"numbers");
            @SuppressWarnings("unchecked") var units = (ComboBox<String>)view.pane.lookup("#preferences-initial-units");
            units.setValue("IN"); field(view,"preferences-decimals-mm").setText("2");
            field(view,"preferences-decimals-in").setText("17");
            button(view,"preferences-numbers-apply").fire();
            assertEquals(0,view.host.numberWrites); assertEquals(UiNumbers.Settings.DEFAULT,view.host.numbers);
            select(view,"plot"); select(view,"numbers");
            assertEquals("17",field(view,"preferences-decimals-in").getText());
            for(String invalid : new String[]{"1","2.5","NaN","","2147483648"}) {
                field(view,"preferences-decimals-in").setText(invalid); button(view,"preferences-numbers-apply").fire();
                assertEquals(0,view.host.numberWrites);
            }
            field(view,"preferences-decimals-in").setText("8"); button(view,"preferences-numbers-apply").fire();
            assertEquals(new UiNumbers.Settings("IN",2,8),view.host.numbers);
            assertEquals(UiNumbers.Settings.DEFAULT,UiNumbers.settings(),"saving must not change current readouts");
            button(view,"preferences-numbers-reset").fire(); assertEquals(1,view.host.numberWrites);
            assertEquals("MM",units.getValue()); assertEquals("4",field(view,"preferences-decimals-in").getText());
            button(view,"preferences-numbers-apply").fire(); assertEquals(UiNumbers.Settings.DEFAULT,view.host.numbers);
            assertEquals(0,view.host.themeWrites); assertEquals(0,view.host.plotWrites);
            return null;
        });
    }

    @Test void navigatingFilteringAndUnitSwitchKeepIndependentUnsavedDrafts() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); select(view, "Cutout");
            field(view, "tool-default-cutout-tooldia").setText("3.175");
            select(view, "NCC"); field(view, "tool-default-ncc-overlap").setText("23");
            search(view).setText("cutout diametro"); view.pane.applyCss(); view.pane.layout();
            assertEquals("Cutout", tree(view).getSelectionModel().getSelectedItem().getValue().id());
            assertEquals("3.175", field(view, "tool-default-cutout-tooldia").getText());
            assertNull(view.pane.lookup("#tool-default-cutout-margin"));
            assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
            search(view).clear(); select(view, "Cutout");
            ((RadioButton)view.pane.lookupAll(".radio-button").stream().filter(n -> n instanceof RadioButton r && r.getText().equals("in")).findFirst().orElseThrow()).setSelected(true);
            view.pane.applyCss(); view.pane.layout();
            field(view, "tool-default-cutout-tooldia").setText("0.125");
            button(view, "tool-defaults-save").fire();
            assertEquals("3.175", ToolDefaults.text("cutout.tooldia", true));
            assertEquals("0.125", ToolDefaults.text("cutout.tooldia", false));
            assertEquals(ToolDefaults.setting("ncc.overlap").factory(true), ToolDefaults.text("ncc.overlap", true));
            select(view, "NCC"); assertEquals("23", field(view, "tool-default-ncc-overlap").getText());
            return null;
        });
    }

    @Test void saveReportsHiddenInvalidDraftAndPreservesItUntilCorrected() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); select(view, "Cutout");
            field(view, "tool-default-cutout-margin").setText("invalid");
            field(view, "tool-default-cutout-tooldia").setText("3.175");
            search(view).setText("cutout diametro"); view.pane.applyCss(); view.pane.layout();
            button(view, "tool-defaults-save").fire();
            assertEquals("3.175", ToolDefaults.text("cutout.tooldia", true));
            assertEquals("0.1", ToolDefaults.text("cutout.margin", true));
            assertTrue(((Label)view.pane.lookup("#tool-defaults-feedback")).getText().startsWith("Não salvo"));
            search(view).clear(); view.pane.applyCss(); view.pane.layout();
            assertEquals("invalid", field(view, "tool-default-cutout-margin").getText());
            field(view, "tool-default-cutout-margin").setText("0.25"); button(view, "tool-defaults-save").fire();
            assertEquals("0.25", ToolDefaults.text("cutout.margin", true));
            return null;
        });
    }

    @Test void scopedRestoreNeedsConfirmationAndDoesNotChangeOtherSectionsOrTheme() throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("cutout.tooldia", true, "4"); ToolDefaults.set("cutout.tooldia", false, "0.2");
            ToolDefaults.set("ncc.overlap", true, "23");
            View view = view(); select(view, "Cutout");
            button(view, "tool-defaults-reset").fire(); assertEquals("4", ToolDefaults.text("cutout.tooldia", true));
            button(view, "tool-defaults-reset-confirm").fire();
            assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
            assertEquals("0.094", ToolDefaults.text("cutout.tooldia", false));
            assertEquals("23", ToolDefaults.text("ncc.overlap", true)); assertEquals(0, view.host.themeWrites);
            return null;
        });
    }

    @Test void plotValidationIsAtomicAndFactoryPreparationDoesNotSave() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); select(view, "plot");
            ((CheckBox)view.pane.lookup("#preferences-grid-linked")).setSelected(false);
            field(view, "preferences-grid-x").setText("0,25"); field(view, "preferences-grid-y").setText("NaN");
            button(view, "preferences-plot-apply").fire(); assertEquals(0, view.host.plotWrites);
            field(view, "preferences-grid-y").setText("0.5"); button(view, "preferences-plot-apply").fire();
            assertEquals(1, view.host.plotWrites); assertEquals(.25, view.host.settings.gridX()); assertEquals(.5, view.host.settings.gridY());
            button(view, "preferences-plot-reset").fire(); assertEquals(1, view.host.plotWrites);
            button(view, "preferences-plot-apply").fire(); assertEquals(2, view.host.plotWrites); assertTrue(view.host.settings.gridLinked());
            assertEquals(1, view.host.settings.gridX()); assertFalse(view.host.settings.workspaceVisible());
            return null;
        });
    }

    @Test void themeResetIsOnlyADraftAndEmptySearchDoesNotDiscardIt() throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(); button(view, "preferences-general-reset").fire(); assertEquals(0, view.host.themeWrites);
            search(view).setText("no-such-setting-xyz"); assertTrue(tree(view).getRoot().getChildren().isEmpty());
            search(view).clear(); select(view, "general"); button(view, "preferences-general-apply").fire();
            assertEquals(1, view.host.themeWrites); assertEquals(ThemeOption.CLASSIC_LIGHT, view.host.theme);
            assertTrue(PreferencesSearch.matches("SOBREPOSICAO ncc", "NCC", "Sobreposição (%)"));
            assertFalse(PreferencesSearch.matches("ncc xyz", "NCC", "Sobreposição"));
            assertFalse(PreferencesSearch.matches("cor", "A validação ocorre ao salvar"));
            assertTrue(PreferencesSearch.matches("cor", "Cores do Gerber"));
            return null;
        });
    }

    @Test void startupSplashDraftPersistsOnlyWithGeneralApplyAndFactoryResetEnablesIt() throws Exception {
        TerminalPanelTest.fx(()-> {
            var view=view(); var splash=(CheckBox)view.pane.lookup("#preferences-splash");
            assertTrue(splash.isSelected()); splash.setSelected(false);
            select(view,"plot"); select(view,"general");
            assertFalse(splash.isSelected()); assertEquals(0,view.host.splashWrites);
            button(view,"preferences-general-apply").fire();
            assertFalse(view.host.splash); assertEquals(1,view.host.splashWrites);
            button(view,"preferences-general-reset").fire();
            assertTrue(splash.isSelected()); assertFalse(view.host.splash);
            button(view,"preferences-general-apply").fire(); assertTrue(view.host.splash);
            return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void colorPreferencesKeepAlphaAndValidateHiddenDraftsAcrossThemes(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            View view=view(theme); select(view,"Gerber: Exibição");
            assertFalse(view.pane.lookup("#tool-defaults-units").isVisible());
            String id="tool-default-display-gerber-fill";
            field(view,id+"-hex").setText("#12345680");
            assertEquals(128.0/255,((ColorPicker)view.pane.lookup("#"+id+"-picker")).getValue().getOpacity(),1e-7);
            search(view).setText("gerber cor"); view.pane.applyCss(); view.pane.layout();
            assertEquals("#12345680",field(view,id+"-hex").getText());
            button(view,"tool-defaults-save").fire(); assertEquals("#12345680",ToolDefaults.text("display.gerber.fill",true));
            view.pane.applyCss(); view.pane.layout();
            if(Boolean.getBoolean("flatcam.tests.snapshots")) saveSnapshot(view,"preferences-colors-"+theme);
            field(view,id+"-hex").setText("invalid"); button(view,"tool-defaults-save").fire();
            assertEquals("#12345680",ToolDefaults.text("display.gerber.fill",true));
            assertTrue(((Label)view.pane.lookup("#tool-defaults-feedback")).getText().startsWith("Não salvo"));
            search(view).clear(); select(view,"Excellon: Exibição");
            select(view,"Gerber: Exibição"); assertEquals("invalid",field(view,id+"-hex").getText());
            button(view,"tool-defaults-reset").fire(); button(view,"tool-defaults-reset-confirm").fire();
            assertEquals("#BBF268BF",ToolDefaults.text("display.gerber.fill",true));
            return null;
        });
    }

    private static void saveSnapshot(View view,String name) throws Exception {
        WritableImage image=view.pane.snapshot(null,null);
        int width=(int)image.getWidth(),height=(int)image.getHeight();
        var png=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[width*height];
        image.getPixelReader().getPixels(0,0,width,height,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,width);
        png.setRGB(0,0,width,height,pixels,0,width);
        javax.imageio.ImageIO.write(png,"png",Path.of("target/"+name+".png").toFile());
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void narrowPreferencesKeepControlsInsideThePageAcrossThemes(ThemeOption theme) throws Exception {
        TerminalPanelTest.fx(() -> {
            View view = view(theme); select(view, "Rules Check");
            view.pane.resize(680, 720); view.pane.applyCss(); view.pane.layout();
            var editor = field(view, "tool-default-rules-copper_to_outline"); assertNotNull(editor);
            assertTrue(editor.getWidth() > 30); assertTrue(editor.localToScene(editor.getBoundsInLocal()).getMaxX() <= 681);
            assertNotNull(editor.getAccessibleHelp());
            if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                WritableImage image = view.pane.snapshot(null, null);
                java.awt.image.BufferedImage png = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                int[] pixels = new int[(int)image.getWidth() * (int)image.getHeight()];
                image.getPixelReader().getPixels(0, 0, (int)image.getWidth(), (int)image.getHeight(), javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, (int)image.getWidth());
                png.setRGB(0, 0, (int)image.getWidth(), (int)image.getHeight(), pixels, 0, (int)image.getWidth());
                javax.imageio.ImageIO.write(png, "png", Path.of("target/preferences-" + theme + ".png").toFile());
            }
            return null;
        });
    }
}
