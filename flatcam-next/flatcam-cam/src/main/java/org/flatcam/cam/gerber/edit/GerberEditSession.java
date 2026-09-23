package org.flatcam.cam.gerber.edit;

import org.flatcam.cam.gerber.GerberImage;

/**
 * An open editing session for one Gerber object - first slice of the Gerber
 * Editor (CONTEXTO_E_PROGRESSO.md section 9.4, step 1: "sessao de edicao com
 * Aplicar/Cancelar"), ported at session-lifecycle granularity from
 * AppGerberEditor.py's edit_fcgerber()/update_fcgerber()/deactivate_grb_editor().
 *
 * <p>Python's editor loads the source object's apertures into an editable
 * storage_dict and, on Apply (update_fcgerber -&gt; new_edited_gerber), builds a
 * NEW Gerber object named "&lt;name&gt;_edit" (or "&lt;name&gt;_edit_N" if the source
 * was itself already an edited copy) rather than overwriting the original;
 * Cancel (deactivate_grb_editor without saving) discards the session and no
 * new object is created. This class reproduces that naming and apply/cancel
 * behavior. There are no drawing tools yet in this port - see
 * GerberEditToolPanel/MainWindow for what step 1 actually wires up - so
 * {@link #workingImage()} is presently always identical to the source image
 * and {@link #apply()} never produces edited geometry; that starts with the
 * next slice (selection/hit-testing, then actual shape edits).
 */
public final class GerberEditSession {

    private final String sourceName;
    private final GerberImage sourceImage;
    private final GerberImage workingImage;

    public GerberEditSession(String sourceName, GerberImage sourceImage) {
        this.sourceName = sourceName;
        this.sourceImage = sourceImage;
        this.workingImage = sourceImage;
    }

    public String sourceName() {
        return sourceName;
    }

    public GerberImage workingImage() {
        return workingImage;
    }

    /** Always false until this port has real edit operations that replace {@link #workingImage()}. */
    public boolean isDirty() {
        return workingImage != sourceImage;
    }

    /** The name and geometry Apply will publish as a new object. */
    public record ApplyResult(String name, GerberImage image) {
    }

    public ApplyResult apply() {
        return new ApplyResult(nextEditedName(sourceName), workingImage);
    }

    /**
     * Ported from AppGerberEditor.py's update_fcgerber(): "&lt;name&gt;_edit" the
     * first time a name is edited; if the name already contains "_edit", the
     * last character is parsed as a digit and incremented (append "_1" if the
     * last character isn't a digit) - faithfully reproduced, including
     * Python's own multi-digit quirk past "_edit_9" (single-character slice,
     * not a real decimal increment), since this is existing legacy behavior
     * to match, not a bug to fix here.
     */
    public static String nextEditedName(String currentName) {
        if (!currentName.contains("_edit")) {
            return currentName + "_edit";
        }
        char lastChar = currentName.charAt(currentName.length() - 1);
        if (Character.isDigit(lastChar)) {
            int next = Character.getNumericValue(lastChar) + 1;
            return currentName.substring(0, currentName.length() - 1) + next;
        }
        return currentName + "_1";
    }
}
