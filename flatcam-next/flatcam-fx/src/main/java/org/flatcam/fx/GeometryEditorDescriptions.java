package org.flatcam.fx;

import javafx.scene.Node;
import javafx.scene.control.MenuItem;

/** One explanation for the Geometry toolbar and its corresponding menu command. */
final class GeometryEditorDescriptions {
    private GeometryEditorDescriptions() { }

    static ToolDescriptions.Description of(String id) {
        return switch (id) {
            case "transform" -> new ToolDescriptions.Description(I18n.text("ui.28799639a7b3"),
                    I18n.text("ui.8e2720da0aa5") + of("rotate").text());
            case "rotate" -> new ToolDescriptions.Description(I18n.text("ui.05c2909c2313"),
                    I18n.text("ui.8a897f7430ef"));
            case "arc" -> new ToolDescriptions.Description(I18n.text("ui.252d17529360"),
                    I18n.text("ui.f10a004bde0c"));
            case "paint" -> new ToolDescriptions.Description("Paint Shape",
                    I18n.text("ui.146acb3956fe"));
            case "text" -> new ToolDescriptions.Description(I18n.text("ui.39409c7cadff"),
                    I18n.text("ui.b544cf9a2975"));
            case "eraser" -> new ToolDescriptions.Description(I18n.text("ui.d1701dbf4622"),
                    I18n.text("ui.dacea0b19fcf"));
            case "buffer" -> new ToolDescriptions.Description(I18n.text("ui.de63663a3c42"),
                    I18n.text("ui.01674ed7b2fe"));
            case "cut_path" -> new ToolDescriptions.Description(I18n.text("ui.32b69f96fbc3"),
                    I18n.text("ui.411e5fa9926d"));
            case "subtract" -> new ToolDescriptions.Description(I18n.text("ui.306dd9dc882b"),
                    I18n.text("ui.56b56b266b06"));
            case "union" -> new ToolDescriptions.Description(I18n.text("ui.dd11d305ef9e"),
                    I18n.text("ui.9b1a2d946edf"));
            case "intersection" -> new ToolDescriptions.Description(I18n.text("ui.18409a9f56cb"),
                    I18n.text("ui.57b36a64d212"));
            case "explode" -> new ToolDescriptions.Description(I18n.text("ui.09b64c236839"),
                    I18n.text("ui.d4184c5fcfc8"));
            case "move", "copy" -> new ToolDescriptions.Description(id.equals("move") ? I18n.text("ui.82ba228461bf") : I18n.text("ui.5569bb78f11f"),
                    I18n.text("ui.3bdb2db03e26")
                            + (id.equals("copy") ? I18n.text("ui.23b231b933a7") : I18n.text("ui.7932419a0028"))
                            + I18n.text("ui.dcec26227dfb"));
            default -> null;
        };
    }

    static void apply(Node node, String id) {
        var description = of(id);
        if (description != null) ToolDescriptions.apply(node, description.title(), description.text());
    }

    static void apply(MenuItem item, String id) {
        var description = of(id);
        if (description != null) ToolDescriptions.apply(item.getProperties(), description.title(), description.text());
    }
}
