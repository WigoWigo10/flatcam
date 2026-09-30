package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.NccBoundary;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccOrder;
import org.flatcam.cam.ncc.NccParameters;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Differential check of the NCC against a real FlatCAM Python result. Needs a Python 8.9xx project that
 * holds a copper Gerber ("F_Cu"), the board-area Geometry ("...Edge_Cuts.gm1_area") and the CNC Job that
 * Python's NCC made from them ("Cobre_MortoFino_Top_cnc": 0.1829 mm cutter, Standard, connect, board
 * area as the boundary, about 54% overlap). It is skipped unless the project's path is given in the
 * FLATCAM_PARITY_PROJECT environment variable, because such projects are private and not part of the repo.
 */
class NccPythonParityTest {

    @Test
    void standardNccClearsTheSameAreaAsPythonWithNearlyTheSameToolpathLength() throws Exception {
        String location = System.getenv("FLATCAM_PARITY_PROJECT");
        assumeTrue(location != null && Files.exists(Path.of(location)), "FLATCAM_PARITY_PROJECT not set");
        ProjectFile project = PythonProjectIO.load(Path.of(location));
        GerberImage copper = project.gerbers().stream().filter(g -> g.name().contains("F_Cu"))
                .findFirst().orElseThrow().image();
        Geometry board = project.geometries().stream().filter(g -> g.name().endsWith("Edge_Cuts.gm1_area"))
                .findFirst().orElseThrow().geometry();
        String gcode = project.cncJobs().stream().filter(j -> "Cobre_MortoFino_Top_cnc".equals(j.name()))
                .findFirst().orElseThrow().gcode();
        double tool = 0.1829;
        Geometry pythonPaths = GCodeToolpathParser.parse(gcode, () -> false, fraction -> { }).cutCenterlines();
        Geometry pythonCover = pythonPaths.buffer(tool / 2, 16);

        NccParameters parameters = new NccParameters(List.of(tool), 0.54, 0.0, NccMethod.STANDARD, true, true,
                0, false, NccOrder.NONE, new NccBoundary.Area(board), List.of());
        Geometry fxPaths = NccGenerator.generate("MM", copper.solidGeometry(), parameters).geometry();
        Geometry fxCover = fxPaths.buffer(tool / 2, 16);

        double common = OverlayNGRobust.overlay(pythonCover, fxCover, OverlayNG.INTERSECTION).getArea();
        double either = OverlayNGRobust.overlay(pythonCover, fxCover, OverlayNG.UNION).getArea();
        assertTrue(common / either > 0.995, "cleared-area agreement " + common / either);
        assertEquals(pythonPaths.getLength(), fxPaths.getLength(), pythonPaths.getLength() * 0.01);
    }
}
