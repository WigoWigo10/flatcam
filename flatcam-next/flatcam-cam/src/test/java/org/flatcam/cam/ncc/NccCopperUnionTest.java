package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.flatcam.cam.CancellationToken;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

class NccCopperUnionTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    static Stream<JSONObject> oracleCases() throws Exception {
        try (var stream = NccCopperUnionTest.class.getResourceAsStream("geos-windows-union-order.json")) {
            assertNotNull(stream);
            var oracle = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(oracle.getString("geos").startsWith("3.10.3"));
            return oracle.getJSONArray("cases").toList().stream().map(value -> new JSONObject((java.util.Map<?,?>)value));
        }
    }

    @ParameterizedTest
    @MethodSource("oracleCases")
    void matchesPublicWindowsGeosRingOrderWithoutChangingSource(JSONObject oracle) {
        int count = oracle.getInt("count");
        var canonical = publicPolygons(count);
        for (double scale : new double[]{1, 1 / 25.4}) {
            for (double shift : new double[]{0, -40}) {
                var transform = AffineTransformation.translationInstance(shift, shift / 2);
                transform.scale(scale, scale);
                Geometry source = transform.transform(FACTORY.createMultiPolygon(canonical.toArray(Polygon[]::new)));
                String before = source.toText();
                Geometry actual = NccCopperUnion.union(source, CancellationToken.none());
                assertEquals(count, actual.getNumGeometries());
                assertTrue(actual.isValid());
                assertEquals(source.getArea(), actual.getArea(), 1e-9 * scale * scale);
                for (int i = 0; i < count; i++) {
                    int index = oracle.getJSONArray("order").getInt(i);
                    int vertex = oracle.getJSONArray("vertices").getInt(i);
                    Polygon input = canonical.get((index * inverse17(count)) % count);
                    Coordinate expected = transform.transform(input.getExteriorRing().getCoordinateN(vertex), new Coordinate());
                    Polygon part = (Polygon)actual.getGeometryN(i);
                    assertEquals(expected.x, part.getExteriorRing().getCoordinateN(0).x, 1e-12 * scale);
                    assertEquals(expected.y, part.getExteriorRing().getCoordinateN(0).y, 1e-12 * scale);
                    Geometry expectedPart = transform.transform(input);
                    assertEquals(expectedPart.getEnvelopeInternal(), part.getEnvelopeInternal());
                }
                assertEquals(before, source.toText());
            }
        }
    }

    private static int inverse17(int count) {
        if (count == 1) return 0;
        for (int i = 1; i < count; i++) if (17 * i % count == 1) return i;
        throw new AssertionError("fixture multiplier not coprime");
    }

    private static List<Polygon> publicPolygons(int count) {
        var result = new ArrayList<Polygon>();
        for (int i = 0; i < count; i++) {
            int index = i * 17 % count;
            result.add((Polygon)FACTORY.createPoint(new Coordinate(index % 7 * 6, index / 7 * 5)).buffer(1, 4));
        }
        return result;
    }

    @Test
    void overlapsDuplicatesHolesInvalidPolygonsAndNonAreaMembersRemainSafe() throws Exception {
        Geometry a = new org.locationtech.jts.io.WKTReader().read(
                "POLYGON ((0 0,10 0,10 10,0 10,0 0),(2 2,2 4,4 4,4 2,2 2))");
        Geometry b = FACTORY.toGeometry(new Envelope(8, 12, 5, 12));
        Geometry invalid = new org.locationtech.jts.io.WKTReader().read("POLYGON ((20 0,24 4,20 4,24 0,20 0))");
        Geometry source = FACTORY.createGeometryCollection(new Geometry[]{a,a.copy(),b,invalid,
                FACTORY.createPoint(new Coordinate(100,100)),FACTORY.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(12,12)})});
        String original = source.toText();
        Geometry result = NccCopperUnion.union(source,CancellationToken.none());
        assertTrue(result.isValid());
        assertEquals(0, result.symDifference(source.buffer(0)).getArea(), 1e-10);
        assertFalse(result.covers(FACTORY.createPoint(new Coordinate(3,3))));
        assertEquals(original, source.toText());
        assertTrue(NccCopperUnion.union(FACTORY.createGeometryCollection(),CancellationToken.none()).isEmpty());
    }

    @Test
    void cancellationDuringCollectionAndUnionNeverMutatesSource() {
        Geometry source = FACTORY.createMultiPolygon(publicPolygons(160).toArray(Polygon[]::new));
        String original = source.toText();
        for (int limit : new int[]{0, 50, 185, 250}) {
            var checks = new AtomicInteger();
            assertThrows(CancellationException.class, () -> NccCopperUnion.union(source, () -> checks.incrementAndGet() > limit));
            assertEquals(original, source.toText());
        }
    }

    @Test
    void legacySorterRemainsAValidPermutationForTiesAndAdversarialOrders() {
        var random = new Random(921);
        for (int size : new int[]{0,1,16,32,33,41,47,100,1000}) {
            for (int run = 0; run < 20; run++) {
                record Item(int key,int id) { }
                var items = new ArrayList<Item>();
                for (int i = 0; i < size; i++) items.add(new Item(run == 0 ? 0 : random.nextInt(10),i));
                var expected = items.stream().map(Item::id).sorted().toList();
                LegacyMsvcSort.sort(items,Comparator.comparingInt(Item::key),CancellationToken.none());
                for (int i = 1; i < size; i++) assertTrue(items.get(i-1).key() <= items.get(i).key());
                assertEquals(expected,items.stream().map(Item::id).sorted().toList());
            }
        }
    }
}
