package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.flatcam.cam.gcode.GCodeToolpathParser.PathStep;
import org.junit.jupiter.api.Test;

class CncStepCsvTest {
    @Test
    void writesOneRowPerLegWithDurationAndRunningTime() {
        List<PathStep> steps = List.of(
                new PathStep(0, true, new double[]{0, 0, 10, 0}, 10, 1, 2, 0.1),
                new PathStep(1, false, new double[]{10, 0, 10, 5}, 5, 2, 3, 0.25),
                new PathStep(2, false, new double[]{10, 5}, 0, 3, 0, Double.NaN));
        String[] lines = CncStepCsv.toCsv(steps, "MM").split("\n");
        assertEquals(CncStepCsv.HEADER, lines[0]);
        assertEquals("1,deslocamento,1,2,0.0000,0.0000,10.0000,0.0000,10.0000,mm,6.00,6.00", lines[1]);
        assertEquals("2,corte,2,3,10.0000,0.0000,10.0000,5.0000,5.0000,mm,9.00,15.00", lines[2]);
        assertEquals("3,furo,3,,10.0000,5.0000,10.0000,5.0000,0.0000,mm,,", lines[3]);
    }
}
