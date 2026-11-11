package org.flatcam.fx;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.ncc.NccResult;
import org.locationtech.jts.geom.Geometry;

/** Historical, worker-computed Lines diagnostics; buttons only display existing result geometries. */
final class ClearingRegionFeedback extends TitledPane {
    private static final String KEY=ClearingRegionFeedback.class.getName();
    ClearingRegionFeedback(VBox form) {
        setId("clearing-region-feedback");setText(I18n.text("clearing.regions.title"));setExpanded(false);setMinWidth(0);
        setVisible(false);managedProperty().bind(visibleProperty());form.getProperties().put(KEY,this);
    }
    static ClearingRegionFeedback of(Node panel) {
        Node form=panel instanceof ScrollPane scroll?scroll.getContent():panel;
        return form==null?null:(ClearingRegionFeedback)form.getProperties().get(KEY);
    }
    void started() { setVisible(false);setContent(null); }
    void complete(NccResult result,Consumer<Geometry> highlight) {
        var content=new VBox(6);content.setMinWidth(0);content.getChildren().add(label(I18n.text("clearing.regions.limit")));
        int index=0;
        for(var tool:result.toolResults()) {
            int toolIndex=++index;var diagnostics=tool.regionDiagnostics();if(diagnostics==null)continue;
            // A tool with no paths is omitted from the generated CNC tool list; don't invent T numbers here.
            content.getChildren().add(label("Ø "+CncPreflight.number(tool.toolDiameter())+" "+result.units()));
            if(diagnostics.radialFallback()) {
                var notice=label(I18n.text("cam.spiral.fallback"));notice.setId("spiral-radial-fallback-"+toolIndex);
                ToolDescriptions.apply(notice,notice.getText(),I18n.text("cam.spiral.fallback.help"));
                content.getChildren().add(notice);
            }
            add(content,"unreachable",diagnostics.unreachableCount(),diagnostics.unreachableArea(),highlight,toolIndex);
            add(content,"supplemented",diagnostics.supplementedCount(),diagnostics.supplementedArea(),highlight,toolIndex);
            add(content,"unserved",diagnostics.unservedCount(),diagnostics.unservedArea(),highlight,toolIndex);
        }
        if(index==0 || result.toolResults().stream().noneMatch(tool->tool.regionDiagnostics()!=null)){started();return;}
        var clear=new Button(I18n.text("clearing.regions.hide"));clear.setId("clearing-regions-hide");
        clear.setMinWidth(0);clear.setMaxWidth(Double.MAX_VALUE);clear.setWrapText(true);clear.setOnAction(event->highlight.accept(null));
        ToolDescriptions.apply(clear,clear.getText(),I18n.text("clearing.regions.hide.help"));
        content.getChildren().add(clear);setContent(content);setVisible(true);
        setExpanded(result.toolResults().stream().filter(tool->tool.regionDiagnostics()!=null)
                .anyMatch(tool->{var d=tool.regionDiagnostics();return d.radialFallback()||d.unreachableCount()+d.supplementedCount()+d.unservedCount()>0;}));
    }
    private static Label label(String text){var label=new Label(text);label.setWrapText(true);label.setMinWidth(0);return label;}
    private static void add(VBox content,String category,int count,Geometry geometry,Consumer<Geometry> highlight,int tool) {
        var button=new Button(String.format(I18n.text("clearing.regions."+category),count));
        button.setId("clearing-region-"+category+"-"+tool);button.setDisable(count==0);
        button.setMinWidth(0);button.setMaxWidth(Double.MAX_VALUE);button.setWrapText(true);
        ToolDescriptions.apply(button,I18n.text("clearing.regions.title"),I18n.text("clearing.regions."+category+".help"));
        button.setOnAction(event->highlight.accept(geometry));content.getChildren().add(button);
    }
}
