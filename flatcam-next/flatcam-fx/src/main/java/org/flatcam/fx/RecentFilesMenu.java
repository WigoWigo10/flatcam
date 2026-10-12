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
        Menu root = new Menu("Recentes"); root.setGraphic(icon.apply("recent_files.png",16.0)); root.setId("recent-files-menu");
        for (boolean projects : new boolean[]{true,false}) {
            Menu category = new Menu(projects ? "Projetos recentes" : "Arquivos recentes");
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
            MenuItem empty = new MenuItem("Nenhum item recente"); empty.setDisable(true); category.getItems().add(empty);
        }
        for (var entry : entries) {
            MenuItem item = new MenuItem(entry.kind().label + " — " + entry.path().getFileName());
            item.setGraphic(icon.apply(entry.kind().icon,16.0));
            ToolDescriptions.apply(item.getProperties(),entry.kind().label,
                    "Reabre usando o mesmo tipo de importação.\n\nArquivo: " + entry.path()
                    + (projects ? "\n\nSubstitui o projeto atual após validar o arquivo. Salve alterações antes de abrir outro projeto."
                                : "\n\nAdiciona ao projeto atual. São usados os parâmetros de importação e unidades atuais."));
            item.setOnAction(event -> open.accept(entry)); category.getItems().add(item);
        }
        MenuItem clear = new MenuItem("Limpar histórico"); clear.setDisable(entries.isEmpty());
        ToolDescriptions.apply(clear.getProperties(),"Limpar histórico","Remove somente esta lista de recentes do FX. Não apaga arquivos nem altera o histórico do Python.");
        clear.setOnAction(event -> { history.clear(projects); refresh(category,history,projects,open,icon); });
        category.getItems().addAll(new SeparatorMenuItem(),clear);
    }
}
