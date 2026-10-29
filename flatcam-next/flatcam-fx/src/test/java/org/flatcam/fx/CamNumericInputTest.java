package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.locationtech.jts.geom.*;

@EnabledOnOs(OS.WINDOWS)
class CamNumericInputTest {
    static Stream<Arguments> inputs() {
        return Stream.of("NCC", "Isolation").flatMap(tool -> Stream.of("MM", "IN").flatMap(units ->
                Stream.of("pt-BR", "en-US").flatMap(locale -> Stream.of(false, true).flatMap(comma ->
                        Stream.of(false, true).map(commit -> Arguments.of(tool, units, locale, comma, commit))))));
    }
    static List<Node> nodes(Node root) {
        var result = new ArrayList<Node>(); result.add(root);
        if (root instanceof Parent p) for (Node child : p.getChildrenUnmodifiable()) result.addAll(nodes(child));
        return result;
    }
    static Node form(String tool, String units, AtomicReference<Object> output) {
        var geometry = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 8));
        var item = new TreeItem<String>("board");
        Node form;
        if (tool.equals("NCC")) {
            var source = new NccToolPanel.SourceCandidate(item, "board", units, true, geometry);
            form = NccToolPanel.build(List.of(source), source, List.of(), (s,a,d,c) -> false,
                    () -> {}, List::of, output::set, () -> {});
        } else {
            var source = new IsolationToolPanel.SourceCandidate(item,
                    GerberImage.of(units, Map.of(), geometry, geometry.getBoundary(), Map.of()));
            form = IsolationToolPanel.build(List.of(source), source, List.of(), (s,a,d,c) -> false,
                    () -> {}, List::of, output::set, () -> {});
        }
        var root = new VBox(form); new Scene(root, 540, 1400); root.applyCss(); root.layout(); return form;
    }
    @ParameterizedTest @MethodSource("inputs")
    void addAndGenerateUseExactEnteredDiameter(String tool, String units, String language, boolean comma, boolean commit) throws Exception {
        TerminalPanelTest.fx(() -> {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag(language));
                var output = new AtomicReference<Object>(); var form = form(tool, units, output);
                var spinner = (Spinner<Double>) nodes(form).stream().filter(n -> n instanceof Spinner<?> s
                        && s.getValueFactory() instanceof SpinnerValueFactory.DoubleSpinnerValueFactory v
                        && v.getMin() == .0001 && v.getMax() == 10000).findFirst().orElseThrow();
                double expected = units.equals("MM") ? .1981234567 : .00781234567;
                String typed = Double.toString(expected); if (comma) typed = typed.replace('.', ',');
                spinner.getEditor().setText(typed);
                if (commit) spinner.commitValue();
                String addKey = tool.equals("NCC") ? "ui.967bcf34a913" : "ui.8f1fc115deb7";
                ((Button) nodes(form).stream().filter(n -> n instanceof Button b && b.getText().equals(I18n.text(addKey)))
                        .findFirst().orElseThrow()).fire();
                ((Button) form.lookup(tool.equals("NCC") ? "#ncc-generate" : "#isolation-generate")).fire();
                assertNotNull(output.get(), tool + " " + language + " " + typed);
                double actual = output.get() instanceof NccToolPanel.Result ncc ? ncc.parameters().toolDiameters().getFirst()
                        : ((IsolationToolPanel.Result) output.get()).tools().getFirst().toolDiameter();
                assertEquals(expected, actual, "Confirmed text must not round the generator input");
                return null;
            } finally { Locale.setDefault(previous); }
        });
    }
    @Test void nccMarginAndOverlapRetainPrecisionOnCommit() throws Exception {
        TerminalPanelTest.fx(() -> {
            var output = new AtomicReference<Object>(); var form = form("NCC", "MM", output);
            for (var entry : Map.of("#ncc-margin", "1.23456789", "#ncc-overlap", "37.5123456").entrySet()) {
                var spinner = (Spinner<?>) form.lookup(entry.getKey()); spinner.getEditor().setText(entry.getValue()); spinner.commitValue();
            }
            ((Button) form.lookup("#ncc-generate")).fire();
            var p = ((NccToolPanel.Result) output.get()).parameters();
            assertEquals(1.23456789, p.margin());
            assertEquals(37.5123456 / 100, p.toolSettings().values().iterator().next().overlapFraction());
            return null;
        });
    }
    @Test void invalidAndOutOfRangeDraftsAreNeverSilentlyClamped() throws Exception {
        TerminalPanelTest.fx(() -> {
            var spinner = CamNumericInput.spinner(.0001, 10000, .3, .1);
            for (String invalid : List.of("bad", "NaN", "Infinity", "-1", "10001", "0.00001")) {
                spinner.getEditor().setText(invalid); spinner.commitValue();
                assertEquals(invalid, spinner.getEditor().getText()); assertEquals(.3, spinner.getValue());
                assertThrows(IllegalArgumentException.class, () -> CamNumericInput.read(spinner, "Diameter"));
                spinner.increment(); assertEquals(invalid, spinner.getEditor().getText());
            }
            spinner.getEditor().setText("0,1981234567"); spinner.commitValue(); spinner.increment();
            assertEquals(.1981234567 + .1, spinner.getValue());
            return null;
        });
    }
    @Test void restHonorsPreferencesOnOpenAndReset() throws Exception {
        TerminalPanelTest.fx(() -> {
            ToolDefaults.set("ncc.connect", true, "false"); ToolDefaults.set("ncc.contour", true, "false");
            try {
                var form = form("NCC", "MM", new AtomicReference<>());
                var connect = (CheckBox) form.lookup("#ncc-rest-connect");
                var contour = (CheckBox) form.lookup("#ncc-rest-contour");
                assertFalse(connect.isSelected()); assertFalse(contour.isSelected());
                connect.setSelected(true); contour.setSelected(true);
                ((Button) nodes(form).stream().filter(n -> n instanceof Button b && b.getText().equals("Reset Tool")).findFirst().orElseThrow()).fire();
                assertFalse(connect.isSelected()); assertFalse(contour.isSelected());
                return null;
            } finally { ToolDefaults.resetAll(); }
        });
    }
}
