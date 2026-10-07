package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class ToolPreviewContentTest {
    @Test void replacingClosingOrSwitchingToLegacyOverlaysForgetsCategoryBindingsNotProjectColors() throws Exception {
        TerminalPanelTest.fx(() -> {
            var plot = new PlotAreaView();
            try {
                plot.resize(400, 300); plot.applyCss(); plot.layout();
                var geometry = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 8));
                Object source = new Object();
                plot.putLayer(source, PlotAreaView.LayerCategory.GERBER, geometry, Color.RED, Color.DARKRED, false);
                Color[] originalColors = plot.layerColors(source);
                var copper = new PlotAreaView.PreviewLayer(geometry, PlotAreaView.LayerCategory.GERBER, false);
                var holes = new PlotAreaView.PreviewLayer(geometry, PlotAreaView.LayerCategory.EXCELLON, false);
                plot.setToolPreviewContent(geometry, List.of(copper, holes));
                assertEquals(2, contents(plot).size());
                assertEquals(2, categoryBindings(plot));
                plot.applyTheme(ThemeOption.CLASSIC_DARK);
                plot.applyTheme(ThemeOption.CLASSIC_LIGHT);
                assertArrayEquals(originalColors, plot.layerColors(source));
                assertEquals(List.of(copper, holes), contents(plot));
                plot.setToolPreviewContent(geometry, List.of(copper));
                assertEquals(1, categoryBindings(plot), "removed category releases its display index");
                plot.setEditorContent(geometry); // 2-Sided/editor retain their existing single-content behavior
                assertTrue(contents(plot).isEmpty()); assertEquals(0, categoryBindings(plot));
                assertArrayEquals(originalColors, plot.layerColors(source));
                plot.setToolPreviewContent(geometry, List.of(copper, holes));
                plot.setToolPreviewContent(null, List.of());
                assertEquals(0, categoryBindings(plot));
                plot.setToolPreviewContent(geometry, List.of(holes));
                plot.clearLayers(); assertTrue(contents(plot).isEmpty()); assertEquals(0, categoryBindings(plot));
            } finally { plot.dispose(); }
            return null;
        });
    }
    private static List<?> contents(PlotAreaView plot) throws Exception {
        var field = PlotAreaView.class.getDeclaredField("toolPreviewContents"); field.setAccessible(true);
        return (List<?>) field.get(plot);
    }
    private static long categoryBindings(PlotAreaView plot) throws Exception {
        var field = PlotAreaView.class.getDeclaredField("indexCache"); field.setAccessible(true);
        var indexes = AsyncPlotIndexCache.class.getDeclaredField("indexes"); indexes.setAccessible(true);
        return ((Map<?, ?>) indexes.get(field.get(plot))).keySet().stream()
                .filter(key -> key.toString().contains("PreviewOverlayKey")).count();
    }
}
