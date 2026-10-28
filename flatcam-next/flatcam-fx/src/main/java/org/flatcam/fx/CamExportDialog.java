package org.flatcam.fx;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.flatcam.cam.excellon.ExcellonExporter;
import org.flatcam.cam.gerber.GerberExporter;

/**
 * Coordinate format asked for by File > Exportar > Gerber / Excellon - the
 * gerber_exp_* and excellon_exp_* options of FlatCAM Python's Preferences
 * (Gerber/Excellon > Export), shared with the Preferences pages and Terminal. Starts from Python's defaults and remembers the
 * last confirmed choice.
 */
final class CamExportDialog {

    private static final String GERBER_KEY = "exportGerberFormat";
    private static final String EXCELLON_KEY = "exportExcellonFormat";
    private static final String UNITS_IN = "IN - polegadas";
    private static final String UNITS_MM = "MM - milímetros";

    private CamExportDialog() {
    }

    static Optional<GerberExporter.Format> askGerberFormat(Window owner, String objectUnits) {
        Optional<GerberExporter.Format> result = gerberDialog(owner, objectUnits).showAndWait();
        result.ifPresent(CamExportDialog::saveGerber);
        return result;
    }

    /** Builds the same dialog used interactively, without displaying it or saving preferences. */
    static Dialog<GerberExporter.Format> gerberDialog(Window owner, String objectUnits) {
        GerberExporter.Format initial = loadGerber();
        Dialog<GerberExporter.Format> dialog = dialog(owner, "Exportar Gerber");

        ComboBox<String> units = unitsCombo(initial.units());
        Spinner<Integer> integerDigits = digits(initial.integerDigits());
        Spinner<Integer> decimalDigits = digits(initial.decimalDigits());
        ComboBox<String> zeros = new ComboBox<>();
        zeros.getItems().addAll("L - omite zeros à esquerda", "T - omite zeros à direita");
        zeros.getSelectionModel().select(initial.omitLeadingZeros() ? 0 : 1);
        units.setOnAction(e -> integerDigits.getValueFactory().setValue(isInch(units) ? 2 : 3));

        GridPane grid = grid();
        grid.addRow(0, new Label("Unidades:"), units);
        grid.addRow(1, new Label("Dígitos inteiros:"), integerDigits);
        grid.addRow(2, new Label("Dígitos decimais:"), decimalDigits);
        grid.addRow(3, new Label("Zeros:"), zeros);
        grid.add(note(objectUnits), 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button.getButtonData() != ButtonBar.ButtonData.OK_DONE ? null
                : new GerberExporter.Format(isInch(units) ? "IN" : "MM", integerDigits.getValue(),
                        decimalDigits.getValue(), zeros.getSelectionModel().getSelectedIndex() == 0));
        PanelTooltips.install(grid, "Exportar Gerber");
        return dialog;
    }

    static Optional<ExcellonExporter.Format> askExcellonFormat(Window owner, String objectUnits) {
        return askExcellonFormat(owner,objectUnits,false);
    }

    static Optional<ExcellonExporter.Format> askExcellonFormat(Window owner, String objectUnits, boolean curvedSlots) {
        Optional<ExcellonExporter.Format> result = excellonDialog(owner, objectUnits, curvedSlots).showAndWait();
        result.ifPresent(CamExportDialog::saveExcellon);
        return result;
    }

    static Dialog<ExcellonExporter.Format> excellonDialog(Window owner, String objectUnits, boolean curvedSlots) {
        ExcellonExporter.Format initial = loadExcellon();
        Dialog<ExcellonExporter.Format> dialog = dialog(owner, "Exportar Excellon");

        ComboBox<String> units = unitsCombo(initial.units());
        ComboBox<String> style = new ComboBox<>();
        style.getItems().addAll("Decimal (com ponto)", "Sem ponto decimal");
        style.getSelectionModel().select(initial.decimal() ? 0 : 1);
        Spinner<Integer> integerDigits = digits(initial.integerDigits());
        Spinner<Integer> decimalDigits = digits(initial.decimalDigits());
        ComboBox<String> zeros = new ComboBox<>();
        zeros.getItems().addAll("LZ - mantém zeros à esquerda", "TZ - mantém zeros à direita");
        zeros.getSelectionModel().select(initial.leadingZeros() ? 0 : 1);
        ComboBox<String> slots = new ComboBox<>();
        slots.getItems().addAll("Roteado (G00/M15/G01/M16)", "Furado (G85)");
        slots.getSelectionModel().select(initial.slots() == ExcellonExporter.SlotStyle.ROUTED ? 0 : 1);
        if (curvedSlots) { slots.getSelectionModel().select(0); slots.setDisable(true); }
        ToolDescriptions.apply(slots, "Formato dos rasgos",
                "Roteado conserva o trajeto; curvas são exportadas por segmentos retos. G85 representa somente rasgos retos.\n\n"
                + (curvedSlots ? "Limitação: este objeto contém curvas, por isso G85 está indisponível." : "Escolha o formato aceito pelo programa que vai ler o arquivo."));
        Runnable syncZeros = () -> {
            boolean decimal = style.getSelectionModel().getSelectedIndex() == 0;
            zeros.setDisable(decimal);
            integerDigits.setDisable(decimal);
        };
        style.setOnAction(e -> syncZeros.run());
        syncZeros.run();
        units.setOnAction(e -> {
            integerDigits.getValueFactory().setValue(isInch(units) ? 2 : 3);
            decimalDigits.getValueFactory().setValue(isInch(units) ? 4 : 3);
        });

        GridPane grid = grid();
        grid.addRow(0, new Label("Unidades:"), units);
        grid.addRow(1, new Label("Formato:"), style);
        grid.addRow(2, new Label("Dígitos inteiros:"), integerDigits);
        grid.addRow(3, new Label("Dígitos decimais:"), decimalDigits);
        grid.addRow(4, new Label("Zeros:"), zeros);
        grid.addRow(5, new Label("Slots:"), slots);
        grid.add(note(objectUnits), 0, 6, 2, 1);
        if (curvedSlots) {
            Label curveNote=new Label("Este objeto contém rasgos curvos. O formato roteado mantém o trajeto por segmentos; G85 só representa rasgos retos.");
            curveNote.setWrapText(true); curveNote.setMaxWidth(400); grid.add(curveNote,0,7,2,1);
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button.getButtonData() != ButtonBar.ButtonData.OK_DONE ? null
                : new ExcellonExporter.Format(isInch(units) ? "IN" : "MM",
                        style.getSelectionModel().getSelectedIndex() == 0,
                        integerDigits.getValue(), decimalDigits.getValue(),
                        zeros.getSelectionModel().getSelectedIndex() == 0,
                        slots.getSelectionModel().getSelectedIndex() == 0
                                ? ExcellonExporter.SlotStyle.ROUTED : ExcellonExporter.SlotStyle.G85));
        PanelTooltips.install(grid, "Exportar Excellon");
        return dialog;
    }

    /** Shared UI/Tcl format lookup; never writes preferences. */
    static GerberExporter.Format loadGerber() {
        return new GerberExporter.Format(ToolDefaults.choice("export.gerber.units"),
                ToolDefaults.integer("export.gerber.integer"), ToolDefaults.integer("export.gerber.decimals"),
                ToolDefaults.choice("export.gerber.zeros").equals("L"));
    }

    static GerberExporter.Format parseGerberFormat(String saved) {
        try {
            String[] parts = saved.split(";");
            return new GerberExporter.Format(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    "L".equals(parts[3]));
        } catch (RuntimeException missingOrStale) {
            return GerberExporter.Format.flatcamDefaults();
        }
    }

    /** Shared by the export dialog and noninteractive Tcl export; does not write preferences. */
    static ExcellonExporter.Format loadExcellon() {
        return new ExcellonExporter.Format(ToolDefaults.choice("export.excellon.units"),
                ToolDefaults.choice("export.excellon.format").equals("dec"), ToolDefaults.integer("export.excellon.integer"),
                ToolDefaults.integer("export.excellon.decimals"), ToolDefaults.choice("export.excellon.zeros").equals("LZ"),
                ToolDefaults.choice("export.excellon.slots", ExcellonExporter.SlotStyle.class));
    }

    static ExcellonExporter.Format parseExcellonFormat(String saved) {
        try {
            String[] parts = saved.split(";");
            return new ExcellonExporter.Format(parts[0], "dec".equals(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), "LZ".equals(parts[4]),
                    ExcellonExporter.SlotStyle.valueOf(parts[5]));
        } catch (RuntimeException missingOrStale) {
            return ExcellonExporter.Format.flatcamDefaults();
        }
    }

    static void saveGerber(GerberExporter.Format format) {
        ToolDefaults.set("export.gerber.units",true,format.units());
        ToolDefaults.set("export.gerber.integer",true,Integer.toString(format.integerDigits()));
        ToolDefaults.set("export.gerber.decimals",true,Integer.toString(format.decimalDigits()));
        ToolDefaults.set("export.gerber.zeros",true,format.omitLeadingZeros() ? "L" : "T");
    }

    static void saveExcellon(ExcellonExporter.Format format) {
        ToolDefaults.set("export.excellon.units",true,format.units());
        ToolDefaults.set("export.excellon.integer",true,Integer.toString(format.integerDigits()));
        ToolDefaults.set("export.excellon.decimals",true,Integer.toString(format.decimalDigits()));
        ToolDefaults.set("export.excellon.format",true,format.decimal() ? "dec" : "ndec");
        ToolDefaults.set("export.excellon.zeros",true,format.leadingZeros() ? "LZ" : "TZ");
        ToolDefaults.set("export.excellon.slots",true,format.slots().name());
    }

    /** Only MainApp invokes this once after installing the persistent store. Reset must not resurrect old formats. */
    static void migrateLegacyPreferences() {
        if ("true".equals(AppPreferences.loadText("exportDefaultsMigrated"))) return;
        migrateLegacyFormats(AppPreferences.loadText(GERBER_KEY), AppPreferences.loadText(EXCELLON_KEY));
        AppPreferences.saveText("exportDefaultsMigrated", "true");
    }

    static void migrateLegacyFormats(String gerber, String excellon) {
        if (gerber != null && !gerber.isBlank()) saveGerber(parseGerberFormat(gerber));
        if (excellon != null && !excellon.isBlank()) saveExcellon(parseExcellonFormat(excellon));
    }

    private static <T> Dialog<T> dialog(Window owner, String title) {
        Dialog<T> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(title);
        FluidTooltips.install(dialog, title);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Exportar...", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst()),
                "Confirmar formato de exportação", "Confirma as unidades e o formato e segue para a escolha do arquivo de destino. A última configuração confirmada é lembrada.\n\n"
                + "Resultado: exporta uma cópia; não transforma as coordenadas do objeto em memória.");
        ToolDescriptions.apply(dialog.getDialogPane().lookupButton(ButtonType.CANCEL), "Cancelar exportação",
                "Fecha este diálogo sem confirmar o formato nem gerar um arquivo.");
        return dialog;
    }

    private static ComboBox<String> unitsCombo(String units) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(UNITS_IN, UNITS_MM);
        combo.setValue("IN".equals(units) ? UNITS_IN : UNITS_MM);
        return combo;
    }

    private static boolean isInch(ComboBox<String> units) {
        return UNITS_IN.equals(units.getValue());
    }

    private static Spinner<Integer> digits(int value) {
        Spinner<Integer> spinner = new Spinner<>(1, 6, value);
        spinner.setPrefWidth(80);
        return spinner;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private static Label note(String objectUnits) {
        Label note = new Label("Objeto em " + objectUnits
                + "; as coordenadas são convertidas para as unidades escolhidas.");
        note.setWrapText(true);
        note.setMaxWidth(320);
        return note;
    }
}
