package org.flatcam.app.project;

import java.util.*;
import org.flatcam.cam.gcode.*;
import org.json.JSONObject;

/** Explicit DB suggestions for a whole CNC job. Missing is not the same as explicit None/automatic. */
public record CncJobDefaults(Map<Field, String> values) {
    public enum Field {
        PREPROCESSOR("ppname_g", "Preprocessor"), RAPID_FEED("feedrate_rapid", "Feed rapids"),
        TOOL_CHANGE("toolchange", "Tool change"), START_Z("startz", "Start Z"), END_Z("endz", "End Z"),
        END_XY("endxy", "End X,Y"), CHANGE_Z("toolchangez", "Tool change Z"), CHANGE_XY("toolchangexy", "Tool change X,Y");
        public final String legacyKey, label;
        Field(String key, String label) { legacyKey = key; this.label = label; }
    }
    public static final CncJobDefaults EMPTY = new CncJobDefaults(Map.of());
    public CncJobDefaults {
        var normalized = new EnumMap<Field, String>(Field.class);
        values.forEach((field, value) -> normalized.put(field, normalize(field, Objects.requireNonNull(value))));
        values = Map.copyOf(normalized);
        // Validate even partial coordinates/heights, independently of a machine profile.
        positions(values, GeometryJobOptions.AUTOMATIC);
    }
    public boolean isEmpty() { return values.isEmpty(); }
    public boolean has(Field field) { return values.containsKey(field); }
    public String value(Field field) { return values.get(field); }
    public GCodePreprocessor preprocessor() { return has(Field.PREPROCESSOR) ? GCodePreprocessor.valueOf(value(Field.PREPROCESSOR)) : null; }
    public GeometryJobOptions positions(GeometryJobOptions base) { return positions(values, base); }

    public static CncJobDefaults fromLegacy(JSONObject data, boolean drilling) {
        var values = new EnumMap<Field, String>(Field.class);
        for (Field field : Field.values()) {
            String key = drilling ? field == Field.PREPROCESSOR ? "tools_drill_ppname_e" : "tools_drill_" + field.legacyKey : field.legacyKey;
            if (data.has(key)) values.put(field, data.isNull(key) ? "None" : data.get(key).toString());
        }
        var result = new CncJobDefaults(values);
        if (drilling && result.preprocessor() != null && !GCodePreprocessor.millingProfiles().contains(result.preprocessor()))
            throw new IllegalArgumentException("Perfil da DB nao suportado para Drilling.");
        return result;
    }
    public JSONObject toJson() {
        var json = new JSONObject(); values.forEach((field, value) -> json.put(field.name(), value)); return json;
    }
    public static CncJobDefaults fromJson(JSONObject json) {
        var values = new EnumMap<Field, String>(Field.class);
        for (String key : json.keySet()) values.put(Field.valueOf(key), json.getString(key));
        return new CncJobDefaults(values);
    }
    public record Resolution(CncJobDefaults agreed, Map<Field, List<Integer>> conflicts) {
        public Resolution { conflicts = Map.copyOf(conflicts); }
        public String description() {
            return Arrays.stream(Field.values()).filter(conflicts::containsKey)
                    .map(field -> field.label + " (ferramentas " + conflicts.get(field) + ")")
                    .collect(java.util.stream.Collectors.joining("; "));
        }
    }
    /** Absence does not veto another tool's explicit job value; disagreements never pick the first cutter. */
    public static Resolution resolve(Map<Integer, CncJobDefaults> tools) {
        var agreed = new EnumMap<Field, String>(Field.class);
        var conflicts = new EnumMap<Field, List<Integer>>(Field.class);
        for (Field field : Field.values()) {
            var supplied = new TreeMap<Integer, String>();
            tools.forEach((id, settings) -> { if (settings.has(field)) supplied.put(id, settings.value(field)); });
            if (new HashSet<>(supplied.values()).size() > 1) conflicts.put(field, List.copyOf(supplied.keySet()));
            else if (!supplied.isEmpty()) agreed.put(field, supplied.firstEntry().getValue());
        }
        return new Resolution(new CncJobDefaults(agreed), conflicts);
    }
    private static String normalize(Field field, String raw) {
        String text = raw.trim();
        if (field == Field.PREPROCESSOR) {
            for (var profile : GCodePreprocessor.geometryProfiles())
                if (profile.name().equalsIgnoreCase(text)) return profile.name();
            for (var profile : GCodePreprocessor.geometryProfiles())
                if (profile != GCodePreprocessor.FX_PORTABLE && PythonProjectWriter.pythonProfile(profile).equalsIgnoreCase(text)) return profile.name();
            throw new IllegalArgumentException("Preprocessor da DB desconhecido: " + text);
        }
        if (field == Field.TOOL_CHANGE) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("Tool change da DB exige true/false.");
            return Boolean.toString(Boolean.parseBoolean(text));
        }
        boolean none = text.isEmpty() || text.equalsIgnoreCase("None") || text.equalsIgnoreCase("null");
        if (field == Field.END_XY || field == Field.CHANGE_XY) {
            if (none) return "None";
            String[] xy = text.replace("(", "").replace(")", "").replace("[", "").replace("]", "")
                    .split(text.contains(";") ? ";" : ",", -1);
            if (xy.length != 2) throw new IllegalArgumentException(field.label + ": use None ou X,Y.");
            return number(xy[0]) + "," + number(xy[1]);
        }
        if (none && field != Field.RAPID_FEED) return "None";
        double value = Double.parseDouble(number(text));
        if (value < 0 || field == Field.CHANGE_Z && value == 0)
            throw new IllegalArgumentException(field.label + " da DB deve ser " + (field == Field.CHANGE_Z ? "positivo." : "nao negativo."));
        return Double.toString(value);
    }
    private static String number(String value) {
        double number = Double.parseDouble(value.trim().replace(',', '.'));
        if (!Double.isFinite(number)) throw new IllegalArgumentException("DB exige coordenadas/valores finitos.");
        return Double.toString(number == 0 ? 0 : number);
    }
    private static Double optional(String value) { return value.equals("None") ? null : Double.valueOf(value); }
    private static Double scalar(Map<Field, String> values, Field field, Double fallback) {
        return values.containsKey(field) ? optional(values.get(field)) : fallback;
    }
    private static Double[] xy(Map<Field, String> values, Field field, Double x, Double y) {
        if (!values.containsKey(field)) return new Double[]{x, y};
        if (values.get(field).equals("None")) return new Double[]{null, null};
        String[] coordinates = values.get(field).split(",");
        return new Double[]{Double.valueOf(coordinates[0]), Double.valueOf(coordinates[1])};
    }
    private static GeometryJobOptions positions(Map<Field, String> values, GeometryJobOptions base) {
        var end = xy(values, Field.END_XY, base.endX(), base.endY());
        var change = xy(values, Field.CHANGE_XY, base.toolChangeX(), base.toolChangeY());
        return new GeometryJobOptions(scalar(values, Field.START_Z, base.startZ()), scalar(values, Field.END_Z, base.endZ()), end[0], end[1],
                scalar(values, Field.CHANGE_Z, base.toolChangeZ()), change[0], change[1], base.exclusionsEnabled(), base.exclusions());
    }
}
