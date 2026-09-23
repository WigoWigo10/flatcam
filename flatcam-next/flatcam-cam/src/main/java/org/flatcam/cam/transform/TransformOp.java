package org.flatcam.cam.transform;

import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * Ports appTools/ToolTransform.py's operations (Buffer excluded - out of
 * scope, see CONTEXTO_E_PROGRESSO.md section 9.2) as pure, composable ops
 * over a single Geometry or Coordinate, so any object kind (Gerber/Excellon/
 * Geometry) can apply the same configured operation to its own geometry
 * without this package knowing anything about object kinds.
 *
 * <p>Every op but {@link Offset} pivots about an explicit {@link Coordinate} -
 * how that pivot itself is computed (Origin/Selection-bounds-center/a typed
 * Point) is a UI-layer concern, matching Python's {@code on_calculate_reference}.
 *
 * <p>{@link Rotate#angleDegrees()} is positive = counter-clockwise, the raw
 * mathematical/JTS convention - matching {@code camlib.Geometry.rotate}'s own
 * convention in Python. A UI that presents "positive = clockwise" to the user
 * (ToolTransform.py's panel, and the Options menu quick actions) must negate
 * the angle before constructing a {@code Rotate}, exactly like Python's own
 * call sites do ({@code obj.rotate(-num, point)}).
 */
public sealed interface TransformOp {

    Geometry apply(Geometry geometry);

    Coordinate apply(Coordinate point);

    record Rotate(double angleDegrees, Coordinate pivot) implements TransformOp {
        public Rotate {
            Objects.requireNonNull(pivot, "pivot");
        }

        private AffineTransformation transformation() {
            return AffineTransformation.rotationInstance(Math.toRadians(angleDegrees), pivot.x, pivot.y);
        }

        @Override
        public Geometry apply(Geometry geometry) {
            return transformation().transform(geometry);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            return transformation().transform(point, new Coordinate());
        }
    }

    /** appTools/ToolTransform.py's Scale X/Scale Y - independent factors, both about the same pivot. */
    record Scale(double xFactor, double yFactor, Coordinate pivot) implements TransformOp {
        public Scale {
            Objects.requireNonNull(pivot, "pivot");
        }

        private AffineTransformation transformation() {
            return AffineTransformation.scaleInstance(xFactor, yFactor, pivot.x, pivot.y);
        }

        @Override
        public Geometry apply(Geometry geometry) {
            return transformation().transform(geometry);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            return transformation().transform(point, new Coordinate());
        }
    }

    /** Mirrors across a vertical line through the pivot (negates X) - appTools/ToolTransform.py's "Flip on X". */
    record MirrorX(Coordinate pivot) implements TransformOp {
        public MirrorX {
            Objects.requireNonNull(pivot, "pivot");
        }

        @Override
        public Geometry apply(Geometry geometry) {
            return new Scale(-1, 1, pivot).apply(geometry);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            return new Scale(-1, 1, pivot).apply(point);
        }
    }

    /** Mirrors across a horizontal line through the pivot (negates Y) - appTools/ToolTransform.py's "Flip on Y". */
    record MirrorY(Coordinate pivot) implements TransformOp {
        public MirrorY {
            Objects.requireNonNull(pivot, "pivot");
        }

        @Override
        public Geometry apply(Geometry geometry) {
            return new Scale(1, -1, pivot).apply(geometry);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            return new Scale(1, -1, pivot).apply(point);
        }
    }

    /**
     * appTools/ToolTransform.py's Skew X/Skew Y (independent angles, both
     * about the same pivot). JTS's {@code shearInstance} takes raw shear
     * FACTORS, not angles, so this converts via {@code tan()} - matching
     * Shapely's {@code affinity.skew}, which does the same conversion
     * internally.
     */
    record Skew(double angleXDegrees, double angleYDegrees, Coordinate pivot) implements TransformOp {
        public Skew {
            Objects.requireNonNull(pivot, "pivot");
        }

        @Override
        public Geometry apply(Geometry geometry) {
            Geometry step = AffineTransformation.translationInstance(-pivot.x, -pivot.y).transform(geometry);
            step = AffineTransformation.shearInstance(shearX(), shearY()).transform(step);
            return AffineTransformation.translationInstance(pivot.x, pivot.y).transform(step);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            Coordinate step = AffineTransformation.translationInstance(-pivot.x, -pivot.y)
                    .transform(point, new Coordinate());
            step = AffineTransformation.shearInstance(shearX(), shearY()).transform(step, new Coordinate());
            return AffineTransformation.translationInstance(pivot.x, pivot.y).transform(step, new Coordinate());
        }

        private double shearX() {
            return Math.tan(Math.toRadians(angleXDegrees));
        }

        private double shearY() {
            return Math.tan(Math.toRadians(angleYDegrees));
        }
    }

    /** Pure translate - pivot-independent, unlike every other op. */
    record Offset(double dx, double dy) implements TransformOp {
        @Override
        public Geometry apply(Geometry geometry) {
            return AffineTransformation.translationInstance(dx, dy).transform(geometry);
        }

        @Override
        public Coordinate apply(Coordinate point) {
            return AffineTransformation.translationInstance(dx, dy).transform(point, new Coordinate());
        }
    }
}
