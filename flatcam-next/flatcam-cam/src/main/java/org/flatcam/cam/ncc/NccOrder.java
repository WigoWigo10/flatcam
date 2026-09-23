package org.flatcam.cam.ncc;

/**
 * appTools/ToolNCC.py's "ncc_order_radio" ('no'/'fwd'/'rev'): how multiple
 * tools are sequenced when {@link NccParameters#restMachining()} is off, in
 * which case every tool clears the same full area independently, so order
 * only affects the sequence tools appear/are cut in, not what each one
 * clears. Ignored when Rest Machining is on, which always forces
 * largest-diameter-first regardless of this setting - Python's
 * on_rest_machining_check disables the radio for the same reason.
 */
public enum NccOrder {
    NONE("No"),
    FORWARD("Forward"),
    REVERSE("Reverse");

    private final String label;

    NccOrder(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
