package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.flatcam.cam.tcl.TclArgs;
import org.flatcam.cam.tcl.TclCommand;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;

/**
 * Registers the FlatCAM-specific Tcl commands (the ~69 classes under Python's
 * {@code tclCommands/}) on a {@link TclInterpreter}, backed by a {@link TclFlatcamHost} rather
 * than the live FX session directly, so the argument handling and error messages here can be
 * unit-tested with a fake host. Each command mirrors one Python {@code TclCommand*.py}'s
 * {@code arg_names}/{@code option_types}/{@code required} - see each registration's doc comment
 * for exactly which flags are covered and which are deliberately not (yet). This is the first
 * batch: object creation/lookup/deletion, with no CAM generator involved yet.
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
    }

    /** Python's TclCommandOpenGerber: {@code open_gerber filename ?-outname name?}. */
    private void registerOpenGerber(TclInterpreter interpreter) {
        interpreter.register("open_gerber", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            String filename = args.positional(0);
            args.rejectUnknownOptions(java.util.Set.of("outname"));
            String outname = args.optionOrDefault("outname", baseName(filename));
            try {
                host.openGerber(Path.of(filename), outname);
            } catch (IOException error) {
                throw new TclException("Could not open Gerber file: " + error.getMessage());
            }
            return "";
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
                host.openExcellon(Path.of(filename), outname);
            } catch (IOException error) {
                throw new TclException("Could not open Excellon file: " + error.getMessage());
            }
            return "";
        });
    }

    /** Python's TclCommandNewGeometry: {@code new_geometry ?name?} (default name "new_geo"). */
    private void registerNewGeometry(TclInterpreter interpreter) {
        interpreter.register("new_geometry", (interp, words) -> {
            TclArgs args = TclArgs.parse(words);
            host.newEmptyGeometry(args.positionalOrDefault(0, "new_geo"));
            return "";
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
            host.newBoundingBoxGeometry(name, outname, margin, rounded);
            return "";
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

    private static String baseName(String filename) {
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }
}
