package org.flatcam.app.project;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Keeps a FlatCAM Python project whole when the FX saves it back. The FX models only part of what a .FlatPrj holds
 * (it has no place for the 500-odd application options, most per-object options, aperture macros, Excellon formats,
 * the CNC job's parameters...), so writing only what it models would drop the rest.
 *
 * <p>Three versions of the project are compared: {@code original} (the opened file), {@code baseline} (what the FX
 * writes for the project exactly as it opened it) and {@code current} (what it writes now). Wherever {@code current}
 * says the same as {@code baseline}, the FX changed nothing and the original value is kept, byte for byte; where they
 * differ, the FX's value is written. Objects are followed into (options, tools, a tool's data); lists and plain
 * values are taken whole. What only the original has is kept; what the FX had and no longer writes is removed.
 */
final class PythonLegacyMerge {

    /** Values derived from an object's geometry: stale once the FX changed that geometry, so not kept then. */
    private static final Set<String> DERIVED_FROM_GEOMETRY = Set.of("source_file");
    private static final Set<String> FX_KINDS = Set.of("gerber", "excellon", "geometry", "cncjob");
    private static final Set<String> GEOMETRY_KEYS = Set.of("solid_geometry", "apertures", "tools", "gcode");

    private PythonLegacyMerge() {
    }

    static JSONObject merge(JSONObject original, JSONObject baseline, JSONObject current) {
        JSONObject root = new JSONObject();
        for (String key : original.keySet()) {
            if (!key.equals("objs")) {
                root.put(key, original.get(key));
            }
        }
        for (String key : current.keySet()) {
            if (key.equals("objs") || key.equals("version")) {
                continue;   // the objects are merged below; the file keeps the version it was written with
            }
            root.put(key, value(original.opt(key), baseline.opt(key), current.get(key)));
        }

        JSONArray originals = objects(original);
        JSONArray baselines = objects(baseline);
        JSONArray currents = objects(current);
        JSONObject[] match = new JSONObject[currents.length()];   // the baseline object each current one came from
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < currents.length(); i++) {
            int found = find(baselines, used, currents.getJSONObject(i), true);
            if (found >= 0) {
                used.add(found);
                match[i] = baselines.getJSONObject(found);
            }
        }
        for (int i = 0; i < currents.length(); i++) {
            if (match[i] == null) {
                // Renamed in the FX: the same object under another name.
                int found = find(baselines, used, currents.getJSONObject(i), false);
                if (found >= 0) {
                    used.add(found);
                    match[i] = baselines.getJSONObject(found);
                }
            }
        }

        // Original order first (Python lists objects in creation order), then what is new.
        List<JSONObject> merged = new ArrayList<>();
        Set<Integer> written = new HashSet<>();
        for (int o = 0; o < originals.length(); o++) {
            JSONObject source = originals.getJSONObject(o);
            if (!FX_KINDS.contains(kind(source))) {
                merged.add(source);     // a script or document of the Python application: the FX never touches it
                continue;
            }
            for (int i = 0; i < currents.length(); i++) {
                if (!written.contains(i) && match[i] != null && sameIdentity(match[i], source)) {
                    merged.add(object(source, match[i], currents.getJSONObject(i)));
                    written.add(i);
                    break;
                }
            }
        }
        for (int i = 0; i < currents.length(); i++) {
            if (!written.contains(i)) {
                merged.add(currents.getJSONObject(i));
            }
        }
        root.put("objs", new JSONArray(merged));
        return root;
    }

    private static JSONArray objects(JSONObject root) {
        JSONArray objects = root.optJSONArray("objs");
        return objects == null ? new JSONArray() : objects;
    }

    private static String kind(JSONObject object) {
        return object.optString("kind", "");
    }

    private static String name(JSONObject object) {
        JSONObject options = object.optJSONObject("options");
        return options == null ? "" : options.optString("name", "");
    }

    private static boolean sameIdentity(JSONObject a, JSONObject b) {
        return kind(a).equals(kind(b)) && name(a).equals(name(b));
    }

    /** By kind and name; or, for a renamed object, the unused one of the same kind that is otherwise identical. */
    private static int find(JSONArray baselines, Set<Integer> used, JSONObject current, boolean byName) {
        for (int i = 0; i < baselines.length(); i++) {
            JSONObject candidate = baselines.getJSONObject(i);
            if (used.contains(i) || !kind(candidate).equals(kind(current))) {
                continue;
            }
            if (byName ? name(candidate).equals(name(current)) : sameExceptName(candidate, current)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameExceptName(JSONObject baseline, JSONObject current) {
        if (!baseline.keySet().equals(current.keySet())) {
            return false;
        }
        for (String key : baseline.keySet()) {
            Object a = baseline.get(key);
            Object b = current.get(key);
            if (key.equals("options") && a instanceof JSONObject x && b instanceof JSONObject y) {
                if (!x.keySet().equals(y.keySet())) {
                    return false;
                }
                for (String option : x.keySet()) {
                    if (!option.equals("name") && !similar(x.get(option), y.get(option))) {
                        return false;
                    }
                }
            } else if (!key.equals("_java") && !similar(a, b)) {
                return false;   // the private FX snapshot repeats the name
            }
        }
        return true;
    }

    private static JSONObject object(JSONObject original, JSONObject baseline, JSONObject current) {
        Object chosen = value(original, baseline, current);
        JSONObject merged = new JSONObject();
        for (String key : ((JSONObject) chosen).keySet()) {
            merged.put(key, ((JSONObject) chosen).get(key));
        }
        // The FX's own snapshot of the object always travels with it: the FX reopens the file from these.
        if (current.has("_java")) {
            merged.put("_java", current.get("_java"));
        }
        boolean geometryChanged = false;
        for (String key : GEOMETRY_KEYS) {
            geometryChanged |= !similar(baseline.opt(key), current.opt(key));
        }
        if (geometryChanged) {
            for (String key : DERIVED_FROM_GEOMETRY) {
                if (!current.has(key)) {
                    merged.remove(key);
                }
            }
        }
        return merged;
    }

    /** The value to write for something the FX writes now as {@code current}. */
    private static Object value(Object original, Object baseline, Object current) {
        if (baseline != null && similar(baseline, current)) {
            return original != null ? original : current;      // untouched by the FX: the file's own value
        }
        if (original instanceof JSONObject o && current instanceof JSONObject c) {
            JSONObject b = baseline instanceof JSONObject known ? known : new JSONObject();
            JSONObject merged = new JSONObject();
            for (String key : o.keySet()) {
                if (!c.has(key) && !b.has(key)) {
                    merged.put(key, o.get(key));               // only the Python application knows this one
                }
            }
            for (String key : c.keySet()) {
                merged.put(key, value(o.opt(key), b.opt(key), c.get(key)));
            }
            return merged;
        }
        return current;
    }

    private static boolean similar(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof JSONObject x) {
            return x.similar(b);
        }
        if (a instanceof JSONArray x) {
            return x.similar(b);
        }
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0 || x.toString().equals(y.toString());
        }
        return a.equals(b);
    }
}
