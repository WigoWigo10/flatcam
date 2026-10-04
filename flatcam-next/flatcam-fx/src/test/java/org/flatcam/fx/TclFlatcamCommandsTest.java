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
import org.flatcam.cam.tcl.TclException;
import org.flatcam.cam.tcl.TclInterpreter;
import org.junit.jupiter.api.Test;

class TclFlatcamCommandsTest {

    /** A minimal in-memory stand-in for the live FX session, mirroring MainWindow's own bookkeeping closely enough for these tests. */
    private static final class FakeHost implements TclFlatcamHost {
        final List<String> openedGerbers = new ArrayList<>();
        final List<String> openedExcellons = new ArrayList<>();
        final Map<String, double[]> bounds = new LinkedHashMap<>();
        final List<String> order = new ArrayList<>();
        boolean deletedAll;
        String nextOpenFails;

        @Override
        public String openGerber(Path file, String outname) throws IOException {
            if (file.toString().equals(nextOpenFails)) {
                throw new IOException("boom");
            }
            openedGerbers.add(outname);
            order.add(outname);
            return outname;
        }

        @Override
        public String openExcellon(Path file, String outname) throws IOException {
            openedExcellons.add(outname);
            order.add(outname);
            return outname;
        }

        @Override
        public List<String> objectNames() {
            return List.copyOf(order);
        }

        @Override
        public Optional<ObjectRef> find(String name) {
            return order.contains(name) ? Optional.of(new ObjectRef(Kind.GERBER, name)) : Optional.empty();
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
}
