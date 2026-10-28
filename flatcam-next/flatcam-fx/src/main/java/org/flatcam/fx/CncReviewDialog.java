package org.flatcam.fx;

import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Confirmation before the FileChooser, not before machine execution. Closing never exports anything. */
final class CncReviewDialog extends Dialog<CncReviewDialog.Decision> {
    record Decision(boolean proceed, CncPreflight.Target edit) { }
    static final ButtonType CONTINUE = new ButtonType(I18n.text("ui.10f677c8abe5"), ButtonBar.ButtonData.OK_DONE);
    static final ButtonType COVERAGE = new ButtonType(I18n.text("ui.71ae9466a9ee"), ButtonBar.ButtonData.LEFT);
    private final GridPane toolDetails = new GridPane();

    CncReviewDialog(Window owner, ThemeOption theme, CncPreflight.Report report) {
        this(owner, theme, report, false);
    }
    CncReviewDialog(Window owner, ThemeOption theme, CncPreflight.Report report, boolean coverageAvailable) {
        setTitle(I18n.text("ui.543922def3d4")); setHeaderText(null); setResizable(true);
        if (owner != null) initOwner(owner);
        var pane = getDialogPane(); pane.setId("cnc-review-dialog"); pane.setPrefSize(900, 650); pane.setMinSize(460, 360);
        Label title = label(report.operation() + " — " + report.source()); title.getStyleClass().add("tool-title");
        Label units = label(I18n.text("ui.bd8a46133e9b") + report.units() + I18n.text("ui.6e1678973423") + report.units() + I18n.text("ui.10e6744e3390") + report.profile());
        boolean editable=report.operation().startsWith("Geometry") || report.operation().startsWith("Excellon");
        Label explanation = label(editable ? I18n.text("ui.56e6fcdb4a03")
                : I18n.text("ui.7bb21a31f7ce"));
        toolDetails.setHgap(12); toolDetails.setVgap(5);
        var detailLeft = new ColumnConstraints(); detailLeft.setPercentWidth(50);
        var detailRight = new ColumnConstraints(); detailRight.setPercentWidth(50);
        toolDetails.getColumnConstraints().setAll(detailLeft, detailRight);
        TableView<CncPreflight.Tool> table = new TableView<>(); table.setId("cnc-review-tools");
        table.getItems().setAll(report.tools()); table.setMinWidth(0); table.setFixedCellSize(UiFonts.rowHeight(28));
        table.setPrefHeight(Math.min(200, 32 + UiFonts.rowHeight(28) * Math.max(1, report.tools().size())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        column(table, I18n.text("ui.c188272ba40c"), CncPreflight.Tool::name);
        column(table, I18n.text("ui.7cd23abc62e0"), CncPreflight.Tool::diameter);
        column(table, editable ? I18n.text("ui.c0562c5f2cda") : I18n.text("ui.90328cb11fb3"), CncPreflight.Tool::cutZ);
        column(table, "Travel Z", CncPreflight.Tool::travelZ);
        column(table, I18n.text("ui.d881fcdc3afc"), CncPreflight.Tool::feedXY);
        column(table, I18n.text("ui.29bf22d30e0f"), CncPreflight.Tool::feedZ);
        column(table, I18n.text("ui.2e4d5935be81"), CncPreflight.Tool::power);
        ToolDescriptions.apply(table, I18n.text("ui.7b196c8f4209"), editable
                ? I18n.text("ui.1469b5aa0969")
                : I18n.text("ui.282ecc5f2b3a"));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            toolDetails.getChildren().clear();
            if (selected != null) {
                var entries = new java.util.ArrayList<>(selected.details());
                String prefix = report.operation().startsWith("Geometry") ? "cnc" : "drill";
                entries.add(new CncPreflight.Value("Travel Z", selected.travelZ(), editable ? new CncPreflight.Target(selected.key(), prefix.equals("cnc") ? "cnc-safe-z" : "drill-travel-z") : null));
                if (!selected.feedXY().equals("—")) entries.add(new CncPreflight.Value(I18n.text("ui.d881fcdc3afc"), selected.feedXY(), editable ? new CncPreflight.Target(selected.key(), "cnc-feed") : null));
                entries.add(new CncPreflight.Value(I18n.text("ui.29bf22d30e0f"), selected.feedZ(), editable ? new CncPreflight.Target(selected.key(), prefix + "-feed-z") : null));
                entries.add(new CncPreflight.Value(I18n.text("ui.2e4d5935be81"), selected.power(), editable ? new CncPreflight.Target(selected.key(), prefix + "-power") : null));
                for (int i = 0; i < entries.size(); i++) toolDetails.add(editLink(entries.get(i)), i % 2, i / 2);
            }
        });
        table.getSelectionModel().selectFirst();
        VBox common = new VBox(5); report.common().forEach(value -> common.getChildren().add(editLink(value)));
        VBox notices = new VBox(7);
        for (var issue : report.issues()) {
            String text = (issue.blocking() ? I18n.text("ui.b44b61b06f0f") : I18n.text("ui.3e9a08c5414d")) + issue.text();
            if (coverageAvailable && issue.text().startsWith(I18n.text("ui.461dbd8fec29")))
                text = I18n.text("ui.567b0923085c");
            if (issue.target() == null) notices.getChildren().add(label(text));
            else {
                Hyperlink link = (Hyperlink)editLink(new CncPreflight.Value(text, I18n.text("ui.132aa00e57c3"), issue.target()));
                if (issue.blocking()) link.getStyleClass().add("form-error-label");
                notices.getChildren().add(link);
            }
        }
        var commonPane = new TitledPane(I18n.text("ui.49c79a652119"), common); commonPane.setExpanded(false);
        var toolPane = new TitledPane(I18n.text("ui.015f5f9b322e"), toolDetails); toolPane.setExpanded(true);
        var warningPane = new TitledPane(I18n.text("ui.a18085fe3079") + report.issues().size() + ")", notices); warningPane.setExpanded(true);
        ToolDescriptions.apply(commonPane,I18n.text("ui.990ba4954df0"), I18n.text("ui.f432033febd0"));
        ToolDescriptions.apply(toolPane,I18n.text("ui.2da626411010"), I18n.text("ui.c089d9a686b6"));
        ToolDescriptions.apply(warningPane,I18n.text("ui.c6aa1e7effae"), I18n.text("ui.e9b2e3f2f53e"));
        VBox body = new VBox(10, title, units, explanation, table, warningPane, toolPane, commonPane,
                label(I18n.text("ui.afe90dba4cb8")));
        body.setPadding(new Insets(8)); body.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setMinSize(0, 0); pane.setContent(scroll);
        var back = new ButtonType(editable ? I18n.text("ui.68064809295a") : I18n.text("ui.8b5197dc2156"), ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(CONTINUE, back);
        if (coverageAvailable) {
            pane.getButtonTypes().addFirst(COVERAGE);
            ToolDescriptions.apply(pane.lookupButton(COVERAGE), I18n.text("ui.6b5b6d3585f6"), I18n.text("ui.c54ee1fb12f5"));
        }
        pane.lookupButton(CONTINUE).setDisable(!report.canContinue());
        ToolDescriptions.apply(pane.lookupButton(CONTINUE), I18n.text("ui.ec49e9758a91"), I18n.text("ui.f37df31c1ceb"));
        ToolDescriptions.apply(pane.lookupButton(back), I18n.text("ui.fcdae5bdf9cf"), I18n.text("ui.8f92b25dcfb3"));
        setResultConverter(button -> button == CONTINUE ? new Decision(true, null)
                : button == COVERAGE ? new Decision(false, new CncPreflight.Target(-1, "__coverage")) : null);
        theme.applyTo(pane.getScene()); FluidTooltips.install(this, I18n.text("ui.558866f33360"));
    }

    private javafx.scene.Node editLink(CncPreflight.Value value) {
        if (value.target()==null) {
            var text=label(value.label()+": "+value.text());
            ToolDescriptions.apply(text,I18n.text("ui.34383e2ab6ea"), I18n.text("ui.9608551ada49"));
            return text;
        }
        Hyperlink link = new Hyperlink(value.label() + ": " + value.text()); link.setWrapText(true); link.setMinWidth(0);
        link.setMaxWidth(Double.MAX_VALUE); link.setUserData(value.target());
        ToolDescriptions.apply(link, I18n.text("ui.9db0c14b4ba2"), I18n.text("ui.e544e01d4962"));
        link.setOnAction(event -> { setResult(new Decision(false, value.target())); close(); }); return link;
    }
    private static Label label(String text) {
        Label label = new Label(text); label.setWrapText(true); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
    private static void column(TableView<CncPreflight.Tool> table, String title, Function<CncPreflight.Tool, String> text) {
        var column = new TableColumn<CncPreflight.Tool, String>(title); column.setSortable(false); column.setMinWidth(48);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
        table.getColumns().add(column);
    }
}
