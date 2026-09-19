package org.flatcam.fx;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * Small monochrome toolbar icons, built as vector shapes (not raster images)
 * so a single icon works across every theme - color comes from the
 * "icon-glyph-stroke"/"icon-glyph-fill" style classes (theme/components.css),
 * which just reference -fx-text-base-color, the same as any label's text.
 * The folder shape is Feather Icons' "folder" path (MIT); play/stop are
 * simple enough to draw directly rather than pull in more path data.
 */
final class Icons {

    private static final double NATIVE_SIZE = 24.0;

    private Icons() {
    }

    static Node folderOpen(double size) {
        SVGPath path = new SVGPath();
        path.setContent("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z");
        path.getStyleClass().add("icon-glyph-stroke");
        return scaled(path, size);
    }

    static Node play(double size) {
        Polygon triangle = new Polygon(5, 3, 19, 12, 5, 21);
        triangle.getStyleClass().add("icon-glyph-fill");
        return scaled(triangle, size);
    }

    /** An open ring - stands in for a drill hole (Excellon). */
    static Node drill(double size) {
        Circle ring = new Circle(12, 12, 8);
        ring.getStyleClass().add("icon-glyph-stroke");
        return scaled(ring, size);
    }

    /** A ">_" prompt glyph - toggles the console/log panel. */
    static Node terminal(double size) {
        Polyline chevron = new Polyline(4, 17, 10, 11, 4, 5);
        chevron.getStyleClass().add("icon-glyph-stroke");
        Line underscore = new Line(12, 19, 20, 19);
        underscore.getStyleClass().add("icon-glyph-stroke");
        return scaled(new Group(chevron, underscore), size);
    }

    static Node stop(double size) {
        Rectangle square = new Rectangle(3, 3, 18, 18);
        square.setArcWidth(4);
        square.setArcHeight(4);
        square.getStyleClass().add("icon-glyph-fill");
        return scaled(square, size);
    }

    /**
     * A raster icon copied straight from the legacy app's own assets/resources/ (e.g.
     * ObjectCollection.py's icon_files map: flatcam_icon16.png/drill16.png/cnc16.png for
     * Gerber/Excellon/CNCJob tree rows) - pixel-identical to Python's, not a redrawn
     * lookalike. See flatcam-fx/src/main/resources/org/flatcam/fx/icons/.
     */
    static Node fromResource(String fileName, double size) {
        Image image = new Image(Icons.class.getResourceAsStream("icons/" + fileName));
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static Node scaled(Node shape, double size) {
        Group group = new Group(shape);
        double factor = size / NATIVE_SIZE;
        group.getTransforms().add(new Scale(factor, factor));
        return group;
    }
}
