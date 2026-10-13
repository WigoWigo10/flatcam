package org.flatcam.fx;

import java.util.function.IntConsumer;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;

/** Panel-local tool selection keeps Geometry indices and Excellon IDs separate. */
final class CncReviewNavigation {
    private static final String SELECT_TOOL = CncReviewNavigation.class.getName();
    static void install(Node root, IntConsumer selection) { root.getProperties().put(SELECT_TOOL, selection); }
    static boolean focus(Node root, CncPreflight.Target target) {
        if (target == null) return false;
        Node panel = root instanceof ScrollPane scroll ? scroll.getContent() : root;
        if (target.tool() >= 0 && panel.getProperties().get(SELECT_TOOL) instanceof IntConsumer select) select.accept(target.tool());
        Node field = panel.lookup("#" + target.field());
        if (field == null) return false;
        for (Node parent = field.getParent(); parent != null; parent = parent.getParent())
            if (parent instanceof TitledPane titled) titled.setExpanded(true);
        panel.applyCss(); if (panel instanceof javafx.scene.Parent parent) parent.layout();
        if (root instanceof ScrollPane scroll) {
            var bounds = panel.sceneToLocal(field.localToScene(field.getBoundsInLocal()));
            double travel = panel.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
            if (travel > 0) scroll.setVvalue(Math.max(0, Math.min(1, bounds.getMinY() / travel)));
        }
        field.requestFocus(); return true;
    }
    private CncReviewNavigation() { }
}
