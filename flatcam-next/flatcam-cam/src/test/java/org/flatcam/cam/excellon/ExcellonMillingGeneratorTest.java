package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;

class ExcellonMillingGeneratorTest {
    private static ExcellonImage sample() {
        return new ExcellonParser().parse(List.of("M48", "METRIC", "T1C1.0", "T2C2.0", "%",
                "T1", "X1.0Y1.0", "X1.0Y1.0G85X5.0Y1.0", "T2", "X10.0Y10.0", "M30"));
    }

    @Test
    void drillMillingOffsetsTheCenterlineByHalfTheDiameterDifference() {
        Geometry paths = ExcellonMillingGenerator.generate(sample(), Set.of(1), 0.6,
                ExcellonMillingGenerator.Kind.DRILLS, CancellationToken.none());
        assertEquals(1, paths.getNumGeometries());
        assertEquals(0.8, paths.getEnvelopeInternal().getMinX(), 1e-6);
        assertEquals(1.2, paths.getEnvelopeInternal().getMaxX(), 1e-6);
    }

    @Test
    void slotMillingOffsetsBothEndsOfTheSlot() {
        Geometry paths = ExcellonMillingGenerator.generate(sample(), Set.of(1), 0.6,
                ExcellonMillingGenerator.Kind.SLOTS, CancellationToken.none());
        assertEquals(1, paths.getNumGeometries());
        assertEquals(0.8, paths.getEnvelopeInternal().getMinX(), 1e-6);
        assertEquals(5.2, paths.getEnvelopeInternal().getMaxX(), 1e-6);
    }

    @Test
    void rejectsOversizeOrMissingToolsWithoutPublishingPaths() {
        assertThrows(IllegalArgumentException.class, () -> ExcellonMillingGenerator.generate(
                sample(), Set.of(1), 1.1, ExcellonMillingGenerator.Kind.DRILLS, CancellationToken.none()));
        assertThrows(IllegalArgumentException.class, () -> ExcellonMillingGenerator.generate(
                sample(), Set.of(9), 0.6, ExcellonMillingGenerator.Kind.DRILLS, CancellationToken.none()));
    }

    @Test
    void equalDiametersStillCreateAClosedPathAndCancellationStopsWork() {
        Geometry paths = ExcellonMillingGenerator.generate(sample(), Set.of(1), 1.0,
                ExcellonMillingGenerator.Kind.DRILLS, CancellationToken.none());
        assertTrue(paths.getLength() > 0);
        assertThrows(CancellationException.class, () -> ExcellonMillingGenerator.generate(
                sample(), Set.of(1), 0.6, ExcellonMillingGenerator.Kind.DRILLS, () -> true));
    }
}
