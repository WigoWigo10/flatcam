package org.flatcam.fx;

import java.util.*;
import java.util.function.Consumer;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.flatcam.cam.gcode.ClearingRoutePreview;

/** Read-only route summary; its visibility switches do not edit the CAM recipe. */
final class ClearingPreviewFeedback extends VBox {
    private static final String KEY=ClearingPreviewFeedback.class.getName();
    private final Label summary=new Label(),assumptions=new Label(I18n.text("cam.preview.assumptions"));
    private final CheckBox cuts=new CheckBox(I18n.text("cam.preview.cuts")),rapid=new CheckBox(I18n.text("cam.preview.rapid")),entries=new CheckBox(I18n.text("cam.preview.entries"));
    private final VBox rows=new VBox(3);
    private Consumer<List<PlotAreaView.PreviewLayer>> publish;
    private ClearingRoutePreview result;
    ClearingPreviewFeedback(VBox form) {
        super(5);setId("clearing-preview-summary");setMinWidth(0);
        summary.setWrapText(true);summary.setId("clearing-preview-metrics");summary.setMinWidth(0);assumptions.setWrapText(true);assumptions.setMinWidth(0);
        var legend=new FlowPane(8,5);boolean dark=true;
        for(var check:List.of(cuts,rapid,entries)) {
            check.setSelected(true);check.setId(check==cuts?"clearing-preview-cuts":check==rapid?"clearing-preview-rapid":"clearing-preview-entries");
            check.setOnAction(e->redraw());ToolDescriptions.apply(check,check.getText(),I18n.text("cam.preview.legend.help"));
            var role=check==cuts?PlotAreaView.PreviewRole.CONTENT:check==rapid?PlotAreaView.PreviewRole.RAPID:PlotAreaView.PreviewRole.ENTRY;
            var sample=new Rectangle(10,10,PlotAreaView.clearingPreviewColors(role,dark).stroke());
            sample.setStyle("-fx-fill: -fc-cam-preview-"+role.name().toLowerCase(java.util.Locale.ROOT)+";");
            legend.getChildren().add(new HBox(4,sample,check));
        }
        var detail=new TitledPane(I18n.text("cam.preview.tools"),rows);detail.setExpanded(false);
        getChildren().addAll(summary,legend,assumptions,detail);ToolDescriptions.apply(summary,I18n.text("cam.preview.paths"),I18n.text("cam.preview.summary.help"));
        ToolDescriptions.apply(assumptions,I18n.text("cam.preview.paths"),I18n.text("cam.preview.summary.help"));
        setVisible(false);managedProperty().bind(visibleProperty());form.getProperties().put(KEY,this);
    }
    static ClearingPreviewFeedback of(Node panel){Node form=panel instanceof ScrollPane p?p.getContent():panel;return form==null?null:(ClearingPreviewFeedback)form.getProperties().get(KEY);}
    void clear(){result=null;publish=null;setVisible(false);rows.getChildren().clear();}
    void complete(ClearingRoutePreview preview,Consumer<List<PlotAreaView.PreviewLayer>> publish){
        this.result=preview;this.publish=publish;String units=preview.units();
        summary.setText(String.format(java.util.Locale.ROOT,I18n.text("cam.preview.metrics"),preview.entryCount(),length(preview.cutLength(),units),units,
                length(preview.travelLength(),units),units,String.format(java.util.Locale.ROOT,"%.2f",preview.xyMinutes())));
        rows.getChildren().clear();
        for(var tool:preview.tools()) {var label=new Label(String.format(java.util.Locale.ROOT,I18n.text("cam.preview.tool"),CncPreflight.number(tool.diameter()),units,tool.entries(),length(tool.cutLength(),units),units));label.setWrapText(true);label.setMinWidth(0);rows.getChildren().add(label);}
        var order=new Label(I18n.text(preview.sourceOrder()?"cam.preview.order.source":"cam.preview.order.nearest"));order.setWrapText(true);rows.getChildren().add(order);
        setVisible(true);redraw();
    }
    private void redraw(){
        if(result==null||publish==null)return;var layers=new ArrayList<PlotAreaView.PreviewLayer>();
        if(cuts.isSelected())layers.add(new PlotAreaView.PreviewLayer(result.cuts(),PlotAreaView.LayerCategory.GEOMETRY,true));
        if(rapid.isSelected())layers.add(new PlotAreaView.PreviewLayer(result.travel(),PlotAreaView.LayerCategory.GEOMETRY,true,PlotAreaView.PreviewRole.RAPID));
        if(entries.isSelected())layers.add(new PlotAreaView.PreviewLayer(result.entries(),PlotAreaView.LayerCategory.GEOMETRY,true,PlotAreaView.PreviewRole.ENTRY));
        publish.accept(layers);
    }
    private static String length(double value,String units){return String.format(java.util.Locale.ROOT,"IN".equalsIgnoreCase(units)?"%.4f":"%.2f",value);}
    /** Controls added by skins are irrelevant; table edits/selection and area-status changes are watched separately. */
    static void watchEdits(Node node,Runnable invalidate){
        if(node instanceof ClearingPreviewFeedback||node instanceof CamGenerationFeedback||node instanceof ClearingRegionFeedback)return;
        if(node instanceof TextInputControl c)c.textProperty().addListener((o,a,b)->invalidate.run());
        if(node instanceof ComboBox<?> c)c.valueProperty().addListener((o,a,b)->invalidate.run());
        if(node instanceof CheckBox c)c.selectedProperty().addListener((o,a,b)->invalidate.run());
        if(node instanceof RadioButton c)c.selectedProperty().addListener((o,a,b)->invalidate.run());
        if(node instanceof Spinner<?> s) {s.valueProperty().addListener((o,a,b)->invalidate.run());s.getEditor().textProperty().addListener((o,a,b)->invalidate.run());}
        if(node instanceof TableView<?> table) {
            table.getItems().addListener((javafx.collections.ListChangeListener<Object>)c->invalidate.run());
            table.getSelectionModel().getSelectedIndices().addListener((javafx.collections.ListChangeListener<Integer>)c->invalidate.run());
            table.editingCellProperty().addListener((o,a,b)->invalidate.run());return;
        }
        if(node instanceof TitledPane pane){if(pane.getContent()!=null)watchEdits(pane.getContent(),invalidate);return;}
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())watchEdits(child,invalidate);
    }
}
