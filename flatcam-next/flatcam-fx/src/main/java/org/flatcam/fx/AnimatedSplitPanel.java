package org.flatcam.fx;

import java.util.function.DoubleSupplier;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** Slides a leading/trailing panel without persisting transient divider positions. FX-thread only. */
final class AnimatedSplitPanel {
    private final SplitPane split;
    private final Region panel;
    private final int index;
    private final DoubleSupplier expandedPosition;
    private final Runnable settled;
    private final Duration duration;
    private Timeline animation;
    private boolean targetVisible;
    private boolean prepared;
    private double originalMinimum;
    private Node originalClip;
    private boolean originalMouseTransparent;

    AnimatedSplitPanel(SplitPane split, Region panel, int index,
                       DoubleSupplier expandedPosition, Runnable settled) {
        this(split, panel, index, expandedPosition, settled, Duration.millis(180));
    }

    AnimatedSplitPanel(SplitPane split, Region panel, int index,
                       DoubleSupplier expandedPosition, Runnable settled, Duration duration) {
        if (index != 0 && index != 1) throw new IllegalArgumentException("Two-pane index must be 0 or 1.");
        this.split = split;
        this.panel = panel;
        this.index = index;
        this.expandedPosition = expandedPosition;
        this.settled = settled;
        this.duration = duration;
        targetVisible = split.getItems().contains(panel);
    }

    boolean isAnimating() { return animation != null; }

    void setVisible(boolean visible) {
        if (targetVisible == visible) return;
        targetVisible = visible;
        if (animation != null) animation.stop();
        prepare();

        Timeline next = new Timeline();
        animation = next; // guards persistence before inserting/repositioning a divider
        if (!split.getItems().contains(panel)) {
            split.getItems().add(index, panel);
            split.setDividerPositions(hiddenPosition());
        }
        double start = split.getDividerPositions()[0];
        double target = visible ? boundedExpandedPosition() : hiddenPosition();
        var position = split.getDividers().getFirst().positionProperty();
        next.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(position, start)),
                new KeyFrame(duration, new KeyValue(position, target, Interpolator.EASE_BOTH)));
        next.setOnFinished(event -> { if (animation == next) finish(); });
        if (duration.lessThanOrEqualTo(Duration.ZERO)) finish();
        else next.play();
    }

    /** A divider grab/screen change may finish immediately; it never leaves temporary constraints behind. */
    void finish() {
        if (animation == null) return;
        animation.stop();
        if (targetVisible) split.setDividerPositions(boundedExpandedPosition());
        else split.getItems().remove(panel);
        restore();
        animation = null;
        settled.run();
    }

    private double hiddenPosition() { return index == 0 ? 0 : 1; }

    private double boundedExpandedPosition() {
        double value = expandedPosition.getAsDouble();
        return Double.isFinite(value) ? Math.clamp(value, 0.01, 0.99) : (index == 0 ? 0.22 : 0.75);
    }

    private void prepare() {
        if (prepared) return; // reversing keeps the original constraints, not the temporary ones
        prepared = true;
        boolean horizontal = split.getOrientation() == Orientation.HORIZONTAL;
        originalMinimum = horizontal ? panel.getMinWidth() : panel.getMinHeight();
        originalClip = panel.getClip();
        originalMouseTransparent = panel.isMouseTransparent();
        if (horizontal) panel.setMinWidth(0); else panel.setMinHeight(0);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(panel.widthProperty());
        clip.heightProperty().bind(panel.heightProperty());
        panel.setClip(clip);
        panel.setMouseTransparent(true);
    }

    private void restore() {
        if (!prepared) return;
        if (split.getOrientation() == Orientation.HORIZONTAL) panel.setMinWidth(originalMinimum);
        else panel.setMinHeight(originalMinimum);
        if (panel.getClip() instanceof Rectangle clip) {
            clip.widthProperty().unbind(); clip.heightProperty().unbind();
        }
        panel.setClip(originalClip);
        panel.setMouseTransparent(originalMouseTransparent);
        prepared = false;
    }
}
