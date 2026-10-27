package org.flatcam.app.project;

import org.json.JSONObject;

/** Presentation only; independent of G-code and machine parameters. */
public record CncPlotSettings(String cutFill, String cutLine, String travelFill, String travelLine,
                              String kind, boolean annotations, boolean arrows, int fontSize, String fontColor) {
    public CncPlotSettings {
        for (String color : new String[]{cutFill,cutLine,travelFill,travelLine,fontColor})
            if (color == null || !color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Cor CNC inválida");
        if (!java.util.Set.of("ALL","CUT","TRAVEL").contains(kind)) throw new IllegalArgumentException("Plot Kind inválido");
        if (fontSize < 1 || fontSize > 72) throw new IllegalArgumentException("Fonte deve estar entre 1 e 72");
    }
    public static CncPlotSettings legacy() { return new CncPlotSettings("#5E6CFFFF","#4650BDFF","#F0E24D4C","#B5AB3A4C","ALL",true,true,11,"#990000"); }
    public JSONObject toJson() { return new JSONObject().put("cutFill",cutFill).put("cutLine",cutLine).put("travelFill",travelFill)
            .put("travelLine",travelLine).put("kind",kind).put("annotations",annotations).put("arrows",arrows).put("fontSize",fontSize).put("fontColor",fontColor); }
    public static CncPlotSettings fromJson(JSONObject json) {
        if (json == null) return null;
        var d=legacy(); return new CncPlotSettings(json.optString("cutFill",d.cutFill),json.optString("cutLine",d.cutLine),
                json.optString("travelFill",d.travelFill),json.optString("travelLine",d.travelLine),json.optString("kind","ALL"),
                json.optBoolean("annotations",true),json.optBoolean("arrows",true),json.optInt("fontSize",11),json.optString("fontColor",d.fontColor));
    }
}
