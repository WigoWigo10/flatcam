package org.flatcam.fx;

import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;

/**
 * What each tool of the Tools menu does, in a sentence or two, for its tooltip. The texts follow the tooltips of the
 * Python tools (appTools/*.py), shortened.
 */
final class ToolDescriptions {

    record Description(String title, String text) {
    }

    private static final Map<String, Description> TOOLS = Map.ofEntries(
            Map.entry("double_sided", new Description(I18n.text("ui.b595bfb73393"),
                    I18n.text("ui.c6f75fe3b23b"))),
            Map.entry("align", new Description("Align Objects Tool",
                    I18n.text("ui.3b96feaf8903"))),
            Map.entry("extract_drills", new Description("Extract Drills Tool",
                    I18n.text("ui.b2fea9c879ea"))),
            Map.entry("cutout", new Description("Cutout Tool",
                    I18n.text("ui.0b8e9011cee9"))),
            Map.entry("ncc", new Description("NCC Tool",
                    I18n.text("ui.2d1f657564d1"))),
            Map.entry("paint", new Description("Paint Tool",
                    I18n.text("ui.bb56be5daffe"))),
            Map.entry("isolation", new Description("Isolation Tool",
                    I18n.text("ui.f8f0c90e86c1"))),
            Map.entry("drilling", new Description("Drilling Tool",
                    I18n.text("ui.963280419814"))),
            Map.entry("panelize", new Description("Panelize Tool",
                    I18n.text("ui.4b12f23a90a9"))),
            Map.entry("film", new Description("Film Tool",
                    I18n.text("ui.c108c58fb278"))),
            Map.entry("solderpaste", new Description("SolderPaste Tool",
                    I18n.text("ui.81d2799b4fdb"))),
            Map.entry("subtract", new Description("Subtract Tool",
                    I18n.text("ui.102074b7e873"))),
            Map.entry("rules", new Description("Rules Check Tool",
                    I18n.text("ui.940c6a7360e7"))),
            Map.entry("optimal", new Description("Optimal Tool",
                    I18n.text("ui.9d6b8e3e33d1"))),
            Map.entry("calculators", new Description("Calculators Tool",
                    I18n.text("ui.d01ce8ad1f9e"))),
            Map.entry("transform", new Description("Transform Tool",
                    I18n.text("ui.f6a98d435659"))),
            Map.entry("qrcode", new Description("QRCode Tool",
                    I18n.text("ui.f751f02d6d3b"))),
            Map.entry("copper_thieving", new Description("Copper Thieving Tool",
                    I18n.text("ui.089547945e41"))),
            Map.entry("fiducials", new Description("Fiducials Tool",
                    I18n.text("ui.6c088f823ca7"))),
            Map.entry("calibration", new Description("Calibration Tool",
                    I18n.text("ui.8dcee48184eb"))),
            Map.entry("punch", new Description("Punch Gerber Tool",
                    I18n.text("ui.1652539bc971"))),
            Map.entry("invert", new Description("Invert Gerber Tool",
                    I18n.text("ui.971d9064a759"))),
            Map.entry("corners", new Description("Corner Markers Tool",
                    I18n.text("ui.8d79b657e2f0"))),
            Map.entry("etch", new Description("Etch Compensation Tool",
                    I18n.text("ui.b1d3c100731a"))));

    private ToolDescriptions() {
    }

    static Description of(String id) {
        return TOOLS.get(id);
    }

    /** Puts the tip of a tool into the properties a menu item or control keeps it in. */
    static void apply(Map<Object, Object> properties, String id) {
        Description description = TOOLS.get(id);
        if (description != null) {
            apply(properties, description.title(), description.text());
        }
    }

    /** A tip for a command that is not one of the tools (conversions, joins...). */
    static void apply(Map<Object, Object> properties, String title, String text) {
        properties.put(FluidTooltips.TITLE_KEY, title);
        properties.put(FluidTooltips.TEXT_KEY, text);
        properties.put(FluidTooltips.CONTENT_KEY, TooltipContent.describe(text));
    }

    /** Authored help is immediately rich and accessible, without a competing native popup. */
    static void apply(Node node, String title, String text) {
        if (node instanceof Control control) control.setTooltip(null);
        if (node instanceof Labeled label && (label.getText() == null || label.getText().isBlank()))
            node.setAccessibleText(title);
        node.setAccessibleHelp(text);
        apply(node.getProperties(), title, text);
    }
}
