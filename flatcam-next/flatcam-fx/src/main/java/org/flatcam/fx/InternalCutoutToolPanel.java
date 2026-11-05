package org.flatcam.fx;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.flatcam.app.project.CncJobDefaults;
import org.flatcam.cam.cutout.*;
import org.flatcam.cam.gcode.GeometryGCodeParameters;
import org.flatcam.cam.geometry.ToolProfile;
import org.locationtech.jts.geom.Geometry;

/** A local draft of selected and drawn openings; generating creates a separate Geometry object. */
final class InternalCutoutToolPanel extends VBox {
    enum Shape { RECTANGLE, CIRCLE, POLYGON }
    record PreviewInfo(double length) { }
    record PreviewIssue(String message, int selectedIndex) { }
    interface Host {
        void load(Consumer<List<Geometry>> done, Consumer<String> failure);
        void preview(List<Geometry> selected, double diameter, double margin, Consumer<PreviewInfo> done, Consumer<PreviewIssue> failure);
        default void frame(List<Geometry> selected) { }
        boolean pick(List<Geometry> candidates, IntConsumer selected, Consumer<String> failure, Runnable finished);
        boolean draw(Shape shape, Consumer<Geometry> accepted, Consumer<String> failure, Runnable cancelled);
        void stop();
        void clear();
    }
    record Settings(String diameter, String margin, String cutZ, boolean multiple, String step,
                    GeometryGCodeParameters machining, ToolProfile profile, CncJobDefaults defaults) { }
    private record Opening(int id, Geometry area, boolean drawn) { }
    private final Host host;
    private final String units;
    private final Settings settings;
    private final TableView<Opening> table = new TableView<>();
    private final Label status = new Label(), error = new Label();
    private final Label summary = new Label(), length = new Label();
    private final SimpleBooleanProperty previewReady=new SimpleBooleanProperty(), parametersValid=new SimpleBooleanProperty(), interacting=new SimpleBooleanProperty();
    private static final PseudoClass INVALID=PseudoClass.getPseudoClass("cutout-invalid");
    private Opening invalidOpening;
    private String previewError="", parameterError="";
    private final TextField diameter, margin, cutZ, step;
    private final CheckBox multiple = new CheckBox("Multi-Depth");
    private final ComboBox<ToolProfile> profile = new ComboBox<>();
    private final MachiningZInput zInput = new MachiningZInput("cutout-internal-z-policy");
    private final PauseTransition debounce = new PauseTransition(Duration.millis(160));
    private int nextId = 1;

    InternalCutoutToolPanel(String units, String sourceName, Settings settings, Host host,
                           BiFunction<String,Double,Node> icons, Consumer<CutoutToolPanel.Result> generate, Runnable close) {
        super(10); this.host = host; this.units = units; this.settings = settings;
        setPadding(new Insets(12)); setId("cutout-internal-panel");
        Label title = new Label(I18n.text("cutout.internal.title"), icons.apply("cut16_bis.png",20.));
        title.getStyleClass().add("tool-title");
        Label source = label(I18n.text("cutout.internal.source").formatted(sourceName,units));
        Label instructions = label(I18n.text("cutout.internal.help"));
        diameter = field("cutout-internal-diameter", settings.diameter());
        margin = field("cutout-internal-margin", settings.margin());
        cutZ = field("cutout-internal-cut-z", settings.cutZ());
        step = field("cutout-internal-step", settings.step());
        multiple.setSelected(settings.multiple()); multiple.setId("cutout-internal-multiple");
        step.disableProperty().bind(multiple.selectedProperty().not());
        profile.getItems().setAll(ToolProfile.values()); profile.setValue(settings.profile());
        profile.setId("cutout-internal-profile"); ToolProfilePicker.decorate(profile);
        help(diameter,"cutout.internal.diameter_help"); help(margin,"cutout.internal.margin_help");
        help(cutZ,"machining.z.help"); help(step,"cutout.internal.depth_help"); help(multiple,"cutout.internal.depth_help");
        GridPane parameters = new GridPane(); parameters.setHgap(8); parameters.setVgap(6);
        parameters.addRow(0,new Label(I18n.text("ui.7cd23abc62e0")),diameter);
        parameters.addRow(1,new Label(I18n.text("cam.recipe.margin")),margin);
        parameters.addRow(2,new Label(I18n.text("cutout.internal.profile")),profile);
        parameters.addRow(3,new Label("Cut Z"),cutZ); parameters.addRow(4,multiple,step);

        table.setId("cutout-internal-openings"); table.setMinWidth(0); table.setPrefHeight(170); table.setMinHeight(90);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TableColumn<Opening,String> number = column("#",row -> (row==invalidOpening ? "! " : "")+row.id());
        number.setMinWidth(34); number.setPrefWidth(38); number.setMaxWidth(52);
        TableColumn<Opening,String> kind = column(I18n.text("cam.recipe.source"),row -> I18n.text(row.drawn()
                ? "cutout.internal.drawn" : "cutout.internal.detected"));
        kind.setMinWidth(75); kind.setPrefWidth(95);
        TableColumn<Opening,String> bounds = column(I18n.text("cutout.internal.dimensions"),row -> {
            var box = row.area().getEnvelopeInternal();
            return UiNumbers.fixed(box.getWidth(),units)+" × "+UiNumbers.fixed(box.getHeight(),units);
        });
        bounds.setMinWidth(90); table.getColumns().setAll(List.of(number,kind,bounds));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setRowFactory(view -> new TableRow<>() {
            @Override protected void updateItem(Opening item,boolean empty) {
                super.updateItem(item,empty); pseudoClassStateChanged(INVALID,!empty && item==invalidOpening);
                setAccessibleHelp(!empty && item==invalidOpening ? previewError : null);
            }
        });
        table.setPlaceholder(label(I18n.text("cutout.internal.no_openings"))); help(table,"cutout.internal.selection_help");
        Button all = button("cutout.internal.all","cutout-internal-all",() -> table.getSelectionModel().selectAll(),"cutout.internal.selection_help");
        Button none = button("cutout.internal.none","cutout-internal-none",() -> table.getSelectionModel().clearSelection(),"cutout.internal.selection_help");
        Button pick = button("cutout.internal.pick","cutout-internal-pick",this::pick,"cutout.internal.pick_help");
        Button frame = button("cutout.internal.frame","cutout-internal-frame",() -> host.frame(selected()),"cutout.internal.frame_help");
        frame.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(table.getSelectionModel().getSelectedItems()));
        all.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(table.getItems()));
        none.disableProperty().bind(frame.disabledProperty());
        pick.disableProperty().bind(all.disabledProperty());
        Button remove = button("cutout.internal.remove","cutout-internal-remove",this::removeDrawn,"cutout.internal.remove_help");
        var menu = new ContextMenu(); var removeItem = new MenuItem(remove.getText());
        ToolDescriptions.apply(removeItem.getProperties(),remove.getText(),I18n.text("cutout.internal.remove_help"));
        removeItem.disableProperty().bind(remove.disabledProperty()); removeItem.setOnAction(e -> remove.fire());
        menu.getItems().add(removeItem); table.setContextMenu(menu);
        table.addEventFilter(KeyEvent.KEY_PRESSED,e -> {
            if (e.getCode()==KeyCode.DELETE && TableKeyboardActions.plain(e) && !TableKeyboardActions.inputTarget(e.getTarget())) {
                if (!remove.isDisabled()) remove.fire(); e.consume();
            }
        });
        table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Opening>)change -> {
            remove.setDisable(table.getSelectionModel().getSelectedItems().stream().noneMatch(Opening::drawn));
            selectionChanged();
        }); remove.setDisable(true);
        FlowPane selection = new FlowPane(6,6,all,none,pick,frame,remove);
        FlowPane drawing = new FlowPane(6,6);
        for (Shape shape : Shape.values()) {
            Button draw = button("cutout.internal.draw_"+shape.name().toLowerCase(java.util.Locale.ROOT),
                    "cutout-internal-draw-"+shape.name().toLowerCase(java.util.Locale.ROOT),() -> draw(shape),"cutout.internal.draw_help");
            draw.setGraphic(icons.apply(switch(shape) {case RECTANGLE -> "rectangle32.png"; case CIRCLE -> "circle32.png"; case POLYGON -> "polygon32.png";},16.));
            drawing.getChildren().add(draw);
        }
        Button stop = button("cutout.internal.stop","cutout-internal-stop",host::stop,"cutout.internal.stop_help");
        drawing.getChildren().add(stop);
        Button create = button("cutout.internal.generate","cutout-internal-generate",() -> generate(generate),"cutout.internal.generate_help");
        create.getStyleClass().add("primary-action"); create.setMaxWidth(Double.MAX_VALUE);
        create.disableProperty().bind(previewReady.not().or(parametersValid.not()).or(interacting));
        Button cancel = new Button(I18n.text("ui.0f2bd88ef0ac")); cancel.setOnAction(e -> close.run());
        cancel.setMaxWidth(Double.MAX_VALUE); help(cancel,"cutout.internal.close_help");
        status.setId("cutout-internal-status"); status.setWrapText(true);
        error.setId("cutout-internal-error"); error.setWrapText(true); error.getStyleClass().add("form-error-label");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());error.managedProperty().bind(error.visibleProperty());
        summary.setId("cutout-internal-summary"); summary.setWrapText(true);
        length.setId("cutout-internal-length"); length.setWrapText(true);
        VBox review=new VBox(6,summary,length);review.getStyleClass().add("cutout-review");help(review,"cutout.internal.summary_help");
        VBox form = new VBox(10,parameters,table,selection,drawing,zInput.view(),legend(),review,create);
        form.setDisable(true); status.setText(I18n.text("cutout.internal.loading"));
        getChildren().setAll(title,source,instructions,form,status,error,cancel);
        debounce.setOnFinished(e -> preview());
        diameter.textProperty().addListener((obs,before,after)->selectionChanged());
        margin.textProperty().addListener((obs,before,after)->selectionChanged());
        cutZ.textProperty().addListener((obs,before,after)->validateParameters());
        step.textProperty().addListener((obs,before,after)->validateParameters());
        multiple.selectedProperty().addListener((obs,before,after)->validateParameters());
        profile.valueProperty().addListener((obs,before,after)->validateParameters());
        validateParameters();
        host.load(openings -> {
            for (Geometry area : openings) table.getItems().add(new Opening(nextId++,area,false));
            form.setDisable(false); status.setText(I18n.text("cutout.internal.loaded").formatted(openings.size()));
        },error::setText);
    }

    private Node legend() {
        FlowPane legend=new FlowPane(12,6);legend.setId("cutout-internal-legend");
        for(String kind:List.of("opening","path","swept","invalid")) {
            javafx.scene.shape.Rectangle swatch=new javafx.scene.shape.Rectangle(20,kind.equals("path")?2:7);
            swatch.getStyleClass().add("cutout-swatch-"+kind);
            Label label=new Label(I18n.text("cutout.internal.legend_"+kind),swatch);
            legend.getChildren().add(label);
        }
        help(legend,"cutout.internal.legend_help");return legend;
    }

    private static TableColumn<Opening,String> column(String title, java.util.function.Function<Opening,String> text) {
        var column = new TableColumn<Opening,String>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue()))); return column;
    }
    private static TextField field(String id,String text) {
        TextField field = new TextField(text); field.setId(id); field.setPrefColumnCount(7); field.setMinWidth(0); return field;
    }
    private static Label label(String text) { Label label = new Label(text); label.setWrapText(true); return label; }
    private static void help(Node node,String key) { ToolDescriptions.apply(node,I18n.text("cutout.internal.title"),I18n.text(key)); }
    private Button button(String key,String id,Runnable action,String help) {
        Button button = new Button(I18n.text(key)); button.setId(id); button.setOnAction(e -> action.run()); help(button,help); return button;
    }
    private List<Geometry> selected() { return table.getSelectionModel().getSelectedItems().stream().map(Opening::area).toList(); }
    private void selectionChanged() {
        previewReady.set(false);previewError="";invalidOpening=null;table.refresh();host.clear();
        length.setText(I18n.text("cutout.internal.length_pending"));
        validateParameters(); status.setText(I18n.text("cutout.internal.selected").formatted(selected().size()));
        if (selected().isEmpty()) debounce.stop(); else debounce.playFromStart();
    }
    private double diameter() { double value=CamNumericInput.number(diameter.getText(),"Tool Dia");
        if(value<=0)throw new IllegalArgumentException(I18n.text("cutout.internal.error.bad_parameters")); return value; }
    private double margin() { double value=CamNumericInput.number(margin.getText(),"Margin");
        if(value<0)throw new IllegalArgumentException(I18n.text("cutout.internal.error.bad_parameters")); return value; }
    private void preview() {
        var rows=List.copyOf(table.getSelectionModel().getSelectedItems());
        try {
            double dia=diameter(), offset=margin(); status.setText(I18n.text("cutout.internal.previewing"));
            host.preview(rows.stream().map(Opening::area).toList(),dia,offset,info -> {
                previewReady.set(true);status.setText(I18n.text("cutout.internal.preview_ready"));
                length.setText(I18n.text("cutout.internal.length").formatted(UiNumbers.fixed(info.length(),units),units));
                validateParameters();
            },issue -> {
                previewReady.set(false);previewError=issue.message();
                invalidOpening=issue.selectedIndex()>=0 && issue.selectedIndex()<rows.size() ? rows.get(issue.selectedIndex()) : null;
                if(invalidOpening!=null) {
                    previewError=I18n.text("cutout.internal.opening_error").formatted(invalidOpening.id(),previewError);
                    table.scrollTo(invalidOpening);
                }
                table.refresh();status.setText(I18n.text("cutout.internal.needs_correction"));showError();
            });
        } catch (RuntimeException invalid) { host.clear();previewError=invalid.getMessage();showError(); }
    }

    private void validateParameters() {
        parametersValid.set(false);parameterError="";
        for(TextField field:List.of(diameter,margin,cutZ,step))field.pseudoClassStateChanged(INVALID,false);
        try {
            checked(diameter,this::diameter);checked(margin,this::margin);
            var policy=MachiningZInput.currentPolicy();
            double z=checked(cutZ,()->CamNumericInput.number(cutZ.getText(),"Cut Z"));
            if(policy==org.flatcam.cam.gcode.MachiningZPolicy.STANDARD && z==0) {
                cutZ.pseudoClassStateChanged(INVALID,true);throw new IllegalArgumentException(I18n.text("machining.z.cut.zero"));
            }
            double pass=multiple.isSelected()?checked(step,()->CamNumericInput.number(step.getText(),"Depth / Pass")):settings.machining().depthPerPass();
            if(multiple.isSelected() && pass<=0) {
                step.pseudoClassStateChanged(INVALID,true);throw new IllegalArgumentException(I18n.text("cutout.internal.pass_positive"));
            }
            settings.machining().withZCoordinates(policy.travelZ(settings.machining().safeZ()),policy.cutZ(z),multiple.isSelected(),pass,policy);
            summary.setText(I18n.text("cutout.internal.summary").formatted(selected().size(),diameter.getText().trim(),units,
                    margin.getText().trim(),units,Double.toString(policy.cutZ(z)),units,
                    multiple.isSelected()?I18n.text("cutout.internal.summary_pass").formatted(step.getText().trim(),units):I18n.text("cutout.internal.single_pass")));
            parametersValid.set(true);
        } catch(RuntimeException invalid) {parameterError=invalid.getMessage();summary.setText(I18n.text("cutout.internal.summary_invalid"));}
        showError();
    }
    private double checked(TextField field,java.util.function.DoubleSupplier value) {
        try{return value.getAsDouble();}catch(RuntimeException invalid){field.pseudoClassStateChanged(INVALID,true);throw invalid;}
    }
    private void showError() {error.setText(parameterError.isBlank()?previewError:parameterError);}
    private void finishedInteraction() {interacting.set(false);status.setText(I18n.text("cutout.internal.stopped"));}
    private void pick() {
        var rows = List.copyOf(table.getItems());
        if (host.pick(rows.stream().map(Opening::area).toList(),index -> {
            Opening row=rows.get(index); int current=table.getItems().indexOf(row); if(current<0)return;
            if(table.getSelectionModel().isSelected(current))table.getSelectionModel().clearSelection(current);
            else table.getSelectionModel().select(current);
            table.scrollTo(current);
        },error::setText,this::finishedInteraction)) {
            interacting.set(true);status.setText(I18n.text("cutout.internal.pick_help"));
        }
        else error.setText(I18n.text("cutout.internal.unavailable"));
    }
    private void draw(Shape shape) {
        if (host.draw(shape,area -> {
            interacting.set(false);
            var opening=new Opening(nextId++,area,true); table.getItems().add(opening);
            table.getSelectionModel().clearAndSelect(table.getItems().size()-1); table.scrollTo(opening);
        },message -> {interacting.set(false);error.setText(message);},this::finishedInteraction)) {
            interacting.set(true);status.setText(I18n.text("cutout.internal.draw_prompt_"+shape.name().toLowerCase(java.util.Locale.ROOT)));
        }
        else error.setText(I18n.text("cutout.internal.unavailable"));
    }
    private void removeDrawn() {
        var remove = table.getSelectionModel().getSelectedItems().stream().filter(Opening::drawn).toList();
        table.getSelectionModel().clearSelection(); table.getItems().removeAll(remove);
    }
    private void generate(Consumer<CutoutToolPanel.Result> generate) {
        try {
            if(!previewReady.get() || !parametersValid.get() || interacting.get())return;
            if(selected().isEmpty())throw new IllegalArgumentException(I18n.text("cutout.internal.error.empty_selection"));
            var params=new CutoutParameters(diameter(),margin(),false,CutoutKind.PANEL,CutoutShape.FREEFORM,0,GapPattern.NONE);
            zInput.begin(); double cut=-zInput.cut(cutZ,"Cut Z");
            var machining=settings.machining().withZCoordinates(zInput.travel(Double.toString(settings.machining().safeZ()),"Travel Z"),
                    cut,multiple.isSelected(),multiple.isSelected()?CamNumericInput.number(step.getText(),"Depth / Pass")
                            :settings.machining().depthPerPass(),zInput.policy());
            debounce.stop(); host.stop(); error.setText("");
            generate.accept(new CutoutToolPanel.Result(params,CutoutToolPanel.GapType.BRIDGE,0,0,List.of(),
                    machining,null,profile.getValue(),settings.defaults(),selected()));
        } catch (RuntimeException invalid) { error.setText(invalid.getMessage()); }
    }
}
