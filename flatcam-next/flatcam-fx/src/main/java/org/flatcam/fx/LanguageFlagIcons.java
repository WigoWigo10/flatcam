package org.flatcam.fx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Vector-only rendering of the pinned, bundled Circle Flags subset; no browser or external SVG engine. */
final class LanguageFlagIcons {
    private static final Map<UiLanguage, Document> ARTWORK = new ConcurrentHashMap<>();
    private LanguageFlagIcons() { }

    static Node icon(UiLanguage language, double size) {
        if (language == null || !Double.isFinite(size) || size <= 0)
            throw new IllegalArgumentException("Invalid language flag size or language.");
        Document svg = ARTWORK.computeIfAbsent(language, LanguageFlagIcons::load);
        Group artwork = new Group(draw(svg.getDocumentElement(), svg));
        Circle edge = new Circle(256, 256, 250, Color.TRANSPARENT);
        edge.setStroke(Color.rgb(112, 112, 112, .65));
        edge.setStrokeWidth(12);
        artwork.getChildren().add(edge);
        artwork.getTransforms().add(new Scale(size / 512, size / 512));
        StackPane holder = new StackPane(new Group(artwork));
        holder.setMinSize(size, size); holder.setPrefSize(size, size); holder.setMaxSize(size, size);
        holder.setMouseTransparent(true);
        holder.getStyleClass().add("language-flag");
        return holder;
    }

    private static Document load(UiLanguage language) {
        String path = "icons/languages/" + language.catalog + ".svg";
        try (var input = LanguageFlagIcons.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing language SVG: " + path);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(input);
            if (!"0 0 512 512".equals(document.getDocumentElement().getAttribute("viewBox")))
                throw new IllegalStateException("Unexpected language SVG viewport: " + path);
            return document;
        } catch (java.io.IOException | org.xml.sax.SAXException | javax.xml.parsers.ParserConfigurationException failure) {
            throw new IllegalStateException("Cannot read language SVG: " + path, failure);
        }
    }

    /** Deliberately supports only the shapes and opaque clipping masks in our four local assets. */
    private static Node draw(Element element, Document document) {
        Node node = switch (element.getLocalName()) {
            case "svg", "g", "clipPath", "mask" -> {
                Group group = new Group();
                var children = element.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element child
                            && !java.util.List.of("defs", "mask", "clipPath").contains(child.getLocalName()))
                        group.getChildren().add(draw(child, document));
                }
                yield group;
            }
            case "path" -> {
                SVGPath path = new SVGPath();
                path.setContent(element.getAttribute("d"));
                path.setFill(fill(element));
                yield path;
            }
            case "circle" -> new Circle(Double.parseDouble(element.getAttribute("cx")),
                    Double.parseDouble(element.getAttribute("cy")), Double.parseDouble(element.getAttribute("r")), fill(element));
            default -> throw new IllegalStateException("Unsupported bundled flag element: " + element.getLocalName());
        };
        String clipping = element.hasAttribute("clip-path") ? element.getAttribute("clip-path") : element.getAttribute("mask");
        if (!clipping.isEmpty()) {
            if (!clipping.matches("url\\(#[a-zA-Z0-9_-]+\\)")) throw new IllegalStateException("Invalid local SVG clip.");
            String id = clipping.substring(5, clipping.length() - 1);
            Element definition = null;
            var elements = document.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < elements.getLength(); i++)
                if (elements.item(i) instanceof Element candidate && id.equals(candidate.getAttribute("id"))) {
                    definition = candidate; break;
                }
            if (definition == null || !java.util.List.of("clipPath", "mask").contains(definition.getLocalName()))
                throw new IllegalStateException("Missing bundled SVG clip: " + id);
            node.setClip(draw(definition, document));
        }
        return node;
    }

    private static Color fill(Element element) {
        return element.hasAttribute("fill") ? Color.web(element.getAttribute("fill")) : Color.BLACK;
    }
}
