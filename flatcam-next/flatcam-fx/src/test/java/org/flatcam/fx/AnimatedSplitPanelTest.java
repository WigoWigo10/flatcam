package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class AnimatedSplitPanelTest {
    private static final class Fixture {
        final Region panel = new Region();
        final Region content = new Region();
        final SplitPane split;
        final StackPane root;
        final AtomicInteger completed = new AtomicInteger();
        final AnimatedSplitPanel animation;
        Fixture(boolean sidebar, boolean visible, Duration duration) {
            panel.setMinSize(160, 90);
            split = visible ? (sidebar ? new SplitPane(panel, content) : new SplitPane(content, panel))
                    : new SplitPane(content);
            split.setOrientation(sidebar ? Orientation.HORIZONTAL : Orientation.VERTICAL);
            root = new StackPane(split);
            new Scene(root, 900, 600);
            root.resize(900, 600); root.applyCss(); root.layout();
            split.setDividerPositions(sidebar ? 0.30 : 0.75); root.layout();
            animation = new AnimatedSplitPanel(split, panel, sidebar ? 0 : 1,
                    () -> sidebar ? 0.30 : 0.75, completed::incrementAndGet, duration);
        }
    }

    @Test void sidebarSlidesThroughIntermediatePositionsAndCompletelyDisappears() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(true, true, Duration.millis(180));
            Rectangle originalClip = new Rectangle(500, 500);
            fixture.panel.setClip(originalClip);
            fixture.animation.setVisible(false);
            try {
                assertTrue(fixture.animation.isAnimating());
                assertTrue(fixture.split.getItems().contains(fixture.panel));
                assertEquals(0, fixture.panel.getMinWidth());
                assertTrue(fixture.panel.isMouseTransparent());
                currentTimeline(fixture.animation).jumpTo(Duration.millis(90));
                fixture.root.layout();
                double position = fixture.split.getDividerPositions()[0];
                assertTrue(position > 0 && position < 0.30, "must slide, not snap: " + position);
                assertTrue(fixture.panel.getWidth() < 160, "min-width cannot block collapse");
                Rectangle clip = (Rectangle) fixture.panel.getClip();
                assertEquals(fixture.panel.getWidth(), clip.getWidth());
                fixture.animation.finish();
                assertFalse(fixture.split.getItems().contains(fixture.panel));
                assertTrue(fixture.split.getDividers().isEmpty());
                assertEquals(160, fixture.panel.getMinWidth());
                assertSame(originalClip, fixture.panel.getClip());
                assertFalse(fixture.panel.isMouseTransparent());
                assertEquals(1, fixture.completed.get());
            } finally { fixture.animation.finish(); }
            return null;
        });
    }

    @Test void consoleRestoresItsRememberedHeightAndOriginalConstraints() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false, true, Duration.millis(180));
            fixture.animation.setVisible(false); fixture.animation.finish();
            assertEquals(1, fixture.split.getItems().size());
            fixture.animation.setVisible(true);
            try {
                assertEquals(2, fixture.split.getItems().size());
                assertEquals(0, fixture.panel.getMinHeight());
                currentTimeline(fixture.animation).jumpTo(Duration.millis(90));
                double position = fixture.split.getDividerPositions()[0];
                assertTrue(position > 0.75 && position < 1);
                fixture.animation.finish();
                assertEquals(0.75, fixture.split.getDividerPositions()[0], 0.01);
                assertEquals(90, fixture.panel.getMinHeight());
                assertNull(fixture.panel.getClip());
                assertFalse(fixture.panel.isMouseTransparent());
                assertEquals(2, fixture.completed.get());
            } finally { fixture.animation.finish(); }
            return null;
        });
    }

    @Test void rapidReversalStartsAtCurrentPositionAndCannotRunStaleCompletion() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(true, true, Duration.millis(180));
            fixture.animation.setVisible(false);
            try {
                Timeline closing = currentTimeline(fixture.animation);
                closing.jumpTo(Duration.millis(90));
                double currentPosition = fixture.split.getDividerPositions()[0];
                fixture.animation.setVisible(true);
                assertEquals(Animation.Status.STOPPED, closing.getStatus());
                assertEquals(currentPosition, fixture.split.getDividerPositions()[0], 0.001);
                closing.getOnFinished().handle(new ActionEvent());
                assertTrue(fixture.animation.isAnimating()); assertEquals(0, fixture.completed.get());
                fixture.animation.finish();
                assertTrue(fixture.split.getItems().contains(fixture.panel));
                assertEquals(0.30, fixture.split.getDividerPositions()[0], 0.01);
                assertEquals(160, fixture.panel.getMinWidth());
                assertEquals(1, fixture.completed.get());
                assertNull(fixture.panel.getClip());
            } finally { fixture.animation.finish(); }
            return null;
        });
    }

    @Test void startingCollapsedAndRepeatedRequestsDoNotDuplicateItemsOrCallbacks() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(false, false, Duration.ZERO);
            fixture.animation.setVisible(false); assertEquals(0, fixture.completed.get());
            for (int cycle = 0; cycle < 20; cycle++) {
                fixture.animation.setVisible(true); fixture.animation.setVisible(true);
                assertEquals(2, fixture.split.getItems().size());
                assertEquals(0.75, fixture.split.getDividerPositions()[0], 0.01);
                fixture.animation.setVisible(false); fixture.animation.setVisible(false);
                assertEquals(1, fixture.split.getItems().size());
                assertEquals(90, fixture.panel.getMinHeight()); assertNull(fixture.panel.getClip());
                assertFalse(fixture.animation.isAnimating());
            }
            assertEquals(40, fixture.completed.get());
            return null;
        });
    }

    @Test void forcedCompletionUsesLatestMonitorTargetAndPreservesMouseState() throws Exception {
        TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(true, false, Duration.ZERO);
            SimpleDoubleProperty target = new SimpleDoubleProperty(0.28);
            fixture.panel.setMouseTransparent(true);
            var animation = new AnimatedSplitPanel(fixture.split, fixture.panel, 0, target::get, () -> { });
            animation.setVisible(true);
            target.set(0.40);
            animation.finish();
            assertEquals(0.40, fixture.split.getDividerPositions()[0], 0.01);
            assertTrue(fixture.panel.isMouseTransparent());
            assertEquals(160, fixture.panel.getMinWidth()); assertNull(fixture.panel.getClip());
            return null;
        });
    }

    @Test void realTimelineCompletesWithoutBlockingTheFxThread() throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        AtomicInteger changes = new AtomicInteger();
        AnimatedSplitPanel animation = TerminalPanelTest.fx(() -> {
            Fixture fixture = new Fixture(true, true, Duration.ZERO);
            fixture.split.getDividers().getFirst().positionProperty().addListener((o, a, b) -> changes.incrementAndGet());
            var animated = new AnimatedSplitPanel(fixture.split, fixture.panel, 0, () -> 0.30,
                    () -> completed.complete(null));
            animated.setVisible(false);
            assertTrue(animated.isAnimating()); assertFalse(completed.isDone());
            return animated;
        });
        try {
            assertTrue(TerminalPanelTest.fx(animation::isAnimating), "another FX event must run before completion");
            completed.get(5, TimeUnit.SECONDS);
            assertTrue(changes.get() > 2, "divider must pass through multiple frames");
            assertFalse(TerminalPanelTest.fx(animation::isAnimating));
        } finally { TerminalPanelTest.fx(() -> { animation.finish(); return null; }); }
    }

    @Test void mainWindowCannotSaveTransitionPositionsOrConfuseNestedDividers() throws Exception {
        JobExecutor jobs = new JobExecutor(1);
        try {
            TerminalPanelTest.fx(() -> {
                Fixture fixture = new Fixture(true, true, Duration.millis(180));
                MainWindow window = new MainWindow(jobs);
                field(MainWindow.class, "consoleCollapsed").setBoolean(window, false);
                field(MainWindow.class, "sidebarAnimation").set(window, fixture.animation);
                fixture.animation.setVisible(false);
                try {
                    // No SplitPane fields installed: a missing guard would throw before writing preferences.
                    window.saveSplitPositions();
                    Method saveSidebar = MainWindow.class.getDeclaredMethod("saveSidebarDividerPosition");
                    saveSidebar.setAccessible(true); saveSidebar.invoke(window);
                } finally { fixture.animation.finish(); }

                Region divider = new Region(); divider.getStyleClass().add("split-pane-divider");
                SplitPane outer = new SplitPane(new Region());
                StackPane owner = new StackPane(divider, fixture.split);
                outer.getItems().setAll(owner);
                StackPane nestedRoot = new StackPane(outer);
                new Scene(nestedRoot, 900, 600);
                nestedRoot.resize(900, 600); nestedRoot.applyCss(); nestedRoot.layout();
                Method identify = MainWindow.class.getDeclaredMethod("isDividerTarget", Object.class, SplitPane.class);
                identify.setAccessible(true);
                assertEquals(true, identify.invoke(null, divider, outer));
                assertEquals(false, identify.invoke(null, divider, fixture.split));
                fixture.animation.setVisible(true); fixture.animation.finish();
                nestedRoot.applyCss(); nestedRoot.layout();
                var nestedDivider = fixture.split.lookup(".split-pane-divider");
                assertNotNull(nestedDivider);
                assertEquals(true, identify.invoke(null, nestedDivider, fixture.split));
                assertEquals(false, identify.invoke(null, nestedDivider, outer));
                return null;
            });
        } finally { jobs.shutdown(); }
    }

    private static Timeline currentTimeline(AnimatedSplitPanel animation) throws Exception {
        return (Timeline) field(AnimatedSplitPanel.class, "animation").get(animation);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
