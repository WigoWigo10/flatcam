package org.flatcam.cam.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;

/** Selected objects as one vector page, at physical 1:1 scale, independent of plot zoom/theme. */
public final class PrintPdfExporter {
    public static final List<String> PAPERS = List.of("Bounds", "A0", "A1", "A2", "A3", "A4", "A5", "A6", "Letter", "Legal", "Tabloid");
    private static final double POINTS_PER_MM = 72.0 / 25.4;
    public record Layer(Geometry geometry, String units, int rgb, boolean filled) {
        public Layer {
            Objects.requireNonNull(geometry);
            if (!"MM".equalsIgnoreCase(units) && !"IN".equalsIgnoreCase(units))
                throw new IllegalArgumentException("Unidade desconhecida: " + units);
        }
        double mmPerUnit() { return "IN".equalsIgnoreCase(units) ? 25.4 : 1; }
    }
    public record Options(String paper, boolean portrait, boolean monochrome) {
        public Options { if (!PAPERS.contains(paper)) throw new IllegalArgumentException("Página desconhecida: " + paper); }
    }
    private PrintPdfExporter() { }

    public static void write(List<Layer> layers, Options options, Path file,
                             CancellationToken cancellation, DoubleConsumer progress) throws IOException {
        byte[] bytes = export(layers, options, cancellation, progress);
        Path destination = file.toAbsolutePath().normalize();
        cancellation.throwIfCancellationRequested();
        Path temporary = Files.createTempFile(destination.getParent(), ".flatcam-print-", ".tmp");
        try {
            Files.write(temporary, bytes);
            cancellation.throwIfCancellationRequested();
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
        progress.accept(1);
    }

    public static byte[] export(List<Layer> layers, Options options, CancellationToken cancellation, DoubleConsumer progress) {
        Objects.requireNonNull(options); Objects.requireNonNull(cancellation); Objects.requireNonNull(progress);
        layers = List.copyOf(layers);
        Envelope bounds = new Envelope();
        long total = 0;
        for (Layer layer : layers) {
            cancellation.throwIfCancellationRequested();
            double mm = layer.mmPerUnit();
            // Validate all vertices; a finite envelope alone cannot detect an internal NaN.
            layer.geometry().apply(new CoordinateSequenceFilter() {
                public void filter(CoordinateSequence c, int i) {
                    if ((i & 1023) == 0) cancellation.throwIfCancellationRequested();
                    double x = c.getX(i) * mm, y = c.getY(i) * mm;
                    if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Coordenadas não finitas no objeto selecionado.");
                    bounds.expandToInclude(x, y);
                }
                public boolean isDone() { return false; }
                public boolean isGeometryChanged() { return false; }
            });
            total += layer.geometry().getNumPoints();
        }
        if (bounds.isNull()) throw new IllegalArgumentException("Os objetos selecionados não têm geometria imprimível.");
        double margin = options.paper().equals("Bounds") ? 1 : 5;
        double[] page = paper(options.paper());
        double width = bounds.getWidth() + 2 * margin, height = bounds.getHeight() + 2 * margin;
        if (page == null) page = new double[]{width, height};
        else if (!options.portrait()) page = new double[]{page[1], page[0]};
        if (width > page[0] + 1e-8 || height > page[1] + 1e-8)
            throw new IllegalArgumentException("A seleção não cabe na página em escala 1:1. Escolha Paisagem, uma página maior ou Bounds; nada foi cortado ou reduzido.");
        if (!Double.isFinite(width) || !Double.isFinite(height) || Math.max(page[0], page[1]) * POINTS_PER_MM > 14400)
            throw new IllegalArgumentException("A página excede o limite de tamanho deste PDF (5080 mm).");
        double ox = bounds.getMinX() - (page[0] - bounds.getWidth()) / 2;
        double oy = bounds.getMinY() - (page[1] - bounds.getHeight()) / 2;
        Writer writer = new Writer(cancellation, progress, Math.max(1, total), ox, oy);
        progress.accept(0.05);
        for (Layer layer : layers) {
            cancellation.throwIfCancellationRequested();
            int rgb = options.monochrome() ? 0 : layer.rgb();
            writer.out.append(String.format(Locale.ROOT, "%.6f %.6f %.6f rg %.6f %.6f %.6f RG\n0.056693 w\n1 J 1 j\n",
                    ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0,
                    ((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0));
            writer.draw(layer.geometry(), layer.mmPerUnit(), layer.filled());
        }
        cancellation.throwIfCancellationRequested();
        progress.accept(0.95);
        return document(writer.out.toString(), page[0] * POINTS_PER_MM, page[1] * POINTS_PER_MM, cancellation);
    }
    private static double[] paper(String name) {
        return switch (name) {
            case "Bounds" -> null; case "A0" -> new double[]{841,1189}; case "A1" -> new double[]{594,841};
            case "A2" -> new double[]{420,594}; case "A3" -> new double[]{297,420}; case "A4" -> new double[]{210,297};
            case "A5" -> new double[]{148,210}; case "A6" -> new double[]{105,148};
            case "Letter" -> new double[]{215.9,279.4}; case "Legal" -> new double[]{215.9,355.6};
            case "Tabloid" -> new double[]{279.4,431.8}; default -> throw new IllegalArgumentException(name);
        };
    }
    private static final class Writer {
        final StringBuilder out = new StringBuilder();
        final CancellationToken cancellation; final DoubleConsumer progress;
        final long total; final double ox, oy; long processed;
        Writer(CancellationToken cancellation, DoubleConsumer progress, long total, double ox, double oy) {
            this.cancellation=cancellation; this.progress=progress; this.total=total; this.ox=ox; this.oy=oy;
        }
        void draw(Geometry geometry, double mm, boolean filled) {
            cancellation.throwIfCancellationRequested();
            if (geometry.isEmpty()) return;
            if (geometry instanceof Polygon polygon) {
                ring(polygon.getExteriorRing().getCoordinateSequence(), mm, true);
                for (int i=0; i<polygon.getNumInteriorRing(); i++) ring(polygon.getInteriorRingN(i).getCoordinateSequence(), mm, true);
                out.append(filled ? "f*\n" : "S\n"); // even-odd preserves internal cutouts irrespective of winding
            } else if (geometry instanceof LineString line) {
                ring(line.getCoordinateSequence(), mm, false); out.append("S\n");
            } else if (geometry instanceof Point point) {
                // Thin visible dot, matching the fixed 0.02 mm drawing stroke.
                double x=(point.getX()*mm-ox)*POINTS_PER_MM, y=(point.getY()*mm-oy)*POINTS_PER_MM;
                out.append(String.format(Locale.ROOT,"%.6f %.6f m %.6f %.6f l S\n",x,y,x,y)); tick();
            } else if (geometry instanceof GeometryCollection collection) {
                for (int i=0; i<collection.getNumGeometries(); i++) draw(collection.getGeometryN(i),mm,filled);
            } else throw new IllegalArgumentException("Geometria não suportada: " + geometry.getGeometryType());
        }
        void ring(CoordinateSequence c, double mm, boolean close) {
            for (int i=0; i<c.size(); i++) {
                out.append(String.format(Locale.ROOT,"%.6f %.6f %s\n",(c.getX(i)*mm-ox)*POINTS_PER_MM,
                        (c.getY(i)*mm-oy)*POINTS_PER_MM,i==0 ? "m" : "l")); tick();
            }
            if (close) out.append("h\n");
        }
        void tick() {
            if ((++processed & 1023) == 0) {
                cancellation.throwIfCancellationRequested(); progress.accept(0.05+0.9*processed/total);
            }
        }
    }
    private static byte[] document(String commands, double width, double height, CancellationToken cancellation) {
        var compressor = new java.util.zip.Deflater();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try {
            compressor.setInput(commands.getBytes(StandardCharsets.US_ASCII)); compressor.finish();
            byte[] block = new byte[8192];
            while (!compressor.finished()) {
                cancellation.throwIfCancellationRequested();
                int count = compressor.deflate(block); compressed.write(block,0,count);
            }
        } finally { compressor.end(); }
        byte[] stream = compressed.toByteArray();
        List<byte[]> objects = new ArrayList<>();
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(ascii(String.format(Locale.ROOT,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.6f %.6f] /Contents 4 0 R /Resources << >> >>",width,height)));
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(ascii("<< /Length " + stream.length + " /Filter /FlateDecode >>\nstream\n")); content.writeBytes(stream); content.writeBytes(ascii("\nendstream"));
        objects.add(content.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.writeBytes(ascii("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int i=0; i<objects.size(); i++) {
            offsets.add(out.size()); out.writeBytes(ascii((i+1)+" 0 obj\n")); out.writeBytes(objects.get(i)); out.writeBytes(ascii("\nendobj\n"));
        }
        int xref=out.size(); out.writeBytes(ascii("xref\n0 5\n0000000000 65535 f \n"));
        for (int offset:offsets) out.writeBytes(ascii(String.format(Locale.ROOT,"%010d 00000 n \n",offset)));
        out.writeBytes(ascii("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n"));
        return out.toByteArray();
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
