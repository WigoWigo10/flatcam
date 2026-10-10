package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;

@EnabledOnOs(OS.WINDOWS)
class PlotRenderDiagnosticsTest {
    @Test void observerSurvivesProjectReplacementButNotViewportDisposal() throws Exception {
        TerminalPanelTest.fx(() -> {
            var view = new PlotAreaView();
            try {
                view.resize(500, 300);
                var samples = new ArrayList<PlotAreaView.RenderSample>();
                view.setRenderObserver(samples::add);
                view.putLayer("a", PlotAreaView.LayerCategory.GERBER,
                        new GeometryFactory().toGeometry(new Envelope(40, 60, 30, 50)), Color.RED, Color.RED, false);
                assertTrue(view.renderReady());
                assertFalse(view.renderFailed());
                view.clearLayers();
                assertTrue(samples.size() >= 2);
                assertTrue(samples.stream().allMatch(sample -> sample.commandsNanos() >= 0 && sample.ready() && !sample.failed()));
                int count = samples.size();
                view.dispose(); view.clearLayers();
                assertEquals(count, samples.size());
                assertFalse(view.renderReady());
            } finally { view.dispose(); }
            return null;
        });
    }
}
