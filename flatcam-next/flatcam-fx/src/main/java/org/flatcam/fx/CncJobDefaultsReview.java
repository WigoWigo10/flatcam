package org.flatcam.fx;

import java.util.*;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.flatcam.app.project.CncJobDefaults;

/** Job-wide DB suggestions: agreement autofills explicit fields; conflicts require an explicit user choice. */
final class CncJobDefaultsReview {
    private final Map<Integer, CncJobDefaults> tools = new LinkedHashMap<>();
    private final Consumer<CncJobDefaults> apply;
    private final Label notice = new Label();
    private final Button confirm = new Button(I18n.text("ui.a69e44da3f1f"));
    private final VBox view = new VBox(6, notice, confirm);
    private boolean conflicted;
    private Set<Integer> activeIds;

    CncJobDefaultsReview(String prefix, Consumer<CncJobDefaults> apply) {
        this.apply = apply;
        notice.setId(prefix + "-common-db-notice"); notice.setWrapText(true);
        view.visibleProperty().bind(notice.textProperty().isNotEmpty());
        view.managedProperty().bind(view.visibleProperty());
        confirm.setId(prefix + "-common-db-confirm"); confirm.setMaxWidth(Double.MAX_VALUE);
        confirm.setTooltip(new Tooltip(I18n.text("ui.a5c0d152643c")));
        confirm.setOnAction(event -> {
            tools.clear(); conflicted = false; confirm.setVisible(false); confirm.setManaged(false);
            notice.setText(I18n.text("ui.12b5164d31c6"));
        });
        clear();
    }
    VBox view() { return view; }
    void clear() { tools.clear(); conflicted = false; updateVisibility(false); notice.setText(""); }
    void replaceAll(Map<Integer, CncJobDefaults> imported) { tools.clear(); tools.putAll(imported); refresh(); }
    void replace(int id, CncJobDefaults defaults) {
        if (defaults.isEmpty()) tools.remove(id); else tools.put(id, defaults);
        refresh();
    }
    void setActiveIds(Set<Integer> ids) { activeIds = Set.copyOf(ids); refresh(); }
    void assertResolved() {
        if (conflicted) throw new IllegalArgumentException(I18n.text("ui.07c6437f5084"));
    }
    private void refresh() {
        var selected = new LinkedHashMap<Integer, CncJobDefaults>();
        tools.forEach((id, defaults) -> { if (activeIds == null || activeIds.contains(id)) selected.put(id, defaults); });
        var resolved = CncJobDefaults.resolve(selected);
        apply.accept(resolved.agreed());
        conflicted = !resolved.conflicts().isEmpty();
        notice.setText(conflicted ? I18n.text("ui.7fb35e301691") + resolved.description() + I18n.text("ui.19380c020ae9")
                : selected.isEmpty() ? "" : I18n.text("ui.49ed97b8732d"));
        updateVisibility(conflicted);
    }
    private void updateVisibility(boolean conflict) {
        confirm.setVisible(conflict); confirm.setManaged(conflict);
    }
}
