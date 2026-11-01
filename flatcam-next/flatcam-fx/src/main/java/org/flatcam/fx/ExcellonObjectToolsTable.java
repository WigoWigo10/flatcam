package org.flatcam.fx;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.flatcam.cam.excellon.ExcellonImage;

/** Excellon tool inventory; the properties view also edits the real diameter of a tool ID. */
final class ExcellonObjectToolsTable {

    interface Host {
        boolean change(int toolId,double diameter);
        boolean history(boolean redo);
        boolean canUndo();
        boolean canRedo();
    }
    record View(Node node, java.util.function.BooleanSupplier commit, java.util.function.BiConsumer<ExcellonImage,String> refresh) {
        boolean finish() { return commit.getAsBoolean(); }
        void update(ExcellonImage image,String message) { refresh.accept(image,message); }
    }
    private static final class ToolRow {
        final int id, drills, slots;
        final String total;
        double diameter;
        ToolRow(int id,double diameter,int drills,int slots,String total) {
            this.id=id;this.diameter=diameter;this.drills=drills;this.slots=slots;this.total=total;
        }
    }

    private record Row(String number, String diameter, String drills, String slots, boolean total) { }

    private ExcellonObjectToolsTable() { }

    static View editable(ExcellonImage image,Host host) {
        var editing=new java.util.concurrent.atomic.AtomicReference<EditableToolDiameterColumn<ToolRow>>();
        TableView<ToolRow> table=new TableView<>() {
            @Override public void edit(int index,TableColumn<ToolRow,?> column) {
                if(index>=0) {
                    if(isDisabled() || !isEditable() || index>=getItems().size() || getItems().get(index).total!=null) return;
                    var helper=editing.get();
                    if(helper!=null && helper.draft(getItems().get(index))==null
                            && getItems().stream().anyMatch(row->helper.draft(row)!=null)
                            && (!helper.commit() || !isEditable())) return;
                }
                super.edit(index,column);
            }
        };
        table.setId("excellon-object-tools");table.setMinWidth(0);
        var number=new TableColumn<ToolRow,String>("#");number.setMinWidth(34);number.setMaxWidth(34);
        number.setCellValueFactory(value->new SimpleStringProperty(value.getValue().total==null ? Integer.toString(value.getValue().id) : ""));
        var diameter=new TableColumn<ToolRow,Double>("Diameter");
        diameter.setCellValueFactory(value->new javafx.beans.property.SimpleObjectProperty<>(value.getValue().diameter));
        var drills=counts("Drills",true);
        var slots=counts("Slots",false);
        drills.setMinWidth(58);slots.setMinWidth(52);
        table.getColumns().addAll(number,diameter,drills,slots);
        table.getColumns().forEach(column->column.setSortable(false));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        var drillCounts=image.drillCounts();var slotCounts=image.slotCounts();
        image.toolDiameters().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry->
                table.getItems().add(new ToolRow(entry.getKey(),entry.getValue(),drillCounts.getOrDefault(entry.getKey(),0),
                        slotCounts.getOrDefault(entry.getKey(),0),null)));
        table.getItems().addAll(new ToolRow(-1,Double.NaN,image.totalDrills(),0,"Total Drills"),
                new ToolRow(-2,Double.NaN,0,image.totalSlots(),"Total Slots"));
        table.setFixedCellSize(UiFonts.rowHeight(27));
        table.setPrefHeight(32+table.getItems().size()*UiFonts.rowHeight(27));
        table.setMinHeight(table.getPrefHeight());table.setMaxHeight(table.getPrefHeight());
        var feedback=new javafx.scene.control.Label();feedback.setId("excellon-diameter-feedback");feedback.setWrapText(true);
        feedback.getStyleClass().add("cam-generation-status");
        var helper=new EditableToolDiameterColumn<>(table,diameter,row->row.diameter,(row,value)->{
            if(host.change(row.id,value)) {row.diameter=value;table.setEditable(false);feedback.setText(I18n.text("excellon.diameter.updating"));}
            else feedback.setText(I18n.text("excellon.diameter.unavailable"));
        },feedback::setText,row->row.total==null,false,row->row.total==null ? UiNumbers.fixed(row.diameter,image.units()) : row.total,
                I18n.text("excellon.diameter.help"));
        editing.set(helper);
        var undo=new javafx.scene.control.MenuItem(I18n.text("cam.table.undo"));
        var redo=new javafx.scene.control.MenuItem(I18n.text("cam.table.redo"));
        undo.setOnAction(event->{if(helper.commit() && table.isEditable() && host.history(false))table.setEditable(false);});
        redo.setOnAction(event->{if(helper.commit() && table.isEditable() && host.history(true))table.setEditable(false);});
        table.getContextMenu().getItems().addAll(new javafx.scene.control.SeparatorMenuItem(),undo,redo);
        table.getContextMenu().addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWING,event->{undo.setDisable(!host.canUndo());redo.setDisable(!host.canRedo());});
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,event->{
            if(!event.isShortcutDown() || event.isAltDown() || TableKeyboardActions.inputTarget(event.getTarget()))return;
            if(event.getCode()==javafx.scene.input.KeyCode.Z || event.getCode()==javafx.scene.input.KeyCode.Y) {
                boolean forward=event.getCode()==javafx.scene.input.KeyCode.Y || event.isShiftDown();
                if(helper.commit() && table.isEditable() && host.history(forward))table.setEditable(false);
                event.consume();
            }
        });
        ToolDescriptions.apply(table,I18n.text("ui.b96a67bfde3e"),I18n.text("excellon.diameter.help"));
        ToolDescriptions.apply(undo.getProperties(),undo.getText(),I18n.text("excellon.diameter.history.help"));
        ToolDescriptions.apply(redo.getProperties(),redo.getText(),I18n.text("excellon.diameter.history.help"));
        table.getSelectionModel().clearSelection();
        var root=new javafx.scene.layout.VBox(4,table,feedback);root.setMinWidth(0);
        return new View(root,helper::commit,(updated,message)->{
            table.edit(-1,null);
            for(var row:table.getItems()) if(row.total==null && updated.toolDiameters().containsKey(row.id))row.diameter=updated.toolDiameters().get(row.id);
            table.setEditable(true);table.refresh();feedback.setText(message);
        });
    }

    private static TableColumn<ToolRow,String> counts(String title,boolean drills) {
        var column=new TableColumn<ToolRow,String>(title);
        column.setCellValueFactory(value->{var row=value.getValue();int count=drills ? row.drills : row.slots;
            return new SimpleStringProperty(count>0 || drills && row.id==-1 || !drills && row.id==-2 ? Integer.toString(count) : "");});
        column.setCellFactory(ignored->new TableCell<>() {
            @Override protected void updateItem(String value,boolean empty) {
                super.updateItem(value,empty);setText(empty ? null : value);
                if(!empty && getTableRow()!=null && getTableRow().getItem()!=null && getTableRow().getItem().total!=null) {
                    if(!getStyleClass().contains("total-cell"))getStyleClass().add("total-cell");
                } else getStyleClass().remove("total-cell");
            }
        });return column;
    }

    static void selectTool(Node root,int id,boolean focus) {
        var node=root.lookup("#excellon-object-tools");
        if(node instanceof TableView<?> table) for(int i=0;i<table.getItems().size();i++)
            if(table.getItems().get(i) instanceof ToolRow row && row.id==id) {
                table.getSelectionModel().clearAndSelect(i);table.scrollTo(i);if(focus)table.requestFocus();return;
            }
    }

    static Node build(ExcellonImage image) {
        var drillCounts = image.drillCounts();
        var slotCounts = image.slotCounts();
        List<Row> rows = new ArrayList<>();
        image.toolDiameters().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int drills = drillCounts.getOrDefault(entry.getKey(), 0);
                    int slots = slotCounts.getOrDefault(entry.getKey(), 0);
                    rows.add(new Row(Integer.toString(entry.getKey()),
                            UiNumbers.fixed(entry.getValue(),image.units()),
                            drills == 0 ? "" : Integer.toString(drills),
                            slots == 0 ? "" : Integer.toString(slots), false));
                });
        rows.add(new Row("", "Total Drills", Integer.toString(image.totalDrills()), "", true));
        rows.add(new Row("", "Total Slots", "", Integer.toString(image.totalSlots()), true));

        TableColumn<Row, String> numberColumn = column("#", Row::number);
        numberColumn.setMinWidth(34);
        numberColumn.setMaxWidth(34);
        TableColumn<Row, String> diameterColumn = column("Diameter", Row::diameter);
        TableColumn<Row, String> drillsColumn = column("Drills", Row::drills);
        drillsColumn.setMinWidth(58);
        TableColumn<Row, String> slotsColumn = column("Slots", Row::slots);
        slotsColumn.setMinWidth(52);
        TableView<Row> table = new TableView<>();
        table.setMinWidth(0);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(numberColumn, diameterColumn, drillsColumn, slotsColumn);
        table.getItems().setAll(rows);
        table.setFixedCellSize(UiFonts.rowHeight(27));
        table.setPrefHeight(30 + rows.size() * UiFonts.rowHeight(27) + 2);
        table.setMinHeight(table.getPrefHeight());
        table.setMaxHeight(table.getPrefHeight());
        table.getSelectionModel().clearSelection();
        table.setFocusTraversable(false);
        return table;
    }

    private static TableColumn<Row, String> column(String title,
                                                    java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                boolean total = !empty && getTableRow() != null
                        && getTableRow().getItem() != null && getTableRow().getItem().total();
                if (total) {
                    if (!getStyleClass().contains("total-cell")) getStyleClass().add("total-cell");
                } else {
                    getStyleClass().remove("total-cell");
                }
            }
        });
        return column;
    }
}
