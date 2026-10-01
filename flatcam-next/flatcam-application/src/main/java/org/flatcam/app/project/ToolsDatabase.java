package org.flatcam.app.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Editable Python FlatDB document. Unknown fields survive edits and copies. */
public final class ToolsDatabase {
    private final TreeMap<Integer, JSONObject> entries = new TreeMap<>();
    public static final List<String> TARGETS = List.of("General", "Milling", "Drilling", "Isolation", "Paint", "NCC", "Cutout");

    public static ToolsDatabase fromJson(JSONObject root) {
        ToolsDatabase result = new ToolsDatabase();
        for (String key : root.keySet()) {
            int id;
            try { id = Integer.parseInt(key); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("ID de ferramenta invalido: " + key, error); }
            if (id <= 0 || !key.equals(Integer.toString(id))) throw new IllegalArgumentException("ID de ferramenta invalido: " + key);
            JSONObject entry = copy(root.getJSONObject(key));
            validate(entry);
            result.entries.put(id, entry);
        }
        return result;
    }

    public static ToolsDatabase load(Path path) throws IOException {
        if (Files.size(path) > 10_000_000) throw new IOException("Tools Database exceeds 10 MB");
        try { return fromJson(new JSONObject(Files.readString(path, StandardCharsets.UTF_8))); }
        catch (RuntimeException error) { throw new IOException("Tools Database invalida: " + error.getMessage(), error); }
    }

    public List<Integer> ids() { return List.copyOf(entries.keySet()); }
    public JSONObject entry(int id) {
        JSONObject entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("Ferramenta inexistente: " + id);
        return copy(entry);
    }
    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        entries.forEach((id, entry) -> root.put(id.toString(), copy(entry)));
        return root;
    }
    public void update(int id, JSONObject entry) {
        if (!entries.containsKey(id)) throw new IllegalArgumentException("Ferramenta inexistente: " + id);
        validate(entry);
        entries.put(id, copy(entry));
    }
    public int add(JSONObject entry) {
        validate(entry);
        int id = entries.isEmpty() ? 1 : Math.addExact(entries.lastKey(), 1);
        entries.put(id, copy(entry));
        return id;
    }
    public int duplicate(int id) {
        JSONObject entry = entry(id);
        entry.put("name", entry.optString("name", "Tool " + id) + "_copy");
        return add(entry);
    }
    public void remove(Collection<Integer> ids) { ids.forEach(entries::remove); }

    public static int targetIndex(Object value) {
        if (value instanceof Number n && n.doubleValue() == n.intValue() && n.intValue() >= 0 && n.intValue() < TARGETS.size())
            return n.intValue();
        for (int i = 0; i < TARGETS.size(); i++) if (TARGETS.get(i).equalsIgnoreCase(String.valueOf(value))) return i;
        return -1;
    }
    public static String targetLabel(JSONObject entry) {
        Object target = entry.getJSONObject("data").opt("tool_target");
        int index = targetIndex(target);
        return index < 0 ? String.valueOf(target) : TARGETS.get(index);
    }

    private static void validate(JSONObject entry) {
        JSONObject data = entry.getJSONObject("data");
        double diameter = entry.getDouble("tooldia");
        if (!Double.isFinite(diameter) || diameter <= 0) throw new IllegalArgumentException("Diameter deve ser positivo.");
        double min = data.optDouble("tol_min", 0), max = data.optDouble("tol_max", 0);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max < min)
            throw new IllegalArgumentException("Diameter Tolerance: Max deve ser maior ou igual a Min (ambos nao negativos).");
        if (entry.has("name") && !(entry.get("name") instanceof String)) throw new IllegalArgumentException("Name deve ser texto.");
    }

    /** Writes atomically, retaining an exact, uniquely named backup before replacement. */
    public Path save(Path requested) throws IOException {
        Path target = requested.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) throw new IOException("Nao sobrescreva um link simbolico de Tools Database.");
        if (Files.exists(target) && !Files.isRegularFile(target)) throw new IOException("O destino deve ser um arquivo de Tools Database.");
        byte[] bytes = toJson().toString(2).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 10_000_000) throw new IOException("Tools Database exceeds 10 MB");
        Path temporary = Files.createTempFile(target.getParent(), ".flatdb-", ".tmp");
        Path backup = null;
        try {
            Files.write(temporary, bytes);
            if (Files.exists(target)) {
                backup = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".bak");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) {
                // Fail safely: a non-atomic fallback could leave a partially written bank.
                throw new IOException("O sistema de arquivos nao permite salvar a base atomicamente.", error);
            }
            return backup;
        } finally { Files.deleteIfExists(temporary); }
    }

    private static JSONObject copy(JSONObject object) { return new JSONObject(object.toString()); }
}
