package org.flatcam.app.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator;
import org.flatcam.cam.gcode.GCodeToolpathParser;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.IsolationGCodeParameters;
import org.flatcam.cam.isolation.IsolationGenerator;
import org.flatcam.cam.isolation.IsolationParameters;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.NccGenerator;
import org.flatcam.cam.ncc.NccMethod;
import org.flatcam.cam.ncc.NccParameters;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Optional CAM smoke tests using an unmodified, real Python .FlatPrj fixture. */
class PythonProjectCamSmokeTest {

    private static ProjectFile fixture() throws IOException {
        String path = System.getProperty("flatcam.python.project.fixture");
        Assumptions.assumeTrue(path != null && !path.isBlank());
        return PythonProjectIO.load(Path.of(path));
    }

    @Test
    void generatesIsolationForEveryImportedGerber() throws IOException {
        ProjectFile project = fixture();
        for (ProjectFile.GerberEntry entry : project.gerbers()) {
            double diameter = "IN".equals(entry.image().units()) ? 0.004 : 0.1;
            var result = IsolationGenerator.generate(entry.image().units(),
                    entry.image().solidGeometry(),
                    new IsolationParameters(diameter, 1, 0, IsolationType.BOTH));
            assertFalse(result.isEmpty(), entry.name());
            assertTrue(result.totalLength() > 0, entry.name());
            assertNotNull(result.bounds(), entry.name());
            boolean inch = "IN".equals(entry.image().units());
            var cncJob = GCodeGenerator.generateIsolationCncJob(result,
                    new IsolationGCodeParameters(inch ? 0.12 : 3.0,
                            inch ? 0.004 : 0.1, inch ? 12 : 300, 0), diameter);
            assertTrue(cncJob.gcode().contains("M30"), entry.name());
            assertFalse(cncJob.cutGeometry().isEmpty(), entry.name());
        }
    }

    @Test
    void generatesDrillingJobsForEveryImportedExcellon() throws IOException {
        ProjectFile project = fixture();
        for (ProjectFile.ExcellonEntry entry : project.excellons()) {
            boolean inch = "IN".equals(entry.image().units());
            var parameters = new DrillGCodeParameters(inch ? 0.12 : 3.0,
                    inch ? 0.067 : 1.7, inch ? 12 : 300, 0, false);
            var result = GCodeGenerator.generateDrillCncJob(entry.image(), parameters, null);
            assertTrue(result.gcode().contains("M30"), entry.name());
            assertFalse(result.cutGeometry().isEmpty(), entry.name());
        }
    }

    @Test
    void parsesEveryImportedCncJobForPreview() throws IOException {
        ProjectFile project = fixture();
        for (ProjectFile.CncJobRecord entry : project.cncJobs()) {
            var result = GCodeToolpathParser.parse(entry.gcode(),
                    CancellationToken.none(), ProgressCallback.none());
            assertTrue(result.lineCount() > 0, entry.name());
            assertTrue(result.plotAvailable(), entry.name() + ": " + result.warning());
            assertFalse(result.travelGeometry().isEmpty() && result.cutGeometry().isEmpty(), entry.name());
        }
    }

    @Test
    void generatesCncJobsFromEveryImportedGeometry() throws IOException {
        ProjectFile project = fixture();
        for (ProjectFile.GeometryEntry entry : project.geometries()) {
            boolean inch = "IN".equals(entry.units()) || "INCH".equals(entry.units());
            var parameters = new GeometryGCodeParameters(inch ? 0.12 : 3.0,
                    inch ? 0.004 : 0.1, false, 0, inch ? 12 : 300, 0, false);
            var result = GCodeGenerator.generateGeometryCncJob(entry.units(), entry.tools(), parameters);
            assertTrue(result.gcode().contains("M30"), entry.name());
            assertFalse(result.cutGeometry().isEmpty(), entry.name());
            var preview = GCodeToolpathParser.parse(result.gcode(),
                    CancellationToken.none(), ProgressCallback.none());
            assertTrue(preview.plotAvailable(), entry.name() + ": " + preview.warning());
        }
    }

    @Test
    void generatesNccForFirstImportedGerber() throws IOException {
        ProjectFile project = fixture();
        ProjectFile.GerberEntry entry = project.gerbers().get(0);
        boolean inch = "IN".equals(entry.image().units());
        var parameters = new NccParameters(inch ? 0.02 : 0.5, 0.4,
                inch ? 0.04 : 1.0, NccMethod.LINES, false, false, 0);
        var result = NccGenerator.generate(entry.image().units(),
                entry.image().solidGeometry(), parameters);
        assertFalse(result.isEmpty());
        assertTrue(result.totalLength() > 0);
        double diameter = inch ? 0.02 : 0.5;
        var cncJob = GCodeGenerator.generateGeometryCncJob(entry.image().units(),
                result.geometry(), new GeometryGCodeParameters(inch ? 0.12 : 3.0,
                        inch ? 0.004 : 0.1, false, 0, inch ? 12 : 300, 0, false), diameter);
        assertTrue(cncJob.gcode().contains("M30"));
        assertFalse(cncJob.cutGeometry().isEmpty());
    }
}
