package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class TableKeyboardActionsTest {
    @Test void equalValuedRowsAreRemovedBySelectionInsteadOfByEquality() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=form(true,false);f.table().getItems().setAll("duplicate","keep","duplicate","duplicate");
            f.table().getSelectionModel().selectIndices(0,2);f.table().fireEvent(key(KeyCode.DELETE,false,false));
            assertEquals(List.of("keep","duplicate"),f.table().getItems());return null;
        });
    }
    private static final class Value { double diameter; Value(double value){diameter=value;} }
    @Test void tabAtEitherEndLeavesTheTableAndEnterReturnsFocusToIt() throws Exception {
        TerminalPanelTest.fx(() -> {
            var table=new TableView<Value>();table.getItems().addAll(new Value(.2),new Value(.4));
            var column=new TableColumn<Value,Double>("Diameter");column.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().diameter));
            table.getColumns().add(column);new EditableToolDiameterColumn<>(table,column,row -> row.diameter,(row,value)->row.diameter=value,message -> {});
            var before=new TextField("before");var after=new TextField("after");var root=new VBox(before,table,after);var scene=new Scene(root,400,300);
            root.applyCss();root.layout();table.getSelectionModel().clearAndSelect(1);table.fireEvent(key(KeyCode.F2,false,false));root.applyCss();root.layout();
            ((TextField)table.lookup("#tool-diameter-editor")).fireEvent(key(KeyCode.TAB,false,false));
            assertNull(table.getEditingCell());assertSame(after,scene.getFocusOwner());
            table.getSelectionModel().clearAndSelect(0);table.fireEvent(key(KeyCode.F2,false,false));root.applyCss();root.layout();
            ((TextField)table.lookup("#tool-diameter-editor")).fireEvent(key(KeyCode.TAB,true,false));
            assertSame(before,scene.getFocusOwner());
            table.fireEvent(key(KeyCode.ENTER,false,false));root.applyCss();root.layout();
            ((TextField)table.lookup("#tool-diameter-editor")).fireEvent(new javafx.event.ActionEvent());
            assertNull(table.getEditingCell());assertSame(table,scene.getFocusOwner());return null;
        });
    }
    private record Form(TableView<String> table, Button remove, VBox root, AtomicInteger plotDeletes) { }
    private static Form form(boolean removable, boolean editorHistory) {
        var table=new TableView<String>();table.getItems().addAll("one","two","three","four");
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        var column=new TableColumn<String,String>("Values");column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue()));
        table.getColumns().add(column);
        var remove=new Button("Remove");remove.setOnAction(e -> TableKeyboardActions.removeSelected(table));
        var root=new VBox(table,remove);var scene=new Scene(root,400,300);root.applyCss();root.layout();
        var plotDeletes=new AtomicInteger();
        scene.addEventFilter(KeyEvent.KEY_PRESSED,e -> {
            if(!TableKeyboardActions.prefersLocalHandling(e)&&e.getCode()==KeyCode.DELETE)plotDeletes.incrementAndGet();
        });
        if(removable)TableKeyboardActions.deleteWith(table,remove,editorHistory);
        return new Form(table,remove,root,plotDeletes);
    }
    private static KeyEvent key(KeyCode code,boolean shift,boolean ctrl){return new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,shift,ctrl,false,false);}

    @Test void deleteFromRenderedCellRemovesWholeSelectionAndSelectsItsNeighbor() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=form(true,false);f.table().getSelectionModel().selectIndices(1,3);
            var cell=f.table().lookupAll(".table-cell").stream().filter(n -> n instanceof TableCell<?,?> c&&c.getIndex()==1).findFirst().orElseThrow();
            cell.fireEvent(key(KeyCode.DELETE,false,false));
            assertEquals(List.of("one","three"),f.table().getItems());assertEquals(1,f.table().getSelectionModel().getSelectedIndex());
            assertEquals(0,f.plotDeletes().get());
            f.table().getContextMenu().getItems().getLast().fire();assertEquals(List.of("one"),f.table().getItems());return null;
        });
    }
    @Test void modifiersNoSelectionAndDisabledActionsDoNotRemoveRows() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=form(true,false);f.table().getSelectionModel().clearSelection();f.table().fireEvent(key(KeyCode.DELETE,false,false));
            assertEquals(4,f.table().getItems().size());
            f.table().getSelectionModel().select(0);
            f.table().fireEvent(key(KeyCode.DELETE,true,false));f.table().fireEvent(key(KeyCode.DELETE,false,true));
            assertEquals(4,f.table().getItems().size());
            f.remove().setDisable(true);f.table().fireEvent(key(KeyCode.DELETE,false,false));
            assertEquals(4,f.table().getItems().size());assertTrue(f.table().getContextMenu().getItems().getLast().isDisable());return null;
        });
    }
    @Test void readOnlyTablesDoNotSendDeleteToAnUnrelatedPlotSelection() throws Exception {
        TerminalPanelTest.fx(() -> {
            var f=form(false,false);f.table().getSelectionModel().select(0);f.table().fireEvent(key(KeyCode.DELETE,false,false));
            assertEquals(4,f.table().getItems().size());assertEquals(0,f.plotDeletes().get());return null;
        });
    }
    @Test void onlyObjectEditorTablesShareUndoWithTheirPlotEditor() throws Exception {
        TerminalPanelTest.fx(() -> {
            for(boolean shared:List.of(false,true)){
                var f=form(true,shared);var owns=new ArrayList<Boolean>();
                f.table().addEventFilter(KeyEvent.KEY_PRESSED,e -> owns.add(TableKeyboardActions.prefersLocalHandling(e)));
                f.table().fireEvent(key(KeyCode.Z,false,true));assertEquals(List.of(!shared),owns);
            }return null;
        });
    }
}
