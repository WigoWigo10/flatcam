package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class ToolDefaultsPaneTest {

    @BeforeEach
    @AfterEach
    void freshStore() {
        ToolDefaults.useStore(ToolDefaults.memoryStore());
    }

    @Test
    void editingSavesPerUnitsRefusesBadValuesAndRestores() throws Exception {
        TerminalPanelTest.fx(() -> {
            VBox root = new VBox(ToolDefaultsPane.build());
            new Scene(root, 480, 700);
            root.applyCss();
            root.layout();
            TextField dia = (TextField) root.lookup("#tool-default-cutout-tooldia");
            assertEquals("2.4", dia.getText());
            dia.setText("3.175");
            ((CheckBox) root.lookup("#tool-default-cutout-multidepth")).setSelected(false);

            // Switching to inches shows the other value and keeps the unsaved millimetre edit.
            RadioButton inches = (RadioButton) root.lookupAll(".radio-button").stream()
                    .filter(node -> "in".equals(((RadioButton) node).getText())).findFirst().orElseThrow();
            inches.setSelected(true);
            root.applyCss();
            root.layout();
            TextField diaInches = (TextField) root.lookup("#tool-default-cutout-tooldia");
            assertEquals("0.094", diaInches.getText());
            diaInches.setText("abc");

            ((Button) root.lookup("#tool-defaults-save")).fire();
            Label feedback = (Label) root.lookup("#tool-defaults-feedback");
            assertTrue(feedback.getText().startsWith("Não salvo"), feedback.getText());
            assertEquals("3.175", ToolDefaults.text("cutout.tooldia", true), "the valid edits are saved");
            assertFalse(ToolDefaults.flag("cutout.multidepth"));
            assertEquals("0.094", ToolDefaults.text("cutout.tooldia", false));

            root.applyCss();
            root.layout();
            ((TextField) root.lookup("#tool-default-cutout-tooldia")).setText("0.125");
            ((Button) root.lookup("#tool-defaults-save")).fire();
            assertEquals("0.125", ToolDefaults.text("cutout.tooldia", false));
            assertTrue(feedback.getText().startsWith("Padrões salvos"), feedback.getText());

            // Restoring needs the confirmation button.
            Button confirm = (Button) root.lookup("#tool-defaults-reset-confirm");
            assertFalse(confirm.isVisible());
            ((Button) root.lookup("#tool-defaults-reset")).fire();
            assertTrue(confirm.isVisible());
            assertEquals("3.175", ToolDefaults.text("cutout.tooldia", true));
            confirm.fire();
            assertEquals("2.4", ToolDefaults.text("cutout.tooldia", true));
            assertTrue(ToolDefaults.flag("cutout.multidepth"));
            return null;
        });
    }
}
