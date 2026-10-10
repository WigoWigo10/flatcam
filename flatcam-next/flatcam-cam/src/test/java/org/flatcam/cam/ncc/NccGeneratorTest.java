package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.CncJobResult;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.junit.jupiter.api.Test;

class NccGeneratorTest {
    @Test
    void copperOffsetAcceptsACollectionContainingMultipartCopperWithoutLegacyListError() {
        for (double mm : List.of(1.,1/25.4)) {
            Polygon first = (Polygon)FACTORY.toGeometry(new Envelope(0,2*mm,0,2*mm));
            Polygon second = (Polygon)FACTORY.toGeometry(new Envelope(4*mm,6*mm,0,2*mm));
            Geometry source = FACTORY.createGeometryCollection(new Geometry[]{
                    FACTORY.createMultiPolygon(new Polygon[]{first,second})});
            String original = source.toText();
            var params = new NccParameters(List.of(.2*mm,.5*mm),.4,mm,NccMethod.STANDARD,false,true,0,
                    false,NccOrder.NONE,new NccBoundary.Itself(),List.of(),Map.of(.2*mm,
                    new NccToolSettings(.25,NccMethod.STANDARD,false,true,.1*mm)));
            var result = NccGenerator.generate(mm==1?"MM":"IN",source,params);
            assertEquals(2,result.toolResults().size());
            assertTrue(result.toolResults().stream().noneMatch(NccToolResult::isEmpty));
            assertEquals(0,result.totalFailedPolygonCount());
            assertTrue(result.geometry().isValid());
            assertFalse(result.geometry().intersects(source.getGeometryN(0)));
            assertEquals(original,source.toText());
        }
    }

    @Test
    void pythonSeedUsesTheRepresentativeScanLineWithoutChangingTheStableDefault() {
        for (double scale : List.of(1.,1 / 25.4)) {
            Geometry copper = FACTORY.toGeometry(new Envelope(1*scale,3*scale,1*scale,3*scale));
            Geometry board = FACTORY.toGeometry(new Envelope(0,20*scale,0,12*scale));
            String original = copper.toText();
            double diameter = .5*scale;
            var stable = new NccParameters(List.of(diameter),.4,0,NccMethod.SEED,false,false,0,
                    false,NccOrder.NONE,new NccBoundary.Area(board));
            var legacy = new NccParameters(List.of(diameter),.4,0,NccMethod.SEED,false,false,0,
                    false,NccOrder.NONE,new NccBoundary.Area(board),List.of(),Map.of(diameter,
                    new NccToolSettings(.4,NccMethod.SEED,false,false,0,NccSeedPolicy.PYTHON)));
            var defaultResult = NccGenerator.generate("MM",copper,stable);
            var stableExplicit = new NccParameters(List.of(diameter),.4,0,NccMethod.SEED,false,false,0,
                    false,NccOrder.NONE,new NccBoundary.Area(board),List.of(),Map.of(diameter,
                    new NccToolSettings(.4,NccMethod.SEED,false,false,0,NccSeedPolicy.STABLE)));
            assertEquals(defaultResult.geometry().toText(),NccGenerator.generate("MM",copper,stableExplicit).geometry().toText());
            var legacyResult = NccGenerator.generate("MM",copper,legacy);
            Geometry safe = org.flatcam.cam.ncc.geosbuffer.GeosBufferOp.bufferOp(legacyResult.clearingArea(),
                    -diameter/2,new org.locationtech.jts.operation.buffer.BufferParameters(64));
            Coordinate seed = safe.getInteriorPoint().getCoordinate();
            Coordinate start = legacyResult.geometry().getGeometryN(0).getCoordinate();
            assertEquals(diameter/2*(1-.4),start.distance(seed),1e-10*scale);
            assertTrue(safe.buffer(1e-10*scale).covers(legacyResult.geometry()));
            assertFalse(defaultResult.geometry().equalsExact(legacyResult.geometry()));
            assertEquals(original,copper.toText());
        }
    }

    @Test
    void restKeepsTheSeedPolicyPerToolAndRejectsNullPolicy() {
        var params = new NccParameters(List.of(.5),.4,1,NccMethod.SEED,true,true,0,
                true,NccOrder.NONE,new NccBoundary.Itself(),List.of(),Map.of(.5,
                new NccToolSettings(.3,NccMethod.SEED,false,false,.1,NccSeedPolicy.PYTHON)));
        assertEquals(NccSeedPolicy.PYTHON,params.settingsFor(.5).seedPolicy());
        assertTrue(params.settingsFor(.5).connect());
        assertTrue(params.settingsFor(.5).contour());
        assertEquals(0,params.settingsFor(.5).copperOffset());
        assertThrows(NullPointerException.class,() -> new NccToolSettings(.4,NccMethod.SEED,false,true,0,null));
    }


    private static final GeometryFactory FACTORY = new GeometryFactory();

    @Test
    void shallowConvexMarginMatchesGeosAcrossBoundaryKindsUnitsAndTranslation() throws Exception {
        var reader = new org.locationtech.jts.io.WKTReader();
        Geometry source = reader.read("POLYGON ((0 0,30 0,30 20,15 20.15,0 20,0 0))");
        // Actual GEOS 3.10.3 result from the independent preparation probe.
        Geometry expectedBoundary = reader.read("POLYGON ((-1 -1,-0.9999999999999999 20.990049998750063,"
                + "15 21.150049998750063,31 20.990049998750063,31 -1,-1 -1))");
        for (double scale : new double[]{1,1 / 25.4}) {
            for (double shift : new double[]{0,-40}) {
                var transform = org.locationtech.jts.geom.util.AffineTransformation.translationInstance(shift,shift / 2);
                transform.scale(scale,scale);
                Geometry input = transform.transform(source);
                Geometry expected = transform.transform(expectedBoundary).difference(input);
                for (NccBoundary boundary : List.of(new NccBoundary.Itself(),new NccBoundary.Area(input),
                        new NccBoundary.ReferenceGerber(input),new NccBoundary.ReferenceGeometry(input))) {
                    var params = new NccParameters(List.of(.5 * scale),.4,scale,NccMethod.STANDARD,
                            true,true,0,false,NccOrder.NONE,boundary,List.of());
                    var result = NccGenerator.generate(scale == 1 ? "MM" : "IN",input,params);
                    assertEquals(0,result.clearingArea().symDifference(expected).getArea(),1e-9 * scale * scale);
                    assertFalse(result.isEmpty());
                }
            }
        }
    }

    @Test
    void importedMultipartCollectionIsPreparedWithoutMutatingItsSource() throws Exception {
        var reader = new org.locationtech.jts.io.WKTReader();
        Geometry multipart = reader.read("MULTIPOLYGON (((0 0,6 0,6 8,0 8,0 0)),((8 0,14 0,14 8,8 8,8 0)))");
        Geometry input = FACTORY.createGeometryCollection(new Geometry[]{multipart});
        String original = input.toText();
        Geometry prepared = NccCopperUnion.union(input,org.flatcam.cam.CancellationToken.none());
        var params = new NccParameters(.5,.4,1,NccMethod.STANDARD,true,true,0);
        var listedResult = NccGenerator.generate("MM",input,params);
        var preparedResult = NccGenerator.generate("MM",prepared,params);
        assertEquals(preparedResult.geometry().toText(),listedResult.geometry().toText());
        assertEquals(0,listedResult.clearingArea().symDifference(preparedResult.clearingArea()).getArea(),1e-10);
        assertEquals(original,input.toText(),"NCC must not reorder the imported project in place");
    }

    @Test
    void seedStopsAtTheFirstEmptyAnnulusLikePython() {
        Geometry left = FACTORY.toGeometry(new Envelope(0, 4, 0, 4));
        Geometry right = FACTORY.toGeometry(new Envelope(20, 22, 1, 3));
        Geometry neck = FACTORY.toGeometry(new Envelope(3, 21, 1.9, 2.1));
        Geometry polygon = left.union(neck).union(right);
        PaintParameters noContour = new PaintParameters(List.of(0.5), 0.4, 0,
                NccMethod.SEED, false, false, NccOrder.NONE, false);
        NccResult result = NccGenerator.paint("MM", polygon, noContour,
                CancellationToken.none(), fraction -> { });

        assertFalse(result.isEmpty());
        assertTrue(result.geometry().getEnvelopeInternal().getMaxX() < 4,
                "The narrow neck erodes away: expanding rings must stop before the remote island");

        PaintParameters contour = new PaintParameters(List.of(0.5), 0.4, 0,
                NccMethod.SEED, false, true, NccOrder.NONE, false);
        NccResult withContour = NccGenerator.paint("MM", polygon, contour,
                CancellationToken.none(), fraction -> { });
        assertTrue(withContour.geometry().getEnvelopeInternal().getMaxX() > 21,
                "Contour still includes every eroded component, independently of the ring stop");
    }

    @Test
    void allLegacyStrategiesProduceSafeToolpaths() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        for (NccMethod method : NccMethod.values()) {
            NccParameters params = new NccParameters(0.5, 0.15, 2.0, method, true, true, 0);
            NccResult result = NccGenerator.generate("MM", copper, params);

            assertFalse(result.isEmpty(), method + " should clear the area around the copper");
            assertTrue(result.pathCount() > 0);
            assertTrue(result.totalLength() > 0);
            assertEquals(0, result.totalFailedPolygonCount());
            assertEquals(1, result.toolResults().size());

            // Cutter-center paths inset by r must keep the physical cutter inside
            // the non-copper area, allowing only polygon-approximation noise.
            Geometry footprint = result.geometry().buffer(0.5 / 2.0, 64);
            double escapedArea = footprint.difference(result.clearingArea().buffer(1e-4)).getArea();
            assertTrue(escapedArea < 5e-4,
                    method + " cutter footprint escaped the clearing area by " + escapedArea);
        }
    }

    @Test
    void copperOffsetCreatesExtraKeepOutDistance() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccResult plain = NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.STANDARD, false, true, 0));
        NccResult offset = NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.STANDARD, false, true, 0.4));

        assertTrue(offset.clearingArea().getArea() < plain.clearingArea().getArea());
        assertTrue(offset.geometry().distance(copper) > plain.geometry().distance(copper));
    }

    @Test
    void reportsMonotonicProgressAndCompletesAtOne() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        List<Double> reports = new ArrayList<>();
        NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.15, 2, NccMethod.LINES, true, true, 0),
                CancellationToken.none(), reports::add);

        assertFalse(reports.isEmpty());
        assertEquals(1.0, reports.get(reports.size() - 1));
        for (int i = 1; i < reports.size(); i++) {
            assertTrue(reports.get(i) >= reports.get(i - 1), "progress must be monotonic");
        }
    }

    @Test
    void honorsCooperativeCancellation() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        AtomicInteger checks = new AtomicInteger();
        CancellationToken cancellation = () -> checks.incrementAndGet() >= 4;

        assertThrows(CancellationException.class, () -> NccGenerator.generate("MM", copper,
                new NccParameters(0.1, 0.5, 5, NccMethod.STANDARD, true, true, 0),
                cancellation, fraction -> { }));
        assertTrue(checks.get() >= 4);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(0, 0.15, 1, NccMethod.STANDARD, true, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(1, 1, 1, NccMethod.STANDARD, true, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(1, 0.15, -1, NccMethod.STANDARD, true, true, 0));
        assertThrows(NullPointerException.class,
                () -> new NccParameters(1, 0.15, 1, null, true, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(List.of(), 0.15, 1, NccMethod.STANDARD, true, true, 0, false, NccOrder.NONE));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(List.of(0.5, 0.5), 0.15, 1, NccMethod.STANDARD, true, true, 0, false, NccOrder.NONE));
        assertThrows(NullPointerException.class,
                () -> new NccParameters(List.of(0.5), 0.15, 1, NccMethod.STANDARD, true, true, 0, false, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(List.of(), 0.15, 1, NccMethod.STANDARD, true, true, 0,
                        false, NccOrder.NONE, new NccBoundary.Itself(), List.of(0.2)));
        assertThrows(IllegalArgumentException.class,
                () -> new NccParameters(List.of(0.5), 0.15, 1, NccMethod.STANDARD, true, true, 0,
                        false, NccOrder.NONE, new NccBoundary.Itself(), List.of(0.5)));
    }

    @Test
    void isoToolCreatesItsOwnClippedContourBeforeClearTools() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters params = new NccParameters(List.of(0.2), 0.15, 2.0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(0.4));

        NccResult result = NccGenerator.generate("MM", copper, params);
        NccResult withoutIso = NccGenerator.generate("MM", copper,
                new NccParameters(0.2, 0.15, 2.0, NccMethod.STANDARD, false, true, 0));

        assertEquals(2, result.toolResults().size());
        assertEquals(NccOperation.ISO, result.toolResults().get(0).operation());
        assertEquals(0.4, result.toolResults().get(0).toolDiameter());
        assertFalse(result.toolResults().get(0).isEmpty());
        assertEquals(0.2, result.toolResults().get(0).geometry().distance(copper), 0.005);
        assertEquals(NccOperation.CLEAR, result.toolResults().get(1).operation());
        assertFalse(result.toolResults().get(1).isEmpty());
        assertTrue(result.clearingArea().getArea() < withoutIso.clearingArea().getArea());
    }

    @Test
    void isoContourRespectsSelectedAreaBoundary() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry selected = FACTORY.toGeometry(new Envelope(4, 5, 3, 7));
        NccParameters params = new NccParameters(List.of(0.2), 0.15, 0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Area(selected), List.of(0.4));

        NccResult result = NccGenerator.generate("MM", copper, params);

        Geometry isoPaths = result.toolResults().get(0).geometry();
        assertFalse(isoPaths.isEmpty());
        assertTrue(selected.buffer(1e-9).covers(isoPaths));
    }

    @Test
    void mixedIsoAndClearGeometryCanBecomeOneMultiToolCncJob() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters params = new NccParameters(List.of(0.2), 0.15, 2,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(0.4));
        NccResult result = NccGenerator.generate("MM", copper, params);
        List<ToolGeometry> tools = result.toolResults().stream()
                .filter(tool -> !tool.isEmpty())
                .map(tool -> new ToolGeometry(tool.toolDiameter(), tool.geometry()))
                .toList();

        CncJobResult job = GCodeGenerator.generateGeometryCncJob("MM", tools,
                new GeometryGCodeParameters(3, 0.1, false, 1, 300, 10_000, true));

        assertEquals(2, tools.size());
        assertTrue(job.gcode().contains("M0"));
        assertFalse(job.cutGeometry().isEmpty());
    }

    @Test
    void climbReversesIsoExteriorWithoutChangingClearingArea() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters climb = new NccParameters(List.of(0.2), 0.15, 2,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(0.4), Map.of(), NccMillingType.CLIMB);
        NccParameters conventional = new NccParameters(List.of(0.2), 0.15, 2,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(0.4), Map.of(), NccMillingType.CONVENTIONAL);

        NccResult climbResult = NccGenerator.generate("MM", copper, climb);
        NccResult conventionalResult = NccGenerator.generate("MM", copper, conventional);
        Geometry climbPath = climbResult.toolResults().get(0).geometry();
        Geometry conventionalPath = conventionalResult.toolResults().get(0).geometry();

        assertTrue(climbPath.equalsTopo(conventionalPath));
        assertTrue(signedArea(climbPath.getGeometryN(0).getCoordinates())
                * signedArea(conventionalPath.getGeometryN(0).getCoordinates()) < 0,
                "milling modes must travel around the same exterior in opposite directions");
        assertTrue(climbResult.clearingArea().equalsTopo(conventionalResult.clearingArea()));
    }

    private static double signedArea(Coordinate[] points) {
        double twiceArea = 0;
        for (int i = 0; i < points.length - 1; i++) {
            twiceArea += points[i].x * points[i + 1].y - points[i + 1].x * points[i].y;
        }
        return twiceArea / 2;
    }

    @Test
    void connectPreservesModernOverlaySeamsAcrossUnitsAndTranslation() throws Exception {
        Geometry source = new org.locationtech.jts.io.WKTReader().read(
                "POLYGON ((0 0,30 0,30 20,0 20,0 0),(4 4,4 16,26 16,26 4,4 4))");
        for (double scale : List.of(1.,1 / 25.4)) {
            for (double shift : List.of(0.,-40.)) {
                Geometry copper = org.locationtech.jts.geom.util.AffineTransformation.translationInstance(shift,shift / 2).transform(source);
                copper = org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(scale,scale).transform(copper);
                var result = NccGenerator.generate(scale == 1 ? "MM" : "IN",copper,
                        new NccParameters(.5 * scale,.4,scale,NccMethod.STANDARD,true,true,0));
                // GEOS reference connects the upper-left interior ring endpoints.
                // Classic JTS subtraction started these rings at the lower-left.
                double inset = .5 / 1.999999;
                Coordinate midpoint = new Coordinate((4 + inset + .15 + shift) * scale,
                        (16 - inset - .15 + shift / 2) * scale);
                assertEquals(0,result.geometry().distance(FACTORY.createPoint(midpoint)),1e-12,
                        "Connect's endpoint must follow modern overlay, not an arbitrary ring rotation");
            }
        }
    }

    @Test
    void restLeavesUnreachablePolygonsForSmallerToolWithoutReportingAnAlgorithmFailure() {
        Geometry copper = FACTORY.toGeometry(new Envelope(0,10,0,10));
        Geometry strip = FACTORY.toGeometry(new Envelope(0,10,10.2,11));
        var boundary = new NccBoundary.Area(strip);
        var rest = NccGenerator.generate("MM",copper,new NccParameters(List.of(3.,.2),.4,0,
                NccMethod.STANDARD,false,true,0,true,NccOrder.NONE,boundary));
        assertTrue(rest.toolResults().getFirst().isEmpty());
        assertEquals(0,rest.totalFailedPolygonCount());
        assertFalse(rest.toolResults().getLast().isEmpty());
        var normal = NccGenerator.generate("MM",copper,new NccParameters(List.of(3.,.2),.4,0,
                NccMethod.STANDARD,false,true,0,false,NccOrder.NONE,boundary));
        assertEquals(1,normal.toolResults().getFirst().failedPolygonCount(),
                "Only Rest has the GUI unreachable-polygon skip policy");
    }

    @Test
    void restFootprintMatchesGuiDefaultResolutionAndRepairPolicy() throws Exception {
        Geometry copper = new org.locationtech.jts.io.WKTReader().read(
                "POLYGON ((0 0,30 0,30 20,0 20,0 0),(4 4,4 16,26 16,26 4,4 4))");
        var result = NccGenerator.generate("MM",copper,new NccParameters(List.of(.2,1.),.4,1,
                NccMethod.STANDARD,false,true,0,true,NccOrder.NONE));
        // Captured independently from the unchanged GUI initializer under
        // Shapely 1.8.5.post1 / GEOS 3.10.3 on this public polygon.
        assertEquals(431.1408414811011,result.toolResults().getFirst().geometry().getLength(),1e-7);
        assertEquals(768.5374933916033,result.toolResults().getLast().geometry().getLength(),1e-7);
        assertEquals(0,result.totalFailedPolygonCount());
    }

    @Test
    void restMachiningLimitsASmallerToolToWhatALargerToolLeftBehind() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccResult smallAlone = NccGenerator.generate("MM", copper,
                new NccParameters(0.2, 0.1, 2.0, NccMethod.STANDARD, false, true, 0));
        assertFalse(smallAlone.isEmpty());

        NccParameters restParams = new NccParameters(List.of(0.2, 1.0), 0.1, 2.0,
                NccMethod.STANDARD, false, true, 0, true, NccOrder.NONE);
        NccResult restResult = NccGenerator.generate("MM", copper, restParams);

        assertEquals(2, restResult.toolResults().size());
        NccToolResult big = restResult.toolResults().get(0);
        NccToolResult small = restResult.toolResults().get(1);
        assertEquals(1.0, big.toolDiameter(), 1e-9, "rest machining always goes largest-first");
        assertEquals(0.2, small.toolDiameter(), 1e-9);
        assertFalse(big.isEmpty(), "the big tool should clear most of the open area");

        double smallAloneLength = smallAlone.totalLength();
        double smallInRestLength = small.isEmpty() ? 0.0 : small.geometry().getLength();
        assertTrue(smallInRestLength < smallAloneLength * 0.5,
                "the small tool should clear much less once the big tool already took most of the area");
    }

    @Test
    void nonRestMultiToolClearsTheFullAreaWithEveryTool() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters params = new NccParameters(List.of(0.2, 0.5), 0.1, 2.0,
                NccMethod.STANDARD, true, true, 0, false, NccOrder.FORWARD);
        NccResult result = NccGenerator.generate("MM", copper, params);

        assertEquals(2, result.toolResults().size());
        assertEquals(0.2, result.toolResults().get(0).toolDiameter(), 1e-9, "FORWARD orders ascending");
        assertEquals(0.5, result.toolResults().get(1).toolDiameter(), 1e-9);
        for (NccToolResult toolResult : result.toolResults()) {
            assertFalse(toolResult.isEmpty(), "every tool clears the same full area independently");
        }
    }

    @Test
    void restMachiningUsesTheSelectedAreaBoundaryNotTheCoppersOwnHull() {
        // generate() resolves the boundary (Itself/Area/Reference) once, before the per-tool
        // Rest Machining loop, so a custom NccBoundary.Area should carry through to every tool
        // exactly like it does outside Rest Machining. This combination (a non-Itself boundary
        // together with restMachining=true) had no regression test before.
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry selectedArea = FACTORY.toGeometry(new Envelope(0, 8, 0, 8));

        NccParameters bigAlone = new NccParameters(List.of(1.0), 0.1, 0.0, NccMethod.STANDARD,
                false, true, 0, false, NccOrder.NONE, new NccBoundary.Area(selectedArea));
        NccResult bigAloneResult = NccGenerator.generate("MM", copper, bigAlone);

        NccParameters restParams = new NccParameters(List.of(0.2, 1.0), 0.1, 0.0, NccMethod.STANDARD,
                false, true, 0, true, NccOrder.NONE, new NccBoundary.Area(selectedArea));
        NccResult restResult = NccGenerator.generate("MM", copper, restParams);

        assertEquals(2, restResult.toolResults().size());
        NccToolResult big = restResult.toolResults().get(0);
        NccToolResult small = restResult.toolResults().get(1);
        assertEquals(1.0, big.toolDiameter(), 1e-9, "rest machining always goes largest-first");
        assertEquals(0.2, small.toolDiameter(), 1e-9);

        assertEquals(bigAloneResult.clearingArea().getArea(), restResult.clearingArea().getArea(), 1e-6,
                "the selected area, not the copper's own convex hull, bounds every tool under Rest Machining");
        assertEquals(bigAloneResult.toolResults().get(0).geometry().getLength(), big.geometry().getLength(), 1e-6,
                "the first (largest) rest tool clears exactly as much as it would alone, inside the selected area");
        assertFalse(small.isEmpty(),
                "the smaller tool should still find leftover corners the big tool could not reach");
    }

    @Test
    void restMachiningUsesTheSelectedAreaBoundaryInInchesToo() {
        // Same fixture as restMachiningUsesTheSelectedAreaBoundaryNotTheCoppersOwnHull, scaled
        // by 1/25.4 and relabeled "IN": generate() takes every distance as a plain number, with
        // no internal MM<->IN conversion, so the same relative outcome should hold regardless
        // of which unit the numbers represent.
        Geometry copper = FACTORY.toGeometry(new Envelope(4 / 25.4, 6 / 25.4, 4 / 25.4, 6 / 25.4));
        Geometry selectedArea = FACTORY.toGeometry(new Envelope(0, 8 / 25.4, 0, 8 / 25.4));

        NccParameters bigAlone = new NccParameters(List.of(1.0 / 25.4), 0.1, 0.0, NccMethod.STANDARD,
                false, true, 0, false, NccOrder.NONE, new NccBoundary.Area(selectedArea));
        NccResult bigAloneResult = NccGenerator.generate("IN", copper, bigAlone);

        NccParameters restParams = new NccParameters(List.of(0.2 / 25.4, 1.0 / 25.4), 0.1, 0.0,
                NccMethod.STANDARD, false, true, 0, true, NccOrder.NONE, new NccBoundary.Area(selectedArea));
        NccResult restResult = NccGenerator.generate("IN", copper, restParams);

        assertEquals(2, restResult.toolResults().size());
        NccToolResult big = restResult.toolResults().get(0);
        NccToolResult small = restResult.toolResults().get(1);
        assertEquals(1.0 / 25.4, big.toolDiameter(), 1e-9, "rest machining always goes largest-first");
        assertEquals(0.2 / 25.4, small.toolDiameter(), 1e-9);

        assertEquals(bigAloneResult.clearingArea().getArea(), restResult.clearingArea().getArea(), 1e-9,
                "the selected area, not the copper's own convex hull, bounds every tool under Rest Machining");
        assertEquals(bigAloneResult.toolResults().get(0).geometry().getLength(), big.geometry().getLength(), 1e-9,
                "the first (largest) rest tool clears exactly as much as it would alone, inside the selected area");
        assertFalse(small.isEmpty(),
                "the smaller tool should still find leftover corners the big tool could not reach");
    }

    @Test
    void referenceGerberBoundaryIntersectsBothConvexHulls() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry referenceGerber = FACTORY.toGeometry(new Envelope(4, 5, 0, 10));

        NccParameters itself = new NccParameters(List.of(0.2), 0.1, 2.0, NccMethod.STANDARD, false, true, 0,
                false, NccOrder.NONE, new NccBoundary.Itself());
        NccParameters referenced = new NccParameters(List.of(0.2), 0.1, 2.0, NccMethod.STANDARD, false, true, 0,
                false, NccOrder.NONE, new NccBoundary.ReferenceGerber(referenceGerber));

        NccResult itselfResult = NccGenerator.generate("MM", copper, itself);
        NccResult referencedResult = NccGenerator.generate("MM", copper, referenced);

        assertFalse(itselfResult.isEmpty());
        assertFalse(referencedResult.isEmpty());
        assertTrue(referencedResult.clearingArea().getArea() < itselfResult.clearingArea().getArea(),
                "the reference Gerber's convex hull should confine the boundary to a smaller area");
    }

    @Test
    void referenceGeometryBoundaryUsesRawShapeNotConvexHull() {
        Geometry big = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        Geometry notch = FACTORY.toGeometry(new Envelope(0, 5, 0, 5));
        Geometry lShapedReference = big.difference(notch);
        Geometry copper = FACTORY.toGeometry(new Envelope(7, 8, 7, 8));

        NccParameters params = new NccParameters(List.of(0.2), 0.1, 0.0, NccMethod.STANDARD, false, true, 0,
                false, NccOrder.NONE, new NccBoundary.ReferenceGeometry(lShapedReference));
        NccResult result = NccGenerator.generate("MM", copper, params);

        Geometry notchPoint = FACTORY.createPoint(new Coordinate(2, 2));
        assertFalse(result.clearingArea().contains(notchPoint),
                "the L-shaped reference's own notch must stay excluded - a convex hull would fill it in");
    }

    @Test
    void nonRestToolsUseTheirOwnMethodOverlapAndCopperOffset() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccToolSettings first = new NccToolSettings(0.1, NccMethod.STANDARD, false, true, 0);
        NccToolSettings second = new NccToolSettings(0.4, NccMethod.LINES, true, false, 0.6);
        NccParameters params = new NccParameters(List.of(0.2, 0.5), 0.1, 2,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(), Map.of(0.2, first, 0.5, second));

        NccResult result = NccGenerator.generate("MM", copper, params);
        NccResult firstAlone = NccGenerator.generate("MM", copper,
                new NccParameters(0.2, 0.1, 2, NccMethod.STANDARD, false, true, 0));
        NccResult secondAlone = NccGenerator.generate("MM", copper,
                new NccParameters(0.5, 0.4, 2, NccMethod.LINES, true, false, 0.6));

        assertEquals(firstAlone.totalLength(), result.toolResults().get(0).geometry().getLength(), 1e-8);
        assertEquals(secondAlone.totalLength(), result.toolResults().get(1).geometry().getLength(), 1e-8);
        assertEquals(firstAlone.clearingArea().getArea(), result.clearingArea().getArea(), 1e-8,
                "the represented area is the union of the tool-specific clearing areas");
    }

    @Test
    void connectRefusesAJoinWhoseToolWidthWouldEscapeANarrowCorridor() {
        // camlib.py's paint_connect buffers the candidate connector by the tool radius before
        // testing it against the safe area - a bare centerline can thread a corridor the tool
        // itself would not fit through. Dumbbell: two 4x4 rooms joined by a corridor only 0.5
        // tall, well under a 0.9-diameter tool's own width.
        Geometry left = FACTORY.toGeometry(new Envelope(0, 4, 0, 4));
        Geometry right = FACTORY.toGeometry(new Envelope(10, 14, 0, 4));
        Geometry corridor = FACTORY.toGeometry(new Envelope(4, 10, 1.75, 2.25));
        Geometry safeArea = left.union(corridor).union(right);
        LineString pathInLeftRoom = FACTORY.createLineString(
                new Coordinate[]{new Coordinate(1, 2), new Coordinate(3, 2)});
        LineString pathInRightRoom = FACTORY.createLineString(
                new Coordinate[]{new Coordinate(11, 2), new Coordinate(13, 2)});

        List<LineString> result = NccGenerator.connectSafePaths(
                new ArrayList<>(List.of(pathInLeftRoom, pathInRightRoom)), safeArea, 0.9,
                FACTORY, CancellationToken.none());

        assertEquals(2, result.size(), "the tool-wide corridor is too narrow to join through; both lifts stay separate");
    }

    @Test
    void connectJoinsThroughACorridorWideEnoughForTheToolItself() {
        // Same shape, but the corridor is 1.2 tall - comfortably wider than a 0.9-diameter tool.
        Geometry left = FACTORY.toGeometry(new Envelope(0, 4, 0, 4));
        Geometry right = FACTORY.toGeometry(new Envelope(10, 14, 0, 4));
        Geometry corridor = FACTORY.toGeometry(new Envelope(4, 10, 1.4, 2.6));
        Geometry safeArea = left.union(corridor).union(right);
        LineString pathInLeftRoom = FACTORY.createLineString(
                new Coordinate[]{new Coordinate(1, 2), new Coordinate(3, 2)});
        LineString pathInRightRoom = FACTORY.createLineString(
                new Coordinate[]{new Coordinate(11, 2), new Coordinate(13, 2)});

        List<LineString> result = NccGenerator.connectSafePaths(
                new ArrayList<>(List.of(pathInLeftRoom, pathInRightRoom)), safeArea, 0.9,
                FACTORY, CancellationToken.none());

        assertEquals(1, result.size(), "wide enough for the tool: both paths join into one continuous travel");
    }

    @Test
    void connectRefusesAJoinLongerThanTenToolDiameters() {
        // camlib.py's paint_connect defaults max_walk to 10 * tooldia: a connector can be
        // entirely safe and still get refused for being an excessively long "joining" travel.
        Geometry safeArea = FACTORY.toGeometry(new Envelope(0, 20, 0, 2)); // tall and wide enough for the tool, no obstruction
        double toolDiameter = 0.5; // max_walk = 5
        LineString firstPath = FACTORY.createLineString(new Coordinate[]{new Coordinate(0.5, 1), new Coordinate(1, 1)});
        LineString farPath = FACTORY.createLineString(new Coordinate[]{new Coordinate(18, 1), new Coordinate(18.5, 1)});

        List<LineString> result = NccGenerator.connectSafePaths(
                new ArrayList<>(List.of(firstPath, farPath)), safeArea, toolDiameter,
                FACTORY, CancellationToken.none());

        assertEquals(2, result.size(), "17mm of travel exceeds max_walk (5mm) even though the area is otherwise wide open");
    }

    @Test
    void connectJoinsAShortSafeTravelWithinMaxWalk() {
        Geometry safeArea = FACTORY.toGeometry(new Envelope(0, 20, 0, 2));
        double toolDiameter = 0.5; // max_walk = 5
        LineString firstPath = FACTORY.createLineString(new Coordinate[]{new Coordinate(0.5, 1), new Coordinate(1, 1)});
        LineString nearPath = FACTORY.createLineString(new Coordinate[]{new Coordinate(4, 1), new Coordinate(4.5, 1)});

        List<LineString> result = NccGenerator.connectSafePaths(
                new ArrayList<>(List.of(firstPath, nearPath)), safeArea, toolDiameter,
                FACTORY, CancellationToken.none());

        assertEquals(1, result.size(), "3mm of travel is within max_walk and the area is safe: must join");
    }

    @Test void connectStartsAtNearestEndpointToOriginButDoesNotReverseFirstPath() {
        var far = FACTORY.createLineString(new Coordinate[]{new Coordinate(20,2),new Coordinate(21,2)});
        var near = FACTORY.createLineString(new Coordinate[]{new Coordinate(3,2),new Coordinate(1,2)});
        var safe = FACTORY.toGeometry(new Envelope(0,30,0,4));
        var result = NccGenerator.connectSafePaths(List.of(far,near),safe,.5,FACTORY,CancellationToken.none());
        assertTrue(result.getFirst().getCoordinateN(0).equals2D(new Coordinate(3,2)),
                "paint_connect selects the near path but preserves its original direction");
        assertEquals(2,result.size());
    }

    @Test void nccConnectTestsSweptToolAgainstOriginalAreaNotAnAlreadyErodedArea() {
        Geometry area = FACTORY.toGeometry(new Envelope(0,10,0,6));
        var params = new PaintParameters(List.of(.5),.4,0,NccMethod.STANDARD,true,true,NccOrder.NONE,false);
        var result = NccGenerator.paint("MM",area,params,CancellationToken.none(),ProgressCallback.none());
        assertEquals(1,result.geometry().getNumGeometries(),"nested Standard rings should connect without double erosion");
        assertTrue(area.covers(result.geometry().buffer(.25,64)),"the physical swept cutter stays inside the original polygon");
    }

    @Test void connectorCannotUseTheOldOutsideToleranceBuffer() {
        Geometry safe = FACTORY.toGeometry(new Envelope(0,10,0,2));
        var a = FACTORY.createLineString(new Coordinate[]{new Coordinate(1,.25 - 5e-11),new Coordinate(2,.25 - 5e-11)});
        var b = FACTORY.createLineString(new Coordinate[]{new Coordinate(3,.25 - 5e-11),new Coordinate(4,.25 - 5e-11)});
        assertEquals(2,NccGenerator.connectSafePaths(List.of(a,b),safe,.5,FACTORY,CancellationToken.none()).size());
    }

    @Test
    void restKeepsPerToolMethodAndOverlapButUsesCommonOffsetConnectAndContour() {
        NccToolSettings override = new NccToolSettings(0.6, NccMethod.LINES, false, false, 0.8);
        NccParameters params = new NccParameters(List.of(0.2), 0.1, 1,
                NccMethod.STANDARD, true, true, 0.1, true, NccOrder.NONE,
                new NccBoundary.Itself(), List.of(), Map.of(0.2, override));

        NccToolSettings resolved = params.settingsFor(0.2);

        assertEquals(0.6, resolved.overlapFraction());
        assertEquals(NccMethod.LINES, resolved.method());
        assertTrue(resolved.connect());
        assertTrue(resolved.contour());
        assertEquals(0.1, resolved.copperOffset());
    }

    @Test
    void lineReferenceReceivesMarginBeforePolygonalCleaning() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry lineReference = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0)});
        NccParameters params = new NccParameters(List.of(0.2), 0.1, 1.0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.ReferenceGeometry(lineReference));

        NccResult result = NccGenerator.generate("MM", copper, params);

        assertTrue(result.clearingArea().getArea() > 0,
                "a line reference must become a corridor when a positive margin is requested");
        assertFalse(result.isEmpty());
    }

    @Test
    void lineReferenceWithoutMarginReportsMissingFilledBoundary() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry lineReference = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0)});
        NccParameters params = new NccParameters(List.of(0.2), 0.1, 0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.ReferenceGeometry(lineReference));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NccGenerator.generate("MM", copper, params));
        assertTrue(error.getMessage().contains("limite do NCC"));
    }

    @Test
    void selectedAreaLimitsNccToItsRectanglePlusMargin() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry selected = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        NccParameters params = new NccParameters(List.of(0.5), 0.2, 0.5,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Area(selected));

        NccResult result = NccGenerator.generate("MM", copper, params);

        assertFalse(result.isEmpty());
        assertTrue(result.clearingArea().covers(FACTORY.createPoint(new Coordinate(-0.25, 5))));
        assertFalse(result.clearingArea().covers(FACTORY.createPoint(new Coordinate(12, 5))));
        assertFalse(result.clearingArea().covers(FACTORY.createPoint(new Coordinate(5, 5))));
        assertThrows(IllegalArgumentException.class,
                () -> new NccBoundary.Area(FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(0, 0), new Coordinate(1, 1)})));
    }

    @Test
    void polygonAreaSelectionRespectsItsConcaveNotch() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        Geometry polygon = FACTORY.createPolygon(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10),
                new Coordinate(7, 10), new Coordinate(7, 3), new Coordinate(0, 3),
                new Coordinate(0, 0)});
        NccParameters params = new NccParameters(List.of(0.2), 0.1, 0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE,
                new NccBoundary.Area(polygon));

        NccResult result = NccGenerator.generate("MM", copper, params);

        assertTrue(result.clearingArea().covers(FACTORY.createPoint(new Coordinate(2, 1))));
        assertFalse(result.clearingArea().covers(FACTORY.createPoint(new Coordinate(2, 8))));
        assertTrue(polygon.buffer(1e-8).covers(result.geometry()));
    }

    @Test
    void geometryLineSourceIsRejectedInsteadOfClearingItsWholeEnvelope() {
        Geometry outline = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10),
                new Coordinate(0, 10), new Coordinate(0, 0)});
        NccParameters params = new NccParameters(0.5, 0.2, 1.0,
                NccMethod.STANDARD, false, true, 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NccGenerator.generate("MM", outline, params));
        assertTrue(error.getMessage().contains("area preenchida"));
    }

    @Test
    void filledGeometrySourceCanBeCleared() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters params = new NccParameters(0.5, 0.2, 1.0,
                NccMethod.STANDARD, false, true, 0);

        NccResult result = NccGenerator.generate("MM", copper, params);

        assertTrue(result.clearingArea().getArea() > 0);
        assertFalse(result.isEmpty());
    }

    @Test
    void noOrderPreservesToolTableInsertionOrder() {
        Geometry copper = FACTORY.toGeometry(new Envelope(4, 6, 4, 6));
        NccParameters params = new NccParameters(List.of(0.8, 0.2, 0.5), 0.1, 2.0,
                NccMethod.STANDARD, false, true, 0, false, NccOrder.NONE);

        NccResult result = NccGenerator.generate("MM", copper, params);

        assertEquals(List.of(0.8, 0.2, 0.5), result.toolResults().stream()
                .map(NccToolResult::toolDiameter).toList());
    }

    @Test
    void minimumCopperClearanceFindsTheNarrowestGapBetweenParts() {
        Geometry a = FACTORY.toGeometry(new Envelope(0, 1, 0, 1));
        Geometry b = FACTORY.toGeometry(new Envelope(1.3, 2.3, 0, 1));
        Geometry copper = a.union(b);

        OptionalDouble clearance = NccGenerator.minimumCopperClearance(copper);
        assertTrue(clearance.isPresent());
        assertEquals(0.3, clearance.getAsDouble(), 1e-9);
    }

    @Test
    void minimumCopperClearanceIsEmptyWithFewerThanTwoParts() {
        Geometry single = FACTORY.toGeometry(new Envelope(0, 1, 0, 1));
        assertTrue(NccGenerator.minimumCopperClearance(single).isEmpty());
        assertTrue(NccGenerator.minimumCopperClearance(FACTORY.createPolygon()).isEmpty());
        assertTrue(NccGenerator.minimumCopperClearance(null).isEmpty());
    }

    @Test
    void clearsARealParsedGerber() throws Exception {
        GerberImage image = new GerberParser().parse(findRepoRoot().resolve("tests/gerber_files/simple1.gbr"));
        NccResult result = NccGenerator.generate(image.units(), image.solidGeometry(),
                new NccParameters(0.02, 0.40, 0.04, NccMethod.COMBO, true, true, 0));

        assertFalse(result.isEmpty());
        assertEquals(image.units(), result.units());
        assertTrue(result.clearingArea().getArea() > 0);
        assertTrue(result.totalLength() > 0);
    }

    private static java.nio.file.Path findRepoRoot() {
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null) {
            if (java.nio.file.Files.isDirectory(dir.resolve("tests/gerber_files"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root");
    }
}
