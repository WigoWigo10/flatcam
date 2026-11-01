package org.flatcam.fx;

import java.util.ArrayList;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.flatcam.cam.gcode.MachiningZPolicy;

/** Operation-local normalization feedback. The worker receives the policy captured on Generate. */
final class MachiningZInput {
    private final Label note = new Label();
    private final ArrayList<String> changes = new ArrayList<>();
    private MachiningZPolicy policy;
    MachiningZInput(String id) {
        note.setId(id); note.setWrapText(true); note.getStyleClass().add("cam-generation-status"); begin();
        ToolDescriptions.apply(note,I18n.text("machining.z.title"),I18n.text("machining.z.help"));
    }
    static MachiningZPolicy currentPolicy() {
        return ToolDefaults.flag("cnc.machinist") ? MachiningZPolicy.MACHINIST : MachiningZPolicy.STANDARD;
    }
    Label view() { return note; }
    MachiningZPolicy policy() { return policy; }
    void begin() { policy = currentPolicy(); changes.clear(); refresh(); }
    double cut(String text, String label) {
        double input = CamNumericInput.number(text,label);
        if(policy==MachiningZPolicy.STANDARD && input==0) throw new IllegalArgumentException(label+": "+I18n.text("machining.z.cut.zero"));
        double output = policy.cutZ(input); report(label,input,output); return -output;
    }
    double cut(TextField field,String label) {
        double depth=cut(field.getText(),label);
        double original=CamNumericInput.number(field.getText(),label);
        if(original != -depth) field.setText(Double.toString(-depth));
        return depth;
    }
    double travel(String text,String label) {
        double input=CamNumericInput.number(text,label);
        if(policy==MachiningZPolicy.STANDARD && input==0) throw new IllegalArgumentException(label+": "+I18n.text("machining.z.travel.zero"));
        double output=policy.travelZ(input); report(label,input,output); return output;
    }
    double travel(TextField field,String label) {
        double output=travel(field.getText(),label);
        if(CamNumericInput.number(field.getText(),label)!=output) field.setText(Double.toString(output));
        return output;
    }
    private void report(String label,double before,double after) {
        if(before!=after) { changes.add(label+": "+before+" → "+after); refresh(); }
    }
    private void refresh() {
        note.setText(I18n.text(policy==MachiningZPolicy.MACHINIST ? "machining.z.machinist" : "machining.z.standard")
                +(changes.isEmpty() ? "" : "\n"+I18n.text("machining.z.converted")+"\n"+String.join("\n",changes)));
    }
}
