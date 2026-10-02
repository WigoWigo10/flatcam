package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

class TextGeometryTest {
    private static Geometry text(String value, String units, boolean bold, boolean italic) {
        return TextGeometry.generate(new TextGeometry.Parameters(value,"SansSerif",10,bold,italic,units),
                CancellationToken.none(),ProgressCallback.none());
    }
    @Test void retainsGlyphHolesAndUsesPythonUnitScale() {
        Geometry mm = text("OB8", "MM", false,false);
        assertTrue(mm.isValid()); assertTrue(mm.getArea() > 0);
        int holes = 0;
        for (int i = 0; i < mm.getNumGeometries(); i++) holes += ((Polygon) mm.getGeometryN(i)).getNumInteriorRing();
        assertTrue(holes >= 5, "O/B/8 cavities must not be filled");
        Geometry inch = text("OB8", "IN", false,false);
        assertEquals(25.4,mm.getEnvelopeInternal().getWidth()/inch.getEnvelopeInternal().getWidth(),1e-5);
    }
    @Test void multilineBaselineAndCombinedStyleHaveOutlines() {
        Geometry first = text("ABC", "MM", true,true);
        Geometry multiline = text("ABC\nABC", "MM",true,true);
        assertTrue(multiline.isValid());
        assertTrue(multiline.getEnvelopeInternal().getMinY() < first.getEnvelopeInternal().getMinY());
        assertEquals(first.getArea()*2,multiline.getArea(),1e-6);
        assertTrue(text("ação", "MM",false,false).isValid());
    }
    @Test void rejectsInvalidInputAndCancellationAndUnknownFonts() {
        assertThrows(IllegalArgumentException.class, () -> text(" ","MM",false,false));
        assertThrows(IllegalArgumentException.class, () -> text("A".repeat(513),"MM",false,false));
        assertThrows(IllegalArgumentException.class, () -> new TextGeometry.Parameters("A","SansSerif",Double.NaN,false,false,"MM"));
        assertThrows(IllegalArgumentException.class, () -> TextGeometry.generate(
                new TextGeometry.Parameters("A","FlatCAM-no-such-font",10,false,false,"MM"),CancellationToken.none(),ProgressCallback.none()));
        assertThrows(CancellationException.class, () -> TextGeometry.generate(
                new TextGeometry.Parameters("A","SansSerif",10,false,false,"MM"),() -> true,ProgressCallback.none()));
    }
    @Test void generatedTextIsOneUndoTransactionWithToolAssociation() {
        Geometry empty = new GeometryFactory().createGeometryCollection();
        var session = new GeometryEditSession(empty,List.of(new ToolGeometry(0.2,empty,ToolProfile.V)));
        var request = session.prepareText(new TextGeometry.Parameters("OB","SansSerif",10,false,false,"MM"),0);
        var result = request.execute(CancellationToken.none(),ProgressCallback.none());
        assertFalse(session.isDirty(),"generation is a preview, not a commit");
        var geometry = empty.getFactory().buildGeometry(result.resultParts().stream().map(GeometryEditSession.ToolPart::geometry).toList());
        session.addGeneratedGeometry(geometry,0);
        assertTrue(session.isDirty());
        assertEquals(ToolProfile.V,session.resultTools().getFirst().toolProfile());
        assertTrue(session.undo()); assertEquals(0,session.shapeCount());
        assertTrue(session.redo()); assertEquals(geometry.getArea(),session.resultGeometry().getArea(),1e-6);
    }
}
