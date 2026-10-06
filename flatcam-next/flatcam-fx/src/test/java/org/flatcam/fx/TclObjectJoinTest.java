package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.app.project.DrillCncSettings;
import org.flatcam.app.project.GeometryCncSettings;
import org.flatcam.app.project.ProjectFile;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.DrillGCodeParameters;
import org.flatcam.cam.gcode.GCodeGenerator.DrillJobOptions;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.gcode.VTipSettings;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.geometry.ToolProfile;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

class TclObjectJoinTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();
    private static final GeometryGCodeParameters FIRST = new GeometryGCodeParameters(2, 1, false, 1, 120, 10000, false);
    private static final GeometryGCodeParameters SECOND = new GeometryGCodeParameters(3, 2, true, 0.5, 250, 9000, false);
    private static Geometry path(double x) {
        return FACTORY.createLineString(new Coordinate[]{new Coordinate(x, 0), new Coordinate(x + 2, 0)});
    }
    private static ProjectFile.GeometryEntry geo(String name, GeometryGCodeParameters defaults, GeometryCncSettings settings, boolean multi) {
        Geometry line = path(name.equals("first") ? 0 : 10);
        return new ProjectFile.GeometryEntry(name, "", "MM", line, true,
                multi ? List.of(new ToolGeometry(0.8, line)) : List.of(), null, null, false, defaults, settings);
    }
    private static GeometryCncSettings settings(GCodePreprocessor profile) {
        return new GeometryCncSettings(profile, null, Map.of(), Map.of());
    }
    private static ProjectFile.ExcellonEntry exc(String name, int id, double diameter, DrillGCodeParameters defaults, DrillCncSettings settings) {
        Geometry solid = FACTORY.createPoint(new Coordinate(id, 2)).buffer(diameter / 2);
        return new ProjectFile.ExcellonEntry(name, ExcellonImage.of("MM", Map.of(id, diameter),
                List.of(new ExcellonImage.Drill(id, id, 2)), List.of(new ExcellonImage.Slot(id, id, 2, id + 1, 2)), solid),
                null, null, true, true, false, defaults == null ? Map.of() : Map.of(id, defaults), settings);
    }
    private static DrillCncSettings drilling(int id) {
        return new DrillCncSettings(GCodePreprocessor.FX_PORTABLE,
                new DrillJobOptions(false, 15, 2, null, null, 0, null), List.of(id), DrillCncSettings.ToolOrder.REVERSE);
    }

    @Test void geometryKeepsToolsSeparateAndRemapsIndividualParameters() {
        var first = geo("first", FIRST, settings(GCodePreprocessor.FX_PORTABLE), true);
        var second = geo("second", SECOND, settings(GCodePreprocessor.FX_PORTABLE), true);
        var result = TclObjectJoin.geometry(List.of(first, second));
        assertEquals(2, result.joined().tools().size()); assertEquals(2, result.joined().geometry().getNumGeometries());
        assertEquals(SECOND, result.defaults()); assertEquals(Map.of(0, FIRST), result.settings().parametersByTool());
        assertTrue(first.geometry().equalsExact(result.joined().tools().getFirst().geometry()));
        assertEquals(FIRST, first.cncDefaults());
    }

    @Test void geometryRemapsVTipWithoutMergingTheSameDiameter() {
        var second = geo("second", FIRST, settings(GCodePreprocessor.FX_PORTABLE), true);
        var first = geo("first", FIRST, settings(GCodePreprocessor.FX_PORTABLE), true);
        var v = new ProjectFile.GeometryEntry("v", "", "MM", first.geometry(), true,
                List.of(new ToolGeometry(0.8, first.geometry(), ToolProfile.V)), null, null, false, FIRST,
                new GeometryCncSettings(GCodePreprocessor.FX_PORTABLE, null, Map.of(0, new VTipSettings(0.1, 30))));
        var result = TclObjectJoin.geometry(List.of(second, v));
        assertEquals(Map.of(1, new VTipSettings(0.1, 30)), result.settings().vTools());
        assertEquals(ToolProfile.V, result.joined().tools().getLast().toolProfile());
        assertTrue(result.settings().parametersByTool().isEmpty());
    }

    @Test void singleGeometryKeepsIdenticalParametersAndRefusesDifferentOnes() {
        var first = geo("first", FIRST, settings(GCodePreprocessor.FX_PORTABLE), false);
        var same = geo("second", FIRST, settings(GCodePreprocessor.FX_PORTABLE), false);
        assertEquals(FIRST, TclObjectJoin.geometry(List.of(first, same)).defaults());
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.geometry(List.of(first,
                geo("second", SECOND, settings(GCodePreprocessor.FX_PORTABLE), false))));
    }

    @Test void geometryRefusesSingleMultiDifferentProfilesAndPartiallyConfiguredSources() {
        var first = geo("first", FIRST, settings(GCodePreprocessor.FX_PORTABLE), true);
        for (var other : List.of(geo("second", FIRST, settings(GCodePreprocessor.FX_PORTABLE), false),
                geo("second", FIRST, settings(GCodePreprocessor.DEFAULT), true),
                geo("second", null, null, true), geo("second", FIRST, null, true)))
            assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.geometry(List.of(first, other)));
    }

    @Test void geometryRefusesConflictingCommonOptionsAndKeepsUniformSpecialProfilesUsable() {
        var first = geo("first", FIRST, settings(GCodePreprocessor.FX_PORTABLE), true);
        var different = new GeometryGCodeParameters(2, 1, false, 1, 120, 10000, true);
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.geometry(List.of(first,
                geo("second", different, settings(GCodePreprocessor.FX_PORTABLE), true))));
        var laser = settings(GCodePreprocessor.GRBL_LASER);
        var same = TclObjectJoin.geometry(List.of(geo("first", FIRST, laser, true), geo("second", FIRST, laser, true)));
        assertTrue(same.settings().parametersByTool().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.geometry(List.of(
                geo("first", FIRST, laser, true), geo("second", SECOND, laser, true))));
    }

    @Test void unconfiguredGeometryDoesNotInventMachiningSettings() {
        var result = TclObjectJoin.geometry(List.of(geo("first", null, null, true), geo("second", null, null, true)));
        assertNull(result.defaults()); assertNull(result.settings());
    }

    @Test void excellonFusesAndRemapsParametersSlotsAndSelectedTools() {
        var parameters = new DrillGCodeParameters(2, 1.7, 300, 0, false);
        var first = exc("first", 7, 0.80001, parameters, drilling(7));
        var second = exc("second", 42, 0.80002, parameters, drilling(42));
        var result = TclObjectJoin.excellon(List.of(first, second));
        assertEquals(Map.of(1, 0.8), result.image().toolDiameters());
        assertEquals(Map.of(1, parameters), result.defaults()); assertEquals(List.of(1), result.settings().selectedToolIds());
        assertEquals(2, result.image().totalDrills()); assertEquals(2, result.image().totalSlots());
        assertTrue(result.image().slots().stream().allMatch(slot -> slot.toolId() == 1));
        assertEquals(7, first.image().drills().getFirst().toolId());
    }

    @Test void differentExcellonDiametersRetainDifferentMachiningParameters() {
        var a = new DrillGCodeParameters(2, 1, 100, 0, false);
        var b = new DrillGCodeParameters(3, 2, 250, 0, false);
        var result = TclObjectJoin.excellon(List.of(exc("first", 7, 0.8, a, drilling(7)), exc("second", 42, 1, b, drilling(42))));
        assertEquals(Map.of(1, a, 2, b), result.defaults());
        assertEquals(List.of(1, 2), result.settings().selectedToolIds());
        assertEquals(DrillCncSettings.ToolOrder.REVERSE, result.settings().toolOrder());
    }

    @Test void fusedExcellonCannotInheritMissingParametersInEitherSourceOrder() {
        var parameters = new DrillGCodeParameters(3, 5, 500, 0, false);
        for (boolean withSettings : List.of(false, true)) {
            var configured = exc("first", 7, 0.80001, parameters, withSettings ? drilling(7) : null);
            var unconfigured = exc("second", 42, 0.80002, null, withSettings ? drilling(42) : null);
            for (var sources : List.of(List.of(configured, unconfigured), List.of(unconfigured, configured))) {
                var error = assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(sources));
                assertTrue(error.getMessage().contains("parametros diferentes"));
            }
            assertEquals(Map.of(7, parameters), configured.drillDefaults());
            assertTrue(unconfigured.drillDefaults().isEmpty());
        }
    }

    @Test void parametersMissingOnBothFusedExcellonToolsRemainMissing() {
        var result = TclObjectJoin.excellon(List.of(
                exc("first", 7, 0.80001, null, null), exc("second", 42, 0.80002, null, null)));
        assertEquals(Map.of(1, 0.8), result.image().toolDiameters());
        assertEquals(2, result.image().totalDrills());
        assertTrue(result.defaults().isEmpty());
        assertNull(result.settings());
    }

    @Test void differentExcellonDiametersDoNotRequireMatchingParameterPresence() {
        var parameters = new DrillGCodeParameters(3, 5, 500, 0, false);
        var result = TclObjectJoin.excellon(List.of(
                exc("first", 7, 0.8, parameters, drilling(7)), exc("second", 42, 1, null, drilling(42))));
        assertEquals(Map.of(1, parameters), result.defaults());
        assertEquals(Map.of(1, 0.8, 2, 1.0), result.image().toolDiameters());
        assertEquals(List.of(1, 2), result.settings().selectedToolIds());
    }

    @Test void parameterPresenceMustMatchWithinOneSourceWhenItsToolsFuse() {
        var image = ExcellonImage.of("MM", Map.of(7, 0.80001, 42, 0.80002),
                List.of(new ExcellonImage.Drill(7, 0, 0), new ExcellonImage.Drill(42, 10, 0)), List.of(),
                FACTORY.createMultiPointFromCoords(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)}).buffer(0.4));
        var partial = new ProjectFile.ExcellonEntry("partial", image, null, null, true, true, false,
                Map.of(7, new DrillGCodeParameters(3, 5, 500, 0, false)), null);
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(List.of(
                partial, exc("second", 50, 1, null, null))));
    }

    @Test void excellonRefusesConflictingFusedParametersAndCommonProfiles() {
        var a = new DrillGCodeParameters(2, 1, 100, 0, false);
        var b = new DrillGCodeParameters(3, 2, 250, 0, false);
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(List.of(
                exc("first", 7, 0.8, a, null), exc("second", 42, 0.8, b, null))));
        var different = new DrillCncSettings(GCodePreprocessor.DEFAULT, drilling(42).options(), List.of(42), drilling(42).toolOrder());
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(List.of(
                exc("first", 7, 0.8, a, drilling(7)), exc("second", 42, 1, b, different))));
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(List.of(
                exc("first", 7, 0.8, a, drilling(7)), exc("second", 42, 1, b, null))));
    }

    @Test void excellonRefusesFusionThatWouldExpandTheSelectedMachiningPaths() {
        var first = exc("first", 7, 0.8, null, drilling(7));
        var image = ExcellonImage.of("MM", Map.of(42, 0.8, 50, 1.0),
                List.of(new ExcellonImage.Drill(42, 2, 3), new ExcellonImage.Drill(50, 4, 5)), List.of(),
                FACTORY.createPoint(new Coordinate(2, 3)).buffer(0.4));
        var second = new ProjectFile.ExcellonEntry("second", image, null, null, true, true, false, Map.of(), drilling(50));
        assertThrows(IllegalArgumentException.class, () -> TclObjectJoin.excellon(List.of(first, second)));
        var aligned = new ProjectFile.ExcellonEntry("second", image, null, null, true, true, false, Map.of(),
                new DrillCncSettings(GCodePreprocessor.FX_PORTABLE, drilling(50).options(), List.of(42, 50), drilling(50).toolOrder()));
        assertEquals(List.of(1, 2), TclObjectJoin.excellon(List.of(first, aligned)).settings().selectedToolIds());
    }
}
