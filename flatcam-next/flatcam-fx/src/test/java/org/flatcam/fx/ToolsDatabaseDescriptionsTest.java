package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolsDatabaseDescriptionsTest {
    private static String help(String key) {
        return ToolsDatabaseDescriptions.fieldText(ToolsDatabaseFields.ALL.stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow());
    }
    @Test void all63PythonFieldsHaveExplanationsRatherThanInternalKeys() {
        for (var field : ToolsDatabaseFields.ALL) {
            String text = ToolsDatabaseDescriptions.fieldText(field);
            assertTrue(text.length() > 50, field.key());
            assertFalse(text.contains(field.key()), "Raw storage key leaked into help: " + field.key());
        }
    }
    @Test void allActionsExplainWhatChangesInMemoryOrOnDisk() {
        for (String action : List.of("Adicionar ferramenta", "Copiar", "Excluir", "Aplicar parametros", "Nova base", "Import DB", "Export DB", "Save DB"))
            assertTrue(ToolsDatabaseDescriptions.actionText(action).length() > 70, action);
        assertTrue(ToolsDatabaseDescriptions.actionText("Aplicar parametros").contains("Não grava no disco"));
        assertTrue(ToolsDatabaseDescriptions.actionText("Export DB").contains("Mantém a associação"));
        assertTrue(ToolsDatabaseDescriptions.actionText("Save DB").contains("Ctrl+S"));
    }
    @Test void unsupportedFieldsAreNotPresentedAsWorkingCamParameters() {
        for (String key : List.of("offset", "tools_iso_follow", "tools_ncc_margin", "tools_drill_feedrate_rapid", "tools_drill_drill_slots", "tools_cutout_gap_depth"))
            assertTrue(help(key).contains("transferência para o CAM ainda não está implementada"), key);
        for (String key : List.of("tooldia", "tools_iso_passes", "tools_ncc_overlap", "tools_drill_feedrate_z", "cutz", "tools_paint_method", "tools_cutout_gapsize"))
            assertFalse(help(key).contains("ainda não está implementada"), key);
    }
    @Test void unitsDependenciesAndPythonSpecificOptionsAreExplained() {
        assertTrue(help("tooldia").contains("não converte unidades"));
        assertTrue(help("tools_drill_feedrate_z").contains("mm/min ou in/min"));
        assertTrue(help("tools_drill_dwelltime").contains("segundos"));
        assertTrue(help("tools_drill_spindlespeed").contains("RPM"));
        assertTrue(help("tools_paint_method").contains("Laser_lines aparece desabilitado"));
        assertTrue(help("tools_cutout_mb_spacing").contains("passo entre centros"));
        assertTrue(help("vtipangle").contains("graus"));
    }
    @Test void everySectionExplainsItsScopeAndUnknownFieldsRequireNewHelp() {
        for (var group : ToolsDatabaseFields.Group.values()) assertTrue(ToolsDatabaseDescriptions.groupText(group).length() > 80, group.name());
        var unknown = new ToolsDatabaseFields.Field(ToolsDatabaseFields.Group.NCC, "unknown", "Unknown", false, 0, 0, 1, false, List.of());
        assertThrows(IllegalArgumentException.class, () -> ToolsDatabaseDescriptions.fieldText(unknown));
    }
}
