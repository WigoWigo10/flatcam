package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.cutout.CutoutGenerator;
import org.flatcam.cam.cutout.CutoutKind;
import org.flatcam.cam.cutout.CutoutParameters;
import org.flatcam.cam.cutout.CutoutShape;
import org.flatcam.cam.cutout.GapPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class CutoutCursorGapsTest {
    private static final GeometryFactory FACTORY = new GeometryFactory();

    @BeforeEach @AfterEach void defaults() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }

    @ParameterizedTest @ValueSource(doubles = {1, 0.03937007874015748})
    void cursorMaskRemovesGapPlusDiameterAndLeavesInternalProfilesClosed(double unit) {
        Geometry outer = FACTORY.toGeometry(new Envelope(0, 20 * unit, 0, 10 * unit));
        Geometry hole = FACTORY.toGeometry(new Envelope(8 * unit, 12 * unit, 3 * unit, 7 * unit));
        Geometry board = outer.difference(hole);
        Geometry mask = CutoutGapPlacementController.cursorMask(FACTORY, 10 * unit, -.5 * unit, unit, 2 * unit);
        assertEquals(3 * unit, mask.getEnvelopeInternal().getWidth(), 1e-12);
        var p = new CutoutParameters(unit, 0, false, CutoutKind.SINGLE, CutoutShape.FREEFORM,
                2 * unit, GapPattern.FOUR, true);
        var before = CutoutGenerator.generate("MM", board, new CutoutParameters(unit, 0, false,
                CutoutKind.SINGLE, CutoutShape.FREEFORM, 0, GapPattern.NONE, true));
        var result = CutoutGenerator.generate("MM", board, p, List.of(mask), CancellationToken.none());
        assertEquals(3 * unit, before.geometry().getLength() - result.geometry().getLength(), 1e-9);
        assertEquals(3 * unit, result.gapGeometry().getLength(), 1e-9);
        Geometry internal = hole.buffer(-.5 * unit).getBoundary();
        assertTrue(result.geometry().covers(internal), "cursor masks must not open internal cuts");
    }

    private static final class Session implements AutoCloseable {
        final JobExecutor jobs = new JobExecutor(1);
        final AtomicBoolean valid = new AtomicBoolean(true);
        final PlotAreaView plot;
        final CutoutGapPlacementController controller;
        final Geometry source = FACTORY.toGeometry(new Envelope(20, 80, 20, 60));
        final List<CutoutToolPanel.Result> generated = new ArrayList<>();
        final Node panel;
        Session() throws Exception {
            plot = TerminalPanelTest.fx(() -> {
                var p = new PlotAreaView(); p.resize(800, 500); p.layout(); p.setGridSnap(false, 1, 1); return p;
            });
            controller = new CutoutGapPlacementController(plot, jobs, "MM", source, valid::get);
            panel = TerminalPanelTest.fx(() -> {
                Node p = CutoutToolPanel.build("MM", (polygon, done, cancel) -> false, controller::cancel,
                        List::of, controller::begin, generated::add, controller::close);
                var root = new VBox(p); new Scene(root); root.applyCss(); root.layout();
                field(p, "cutout-tool-diameter").setText("1"); field(p, "cutout-margin").setText("0");
                field(p, "cutout-gap-size").setText("2"); return p;
            });
        }
        void start() throws Exception {
            TerminalPanelTest.fx(() -> { button(panel, "cutout-cursor-freeform").fire(); return null; });
            awaitWorker();
        }
        void awaitWorker() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                var handle = TerminalPanelTest.fx(() -> (JobHandle<?>) reflected(controller, "job"));
                if (handle == null) return; // Wait for the FX publication, not just the computation.
                handle.completion().get(10, TimeUnit.SECONDS);
                Thread.sleep(5);
            }
            fail("Cutout preview was not published");
        }
        void click(double x, double y, MouseButton button) throws Exception {
            TerminalPanelTest.fx(() -> {
                double sx = (x - 50) * 3 + (plot.getWidth() - 44) / 2 + 44;
                double sy = (plot.getHeight() - 20) / 2 - (y - 40) * 3 + 20;
                for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED))
                    plot.fireEvent(new MouseEvent(type, sx, sy, sx, sy, button, 1, false, false, false, false,
                            type == MouseEvent.MOUSE_PRESSED && button == MouseButton.PRIMARY, false,
                            type == MouseEvent.MOUSE_PRESSED && button == MouseButton.SECONDARY, false, false, true, null));
                return null;
            });
            awaitWorker();
        }
        String status() { return ((Label) panel.lookup("#cutout-manual-status")).getText(); }
        @Override public void close() throws Exception {
            TerminalPanelTest.fx(() -> { controller.close(); return null; }); jobs.shutdown();
        }
    }

    @Test void repeatedClicksKeepDraftUntilGenerateAndRightClickFinishes() throws Exception {
        try (var s = new Session()) {
            s.start();
            s.click(19.5, 35, MouseButton.PRIMARY);
            s.click(19.5, 45, MouseButton.PRIMARY);
            TerminalPanelTest.fx(() -> {
                assertTrue(s.plot.isPlacementActive()); assertTrue(s.generated.isEmpty());
                assertTrue(s.status().contains("2 gap"), s.status()); return null;
            });
            s.click(19.5, 45, MouseButton.SECONDARY);
            TerminalPanelTest.fx(() -> {
                assertFalse(s.plot.isPlacementActive());
                button(s.panel, "cutout-generate-rectangular").fire();
                assertTrue(s.generated.isEmpty());
                assertTrue(((Label) s.panel.lookup("#cutout-error")).getText().contains("mesmo contorno"));
                button(s.panel, "cutout-generate-freeform").fire();
                assertEquals(2, s.generated.getFirst().manualGapAreas().size());
                var r = s.generated.getFirst();
                var actual = CutoutGenerator.generate("MM", s.source, r.cutoutParams(), r.manualGapAreas(), CancellationToken.none());
                assertEquals(6, actual.gapGeometry().getLength(), 1e-9);
                return null;
            });
        }
    }

    @Test void missesAndDuplicateClicksDoNotAddGapsAndParameterChangesInvalidateCursorDraft() throws Exception {
        try (var s = new Session()) {
            s.start();
            s.click(50, 40, MouseButton.PRIMARY);
            TerminalPanelTest.fx(() -> { assertTrue(s.status().contains("Clique sobre"), s.status()); return null; });
            s.click(19.5, 35, MouseButton.PRIMARY);
            s.click(19.5, 35, MouseButton.PRIMARY);
            TerminalPanelTest.fx(() -> {
                assertTrue(s.status().contains("Clique sobre"));
                field(s.panel, "cutout-margin").setText("1");
                assertFalse(s.plot.isPlacementActive());
                assertNull(reflected(s.plot, "editorReferenceGeometry"));
                assertTrue(s.status().contains("gaps do cursor limpos"));
                button(s.panel, "cutout-generate-freeform").fire();
                assertTrue(s.generated.getFirst().manualGapAreas().isEmpty()); return null;
            });
        }
    }

    @Test void cursorUsesTheActualSnapToggleAndEscapeCancellationKeepsConfirmedDraft() throws Exception {
        TerminalPanelTest.fx(() -> {
            var plot = new PlotAreaView(); plot.resize(800, 500); plot.layout();
            var points = new ArrayList<Double>(); var previews = new ArrayList<Geometry>();
            var finished = new AtomicBoolean();
            assertTrue(plot.beginCursorPlacement(new PlotAreaView.CursorPlacementHandler() {
                public Geometry preview(double x, double y) {
                    Geometry area = CutoutGapPlacementController.cursorMask(FACTORY, x, y, 1, 2);
                    previews.add(area); return area;
                }
                public void onClick(double x, double y) { points.add(x); }
                public void onFinish() { finished.set(true); }
            }));
            for (boolean snap : new boolean[]{true, false}) {
                plot.setGridSnap(snap, 2, 2);
                for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED))
                    plot.fireEvent(new MouseEvent(type, 332.7, 232.2, 332.7, 232.2, MouseButton.PRIMARY, 1,
                            false, false, false, false, type == MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null));
                assertEquals(points.getLast(), previews.getLast().getCentroid().getX(), 1e-9);
            }
            assertEquals(0, points.getFirst() % 2); assertNotEquals(points.getFirst(), points.getLast());
            plot.cancelPlacement(); // MainWindow's Escape filter invokes this same method.
            assertTrue(finished.get()); assertFalse(plot.isPlacementActive()); return null;
        });
    }

    @Test void closedToolRejectsCompletionAlreadyQueuedByTheWorker() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                button(s.panel, "cutout-cursor-freeform").fire();
                var h = (JobHandle<?>) reflected(s.controller, "job");
                h.completion().get(10, TimeUnit.SECONDS);
                s.controller.close(); return null;
            });
            TerminalPanelTest.fx(() -> {
                assertNull(reflected(s.plot, "editorReferenceGeometry"));
                assertFalse(s.plot.isPlacementActive()); assertTrue(s.generated.isEmpty()); return null;
            });
        }
    }

    @Test void changedSourceDiscardsQueuedPreviewAndExplainsWhy() throws Exception {
        try (var s = new Session()) {
            TerminalPanelTest.fx(() -> {
                button(s.panel, "cutout-cursor-freeform").fire();
                var h = (JobHandle<?>) reflected(s.controller, "job");
                h.completion().get(10, TimeUnit.SECONDS);
                s.valid.set(false); return null;
            });
            s.awaitWorker();
            TerminalPanelTest.fx(() -> {
                assertNull(reflected(s.plot, "editorReferenceGeometry"));
                assertFalse(s.plot.isPlacementActive()); assertTrue(s.status().contains("Origem alterada")); return null;
            });
        }
    }

    @Test void cursorGapOnPanelDoesNotOpenTheOtherBoard() {
        Geometry first = FACTORY.toGeometry(new Envelope(0, 20, 0, 10));
        Geometry second = FACTORY.toGeometry(new Envelope(25, 45, 0, 10));
        Geometry panel = FACTORY.buildGeometry(List.of(first, second));
        var p = new CutoutParameters(1, 0, false, CutoutKind.PANEL, CutoutShape.FREEFORM, 2, GapPattern.NONE);
        Geometry mask = CutoutGapPlacementController.cursorMask(FACTORY, 10, -.5, 1, 2);
        var result = CutoutGenerator.generate("MM", panel, p, List.of(mask), CancellationToken.none());
        Geometry other = CutoutGenerator.generate("MM", second, p).geometry();
        assertTrue(result.geometry().covers(other));
        assertEquals(3, result.gapGeometry().getLength(), 1e-9);
    }

    private static TextField field(Node root, String id) { return (TextField) root.lookup("#" + id); }
    private static Button button(Node root, String id) { return (Button) root.lookup("#" + id); }
    private static Object reflected(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
}
