package org.flatcam.cam.pdf;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.InflaterInputStream;
import java.io.ByteArrayInputStream;
import org.flatcam.cam.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;

class PrintPdfExporterTest {
    @TempDir Path directory;
    private final GeometryFactory factory = new GeometryFactory();
    private Geometry square(double x, double y, double size) {
        return factory.toGeometry(new Envelope(x,x+size,y,y+size));
    }
    private static String stream(byte[] pdf) throws Exception {
        String ascii = new String(pdf,StandardCharsets.ISO_8859_1);
        int start = ascii.indexOf("stream\n")+7, end = ascii.indexOf("\nendstream",start);
        try (var inflater = new InflaterInputStream(new ByteArrayInputStream(pdf,start,end-start))) {
            return new String(inflater.readAllBytes(),StandardCharsets.US_ASCII);
        }
    }
    private byte[] export(List<PrintPdfExporter.Layer> layers, String paper, boolean portrait, boolean mono) {
        return PrintPdfExporter.export(layers,new PrintPdfExporter.Options(paper,portrait,mono),CancellationToken.none(),f -> {});
    }
    @Test void boundsPreservesPhysicalScaleOffsetsAndMixedUnits() throws Exception {
        Geometry mm = square(-10,-20,25.4), inches = square(0,0,1);
        var layers = List.of(new PrintPdfExporter.Layer(mm,"MM",0xff0000,true),new PrintPdfExporter.Layer(inches,"IN",0x0000ff,true));
        byte[] pdf = export(layers,"Bounds",true,false);
        String header = new String(pdf,StandardCharsets.ISO_8859_1);
        assertTrue(header.contains("/MediaBox [0 0 106.015748 134.362205]")); // 37.4 x 47.4 mm, including 1 mm border
        String content = stream(pdf);
        assertTrue(content.contains("2.834646 2.834646 m"));
        assertTrue(content.contains("1.000000 0.000000 0.000000 rg"));
        assertTrue(content.contains("0.000000 0.000000 1.000000 rg"));
        assertEquals(-10,mm.getEnvelopeInternal().getMinX()); assertEquals(1,inches.getEnvelopeInternal().getWidth());
        assertFalse(header.contains("/Subtype /Image"));
    }
    @Test void internalCutsUseEvenOddFillAndLineShapesStayUnfilled() throws Exception {
        Geometry board = new WKTReader().read("POLYGON ((0 0,10 0,10 10,0 10,0 0),(2 2,8 2,8 8,2 8,2 2))");
        Geometry line = new WKTReader().read("LINESTRING (0 0,10 10)");
        String content = stream(export(List.of(new PrintPdfExporter.Layer(board,"MM",0,true),
                new PrintPdfExporter.Layer(line,"MM",0,false)),"Bounds",true,true));
        assertEquals(2,content.lines().filter("h"::equals).count());
        assertEquals(1,content.lines().filter("f*"::equals).count());
        assertTrue(content.endsWith("S\n")); assertEquals(64,board.getArea());
        String outlines = stream(export(List.of(new PrintPdfExporter.Layer(board,"MM",0,false)),"Bounds",true,true));
        assertFalse(outlines.contains("f*")); assertTrue(outlines.endsWith("S\n"));
    }
    @Test void paperDoesNotSilentlyClipOrScaleAndLandscapeIsAvailable() {
        var layers = List.of(new PrintPdfExporter.Layer(factory.toGeometry(new Envelope(0,240,0,180)),"MM",0,true));
        assertThrows(IllegalArgumentException.class,() -> export(layers,"A4",true,true));
        String pdf = new String(export(layers,"A4",false,true),StandardCharsets.ISO_8859_1);
        assertTrue(pdf.contains("/MediaBox [0 0 841.889764 595.275591]"));
    }
    @Test void emptyNonfiniteUnknownUnitsAndOversizedPagesAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> export(List.of(),"A4",true,true));
        var line = factory.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(Double.NaN,1)});
        assertThrows(IllegalArgumentException.class,() -> export(List.of(new PrintPdfExporter.Layer(line,"MM",0,true)),"Bounds",true,true));
        assertThrows(IllegalArgumentException.class,() -> new PrintPdfExporter.Layer(square(0,0,1),"CM",0,true));
        assertThrows(IllegalArgumentException.class,() -> export(List.of(new PrintPdfExporter.Layer(square(0,0,6000),"MM",0,true)),"Bounds",true,true));
    }
    @Test void cancellationAndValidationLeaveExistingDestinationUntouched() throws Exception {
        Path file = directory.resolve("print.pdf"); Files.writeString(file,"original");
        var layers = List.of(new PrintPdfExporter.Layer(square(0,0,10),"MM",0,true));
        assertThrows(CancellationException.class,() -> PrintPdfExporter.write(layers,new PrintPdfExporter.Options("A4",true,true),file,() -> true,f -> {}));
        assertEquals("original",Files.readString(file));
        AtomicBoolean cancel = new AtomicBoolean();
        assertThrows(CancellationException.class,() -> PrintPdfExporter.write(layers,new PrintPdfExporter.Options("A4",true,true),file,cancel::get,
                f -> { if (f>=0.95) cancel.set(true); }));
        assertEquals("original",Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(List.of(file),files.toList()); }
    }
    @Test void publishedPdfHasCorrectXrefAndReopensAsVectorArtwork() throws Exception {
        Path file = directory.resolve("print.pdf");
        var layers = List.of(new PrintPdfExporter.Layer(square(0,0,10),"MM",0xff0000,true));
        PrintPdfExporter.write(layers,new PrintPdfExporter.Options("Bounds",true,false),file,CancellationToken.none(),f -> {});
        byte[] bytes = Files.readAllBytes(file); String pdf = new String(bytes,StandardCharsets.ISO_8859_1);
        int xref = Integer.parseInt(pdf.substring(pdf.lastIndexOf("startxref\n")+10,pdf.lastIndexOf("\n%%EOF")).trim());
        assertTrue(pdf.substring(xref).startsWith("xref\n0 5\n"));
        var entries = pdf.substring(xref).split("\n");
        for (int i=1; i<=4; i++) {
            int offset = Integer.parseInt(entries[2+i].substring(0,10)); assertTrue(pdf.substring(offset).startsWith(i+" 0 obj\n"));
        }
        var imported = PdfImporter.parse(bytes,"MM",CancellationToken.none());
        assertEquals(1,imported.layers().size()); assertEquals(100,imported.layers().getFirst().solidGeometry().getArea(),1e-5);
        assertEquals(1,imported.layers().getFirst().solidGeometry().getEnvelopeInternal().getMinX(),1e-6);
    }

    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="flatcam.tests.pdfProof",matches="true")
    void optionalVisualProofForIndependentRenderer() throws Exception {
        Geometry board = factory.toGeometry(new Envelope(-10,40,-5,30));
        board = board.difference(factory.createPoint(new Coordinate(-5,0)).buffer(2,32))
                .difference(factory.toGeometry(new Envelope(10,20,10,20)));
        Geometry drills = factory.createPoint(new Coordinate(2,0.5)).buffer(0.05,32);
        Geometry route = new WKTReader().read("LINESTRING (-7 24,3 24,3 7,7 7)");
        Path proof = Path.of("target/print-pdf-proof.pdf");
        PrintPdfExporter.write(List.of(new PrintPdfExporter.Layer(board,"MM",0x2080b0,true),
                new PrintPdfExporter.Layer(drills,"IN",0xd02020,true),new PrintPdfExporter.Layer(route,"MM",0x000000,false)),
                new PrintPdfExporter.Options("Bounds",true,false),proof,CancellationToken.none(),f -> {});
        assertTrue(Files.size(proof)>200);
    }
}
