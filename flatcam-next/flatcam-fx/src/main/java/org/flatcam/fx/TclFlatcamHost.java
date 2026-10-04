package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.tcl.TclException;
import org.locationtech.jts.geom.Geometry;

/**
 * What {@link TclFlatcamCommands} needs from the live FX session - object creation, lookup by
 * name, and deletion - kept as a narrow interface so the commands' own argument handling and
 * error messages can be unit-tested with a fake implementation, without a JavaFX {@code Scene}.
 * {@link MainWindow} is the real implementation, backed by its per-type {@code TreeItem} maps.
 *
 * <p>Python FlatCAM resolves every Tcl command's object argument through one shared
 * {@code self.app.collection.get_by_name(name)}, regardless of kind (Gerber/Excellon/Geometry/
 * CNCJob all live in the same {@code collection}). This interface's {@link #find} mirrors that:
 * one name lookup across every kind, returning which kind it was.
 */
interface TclFlatcamHost {

    enum Kind { GERBER, EXCELLON, GEOMETRY, CNC_JOB }

    record ObjectRef(Kind kind, String name) {
    }

    /** Opens and parses a Gerber file, adds it to the project, and returns the name it was given. */
    String openGerber(Path file, String outname) throws IOException;

    /** Opens and parses an Excellon file, adds it to the project, and returns the name it was given. */
    String openExcellon(Path file, String outname) throws IOException;

    /** Every object name currently in the project, across every kind - Python's {@code collection.get_names()}. */
    List<String> objectNames();

    Optional<ObjectRef> find(String name);

    /** Deletes one named object; a name that does not resolve to anything is silently ignored. */
    void delete(String name);

    void deleteAll();

    /** {@code [minX, minY, maxX, maxY]}, if {@code name} resolves to an object with a boundable geometry. */
    Optional<double[]> boundsOf(String name);

    /** Creates an empty Geometry object - Python's {@code new_object('geometry', name, lambda x, y: None)}. */
    String newEmptyGeometry(String name);

    /**
     * Creates a new Geometry object that is {@code name}'s envelope, expanded by {@code margin} -
     * Python's {@code bbox} command. {@code rounded} keeps the margin's round corners (a buffered
     * envelope) instead of squaring them back off.
     */
    String newBoundingBoxGeometry(String sourceName, String outname, double margin, boolean rounded)
            throws TclException;

    /** The kind of a resolvable name - used to decide how {@code -box <name>} should reference it for NCC. */
    Optional<Kind> kindOf(String name);

    /** The "natural" geometry for CAM operations: Gerber/Excellon solid geometry, a Geometry's own, or a CNC Job's cut geometry. */
    Optional<Geometry> geometryOf(String name);

    /**
     * Python's {@code isolate}: creates isolation routing Geometry for a Gerber. Always produces
     * one combined Geometry object regardless of pass count - Python's {@code -combine False}
     * (one Geometry object per pass) is not supported here.
     */
    String isolate(String sourceName, String outname, double toolDiameter, int passes,
                   double overlapFraction, IsolationType type) throws TclException;

    /** Python's {@code cutout}: rectangular board cutout only (Free-form/Thin/M-Bites are not Tcl-exposed in Python either). */
    String cutoutRectangular(String sourceName, String outname, double toolDiameter, double margin,
                             double gapSize, GapPattern gaps) throws TclException;

    /**
     * Python's {@code ncc}/{@code ncc_clear}: non-copper clearing. {@code order} (Python's
     * {@code fwd}/{@code rev}/{@code no}) and the Lines/Combo methods beyond Standard/Seed are not
     * yet exposed here.
     */
    String nccClear(String sourceName, String outname, List<Double> toolDiameters, double overlapFraction,
                    double margin, NccMethod method, boolean connect, boolean contour, boolean rest,
                    NccBoundary boundary) throws TclException;

    /**
     * Python's {@code cncjob}: converts a Geometry object into a CNC Job. Only the most common
     * flags are covered (tool diameter, Z cut/move, the three feedrates); toolchange, dwell,
     * multidepth, end/park position and preprocessor selection are not yet Tcl-exposed here.
     */
    String cncjob(String sourceName, String outname, double toolDiameter, double zCut, double zMove,
                 double feedrate, double feedrateZ, double feedrateRapid) throws TclException;

    /** Python's {@code export_gcode}: returns a CNC Job's G-code text, with optional preamble/postamble. */
    String exportGcode(String cncJobName, String preamble, String postamble) throws TclException;

    /** Python's {@code write_gcode}: saves a CNC Job's G-code (with optional preamble/postamble) to a file. */
    void writeGcode(String cncJobName, Path outputFile, String preamble, String postamble) throws TclException, IOException;
}
