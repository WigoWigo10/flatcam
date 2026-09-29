package org.flatcam.cam.dxf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;

class DxfExporterTest {

    private final DxfExporter exporter = new DxfExporter();

    @Test
    void ringsAndLinesBecomeR12PolylinesWithUnits() throws Exception {
        Geometry geometry = new WKTReader().read("GEOMETRYCOLLECTION ("
                + "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 2 2)),"
                + "LINESTRING (20 0, 25 5, 30 0), POINT (1 1))");

        String dxf = exporter.export(geometry, "MM");
        List<String[]> pairs = pairs(dxf);

        assertTrue(dxf.contains("$ACADVER\n  1\nAC1009\n"));
        assertTrue(dxf.contains("$INSUNITS\n 70\n4\n"), "millimetres");
        assertEquals(3, count(pairs, "0", "POLYLINE"), "exterior, hole and line; the point is skipped");
        assertEquals(4 + 3 + 3, count(pairs, "0", "VERTEX"), "closed rings drop the repeated end vertex");
        List<String> closedFlags = new ArrayList<>();
        for (int index = 0; index < pairs.size(); index++) {
            if (pairs.get(index)[1].equals("POLYLINE")) {
                for (int next = index + 1; ; next++) {
                    if (pairs.get(next)[0].equals("70")) {
                        closedFlags.add(pairs.get(next)[1]);
                        break;
                    }
                }
            }
        }
        assertEquals(List.of("1", "1", "0"), closedFlags);
        assertTrue(dxf.contains(" 10\n25.0\n 20\n5.0\n"));
        assertTrue(dxf.endsWith("  0\nEOF\n"));
    }

    @Test
    void inchObjectsDeclareInchesAndEmptyObjectsAreRejected() throws Exception {
        assertTrue(exporter.export(new WKTReader().read("LINESTRING (0 0, 1 1)"), "IN")
                .contains("$INSUNITS\n 70\n1\n"));
        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(new WKTReader().read("POINT (1 1)"), "MM"));
    }

    private static List<String[]> pairs(String dxf) {
        String[] lines = dxf.split("\n");
        List<String[]> pairs = new ArrayList<>();
        for (int index = 0; index + 1 < lines.length; index += 2) {
            pairs.add(new String[]{lines[index].trim(), lines[index + 1]});
        }
        return pairs;
    }

    private static long count(List<String[]> pairs, String code, String value) {
        return pairs.stream().filter(pair -> pair[0].equals(code) && pair[1].equals(value)).count();
    }
}
