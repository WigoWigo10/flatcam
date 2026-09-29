package org.flatcam.cam.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class SvgExporterTest {

    private final SvgExporter exporter = new SvgExporter();

    @Test
    void viewBoxFramesTheFlippedDrawingIncludingTheStroke() throws Exception {
        Geometry square = new WKTReader().read(
                "POLYGON ((0 5, 10 5, 10 8, 0 8, 0 5), (2 6, 3 6, 3 7, 2 7, 2 6))");

        String svg = exporter.export(List.of(SvgExporter.shapes(square)), "MM");
        Element root = parse(svg).getDocumentElement();

        assertEquals("10.02mm", root.getAttribute("width"));
        assertEquals("3.02mm", root.getAttribute("height"));
        // scale(1,-1) maps y 5..8 to -8..-5, so the box starts at -maxY (Python used minY - height).
        assertEquals("-0.01 -8.01 10.02 3.02", root.getAttribute("viewBox"));
        Element path = (Element) root.getElementsByTagName("path").item(0);
        assertEquals("evenodd", path.getAttribute("fill-rule"));
        assertEquals(SvgExporter.SHAPE_FILL, path.getAttribute("fill"));
        assertEquals("M0,5 L10,5 L10,8 L0,8 L0,5 z M2,6 L3,6 L3,7 L2,7 L2,6 z", path.getAttribute("d"));
    }

    @Test
    void toolpathsAreDrawnAtToolWidthWithTravelsUnderCuts() throws Exception {
        WKTReader reader = new WKTReader();
        Geometry travel = reader.read("MULTILINESTRING ((0 0, 5 0))");
        Geometry cut = reader.read("GEOMETRYCOLLECTION (POINT (5 0), LINESTRING (5 0, 5 4))");

        String svg = exporter.export(List.of(
                SvgExporter.toolpath(travel, SvgExporter.TRAVEL_COLOR, 0.8),
                SvgExporter.toolpath(cut, SvgExporter.CUT_COLOR, 0.8)), "IN");
        Element root = parse(svg).getDocumentElement();

        assertEquals("5.8in", root.getAttribute("width"));
        Element first = (Element) root.getElementsByTagName("polyline").item(0);
        assertEquals(SvgExporter.TRAVEL_COLOR, first.getAttribute("stroke"));
        assertEquals("0.8", first.getAttribute("stroke-width"));
        assertEquals("round", first.getAttribute("stroke-linecap"));
        Element plunge = (Element) root.getElementsByTagName("circle").item(0);
        assertEquals("0.4", plunge.getAttribute("r"));
        assertEquals(SvgExporter.CUT_COLOR, plunge.getAttribute("fill"));
        assertTrue(svg.indexOf(SvgExporter.TRAVEL_COLOR) < svg.indexOf(SvgExporter.CUT_COLOR));
    }

    @Test
    void emptyObjectsAndUnknownUnitsAreRejected() throws Exception {
        Geometry empty = new WKTReader().read("POLYGON EMPTY");
        Geometry point = new WKTReader().read("POINT (1 1)");

        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(List.of(SvgExporter.shapes(empty)), "MM"));
        assertThrows(IllegalArgumentException.class,
                () -> exporter.export(List.of(SvgExporter.shapes(point)), "CM"));
    }

    private static Document parse(String svg) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }
}
