package org.flatcam.fx;

import java.util.Map;
import java.util.Set;
import javafx.scene.Node;

/** Portuguese help based on ToolsDB2UI, with explicit FX transfer limitations. */
final class ToolsDatabaseDescriptions {
    private static final Set<String> TRANSFERRED = Set.of("name", "tooldia", "tool_type", "tool_target", "tol_min", "tol_max",
            "tools_iso_passes", "tools_iso_overlap", "tools_iso_isotype",
            "vtipdia", "vtipangle", "cutz", "multidepth", "depthperpass", "travelz", "feedrate", "feedrate_rapid", "spindlespeed",
            "feedrate_z", "dwell", "dwelltime", "extracut", "extracut_length", "offset", "offset_value",
            "tools_paint_overlap", "tools_paint_offset", "tools_paint_method", "tools_paint_connect", "tools_paint_contour",
            "tools_cutout_margin", "tools_cutout_gapsize", "tools_cutout_gaps_ff", "tools_cutout_convexshape",
            "tools_cutout_gap_type", "tools_cutout_gap_depth", "tools_cutout_mb_dia", "tools_cutout_mb_spacing",
            "tools_ncc_operation", "tools_ncc_overlap", "tools_ncc_method", "tools_ncc_connect", "tools_ncc_contour",
            "tools_ncc_offset_choice", "tools_ncc_offset_value", "tools_drill_cutz", "tools_drill_multidepth",
            "tools_drill_depthperpass", "tools_drill_travelz", "tools_drill_feedrate_z", "tools_drill_spindlespeed",
            "tools_drill_dwell", "tools_drill_dwelltime", "tools_drill_offset", "tools_drill_feedrate_rapid");
    private static final Set<String> LINEAR = Set.of("tooldia", "tol_min", "tol_max", "vtipdia", "offset_value", "cutz",
            "depthperpass", "travelz", "extracut_length", "tools_drill_cutz", "tools_drill_depthperpass", "tools_drill_travelz",
            "tools_drill_offset", "tools_paint_offset", "tools_ncc_margin", "tools_ncc_offset_value", "tools_cutout_margin",
            "tools_cutout_gapsize", "tools_cutout_gap_depth", "tools_cutout_mb_dia", "tools_cutout_mb_spacing");

    static String fieldText(ToolsDatabaseFields.Field field) {
        String body = switch (field.key()) {
            case "name" -> I18n.text("ui.b4fb9d56dff1");
            case "tooldia" -> I18n.text("ui.007d3b5c4131");
            case "tol_min" -> I18n.text("ui.2c29980b6896");
            case "tol_max" -> I18n.text("ui.f83d09957f60");
            case "tool_target" -> I18n.text("ui.3e44b0e6143e");
            case "tool_type" -> I18n.text("ui.5a2216e5f95d");
            case "vtipdia" -> I18n.text("ui.9c4a7913fa01");
            case "vtipangle" -> I18n.text("ui.ce4c5153c754");
            case "type" -> I18n.text("ui.41ab10f67dd9");
            case "offset" -> I18n.text("ui.aacaddc8971f");
            case "offset_value" -> I18n.text("ui.f75bc6543a18");
            case "cutz" -> I18n.text("ui.5ea9e2fac451");
            case "tools_drill_cutz" -> I18n.text("ui.4df34f0ac8aa");
            case "multidepth", "tools_drill_multidepth" -> I18n.text("ui.1dcd647d56b9");
            case "depthperpass", "tools_drill_depthperpass" -> I18n.text("ui.456a8ebadd8c");
            case "travelz", "tools_drill_travelz" -> I18n.text("ui.f91d4c8f5ad8");
            case "feedrate" -> I18n.text("ui.cc211002f83a");
            case "feedrate_z", "tools_drill_feedrate_z" -> I18n.text("ui.c5ecfce43470");
            case "feedrate_rapid", "tools_drill_feedrate_rapid" -> I18n.text("ui.5ac9157749c6");
            case "spindlespeed", "tools_drill_spindlespeed" -> I18n.text("ui.780bbcbf8bf0");
            case "dwell", "tools_drill_dwell" -> I18n.text("ui.51c14e84cf54");
            case "dwelltime", "tools_drill_dwelltime" -> I18n.text("ui.a469ddecc553");
            case "extracut" -> I18n.text("ui.a55f6af44e1e");
            case "extracut_length" -> I18n.text("ui.d317fdcd23ad");
            case "tools_drill_offset" -> I18n.text("ui.e55b167f74ed");
            case "tools_drill_drill_slots" -> I18n.text("ui.54e4cfa8263b");
            case "tools_drill_drill_overlap" -> I18n.text("ui.0fa4dc89274a");
            case "tools_drill_last_drill" -> I18n.text("ui.47cd96e13b62");
            case "tools_iso_passes" -> I18n.text("ui.a97ad6ed9408");
            case "tools_iso_overlap" -> I18n.text("ui.e683b36ba450");
            case "tools_iso_milling_type", "tools_ncc_milling_type" -> I18n.text("ui.98144a76b336");
            case "tools_iso_follow" -> I18n.text("ui.61ef052ba553");
            case "tools_iso_isotype" -> I18n.text("ui.c6f69ce7028f");
            case "tools_paint_overlap", "tools_ncc_overlap" -> I18n.text("ui.2f6693817b9b");
            case "tools_paint_offset" -> I18n.text("ui.5ad3e4c20743");
            case "tools_paint_method" -> I18n.text("ui.1e3c0b1a3d6b");
            case "tools_paint_connect", "tools_ncc_connect" -> I18n.text("ui.a249bfed7735");
            case "tools_paint_contour", "tools_ncc_contour" -> I18n.text("ui.cbc8c3307206");
            case "tools_ncc_operation" -> I18n.text("ui.5ab0ef4a4657");
            case "tools_ncc_margin" -> I18n.text("ui.d37eabcd9bfb");
            case "tools_ncc_method" -> I18n.text("ui.d09474e58c9a");
            case "tools_ncc_offset_choice" -> I18n.text("ui.5325f315fe81");
            case "tools_ncc_offset_value" -> I18n.text("ui.4707e070d1b3");
            case "tools_cutout_margin" -> I18n.text("ui.6f87fa37b6d6");
            case "tools_cutout_gapsize" -> I18n.text("ui.0e76d814e99b");
            case "tools_cutout_gaps_ff" -> I18n.text("ui.fecbff00731c");
            case "tools_cutout_convexshape" -> I18n.text("ui.156747d498e6");
            case "tools_cutout_gap_type" -> I18n.text("ui.b6e79f64013d");
            case "tools_cutout_gap_depth" -> I18n.text("ui.60649971e444");
            case "tools_cutout_mb_dia" -> I18n.text("ui.f6561f253beb");
            case "tools_cutout_mb_spacing" -> I18n.text("ui.d383d48d875a");
            default -> throw new IllegalArgumentException(I18n.text("ui.45d311781d98") + field.key());
        };
        if ("tools_paint_method".equals(field.key())) body += I18n.text("ui.7feedc0c4379");
        if (LINEAR.contains(field.key())) body += I18n.text("ui.f73eec9b0794");
        if (!TRANSFERRED.contains(field.key())) body += I18n.text("ui.12eb679d5413");
        return body;
    }

    static String actionText(String label) {
        return switch (I18n.source(label, "Adicionar ferramenta", "Copiar", "Excluir", "Aplicar parâmetros", "Nova base", "Importar base", "Exportar base", "Salvar base")) {
            case "Adicionar ferramenta" -> I18n.text("ui.f217a4cb925d");
            case "Copiar" -> I18n.text("ui.a8dee3b4c25c");
            case "Excluir" -> I18n.text("ui.63c41b6f1b62");
            case "Aplicar parâmetros" -> I18n.text("ui.7454aeb3a9f1");
            case "Nova base" -> I18n.text("ui.50eeae2a66f3");
            case "Importar base" -> I18n.text("ui.841aa9d512f7");
            case "Exportar base" -> I18n.text("ui.88d2c76ce9e0");
            case "Salvar base" -> I18n.text("ui.0a04515a75ba");
            default -> throw new IllegalArgumentException(I18n.text("ui.45d311781d98") + label);
        };
    }

    static String groupText(ToolsDatabaseFields.Group group) {
        return switch (group) {
            case DESCRIPTION -> I18n.text("ui.2f98070e9466");
            case MILLING -> I18n.text("ui.953c8825f4d0");
            case DRILLING -> I18n.text("ui.ef816d0f3ef1");
            case ISOLATION -> I18n.text("ui.7ce686d0ec4a");
            case PAINT -> I18n.text("ui.10022c39b858");
            case NCC -> I18n.text("ui.35d29b8dde80");
            case CUTOUT -> I18n.text("ui.5bab1dddc1c3");
        };
    }

    static TooltipContent content(String text) {
        return TooltipContent.describe(text);
    }

    static void apply(Map<Object, Object> properties, String title, String text) {
        ToolDescriptions.apply(properties, title, text);
    }

    static void apply(Node node, String title, String text) {
        ToolDescriptions.apply(node, title, text);
    }

    private ToolsDatabaseDescriptions() { }
}
