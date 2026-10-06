package org.flatcam.fx;

import org.flatcam.cam.transform.TransformOp;
import org.flatcam.cam.tcl.TclException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

/** Immutable command parameters; pivots are resolved against the same captured source as the worker. */
record TclTransformRequest(Operation operation, double x, double y, Reference reference,
                           double pivotX, double pivotY, String box) {
    enum Operation { OFFSET, SCALE, MIRROR, SKEW, ROTATE }
    enum Reference { ORIGIN, CENTER, MIN_BOUNDS, POINT, BOX }

    TclTransformRequest {
        if (operation == null || reference == null) throw new IllegalArgumentException("Missing transform operation/reference.");
        for (double value : new double[]{x, y, pivotX, pivotY})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Transform values must be finite.");
        if (operation == Operation.SCALE && (x == 0 || y == 0))
            throw new IllegalArgumentException("Scale factors must be nonzero; an omitted axis remains unchanged.");
        if (operation == Operation.SKEW && (Math.abs(x) >= 90 || Math.abs(y) >= 90))
            throw new IllegalArgumentException("Skew angles must be strictly between -90 and 90 degrees.");
        if (operation == Operation.MIRROR && x != 0 && x != 1)
            throw new IllegalArgumentException("Mirror axis must be X or Y.");
        if (reference == Reference.BOX && (box == null || box.isBlank()))
            throw new IllegalArgumentException("Reference box must name an object.");
    }

    TransformOp resolve(Envelope source, Envelope boxBounds) throws TclException {
        Coordinate pivot = switch (reference) {
            case ORIGIN -> new Coordinate(0, 0);
            case POINT -> new Coordinate(pivotX, pivotY);
            case CENTER -> center(source);
            case BOX -> center(boxBounds);
            case MIN_BOUNDS -> {
                requireBounds(source);
                yield new Coordinate(source.getMinX(), source.getMinY());
            }
        };
        return switch (operation) {
            case OFFSET -> new TransformOp.Offset(x, y);
            case SCALE -> new TransformOp.Scale(x, y, pivot);
            // Python's axis is the line of reflection, not the coordinate to negate.
            case MIRROR -> x == 0 ? new TransformOp.MirrorY(pivot) : new TransformOp.MirrorX(pivot);
            case SKEW -> new TransformOp.Skew(x, y, pivot);
            // Terminal follows the Transformations UI: positive clockwise; raw CAM is counter-clockwise.
            case ROTATE -> new TransformOp.Rotate(-(x % 360), pivot);
        };
    }

    private static Coordinate center(Envelope bounds) throws TclException {
        requireBounds(bounds);
        return new Coordinate(bounds.getMinX() / 2 + bounds.getMaxX() / 2,
                bounds.getMinY() / 2 + bounds.getMaxY() / 2);
    }

    private static void requireBounds(Envelope bounds) throws TclException {
        if (bounds == null || bounds.isNull()) throw new TclException("Reference object has no geometry bounds.");
    }
}
