package org.flatcam.fx;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.flatcam.cam.tcl.TclException;

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
}
