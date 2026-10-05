package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.tcl.TclArgs;
import org.flatcam.cam.tcl.TclCommand;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;
import org.locationtech.jts.geom.Geometry;

/**
 * Registers the FlatCAM-specific Tcl commands (the ~69 classes under Python's
 * {@code tclCommands/}) on a {@link TclInterpreter}, backed by a {@link TclFlatcamHost} rather
 * than the live FX session directly, so the argument handling and error messages here can be
 * unit-tested with a fake host. Each command mirrors one Python {@code TclCommand*.py}'s
 * {@code arg_names}/{@code option_types}/{@code required} - see each registration's doc comment
 * for exactly which flags are covered and which are deliberately not (yet): object creation/
 * lookup/deletion (open_gerber, open_excellon, new_geometry, delete/del, get_names, bbox, bounds),
 * and the CAM operations built on this port's existing generators (isolate, cutout, ncc/ncc_clear,
 * cncjob, export_gcode, write_gcode).
 */
final class TclFlatcamCommands {

    private final TclFlatcamHost host;

    TclFlatcamCommands(TclFlatcamHost host) {
        this.host = host;
    }

    void registerOn(TclInterpreter interpreter) {
        registerOpenGerber(interpreter);
        registerOpenExcellon(interpreter);
        registerNewGeometry(interpreter);
        registerDelete(interpreter);
        registerGetNames(interpreter);
        registerBbox(interpreter);
        registerBounds(interpreter);
        registerIsolate(interpreter);
        registerCutout(interpreter);
        registerNcc(interpreter);
        registerCncjob(interpreter);
        registerExportGcode(interpreter);
        registerWriteGcode(interpreter);
    }

    /** Keep the normal empty result, but disclose a name suffixed to avoid collisions. */
    private static String creationResult(String requested, String actual) {
        return requested.equals(actual) ? "" : actual;
    }

    /** Python's TclCommandOpenGerber: {@code open_gerber filename ?-outname name?}. */
    private void registerOpenGerber(TclInterpreter interpreter) {
        interpreter.register("open_gerber", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String filename = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("outname"));
            String outname = args.optionOrDefault("outname", baseName(filename));
            try {
                return creationResult(outname, host.openGerber(Path.of(filename), outname));
            } catch (IOException error) {
                throw new TclException("Could not open Gerber file: " + error.getMessage());
            }
        });
    }

    /** Python's TclCommandOpenExcellon: {@code open_excellon filename ?-outname name?}. */
    private void registerOpenExcellon(TclInterpreter interpreter) {
        interpreter.register("open_excellon", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String filename = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("outname"));
            String outname = args.optionOrDefault("outname", baseName(filename));
            try {
                return creationResult(outname, host.openExcellon(Path.of(filename), outname));
            } catch (IOException error) {
                throw new TclException("Could not open Excellon file: " + error.getMessage());
            }
        });
    }

    /** Python's TclCommandNewGeometry: {@code new_geometry ?name?} (default name "new_geo"). */
    private void registerNewGeometry(TclInterpreter interpreter) {
        interpreter.register("new_geometry", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positionalOrDefault(0, "new_geo");
            return creationResult(name, host.newEmptyGeometry(name));
        });
    }

    /**
     * Python's TclCommandDelete: {@code delete|del ?name? ?-f ?value??} - a missing name deletes
     * every object. {@code -f} (force) is accepted for compatibility but has no effect here: this
     * port has no "unsaved changes" confirmation step to force past.
     */
    private void registerDelete(TclInterpreter interpreter) {
        TclCommand delete = (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            args.rejectUnknownOptions(java.util.Set.of("f"));
            if (args.positionalCount() == 0) {
                host.deleteAll();
            } else {
                host.delete(args.positional(0));
            }
            return "";
        };
        interpreter.register("delete", delete);
        interpreter.register("del", delete);
    }

    /** Python's TclCommandGetNames: {@code get_names} - every object name, one per line. */
    private void registerGetNames(TclInterpreter interpreter) {
        interpreter.register("get_names", (interp, words) -> String.join("\n", host.objectNames()));
    }

    /**
     * Python's TclCommandBbox: {@code bbox|bounding_box name ?-outname name? ?-margin n? ?-rounded bool?}.
     * Margin/rounded default to 0/false here rather than Python's Preferences-driven defaults
     * ({@code gerber_bboxmargin}/{@code gerber_bboxrounded}) - this port has no Tcl-visible
     * preferences system yet.
     */
    private void registerBbox(TclInterpreter interpreter) {
        TclCommand bbox = (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("outname", "margin", "rounded"));
            String outname = args.optionOrDefault("outname", name + "_bbox");
            double margin = args.doubleOrDefault("margin", 0.0);
            boolean rounded = args.booleanOrDefault("rounded", false);
            return creationResult(outname, host.newBoundingBoxGeometry(name, outname, margin, rounded));
        };
        interpreter.register("bbox", bbox);
        interpreter.register("bounding_box", bbox);
    }

    /**
     * Python's TclCommandBounds: {@code bounds|get_bounds objects} where {@code objects} is a
     * comma-separated name list with no spaces. Python returns a Tcl list of 4-element lists;
     * this dialect has no real nested-list format (see TclInterpreter's class doc), so each
     * object's "minX minY maxX maxY" is returned on its own line instead - still one value per
     * object, just newline- rather than list-nested.
     */
    private void registerBounds(TclInterpreter interpreter) {
        TclCommand bounds = (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String objects = args.positional(0);
            List<String> names = new ArrayList<>();
            for (String part : objects.split(",")) {
                if (!part.isEmpty()) names.add(part);
            }
            if (names.isEmpty()) {
                throw new TclException("Expected a list of object names separated by comma. Got: " + objects);
            }
            List<String> lines = new ArrayList<>();
            for (String name : names) {
                Optional<double[]> objectBounds = host.boundsOf(name);
                if (objectBounds.isEmpty()) {
                    throw new TclException("Object not found: " + name);
                }
                double[] b = objectBounds.get();
                lines.add(b[0] + " " + b[1] + " " + b[2] + " " + b[3]);
            }
            return String.join("\n", lines);
        };
        interpreter.register("bounds", bounds);
        interpreter.register("get_bounds", bounds);
    }

    /**
     * Python's TclCommandIsolate: {@code isolate name -dia d ?-passes n? ?-overlap pct?
     * ?-combine bool? ?-iso_type 0|1|2? ?-outname name?}. {@code -dia} is required here (Python
     * falls back to a Preferences default this port does not expose to Tcl yet). {@code -combine}
     * is accepted but has no effect - see {@link TclFlatcamHost#isolate}. {@code -follow} is
     * recognized but not yet implemented.
     */
    private void registerIsolate(TclInterpreter interpreter) {
        interpreter.register("isolate", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("dia", "passes", "overlap", "combine",
                    "outname", "follow", "iso_type"));
            if (args.booleanOrDefault("follow", false)) {
                throw new TclException("isolate -follow is not yet supported in this port");
            }
            double dia = args.requireDouble("dia");
            int passes = args.intOrDefault("passes", 1);
            double overlapFraction = args.doubleOrDefault("overlap", 0.0) / 100.0;
            String outname = args.optionOrDefault("outname", name + "_iso");
            IsolationType type = switch (args.intOrDefault("iso_type", 2)) {
                case 0 -> IsolationType.EXTERIOR;
                case 1 -> IsolationType.INTERIOR;
                case 2 -> IsolationType.BOTH;
                default -> throw new TclException("iso_type must be 0, 1 or 2");
            };
            return creationResult(outname, host.isolate(name, outname, dia, passes, overlapFraction, type));
        });
    }

    /**
     * Python's TclCommandCutout: {@code cutout name -dia d -margin m -gapsize s ?-gaps tb|lr|4?
     * ?-outname name?}. Rectangular shape only - same as Python's own Tcl command (Free-form/Thin/
     * M-Bites have no Tcl exposure in Python either). {@code -dia}/{@code -gapsize} are required
     * here (no Preferences fallback); {@code -gaps} defaults to {@code "4"}.
     */
    private void registerCutout(TclInterpreter interpreter) {
        interpreter.register("cutout", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("dia", "margin", "gapsize", "gaps", "outname"));
            double dia = args.requireDouble("dia");
            double margin = args.doubleOrDefault("margin", 0.0);
            double gapSize = args.requireDouble("gapsize");
            String gapsText = args.optionOrDefault("gaps", "4");
            GapPattern gaps = switch (gapsText.toLowerCase(java.util.Locale.ROOT)) {
                case "tb" -> GapPattern.TB;
                case "lr" -> GapPattern.LR;
                case "4" -> GapPattern.FOUR;
                default -> throw new TclException("gaps must be 'tb', 'lr' or '4', got: " + gapsText);
            };
            String outname = args.optionOrDefault("outname", name + "_cutout");
            return creationResult(outname, host.cutoutRectangular(name, outname, dia, margin, gapSize, gaps));
        });
    }

    /**
     * Python's TclCommandCopperClear: {@code ncc|ncc_clear name -tooldia d1,d2,... (-all | -box ref)
     * ?-overlap pct? ?-margin m? ?-method standard|seed? ?-connect bool? ?-contour bool?
     * ?-rest bool? ?-outname name?}. Exactly one of {@code -all}/{@code -box} is required, same as
     * Python. {@code -order} and the Lines/Combo methods are not yet Tcl-exposed here.
     */
    private void registerNcc(TclInterpreter interpreter) {
        TclCommand ncc = (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("tooldia", "overlap", "margin", "method",
                    "connect", "contour", "rest", "all", "box", "outname"));
            List<Double> tools = new ArrayList<>();
            for (String part : args.requireOption("tooldia").split(",")) {
                if (!part.isEmpty()) tools.add(Double.parseDouble(part));
            }
            if (tools.isEmpty()) {
                throw new TclException("tooldia must list at least one diameter");
            }
            double overlapFraction = args.doubleOrDefault("overlap", 0.0) / 100.0;
            double margin = args.doubleOrDefault("margin", 0.0);
            String methodText = args.optionOrDefault("method", "standard");
            NccMethod method = switch (methodText.toLowerCase(java.util.Locale.ROOT)) {
                case "standard" -> NccMethod.STANDARD;
                case "seed" -> NccMethod.SEED;
                default -> throw new TclException("method must be 'standard' or 'seed', got: " + methodText);
            };
            boolean connect = args.booleanOrDefault("connect", false);
            boolean contour = args.booleanOrDefault("contour", false);
            boolean rest = args.booleanOrDefault("rest", false);
            boolean all = args.isPresent("all");
            boolean hasBox = args.has("box");
            if (all == hasBox) {
                throw new TclException("Expected either -box <value> or -all.");
            }
            NccBoundary boundary;
            if (all) {
                boundary = new NccBoundary.Itself();
            } else {
                String boxName = args.requireOption("box");
                Geometry boxGeometry = host.geometryOf(boxName)
                        .orElseThrow(() -> new TclException("Could not retrieve object: " + boxName));
                boundary = host.kindOf(boxName).filter(kind -> kind == TclFlatcamHost.Kind.GERBER).isPresent()
                        ? new NccBoundary.ReferenceGerber(boxGeometry)
                        : new NccBoundary.ReferenceGeometry(boxGeometry);
            }
            String outname = args.optionOrDefault("outname", name + (rest ? "_ncc" : "_ncc_rm"));
            return creationResult(outname, host.nccClear(name, outname, tools, overlapFraction, margin, method, connect, contour, rest, boundary));
        };
        interpreter.register("ncc", ncc);
        interpreter.register("ncc_clear", ncc);
    }

    /**
     * Python's TclCommandCncjob: {@code cncjob name -dia d -z_cut c -z_move m -feedrate f
     * ?-feedrate_z fz? ?-feedrate_rapid fr? ?-outname name?}. Only this reduced flag set is
     * covered here - see {@link TclFlatcamHost#cncjob}. {@code -feedrate_z} defaults to
     * {@code -feedrate} and {@code -feedrate_rapid} defaults to 0 (automatic), both this port's
     * own simplification, not Python's Preferences defaults.
     */
    private void registerCncjob(TclInterpreter interpreter) {
        interpreter.register("cncjob", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("dia", "z_cut", "z_move", "feedrate",
                    "feedrate_z", "feedrate_rapid", "outname"));
            double dia = args.requireDouble("dia");
            double zCut = args.requireDouble("z_cut");
            double zMove = args.requireDouble("z_move");
            double feedrate = args.requireDouble("feedrate");
            double feedrateZ = args.doubleOrDefault("feedrate_z", feedrate);
            double feedrateRapid = args.doubleOrDefault("feedrate_rapid", 0.0);
            String outname = args.optionOrDefault("outname", name + "_cnc");
            return creationResult(outname, host.cncjob(name, outname, dia, zCut, zMove, feedrate, feedrateZ, feedrateRapid));
        });
    }

    /** Python's TclCommandExportGcode: {@code export_gcode name ?-preamble p? ?-postamble p?} - returns the G-code text. */
    private void registerExportGcode(TclInterpreter interpreter) {
        interpreter.register("export_gcode", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("preamble", "postamble"));
            return host.exportGcode(name, args.optionOrDefault("preamble", ""), args.optionOrDefault("postamble", ""));
        });
    }

    /**
     * Python's TclCommandWriteGCode: {@code write_gcode name filename ?-preamble p? ?-postamble p?
     * ?-muted bool?}. {@code -muted} is accepted for compatibility but has no effect: this port
     * always raises a {@link TclException} on failure rather than silently returning.
     */
    private void registerWriteGcode(TclInterpreter interpreter) {
        interpreter.register("write_gcode", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String name = args.positional(0);
            String filename = args.positional(1);
            args.rejectUnknownOptions(java.util.Set.of("preamble", "postamble", "muted"));
            try {
                host.writeGcode(name, Path.of(filename), args.optionOrDefault("preamble", ""),
                        args.optionOrDefault("postamble", ""));
            } catch (IOException error) {
                throw new TclException("Operation failed: " + error.getMessage());
            }
            return "";
        });
    }

    private static String baseName(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
