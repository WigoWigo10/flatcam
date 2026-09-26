package org.flatcam.cam.gerber.edit;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

/**
 * Routing styles used by the legacy Gerber editor's Track tool. Each mode
 * expands one pair of clicked anchors into either a direct segment or two
 * segments joined by a deterministic corner.
 */
public enum TrackBendMode {
    FORTY_FIVE("45 graus"),
    REVERSE_FORTY_FIVE("45 graus invertido"),
    NINETY("90 graus"),
    REVERSE_NINETY("90 graus invertido"),
    FREE("angulo livre");

    private final String displayName;

    TrackBendMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public TrackBendMode next() {
        TrackBendMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public TrackBendMode previous() {
        TrackBendMode[] modes = values();
        return modes[(ordinal() + modes.length - 1) % modes.length];
    }

    /**
     * Returns the centerline points from {@code start} to {@code end},
     * including both endpoints. Duplicate corner/end points are omitted.
     */
    public List<Coordinate> route(Coordinate start, Coordinate end) {
        requireFinite(start);
        requireFinite(end);
        if (start.equals2D(end)) {
            return List.of(new Coordinate(start));
        }

        List<Coordinate> points = new ArrayList<>(3);
        points.add(new Coordinate(start));
        Coordinate corner = corner(start, end);
        if (corner != null && !corner.equals2D(start) && !corner.equals2D(end)) {
            points.add(corner);
        }
        points.add(new Coordinate(end));
        return List.copyOf(points);
    }

    private Coordinate corner(Coordinate start, Coordinate end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double signX = Math.signum(dx);
        double signY = Math.signum(dy);
        return switch (this) {
            case FORTY_FIVE -> absX > absY
                    ? new Coordinate(start.x + signX * (absX - absY), start.y)
                    : new Coordinate(start.x, start.y + signY * (absY - absX));
            case REVERSE_FORTY_FIVE -> absX > absY
                    ? new Coordinate(start.x + signX * absY, end.y)
                    : new Coordinate(end.x, start.y + signY * absX);
            case NINETY -> new Coordinate(end.x, start.y);
            case REVERSE_NINETY -> new Coordinate(start.x, end.y);
            case FREE -> null;
        };
    }

    private static void requireFinite(Coordinate coordinate) {
        if (coordinate == null || !Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)) {
            throw new IllegalArgumentException("Track coordinates must be finite");
        }
    }
}
