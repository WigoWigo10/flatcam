package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.project.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class ToolsDatabasePanelTest {
    @TempDir Path directory;
    private JobExecutor jobs;
    @BeforeEach void startJobs() { jobs = new JobExecutor(1); }
    @AfterEach void stopJobs() { jobs.shutdown(); }
    private static <T> T onFx(Callable<T> action) throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException alreadyStarted) { }
        FutureTask<T> future = new FutureTask<>(action); Platform.runLater(future); return future.get(15, TimeUnit.SECONDS);
    }
    private ToolsDatabasePanel panel() {
        var panel = new ToolsDatabasePanel(jobs, () -> null, path -> {}, file -> Icons.fromResource(file, 16));
        Scene scene = new Scene(panel, 1100, 720); ThemeOption.ICE_LIGHT.applyTo(scene); panel.applyCss(); panel.layout(); return panel;
    }
    private static TextField text(ToolsDatabasePanel panel, String key) { return (TextField) panel.lookup("#db-" + key); }
    private static void fire(ToolsDatabasePanel panel, String key) { ((Button) panel.lookup("#db-" + key)).fire(); }
    @SuppressWarnings("unchecked") private static TableView<ToolsDatabasePanel.Row> table(ToolsDatabasePanel panel) { return (TableView<ToolsDatabasePanel.Row>) panel.lookup("#db-table"); }
    @SuppressWarnings("unchecked") private static ComboBox<ToolsDatabaseFields.Choice> combo(ToolsDatabasePanel panel, String key) { return (ComboBox<ToolsDatabaseFields.Choice>) panel.lookup("#db-" + key); }
    private static void choose(ToolsDatabasePanel panel, String key, Object value) {
        var combo = combo(panel, key); combo.setValue(combo.getItems().stream().filter(c -> Objects.equals(c.value(), value)).findFirst().orElseThrow());
    }
    private static boolean groupVisible(Control control) {
        for (var node = control.getParent(); node != null; node = node.getParent()) if (node instanceof TitledPane) return node.isVisible();
        throw new AssertionError("Missing group");
    }
    private static void awaitIdle(ToolsDatabasePanel panel) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (onFx(panel::isBusy)) {
            if (System.nanoTime() > deadline) fail("Database operation timed out");
            Thread.sleep(10);
        }
    }
    private ToolsDatabasePanel load(JSONObject root) throws Exception {
        Path file = directory.resolve("loaded.FlatDB"); Files.writeString(file, root.toString());
        ToolsDatabasePanel panel = onFx(() -> { var created = panel(); created.loadPath(file); return created; });
        awaitIdle(panel); return panel;
    }

    @Test void fieldInventoryMatchesPythonAndAllIconsExist() throws Exception {
        assertEquals(63, ToolsDatabaseFields.ALL.size());
        assertEquals(63, ToolsDatabaseFields.ALL.stream().map(ToolsDatabaseFields.Field::key).distinct().count());
        for (var group : ToolsDatabaseFields.Group.values()) assertNotNull(getClass().getResource("icons/" + group.icon), group.icon);
        onFx(() -> {
            var panel = panel(); fire(panel, "add");
            for (var field : ToolsDatabaseFields.ALL) assertNotNull(panel.lookup("#db-" + field.key()), field.key());
            for (String id : List.of("add", "copy", "delete", "apply", "new", "import", "export", "save"))
                assertNotNull(((Button) panel.lookup("#db-" + id)).getGraphic(), id);
            assertEquals(1, panel.snapshot().length()); return null;
        });
    }
    @Test void fieldKeysMatchTheActualPythonForm() throws Exception {
        Path reference = Path.of("../../appDatabase.py"); Assumptions.assumeTrue(Files.isRegularFile(reference));
        String python = Files.readString(reference);
        String form = python.substring(python.indexOf("self.form_fields ="), python.indexOf("self.name2option ="));
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:").matcher(form);
        Set<String> pythonKeys = new HashSet<>(); while (matcher.find()) pythonKeys.add(matcher.group(1));
        assertEquals(pythonKeys, new HashSet<>(ToolsDatabaseFields.ALL.stream().map(ToolsDatabaseFields.Field::key).toList()));
    }
    @Test void fieldsAndEnabledLabelsCarryTheSameAnimatedHelpIncludingDisabledInputs() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add");
            for (var field : ToolsDatabaseFields.ALL) {
                var input = (Control) panel.lookup("#db-" + field.key());
                var label = (Label) panel.lookup("#db-label-" + field.key());
                assertNotNull(input.getProperties().get(FluidTooltips.TITLE_KEY), field.key());
                assertEquals(ToolsDatabaseDescriptions.fieldText(field), input.getProperties().get(FluidTooltips.TEXT_KEY));
                assertEquals(input.getProperties().get(FluidTooltips.TEXT_KEY), label.getProperties().get(FluidTooltips.TEXT_KEY));
                assertInstanceOf(TooltipContent.class, input.getProperties().get(FluidTooltips.CONTENT_KEY));
                assertEquals(input.getProperties().get(FluidTooltips.CONTENT_KEY), label.getProperties().get(FluidTooltips.CONTENT_KEY));
                assertFalse(label.isDisabled()); assertNull(input.getTooltip());
            }
            assertTrue(text(panel, "depthperpass").isDisabled());
            assertTrue(panel.lookup("#db-label-depthperpass").getProperties().get(FluidTooltips.TEXT_KEY).toString().contains("Multi-Depth"));
            choose(panel, "tool_target", 5); panel.applyDraft();
            assertEquals(ToolsDatabaseDescriptions.fieldText(ToolsDatabaseFields.ALL.stream().filter(f -> f.key().equals("tools_ncc_overlap")).findFirst().orElseThrow()),
                    panel.lookup("#db-tools_ncc_overlap").getProperties().get(FluidTooltips.TEXT_KEY));
            return null;
        });
    }
    @Test void allCommandsHaveRichHelpIncludingMemoryOnlyActions() throws Exception {
        onFx(() -> {
            var panel = panel();
            for (String id : List.of("apply", "new", "import", "export", "save", "search", "filter", "table", "scope", "location")) {
                var control = (Control) panel.lookup("#db-" + id);
                assertNotNull(control.getProperties().get(FluidTooltips.TITLE_KEY), id);
                assertTrue(control.getProperties().get(FluidTooltips.TEXT_KEY).toString().length() > 70, id);
                assertNull(control.getTooltip(), id);
                assertInstanceOf(TooltipContent.class, control.getProperties().get(FluidTooltips.CONTENT_KEY), id);
            }
            for (var group : ToolsDatabaseFields.Group.values()) {
                var pane = panel.lookup("#db-group-" + group.name().toLowerCase(Locale.ROOT));
                assertTrue(pane.getProperties().get(FluidTooltips.TEXT_KEY).toString().contains("recolher ou expandir"));
            }
            for (String id : List.of("add", "copy", "delete")) {
                var control = (Control) panel.lookup("#db-" + id);
                assertNull(control.getTooltip(), id);
                assertInstanceOf(TooltipContent.class,control.getProperties().get(FluidTooltips.CONTENT_KEY), id);
            }
            for (var item : panel.contextMenuForTooltips().getItems())
                assertInstanceOf(TooltipContent.class,item.getProperties().get(FluidTooltips.CONTENT_KEY), item.getText());
            return null;
        });
    }
    @Test void selectionAppliesValidDraftToOldToolAndKeepsNewRowSelected() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); fire(panel, "add");
            var table = table(panel); table.getSelectionModel().select(0);
            text(panel, "name").setText("changed first"); table.getSelectionModel().clearAndSelect(1);
            assertEquals(2, table.getSelectionModel().getSelectedItem().id());
            assertEquals("new_tool_2", text(panel, "name").getText());
            assertEquals("changed first", panel.snapshot().getJSONObject("1").getString("name"));
            assertTrue(panel.dirtyProperty().get()); return null;
        });
    }
    @Test void invalidDraftBlocksSelectionAndDoesNotLoseTextOrCorruptBank() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); fire(panel, "add");
            text(panel, "tooldia").setText("NaN"); table(panel).getSelectionModel().clearAndSelect(0);
            assertEquals(2, table(panel).getSelectionModel().getSelectedItem().id());
            assertEquals("NaN", text(panel, "tooldia").getText());
            assertThrows(IllegalArgumentException.class, panel::snapshot);
            text(panel, "tooldia").setText("0,8"); assertTrue(panel.applyDraft());
            assertEquals(0.8, panel.snapshot().getJSONObject("2").getDouble("tooldia"));
            text(panel, "tol_min").setText("1"); text(panel, "tol_max").setText("0.5"); assertFalse(panel.applyDraft());
            text(panel, "tol_max").setText("1.5"); assertTrue(panel.applyDraft()); return null;
        });
    }
    @Test void copySupportsMultipleSelectionAndEditorDoesNotEditMultipleTools() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); fire(panel, "add");
            table(panel).getSelectionModel().selectAll(); assertTrue(text(panel, "name").isDisabled());
            fire(panel, "copy"); JSONObject result = panel.snapshot();
            assertEquals(4, result.length()); assertEquals("new_tool_1_copy", result.getJSONObject("3").getString("name"));
            assertEquals("new_tool_2_copy", result.getJSONObject("4").getString("name"));
            text(panel, "name").setText("copy only"); panel.applyDraft();
            assertEquals("new_tool_2", panel.snapshot().getJSONObject("2").getString("name")); return null;
        });
    }
    @Test void operationFiltersGroupsAndOptionalInputsFollowPythonDependencies() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); choose(panel, "tool_target", 2);
            assertTrue(groupVisible(text(panel, "tools_drill_cutz")));
            assertFalse(groupVisible(text(panel, "cutz")));
            assertTrue(text(panel, "tools_drill_depthperpass").isDisabled());
            ((CheckBox) panel.lookup("#db-tools_drill_multidepth")).setSelected(true);
            assertFalse(text(panel, "tools_drill_depthperpass").isDisabled());
            choose(panel, "tool_target", 6); choose(panel, "tools_cutout_gap_type", "mb");
            assertFalse(text(panel, "tools_cutout_mb_dia").isDisabled()); assertTrue(text(panel, "tools_cutout_gap_depth").isDisabled());
            assertTrue(panel.applyDraft()); return null;
        });
    }
    @Test void vShapeCalculatesDiameterFromTipAngleAndCutDepth() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); choose(panel, "tool_type", "V");
            assertTrue(text(panel, "tooldia").isDisabled()); assertFalse(text(panel, "vtipdia").isDisabled());
            text(panel, "vtipdia").setText("0.1"); text(panel, "vtipangle").setText("60"); text(panel, "cutz").setText("-0.2");
            assertTrue(panel.applyDraft());
            assertEquals(0.1 + 0.4 * Math.tan(Math.toRadians(30)), panel.snapshot().getJSONObject("1").getDouble("tooldia"), 1e-12);
            text(panel, "vtipangle").setText("180"); assertFalse(panel.applyDraft()); return null;
        });
    }
    @Test void renamingImportedToolPreservesAbsentAndUnknownParameters() throws Exception {
        JSONObject entry = new JSONObject().put("name", "legacy").put("tooldia", 0.7)
                .put("custom", new JSONObject().put("vendor", "original"))
                .put("data", new JSONObject().put("tool_target", "NCC").put("future", new JSONObject().put("field", 123)));
        var panel = load(new JSONObject().put("7", entry));
        onFx(() -> {
            assertFalse(panel.dirtyProperty().get()); text(panel, "name").setText("renamed"); assertTrue(panel.applyDraft());
            JSONObject result = panel.snapshot().getJSONObject("7"); entry.put("name", "renamed");
            assertTrue(entry.similar(result)); assertEquals(1, LegacyToolsDatabase.nccTools(panel.snapshot()).size()); return null;
        });
    }
    @Test void searchAndOperationFilterNeverDiscardUnmatchedTools() throws Exception {
        onFx(() -> {
            var panel = panel(); fire(panel, "add"); fire(panel, "add"); choose(panel, "tool_target", 5); panel.applyDraft();
            @SuppressWarnings("unchecked") var filter = (ComboBox<String>) panel.lookup("#db-filter"); filter.setValue("NCC");
            assertEquals(1, table(panel).getItems().size()); assertEquals(2, table(panel).getItems().getFirst().id());
            ((TextField) panel.lookup("#db-search")).setText("missing"); assertTrue(table(panel).getItems().isEmpty());
            assertEquals(2, panel.snapshot().length()); return null;
        });
    }
    @Test void asynchronousSaveReloadAndExportKeepCorrectDirtyStateAndBackup() throws Exception {
        Path file = directory.resolve("bank.FlatDB"), exported = directory.resolve("export.FlatDB");
        var panel = onFx(() -> { var created = panel(); fire(created, "add"); created.saveTo(file, false); return created; }); awaitIdle(panel);
        assertEquals(1, ToolsDatabase.load(file).ids().size());
        onFx(() -> { assertFalse(panel.dirtyProperty().get()); text(panel, "name").setText("updated"); panel.saveTo(exported, true); return null; }); awaitIdle(panel);
        onFx(() -> { assertTrue(panel.dirtyProperty().get()); panel.saveTo(file, false); return null; }); awaitIdle(panel);
        assertEquals("updated", ToolsDatabase.load(exported).entry(1).getString("name"));
        assertEquals("updated", ToolsDatabase.load(file).entry(1).getString("name"));
        try (var files = Files.list(directory)) { assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".bak")).count()); }
        onFx(() -> { assertFalse(panel.dirtyProperty().get()); assertTrue(panel.confirmClose()); panel.loadPath(file); return null; }); awaitIdle(panel);
        onFx(() -> { assertEquals("updated", text(panel, "name").getText()); return null; });
    }
    @Test void failedImportKeepsCurrentBankAndUnappliedDraft() throws Exception {
        Path invalid = directory.resolve("bad.FlatDB"); Files.writeString(invalid, "not JSON");
        var panel = onFx(() -> { var created = panel(); fire(created, "add"); text(created, "name").setText("draft"); created.loadPath(invalid); return created; }); awaitIdle(panel);
        onFx(() -> {
            assertTrue(panel.dirtyProperty().get()); assertEquals("draft", text(panel, "name").getText());
            assertEquals("draft", panel.snapshot().getJSONObject("1").getString("name")); return null;
        });
    }

    @ParameterizedTest @EnumSource(ThemeOption.class)
    void layoutAndControlsRemainUsableInEachTheme(ThemeOption theme) throws Exception {
        onFx(() -> {
            var panel = new ToolsDatabasePanel(jobs, () -> null, p -> {}, file -> Icons.fromResource((theme.isDark() ? "dark/" : "") + file, 16));
            Scene scene = new Scene(panel, 1100, 720); theme.applyTo(scene); fire(panel, "add"); panel.applyCss(); panel.layout();
            assertTrue(table(panel).getWidth() > 180); assertTrue(text(panel, "name").getWidth() >= 90);
            assertNotNull(((Button) panel.lookup("#db-save")).getGraphic());
            if (Boolean.getBoolean("flatcam.tests.snapshots")) {
                WritableImage image = panel.snapshot(null, null); var pixels = image.getPixelReader();
                var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, pixels.getArgb(x, y));
                javax.imageio.ImageIO.write(output, "png", Path.of("target", "tools-db-" + theme + ".png").toFile());
            }
            panel.resize(660, 720); panel.layout();
            assertTrue(table(panel).getWidth() >= 180); assertTrue(text(panel, "name").getWidth() >= 90);
            var save = panel.lookup("#db-save");
            assertTrue(save.getBoundsInParent().getMaxX() <= save.getParent().getLayoutBounds().getWidth());
            return null;
        });
    }
}
