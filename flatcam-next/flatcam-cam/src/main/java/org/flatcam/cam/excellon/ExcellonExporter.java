package org.flatcam.cam.excellon;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Writes the current drill and slot data, including edits, as decimal-coordinate Excellon. */
public final class ExcellonExporter {

    /** Publishes a complete file without truncating an existing destination on failure. */
    public void write(ExcellonImage image, Path path) throws IOException {
        String excellon = export(image);
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(),
                "." + destination.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, excellon, StandardCharsets.US_ASCII);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public String export(ExcellonImage image) {
        Objects.requireNonNull(image, "image");
        String units = image.units();
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported Excellon units: " + units);
        }
        Map<Integer, Double> diameters = image.toolDiameters();
        StringBuilder output = new StringBuilder("M48\n");
        output.append("MM".equals(units) ? "METRIC\n" : "INCH\n");
        diameters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() <= 0 || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Invalid Excellon tool: " + entry.getKey());
            }
            output.append('T').append(entry.getKey()).append('C')
                    .append(decimal(entry.getValue())).append('\n');
        });
        output.append("%\nG90\n");
        diameters.keySet().stream().sorted(Comparator.naturalOrder()).forEach(toolId -> {
            boolean hasDrills = image.drills().stream().anyMatch(drill -> drill.toolId() == toolId);
            boolean hasSlots = image.slots().stream().anyMatch(slot -> slot.toolId() == toolId);
            if (!hasDrills && !hasSlots) {
                return;
            }
            output.append('T').append(toolId).append('\n');
            for (ExcellonImage.Drill drill : image.drills()) {
                if (drill.toolId() == toolId) {
                    output.append(position(drill.x(), drill.y())).append('\n');
                }
            }
            for (ExcellonImage.Slot slot : image.slots()) {
                if (slot.toolId() == toolId) {
                    output.append(position(slot.x1(), slot.y1())).append("G85")
                            .append(position(slot.x2(), slot.y2())).append('\n');
                }
            }
        });
        for (ExcellonImage.Drill drill : image.drills()) {
            requireTool(diameters, drill.toolId());
        }
        for (ExcellonImage.Slot slot : image.slots()) {
            requireTool(diameters, slot.toolId());
        }
        output.append("M30\n");
        return output.toString();
    }

    private static void requireTool(Map<Integer, Double> diameters, int toolId) {
        if (!diameters.containsKey(toolId)) {
            throw new IllegalArgumentException("Excellon hit references undefined tool: " + toolId);
        }
    }

    private static String position(double x, double y) {
        return "X" + decimal(x) + "Y" + decimal(y);
    }

    private static String decimal(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Excellon value is not finite: " + value);
        }
        return BigDecimal.valueOf(value).toPlainString();
    }
}
