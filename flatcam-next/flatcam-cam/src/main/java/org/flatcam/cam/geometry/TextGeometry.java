package org.flatcam.cam.geometry;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/** System-font outlines, not raster text. No JavaFX or project mutation. */
public final class TextGeometry {
    private TextGeometry() { }

    public record Parameters(String text, String family, double size, boolean bold, boolean italic, String units) {
        public Parameters {
            if (text == null || family == null) throw new IllegalArgumentException("Informe texto e fonte.");
            if (text.isBlank() || text.length() > 512)
                throw new IllegalArgumentException("Informe texto visivel, com no maximo 512 caracteres.");
            if (family.isBlank()) throw new IllegalArgumentException("Escolha uma fonte.");
            if (!Double.isFinite(size) || size < 0.1 || size > 1000)
                throw new IllegalArgumentException("Tamanho deve estar entre 0.1 e 1000.");
            if (!"MM".equalsIgnoreCase(units) && !"IN".equalsIgnoreCase(units))
                throw new IllegalArgumentException("Unidade do texto deve ser MM ou IN.");
        }
    }

    public static List<String> fontFamilies() {
        return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static Geometry generate(Parameters p, CancellationToken cancellation, ProgressCallback progress) {
        cancellation.throwIfCancellationRequested();
        if (fontFamilies().stream().noneMatch(name -> name.equalsIgnoreCase(p.family())))
            throw new IllegalArgumentException("Fonte nao instalada: " + p.family());
        int style = (p.bold() ? Font.BOLD : Font.PLAIN) | (p.italic() ? Font.ITALIC : Font.PLAIN);
        Font font = new Font(p.family(), style, 1).deriveFont((float) p.size());
        String normalized = p.text().replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        if (font.canDisplayUpTo(normalized.replace("\n", "")) >= 0)
            throw new IllegalArgumentException("A fonte nao possui todos os caracteres deste texto.");
        var context = new FontRenderContext(null, true, true);
        Area outlines = new Area();
        String[] lines = normalized.split("\n", -1);
        double lineHeight = font.getLineMetrics("Ag", context).getHeight();
        for (int i = 0; i < lines.length; i++) {
            cancellation.throwIfCancellationRequested();
            if (!lines[i].isBlank()) outlines.add(new Area(new TextLayout(lines[i], font, context)
                    .getOutline(AffineTransform.getTranslateInstance(0, i * lineHeight))));
            progress.report(0.1 + 0.2 * (i + 1) / lines.length);
        }
        // ParseFont.py scales FreeType coordinates in 1/64 font units by these constants.
        double scale = 64 * (p.units().equalsIgnoreCase("MM") ? 0.0080187969924812 : 0.00031570066);
        var transform = AffineTransform.getScaleInstance(scale, -scale);
        // Flatness is in project units; 0.02 font units gives resolution independent of MM/IN.
        PathIterator path = outlines.getPathIterator(transform, scale * 0.02);
        var factory = new GeometryFactory();
        List<Geometry> contours = new ArrayList<>();
        List<Coordinate> points = new ArrayList<>();
        double[] coordinates = new double[6];
        int count = 0;
        while (!path.isDone()) {
            cancellation.throwIfCancellationRequested();
            if (++count > 100_000) throw new IllegalArgumentException("Texto excede 100.000 pontos; reduza o texto/tamanho.");
            int segment = path.currentSegment(coordinates);
            if (segment == PathIterator.SEG_MOVETO) {
                points = new ArrayList<>(); points.add(new Coordinate(coordinates[0], coordinates[1]));
            } else if (segment == PathIterator.SEG_LINETO) {
                var next = new Coordinate(coordinates[0], coordinates[1]);
                if (!next.equals2D(points.getLast())) points.add(next);
            } else if (segment == PathIterator.SEG_CLOSE && points.size() >= 3) {
                if (!points.getLast().equals2D(points.getFirst())) points.add(new Coordinate(points.getFirst()));
                Geometry contour = factory.createPolygon(points.toArray(Coordinate[]::new));
                if (!contour.isValid()) contour = contour.buffer(0);
                if (!contour.isEmpty() && contour.getArea() > 0) contours.add(contour);
            }
            path.next();
        }
        Geometry result = factory.createPolygon();
        // Area resolves overlap and winding first; its remaining rings nest by even/odd depth.
        for (int i = 0; i < contours.size(); i++) {
            cancellation.throwIfCancellationRequested();
            result = OverlayNGRobust.overlay(result, contours.get(i), OverlayNG.SYMDIFFERENCE);
            progress.report(0.3 + 0.7 * (i + 1) / contours.size());
        }
        cancellation.throwIfCancellationRequested();
        if (result.isEmpty()) throw new IllegalArgumentException("A fonte nao gerou contornos visiveis.");
        progress.report(1);
        return result;
    }
}
