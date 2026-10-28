package org.flatcam.fx;

import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Read-only guide in a reusable center tab. No browser, network, scripts or project mutations. */
final class HelpGuidePane extends BorderPane {
    private final TextField search=new TextField();
    private final ListView<HelpGuideCatalog.Topic> topics=new ListView<>();
    private final ScrollPane article=new ScrollPane();
    private final Label results=new Label();
    private String selectedTopic="workflow";

    HelpGuidePane(Function<String,Node> icons) {
        setId("help-guide");getStyleClass().add("help-guide");setPadding(new Insets(14));
        Label title=label(I18n.text("guide.title"),"tool-title");
        Label introduction=label(I18n.text("guide.intro"),"guide-muted");
        search.setId("guide-search");search.setPromptText(I18n.text("guide.search"));
        search.setMinWidth(0);search.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(search,Priority.ALWAYS);
        ToolDescriptions.apply(search,I18n.text("guide.search"),I18n.text("guide.search.help"));
        Button clear=new Button(I18n.text("guide.clear"));clear.setId("guide-clear");clear.setOnAction(e->search.clear());
        ToolDescriptions.apply(clear,I18n.text("guide.clear"),I18n.text("guide.clear.help"));
        VBox header=new VBox(8,title,introduction,new HBox(8,search,clear),results);
        header.setPadding(new Insets(0,0,12,0));setTop(header);

        topics.setId("guide-topics");topics.setMinSize(125,95);topics.setPrefWidth(235);
        ToolDescriptions.apply(topics,I18n.text("guide.topics"),I18n.text("guide.topics.help"));
        topics.setCellFactory(view->new ListCell<>() {
            { setWrapText(true); }
            @Override protected void updateItem(HelpGuideCatalog.Topic topic,boolean empty) {
                super.updateItem(topic,empty);
                setText(empty || topic==null ? null : topic.title());
                setGraphic(empty || topic==null ? null : icons.apply(topic.icon()));
            }
        });
        article.setId("guide-article");article.setFitToWidth(true);article.setMinSize(0,140);
        SplitPane split=new SplitPane(topics,article);split.setId("guide-split");split.setDividerPositions(.25);
        widthProperty().addListener((o,was,width)-> {
            var orientation=width.doubleValue()<650 ? Orientation.VERTICAL : Orientation.HORIZONTAL;
            if(split.getOrientation()!=orientation) {split.setOrientation(orientation);split.setDividerPositions(.29);}
        });
        setCenter(split);
        topics.getSelectionModel().selectedItemProperty().addListener((o,was,topic)-> {if(topic!=null) show(topic);});
        search.textProperty().addListener((o,was,query)->filter(query));
        addEventFilter(KeyEvent.KEY_PRESSED,event-> {
            if(event.isShortcutDown() && !event.isAltDown() && !event.isShiftDown() && event.getCode()==KeyCode.F) {
                search.requestFocus();search.selectAll();event.consume();
            }
        });
        filter("");
    }

    String selectedTopic() { return selectedTopic; }
    Node articleContent() { return article.getContent(); }

    private void filter(String query) {
        var found=HelpGuideCatalog.search(query);topics.getItems().setAll(found);
        results.setText(String.format(java.util.Locale.ROOT,I18n.text("guide.results"),found.size(),HelpGuideCatalog.TOPICS.size()));
        if(found.isEmpty()) {
            topics.getSelectionModel().clearSelection();
            VBox empty=new VBox(8,label(I18n.text("guide.empty"),"guide-heading"),label(I18n.text("guide.empty.help"),"guide-muted"));
            empty.setPadding(new Insets(16));article.setContent(empty);article.setVvalue(0);return;
        }
        var previous=found.stream().filter(t->t.id().equals(selectedTopic)).findFirst().orElse(found.getFirst());
        topics.getSelectionModel().select(previous);
        // ListView can retain its item on an equivalent result list without emitting a selection event.
        show(previous);
    }

    void selectTopic(String id) {
        var topic=HelpGuideCatalog.find(id);
        if(topic==null)throw new IllegalArgumentException("Unknown guide topic: "+id);
        search.clear();topics.getSelectionModel().select(topic);topics.scrollTo(topic);
    }

    private void show(HelpGuideCatalog.Topic topic) {
        selectedTopic=topic.id();
        VBox page=new VBox(12);page.setId("guide-page-"+topic.id());page.setMinWidth(0);page.setPadding(new Insets(16));
        page.getChildren().addAll(label(topic.category(),"guide-muted"),label(topic.title(),"guide-title"),rich(topic.text("summary")));
        section(page,"before",rich(topic.text("before")));
        VBox steps=new VBox(9);int number=1;
        for(String step:topic.text("steps").split("\n")) {
            Label index=label(number++ + ".","guide-step");index.setMinWidth(24);
            var body=rich(step);HBox.setHgrow(body,Priority.ALWAYS);steps.getChildren().add(new HBox(8,index,body));
        }
        section(page,"steps",steps);section(page,"check",rich(topic.text("check")));
        VBox note=new VBox(6,label(I18n.text("guide.note"),"guide-note-title"),rich(topic.text("note")));
        note.getStyleClass().add("guide-note");page.getChildren().add(note);
        FlowPane links=new FlowPane(8,8);
        for(String id:topic.related()) {
            var related=HelpGuideCatalog.find(id);Button link=new Button(related.title());link.setId("guide-link-"+id);
            link.setWrapText(true);link.setMinWidth(0);link.setMaxWidth(300);link.getStyleClass().add("guide-link");
            link.setOnAction(e->selectTopic(id));
            ToolDescriptions.apply(link,related.title(),I18n.text("guide.link.help"));links.getChildren().add(link);
        }
        section(page,"related",links);article.setContent(page);article.setVvalue(0);article.setHvalue(0);
    }

    private static void section(VBox page,String heading,Node body) {
        VBox section=new VBox(7,label(I18n.text("guide.section."+heading),"guide-heading"),body);section.setMinWidth(0);
        page.getChildren().add(section);
    }

    private static Label label(String text,String style) {
        Label label=new Label(text);label.setWrapText(true);label.setMinWidth(0);label.getStyleClass().add(style);return label;
    }

    /** Only authored bold spans are interpreted. No HTML or executable/link markup. */
    static TextFlow rich(String body) {
        TextFlow flow=new TextFlow();flow.setMinWidth(0);flow.setLineSpacing(3);
        flow.setAccessibleRole(AccessibleRole.TEXT);flow.setAccessibleText(body.replace("**",""));
        String[] spans=body.split("\\*\\*",-1);
        for(int i=0;i<spans.length;i++) {
            Text text=new Text(spans[i]);text.getStyleClass().add("guide-prose");
            if(i%2==1)text.getStyleClass().add("guide-emphasis");flow.getChildren().add(text);
        }
        return flow;
    }
}
