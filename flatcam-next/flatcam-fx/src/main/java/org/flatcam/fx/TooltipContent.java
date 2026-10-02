package org.flatcam.fx;

import java.util.List;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Structured tooltip text: formatting is local to each span, never parsed as HTML or Markdown. */
record TooltipContent(List<Span> spans) {
    enum Style { NORMAL, BOLD, ACCENT, NOTE }
    record Span(String text, Style style) { }

    TooltipContent {
        spans = List.copyOf(spans);
    }

    String plainText() {
        return spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());
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
