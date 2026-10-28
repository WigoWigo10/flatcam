package org.flatcam.fx;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

final class RecentFilesMenu {
    private RecentFilesMenu() { }
    static Menu build(RecentFiles history, Consumer<RecentFiles.Entry> open, BiFunction<String,Double,Node> icon) {
        Menu root = new Menu(I18n.text("ui.3068eba03d59")); root.setGraphic(icon.apply("recent_files.png",16.0)); root.setId("recent-files-menu");
        for (boolean projects : new boolean[]{true,false}) {
            Menu category = new Menu(projects ? I18n.text("ui.b309a28679e9") : I18n.text("ui.ddfe38802540"));
            category.setId(projects ? "recent-projects-menu" : "recent-cam-menu");
            Runnable refresh = () -> refresh(category,history,projects,open,icon);
            refresh.run(); category.setOnShowing(event -> refresh.run()); root.getItems().add(category);
        }
        return root;
    }
    static void refresh(Menu category, RecentFiles history, boolean projects, Consumer<RecentFiles.Entry> open,
                        BiFunction<String,Double,Node> icon) {
        category.getItems().clear();
        var entries = history.entries(projects);
        if (entries.isEmpty()) {
            MenuItem empty = new MenuItem(I18n.text("ui.e48b3f0d06b3")); empty.setDisable(true); category.getItems().add(empty);
        }
        for (var entry : entries) {
            MenuItem item = new MenuItem(entry.kind().label + " — " + entry.path().getFileName());
            item.setGraphic(icon.apply(entry.kind().icon,16.0));
            ToolDescriptions.apply(item.getProperties(),entry.kind().label,
                    I18n.text("ui.80dd2d2e4bba") + entry.path()
                    + (projects ? I18n.text("ui.0e34ecedb356")
                                : I18n.text("ui.dc9f600c28f4")));
            item.setOnAction(event -> open.accept(entry)); category.getItems().add(item);
        }
        MenuItem clear = new MenuItem(I18n.text("ui.a24ee0cafc12")); clear.setDisable(entries.isEmpty());
        ToolDescriptions.apply(clear.getProperties(),I18n.text("ui.a24ee0cafc12"),I18n.text("ui.5bd980fce832"));
        clear.setOnAction(event -> { history.clear(projects); refresh(category,history,projects,open,icon); });
        category.getItems().addAll(new SeparatorMenuItem(),clear);
    }
}
