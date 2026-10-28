package org.flatcam.fx;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.locationtech.jts.geom.Coordinate;

/** Numeric point entry: jumping previews; adding is a separate explicit action. */
final class ArcCoordinateDialog extends Dialog<ArcCoordinateDialog.Choice> {
    record Choice(Coordinate point, boolean insert, boolean relative) {
        Choice(Coordinate point, boolean insert) { this(point, insert, false); }
    }
    final ButtonType preview = new ButtonType(I18n.text("ui.3ad567764730"), ButtonBar.ButtonData.OK_DONE);
    final ButtonType insert = new ButtonType(I18n.text("ui.fb884fbbec04"), ButtonBar.ButtonData.APPLY);
    final TextField x = new TextField(), y = new TextField();
    final CheckBox relative = new CheckBox(I18n.text("ui.9850f6477113"));
    final Label error = new Label();

    ArcCoordinateDialog(Window owner, Coordinate reference, String units) {
        this(owner, reference, units, true, false);
    }

    ArcCoordinateDialog(Window owner, Coordinate reference, String units, boolean allowInsert, boolean relativeInitially) {
        setTitle(allowInsert ? I18n.text("ui.caa293fe6d16") : I18n.text("ui.939467b35f12"));
        if (owner != null) initOwner(owner);
        Coordinate origin = new Coordinate(reference);
        x.setId("arc-coordinate-x"); y.setId("arc-coordinate-y"); relative.setId("arc-coordinate-relative");
        x.setText(number(origin.x)); y.setText(number(origin.y));
        for (TextField field : new TextField[]{x,y}) { field.setPrefColumnCount(12); field.setMinWidth(0); }
        relative.selectedProperty().addListener((obs, old, selected) -> {
            x.setText(selected ? "0" : number(origin.x)); y.setText(selected ? "0" : number(origin.y)); error.setText("");
        });
        relative.setSelected(relativeInitially);
        ToolDescriptions.apply(x,I18n.text("ui.a6a7266b135b"),I18n.text("ui.28d114c3cfa3") + units + I18n.text("ui.547c25f48225"));
        ToolDescriptions.apply(y,I18n.text("ui.ec01def5acef"),I18n.text("ui.8546e6138537") + units + I18n.text("ui.a36918619d43"));
        ToolDescriptions.apply(relative,I18n.text("ui.2f82e2a1829e"),
                I18n.text("ui.40739c533b68"));
        Label referenceLabel = new Label(I18n.text("ui.5b5c802661c7") + number(origin.x) + "; Y " + number(origin.y) + " [" + units + "]");
        referenceLabel.setWrapText(true);
        Label note = new Label(I18n.text("ui.1e0a86569b50")
                + (allowInsert ? I18n.text("ui.8fbd4ad60a5f") : I18n.text("ui.4addd664a185")));
        note.setWrapText(true); note.setMaxWidth(440);
        error.getStyleClass().add("form-error-label"); error.setWrapText(true); error.setId("arc-coordinate-error");
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(12));
        Label lx = new Label("X [" + units + "]:"), ly = new Label("Y [" + units + "]:");
        lx.setLabelFor(x); ly.setLabelFor(y);
        grid.add(referenceLabel,0,0,2,1); grid.addRow(1,lx,x); grid.addRow(2,ly,y);
        grid.add(relative,0,3,2,1); grid.add(note,0,4,2,1); grid.add(error,0,5,2,1);
        getDialogPane().setContent(grid);
        ButtonType cancel = new ButtonType(I18n.text("ui.bb9dbb406dcb"),ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(preview);
        if (allowInsert) getDialogPane().getButtonTypes().add(insert);
        getDialogPane().getButtonTypes().add(cancel);
        for (ButtonType action : allowInsert ? new ButtonType[]{preview,insert} : new ButtonType[]{preview}) {
            getDialogPane().lookupButton(action).addEventFilter(ActionEvent.ACTION,event -> {
                try { resolve(x.getText(),y.getText(),relative.isSelected(),origin); error.setText(""); }
                catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); event.consume(); }
            });
        }
        ToolDescriptions.apply(getDialogPane().lookupButton(preview),I18n.text("ui.3ad567764730"),
                I18n.text("ui.3d6b6c3d4283"));
        if (allowInsert) ToolDescriptions.apply(getDialogPane().lookupButton(insert),I18n.text("ui.8b24c9002148"),
                I18n.text("ui.378558df8445"));
        ToolDescriptions.apply(getDialogPane().lookupButton(cancel),I18n.text("ui.96db7435072e"),I18n.text("ui.34690a1f11a0"));
        setResultConverter(action -> action == preview || allowInsert && action == insert
                ? new Choice(resolve(x.getText(),y.getText(),relative.isSelected(),origin),action == insert,relative.isSelected()) : null);
        if (owner != null && owner.getScene() != null)
            getDialogPane().getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        FluidTooltips.install(this,allowInsert ? I18n.text("ui.caa293fe6d16") : I18n.text("ui.939467b35f12"));
    }

    static Coordinate resolve(String x, String y, boolean relative, Coordinate origin) {
        try {
            double px = Double.parseDouble(x.trim().replace(',','.'));
            double py = Double.parseDouble(y.trim().replace(',','.'));
            if (relative) { px += origin.x; py += origin.y; }
            if (!Double.isFinite(px) || !Double.isFinite(py)) throw new NumberFormatException();
            return new Coordinate(px,py);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(I18n.text("ui.213f26cc7f04"));
        }
    }

    private static String number(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
