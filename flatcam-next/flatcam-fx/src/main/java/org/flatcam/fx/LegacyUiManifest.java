package org.flatcam.fx;

import java.util.List;

/**
 * Visible legacy commands whose implementation is still being ported. The
 * labels/icons follow appGUI/MainGUI.py's toolbars and UI_INVENTORY.md. An
 * identifier is used only to connect the few commands already implemented;
 * the rest deliberately remain disabled in both menu and toolbar.
 */
final class LegacyUiManifest {

    record Command(String id, String label, String icon) {
    }

    static final List<Command> TOOLS_PREPARATION = List.of(
            new Command("double_sided", "2-Sided Tool", "doubleside32.png"),
            new Command("align", "Align Objects Tool", "align32.png"),
            new Command("extract_drills", "Extract Drills Tool", "extract_drill32.png"));

    static final List<Command> TOOLS_CAM = List.of(
            new Command("cutout", "Cutout Tool", "cut16_bis.png"),
            new Command("ncc", "NCC Tool", "ncc16.png"),
            new Command("paint", "Paint Tool", "paint20_1.png"),
            new Command("isolation", "Isolation Tool", "iso_16.png"),
            new Command("drilling", "Drilling Tool", "drilling_tool32.png"),
            new Command("panelize", "Panelize Tool", "panelize32.png"),
            new Command("film", "Film Tool", "film16.png"),
            new Command("solderpaste", "SolderPaste Tool", "solderpastebis32.png"),
            new Command("subtract", "Subtract Tool", "sub32.png"),
            new Command("rules", "Rules Check Tool", "rules32.png"),
            new Command("optimal", "Optimal Tool", "open_excellon32.png"));

    static final List<Command> TOOLS_UTILITIES = List.of(
            new Command("calculators", "Calculators Tool", "calculator24.png"),
            new Command("transform", "Transform Tool", "transform.png"),
            new Command("qrcode", "QRCode Tool", "qrcode32.png"),
            new Command("copper_thieving", "Copper Thieving Tool", "copperfill32.png"),
            new Command("fiducials", "Fiducials Tool", "fiducials_32.png"),
            new Command("calibration", "Calibration Tool", "calibrate_32.png"),
            new Command("punch", "Punch Gerber Tool", "punch32.png"),
            new Command("invert", "Invert Gerber Tool", "invert32.png"),
            new Command("corners", "Corner Markers Tool", "corners_32.png"),
            new Command("etch", "Etch Compensation Tool", "etch_32.png"));

    static final List<Command> GERBER_EDITOR = List.of(
            new Command("select", "Selecionar", "pointer32.png"),
            new Command("pad", "Adicionar Pad", "aperture32.png"),
            new Command("pad_array", "Array de Pads", "padarray32.png"),
            new Command("track", "Adicionar Trilha", "track32.png"),
            new Command("region", "Adicionar Região", "polygon32.png"),
            new Command("polygonize", "Poligonizar", "poligonize32.png"),
            new Command("semidisc", "Semidisco", "semidisc32.png"),
            new Command("disc", "Disco", "disc32.png"),
            new Command("buffer", "Buffer", "buffer16-2.png"),
            new Command("scale", "Escalar", "scale32.png"),
            new Command("mark_area", "Marcar Área", "markarea32.png"),
            new Command("eraser", "Borracha", "eraser26.png"),
            new Command("copy", "Copiar", "copy32.png"),
            new Command("delete", "Excluir", "trash32.png"),
            new Command("transform", "Transformações", "transform.png"),
            new Command("move", "Mover", "move32.png"));

    static final List<Command> EXCELLON_EDITOR = List.of(
            new Command("select", "Selecionar", "pointer32.png"),
            new Command("drill", "Adicionar Furo", "plus16.png"),
            new Command("drill_array", "Array de Furos", "addarray16.png"),
            new Command("slot", "Adicionar Slot", "slot26.png"),
            new Command("slot_array", "Array de Slots", "slot_array26.png"),
            new Command("resize", "Redimensionar Furo", "resize16.png"),
            new Command("copy", "Copiar Furo", "copy32.png"),
            new Command("delete", "Excluir Furo", "trash32.png"),
            new Command("move", "Mover Furo", "move32.png"));

    static final List<Command> GEOMETRY_EDITOR = List.of(
            new Command("select", "Selecionar", "pointer32.png"),
            new Command("circle", "Círculo", "circle32.png"),
            new Command("arc", "Arco", "arc32.png"),
            new Command("rectangle", "Retângulo", "rectangle32.png"),
            new Command("path", "Caminho", "path32.png"),
            new Command("polygon", "Polígono", "polygon32.png"),
            new Command("text", "Texto", "text32.png"),
            new Command("buffer", "Buffer", "buffer16-2.png"),
            new Command("paint", "Paint Shape", "paint20_1.png"),
            new Command("eraser", "Borracha", "eraser26.png"),
            new Command("union", "União", "union32.png"),
            new Command("explode", "Explodir", "explode32.png"),
            new Command("intersection", "Interseção", "intersection32.png"),
            new Command("subtract", "Subtração", "subtract32.png"),
            new Command("cut_path", "Cortar Caminho", "cutpath32.png"),
            new Command("copy", "Copiar", "copy32.png"),
            new Command("delete", "Excluir", "trash32.png"),
            new Command("transform", "Transformações", "transform.png"),
            new Command("move", "Mover", "move32.png"));

    private LegacyUiManifest() {
    }
}
