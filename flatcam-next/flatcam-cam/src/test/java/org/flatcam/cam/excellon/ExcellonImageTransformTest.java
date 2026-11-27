package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.flatcam.cam.transform.TransformOp;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

class ExcellonImageTransformTest {

    private static ExcellonImage parse(String... lines) {
        return new ExcellonParser().parse(List.of(lines));
    }

    @Test
    void offsetMovesEveryDrillAndSlotEndpoint() {
        ExcellonImage image = parse("M48", "METRIC", "T1C1.0", "%",
                "T1", "X1.0Y1.0", "X1.0Y1.0G85X5.0Y1.0", "M30");

        ExcellonImage moved = image.transformed(new TransformOp.Offset(10, 20));

        ExcellonImage.Drill drill = moved.drills().get(0);
        assertEquals(11.0, drill.x(), 1e-9);
        assertEquals(21.0, drill.y(), 1e-9);

        ExcellonImage.Slot slot = moved.slots().get(0);
        assertEquals(11.0, slot.x1(), 1e-9);
        assertEquals(21.0, slot.y1(), 1e-9);
        assertEquals(15.0, slot.x2(), 1e-9);
        assertEquals(21.0, slot.y2(), 1e-9);

        // Tool table is untouched by a pure offset.
        assertEquals(image.toolDiameters(), moved.toolDiameters());
    }

    @Test
    void mirrorYNegatesOnlyEveryYCoordinate() {
        ExcellonImage image = parse("M48", "METRIC", "T1C1.0", "%", "T1", "X3.0Y4.0", "M30");

        ExcellonImage mirrored = image.transformed(new TransformOp.MirrorY(new Coordinate(0, 0)));

        ExcellonImage.Drill drill = mirrored.drills().get(0);
        assertEquals(3.0, drill.x(), 1e-9, "mirroring Y must not move X");
        assertEquals(-4.0, drill.y(), 1e-9);
    }

    @Test
    void scalingHolePositionsKeepsTheirPhysicalDiameterInThePlot() {
        ExcellonImage image = parse("M48", "METRIC", "T1C1.0", "%", "T1", "X3.0Y4.0", "M30");
        ExcellonImage scaled = image.transformed(new TransformOp.Scale(2, 3, new Coordinate(0, 0)));
        assertEquals(new ExcellonImage.Drill(1, 6, 12), scaled.drills().getFirst());
        assertEquals(1.0, scaled.toolDiameters().get(1), 1e-9);
        var footprint = scaled.solidGeometry().getEnvelopeInternal();
        assertEquals(1.0, footprint.getWidth(), 1e-9);
        assertEquals(1.0, footprint.getHeight(), 1e-9);
    }

    @Test
    void convertingUnitsScalesTheSlotCentresAndToolDiameterTogether() {
        ExcellonImage image = parse("M48", "INCH", "T1C0.0315", "%", "T1", "X1.0Y2.0G85X2.0Y2.0", "M30");
        ExcellonImage mm = image.convertedTo("MM");
        assertEquals("MM", mm.units());
        assertEquals(0.8001, mm.toolDiameters().get(1), 1e-9);
        assertEquals(25.4, mm.slots().getFirst().x1(), 1e-9);
        assertEquals(50.8, mm.slots().getFirst().x2(), 1e-9);
        assertEquals(0.8001, mm.solidGeometry().getEnvelopeInternal().getHeight(), 1e-9);
        assertEquals(0.0315, mm.convertedTo("IN").toolDiameters().get(1), 1e-9);
    }

    @Test
    void originalImageIsNeverMutated() {
        ExcellonImage image = parse("M48", "METRIC", "T1C1.0", "%", "T1", "X3.0Y4.0", "M30");
        image.transformed(new TransformOp.Offset(100, 100));
        ExcellonImage.Drill stillOriginal = image.drills().get(0);
        assertEquals(3.0, stillOriginal.x(), 1e-9);
        assertEquals(4.0, stillOriginal.y(), 1e-9);
    }
}
