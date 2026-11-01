package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
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
        Node panel; String applied, saved; Path target; boolean copy; Runnable success; Consumer<String> failure;
        Consumer<GCodeEditorController.SaveResult> saveCompletion;
        boolean immediateSave = true;
        public void openToolPanel(String name,Node node){panel=node;}
        public void closeToolPanel(){panel=null;}
        public boolean apply(TreeItem<String> item,String text,Runnable done,Consumer<String> failed){applied=text;success=done;failure=failed;return true;}
        public boolean save(TreeItem<String> item,String text,Path path,boolean asCopy,Consumer<GCodeEditorController.SaveResult> done){
            saved=text;target=path;copy=asCopy;saveCompletion=done;
            if(immediateSave) done.accept(new GCodeEditorController.SaveResult(path,"Saved fixture")); return true;
        }
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
    @Test void saveTargetsOriginalAndSaveAsKeepsItsAssociationAndDraft() throws Exception {
        TerminalPanelTest.fx(() -> {
            Host host=new Host(); host.immediateSave=false;
            TabPane tabs=new TabPane(); var controller=new GCodeEditorController(tabs,host);
            Path original=Path.of("original.nc").toAbsolutePath();
            controller.start(new TreeItem<>("fixture"),"G21\n",original);
            var editor=(CodeEditor)tabs.getTabs().getFirst().getContent(); editor.area().appendText("M5\n");
            ((Button)host.panel.lookup("#gcode-save")).fire(); assertEquals(original,host.target); assertFalse(host.copy);
            assertTrue(((Button)host.panel.lookup("#gcode-save-as")).isDisabled());
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(original,"Saved"));
            ((Button)host.panel.lookup("#gcode-save-as")).fire(); assertNull(host.target); assertTrue(host.copy);
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(Path.of("copy.nc").toAbsolutePath(),"Copied"));
            controller.save(false); assertEquals(original,host.target); assertFalse(host.copy);
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(null,"Failure"));
            assertTrue(controller.hasUnappliedChanges()); assertNull(host.applied);
            assertEquals("G21\nM5\n",editor.getText()); controller.cancel(); return null;
        });
    }
    @Test void firstSaveAdoptsDestinationOnlyOnSuccessAndShortcutsChooseTheCorrectAction() throws Exception {
        TerminalPanelTest.fx(() -> {
            Host host=new Host(); host.immediateSave=false;
            TabPane tabs=new TabPane(); var controller=new GCodeEditorController(tabs,host);
            controller.start(new TreeItem<>("embedded"),"G21\n");
            var editor=(CodeEditor)tabs.getTabs().getFirst().getContent();
            controller.save(false); assertNull(host.target);
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(null,"Cancelled"));
            controller.save(false); assertNull(host.target);
            Path target=Path.of("new.nc").toAbsolutePath();
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(target,"Saved"));
            editor.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",
                    javafx.scene.input.KeyCode.S,false,true,false,false));
            assertEquals(target,host.target); assertFalse(host.copy);
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(target,"Saved"));
            editor.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,"","",
                    javafx.scene.input.KeyCode.S,true,true,false,false));
            assertNull(host.target); assertTrue(host.copy);
            host.saveCompletion.accept(new GCodeEditorController.SaveResult(null,"Cancelled"));
            controller.cancel(); return null;
        });
    }
}
