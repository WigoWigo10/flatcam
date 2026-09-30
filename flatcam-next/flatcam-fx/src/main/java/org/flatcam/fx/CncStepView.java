package org.flatcam.fx;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.flatcam.cam.gcode.GCodeToolpathParser.PathMark;
import org.flatcam.cam.gcode.GCodeToolpathParser.PathStep;

/**
 * Step-by-step reading of a CNC Job's route (not in the Python app): click a number or a
 * route line and that leg, the one before it and the one after it are lit up while the
 * rest of the plot fades, so the order the machine follows can be walked one leg at a time.
 * Owns the steps, the selection, hit testing and drawing; {@link PlotAreaView} only routes
 * clicks, keys and the redraw to it.
 */
final class CncStepView {

    /** The current view transform, in the plot canvas' own coordinates. */
    record View(double centerX, double centerY, double scale, double contentWidth, double contentHeight,
                double offsetX, double offsetY) {
        double sx(double worldX) {
            return (worldX - centerX) * scale + contentWidth / 2.0 + offsetX;
        }

        double sy(double worldY) {
            return contentHeight / 2.0 - (worldY - centerY) * scale + offsetY;
        }
    }

    private static final Color SELECTED_DARK_BG = Color.web("#FFD600");
    private static final Color PREVIOUS_DARK_BG = Color.web("#29B6F6");
    private static final Color NEXT_DARK_BG = Color.web("#FF7043");
    private static final Color SELECTED_LIGHT_BG = Color.web("#D98200");
    private static final Color PREVIOUS_LIGHT_BG = Color.web("#0277BD");
    private static final Color NEXT_LIGHT_BG = Color.web("#C62828");
    private static final double MARK_HIT_PX = 14;
    private static final double LINE_HIT_PX = 8;
    private static final Font HUD_FONT = Font.font("System", FontWeight.BOLD, 14);
    private static final Color HUD_FILL = Color.web("#1E252B", 0.95);
    private static final Color HUD_TEXT = Color.web("#ECEFF1");
    private static final Font BADGE_FONT = Font.font("System", FontWeight.BOLD, 11);

    private record Layer(List<PathStep> steps, Map<Integer, double[]> markPositions, double widthWorld,
                         String units) {
    }

    private final Map<Object, Layer> layers = new LinkedHashMap<>();
    private Object selectedKey;
    private int selectedIndex = -1;

    boolean isEmpty() {
        return layers.isEmpty();
    }

    boolean hasSelection() {
        return selectedKey != null && selectedIndex >= 0;
    }

    Object selectedKey() {
        return selectedKey;
    }

    /** Replaces (or, with no steps, removes) the route of one CNC Job. Returns true if the selection changed. */
    boolean set(Object key, List<PathStep> steps, List<PathMark> marks, double widthWorld, String units) {
        if (steps == null || steps.isEmpty()) {
            layers.remove(key);
        } else {
            Map<Integer, double[]> positions = new HashMap<>();
            for (PathMark mark : marks) {
                positions.put(mark.sequence(), new double[]{mark.x(), mark.y()});
            }
            layers.put(key, new Layer(List.copyOf(steps), positions, widthWorld, units));
        }
        if (key.equals(selectedKey)) {
            Layer layer = layers.get(key);
            if (layer == null || selectedIndex >= layer.steps().size()) {
                selectedKey = null;
                selectedIndex = -1;
                return true;
            }
        }
        return false;
    }

    void clear() {
        layers.clear();
        selectedKey = null;
        selectedIndex = -1;
    }

    void clearSelection() {
        selectedKey = null;
        selectedIndex = -1;
    }

    void select(Object key, int index) {
        Layer layer = layers.get(key);
        if (layer == null || index < 0 || index >= layer.steps().size()) {
            return;
        }
        selectedKey = key;
        selectedIndex = index;
    }

    /** Moves the selection to the previous (-1) or next (+1) leg; false when there is nothing to move. */
    boolean stepBy(int delta) {
        if (!hasSelection()) {
            return false;
        }
        int size = layers.get(selectedKey).steps().size();
        selectedIndex = Math.max(0, Math.min(size - 1, selectedIndex + delta));
        return true;
    }

    /** Text for a status line: which leg, what kind, between which numbers, how long. */
    String describe() {
        if (!hasSelection()) {
            return "";
        }
        Layer layer = layers.get(selectedKey);
        PathStep step = layer.steps().get(selectedIndex);
        String kind = step.travel() ? "Deslocamento" : step.xy().length == 2 ? "Furo" : "Corte";
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "Passo %d/%d - %s",
                selectedIndex + 1, layer.steps().size(), kind));
        if (step.fromMark() > 0 || step.toMark() > 0) {
            text.append(" (");
            text.append(step.fromMark() > 0 ? Integer.toString(step.fromMark()) : "-");
            if (step.xy().length > 2) {
                text.append(" > ").append(step.toMark() > 0 ? Integer.toString(step.toMark()) : "-");
            }
            text.append(')');
        }
        if (step.length() > 0) {
            text.append(String.format(Locale.ROOT, " - %.3f %s", step.length(), layer.units().toLowerCase(Locale.ROOT)));
        }
        double total = layer.steps().get(layer.steps().size() - 1).endMinutes();
        if (!Double.isNaN(step.endMinutes()) && !Double.isNaN(total)) {
            text.append(" - ").append(clock(step.endMinutes())).append(" / ").append(clock(total));
        }
        return text.toString();
    }

    /** Minutes as m:ss (h:mm:ss from an hour up). */
    static String clock(double minutes) {
        long seconds = Math.round(minutes * 60);
        long hours = seconds / 3600;
        long rest = seconds % 3600;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, rest / 60, rest % 60)
                : String.format(Locale.ROOT, "%d:%02d", rest / 60, rest % 60);
    }

    /** True when the lit leg is the last one of the route. */
    boolean atLastStep() {
        return hasSelection() && selectedIndex >= layers.get(selectedKey).steps().size() - 1;
    }

    /** Bounding box of the lit leg (world units), or null when nothing is lit. */
    org.locationtech.jts.geom.Envelope selectedBounds() {
        if (!hasSelection()) {
            return null;
        }
        double[] xy = layers.get(selectedKey).steps().get(selectedIndex).xy();
        org.locationtech.jts.geom.Envelope box = new org.locationtech.jts.geom.Envelope();
        for (int i = 0; i + 1 < xy.length; i += 2) {
            box.expandToInclude(xy[i], xy[i + 1]);
        }
        return box;
    }

    /** Number of legs in the lit job's route. */
    int stepCount() {
        return hasSelection() ? layers.get(selectedKey).steps().size() : 0;
    }

    /** What the play button and the speed button show; set by the plot that owns the timer. */
    void setWalkState(boolean playing, String speedLabel) {
        this.playing = playing;
        this.speedLabel = speedLabel;
    }

    private boolean playing;
    private String speedLabel = "1x";

    /**
     * Selects what lies under {@code (worldX, worldY)}: a number first, else the nearest route
     * line. {@code scale} is pixels per world unit. Returns true when something was hit.
     */
    boolean hit(double worldX, double worldY, double scale) {
        Object bestKey = null;
        int bestIndex = -1;
        double bestDistance = MARK_HIT_PX / scale;
        for (Map.Entry<Object, Layer> entry : layers.entrySet()) {
            Layer layer = entry.getValue();
            for (Map.Entry<Integer, double[]> mark : layer.markPositions().entrySet()) {
                double d = Math.hypot(mark.getValue()[0] - worldX, mark.getValue()[1] - worldY);
                if (d <= bestDistance) {
                    int index = stepStartingAt(layer, mark.getKey());
                    if (index >= 0) {
                        bestDistance = d;
                        bestKey = entry.getKey();
                        bestIndex = index;
                    }
                }
            }
        }
        if (bestKey == null) {
            bestDistance = LINE_HIT_PX / scale;
            for (Map.Entry<Object, Layer> entry : layers.entrySet()) {
                List<PathStep> steps = entry.getValue().steps();
                for (int i = 0; i < steps.size(); i++) {
                    double d = distanceToStep(steps.get(i).xy(), worldX, worldY);
                    if (d <= bestDistance) {
                        bestDistance = d;
                        bestKey = entry.getKey();
                        bestIndex = i;
                    }
                }
            }
        }
        if (bestKey == null) {
            return false;
        }
        select(bestKey, bestIndex);
        return true;
    }

    private static int stepStartingAt(Layer layer, int mark) {
        int endsHere = -1;
        List<PathStep> steps = layer.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).fromMark() == mark) {
                return i;
            }
            if (endsHere < 0 && steps.get(i).toMark() == mark) {
                endsHere = i;
            }
        }
        return endsHere;
    }

    private static double distanceToStep(double[] xy, double px, double py) {
        if (xy.length == 2) {
            return Math.hypot(xy[0] - px, xy[1] - py);
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 3 < xy.length; i += 2) {
            best = Math.min(best, distanceToSegment(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], px, py));
        }
        return best;
    }

    private static double distanceToSegment(double ax, double ay, double bx, double by, double px, double py) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /**
     * Role of each mark number of the selected job's three lit legs: 0 selected, 1 previous,
     * 2 next (the selected leg wins where they share a number). Empty when nothing is selected.
     */
    Map<Integer, Integer> markRoles(Object key) {
        Map<Integer, Integer> roles = new HashMap<>();
        if (!hasSelection() || !selectedKey.equals(key)) {
            return roles;
        }
        List<PathStep> steps = layers.get(key).steps();
        int[] order = {2, 1, 0};
        for (int role : order) {
            int index = selectedIndex + (role == 1 ? -1 : role == 2 ? 1 : 0);
            if (index >= 0 && index < steps.size()) {
                PathStep step = steps.get(index);
                if (step.fromMark() > 0) {
                    roles.put(step.fromMark(), role);
                }
                if (step.toMark() > 0) {
                    roles.put(step.toMark(), role);
                }
            }
        }
        return roles;
    }

    private static boolean lightBackground(Color background) {
        return 0.2126 * background.getRed() + 0.7152 * background.getGreen() + 0.0722 * background.getBlue() > 0.55;
    }

    /** Selected leg, previous leg and next leg colours: brighter on dark plots, deeper on light ones. */
    static Color roleColor(int role, Color background) {
        boolean light = lightBackground(background);
        return role == 0 ? (light ? SELECTED_LIGHT_BG : SELECTED_DARK_BG)
                : role == 1 ? (light ? PREVIOUS_LIGHT_BG : PREVIOUS_DARK_BG)
                : (light ? NEXT_LIGHT_BG : NEXT_DARK_BG);
    }

    /** Draws the three lit legs and their direction arrows; call after dimming the rest of the plot. */
    void drawLegs(GraphicsContext gc, View view, Color background) {
        if (!hasSelection()) {
            return;
        }
        Layer layer = layers.get(selectedKey);
        double body = Math.max(2, layer.widthWorld() * view.scale());
        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        int[] order = {1, 2, 0}; // the selected leg on top
        for (int role : order) {
            int index = selectedIndex + (role == 1 ? -1 : role == 2 ? 1 : 0);
            if (index < 0 || index >= layer.steps().size()) {
                continue;
            }
            PathStep step = layer.steps().get(index);
            double width = role == 0 ? Math.max(5, body + 3) : Math.max(3.5, body + 1.5);
            drawLeg(gc, view, step, roleColor(role, background), background, width);
        }
        gc.restore();
    }

    private static void drawLeg(GraphicsContext gc, View view, PathStep step, Color color, Color background,
                                double width) {
        double[] xy = step.xy();
        if (xy.length == 2) {
            double x = view.sx(xy[0]);
            double y = view.sy(xy[1]);
            double radius = Math.max(6, width / 2 + 2);
            gc.setFill(background);
            gc.fillOval(x - radius - 2, y - radius - 2, (radius + 2) * 2, (radius + 2) * 2);
            gc.setStroke(color);
            gc.setLineWidth(3);
            gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
            gc.setFill(color);
            gc.fillOval(x - 2.5, y - 2.5, 5, 5);
            return;
        }
        for (int pass = 0; pass < 2; pass++) {
            gc.setStroke(pass == 0 ? background : color);
            gc.setLineWidth(pass == 0 ? width + 4 : width);
            if (step.travel() && pass == 1) {
                gc.setLineDashes(9, 6);
            } else {
                gc.setLineDashes();
            }
            gc.beginPath();
            gc.moveTo(view.sx(xy[0]), view.sy(xy[1]));
            for (int i = 2; i + 1 < xy.length; i += 2) {
                gc.lineTo(view.sx(xy[i]), view.sy(xy[i + 1]));
            }
            gc.stroke();
        }
        gc.setLineDashes();
        if (!step.travel() && width >= 9) {
            // A wide cutter hides its passes: trace them thinly over the body.
            gc.setStroke(color.deriveColor(0, 1, 0.55, 0.9));
            gc.setLineWidth(1.25);
            gc.beginPath();
            gc.moveTo(view.sx(xy[0]), view.sy(xy[1]));
            for (int i = 2; i + 1 < xy.length; i += 2) {
                gc.lineTo(view.sx(xy[i]), view.sy(xy[i + 1]));
            }
            gc.stroke();
        }
        drawDirection(gc, view, xy, color, background);
    }

    /** Arrowheads along a leg, one about every 56 px and always at least one, pointing the way it is walked. */
    private static void drawDirection(GraphicsContext gc, View view, double[] xy, Color color, Color background) {
        double total = 0;
        for (int i = 0; i + 3 < xy.length; i += 2) {
            total += Math.hypot(view.sx(xy[i + 2]) - view.sx(xy[i]), view.sy(xy[i + 3]) - view.sy(xy[i + 1]));
        }
        // About one arrow per 56 px, but never more than a couple of dozen on a spiral of passes.
        double spacing = Math.max(56, total / 24);
        double carried = spacing / 2;
        boolean drawn = false;
        double longestLength = -1;
        double longestX = 0;
        double longestY = 0;
        double longestUx = 1;
        double longestUy = 0;
        for (int i = 0; i + 3 < xy.length; i += 2) {
            double ax = view.sx(xy[i]);
            double ay = view.sy(xy[i + 1]);
            double bx = view.sx(xy[i + 2]);
            double by = view.sy(xy[i + 3]);
            double length = Math.hypot(bx - ax, by - ay);
            if (length < 1e-6) {
                continue;
            }
            double ux = (bx - ax) / length;
            double uy = (by - ay) / length;
            if (length > longestLength) {
                longestLength = length;
                longestX = (ax + bx) / 2;
                longestY = (ay + by) / 2;
                longestUx = ux;
                longestUy = uy;
            }
            double at = spacing - carried;
            while (at <= length) {
                arrowHead(gc, ax + ux * at, ay + uy * at, ux, uy, color, background);
                drawn = true;
                at += spacing;
            }
            carried = (carried + length) % spacing;
        }
        if (!drawn && longestLength > 0) {
            arrowHead(gc, longestX, longestY, longestUx, longestUy, color, background);
        }
    }

    private static void arrowHead(GraphicsContext gc, double x, double y, double ux, double uy, Color color,
                                  Color background) {
        double px = -uy;
        double py = ux;
        double[] xs = {x + ux * 7, x - ux * 5 + px * 5, x - ux * 5 - px * 5};
        double[] ys = {y + uy * 7, y - uy * 5 + py * 5, y - uy * 5 - py * 5};
        gc.setLineWidth(2);
        gc.setStroke(background);
        gc.strokePolygon(xs, ys, 3);
        gc.setFill(color);
        gc.fillPolygon(xs, ys, 3);
    }

    /** A number badge in the color of its leg's role. */
    static void drawRoleBadge(GraphicsContext gc, double x, double y, String text, int role, Color background) {
        double textWidth = text.length() * 7;
        double badgeX = x + 5;
        double badgeY = y - 5 - 16;
        gc.setFill(roleColor(role, background));
        gc.fillRoundRect(badgeX, badgeY, textWidth + 8, 16, 8, 8);
        gc.setLineWidth(1.5);
        gc.setStroke(background);
        gc.strokeRoundRect(badgeX, badgeY, textWidth + 8, 16, 8, 8);
        gc.setFont(BADGE_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(lightBackground(background) ? Color.WHITE : Color.web("#1B1B1B"));
        gc.fillText(text, badgeX + 4, badgeY + 12);
        gc.setFill(roleColor(role, background));
        gc.fillOval(x - 3, y - 3, 6, 6);
    }

    /** HUD button ids returned by {@link #hudAction}, in the order the buttons are laid out. */
    static final int HUD_NONE = 0;
    static final int HUD_PREVIOUS = 1;
    static final int HUD_PLAY = 2;
    static final int HUD_NEXT = 3;
    static final int HUD_SPEED = 4;
    static final int HUD_CLEAR = 5;
    static final int HUD_PANEL = 6;
    private static final int HUD_BUTTONS = 5;

    /** Where the caption and its buttons were last drawn (x, y, width, height), for clicks; null when hidden. */
    private double[] hudPanel;
    private double[][] hudButtons;

    /**
     * What a click at canvas position (x, y) lands on: one of the buttons, the caption itself
     * (which swallows the click so it does not pan or select behind it) or nothing.
     */
    int hudAction(double x, double y) {
        if (hudPanel == null || !hasSelection()) {
            return HUD_NONE;
        }
        for (int i = 0; i < hudButtons.length; i++) {
            double[] b = hudButtons[i];
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] && y <= b[1] + b[3]) {
                return buttonEnabled(i + 1) ? i + 1 : HUD_PANEL;
            }
        }
        boolean inside = x >= hudPanel[0] && x <= hudPanel[0] + hudPanel[2]
                && y >= hudPanel[1] && y <= hudPanel[1] + hudPanel[3];
        return inside ? HUD_PANEL : HUD_NONE;
    }

    private boolean buttonEnabled(int button) {
        if (!hasSelection()) {
            return false;
        }
        int steps = layers.get(selectedKey).steps().size();
        return switch (button) {
            case HUD_PREVIOUS -> selectedIndex > 0;
            case HUD_NEXT -> selectedIndex < steps - 1;
            case HUD_PLAY -> steps > 1;
            default -> true;
        };
    }

    /** The bottom-center caption: where the walk is, and buttons to move or play it. */
    void drawHud(GraphicsContext gc, View view, Color background, Color text) {
        if (!hasSelection()) {
            hudPanel = null;
            return;
        }
        // Always a dark panel with light text: readable over any plot theme.
        Color dark = Color.BLACK;
        String line = describe();
        double buttonSize = 30;
        double speedWidth = 44;
        double gap = 6;
        double buttonsWidth = 4 * buttonSize + speedWidth + (HUD_BUTTONS - 1) * gap;
        double fixed = 18 + 16 + buttonsWidth + 10;
        double available = Math.max(200, view.contentWidth() - 24);
        double textWidth = measure(line);
        if (fixed + textWidth > available) {
            int keep = Math.max(4, (int) (line.length() * (available - fixed) / Math.max(1, textWidth)) - 1);
            line = line.substring(0, Math.min(line.length(), keep)) + "…";
            textWidth = measure(line);
        }
        double width = fixed + textWidth;
        double height = 42;
        double x = view.offsetX() + (view.contentWidth() - width) / 2;
        double y = view.offsetY() + view.contentHeight() - height - 14;
        hudPanel = new double[]{x, y, width, height};
        gc.save();
        gc.setFont(HUD_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(HUD_FILL);
        gc.fillRoundRect(x, y, width, height, 20, 20);
        gc.setStroke(roleColor(0, dark));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x, y, width, height, 20, 20);
        gc.setFill(HUD_TEXT);
        gc.fillText(line, x + 18, y + height / 2 + 5);
        hudButtons = new double[HUD_BUTTONS][];
        double bx = x + width - buttonsWidth - 10;
        for (int i = 0; i < HUD_BUTTONS; i++) {
            double w = i == HUD_SPEED - 1 ? speedWidth : buttonSize;
            double[] b = {bx, y + (height - buttonSize) / 2, w, buttonSize};
            bx += w + gap;
            hudButtons[i] = b;
            boolean enabled = buttonEnabled(i + 1);
            boolean active = i == HUD_PLAY - 1 && playing;
            gc.setGlobalAlpha(enabled ? 1 : 0.3);
            gc.setFill(active ? roleColor(0, dark).deriveColor(0, 1, 1, 0.45) : Color.web("#FFFFFF", 0.14));
            gc.fillRoundRect(b[0], b[1], b[2], b[3], 10, 10);
            gc.setStroke(enabled ? roleColor(0, dark) : HUD_TEXT);
            gc.setLineWidth(1.25);
            gc.strokeRoundRect(b[0], b[1], b[2], b[3], 10, 10);
            double cx = b[0] + b[2] / 2;
            double cy = b[1] + b[3] / 2;
            gc.setFill(HUD_TEXT);
            gc.setStroke(HUD_TEXT);
            gc.setLineWidth(2.4);
            switch (i + 1) {
                case HUD_PREVIOUS -> {
                    gc.strokeLine(cx - 6, cy - 6, cx - 6, cy + 6);
                    gc.fillPolygon(new double[]{cx + 6, cx - 3, cx + 6}, new double[]{cy - 7, cy, cy + 7}, 3);
                }
                case HUD_NEXT -> {
                    gc.strokeLine(cx + 6, cy - 6, cx + 6, cy + 6);
                    gc.fillPolygon(new double[]{cx - 6, cx + 3, cx - 6}, new double[]{cy - 7, cy, cy + 7}, 3);
                }
                case HUD_PLAY -> {
                    if (playing) {
                        gc.fillRect(cx - 6, cy - 7, 4.5, 14);
                        gc.fillRect(cx + 1.5, cy - 7, 4.5, 14);
                    } else {
                        gc.fillPolygon(new double[]{cx - 5, cx + 7, cx - 5}, new double[]{cy - 8, cy, cy + 8}, 3);
                    }
                }
                case HUD_SPEED -> {
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(speedLabel, cx, cy + 5);
                    gc.setTextAlign(TextAlignment.LEFT);
                }
                default -> {
                    gc.strokeLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    gc.strokeLine(cx - 5, cy + 5, cx + 5, cy - 5);
                }
            }
            gc.setGlobalAlpha(1);
        }
        gc.restore();
    }

    private static double measure(String text) {
        javafx.scene.text.Text probe = new javafx.scene.text.Text(text);
        probe.setFont(HUD_FONT);
        return probe.getLayoutBounds().getWidth();
    }
}
