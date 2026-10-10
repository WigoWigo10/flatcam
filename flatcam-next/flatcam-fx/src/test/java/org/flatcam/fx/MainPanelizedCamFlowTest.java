package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.scene.control.TreeItem;
import org.flatcam.app.job.JobHandle;
import org.flatcam.app.project.*;
import org.flatcam.cam.convert.OutlineToArea;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolProfile;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.isolation.*;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.panel.Panelize;
import org.flatcam.cam.transform.TransformOp;
import org.json.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** Panelize -> actual MainWindow CAM jobs -> CNC/export -> native reopen.
 * Private fixture is opt-in, read-only; generated data must stay under target.
 * No Stage, machine, FileChooser or user preferences are involved. */
@EnabledOnOs(OS.WINDOWS)
class MainPanelizedCamFlowTest {
    @TempDir Path temporary;
    private static final GeometryFactory F = new GeometryFactory();
    private static final WKTWriter WKT = new WKTWriter();

    private record Input(String units, List<ProjectFile.GerberEntry> copper,
                         ProjectFile.GerberEntry outline, Geometry area,
                         List<ProjectFile.ExcellonEntry> drills) { }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = MainWindow.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object call(MainWindow window, String name, Class<?>[] types, Object... args) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(window, args);
    }
    private static ProjectFile.GerberEntry gerber(String name, String units, Geometry solid, Geometry follow) {
        return new ProjectFile.GerberEntry(name, GerberImage.of(units, Map.of(), solid, follow, Map.of()),
                null, null, true, true, false, false);
    }
    private static Input synthetic(String units) {
        double u = units.equals("IN") ? 1 / 25.4 : 1;
        Geometry area = F.toGeometry(new Envelope(10, 30, 20, 36))
                .difference(F.toGeometry(new Envelope(24, 27, 29, 32)));
        Geometry copper = area.buffer(-1).difference(F.createPoint(new Coordinate(15, 25)).buffer(.6));
        var scale = new TransformOp.Scale(u, u, new Coordinate(0, 0));
        area = scale.apply(area); copper = scale.apply(copper);
        Geometry outline = area.getBoundary();
        var holes = ExcellonImage.of(units, Map.of(7, .8 * u),
                List.of(new ExcellonImage.Drill(7, 15 * u, 25 * u)),
                List.of(new ExcellonImage.Slot(7, 17 * u, 25 * u, 18 * u, 25 * u)),
                F.createPoint(new Coordinate(15 * u, 25 * u)).buffer(.4 * u));
        return new Input(units, List.of(gerber("F_Cu", units, copper, copper.getBoundary()),
                gerber("B_Cu", units, copper, copper.getBoundary())),
                gerber("Edge_Cuts", units, outline.buffer(.05 * u), outline), area,
                List.of(new ProjectFile.ExcellonEntry("PTH", holes, null, null, true, true, false)));
    }
    private static Input real(Path path) throws Exception {
        var project = PythonProjectIO.load(path);
        var copper = project.gerbers().stream().filter(g -> {
            String n = g.name().toLowerCase(Locale.ROOT); return n.contains("f_cu") || n.contains("b_cu");
        }).toList();
        assertEquals(2, copper.size(), "Fixture must have both F_Cu and B_Cu");
        var edge = project.gerbers().stream().filter(g -> g.name().toLowerCase(Locale.ROOT).contains("edge_cuts"))
                .findFirst().orElseThrow();
        assertNotNull(edge.image().followGeometry(), "Use the actual Edge_Cuts center lines, not copper or a bounding box");
        Geometry area = OutlineToArea.convert(edge.image().followGeometry()).area();
        return new Input(edge.image().units(), copper, edge, area, project.excellons());
    }

    private record Grid(int columns, int rows, double spacingXmm, double spacingYmm) { }

    @ParameterizedTest @CsvSource({"MM,2,2,5,5", "IN,2,2,5,5", "MM,3,1,0,0",
            "IN,3,1,0,0", "MM,1,3,2,7", "IN,1,3,2,7"})
    void panelizedMainFlowsKeepAllCopiesAndRoundTrip(String units, int columns, int rows,
                                                    double spacingX, double spacingY) throws Exception {
        var grid = new Grid(columns, rows, spacingX, spacingY);
        String publicOutput = System.getProperty("flatcam.panelized.public.output");
        Path directory = publicOutput == null || publicOutput.isBlank() ? temporary.resolve(units)
                : safeOutput(Path.of(publicOutput).resolve(units + "-" + columns + "x" + rows));
        run(synthetic(units), directory, null, grid);
    }
    @Test void realProjectPanelizedMainFlows() throws Exception {
        String fixture = System.getProperty("flatcam.python.project.fixture");
        Assumptions.assumeTrue(fixture != null && !fixture.isBlank(), "Private fixture is explicitly opt-in");
        Path source = Path.of(fixture).toRealPath();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        var grid = new Grid(Integer.getInteger("flatcam.panelized.columns", 2),
                Integer.getInteger("flatcam.panelized.rows", 2),
                Double.parseDouble(System.getProperty("flatcam.panelized.spacingXmm", "5")),
                Double.parseDouble(System.getProperty("flatcam.panelized.spacingYmm", "5")));
        try { run(real(source), output(), source, grid); }
        finally { assertArrayEquals(hash, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)),
                "The original project must never change, even if CAM fails"); }
    }
    private Path output() throws Exception {
        String output = System.getProperty("flatcam.panelized.output");
        if (output == null || output.isBlank()) return temporary.resolve("real");
        return safeOutput(Path.of(output));
    }
    private Path safeOutput(Path requested) throws Exception {
        Path reactor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (reactor != null && !Files.isRegularFile(reactor.resolve("CONTEXTO_E_PROGRESSO.md"))) reactor = reactor.getParent();
        assertNotNull(reactor, "Cannot identify the FlatCAM FX reactor");
        Path root = reactor.resolve("target").toRealPath();
        Path path = requested.toAbsolutePath().normalize();
        assertTrue(path.startsWith(root) && !path.equals(root), "Private output must be a new subdirectory of reactor target");
        assertFalse(Files.exists(path), "Do not overwrite existing reports");
        Files.createDirectories(path);
        assertTrue(path.toRealPath().startsWith(root), "Reject symlink escapes");
        return path;
    }
    private static void await(MainCamFlowTest.Session s, JobHandle<?> job) throws Exception {
        assertNotNull(job, "The real host must submit the job");
        job.completion().get(180, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (TerminalPanelTest.fx(() -> field("runningJob").get(s.window) == null)) return;
            Thread.sleep(20);
        }
        fail("Job finished but the FX publication did not complete");
    }
    private static JobHandle<?> start(MainCamFlowTest.Session s, String method, Class<?>[] types, Object... args) throws Exception {
        return TerminalPanelTest.fx(() -> {
            call(s.window, method, types, args); return (JobHandle<?>) field("runningJob").get(s.window);
        });
    }
    private static TreeItem<String> addGerber(MainCamFlowTest.Session s, ProjectFile.GerberEntry entry) throws Exception {
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addGerberToProject",
                new Class<?>[]{String.class, Path.class, GerberImage.class}, entry.name(), null, entry.image()));
    }
    private static TreeItem<String> addArea(MainCamFlowTest.Session s, String units, Geometry area) throws Exception {
        return TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window, "addGeometryToProject",
                new Class<?>[]{String.class, String.class, String.class, Geometry.class, boolean.class},
                "board_area", "Edge_Cuts", units, area, false));
    }
    private static TreeItem<String> find(MainCamFlowTest.Session s, String mapName, String name) throws Exception {
        return TerminalPanelTest.fx(() -> ((Map<TreeItem<String>, ?>) field(mapName).get(s.window)).keySet().stream()
                .filter(i -> i.getValue().equals(name)).findFirst().orElseThrow());
    }
    private static ProjectFile.GeometryEntry newGeometry(MainCamFlowTest.Session s, Set<String> before) throws Exception {
        var added = MainIsolationMachiningTest.snapshot(s.window).geometries().stream()
                .filter(g -> !before.contains(g.name())).toList();
        assertEquals(1, added.size(), "Expected exactly one published CAM Geometry");
        return added.getFirst();
    }
    private static Set<String> geometryNames(MainCamFlowTest.Session s) throws Exception {
        return new HashSet<>(MainIsolationMachiningTest.snapshot(s.window).geometries().stream().map(ProjectFile.GeometryEntry::name).toList());
    }

    private void run(Input input, Path output, Path source, Grid grid) throws Exception {
        Files.createDirectories(output);
        double u = input.units().equals("IN") ? 1 / 25.4 : 1;
        var layout = Panelize.layout(input.outline().image().bounds(), grid.columns(), grid.rows(),
                grid.spacingXmm() * u, grid.spacingYmm() * u, Double.NaN, Double.NaN);
        int copies = layout.offsets().size();
        var summary = new JSONObject().put("scope", "MainWindow jobs without Stage; no physical machining or FPS measurement")
                .put("units", input.units()).put("columns", grid.columns()).put("rows", grid.rows())
                .put("spacingX", grid.spacingXmm() * u).put("spacingY", grid.spacingYmm() * u);
        var results = new JSONArray(); summary.put("operations", results);
        int internalRings = 0;
        for (int i=0; i<input.area().getNumGeometries(); i++)
            if (input.area().getGeometryN(i) instanceof Polygon polygon) internalRings += polygon.getNumInteriorRing();
        summary.put("sourceInternalRings", internalRings).put("sourceBoardArea", input.area().getArea());
        try (var s = new MainCamFlowTest.Session(input.units())) {
            s.release.countDown();
            TerminalPanelTest.fx(() -> {
                call(s.window, "removeFromProject", new Class<?>[]{TreeItem.class, Map.class}, s.item, field("gerberByItem").get(s.window));
                call(s.window, "removeFromProject", new Class<?>[]{TreeItem.class, Map.class}, s.reference, s.geometries());
                return null;
            });
            List<TreeItem<String>> selected = new ArrayList<>();
            for (var entry : input.copper()) selected.add(addGerber(s, entry));
            var edge = addGerber(s, input.outline()); selected.add(edge);
            var area = addArea(s, input.units(), input.area()); selected.add(area);
            for (var drills : input.drills()) selected.add(TerminalPanelTest.fx(() -> (TreeItem<String>) call(s.window,
                    "addExcellonToProject", new Class<?>[]{String.class, Path.class, ExcellonImage.class}, drills.name(), null, drills.image())));
            long began = System.nanoTime();
            await(s, start(s, "runPanelize", new Class<?>[]{PanelizeToolPanel.Request.class},
                    new PanelizeToolPanel.Request(selected, edge, input.outline().image().bounds(), layout, false)));
            summary.put("panelizeMs", elapsed(began));
            var panel = MainIsolationMachiningTest.snapshot(s.window);
            var board = panel.geometries().stream().filter(g -> g.name().equals("board_area_panelized")).findFirst().orElseThrow();
            assertEquals(input.area().getArea() * copies, board.geometry().getArea(), Math.max(1, input.area().getArea()) * 1e-9);
            var panelEdge = panel.gerbers().stream().filter(g -> g.name().equals(input.outline().name() + "_panelized")).findFirst().orElseThrow();
            assertEquals(input.outline().image().followGeometry().getLength() * copies,
                    panelEdge.image().followGeometry().getLength(), 1e-7 * u);
            var expectedEdge = F.buildGeometry(layout.offsets().stream().map(o -> new TransformOp.Offset(o[0],o[1])
                    .apply(input.outline().image().followGeometry())).toList());
            assertTrue(org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance.distance(expectedEdge,
                    panelEdge.image().followGeometry()) < 1e-7 * u, "All internal/external outline positions must survive panelization");
            for (var original : input.drills()) verifyDrills(original.image(), panel.excellons().stream()
                    .filter(e -> e.name().equals(original.name() + "_panelized")).findFirst().orElseThrow().image(), layout, true);
            summary.put("drillRegistrationChecked", true).put("outlineCopiesChecked", true);
            var boundaryItem = find(s, "geometryByItem", board.name());
            for (var original : input.copper()) {
                var copper = panel.gerbers().stream().filter(g -> g.name().equals(original.name() + "_panelized")).findFirst().orElseThrow();
                assertEquals(original.image().solidGeometry().getArea() * copies, copper.image().solidGeometry().getArea(),
                        Math.max(1, original.image().solidGeometry().getArea()) * 1e-8);
                var item = find(s, "gerberByItem", copper.name());
                var before = geometryNames(s); began = System.nanoTime();
                var isolation = new IsolationToolPanel.Result(new IsolationToolPanel.SourceCandidate(item, copper.image()),
                        List.of(new IsolationParameters(.1 * u, 3, .15, IsolationType.BOTH)), Map.of(.1 * u, ToolProfile.C1),
                        false, true, true, false, false, null, null);
                await(s, start(s, "runIsolationGeneration", new Class<?>[]{TreeItem.class, GerberImage.class, IsolationToolPanel.Result.class},
                        item, copper.image(), isolation));
                var isoEntry = newGeometry(s, before);
                var isoCase = export(s, output, isoEntry, "isolation-" + (original.name().toLowerCase(Locale.ROOT).contains("f_cu") ? "f-cu" : "b-cu"),
                        "isolation", copper.image().solidGeometry(), .1 * u, new JSONObject().put("passes", 3).put("overlap", .15), elapsed(began), results);
                for (double[] offset : layout.offsets()) assertFalse(intersection(isoEntry.geometry(),new TransformOp.Offset(offset[0], offset[1])
                        .apply(original.image().solidGeometry()).getEnvelope().buffer(u)).isEmpty(), "Isolation must reach every board");
                JSONArray cases = new JSONArray().put(isoCase);
                for (boolean reference : List.of(true, false)) for (boolean rest : List.of(false, true))
                    for (boolean connect : List.of(false, true)) {
                        cases.put(nccCase(s, input, output, board, boundaryItem, copper, item,
                                layout, reference, rest, connect, results));
                    }
                var metadata = new JSONObject().put("columns", grid.columns()).put("rows", grid.rows())
                        .put("spacingX", grid.spacingXmm() * u).put("spacingY", grid.spacingYmm() * u)
                        .put("referenceName", input.outline().name());
                var comparison = new JSONObject().put("schema", 1).put("sourceName", original.name()).put("units", input.units())
                        .put("sourceWkt", WKT.write(copper.image().solidGeometry())).put("cases", cases);
                if (source != null) comparison.put("panelization", metadata);
                else comparison.put("comparisonScope", "Public synthetic translated copper shared explicitly; independent Python CAM/G-code, not independent source panelization");
                Files.writeString(output.resolve(original.name().toLowerCase(Locale.ROOT).contains("f_cu") ? "fx-f-cu.json" : "fx-b-cu.json"), comparison.toString(2));
            }
            var before = geometryNames(s); began = System.nanoTime();
            var machining = new GeometryGCodeParameters(3 * u, 1.7 * u, true, .5 * u, 120 * u, 0, false);
            var cutout = new CutoutToolPanel.Result(new CutoutParameters(.8 * u, 0, false, CutoutKind.PANEL,
                    CutoutShape.FREEFORM, 2 * u, GapPattern.FOUR), CutoutToolPanel.GapType.BRIDGE, .5 * u, .2 * u,
                    List.of(), machining, machining, ToolProfile.C1);
            await(s, start(s, "runCutoutGeneration", new Class<?>[]{TreeItem.class, String.class, Geometry.class, BooleanSupplier.class, CutoutToolPanel.Result.class},
                    boundaryItem, input.units(), board.geometry(), (BooleanSupplier) () -> true, cutout));
            var cutEntry = newGeometry(s, before);
            var single = CutoutGenerator.generate(input.units(), input.area(), cutout.cutoutParams()).geometry();
            Geometry expected = F.buildGeometry(layout.offsets().stream()
                    .map(o -> new TransformOp.Offset(o[0], o[1]).apply(single)).toList());
            assertEquals(expected.getLength(), cutEntry.geometry().getLength(), 1e-7 * u, "Cutout must repeat per board, not wrap the entire panel");
            // Translating a buffered ring vs buffering its translation differs by ULPs.
            // Compare all vertices bidirectionally, not exact Boolean line topology.
            assertTrue(org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance.distance(expected, cutEntry.geometry()) < 1e-7 * u,
                    "Translated per-board outlines and bridge masks must match");
            var cutCase = export(s, output, cutEntry, "cutout-panel", "cutout", board.geometry(), .8 * u,
                    new JSONObject().put("kind", "PANEL").put("shape", "FREEFORM").put("margin", 0).put("gapSize", 2 * u).put("gaps", "4"), elapsed(began), results);
            Files.writeString(output.resolve("fx-cutout.json"), new JSONObject().put("schema", 1).put("sourceName", "shared-board-area")
                    .put("units", input.units()).put("sourceWkt", WKT.write(board.geometry())).put("cases", new JSONArray().put(cutCase))
                    .put("comparisonScope", "Cutout on explicitly shared filled board areas; independent Python Edge_Cuts conversion and internal routing are NOT validated").toString(2));
            summary.put("cutoutInternalRingsIncluded", false).put("cutoutWarning", "Only exterior rings are cut. Internal rings require separate machining; this test does not certify them.");
            // FX opt-in extension, tested separately from the perimeter-only Python oracle.
            if (internalRings > 0) {
                before = geometryNames(s); began = System.nanoTime();
                var internal = new CutoutToolPanel.Result(new CutoutParameters(.8*u, 0, false,
                        CutoutKind.PANEL, CutoutShape.FREEFORM, 2*u, GapPattern.FOUR, true),
                        CutoutToolPanel.GapType.BRIDGE, 0, 0, List.of(), cutout.machining(), null, ToolProfile.C1);
                await(s, start(s, "runCutoutGeneration", new Class<?>[]{TreeItem.class, String.class, Geometry.class, BooleanSupplier.class, CutoutToolPanel.Result.class},
                        boundaryItem, input.units(), board.geometry(), (BooleanSupplier) () -> true, internal));
                var entry = newGeometry(s, before);
                int closed = 0;
                for (int i=0; i<entry.geometry().getNumGeometries(); i++)
                    if (entry.geometry().getGeometryN(i) instanceof LineString line && line.isClosed()) closed++;
                assertEquals(internalRings*copies, closed, "Every internal panel cut stays closed despite external bridges");
                export(s, output, entry, "cutout-panel-internal", "cutout-internal-fx", board.geometry(), .8*u,
                        new JSONObject().put("includeInternalCuts", true), elapsed(began), results);
                summary.put("internalCutsFxRoundTripTested", true);
            }
            var saved = MainIsolationMachiningTest.snapshot(s.window);
            began = System.nanoTime();
            Path project = output.resolve("panelized-flow.fcnproj"); s.window.saveProject(project);
            summary.put("saveMs", elapsed(began));
            long reopenStart = System.nanoTime(); s.window.openProject(project);
            summary.put("reopenMs", elapsed(reopenStart)).put("nativeBytes", Files.size(project));
            var reopened = MainIsolationMachiningTest.snapshot(s.window);
            summary.put("saveAndReopenMs", elapsed(began));
            assertEquals(saved.gerbers().size(), reopened.gerbers().size()); assertEquals(saved.excellons().size(), reopened.excellons().size());
            assertEquals(saved.geometries().size(), reopened.geometries().size()); assertEquals(saved.cncJobs().size(), reopened.cncJobs().size());
            for (var entry : saved.geometries()) {
                var copy = reopened.geometries().stream().filter(g -> g.name().equals(entry.name())).findFirst().orElseThrow();
                assertTrue(entry.geometry().equalsExact(copy.geometry(), 1e-10 * u));
                assertEquals(entry.units(), copy.units()); assertEquals(entry.cncDefaults(), copy.cncDefaults());
                assertEquals(entry.tools().size(), copy.tools().size());
                for (int i=0; i<entry.tools().size(); i++) {
                    var tool = entry.tools().get(i); var restored = copy.tools().get(i);
                    assertEquals(tool.toolDiameter(), restored.toolDiameter());
                    assertEquals(tool.toolProfile(), restored.toolProfile());
                    assertTrue(tool.geometry().equalsExact(restored.geometry(), 1e-10 * u));
                }
            }
            for (var original : input.drills()) verifyDrills(original.image(), reopened.excellons().stream()
                    .filter(e -> e.name().equals(original.name() + "_panelized")).findFirst().orElseThrow().image(), layout, false);
            for (var job : saved.cncJobs()) assertEquals(job.gcode(), reopened.cncJobs().stream().filter(j -> j.name().equals(job.name())).findFirst().orElseThrow().gcode());
            summary.put("nativeRoundTripChecked", true).put("sourceReadOnly", source != null).put("status", "PASS_WITH_DOCUMENTED_LIMITATIONS");
        } finally { Files.writeString(output.resolve("flow-report.json"), summary.toString(2)); }
    }
    private JSONObject nccCase(MainCamFlowTest.Session s, Input input, Path output,
                               ProjectFile.GeometryEntry board, TreeItem<String> boundaryItem,
                               ProjectFile.GerberEntry copper, TreeItem<String> item, Panelize.Layout layout,
                               boolean reference, boolean rest, boolean connect, JSONArray results) throws Exception {
        double u = input.units().equals("IN") ? 1 / 25.4 : 1;
        String id = "ncc-" + (rest ? "rest-" : "") + (reference ? "reference-geometry" : "itself")
                + (connect ? "-connect" : "");
        var before = geometryNames(s); long began = System.nanoTime();
        NccBoundary boundary = reference ? new NccBoundary.ReferenceGeometry(board.geometry()) : new NccBoundary.Itself();
        // Deliberately entered small-first: Rest must process largest-first.
        var params = new NccParameters(rest ? List.of(.2*u, u) : List.of(.5*u), .4, 0,
                NccMethod.STANDARD, connect, true, 0, rest, NccOrder.NONE, boundary, List.of());
        var ncc = new NccToolPanel.Result(new NccToolPanel.SourceCandidate(item, copper.name(), input.units(), true,
                copper.image().solidGeometry()), params, false, Map.of(), reference
                ? new NccToolPanel.ReferenceCandidate(boundaryItem, board.name(), false, board.geometry()) : null);
        var job = start(s, "runNccGeneration",
                new Class<?>[]{TreeItem.class, String.class, Geometry.class, boolean.class, NccToolPanel.Result.class},
                item, input.units(), copper.image().solidGeometry(), true, ncc);
        await(s, job);
        Object outcome = job.completion().get();
        var accessor = outcome.getClass().getDeclaredMethod("result"); accessor.setAccessible(true);
        var result = (NccResult) accessor.invoke(outcome);
        var entry = newGeometry(s, before);
        for (double[] offset : layout.offsets()) assertFalse(intersection(entry.geometry(),new TransformOp.Offset(offset[0],offset[1])
                .apply(input.area())).isEmpty(), id + " must reach every board");
        // Assertion-only robust overlay: classic line/polygon difference may
        // throw on almost coincident vertices in dense, otherwise valid paths.
        // Does not snap, repair or alter the exported production CAM paths.
        if (reference) assertTrue(difference(entry.geometry(), board.geometry().buffer(1e-7*u)).getLength() < 1e-6*u,
                id + " must not clear the spaces between boards or internal cutouts");
        else {
            Geometry hull = copper.image().solidGeometry().convexHull();
            assertTrue(difference(entry.geometry(), hull.buffer(1e-7*u)).getLength() < 1e-6*u,
                    "Itself follows the copper convex hull, not individual board outlines");
            if (layout.columns()*layout.rows() > 1) assertTrue(difference(result.clearingArea(),board.geometry()).getArea() > 0,
                    "Itself may clear spaces between copies; this is its documented semantics");
        }
        var tools = new JSONArray(); var toolResults = new JSONArray();
        for (double diameter : params.toolDiameters()) {
            var setting = params.settingsFor(diameter);
            tools.put(new JSONObject().put("diameter",diameter).put("method",setting.method().name())
                    .put("overlap",setting.overlapFraction()).put("connect",setting.connect())
                    .put("contour",setting.contour()).put("copperOffset",setting.copperOffset()));
        }
        var nonempty = result.toolResults().stream().filter(t -> !t.isEmpty()).toList();
        assertEquals(nonempty.size(), entry.tools().size(), "Published paths must stay associated with their cutters");
        if (rest) assertEquals(u, result.toolResults().getFirst().toolDiameter(), "Rest must ignore input order");
        var form = MainIsolationMachiningTest.cnc(entry);
        assertEquals(entry.tools(), form.tools(), "CNC form must retain per-tool paths and order");
        for (int i=0; i<nonempty.size(); i++) {
            assertEquals(nonempty.get(i).toolDiameter(), entry.tools().get(i).toolDiameter());
            assertTrue(nonempty.get(i).geometry().equalsExact(entry.tools().get(i).geometry()));
        }
        for (var tool : result.toolResults()) toolResults.put(new JSONObject().put("diameter",tool.toolDiameter())
                .put("fxWkt",WKT.write(tool.geometry())).put("fxFailedPolygons",tool.failedPolygonCount())
                .put("gcode",tool.isEmpty() ? "" : GCodeGenerator.generateGeometryCncJob(input.units(), tool.geometry(),
                        testCncParameters(u, false), tool.toolDiameter(), () -> false).gcode()));
        var json = new JSONObject().put("method","STANDARD").put("overlap",.4).put("margin",0)
                .put("connect",connect).put("contour",true).put("boundary",reference ? "reference-geometry" : "itself")
                .put("fxClearingAreaWkt",WKT.write(result.clearingArea())).put("fxFailedPolygons",result.totalFailedPolygonCount());
        if (reference) json.put("referenceWkt",WKT.write(board.geometry()));
        if (rest) json.put("restMachining",true).put("order","NONE").put("copperOffset",0)
                .put("tools",tools).put("fxToolResults",toolResults);
        return export(s, output, entry, id, "ncc", copper.image().solidGeometry(), rest ? .2*u : .5*u,
                json, elapsed(began), results);
    }
    private static GeometryGCodeParameters testCncParameters(double u, boolean multiTool) {
        return new GeometryGCodeParameters(3*u, .1*u, false, 0, 120*u, 0,
                multiTool, 0, null, 50*u, false, 0, false, 0);
    }
    private static Geometry difference(Geometry a, Geometry b) {
        return OverlayNGRobust.overlay(a,b,OverlayNG.DIFFERENCE);
    }
    private static Geometry intersection(Geometry a, Geometry b) {
        return OverlayNGRobust.overlay(a,b,OverlayNG.INTERSECTION);
    }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1e6; }
    private static void verifyDrills(ExcellonImage source, ExcellonImage panel, Panelize.Layout layout, boolean ordered) {
        assertEquals(source.toolDiameters(), panel.toolDiameters());
        int copies = layout.offsets().size();
        assertEquals(source.totalDrills() * copies, panel.totalDrills()); assertEquals(source.totalSlots() * copies, panel.totalSlots());
        List<ExcellonImage.Drill> expectedDrills = new ArrayList<>();
        List<ExcellonImage.Slot> expectedSlots = new ArrayList<>();
        for (double[] offset : layout.offsets()) {
            for (var d : source.drills()) expectedDrills.add(new ExcellonImage.Drill(d.toolId(), d.x()+offset[0], d.y()+offset[1]));
            for (var p : source.slots()) expectedSlots.add(new ExcellonImage.Slot(p.toolId(), p.x1()+offset[0], p.y1()+offset[1], p.x2()+offset[0], p.y2()+offset[1]));
        }
        var actualDrills = new ArrayList<>(panel.drills()); var actualSlots = new ArrayList<>(panel.slots());
        // Native/Python codecs regroup by tool; that is not a missing hole or changed tool ID.
        // Sorting retains duplicate multiplicities, unlike a set comparison.
        if (!ordered) {
            var dc = Comparator.comparingInt(ExcellonImage.Drill::toolId).thenComparingDouble(ExcellonImage.Drill::x).thenComparingDouble(ExcellonImage.Drill::y);
            var sc = Comparator.comparingInt(ExcellonImage.Slot::toolId).thenComparingDouble(ExcellonImage.Slot::x1).thenComparingDouble(ExcellonImage.Slot::y1)
                    .thenComparingDouble(ExcellonImage.Slot::x2).thenComparingDouble(ExcellonImage.Slot::y2);
            actualDrills.sort(dc); expectedDrills.sort(dc); actualSlots.sort(sc); expectedSlots.sort(sc);
        }
        for (int i=0; i<expectedDrills.size(); i++) {
            var d=expectedDrills.get(i); var c=actualDrills.get(i); assertEquals(d.toolId(),c.toolId());
            assertEquals(d.x(),c.x(),1e-10); assertEquals(d.y(),c.y(),1e-10);
        }
        for (int i=0; i<expectedSlots.size(); i++) {
            var p=expectedSlots.get(i); var c=actualSlots.get(i); assertEquals(p.toolId(),c.toolId());
            assertEquals(p.x1(),c.x1(),1e-10); assertEquals(p.y1(),c.y1(),1e-10);
            assertEquals(p.x2(),c.x2(),1e-10); assertEquals(p.y2(),c.y2(),1e-10);
        }
    }
    private static JSONObject export(MainCamFlowTest.Session s, Path directory, ProjectFile.GeometryEntry entry,
                                     String id, String operation, Geometry input, double diameter, JSONObject params,
                                     double millis, JSONArray results) throws Exception {
        assertFalse(entry.geometry().isEmpty()); assertTrue(entry.geometry().isValid()); assertTrue(entry.strokeOnly());
        assertFalse(entry.tools().isEmpty());
        long began = System.nanoTime();
        String jobName = id + "-" + results.length();
        // Explicit test settings, not machine recommendations.
        // Tcl cncjob uses one depth pass; GUI multi-depth defaults are checked separately in the round-trip.
        double u = entry.units().equals("IN") ? 1 / 25.4 : 1;
        double cutDepth = entry.cncDefaults() == null ? .1 * u : entry.cncDefaults().cutDepth();
        Path path = directory.resolve(jobName + ".nc");
        if (params.has("fxToolResults")) {
            // Tcl cncjob is intentionally single-tool; do not pass a Rest drawing
            // through it pretending every path uses the same cutter.
            var generated = GCodeGenerator.generateGeometryCncJob(entry.units(), entry.tools(), testCncParameters(u, true));
            TerminalPanelTest.fx(() -> call(s.window,"addCncJobToProject",
                    new Class<?>[]{String.class,String.class,Path.class,String.class,Geometry.class,Geometry.class},
                    jobName,entry.name(),path,generated.gcode(),generated.travelGeometry(),generated.cutGeometry()));
        } else s.window.cncjob(entry.name(), jobName, diameter, -cutDepth, 3*u, 120*u, 50*u, 0);
        s.window.writeGcode(jobName, path, "", "");
        String text = Files.readString(path);
        var preview = GCodeToolpathParser.parse(text, () -> false, f -> {});
        assertTrue(preview.plotAvailable(), preview.warning()); assertFalse(preview.cutGeometry().isEmpty());
        assertEquals(entry.units(), preview.units());
        // Four-decimal G-code precision is a separate numerical check from CAM topology.
        assertNotNull(preview.cutCenterlines());
        double sourceLength = params.has("fxToolResults") ? entry.tools().stream().mapToDouble(t -> t.geometry().getLength()).sum()
                : entry.geometry().getLength();
        assertEquals(sourceLength, preview.cutCenterlines().getLength(), Math.max(.003*u, sourceLength*.001));
        results.put(new JSONObject().put("id", jobName).put("source", entry.sourceName()).put("camAndPublicationMs", millis)
                .put("cncExportAndPreviewMs", elapsed(began)).put("length", entry.geometry().getLength())
                .put("points", entry.geometry().getNumPoints()).put("previewAvailable", true).put("failedPolygons",params.optInt("fxFailedPolygons",0)));
        return new JSONObject().put("id", id).put("operation", operation).put("diameter", diameter).put("units", entry.units())
                .put("inputWkt", WKT.write(input)).put("fxWkt", WKT.write(entry.geometry())).put("parameters", params)
                .put("gcode", text).put("fxDetailedPreviewAvailable", true).put("fxPreviewWarning", "");
    }
}
