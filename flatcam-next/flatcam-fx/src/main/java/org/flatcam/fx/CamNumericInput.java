package org.flatcam.fx;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

/** Editable CAM data must round-trip independently of the display precision and OS locale. */
final class CamNumericInput {
    private CamNumericInput() { }

    static Spinner<Double> spinner(double min, double max, double initial, double step) {
        var values = new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);
        class Converter extends StringConverter<Double> {
            String invalidDraft;
            @Override public String toString(Double value) {
                return invalidDraft != null ? invalidDraft : value == null ? "" : text(value);
            }
            @Override public Double fromString(String input) {
                try {
                    double value = ranged(input, min, max, I18n.text("ui.b2f530c46991"));
                    invalidDraft = null; return value;
                } catch (IllegalArgumentException invalid) {
                    // Focus loss commits a Spinner automatically. Keep an invalid draft visible,
                    // rather than silently clamping it or throwing on the FX event thread.
                    invalidDraft = input; return values.getValue();
                }
            }
        }
        var converter = new Converter(); values.setConverter(converter);
        values.valueProperty().addListener((obs, old, value) -> converter.invalidDraft = null);
        var spinner = new Spinner<Double>(values) {
            private boolean validDraft() {
                try { ranged(getEditor().getText(), min, max, I18n.text("ui.b2f530c46991")); return true; }
                catch (IllegalArgumentException invalid) { return false; }
            }
            @Override public void increment(int steps) { if (validDraft()) super.increment(steps); }
            @Override public void decrement(int steps) { if (validDraft()) super.decrement(steps); }
        };
        spinner.setEditable(true); spinner.setMinWidth(0); spinner.setPrefWidth(115);
        spinner.getEditor().setMinWidth(0);
        return spinner;
    }

    static double read(Spinner<Double> spinner, String label) {
        var values = (SpinnerValueFactory.DoubleSpinnerValueFactory) spinner.getValueFactory();
        return ranged(spinner.getEditor().getText(), values.getMin(), values.getMax(), label);
    }

    private static double ranged(String text, double min, double max, String label) {
        double value = number(text, label);
        if (value < min || value > max) throw new IllegalArgumentException(String.format(
                I18n.text("cam.number.range"), label, CamNumericInput.text(min), CamNumericInput.text(max)));
        return value;
    }

    static double number(String text, String label) {
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + I18n.text("ui.a8a35e0360a2"));
        }
    }

    static String text(double value) { return Double.toString(value); }
}
