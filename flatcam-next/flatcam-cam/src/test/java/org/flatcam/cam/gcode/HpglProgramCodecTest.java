package org.flatcam.cam.gcode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.hpgl.HpglImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class HpglProgramCodecTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    private static Geometry path(double... xy) {
        Coordinate[] points = new Coordinate[xy.length / 2];
        for (int i = 0; i < points.length; i++) points[i] = new Coordinate(xy[i * 2], xy[i * 2 + 1]);
        return FACTORY.createLineString(points);
    }

    private static CncJobResult generate(String units, List<ToolGeometry> tools) {
        return GCodeGenerator.generateGeometryCncJob(units, tools,
                new GeometryGCodeParameters(2, 1, false, 1, 100, 10000, false),
                Map.of(), CancellationToken.none(), GCodePreprocessor.HPGL);
    }

    private static GCodeToolpathParser.Result parse(String text) {
        return GCodeToolpathParser.parse(text, CancellationToken.none(), ignored -> {});
    }

    @Test
    void generatedProgramUsesNativeCommandsSafePenStateAndNoMillingCommands() {
        var job = generate("MM", List.of(new ToolGeometry(0.2, path(1, 2, 4, 2))));
        assertEquals("IN;\nCO \"Preprocessor: HPGL\";\nCO \"FCFX HPGL UNITS MM\";\nPU;\nPA;\n"
                + "CO \"FCFX PEN P1 D0.2\";\nSP1;\nPU;\nPA40,80;\nPD;\nPA160,80;\nPU;\nPU;\nSP0;\n", job.gcode());
        assertFalse(job.gcode().lines().anyMatch(line -> line.matches("[GMTF]\\d.*")));
        var preview = parse(job.gcode());
        assertNull(preview.warning());
        assertEquals(3, preview.cutCenterlines().getLength(), 1e-12);
        assertEquals(0.2, preview.stats().cutterDiameter());
        assertTrue(Double.isNaN(preview.stats().estimatedMinutes()), "HPGL does not specify speed");
        assertEquals(job.gcode().lines().count(), preview.lineCount());
        assertEquals(job.cutGeometry().getEnvelopeInternal(), preview.cutGeometry().getEnvelopeInternal());
        assertEquals(".plt", GCodePreprocessor.HPGL.fileExtension());
        assertEquals(List.of("*.plt", "*.hpgl", "*.hpg"), GCodePreprocessor.HPGL.filePatterns());
    }

    @Test
    void multiPenProgramsKeepWidthsAndNeverDrawConnectingTravels() {
        var job = generate("MM", List.of(new ToolGeometry(0.2, path(1, 2, 4, 2)),
                new ToolGeometry(0.4, path(7, 5, 7, 9))));
        var preview = parse(job.gcode());
        assertNull(preview.warning());
        assertEquals(7, preview.cutCenterlines().getLength(), 1e-12);
        assertEquals(1.9, preview.cutGeometry().getEnvelopeInternal().getMinY(), 1e-12);
        assertEquals(9.2, preview.cutGeometry().getEnvelopeInternal().getMaxY(), 1e-12);
        assertFalse(preview.travelCenterlines().isEmpty());
        assertTrue(preview.stats().hits().isEmpty());
        var imported = HpglImporter.parse(job.gcode(), "MM");
        assertEquals(List.of(1, 2), List.copyOf(imported.pens().keySet()));
        assertEquals(7, imported.all().getLength(), 1e-12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MM", "IN"})
    void physicalOutputIsIdenticalAcrossSourceUnitsAndPreviewRetainsSourceUnits(String units) {
        double factor = units.equals("IN") ? 1 / 25.4 : 1;
        var job = generate(units, List.of(new ToolGeometry(0.2 * factor, path(25.4 * factor, 50.8 * factor,
                50.8 * factor, 50.8 * factor))));
        assertTrue(job.gcode().contains("PA1016,2032;"));
        assertTrue(job.gcode().contains("PA2032,2032;"));
        var preview = parse(job.gcode());
        assertEquals(units, preview.units());
        assertEquals(25.4 * factor, preview.cutCenterlines().getLength(), 1e-12);
        assertEquals(25.4, HpglImporter.parse(job.gcode(), "MM").all().getLength(), 1e-12);
    }

    @Test
    void quantizationUsesTiesToEvenAndPreviewFollowsRoundedCoordinates() {
        assertEquals(0, HpglProgramCodec.coordinate(0.0125, "MM"));
        assertEquals(2, HpglProgramCodec.coordinate(0.0375, "MM"));
        assertEquals(-2, HpglProgramCodec.coordinate(-0.0375, "MM"));
        var job = generate("MM", List.of(new ToolGeometry(0.2, path(0.014, 0.014, 0.087, 0.014))));
        assertTrue(job.gcode().contains("PA1,1;"));
        assertTrue(job.gcode().contains("PA3,1;"));
        assertEquals(0.05, parse(job.gcode()).cutCenterlines().getLength(), 1e-12);
    }

    @Test
    void rejectsOverflowAndPathsThatCollapseInsteadOfClippingOrInventingDots() {
        assertEquals(-32767, HpglProgramCodec.coordinate(-32767.0 / 40, "MM"));
        assertEquals(32768, HpglProgramCodec.coordinate(32768.0 / 40, "MM"));
        assertThrows(IllegalArgumentException.class, () -> HpglProgramCodec.coordinate(900, "MM"));
        assertThrows(IllegalArgumentException.class, () -> HpglProgramCodec.coordinate(Double.NaN, "MM"));
        assertThrows(IllegalArgumentException.class, () -> generate("MM", List.of(new ToolGeometry(0.2, path(0, 0, 0.001, 0)))));
        assertThrows(IllegalArgumentException.class, () -> generate("MM", List.of(new ToolGeometry(0.2,
                FACTORY.createPoint(new Coordinate(1, 1))))));
    }

    @Test
    void nativeReaderSupportsCommandStreamsRelativeMovesAndPenMovesWithCoordinates() {
        var preview = parse("\uFEFFIN;PU0,0;SP2;PD40,0,40,40;PR40,0;PU;PA0,0;SP0;");
        assertNull(preview.warning());
        assertEquals(3, preview.cutCenterlines().getLength(), 1e-12);
        assertEquals(Math.sqrt(5), preview.travelCenterlines().getLength(), 1e-12);
        assertTrue(Double.isNaN(preview.stats().estimatedMinutes()));
        assertTrue(GCodeToolpathParser.isHpglProgram("CO \"comment; with semicolon\"; IN; PU;"));
        assertFalse(GCodeToolpathParser.isHpglProgram("; IN; not HPGL\nG21\n"));
    }

    @Test
    void widthDoesNotLeakToUnmarkedExternalPen() {
        var preview = parse("IN;CO \"FCFX PEN P1 D0.8\";PU0,0;SP1;PD40,0;PU;SP2;PU0,80;PD40,80;PU;SP0;");
        assertNull(preview.warning());
        assertEquals(2.01, preview.cutGeometry().getEnvelopeInternal().getMaxY(), 1e-12);
        assertNull(preview.stats().cutterDiameter());
    }

    @Test
    void nativeReaderRejectsUnmodeledOrMalformedCommandsRatherThanShowingFalsePreview() {
        for (String command : List.of("SC0,1,0,1;", "IP0,0,100,100;", "AA0,0,90;", "CI40;", "VS10;",
                "IN;", "PA1;", "PA1,,2;", "PA1,no;", "PA999999,0;", "PA32768,0;PR1,0;", "PA1,2",
                "SP-1;", "SP1.5;", "PD;SP2;", "SP0;PD40,0;", "CO noquotes;",
                "PU0,0;CO \"FCFX HPGL UNITS IN\";")) {
            assertThrows(IllegalArgumentException.class, () -> parse("IN;" + command), command);
        }
    }

    @Test
    void geometryVToolsArePenWidthsWithoutRequiringMillingSettings() {
        var job = generate("MM", List.of(new ToolGeometry(0.2, path(0, 0, 1, 0), ToolProfile.V)));
        assertEquals(1, parse(job.gcode()).cutCenterlines().getLength(), 1e-12);
    }

    @Test
    void millingOnlyEntrypointsAndUnsupportedPlotterModesAreRejected() {
        var image = new ExcellonParser().parse(List.of("M48", "METRIC", "T1C0.8", "%", "T1", "X1.0Y1.0", "M30"));
        var drill = new DrillGCodeParameters(2, 0.5, 100, 10000, false);
        assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateDrillCncJob(image,
                Map.of(1, drill), List.of(1), new GCodeGenerator.DrillJobOptions(false, 2, 2, null, null), GCodePreprocessor.HPGL));
        var tools = List.of(new ToolGeometry(0.2, path(0, 0, 1, 0)));
        for (var parameters : List.of(new GeometryGCodeParameters(2, 1, true, 0.1, 100, 0, false),
                new GeometryGCodeParameters(2, 1, false, 1, 100, 0, true))) {
            assertThrows(IllegalArgumentException.class, () -> GCodeGenerator.generateGeometryCncJob("MM", tools,
                    parameters, Map.of(), CancellationToken.none(), GCodePreprocessor.HPGL));
        }
        assertThrows(IllegalArgumentException.class, () -> generate("invalid", tools));
    }

    @Test
    void cancellationAndProgressRemainResponsiveAndMonotonic() {
        String code = "IN;PU0,0;PD40,0;PU;SP0;";
        assertThrows(CancellationException.class, () -> GCodeToolpathParser.parse(code, () -> true, ignored -> {}));
        List<Double> progress = new ArrayList<>();
        assertTrue(GCodeToolpathParser.parse(code, CancellationToken.none(), progress::add).plotAvailable());
        assertEquals(1, progress.getLast());
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1), progress.toString());
        assertThrows(CancellationException.class, () -> GCodeGenerator.generateGeometryCncJob("MM",
                List.of(new ToolGeometry(0.2, path(0, 0, 1, 0))),
                new GeometryGCodeParameters(2, 1, false, 1, 100, 0, false), Map.of(), () -> true, GCodePreprocessor.HPGL));
    }
}
