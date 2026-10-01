package org.flatcam.cam.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.analysis.RulesCheck.Named;
import org.flatcam.cam.analysis.RulesCheck.Rule;
import org.flatcam.cam.analysis.RulesCheck.RuleResult;
import org.flatcam.cam.analysis.RulesCheck.Setting;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.gerber.GerberParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesCheckTest {

    @TempDir
    Path directory;

    private int counter;

    /** A Gerber in millimetres (format 2.4: 10000 units = 1 mm) with the given body lines. */
    private GerberImage gerber(String... body) throws IOException {
        Path file = directory.resolve("layer" + (counter++) + ".gbr");
        Files.writeString(file, "%FSLAX24Y24*%\n%MOMM*%\n" + String.join("\n", body) + "\nM02*\n");
        return new GerberParser().parse(file);
    }

    private static Named<GerberImage> named(String name, GerberImage image) {
        return new Named<>(name, image);
    }

    private static RulesCheck.Board board(Named<GerberImage> copperTop, Named<GerberImage> outline,
                                          Named<ExcellonImage> drills) {
        return new RulesCheck.Board(copperTop, null, null, null, null, null, outline, drills, null);
    }

    private static List<RuleResult> run(Rule rule, double value, RulesCheck.Board board) {
        Map<Rule, Setting> settings = new EnumMap<>(Rule.class);
        settings.put(rule, new Setting(true, value));
        return RulesCheck.check(board, settings, CancellationToken.none(), ProgressCallback.none());
    }

    private static RuleResult only(Rule rule, double value, RulesCheck.Board board) {
        List<RuleResult> results = run(rule, value, board);
        assertEquals(1, results.size());
        return results.get(0);
    }

    private static Named<ExcellonImage> drills(String name, Map<Integer, Double> tools, ExcellonImage.Drill... drills) {
        return new Named<>(name, ExcellonImage.of("MM", tools, List.of(drills), List.of(), null));
    }

    @Test
    void thinTracesAreFlaggedWithTheirSizes() throws Exception {
        GerberImage copper = gerber("%ADD10C,0.2*%", "%ADD11C,0.5*%", "D10*", "X0Y0D02*", "X100000Y0D01*",
                "D11*", "X200000Y100000D03*");
        RuleResult result = only(Rule.TRACE_SIZE, 0.25, board(named("top", copper), null, null));
        assertTrue(result.failed());
        assertEquals(List.of(0.2), result.sizes());
        assertFalse(result.points().isEmpty());
        assertFalse(only(Rule.TRACE_SIZE, 0.1, board(named("top", copper), null, null)).failed());
    }

    @Test
    void copperPiecesCloserThanTheLimitAreFlaggedAtTheMiddleOfTheGap() throws Exception {
        // Pads 1.0 wide whose edges are 0.1 apart, and a third one far away.
        GerberImage copper = gerber("%ADD10R,1.0X1.0*%", "D10*", "X0Y0D03*", "X11000Y0D03*", "X100000Y0D03*");
        RuleResult result = only(Rule.COPPER_TO_COPPER, 0.25, board(named("top", copper), null, null));
        assertTrue(result.failed());
        assertEquals(1, result.points().size());
        assertEquals(0.55, result.points().get(0).x, 1e-6);
        assertFalse(only(Rule.COPPER_TO_COPPER, 0.05, board(named("top", copper), null, null)).failed());
        // Touching pads are one piece: nothing to compare.
        GerberImage touching = gerber("%ADD10R,1.0X1.0*%", "D10*", "X0Y0D03*", "X10000Y0D03*");
        RuleResult single = only(Rule.COPPER_TO_COPPER, 0.25, board(named("top", touching), null, null));
        assertFalse(single.failed());
        assertNotNull(single.note());
    }

    @Test
    void copperTooCloseToTheOutlineIsFlaggedAndAMissingObjectIsExplained() throws Exception {
        GerberImage copper = gerber("%ADD10R,1.0X1.0*%", "D10*", "X20000Y20000D03*");
        GerberImage outline = gerber("%ADD11C,0.1*%", "D11*", "X0Y0D02*", "X40000Y0D01*", "X40000Y40000D01*",
                "X0Y40000D01*", "X0Y0D01*");
        RulesCheck.Board board = board(named("top", copper), named("edge", outline), null);
        assertTrue(only(Rule.COPPER_TO_OUTLINE, 1.5, board).failed());
        assertFalse(only(Rule.COPPER_TO_OUTLINE, 0.2, board).failed());
        RuleResult missing = only(Rule.COPPER_TO_OUTLINE, 1.0, board(named("top", copper), null, null));
        assertNotNull(missing.error());
        assertFalse(missing.ran());
        assertFalse(missing.failed());
    }

    @Test
    void annularRingNeedsMoreCopperAroundEachHole() throws Exception {
        GerberImage copper = gerber("%ADD10C,2.0*%", "D10*", "X0Y0D03*", "X100000Y0D03*", "X200000Y0D03*");
        // Ring 0.1 at x=0 (hole 1.8), 0.5 at x=10 (hole 1.0), and a hole bigger than its pad at x=20 (3.0).
        Named<ExcellonImage> holes = drills("drl", Map.of(1, 1.8, 2, 1.0, 3, 3.0), new ExcellonImage.Drill(1, 0, 0),
                new ExcellonImage.Drill(2, 10, 0), new ExcellonImage.Drill(3, 20, 0));
        RuleResult result = only(Rule.ANNULAR_RING, 0.3, board(named("top", copper), null, holes));
        assertTrue(result.failed());
        assertEquals(2, result.points().size());
        assertEquals(0.0, result.points().get(0).x, 1.0);
        assertEquals(20.0, result.points().get(1).x, 1e-6);
    }

    @Test
    void holesTooCloseAndHolesTooSmallAreFlagged() {
        Named<ExcellonImage> holes = drills("drl", Map.of(1, 1.0, 2, 0.2), new ExcellonImage.Drill(1, 0, 0),
                new ExcellonImage.Drill(1, 1.2, 0), new ExcellonImage.Drill(2, 50, 0));
        RulesCheck.Board board = board(null, null, holes);
        RuleResult close = only(Rule.HOLE_TO_HOLE, 0.3, board);
        assertTrue(close.failed());
        assertEquals(1, close.points().size());
        assertEquals(0.6, close.points().get(0).x, 1e-6);
        assertFalse(only(Rule.HOLE_TO_HOLE, 0.1, board).failed());
        RuleResult size = only(Rule.HOLE_SIZE, 0.3, board);
        assertEquals(List.of(0.2), size.sizes());
        assertFalse(only(Rule.HOLE_SIZE, 0.1, board).failed());
    }

    @Test
    void theSolderMaskSliverRuleHasItsOwnSwitch() throws Exception {
        GerberImage mask = gerber("%ADD10R,1.0X1.0*%", "D10*", "X0Y0D03*", "X11000Y0D03*");
        RulesCheck.Board board = new RulesCheck.Board(null, null, null, null, named("mask", mask), null, null, null, null);
        Map<Rule, Setting> settings = new EnumMap<>(Rule.class);
        settings.put(Rule.SILK_TO_SILK, new Setting(false, 0.25));
        settings.put(Rule.MASK_SLIVER, new Setting(true, 0.25));
        List<RuleResult> results = RulesCheck.check(board, settings, CancellationToken.none(), ProgressCallback.none());
        assertEquals(1, results.size());
        assertEquals(Rule.MASK_SLIVER, results.get(0).rule());
        assertTrue(results.get(0).failed());
    }

    @Test
    void defaultsMatchPythonAndBadValuesOrCancellationAreReported() {
        Map<Rule, Setting> defaults = RulesCheck.defaults();
        assertEquals(10, defaults.size());
        assertEquals(0.25, defaults.get(Rule.TRACE_SIZE).value());
        assertEquals(1.0, defaults.get(Rule.COPPER_TO_OUTLINE).value());
        assertEquals(0.3, defaults.get(Rule.ANNULAR_RING).value());
        assertTrue(defaults.values().stream().allMatch(Setting::enabled));
        Map<Rule, Setting> settings = new EnumMap<>(Rule.class);
        settings.put(Rule.HOLE_SIZE, new Setting(false, 0.3));
        settings.put(Rule.HOLE_TO_HOLE, new Setting(true, -1));
        List<RuleResult> results = RulesCheck.check(board(null, null, null), settings, CancellationToken.none(),
                ProgressCallback.none());
        assertEquals(1, results.size());
        assertNotNull(results.get(0).error());
        assertThrows(CancellationException.class, () -> RulesCheck.check(board(null, null, null), RulesCheck.defaults(),
                () -> true, ProgressCallback.none()));
    }
}
