package org.flatcam.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Help > Shortcut List (Python's on_shortcut_list / "Key Shortcut List" tab), listing only the keys this
 * port actually handles - MainWindow's scene filter and project tree, PlotAreaView, CodeEditor,
 * TerminalPanel and ToolsDatabasePanel. Python's own list has many more (its editors' tool letters, the
 * object-type hotkeys); those are not offered here until they are implemented. Keep this table in step
 * with those handlers.
 */
final class ShortcutListPanel extends VBox {

    record Shortcut(String keys, String action) { }

    static final Map<String, List<Shortcut>> GROUPS = groups();

    private static Map<String, List<Shortcut>> groups() {
        Map<String, List<Shortcut>> groups = new LinkedHashMap<>();
        groups.put("Geral", List.of(
                new Shortcut("Ctrl+N", "Novo projeto"),
                new Shortcut("Ctrl+S", "Salvar projeto (.fcnproj atual; senão pergunta o destino)"),
                new Shortcut("Ctrl+Shift+S", "Salvar projeto como"),
                new Shortcut("Ctrl+P", "Imprimir como PDF"),
                new Shortcut("Ctrl+A", "Selecionar todos os objetos")));
        groups.put("Plot e vista", List.of(
                new Shortcut("Roda do mouse", "Zoom no cursor"),
                new Shortcut("Arrastar com botão direito ou do meio", "Mover a vista"),
                new Shortcut("= ou + (teclado numérico)", "Aproximar 1,5×"),
                new Shortcut("- (teclado numérico também)", "Afastar 1,5×"),
                new Shortcut("G", "Alternar snap da grade"),
                new Shortcut("O", "Definir origem (clique no Plot)"),
                new Shortcut("Shift+O", "Mover seleção para a origem"),
                new Shortcut("J", "Ir para localização"),
                new Shortcut("Shift+J", "Localizar no objeto"),
                new Shortcut("Ctrl+M", "Medir distância"),
                new Shortcut("Shift+M", "Distância mínima entre dois objetos"),
                new Shortcut("Ctrl+Z / Ctrl+Y", "Desfazer / refazer movimento de objetos no Plot"),
                new Shortcut("Esc", "Cancelar escolha de ponto ou posicionamento")));
        groups.put("Árvore do projeto", List.of(
                new Shortcut("Enter", "Abrir Propriedades do objeto"),
                new Shortcut("F2", "Renomear"),
                new Shortcut("Delete", "Excluir selecionados"),
                new Shortcut("Espaço", "Alternar Plot dos selecionados"),
                new Shortcut("Esc", "Limpar seleção"),
                new Shortcut("Ctrl+C", "Copiar selecionados")));
        groups.put("Inspeção de trajeto CNC", List.of(
                new Shortcut("← / ↑", "Passo anterior"),
                new Shortcut("→ / ↓", "Próximo passo"),
                new Shortcut("Espaço", "Percorrer / pausar"),
                new Shortcut("Esc", "Sair da inspeção")));
        groups.put("Editores Geometry/Excellon/Gerber", List.of(
                new Shortcut("Ctrl+Z / Ctrl+Y", "Desfazer / refazer"),
                new Shortcut("Delete", "Excluir forma ou furo selecionado"),
                new Shortcut("Enter", "Concluir trilha"),
                new Shortcut("Backspace", "Voltar um segmento da trilha"),
                new Shortcut("T / R", "Alternar modo de dobra da trilha"),
                new Shortcut("G", "Alternar snap da grade durante a trilha")));
        groups.put("Editor de código e scripts", List.of(
                new Shortcut("Ctrl+F", "Buscar"),
                new Shortcut("F3 / Shift+F3", "Próxima / anterior ocorrência"),
                new Shortcut("Enter / Shift+Enter (na busca)", "Próxima / anterior ocorrência"),
                new Shortcut("Esc", "Fechar a busca")));
        groups.put("Terminal Tcl", List.of(
                new Shortcut("Enter", "Executar comando"),
                new Shortcut("↑ / ↓", "Histórico de comandos"),
                new Shortcut("Esc", "Cancelar o script em execução")));
        groups.put("Tools Database", List.of(
                new Shortcut("Ctrl+S", "Salvar a base"),
                new Shortcut("Ctrl+F", "Buscar"),
                new Shortcut("Delete", "Excluir ferramenta selecionada")));
        return java.util.Collections.unmodifiableMap(groups);
    }

    ShortcutListPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        Label title = new Label("Lista de Atalhos");
        title.getStyleClass().add("tool-title");
        Label note = new Label("Atalhos de teclado e mouse implementados no FlatCAM FX. As teclas do Plot não agem "
                + "enquanto um campo de texto tem o foco. Letras de ferramentas dos editores do Python ainda não existem aqui.");
        note.setWrapText(true);
        TableView<List<String>> table = new TableView<>();
        table.setId("shortcut-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        String[] headers = {"Área", "Teclas", "Ação"};
        for (int index = 0; index < headers.length; index++) {
            int column = index;
            TableColumn<List<String>, String> col = new TableColumn<>(headers[index]);
            col.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().get(column)));
            col.setSortable(false);
            col.setPrefWidth(index == 2 ? 360 : 190);
            table.getColumns().add(col);
        }
        GROUPS.forEach((group, shortcuts) -> shortcuts.forEach(shortcut ->
                table.getItems().add(List.of(group, shortcut.keys(), shortcut.action()))));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, note, table);
    }
}
