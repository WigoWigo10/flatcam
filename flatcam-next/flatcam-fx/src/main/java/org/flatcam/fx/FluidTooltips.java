package org.flatcam.fx;

import java.util.function.Supplier;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.Control;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * The application's tooltips: a short delay (almost none when moving between neighbouring controls), a fade and slide in,
 * a glide from one control to the next while a tip is already open, and a fade out. A tip has an optional bold title and a
 * wrapped body, in the colours of the theme.
 *
 * <p>It takes over the plain JavaFX {@link Tooltip}s of every control it meets: when the mouse enters a control that has
 * one, its text is moved here and the native tooltip is removed, so no panel has to change. A control (or a menu item) can
 * also carry a title and a text in its properties ({@link #TITLE_KEY}, {@link #TEXT_KEY}). Table and tree cells keep their
 * native tooltips, which they refresh on every update.
 */
final class FluidTooltips {

    static final String TITLE_KEY = "fx.tip.title";
    static final String TEXT_KEY = "fx.tip.text";
    private static final String INSTALLED_KEY = "fx.tip.installed";

    private static final double SHOW_DELAY_MS = 450;
    private static final double WARM_DELAY_MS = 60;
    private static final long WARM_WINDOW_NANOS = 700_000_000L;
    private static final double FADE_IN_MS = 150;
    private static final double GLIDE_MS = 170;
    private static final double FADE_OUT_MS = 90;
    private static final double MAX_WIDTH = 340;

    private final Scene scene;
    private final Supplier<ThemeOption> theme;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(3);
    private final Label title = new Label();
    private final Label body = new Label();
    private final PauseTransition delay = new PauseTransition();
    private Timeline motion;
    private FadeTransition fade;
    /** The node whose tip is open or waiting to open, and where the tip goes relative to it. */
    private Node owner;
    private boolean ownerBeside;
    private boolean open;
    private long lastClosedNanos = Long.MIN_VALUE / 2;
    /** For tests: the area tips are kept inside, instead of the monitor the node is on. */
    private Rectangle2D areaOverride;

    FluidTooltips(Scene scene, Supplier<ThemeOption> theme) {
        this.scene = scene;
        this.theme = theme;
        title.setWrapText(true);
        title.setMaxWidth(MAX_WIDTH);
        body.setWrapText(true);
        body.setMaxWidth(MAX_WIDTH);
        box.getChildren().addAll(title, body);
        box.setMouseTransparent(true);
        box.setMaxWidth(MAX_WIDTH + 24);
        popup.getContent().add(box);
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setConsumeAutoHidingEvents(false);
        popup.setHideOnEscape(false);
        // A tip left open when the window closes would be torn down by the toolkit's exit (a native crash was seen):
        // close it with the window.
        scene.windowProperty().addListener((observable, was, window) -> {
            if (window != null) {
                window.showingProperty().addListener((o, wasShowing, showing) -> {
                    if (!showing) {
                        closeNow();
                    }
                });
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, event -> {
            if (event.getTarget() instanceof Node node) {
                enter(ownerOf(node), false);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> enter(null, false));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> enter(null, false));
        scene.addEventFilter(ScrollEvent.SCROLL, event -> enter(null, false));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> enter(null, false));
    }

    /**
     * Gives the tips of a menu's items (and of its submenus) hover handlers when the menu opens: a menu is a window of its
     * own, so the scene filters above never see its items. Items carry their text in their properties.
     */
    void attachMenu(Menu menu) {
        menu.showingProperty().addListener((observable, was, now) -> {
            if (now) {
                for (MenuItem item : menu.getItems()) {
                    install(item);
                }
            } else {
                enter(null, false);
            }
        });
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu submenu) {
                attachMenu(submenu);
            }
        }
    }

    /** Context menus have a separate popup scene too; install after their item nodes exist. */
    void attachContextMenu(ContextMenu menu) {
        menu.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> menu.getItems().forEach(this::install));
        menu.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> closeNow());
    }

    private void install(MenuItem item) {
        if (!item.getProperties().containsKey(TEXT_KEY) || item.getProperties().containsKey(INSTALLED_KEY)) {
            return;
        }
        Node node = item.getStyleableNode();
        if (node == null) {
            return;
        }
        item.getProperties().put(INSTALLED_KEY, Boolean.TRUE);
        node.getProperties().put(TITLE_KEY, item.getProperties().get(TITLE_KEY));
        node.getProperties().put(TEXT_KEY, item.getProperties().get(TEXT_KEY));
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> enter(node, true));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (owner == node) {
                enter(null, false);
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> enter(null, false));
    }

    /** The nearest node at or above {@code node} with a tip; a plain JavaFX tooltip is adopted on the way. */
    private Node ownerOf(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getProperties().containsKey(TEXT_KEY)) {
                return current;
            }
            if (current instanceof Control control && !(control instanceof Cell<?>) && control.getTooltip() != null) {
                Tooltip tooltip = control.getTooltip();
                String text = tooltip.getText();
                if (text != null && !text.isBlank()) {
                    control.getProperties().put(TEXT_KEY, text);
                    control.setTooltip(null);
                    return control;
                }
            }
        }
        return null;
    }

    private void enter(Node node, boolean beside) {
        if (node == owner) {
            return;
        }
        owner = node;
        ownerBeside = beside;
        delay.stop();
        if (node == null) {
            close();
            return;
        }
        if (open) {
            glideTo(node, beside);
            return;
        }
        boolean warm = System.nanoTime() - lastClosedNanos < WARM_WINDOW_NANOS;
        delay.setDuration(Duration.millis(warm ? WARM_DELAY_MS : SHOW_DELAY_MS));
        delay.setOnFinished(event -> {
            if (owner == node) {
                openAt(node, beside);
            }
        });
        delay.playFromStart();
    }

    private void fill(Node node) {
        Object tipTitle = node.getProperties().get(TITLE_KEY);
        Object tipText = node.getProperties().get(TEXT_KEY);
        ThemeOption current = theme.get();
        Color text = Color.web(current.tooltipPalette().text());
        box.setStyle(current.objectTooltipStyle()
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0.1, 0, 3); -fx-padding: 7 11 8 11;");
        title.setText(tipTitle == null ? "" : tipTitle.toString());
        title.setVisible(tipTitle != null && !tipTitle.toString().isBlank());
        title.setManaged(title.isVisible());
        title.setTextFill(text);
        title.setStyle("-fx-font-weight: bold;");
        body.setText(tipText == null ? "" : tipText.toString());
        body.setTextFill(title.isVisible() ? text.deriveColor(0, 1, 1, 0.9) : text);
    }

    /** Where the tip's top-left corner goes: under the node (or above when there is no room), or beside it. */
    private double[] target(Node node, boolean beside) {
        Bounds bounds = node.localToScreen(node.getBoundsInLocal());
        if (bounds == null) {
            return null;
        }
        double width = Math.max(box.getWidth(), 140);
        double height = Math.max(box.getHeight(), 34);
        Rectangle2D area = areaOverride != null ? areaOverride
                : Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1).stream().findFirst()
                        .map(Screen::getVisualBounds).orElse(Screen.getPrimary().getVisualBounds());
        double x = beside ? bounds.getMaxX() + 6 : bounds.getMinX();
        double y = beside ? bounds.getMinY() : bounds.getMaxY() + 6;
        if (beside && x + width > area.getMaxX()) {
            x = bounds.getMinX() - width - 6;
        } else if (!beside && y + height > area.getMaxY()) {
            y = bounds.getMinY() - height - 6;
        }
        x = Math.max(area.getMinX() + 4, Math.min(x, area.getMaxX() - width - 4));
        y = Math.max(area.getMinY() + 4, Math.min(y, area.getMaxY() - height - 4));
        return new double[] {x, y};
    }

    private void openAt(Node node, boolean beside) {
        if (scene.getWindow() == null || !scene.getWindow().isShowing()) {
            return;
        }
        stopMotion();
        fill(node);
        double[] first = target(node, beside);
        if (first == null) {
            return;
        }
        box.setOpacity(0);
        box.setTranslateY(beside ? 0 : -6);
        box.setTranslateX(beside ? -6 : 0);
        open = true;
        popup.show(scene.getWindow(), first[0], first[1]);
        // The real size is only known once the popup has been laid out; settle the position while it is still invisible.
        Platform.runLater(() -> {
            if (owner != node || !open) {
                return;
            }
            double[] settled = target(node, beside);
            if (settled != null) {
                popup.setX(settled[0]);
                popup.setY(settled[1]);
            }
            motion = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(box.opacityProperty(), 0),
                            new KeyValue(box.translateYProperty(), beside ? 0 : -6),
                            new KeyValue(box.translateXProperty(), beside ? -6 : 0)),
                    new KeyFrame(Duration.millis(FADE_IN_MS),
                            new KeyValue(box.opacityProperty(), 1, Interpolator.EASE_OUT),
                            new KeyValue(box.translateYProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(box.translateXProperty(), 0, Interpolator.EASE_OUT)));
            motion.play();
        });
    }

    /** A tip is open and the mouse reached another control with one: slide there instead of closing and reopening. */
    private void glideTo(Node node, boolean beside) {
        stopMotion();
        fill(node);
        box.setOpacity(1);
        box.setTranslateX(0);
        box.setTranslateY(0);
        // The text changes at once; the box resizes with it and the window follows to the new spot.
        Platform.runLater(() -> {
            if (owner != node || !open) {
                return;
            }
            double[] to = target(node, beside);
            if (to == null) {
                return;
            }
            double fromX = popup.getX();
            double fromY = popup.getY();
            javafx.beans.property.DoubleProperty progress = new javafx.beans.property.SimpleDoubleProperty(0);
            progress.addListener((observable, was, now) -> {
                popup.setX(fromX + (to[0] - fromX) * now.doubleValue());
                popup.setY(fromY + (to[1] - fromY) * now.doubleValue());
            });
            box.setOpacity(0.55);
            motion = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(progress, 0), new KeyValue(box.opacityProperty(), 0.55)),
                    new KeyFrame(Duration.millis(GLIDE_MS), new KeyValue(progress, 1, Interpolator.EASE_BOTH),
                            new KeyValue(box.opacityProperty(), 1, Interpolator.EASE_OUT)));
            motion.play();
        });
    }

    private void close() {
        if (!open) {
            return;
        }
        open = false;
        lastClosedNanos = System.nanoTime();
        stopMotion();
        fade = new FadeTransition(Duration.millis(FADE_OUT_MS), box);
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            if (!open) {
                popup.hide();
            }
        });
        fade.play();
    }

    private void stopMotion() {
        if (motion != null) {
            motion.stop();
            motion = null;
        }
        if (fade != null) {
            fade.stop();
            fade = null;
        }
    }

    /** Closes the tip at once, with no animation (the window is going away). */
    void closeNow() {
        delay.stop();
        stopMotion();
        owner = null;
        open = false;
        popup.hide();
    }

    /** For tests: keep tips inside this area instead of the monitor of the node. */
    void setAreaForTests(Rectangle2D area) {
        this.areaOverride = area;
    }

    /** For tests: the tip window. */
    Popup popup() {
        return popup;
    }

    /** For tests: whether a tip is open (or fading in). */
    boolean isOpen() {
        return open;
    }

    /** Fires the event the scene filter reacts to, as if the mouse had entered {@code node}. */
    static void simulateEnter(Node node) {
        Event.fireEvent(node, new MouseEvent(MouseEvent.MOUSE_ENTERED_TARGET, 0, 0, 0, 0,
                javafx.scene.input.MouseButton.NONE, 0, false, false, false, false, false, false, false, true, false,
                false, null));
    }
}
