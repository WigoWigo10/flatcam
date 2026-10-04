package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class TclFlatcamCommandsTest {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    /** A minimal in-memory stand-in for the live FX session, mirroring MainWindow's own bookkeeping closely enough for these tests. */
    private static final class FakeHost implements TclFlatcamHost {
        record IsolateCall(String source, String outname, double dia, int passes, double overlapFraction, IsolationType type) {
        }
        record CutoutCall(String source, String outname, double dia, double margin, double gapSize, GapPattern gaps) {
        }
        record NccCall(String source, String outname, List<Double> tools, double overlapFraction, double margin,
                       NccMethod method, boolean connect, boolean contour, boolean rest, NccBoundary boundary) {
        }
        record CncjobCall(String source, String outname, double dia, double zCut, double zMove,
                          double feedrate, double feedrateZ, double feedrateRapid) {
        }

        final List<String> openedGerbers = new ArrayList<>();
        final List<String> openedExcellons = new ArrayList<>();
        final Map<String, double[]> bounds = new LinkedHashMap<>();
        final Map<String, Kind> kinds = new LinkedHashMap<>();
        final Map<String, Geometry> geometries = new LinkedHashMap<>();
        final Map<String, String> gcodeByName = new LinkedHashMap<>();
        final List<String> order = new ArrayList<>();
        final List<IsolateCall> isolateCalls = new ArrayList<>();
        final List<CutoutCall> cutoutCalls = new ArrayList<>();
        final List<NccCall> nccCalls = new ArrayList<>();
        final List<CncjobCall> cncjobCalls = new ArrayList<>();
        final List<Path> writtenFiles = new ArrayList<>();
        final List<String> writtenContents = new ArrayList<>();
        boolean deletedAll;
        boolean failWrite;
        String nextOpenFails;

        @Override
        public String openGerber(Path file, String outname) throws IOException {
            if (file.toString().equals(nextOpenFails)) {
                throw new IOException("boom");
            }
            openedGerbers.add(outname);
            order.add(outname);
            kinds.put(outname, Kind.GERBER);
            return outname;
        }

        @Override
        public String openExcellon(Path file, String outname) throws IOException {
            openedExcellons.add(outname);
            order.add(outname);
            kinds.put(outname, Kind.EXCELLON);
            return outname;
        }

        @Override
        public List<String> objectNames() {
            return List.copyOf(order);
        }

        @Override
        public Optional<ObjectRef> find(String name) {
            return order.contains(name)
                    ? Optional.of(new ObjectRef(kinds.getOrDefault(name, Kind.GERBER), name))
                    : Optional.empty();
        }

        @Override
        public void delete(String name) {
            order.remove(name);
        }

        @Override
        public void deleteAll() {
            deletedAll = true;
            order.clear();
        }

        @Override
        public Optional<double[]> boundsOf(String name) {
            return Optional.ofNullable(bounds.get(name));
        }

        @Override
        public String newEmptyGeometry(String name) {
            order.add(name);
            return name;
        }

        @Override
        public String newBoundingBoxGeometry(String sourceName, String outname, double margin, boolean rounded) {
            order.add(outname);
            return outname;
        }

        @Override
        public Optional<Kind> kindOf(String name) {
            return Optional.ofNullable(kinds.get(name));
        }

        @Override
        public Optional<Geometry> geometryOf(String name) {
            return Optional.ofNullable(geometries.get(name));
        }

        @Override
        public String isolate(String sourceName, String outname, double toolDiameter, int passes,
                              double overlapFraction, IsolationType type) throws TclException {
            if (kinds.get(sourceName) != Kind.GERBER) {
                throw new TclException("Expected a Gerber object, got: " + sourceName);
            }
            isolateCalls.add(new IsolateCall(sourceName, outname, toolDiameter, passes, overlapFraction, type));
            order.add(outname);
            kinds.put(outname, Kind.GEOMETRY);
            return outname;
        }

        @Override
        public String cutoutRectangular(String sourceName, String outname, double toolDiameter, double margin,
                                        double gapSize, GapPattern gaps) throws TclException {
            if (!order.contains(sourceName)) {
                throw new TclException("Could not retrieve object: " + sourceName);
            }
            cutoutCalls.add(new CutoutCall(sourceName, outname, toolDiameter, margin, gapSize, gaps));
            order.add(outname);
            kinds.put(outname, Kind.GEOMETRY);
            return outname;
        }

        @Override
        public String nccClear(String sourceName, String outname, List<Double> toolDiameters, double overlapFraction,
                               double margin, NccMethod method, boolean connect, boolean contour, boolean rest,
                               NccBoundary boundary) throws TclException {
            if (!order.contains(sourceName)) {
                throw new TclException("Could not retrieve object: " + sourceName);
            }
            nccCalls.add(new NccCall(sourceName, outname, toolDiameters, overlapFraction, margin, method,
                    connect, contour, rest, boundary));
            order.add(outname);
            kinds.put(outname, Kind.GEOMETRY);
            return outname;
        }

        @Override
        public String cncjob(String sourceName, String outname, double toolDiameter, double zCut, double zMove,
                             double feedrate, double feedrateZ, double feedrateRapid) throws TclException {
            if (!order.contains(sourceName)) {
                throw new TclException("Object not found: " + sourceName);
            }
            cncjobCalls.add(new CncjobCall(sourceName, outname, toolDiameter, zCut, zMove, feedrate, feedrateZ, feedrateRapid));
            order.add(outname);
            kinds.put(outname, Kind.CNC_JOB);
            gcodeByName.put(outname, "G21\nG1 X0 Y0\nM30\n");
            return outname;
        }

        @Override
        public String exportGcode(String cncJobName, String preamble, String postamble) throws TclException {
            String gcode = gcodeByName.get(cncJobName);
            if (gcode == null) {
                throw new TclException("Expected CNCjob, got: " + cncJobName);
            }
            return preamble + gcode + postamble;
        }

        @Override
        public void writeGcode(String cncJobName, Path outputFile, String preamble, String postamble)
                throws TclException, IOException {
            String gcode = gcodeByName.get(cncJobName);
            if (gcode == null) {
                throw new TclException("Could not retrieve object: " + cncJobName);
            }
            if (failWrite) {
                throw new IOException("disk full");
            }
            writtenFiles.add(outputFile);
            writtenContents.add(preamble + gcode + postamble);
        }
    }

    private static TclInterpreter withCommands(FakeHost host) {
        TclInterpreter interpreter = new TclInterpreter();
        new TclFlatcamCommands(host).registerOn(interpreter);
        return interpreter;
    }

    @Test
    void openGerberDefaultsOutnameToTheFileNameWithoutItsPath() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber C:/boards/test.gbr");
        assertEquals(List.of("test.gbr"), host.openedGerbers);
    }

    @Test
    void openGerberHonorsAnExplicitOutname() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber test.gbr -outname gerber_file");
        assertEquals(List.of("gerber_file"), host.openedGerbers);
    }

    @Test
    void openGerberWrapsAnIoFailureAsATclException() {
        FakeHost host = new FakeHost();
        host.nextOpenFails = "missing.gbr";
        TclInterpreter interpreter = withCommands(host);
        assertThrows(TclException.class, () -> interpreter.eval("open_gerber missing.gbr"));
    }

    @Test
    void openExcellonDefaultsOutnameFromTheFilename() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_excellon D:\\\\boards\\\\test.drl");
        assertEquals(List.of("test.drl"), host.openedExcellons);
    }

    @Test
    void newGeometryDefaultsToNewGeoWhenNoNameIsGiven() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("new_geometry");
        assertEquals(List.of("new_geo"), host.order);
        interpreter.eval("new_geometry my_geo");
        assertEquals(List.of("new_geo", "my_geo"), host.order);
    }

    @Test
    void getNamesListsEveryObjectOnePerLine() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("open_excellon b.drl");
        assertEquals("a.gbr\nb.drl", interpreter.eval("get_names"));
    }

    @Test
    void deleteWithANameRemovesOnlyThatObject() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("open_gerber b.gbr");
        interpreter.eval("delete a.gbr");
        assertEquals(List.of("b.gbr"), host.order);
        assertFalse(host.deletedAll);
    }

    @Test
    void deleteWithNoNameDeletesEverything() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("delete");
        assertTrue(host.deletedAll);
        assertTrue(host.order.isEmpty());
    }

    @Test
    void delAliasBehavesLikeDelete() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("del a.gbr");
        assertTrue(host.order.isEmpty());
    }

    @Test
    void bboxDefaultsOutnameAndMarginAndRegistersTheNewGeometry() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber source.gbr");
        interpreter.eval("bbox source.gbr");
        assertEquals(List.of("source.gbr", "source.gbr_bbox"), host.order);
    }

    @Test
    void bboxHonorsAnExplicitOutname() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber source.gbr");
        interpreter.eval("bbox source.gbr -outname my_box -margin 2");
        assertTrue(host.order.contains("my_box"));
    }

    @Test
    void boundsReturnsOneLinePerCommaSeparatedObject() throws TclException {
        FakeHost host = new FakeHost();
        host.bounds.put("a.gbr", new double[]{0, 0, 10, 10});
        host.bounds.put("b.drl", new double[]{1, 1, 5, 5});
        TclInterpreter interpreter = withCommands(host);
        assertEquals("0.0 0.0 10.0 10.0\n1.0 1.0 5.0 5.0", interpreter.eval("bounds a.gbr,b.drl"));
    }

    @Test
    void boundsRaisesAnErrorForAnUnknownObject() {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        assertThrows(TclException.class, () -> interpreter.eval("bounds nope"));
    }

    @Test
    void unknownOptionIsRejectedWithATclException() {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        assertThrows(TclException.class, () -> interpreter.eval("open_gerber a.gbr -bogus x"));
    }

    @Test
    void isolateRequiresDiaAndDefaultsPassesAndOutname() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber margin.gbr");
        interpreter.eval("isolate margin.gbr -dia 3");
        assertEquals(1, host.isolateCalls.size());
        var call = host.isolateCalls.get(0);
        assertEquals("margin.gbr", call.source());
        assertEquals("margin.gbr_iso", call.outname());
        assertEquals(3.0, call.dia(), 1e-9);
        assertEquals(1, call.passes());
        assertEquals(0.0, call.overlapFraction(), 1e-9);
        assertEquals(IsolationType.BOTH, call.type());
    }

    @Test
    void isolateConvertsOverlapPercentAndMapsIsoType() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber margin.gbr");
        interpreter.eval("isolate margin.gbr -dia 0.1 -passes 2 -overlap 25 -iso_type 0 -outname out_geo");
        var call = host.isolateCalls.get(0);
        assertEquals("out_geo", call.outname());
        assertEquals(2, call.passes());
        assertEquals(0.25, call.overlapFraction(), 1e-9);
        assertEquals(IsolationType.EXTERIOR, call.type());
    }

    @Test
    void isolateRejectsANonGerberSource() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        host.order.add("some_geo");
        host.kinds.put("some_geo", TclFlatcamHost.Kind.GEOMETRY);
        assertThrows(TclException.class, () -> interpreter.eval("isolate some_geo -dia 0.1"));
    }

    @Test
    void isolateWithoutDiaIsRejected() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber margin.gbr");
        assertThrows(TclException.class, () -> interpreter.eval("isolate margin.gbr"));
    }

    @Test
    void cutoutDefaultsMarginAndGapsAndOutname() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber new_geo.gbr");
        interpreter.eval("cutout new_geo.gbr -dia 1.2 -gapsize 3");
        var call = host.cutoutCalls.get(0);
        assertEquals("new_geo.gbr_cutout", call.outname());
        assertEquals(1.2, call.dia(), 1e-9);
        assertEquals(0.0, call.margin(), 1e-9);
        assertEquals(GapPattern.FOUR, call.gaps());
    }

    @Test
    void cutoutMapsGapsTbAndLr() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("cutout a.gbr -dia 1 -gapsize 1 -gaps tb -outname cut_tb");
        interpreter.eval("cutout a.gbr -dia 1 -gapsize 1 -gaps lr -outname cut_lr");
        assertEquals(GapPattern.TB, host.cutoutCalls.get(0).gaps());
        assertEquals(GapPattern.LR, host.cutoutCalls.get(1).gaps());
    }

    @Test
    void cutoutRejectsAnInvalidGapsValue() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        assertThrows(TclException.class, () -> interpreter.eval("cutout a.gbr -dia 1 -gapsize 1 -gaps bogus"));
    }

    @Test
    void nccRequiresEitherAllOrBoxButNotBoth() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        assertThrows(TclException.class, () -> interpreter.eval("ncc a.gbr -tooldia 0.3"));
        assertThrows(TclException.class, () ->
                interpreter.eval("ncc a.gbr -tooldia 0.3 -all -box a.gbr"));
    }

    @Test
    void nccWithAllUsesItselfBoundaryAndParsesToolList() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("ncc a.gbr -tooldia 0.3,1.0 -overlap 10 -margin 1.0 -method standard -all");
        var call = host.nccCalls.get(0);
        assertEquals(List.of(0.3, 1.0), call.tools());
        assertEquals(0.1, call.overlapFraction(), 1e-9);
        assertEquals(1.0, call.margin(), 1e-9);
        assertEquals(NccMethod.STANDARD, call.method());
        assertTrue(call.boundary() instanceof NccBoundary.Itself);
        assertEquals("a.gbr_ncc_rm", call.outname());
    }

    @Test
    void nccWithBoxOnAGerberUsesReferenceGerberBoundary() throws TclException {
        FakeHost host = new FakeHost();
        Geometry reference = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        host.geometries.put("ref.gbr", reference);
        host.kinds.put("ref.gbr", TclFlatcamHost.Kind.GERBER);
        host.order.add("ref.gbr");
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("ncc a.gbr -tooldia 0.3 -box ref.gbr -rest 1");
        var call = host.nccCalls.get(0);
        assertTrue(call.boundary() instanceof NccBoundary.ReferenceGerber);
        assertTrue(call.rest());
        assertEquals("a.gbr_ncc", call.outname());
    }

    @Test
    void nccWithBoxOnAGeometryUsesReferenceGeometryBoundary() throws TclException {
        FakeHost host = new FakeHost();
        Geometry reference = FACTORY.toGeometry(new Envelope(0, 10, 0, 10));
        host.geometries.put("ref_geo", reference);
        host.kinds.put("ref_geo", TclFlatcamHost.Kind.GEOMETRY);
        host.order.add("ref_geo");
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        interpreter.eval("ncc a.gbr -tooldia 0.3 -box ref_geo");
        assertTrue(host.nccCalls.get(0).boundary() instanceof NccBoundary.ReferenceGeometry);
    }

    @Test
    void nccRejectsAnUnknownMethod() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        interpreter.eval("open_gerber a.gbr");
        assertThrows(TclException.class, () -> interpreter.eval("ncc a.gbr -tooldia 0.3 -all -method lines"));
    }

    @Test
    void cncjobDefaultsFeedrateZToFeedrateAndFeedrateRapidToZero() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        host.order.add("geo");
        interpreter.eval("cncjob geo -dia 0.5 -z_cut -1.7 -z_move 2 -feedrate 120");
        var call = host.cncjobCalls.get(0);
        assertEquals("geo_cnc", call.outname());
        assertEquals(120.0, call.feedrateZ(), 1e-9);
        assertEquals(0.0, call.feedrateRapid(), 1e-9);
    }

    @Test
    void cncjobMissingRequiredFlagIsRejected() {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        host.order.add("geo");
        assertThrows(TclException.class, () -> interpreter.eval("cncjob geo -dia 0.5"));
    }

    @Test
    void exportGcodeReturnsTheGcodeTextWithPreambleAndPostamble() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        host.order.add("geo");
        interpreter.eval("cncjob geo -dia 0.5 -z_cut -1 -z_move 2 -feedrate 100");
        String result = interpreter.eval("export_gcode geo_cnc -preamble PRE -postamble POST");
        assertTrue(result.startsWith("PRE"));
        assertTrue(result.endsWith("POST"));
    }

    @Test
    void exportGcodeRejectsANonCncJobName() {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        assertThrows(TclException.class, () -> interpreter.eval("export_gcode nope"));
    }

    @Test
    void writeGcodeSavesToTheGivenFile() throws TclException {
        FakeHost host = new FakeHost();
        TclInterpreter interpreter = withCommands(host);
        host.order.add("geo");
        interpreter.eval("cncjob geo -dia 0.5 -z_cut -1 -z_move 2 -feedrate 100");
        interpreter.eval("write_gcode geo_cnc out.gcode -preamble PRE");
        assertEquals(List.of(Path.of("out.gcode")), host.writtenFiles);
        assertTrue(host.writtenContents.get(0).startsWith("PRE"));
    }

    @Test
    void writeGcodeWrapsAnIoFailureAsATclException() throws TclException {
        FakeHost host = new FakeHost();
        host.failWrite = true;
        TclInterpreter interpreter = withCommands(host);
        host.order.add("geo");
        interpreter.eval("cncjob geo -dia 0.5 -z_cut -1 -z_move 2 -feedrate 100");
        assertThrows(TclException.class, () -> interpreter.eval("write_gcode geo_cnc out.gcode"));
    }
}