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
 * (Gerber/Excellon > Export), asked for at export time instead of being
 * buried in Preferences. Starts from Python's defaults and remembers the
 * last choice.
 */
final class CamExportDialog {

    private static final String GERBER_KEY = "exportGerberFormat";
    private static final String EXCELLON_KEY = "exportExcellonFormat";
    private static final String UNITS_IN = "IN - polegadas";
    private static final String UNITS_MM = "MM - milimetros";

    private CamExportDialog() {
    }

    static Optional<GerberExporter.Format> askGerberFormat(Window owner, String objectUnits) {
        GerberExporter.Format initial = loadGerber();
        Dialog<GerberExporter.Format> dialog = dialog(owner, "Exportar Gerber");

        ComboBox<String> units = unitsCombo(initial.units());
        Spinner<Integer> integerDigits = digits(initial.integerDigits());
        Spinner<Integer> decimalDigits = digits(initial.decimalDigits());
        ComboBox<String> zeros = new ComboBox<>();
        zeros.getItems().addAll("L - omite zeros a esquerda", "T - omite zeros a direita");
        zeros.getSelectionModel().select(initial.omitLeadingZeros() ? 0 : 1);
        units.setOnAction(e -> integerDigits.getValueFactory().setValue(isInch(units) ? 2 : 3));

        GridPane grid = grid();
        grid.addRow(0, new Label("Unidades:"), units);
        grid.addRow(1, new Label("Digitos inteiros:"), integerDigits);
        grid.addRow(2, new Label("Digitos decimais:"), decimalDigits);
        grid.addRow(3, new Label("Zeros:"), zeros);
        grid.add(note(objectUnits), 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button.getButtonData() != ButtonBar.ButtonData.OK_DONE ? null
                : new GerberExporter.Format(isInch(units) ? "IN" : "MM", integerDigits.getValue(),
                        decimalDigits.getValue(), zeros.getSelectionModel().getSelectedIndex() == 0));
        Optional<GerberExporter.Format> result = dialog.showAndWait();
        result.ifPresent(format -> AppPreferences.saveText(GERBER_KEY, format.units() + ";"
                + format.integerDigits() + ";" + format.decimalDigits() + ";"
                + (format.omitLeadingZeros() ? "L" : "T")));
        return result;
    }

    static Optional<ExcellonExporter.Format> askExcellonFormat(Window owner, String objectUnits) {
        ExcellonExporter.Format initial = loadExcellon();
        Dialog<ExcellonExporter.Format> dialog = dialog(owner, "Exportar Excellon");

        ComboBox<String> units = unitsCombo(initial.units());
        ComboBox<String> style = new ComboBox<>();
        style.getItems().addAll("Decimal (com ponto)", "Sem ponto decimal");
        style.getSelectionModel().select(initial.decimal() ? 0 : 1);
        Spinner<Integer> integerDigits = digits(initial.integerDigits());
        Spinner<Integer> decimalDigits = digits(initial.decimalDigits());
        ComboBox<String> zeros = new ComboBox<>();
        zeros.getItems().addAll("LZ - mantem zeros a esquerda", "TZ - mantem zeros a direita");
        zeros.getSelectionModel().select(initial.leadingZeros() ? 0 : 1);
        ComboBox<String> slots = new ComboBox<>();
        slots.getItems().addAll("Roteado (G00/M15/G01/M16)", "Furado (G85)");
        slots.getSelectionModel().select(initial.slots() == ExcellonExporter.SlotStyle.ROUTED ? 0 : 1);
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
        grid.addRow(2, new Label("Digitos inteiros:"), integerDigits);
        grid.addRow(3, new Label("Digitos decimais:"), decimalDigits);
        grid.addRow(4, new Label("Zeros:"), zeros);
        grid.addRow(5, new Label("Slots:"), slots);
        grid.add(note(objectUnits), 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button.getButtonData() != ButtonBar.ButtonData.OK_DONE ? null
                : new ExcellonExporter.Format(isInch(units) ? "IN" : "MM",
                        style.getSelectionModel().getSelectedIndex() == 0,
                        integerDigits.getValue(), decimalDigits.getValue(),
                        zeros.getSelectionModel().getSelectedIndex() == 0,
                        slots.getSelectionModel().getSelectedIndex() == 0
                                ? ExcellonExporter.SlotStyle.ROUTED : ExcellonExporter.SlotStyle.G85));
        Optional<ExcellonExporter.Format> result = dialog.showAndWait();
        result.ifPresent(format -> AppPreferences.saveText(EXCELLON_KEY, format.units() + ";"
                + (format.decimal() ? "dec" : "ndec") + ";" + format.integerDigits() + ";"
                + format.decimalDigits() + ";" + (format.leadingZeros() ? "LZ" : "TZ") + ";"
                + format.slots().name()));
        return result;
    }

    private static GerberExporter.Format loadGerber() {
        String saved = AppPreferences.loadText(GERBER_KEY);
        try {
            String[] parts = saved.split(";");
            return new GerberExporter.Format(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    "L".equals(parts[3]));
        } catch (RuntimeException missingOrStale) {
            return GerberExporter.Format.flatcamDefaults();
        }
    }

    private static ExcellonExporter.Format loadExcellon() {
        String saved = AppPreferences.loadText(EXCELLON_KEY);
        try {
            String[] parts = saved.split(";");
            return new ExcellonExporter.Format(parts[0], "dec".equals(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), "LZ".equals(parts[4]),
                    ExcellonExporter.SlotStyle.valueOf(parts[5]));
        } catch (RuntimeException missingOrStale) {
            return ExcellonExporter.Format.flatcamDefaults();
        }
    }

    private static <T> Dialog<T> dialog(Window owner, String title) {
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        FluidTooltips.install(dialog, title);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Exportar...", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
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
                + "; as coordenadas sao convertidas para as unidades escolhidas.");
        note.setWrapText(true);
        note.setMaxWidth(320);
        return note;
    }
}
