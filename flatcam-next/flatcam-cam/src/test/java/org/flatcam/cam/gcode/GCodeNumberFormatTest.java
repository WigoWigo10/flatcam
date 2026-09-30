package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The hand-rolled coordinate formatter must print exactly what String.format did. */
class GCodeNumberFormatTest {
    @Test
    void fmtMatchesFormatterForEdgeAndRandomValues() {
        double[] edge = {0, -0.0, 0.00005, -0.00005, 0.00004, -0.00001, 1.00005, 2.5, -2.5, 1e-9, -1e-9,
                123456.78905, 0.99995, 9.99995, 1e15, -1e15, 1.0E-4, 5.0E-5, Double.MIN_VALUE};
        for (double value : edge) {
            assertEquals(String.format(Locale.ROOT, "%.4f", value), GCodeGenerator.fmt(value), "value " + value);
        }
        Random random = new Random(42);
        for (int i = 0; i < 300_000; i++) {
            double value = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(8) - 2);
            assertEquals(String.format(Locale.ROOT, "%.4f", value), GCodeGenerator.fmt(value), "value " + value);
        }
    }
}
