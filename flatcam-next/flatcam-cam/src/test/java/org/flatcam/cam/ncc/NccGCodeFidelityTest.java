package org.flatcam.cam.ncc;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.ToolGeometry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.WKTReader;

/** Regression: round-off may merge unique coverage, but must not remove a pass. */
class NccGCodeFidelityTest {
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void linesAndPerToolSettingsRetainTheirTravelLengthWhenExported(String units) throws Exception {
        double mm = units.equals("IN") ? 1/25.4 : 1;
        Geometry copper = AffineTransformation.scaleInstance(mm,mm).transform(new WKTReader().read(
                "POLYGON ((0 0,30 0,30 20,0 20,0 0),(4 4,4 16,26 16,26 4,4 4))"));
        String original = copper.toText();
        var lines = new NccParameters(.5*mm,.4,mm,NccMethod.LINES,false,true,0);
        var multi = new NccParameters(List.of(.2*mm,mm),.4,mm,NccMethod.STANDARD,false,true,0,
                false,NccOrder.NONE,new NccBoundary.Itself(),List.of(),Map.of(.2*mm,
                new NccToolSettings(.25,NccMethod.STANDARD,false,true,.1*mm)));
        for (var params : List.of(lines,multi)) {
            var result = NccGenerator.generate(units,copper,params);
            List<ToolGeometry> tools = result.toolResults().stream()
                    .map(t -> new ToolGeometry(t.toolDiameter(),t.geometry())).toList();
            var job = GCodeGenerator.generateGeometryCncJob(units,tools,
                    new GeometryGCodeParameters(3*mm,.1*mm,false,0,300*mm,0,tools.size()>1));
            var parsed = GCodeToolpathParser.parse(job.gcode(),CancellationToken.none(),ProgressCallback.none());
            assertTrue(parsed.plotAvailable(),parsed.warning());
            double sourceLength = result.toolResults().stream().mapToDouble(t -> t.geometry().getLength()).sum();
            double parsedLength = parsed.cutCenterlines().getLength();
            assertEquals(sourceLength,parsedLength,sourceLength*.001);
            assertTrue(result.geometry().buffer(.003*mm).covers(parsed.cutCenterlines()));
            assertEquals(original,copper.toText());
        }
    }
}
