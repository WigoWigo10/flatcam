package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.excellon.ExcellonEditSession;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.ApertureKind;
import org.flatcam.cam.gerber.GerberParser;
import org.flatcam.cam.gerber.GerberShape;
import org.flatcam.cam.gerber.edit.GerberEditSession;
import org.flatcam.cam.geometry.GeometryEditSession;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.app.project.flatprj.GerberFlatPrjCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class ProjectFileIOTest {

    @Test void invalidPlainCompressedAndTruncatedProjectsFailAsIoErrors() throws Exception {
        assertThrows(IOException.class, () -> ProjectFileIO.parseRoot("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> ProjectFileIO.parseRoot(java.util.Arrays.copyOf(org.tukaani.xz.XZ.HEADER_MAGIC, 6)));
        var output = new java.io.ByteArrayOutputStream();
        try (var xz = new org.tukaani.xz.XZOutputStream(output, new org.tukaani.xz.LZMA2Options(1))) {
            xz.write("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertThrows(IOException.class, () -> ProjectFileIO.parseRoot(output.toByteArray()));
    }

    @TempDir
    Path tempDir;

    @Test
    void probeDefaultsAndMachineProgramRoundTripWithoutInventingContactGeometry() throws IOException {
        var probe = new org.flatcam.cam.gcode.ProbeToolChangeParameters(15, -5, 50, 0.5, 7.0, 11.0);
        var parameters = new org.flatcam.cam.gcode.GeometryGCodeParameters(3, 1, false, 1, 100, 9000, true, 0, probe);
        var path = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(1, 2), new Coordinate(4, 2)});
        var geometry = new ProjectFile.GeometryEntry("paths", "source", "MM", path, true, List.of(), null, null, true, parameters);
        var job = org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob("MM", List.of(new ToolGeometry(0.8, path)),
                parameters, java.util.Map.of(), org.flatcam.cam.CancellationToken.none(),
                org.flatcam.cam.gcode.GCodePreprocessor.TOOLCHANGE_PROBE_MACH3);
        var record = new ProjectFile.CncJobRecord("probe.nc", "source", tempDir.resolve("missing.nc").toString(), job.gcode());
        Path file = tempDir.resolve("probe.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(geometry), List.of(record)), file, false);
        var loaded = ProjectFileIO.load(file);
        assertEquals(parameters, loaded.geometries().get(0).cncDefaults());
        assertEquals(job.gcode(), loaded.cncJobs().get(0).gcode());
        var parsed = org.flatcam.cam.gcode.GCodeToolpathParser.parse(loaded.cncJobs().get(0).gcode(),
                org.flatcam.cam.CancellationToken.none(), ignored -> {});
        assertFalse(parsed.plotAvailable());
        assertTrue(parsed.warning().contains("G31/G92"));
        JSONObject old = new JSONObject(Files.readString(file));
        old.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0)
                .getJSONObject("cncDefaults").remove("probing");
        Files.writeString(file, old.toString());
        org.junit.jupiter.api.Assertions.assertNull(ProjectFileIO.load(file).geometries().get(0).cncDefaults().probing());
    }

    @Test
    void probeDefaultsWithNoXyRemainOptionalAfterSaving() throws IOException {
        var probe = new org.flatcam.cam.gcode.ProbeToolChangeParameters(15, -5, 50, 0, null, null);
        var parameters = new org.flatcam.cam.gcode.GeometryGCodeParameters(3, 1, false, 1, 100, 9000, true, 0, probe);
        var path = new GeometryFactory().createPoint(new Coordinate(1, 2));
        var entry = new ProjectFile.GeometryEntry("paths", "source", "MM", path, true, List.of(), null, null, true, parameters);
        Path file = tempDir.resolve("probe-no-xy.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(entry), List.of()), file, false);
        assertEquals(parameters, ProjectFileIO.load(file).geometries().get(0).cncDefaults());
    }

    @Test
    void nativeRolandJobReopensFromEmbeddedTextWithoutTheOutputFile() throws IOException {
        var geometry = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(3, 7), new Coordinate(6, 7)});
        var job = org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob("MM",
                List.of(new ToolGeometry(0.8, geometry)),
                new org.flatcam.cam.gcode.GeometryGCodeParameters(2, 1, false, 1, 120, 0, false),
                java.util.Map.of(), org.flatcam.cam.CancellationToken.none(), org.flatcam.cam.gcode.GCodePreprocessor.ROLAND_MDX_20);
        var record = new ProjectFile.CncJobRecord("paths.rml", "source", tempDir.resolve("missing.rml").toString(), job.gcode());
        Path file = tempDir.resolve("native-roland.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(record)), file);
        var loaded = ProjectFileIO.load(file).cncJobs().get(0);
        assertEquals(job.gcode(), loaded.gcode());
        assertFalse(Files.exists(Path.of(loaded.outputPath())));
        var parsed = org.flatcam.cam.gcode.GCodeToolpathParser.parse(loaded.gcode(),
                org.flatcam.cam.CancellationToken.none(), ignored -> {});
        assertEquals(3, parsed.cutCenterlines().getLength(), 1e-9);
        assertTrue(parsed.plotAvailable());
    }

    @Test
    void nativeHpglJobReopensFromEmbeddedTextWithoutTheOutputFile() throws IOException {
        var geometry = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(3, 7), new Coordinate(6, 7)});
        var job = org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob("MM",
                List.of(new ToolGeometry(0.8, geometry)),
                new org.flatcam.cam.gcode.GeometryGCodeParameters(2, 1, false, 1, 120, 0, false),
                java.util.Map.of(), org.flatcam.cam.CancellationToken.none(), org.flatcam.cam.gcode.GCodePreprocessor.HPGL);
        var record = new ProjectFile.CncJobRecord("paths.plt", "source", tempDir.resolve("missing.plt").toString(), job.gcode());
        Path file = tempDir.resolve("native-hpgl.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(record)), file);
        var loaded = ProjectFileIO.load(file).cncJobs().get(0);
        assertEquals(job.gcode(), loaded.gcode());
        assertFalse(Files.exists(Path.of(loaded.outputPath())));
        var parsed = org.flatcam.cam.gcode.GCodeToolpathParser.parse(loaded.gcode(),
                org.flatcam.cam.CancellationToken.none(), ignored -> {});
        assertEquals(3, parsed.cutCenterlines().getLength(), 1e-9);
        assertEquals(0.8, parsed.stats().cutterDiameter());
        assertTrue(parsed.plotAvailable());
    }

    @Test
    void nativeIcpJobReopensFromEmbeddedTextWithoutTheOutputFile() throws IOException {
        var geometry = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(3, 7), new Coordinate(6, 7)});
        var job = org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob("MM",
                List.of(new ToolGeometry(0.8, geometry)),
                new org.flatcam.cam.gcode.GeometryGCodeParameters(2, 1, false, 1, 120, 12000, false),
                java.util.Map.of(), org.flatcam.cam.CancellationToken.none(),
                org.flatcam.cam.gcode.GCodePreprocessor.ISEL_ICP_CNC);
        var record = new ProjectFile.CncJobRecord("paths.imf", "source", tempDir.resolve("missing.imf").toString(), job.gcode());
        Path file = tempDir.resolve("native-icp.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(record)), file);
        var loaded = ProjectFileIO.load(file).cncJobs().get(0);
        assertEquals(job.gcode(), loaded.gcode());
        assertFalse(Files.exists(Path.of(loaded.outputPath())));
        var parsed = org.flatcam.cam.gcode.GCodeToolpathParser.parse(loaded.gcode(),
                org.flatcam.cam.CancellationToken.none(), ignored -> {});
        assertEquals(3, parsed.cutCenterlines().getLength(), 1e-9);
        assertTrue(parsed.plotAvailable());
    }

    @Test
    void rapidFeedRoundTripsAndOldProjectsDefaultToAutomatic() throws IOException {
        var parameters = new org.flatcam.cam.gcode.GeometryGCodeParameters(2, 1, false, 1, 100, 128, false, 600);
        var geometry = new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(1, 0)});
        var entry = new ProjectFile.GeometryEntry("paths", "source", "MM", geometry, true, List.of(),
                null, null, true, parameters);
        Path file = tempDir.resolve("rapid.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(entry), List.of()), file, false);
        assertEquals(parameters, ProjectFileIO.load(file).geometries().get(0).cncDefaults());
        JSONObject old = new JSONObject(Files.readString(file));
        old.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0)
                .getJSONObject("cncDefaults").remove("rapidFeedRate");
        Files.writeString(file, old.toString());
        assertEquals(0, ProjectFileIO.load(file).geometries().get(0).cncDefaults().rapidFeedRate());
    }

    @Test
    void preservesHiddenCncJobWhenSavingNativeProject() throws IOException {
        ProjectFile.CncJobRecord hidden = new ProjectFile.CncJobRecord(
                "cut_cnc", "outline", "cut.nc", "G21\nG0 X0 Y0\nM30\n", false);
        Path file = tempDir.resolve("hidden-job.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(hidden)), file);
        assertFalse(ProjectFileIO.load(file).cncJobs().get(0).visible());
    }

    private static final GerberImage RECTANGLE_GERBER = new GerberParser().parse(List.of(
            "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

    private static final ExcellonImage DRILL_EXCELLON = new ExcellonParser().parse(List.of(
            "M48", "METRIC", "T1C1.0", "%", "T1", "X1.0Y1.0", "X3.0Y1.0G85X5.0Y1.0", "M30"));

    @Test
    void roundTripsExcellonEditorChanges() throws IOException {
        ExcellonEditSession edit = new ExcellonEditSession(DRILL_EXCELLON);
        edit.clickSelect(1, 1, 0, false);
        assertTrue(edit.deleteSelected());
        edit.addDrill(1, 8, 9);
        edit.addSlot(1, 10, 11, 12, 13);
        edit.clickSelect(8, 9, 0, false);
        int newTool = edit.resizeSelected(0.6);
        Path file = tempDir.resolve("edited-excellon.fcnproj");
        ProjectFile.ExcellonEntry entry = new ProjectFile.ExcellonEntry("edited.drl", edit.resultImage(),
                null, null, true, true, false);
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(entry), List.of()), file);
        ExcellonImage loaded = ProjectFileIO.load(file).excellons().get(0).image();

        assertEquals(1, loaded.totalDrills());
        assertEquals(2, loaded.totalSlots());
        assertEquals(8, loaded.drills().get(0).x(), 1e-9);
        assertEquals(9, loaded.drills().get(0).y(), 1e-9);
        assertEquals(newTool, loaded.drills().get(0).toolId());
        assertEquals(0.6, loaded.toolDiameters().get(newTool), 1e-9);
        assertEquals(12, loaded.slots().get(1).x2(), 1e-9);
        assertFalse(loaded.solidGeometry().isEmpty());
    }

    @Test
    void roundTripsEditedGeometryAndToolAssignments() throws IOException {
        GeometryFactory factory = new GeometryFactory();
        Geometry first = factory.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(0, 5)});
        Geometry second = factory.createLineString(new Coordinate[]{new Coordinate(10, 0), new Coordinate(10, 5)});
        List<ToolGeometry> tools = List.of(new ToolGeometry(0.8, first, ToolProfile.C2),
                new ToolGeometry(1.2, second, ToolProfile.B));
        GeometryEditSession edit = new GeometryEditSession(factory.buildGeometry(List.of(first, second)), tools);
        edit.clickSelect(0, 2, 0.01, false);
        assertTrue(edit.deleteSelected());
        ProjectFile.GeometryEntry geometry = new ProjectFile.GeometryEntry("paths_edit", "top.gbr", "MM",
                edit.resultGeometry(), true, edit.resultTools(), "0xff0000ff", "0x0000ffff", false);
        Path file = tempDir.resolve("geometry.fcnproj");

        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(geometry), List.of()), file, false);
        ProjectFile.GeometryEntry loaded = ProjectFileIO.load(file).geometries().get(0);

        assertEquals("paths_edit", loaded.name());
        assertEquals("top.gbr", loaded.sourceName());
        assertEquals("MM", loaded.units());
        assertTrue(loaded.strokeOnly());
        assertFalse(loaded.visible());
        assertEquals("0xff0000ff", loaded.fillColorWeb());
        assertTrue(loaded.geometry().equalsTopo(second));
        assertEquals(2, loaded.tools().size());
        assertEquals(0.8, loaded.tools().get(0).toolDiameter());
        assertEquals(ToolProfile.C2, loaded.tools().get(0).toolProfile());
        assertTrue(loaded.tools().get(0).geometry().isEmpty());
        assertEquals(ToolProfile.B, loaded.tools().get(1).toolProfile());
        assertTrue(loaded.tools().get(1).geometry().equalsTopo(second));

        JSONObject olderProject = new JSONObject(Files.readString(file));
        olderProject.getJSONObject("_java").getJSONArray("geometries").getJSONObject(0)
                .getJSONArray("tools").getJSONObject(0).remove("toolType");
        Files.writeString(file, olderProject.toString());
        assertEquals(ToolProfile.C1, ProjectFileIO.load(file).geometries().get(0)
                .tools().get(0).toolProfile());
    }

    @Test
    void roundTripsEmbeddedGerberAndExcellonGeometryAndDisplayState() throws IOException {
        ProjectFile.GerberEntry gerber = new ProjectFile.GerberEntry(
                "top.gbr", RECTANGLE_GERBER, "0xbbf268ff", "0x006e20ff", false, true, true, false);
        ProjectFile.ExcellonEntry excellon = new ProjectFile.ExcellonEntry(
                "pth.drl", DRILL_EXCELLON, "0xc40000ff", "0x750000ff", true, false, true);
        ProjectFile.CncJobRecord job = new ProjectFile.CncJobRecord("pth.drl", "C:/board/pth_drill.nc");
        ProjectFile original = new ProjectFile(List.of(gerber), List.of(excellon), List.of(job));

        Path file = tempDir.resolve("project.fcnproj");
        ProjectFileIO.save(original, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(1, loaded.gerbers().size());
        ProjectFile.GerberEntry loadedGerber = loaded.gerbers().get(0);
        assertEquals("top.gbr", loadedGerber.name());
        assertEquals("0xbbf268ff", loadedGerber.fillColorWeb());
        assertEquals("0x006e20ff", loadedGerber.strokeColorWeb());
        assertFalse(loadedGerber.visible());
        assertTrue(loadedGerber.filled());
        assertTrue(loadedGerber.multicolor());
        assertFalse(loadedGerber.followMode());
        assertEquals(RECTANGLE_GERBER.units(), loadedGerber.image().units());
        assertTrue(RECTANGLE_GERBER.solidGeometry().equalsExact(loadedGerber.image().solidGeometry(), 1e-9),
                "Gerber solid_geometry must survive the WKT round-trip exactly");
        assertEquals(RECTANGLE_GERBER.apertures().keySet(), loadedGerber.image().apertures().keySet());

        assertEquals(1, loaded.excellons().size());
        ProjectFile.ExcellonEntry loadedExcellon = loaded.excellons().get(0);
        assertEquals("pth.drl", loadedExcellon.name());
        assertTrue(loadedExcellon.visible());
        assertFalse(loadedExcellon.filled());
        assertTrue(loadedExcellon.multicolor());
        assertEquals(DRILL_EXCELLON.units(), loadedExcellon.image().units());
        assertEquals(DRILL_EXCELLON.totalDrills(), loadedExcellon.image().totalDrills());
        assertEquals(DRILL_EXCELLON.totalSlots(), loadedExcellon.image().totalSlots());
        assertEquals(DRILL_EXCELLON.drills().get(0).x(), loadedExcellon.image().drills().get(0).x(), 1e-9);
        assertEquals(DRILL_EXCELLON.drills().get(0).y(), loadedExcellon.image().drills().get(0).y(), 1e-9);

        assertEquals(List.of(job), loaded.cncJobs());
    }

    @Test
    void roundTripsEditedGCodeWithoutDependingOnOutputFile() throws IOException {
        String edited = "G21\nG90\nG0 X1 Y1\nG1 Z-0.2\nG1 X3 Y1\n";
        ProjectFile.CncJobRecord job = new ProjectFile.CncJobRecord(
                "edited-job", "board.gbr", tempDir.resolve("missing-output.nc").toString(), edited);
        Path file = tempDir.resolve("edited-cnc.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(), List.of(), List.of(job)), file);
        assertEquals(List.of(job), ProjectFileIO.load(file).cncJobs());
    }

    @Test
    void roundTripsIndividualGerberShapesAndAnEditedResult() throws IOException {
        GerberImage parsed = new GerberParser().parse(List.of(
                "%FSLAX23Y23*%", "%MOMM*%", "%ADD10C,1*%", "D10*",
                "X1000Y1000D03*", "X2000Y1000D03*",
                "G36*", "X4000Y4000D02*", "X5000Y4000D01*",
                "X5000Y5000D01*", "X4000Y5000D01*", "X4000Y4000D01*", "G37*",
                "%LPC*%", "X2000Y1000D03*", "M02*"));
        Path file = tempDir.resolve("editable.fcnproj");
        ProjectFile project = new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board", parsed, null, null, true, true, false, false)), List.of(), List.of());

        ProjectFileIO.save(project, file, false);
        JSONObject saved = new JSONObject(Files.readString(file));
        JSONObject gerber = saved.getJSONArray("objs").getJSONObject(0);
        assertEquals(3, gerber.getJSONObject("apertures").getJSONObject("10").getJSONArray("geometry").length());
        assertEquals(1, gerber.getJSONObject("apertures").getJSONObject("0").getJSONArray("geometry").length());
        assertEquals(4, gerber.getJSONObject("_java").getJSONArray("shape_order").length());

        GerberImage loaded = ProjectFileIO.load(file).gerbers().get(0).image();
        assertEquals(4, loaded.shapes().size());
        assertEquals(List.of("10", "10", GerberShape.REGION_APERTURE, "10"),
                loaded.shapes().stream().map(GerberShape::apertureCode).toList());
        assertTrue(loaded.shapes().get(3).clear());
        assertTrue(loaded.shapes().stream().allMatch(shape -> shape.followGeometry() != null));

        GerberEditSession editor = new GerberEditSession("board", loaded);
        assertFalse(editor.shapesApproximated());
        editor.clickSelect(1, 1, false);
        assertTrue(editor.deleteSelected());
        GerberImage edited = editor.apply().image();
        ProjectFileIO.save(new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board_edit", edited, null, null, true, true, false, false)), List.of(), List.of()), file);
        GerberImage reopened = ProjectFileIO.load(file).gerbers().get(0).image();
        assertEquals(3, reopened.shapes().size());
        assertTrue(edited.solidGeometry().equalsExact(reopened.solidGeometry(), 1e-9));
        assertFalse(new GerberEditSession("board_edit", reopened).shapesApproximated());
    }

    @Test
    void roundTripsAFlashedCircularPadFromTheEditor() throws IOException {
        GerberImage source = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1*%", "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession editor = new GerberEditSession("board", source);
        String newCode = editor.addCircularAperture(0.5);
        assertTrue(editor.addCircularPad(newCode, 4, 5));
        GerberImage edited = editor.apply().image();
        Path file = tempDir.resolve("pad-edit.fcnproj");

        ProjectFileIO.save(new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board_edit", edited, null, null, true, true, false, false)), List.of(), List.of()), file);
        GerberImage reopened = ProjectFileIO.load(file).gerbers().get(0).image();

        assertEquals(2, reopened.shapes().size());
        assertEquals(newCode, reopened.shapes().get(1).apertureCode());
        assertEquals(0.5, reopened.apertures().get(newCode).width, 1e-9);
        assertEquals(4, reopened.shapes().get(1).followGeometry().getCoordinate().x, 1e-9);
        assertEquals(5, reopened.shapes().get(1).followGeometry().getCoordinate().y, 1e-9);
        assertTrue(edited.solidGeometry().equalsExact(reopened.solidGeometry(), 1e-9));
        assertFalse(new GerberEditSession("board_edit", reopened).shapesApproximated());
    }

    @Test
    void roundTripsRectangularAndObroundPadsFromTheEditor() throws IOException {
        GerberImage source = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1*%", "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession editor = new GerberEditSession("board", source);
        String rectangle = editor.addAperture(ApertureKind.RECTANGLE, 2, 1);
        String obround = editor.addAperture(ApertureKind.OBROUND, 1, 3);
        assertTrue(editor.addPad(rectangle, 4, 5));
        assertTrue(editor.addPad(obround, 8, 9));
        GerberImage edited = editor.apply().image();
        Path file = tempDir.resolve("shaped-pad-edit.fcnproj");

        ProjectFileIO.save(new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board_edit", edited, null, null, true, true, false, false)), List.of(), List.of()), file);
        GerberImage reopened = ProjectFileIO.load(file).gerbers().get(0).image();

        assertEquals(3, reopened.shapes().size());
        assertEquals(ApertureKind.RECTANGLE, reopened.apertures().get(rectangle).kind);
        assertEquals(ApertureKind.OBROUND, reopened.apertures().get(obround).kind);
        assertEquals(2, reopened.apertures().get(rectangle).width, 1e-9);
        assertEquals(3, reopened.apertures().get(obround).height, 1e-9);
        assertEquals(rectangle, reopened.shapes().get(1).apertureCode());
        assertEquals(obround, reopened.shapes().get(2).apertureCode());
        assertEquals(4, reopened.shapes().get(1).followGeometry().getCoordinate().x, 1e-9);
        assertEquals(9, reopened.shapes().get(2).followGeometry().getCoordinate().y, 1e-9);
        assertTrue(edited.solidGeometry().equalsExact(reopened.solidGeometry(), 1e-9));
        assertFalse(new GerberEditSession("board_edit", reopened).shapesApproximated());
    }

    @Test
    void roundTripsAnEditedPolylineTrackAndItsCenterline() throws IOException {
        GerberImage source = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1*%", "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession editor = new GerberEditSession("board", source);
        assertTrue(editor.addTrack("10", List.of(
                new org.locationtech.jts.geom.Coordinate(4, 5),
                new org.locationtech.jts.geom.Coordinate(7, 5),
                new org.locationtech.jts.geom.Coordinate(7, 8),
                new org.locationtech.jts.geom.Coordinate(9, 8))));
        GerberImage edited = editor.apply().image();
        Path file = tempDir.resolve("track-edit.fcnproj");

        ProjectFileIO.save(new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board_edit", edited, null, null, true, true, false, false)), List.of(), List.of()), file);
        GerberImage reopened = ProjectFileIO.load(file).gerbers().get(0).image();

        assertEquals(2, reopened.shapes().size());
        GerberShape track = reopened.shapes().get(1);
        assertEquals("10", track.apertureCode());
        assertEquals(4, track.followGeometry().getNumPoints());
        assertEquals(4, track.followGeometry().getCoordinates()[0].x, 1e-9);
        assertEquals(7, track.followGeometry().getCoordinates()[1].x, 1e-9);
        assertEquals(8, track.followGeometry().getCoordinates()[2].y, 1e-9);
        assertEquals(9, track.followGeometry().getCoordinates()[3].x, 1e-9);
        assertTrue(edited.solidGeometry().equalsExact(reopened.solidGeometry(), 1e-9));
        assertFalse(new GerberEditSession("board_edit", reopened).shapesApproximated());
    }

    @Test
    void roundTripsAnEditedRegionAndPolygonAperture() throws IOException {
        GerberImage source = new GerberParser().parse(List.of(
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10C,1*%", "D10*", "X0Y0D03*", "M02*"));
        GerberEditSession editor = new GerberEditSession("board", source);
        String code = editor.addPolygonAperture(2, 6, 15);
        assertTrue(editor.addPad(code, 20, 20));
        assertTrue(editor.addRegion(List.of(
                new org.locationtech.jts.geom.Coordinate(30, 30),
                new org.locationtech.jts.geom.Coordinate(34, 30),
                new org.locationtech.jts.geom.Coordinate(34, 32),
                new org.locationtech.jts.geom.Coordinate(30, 32))));
        GerberImage edited = editor.apply().image();
        Path file = tempDir.resolve("region-polygon-edit.fcnproj");
        ProjectFileIO.save(new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "board_edit", edited, null, null, true, true, false, false)), List.of(), List.of()), file);
        GerberImage reopened = ProjectFileIO.load(file).gerbers().get(0).image();

        assertEquals(3, reopened.shapes().size());
        assertEquals(ApertureKind.POLYGON, reopened.apertures().get(code).kind);
        assertEquals(6, reopened.apertures().get(code).polygonVertices());
        assertEquals(GerberShape.REGION_APERTURE, reopened.shapes().get(2).apertureCode());
        assertEquals(8, reopened.shapes().get(2).geometry().getArea(), 1e-9);
        assertTrue(reopened.solidGeometry().covers(
                new org.locationtech.jts.geom.GeometryFactory().createPoint(
                        new org.locationtech.jts.geom.Coordinate(32, 31))));
        assertFalse(new GerberEditSession("board_edit", reopened).shapesApproximated());
    }

    @Test
    void usesPythonApertureNamesAndReadsPreviousJavaNames() {
        GerberImage parsed = new GerberParser().parse(List.of(
                "%FSLAX23Y23*%", "%MOMM*%", "%ADD10C,1*%", "%ADD11R,2X1*%",
                "%ADD12O,2X1*%", "%ADD13P,2X6X15*%",
                "D10*", "X1000Y1000D03*", "D11*", "X3000Y1000D03*",
                "D12*", "X6000Y1000D03*", "D13*", "X9000Y1000D03*", "M02*"));
        JSONObject saved = GerberFlatPrjCodec.toJson("board", parsed, null, null,
                true, true, false, false);
        JSONObject apertures = saved.getJSONObject("apertures");
        assertEquals("C", apertures.getJSONObject("10").getString("type"));
        assertEquals(1.0, apertures.getJSONObject("10").getDouble("size"));
        assertEquals("R", apertures.getJSONObject("11").getString("type"));
        assertEquals(2.0, apertures.getJSONObject("11").getDouble("width"));
        assertEquals("O", apertures.getJSONObject("12").getString("type"));
        assertEquals("P", apertures.getJSONObject("13").getString("type"));
        assertEquals(2.0, apertures.getJSONObject("13").getDouble("diam"));
        assertEquals(6, apertures.getJSONObject("13").getInt("nVertices"));
        assertEquals(15.0, apertures.getJSONObject("13").getDouble("rotation"));

        GerberImage loaded = GerberFlatPrjCodec.fromJson(saved).image();
        assertEquals(parsed.apertures().get("13").polygonVertices(),
                loaded.apertures().get("13").polygonVertices());
        assertEquals(parsed.apertures().get("13").polygonRotation(),
                loaded.apertures().get("13").polygonRotation());
        assertEquals(parsed.shapes().size(), loaded.shapes().size());

        // Projects saved by earlier Java versions used enum names and different
        // polygon keys. Keep them readable after switching to Python's schema.
        apertures.getJSONObject("10").put("type", "CIRCLE").put("width", 1);
        apertures.getJSONObject("11").put("type", "RECTANGLE");
        apertures.getJSONObject("12").put("type", "OBROUND");
        apertures.getJSONObject("13").put("type", "POLYGON").put("width", 2)
                .put("polygon_vertices", 6).put("polygon_rotation", 15)
                .remove("nVertices");
        apertures.getJSONObject("13").remove("rotation");
        apertures.getJSONObject("13").remove("diam");
        GerberImage legacy = GerberFlatPrjCodec.fromJson(saved).image();
        assertEquals(6, legacy.apertures().get("13").polygonVertices());
        assertEquals(15.0, legacy.apertures().get("13").polygonRotation());
    }

    @Test
    void roundTripsEmptyProject() throws IOException {
        ProjectFile empty = new ProjectFile(List.of(), List.of(), List.of());
        Path file = tempDir.resolve("empty.fcnproj");
        ProjectFileIO.save(empty, file);
        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(List.of(), loaded.gerbers());
        assertEquals(List.of(), loaded.excellons());
        assertEquals(List.of(), loaded.cncJobs());
    }

    @Test
    void savedFileIsActuallyXzCompressedByDefault() throws IOException {
        ProjectFile project = new ProjectFile(
                List.of(new ProjectFile.GerberEntry("g.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)),
                List.of(), List.of());
        Path file = tempDir.resolve("compressed.fcnproj");
        ProjectFileIO.save(project, file);

        byte[] raw = Files.readAllBytes(file);
        assertThrows(RuntimeException.class, () -> new JSONObject(new String(raw)),
                "a compressed file's raw bytes should not parse as plain JSON");

        ProjectFile loaded = ProjectFileIO.load(file);
        assertEquals(1, loaded.gerbers().size());
    }

    @Test
    void uncompressedSaveStillLoadsViaAutoDetection() throws IOException {
        ProjectFile project = new ProjectFile(
                List.of(new ProjectFile.GerberEntry("g.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)),
                List.of(), List.of());
        Path file = tempDir.resolve("plain.fcnproj");
        ProjectFileIO.save(project, file, false);

        // A plain save really is plain JSON text - readable without decompression.
        JSONObject plain = new JSONObject(Files.readString(file));
        assertEquals(2, plain.getInt("version"));

        ProjectFile loaded = ProjectFileIO.load(file);
        assertEquals(1, loaded.gerbers().size());
    }

    @Test
    void replacingAnExistingProjectPublishesACompleteFile() throws IOException {
        Path file = tempDir.resolve("existing.fcnproj");
        Files.writeString(file, "previous contents");
        ProjectFile project = new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "g.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)), List.of(), List.of());

        ProjectFileIO.save(project, file);

        assertEquals("g.gbr", ProjectFileIO.load(file).gerbers().get(0).name());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(file), files.toList(), "temporary file must not remain after publication");
        }
    }

    @Test
    void loadsPreviousV2AggregateGerberWithoutInventingIndividualShapes() throws IOException {
        ProjectFile project = new ProjectFile(List.of(new ProjectFile.GerberEntry(
                "old.gbr", RECTANGLE_GERBER, null, null, true, true, false, false)), List.of(), List.of());
        Path file = tempDir.resolve("previous-v2.fcnproj");
        ProjectFileIO.save(project, file, false);
        JSONObject old = new JSONObject(Files.readString(file));
        old.getJSONArray("objs").getJSONObject(0).getJSONObject("_java").remove("shape_order");
        Files.writeString(file, old.toString());

        GerberImage loaded = ProjectFileIO.load(file).gerbers().get(0).image();
        assertTrue(loaded.shapes().isEmpty());
        assertTrue(RECTANGLE_GERBER.solidGeometry().equalsTopo(loaded.solidGeometry()));
        assertTrue(new GerberEditSession("old.gbr", loaded).shapesApproximated());
    }

    @Test
    void loadsLegacyV1ProjectsByReparsingTheirSourceFiles() throws IOException {
        Path gerberFile = tempDir.resolve("legacy.gbr");
        Files.writeString(gerberFile, String.join("\n",
                "%FSLAX24Y24*%", "%MOMM*%", "%ADD10R,2X1*%", "D10*", "X0Y0D03*", "M02*"));

        JSONObject legacy = new JSONObject();
        legacy.put("version", 1);
        legacy.put("gerbers", new org.json.JSONArray(List.of(gerberFile.toString().replace('\\', '/'))));
        legacy.put("excellons", new org.json.JSONArray());
        legacy.put("cncJobs", new org.json.JSONArray());
        Path file = tempDir.resolve("legacy.fcnproj");
        Files.writeString(file, legacy.toString());

        ProjectFile loaded = ProjectFileIO.load(file);

        assertEquals(1, loaded.gerbers().size());
        assertTrue(loaded.gerbers().get(0).image().solidGeometry().getArea() > 0);
        assertEquals(0, loaded.excellons().size());
    }

    @Test
    void rejectsUnknownVersion() throws IOException {
        Path file = tempDir.resolve("future.fcnproj");
        Files.writeString(file, "{\"version\": 99, \"objs\": [], \"options\": {}}");
        assertThrows(IOException.class, () -> ProjectFileIO.load(file));
    }
}
