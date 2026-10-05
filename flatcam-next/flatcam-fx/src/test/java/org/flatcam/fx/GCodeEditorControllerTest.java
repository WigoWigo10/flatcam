package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class GCodeEditorControllerTest {
    static final class Host implements GCodeEditorController.Host {
        Node panel; String applied, saved; Runnable success; Consumer<String> failure;
        public void openToolPanel(String name,Node node){panel=node;}
        public void closeToolPanel(){panel=null;}
        public boolean apply(TreeItem<String> item,String text,Runnable done,Consumer<String> failed){applied=text;success=done;failure=failed;return true;}
        public boolean saveAs(String text,Consumer<String> done){saved=text;done.accept("Saved fixture");return true;}
        public void log(String text) { }
        Button button(String prefix){return (Button)((VBox)panel).getChildren().stream().filter(n->n instanceof Button b && b.getText().startsWith(prefix)).findFirst().orElseThrow();}
    }
    @Test void editApplyAndFailurePreserveDraftAndBusyLock() throws Exception {
        TerminalPanelTest.fx(() -> {
            Host host=new Host(); TabPane tabs=new TabPane(); GCodeEditorController controller=new GCodeEditorController(tabs,host);
            controller.start(new TreeItem<>("fixture.nc"),"G21\r\nG90\r\n");
            CodeEditor editor=(CodeEditor)tabs.getTabs().getFirst().getContent();
            try {
                assertFalse(controller.hasUnappliedChanges()); assertTrue(host.button("Aplicar").isDisabled());
                editor.area().appendText("M5\n"); assertTrue(controller.hasUnappliedChanges());
                host.button("Aplicar").fire(); assertEquals(editor.getText(),host.applied); assertFalse(editor.area().isEditable());
                assertTrue(host.button("Cancelar").isDisabled()); host.failure.accept("Fixture error");
                assertTrue(controller.isActive()); assertTrue(editor.area().isEditable());
                host.button("Aplicar").fire(); host.success.run(); assertFalse(controller.isActive()); assertTrue(tabs.getTabs().isEmpty());
            } finally { controller.cancel(); } return null;
        });
    }
    @Test void saveDoesNotApplyAndCancelDiscardsTheDraft() throws Exception {
        TerminalPanelTest.fx(() -> {
            Host host=new Host(); TabPane tabs=new TabPane(); GCodeEditorController controller=new GCodeEditorController(tabs,host);
            controller.start(new TreeItem<>("fixture.nc"),"G21\n");
            var editor=(CodeEditor)tabs.getTabs().getFirst().getContent(); editor.area().appendText("M5\n");
            host.button("Salvar").fire(); assertEquals("G21\nM5\n",host.saved); assertNull(host.applied); assertTrue(controller.isActive());
            controller.cancel(); assertFalse(controller.isActive()); assertTrue(tabs.getTabs().isEmpty()); return null;
        });
    }
    @Test void undoToOriginalAndBlankDraftNeverApplyMachineCode() throws Exception {
        TerminalPanelTest.fx(() -> {
            Host host=new Host(); TabPane tabs=new TabPane(); GCodeEditorController controller=new GCodeEditorController(tabs,host);
            controller.start(new TreeItem<>("fixture.nc"),"G21\n");
            var editor=(CodeEditor)tabs.getTabs().getFirst().getContent(); editor.area().appendText("M5\n"); editor.area().undo();
            assertFalse(controller.hasUnappliedChanges()); editor.area().replaceText("  \n"); host.button("Aplicar").fire();
            assertNull(host.applied); assertTrue(controller.isActive()); controller.cancel(); return null;
        });
    }
}
