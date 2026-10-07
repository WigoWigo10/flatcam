package org.flatcam.fx;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Structured tooltip text: formatting is local to each span, never parsed as HTML or Markdown. */
record TooltipContent(List<Span> spans) {
    private static final Pattern EMPHASIS = Pattern.compile(
            "\\b(?:mm/min ou in/min|mm ou in|C1 a C4|V-Dia|V-Angle|Cut Z|Shape V|Multi-Depth|Depth / Pass|"
            + "Dwell Time|Offset Z|Custom Offset|Tool Offset|Drill Slots|Last Drill|Extra Cut Length|Extra Cut|"
            + "Offset Value|Gap Type|Thin Depth|Start Z|End Z|Travel Z|Texto|Borracha|Mouse Bites|M-Bites|Laser_lines|Standard|Seed|Lines|Combo|Both|Exterior|Interior|"
            + "Climb|Conventional|Bridge|Thin|Clear|Isolation|General|Milling|Drilling|Paint|NCC|Cutout|"
            + "Save DB|Export DB|Import DB|Ctrl\\+[A-Z]|Shift\\+Enter|Shift\\+F3|Enter|F3|Esc|"
            + "Around|Over|Over Z|Rest Machining|None|MM|IN|DPI|RPM|segundos|graus)\\b");
    private static final Pattern PREFIX = Pattern.compile("(?m)^(Integração FX:|Atenção:|Unidades:|Atalho:|Atalhos:)");
    private static final Pattern TOKENS = Pattern.compile(PREFIX.pattern() + "|" + EMPHASIS.pattern(), Pattern.MULTILINE);
    enum Style { NORMAL, BOLD, ACCENT, NOTE }
    record Span(String text, Style style) { }

    TooltipContent {
        spans = List.copyOf(spans);
    }

    /** Shared, restrained formatting for help anywhere in the application. Never interprets user text as markup. */
    static TooltipContent describe(String text) {
        List<Span> spans = new ArrayList<>();
        String[] paragraphs = text.replaceAll("(?<=\\.) (?=\\p{Lu})", "\n\n").split("\n\n", -1);
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) spans.add(new Span("\n\n", Style.NORMAL));
            String paragraph = paragraphs[i];
            var matches = TOKENS.matcher(paragraph);
            int cursor = 0;
            while (matches.find()) {
                if (matches.start() > cursor) spans.add(new Span(paragraph.substring(cursor, matches.start()), Style.NORMAL));
                String token = matches.group();
                Style style = token.endsWith(":") ? (token.equals("Integração FX:") || token.equals("Atenção:")
                        ? Style.NOTE : Style.ACCENT) : Style.BOLD;
                spans.add(new Span(token, style));
                cursor = matches.end();
            }
            if (cursor < paragraph.length()) spans.add(new Span(paragraph.substring(cursor), Style.NORMAL));
        }
        return new TooltipContent(spans);
    }

    String plainText() {
        return spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());
    }

    /** Cells keep native tooltips because their text is refreshed as they are reused. */
    void installNative(javafx.scene.control.Tooltip tooltip, ThemeOption theme) {
        TextFlow flow = new TextFlow();
        flow.setPrefWidth(330); flow.setMaxWidth(330); flow.setLineSpacing(2);
        renderInto(flow, theme);
        tooltip.setText(plainText());
        tooltip.setGraphic(flow);
        tooltip.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        tooltip.setWrapText(true);
        tooltip.setStyle(theme.objectTooltipStyle());
    }

    void renderInto(TextFlow flow, ThemeOption theme) {
        flow.getChildren().clear();
        flow.setAccessibleText(plainText());
        for (Span span : spans) {
            Text text = new Text(span.text());
            text.setFill(Color.web(switch (span.style()) {
                case ACCENT -> theme.isDark() ? "#8ddcff" : "#005b79";
                case NOTE -> theme.isDark() ? "#ffd18c" : "#7a4300";
                default -> theme.tooltipPalette().text();
            }));
            text.setStyle("-fx-font-size: 12px; -fx-font-weight: "
                    + (span.style() == Style.NORMAL ? "normal;" : "bold;"));
            flow.getChildren().add(text);
        }
    }
}
