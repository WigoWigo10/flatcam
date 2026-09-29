package org.flatcam.cam.excellon;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * Writes the current drill and slot data, including edits, as Excellon.
 *
 * <p>{@link #export(ExcellonImage)} keeps the image's units with exact decimal
 * coordinates and G85 slots; {@link #export(ExcellonImage, Format)} writes the
 * File > Export > Excellon format chosen by the user (Python's excellon_exp_*
 * preferences). Unlike Python's writer, routed slots go from the real start to
 * the real end, and zero suppression follows the header it declares.
 */
public final class ExcellonExporter {

    /** How slots are written: a routed G00/M15/G01/M16 move, or a single G85 line. */
    public enum SlotStyle { ROUTED, G85 }

    /**
     * Output units and coordinate format. With {@code decimal} false the
     * coordinates have no decimal point: {@code leadingZeros} true writes
     * "LZ" (leading zeros kept, full width), false writes "TZ" (leading zeros
     * dropped, trailing kept). The digit counts go in a ;FILE_FORMAT comment.
     */
    public record Format(String units, boolean decimal, int integerDigits, int decimalDigits,
                         boolean leadingZeros, SlotStyle slots) {
        public Format {
            if (!"MM".equals(units) && !"IN".equals(units)) {
                throw new IllegalArgumentException("Unsupported Excellon units: " + units);
            }
            if (integerDigits < 1 || integerDigits > 6 || decimalDigits < 1 || decimalDigits > 6) {
                throw new IllegalArgumentException("Excellon digits must be between 1 and 6");
            }
            Objects.requireNonNull(slots, "slots");
        }

        /** FlatCAM Python's export defaults: inch, decimal, 2:4, LZ, routed slots. */
        public static Format flatcamDefaults() {
            return new Format("IN", true, 2, 4, true, SlotStyle.ROUTED);
        }
    }

    /** Publishes a complete file without truncating an existing destination on failure. */
    public void write(ExcellonImage image, Path path) throws IOException {
        writeText(export(image), path);
    }

    /** {@link #write(ExcellonImage, Path)} in a user-chosen format. */
    public void write(ExcellonImage image, Format format, Path path) throws IOException {
        writeText(export(image, format), path);
    }

    private static void writeText(String excellon, Path path) throws IOException {
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
        String units = requireSupportedUnits(image);
        String header = "MM".equals(units) ? "METRIC\n" : "INCH\n";
        return export(image, header, ExcellonExporter::decimal, ExcellonExporter::decimal, SlotStyle.G85);
    }

    public String export(ExcellonImage image, Format format) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(format, "format");
        double factor = requireSupportedUnits(image).equals(format.units()) ? 1
                : "MM".equals(format.units()) ? 25.4 : 1 / 25.4;
        String unitsWord = "MM".equals(format.units()) ? "METRIC" : "INCH";
        String header = format.decimal() ? unitsWord + "\n"
                : ";FILE_FORMAT=" + format.integerDigits() + ":" + format.decimalDigits() + "\n"
                        + unitsWord + (format.leadingZeros() ? ",LZ" : ",TZ") + "\n";
        int diameterDecimals = Math.max(4, format.decimalDigits());
        DoubleFunction<String> diameter = value -> fixed(value * factor, diameterDecimals);
        DoubleFunction<String> coordinate = format.decimal()
                ? value -> fixed(value * factor, format.decimalDigits())
                : value -> suppressed(value * factor, format);
        return export(image, header, diameter, coordinate, format.slots());
    }

    private static String requireSupportedUnits(ExcellonImage image) {
        String units = image.units();
        if (!"MM".equals(units) && !"IN".equals(units)) {
            throw new IllegalArgumentException("Unsupported Excellon units: " + units);
        }
        return units;
    }

    private static String export(ExcellonImage image, String unitsHeader, DoubleFunction<String> diameter,
                                 DoubleFunction<String> coordinate, SlotStyle slotStyle) {
        Map<Integer, Double> diameters = image.toolDiameters();
        for (ExcellonImage.Drill drill : image.drills()) {
            requireTool(diameters, drill.toolId());
        }
        for (ExcellonImage.Slot slot : image.slots()) {
            requireTool(diameters, slot.toolId());
        }
        StringBuilder output = new StringBuilder("M48\n");
        output.append(unitsHeader);
        diameters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey() <= 0 || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Invalid Excellon tool: " + entry.getKey());
            }
            output.append('T').append(entry.getKey()).append('C')
                    .append(diameter.apply(entry.getValue())).append('\n');
        });
        output.append("%\nG90\nG05\n");
        diameters.keySet().stream().sorted(Comparator.naturalOrder()).forEach(toolId -> {
            boolean hasDrills = image.drills().stream().anyMatch(drill -> drill.toolId() == toolId);
            boolean hasSlots = image.slots().stream().anyMatch(slot -> slot.toolId() == toolId);
            if (!hasDrills && !hasSlots) {
                return;
            }
            output.append('T').append(toolId).append('\n');
            for (ExcellonImage.Drill drill : image.drills()) {
                if (drill.toolId() == toolId) {
                    output.append(position(coordinate, drill.x(), drill.y())).append('\n');
                }
            }
            for (ExcellonImage.Slot slot : image.slots()) {
                if (slot.toolId() != toolId) {
                    continue;
                }
                String start = position(coordinate, slot.x1(), slot.y1());
                String end = position(coordinate, slot.x2(), slot.y2());
                if (slotStyle == SlotStyle.ROUTED) {
                    output.append("G00").append(start).append("\nM15\nG01").append(end).append("\nM16\nG05\n");
                } else {
                    output.append(start).append("G85").append(end).append('\n');
                }
            }
        });
        output.append("M30\n");
        return output.toString();
    }

    private static void requireTool(Map<Integer, Double> diameters, int toolId) {
        if (!diameters.containsKey(toolId)) {
            throw new IllegalArgumentException("Excellon hit references undefined tool: " + toolId);
        }
    }

    private static String position(DoubleFunction<String> coordinate, double x, double y) {
        return "X" + coordinate.apply(x) + "Y" + coordinate.apply(y);
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(requireFinite(value)).toPlainString();
    }

    private static String fixed(double value, int decimals) {
        return BigDecimal.valueOf(requireFinite(value)).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    /** No decimal point: LZ keeps the full integer:decimal width, TZ drops the leading zeros. */
    private static String suppressed(double value, Format format) {
        long scaled = BigDecimal.valueOf(requireFinite(value)).movePointRight(format.decimalDigits())
                .setScale(0, RoundingMode.HALF_UP).longValue();
        String digits = Long.toString(Math.abs(scaled));
        int width = format.integerDigits() + format.decimalDigits();
        if (digits.length() > width) {
            throw new IllegalArgumentException("Excellon coordinate " + value + " does not fit the "
                    + format.integerDigits() + ":" + format.decimalDigits() + " format");
        }
        if (format.leadingZeros()) {
            digits = "0".repeat(width - digits.length()) + digits;
        }
        return (scaled < 0 ? "-" : "") + digits;
    }

    private static double requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Excellon value is not finite: " + value);
        }
        return value;
    }
}
