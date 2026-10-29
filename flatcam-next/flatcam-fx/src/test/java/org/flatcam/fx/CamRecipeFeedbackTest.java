package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import static org.flatcam.fx.MainToolPersistenceTest.*;
import javafx.scene.control.*;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.NccMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class CamRecipeFeedbackTest {
    @Test void comparingNccVersionsDoesNotHideTheSourceOrUnrelatedGeometry() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var form = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var node = panel(s).getContent();
                ((TableView<?>) node.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                return node;
            });
            for (int i = 0; i < 2; i++) {
                var handle = TerminalPanelTest.fx(() -> { ((Button) form.lookup("#ncc-generate")).fire(); return job(s); });
                finish(s, handle);
            }
            TerminalPanelTest.fx(() -> {
                var versions = s.geometries().keySet().stream().filter(item -> item != s.reference).toList();
                assertEquals(2, versions.size());
                var feedback = CamGenerationFeedback.of(panel(s));
                var content = ((TitledPane) feedback.getChildren().get(1)).getContent();
                var plot = (PlotAreaView) field(s.window, "plotAreaView");
                ((Button) content.lookup("#cam-show-previous")).fire();
                assertTrue(plot.isLayerVisible(versions.getFirst())); assertFalse(plot.isLayerVisible(versions.getLast()));
                assertTrue(plot.isLayerVisible(s.item)); assertTrue(plot.isLayerVisible(s.reference));
                ((Button) content.lookup("#cam-show-current")).fire();
                assertFalse(plot.isLayerVisible(versions.getFirst())); assertTrue(plot.isLayerVisible(versions.getLast()));
                return null;
            });
        }
    }
    @Test void editsDuringGenerationDoNotRelabelTheSubmittedRecipe() throws Exception {
        try (var s = new MainCamFlowTest.Session("MM")) {
            var form = TerminalPanelTest.fx(() -> {
                call(s.window, "generateNcc", new Class<?>[]{TreeItem.class, GerberImage.class}, s.item, s.image);
                var node = panel(s).getContent();
                ((TableView<?>) node.lookup("#ncc-tools")).getSelectionModel().clearAndSelect(0);
                ((ComboBox<NccMethod>) node.lookup("#ncc-method")).setValue(NccMethod.SEED);
                ((Spinner<?>) node.lookup("#ncc-margin")).getEditor().setText("1.7");
                ((Button) node.lookup("#ncc-generate")).fire();
                ((ComboBox<NccMethod>) node.lookup("#ncc-method")).setValue(NccMethod.STANDARD);
                return node;
            });
            finish(s, TerminalPanelTest.fx(() -> job(s)));
            TerminalPanelTest.fx(() -> {
                var feedback = CamGenerationFeedback.of(panel(s)); assertNotNull(feedback);
                assertEquals(I18n.text("cam.result.changed"), ((Label) form.lookup("#cam-generation-status")).getText());
                var content = ((TitledPane) feedback.getChildren().get(1)).getContent();
                var text = ((TextArea) content.lookup("#cam-generation-recipe")).getText();
                assertTrue(text.contains(NccMethod.SEED.toString()), text); assertFalse(text.contains(NccMethod.STANDARD.toString()), text);
                assertTrue(text.contains("1.7")); assertTrue(text.contains("board_ncc"));
                return null;
            });
        }
    }
}
