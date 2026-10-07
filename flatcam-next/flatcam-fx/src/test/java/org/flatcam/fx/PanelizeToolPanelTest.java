package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class PanelizeToolPanelTest {
    private static final class Host implements PanelizeToolPanel.Host {
        final TreeItem<String> copper = new TreeItem<>("F_Cu");
        final TreeItem<String> drills = new TreeItem<>("holes");
        final TreeItem<String> contour = new TreeItem<>("Edge_Cuts");
        String drillUnits = "MM";
        PanelizeToolPanel.Request preview, created;
        PanelizeToolPanel.PreviewOptions options;
        @Override public List<TreeItem<String>> sources() { return List.of(copper, drills, contour); }
        @Override public TreeItem<String> initialSource() { return copper; }
        @Override public double[] bounds(TreeItem<String> item) {
            return item == copper ? new double[]{0, 0, 10, 8} : item == drills ? new double[]{2, 2, 4, 4} : new double[]{-2, -2, 12, 10};
        }
        @Override public boolean isGerber(TreeItem<String> item) { return item == copper; }
        @Override public boolean canBeContour(TreeItem<String> item) { return item != drills; }
        @Override public String units(TreeItem<String> item) { return item == drills ? drillUnits : "MM"; }
        @Override public void preview(PanelizeToolPanel.Request request, PanelizeToolPanel.PreviewOptions options, Consumer<String> state) {
            preview = request; this.options = options; state.accept(request == null ? "" : "ready");
        }
        @Override public void panelize(PanelizeToolPanel.Request request) { created = request; }
    }
    private static Node control(Node root, String id) {
        if (id.equals(root.getId())) return root;
        if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            Node found = control(child, id); if (found != null) return found;
        }
        return null;
    }
    private static void check(Node root, int index) {
        var list = (ListView<TreeItem<String>>) control(root, "panelize-sources");
        var cell = list.getCellFactory().call(list); cell.updateListView(list); cell.updateIndex(index);
        ((CheckBox) cell.getGraphic()).fire();
    }
    @Test void batchUsesOneReferenceAndMirrorsTheTwoSidedPreviewControls() throws Exception {
        TerminalPanelTest.fx(() -> {
            var host = new Host(); Node root = PanelizeToolPanel.build(host, () -> {});
            assertSame(host.contour, host.options.contour());
            assertSame(host.contour, host.preview.reference(), "default pitch follows the physical board, not copper bounds");
            assertTrue(((RadioButton) control(root, "panelize-other-box")).isSelected());
            assertTrue(host.options.showContent()); assertTrue(host.options.showOutline()); assertTrue(host.options.fillInterior());
            ((CheckBox) control(root, "panelize-together")).fire(); check(root, 1); check(root, 2);
            ((RadioButton) control(root, "panelize-other-box")).fire();
            assertEquals(List.of(host.copper, host.drills, host.contour), host.preview.sources());
            assertSame(host.contour, host.preview.reference());
            assertEquals(14, host.preview.layout().stepX()); assertEquals(12, host.preview.layout().stepY());
            ((Button) control(root, "panelize-create")).fire();
            assertEquals(3, host.created.sources().size());
            assertNull(host.preview, "creating clears the display-only preview");
            return null;
        });
    }
    @Test void mismatchedUnitsBlockBatchAndCannotCreateAnything() throws Exception {
        TerminalPanelTest.fx(() -> {
            var host = new Host(); host.drillUnits = "IN"; Node root = PanelizeToolPanel.build(host, () -> {});
            ((CheckBox) control(root, "panelize-together")).fire(); check(root, 1);
            assertNull(host.preview);
            assertTrue(((Label) control(root, "panelize-error")).getText().contains("mesmas unidades"));
            ((Button) control(root, "panelize-create")).fire(); assertNull(host.created);
            return null;
        });
    }
    @Test void togglingPreviewDoesNotChangeGridOrGenerationRequest() throws Exception {
        TerminalPanelTest.fx(() -> {
            var host = new Host(); Node root = PanelizeToolPanel.build(host, () -> {});
            var layout = host.preview.layout();
            assertTrue(control(root, "panelize-preview-legend").isVisible());
            ((CheckBox) control(root, "panelize-preview-content")).fire(); assertFalse(host.options.showContent());
            assertFalse(control(root, "panelize-preview-legend").isVisible());
            ((CheckBox) control(root, "panelize-preview")).fire(); assertNull(host.preview);
            assertTrue(control(root, "panelize-preview-content").isDisabled());
            ((Button) control(root, "panelize-create")).fire(); assertEquals(layout, host.created.layout());
            ((TextField) control(root, "panelize-spacing-x")).setText("NaN");
            assertTrue(((Label) control(root, "panelize-error")).getText().contains("Valor invalido"));
            return null;
        });
    }
}
