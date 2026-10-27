package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.locationtech.jts.geom.Coordinate;

@EnabledOnOs(OS.WINDOWS)
class ArcCoordinateDialogTest {
    @Test void generalJumpRestoresReferenceAndDoesNotOfferInsertion() throws Exception {
        TerminalPanelTest.fx(() -> {
            var d = new ArcCoordinateDialog(null,new Coordinate(10,20),"MM",false,true);
            try {
                assertTrue(d.relative.isSelected()); assertEquals("0",d.x.getText());
                assertFalse(d.getDialogPane().getButtonTypes().contains(d.insert));
                d.x.setText("2.5"); d.y.setText("-3");
                var choice = d.getResultConverter().call(d.preview);
                assertTrue(choice.relative()); assertFalse(choice.insert());
                assertEquals(new Coordinate(12.5,17),choice.point());
                assertNull(d.getResultConverter().call(d.insert));
                d.relative.setSelected(false); assertEquals("10",d.x.getText());
                assertFalse(d.getResultConverter().call(d.preview).relative());
                assertNull(d.getResultConverter().call(javafx.scene.control.ButtonType.CANCEL));
                return null;
            } finally { d.close(); }
        });
    }
    @Test void absoluteAndRelativeCoordinatesAcceptDecimalCommaAndRejectNonfiniteOrExpressions() {
        Coordinate origin = new Coordinate(10.25,-5);
        assertEquals(new Coordinate(1.25,-2.5),ArcCoordinateDialog.resolve("1,25","-2.5",false,origin));
        assertEquals(new Coordinate(11.5,-7.5),ArcCoordinateDialog.resolve("1,25","-2.5",true,origin));
        for (String value : new String[]{"NaN","Infinity","1+2","", "(2,3)"})
            assertThrows(IllegalArgumentException.class,()->ArcCoordinateDialog.resolve(value,"0",false,origin));
        assertThrows(IllegalArgumentException.class,()->ArcCoordinateDialog.resolve("1e308","0",true,new Coordinate(1e308,0)));
    }

    @Test void previewAndInsertAreDistinctAndInvalidInputKeepsTheDialogDraft() throws Exception {
        TerminalPanelTest.fx(() -> {
            var d = new ArcCoordinateDialog(null,new Coordinate(10,20),"IN");
            try {
                d.x.setText("1,5"); d.y.setText("-2");
                var preview=d.getResultConverter().call(d.preview);
                var insert=d.getResultConverter().call(d.insert);
                assertFalse(preview.insert()); assertTrue(insert.insert());
                assertEquals(new Coordinate(1.5,-2),insert.point());
                assertTrue(d.x.getAccessibleHelp().contains("IN"));
                d.relative.setSelected(true); assertEquals("0",d.x.getText());
                d.x.setText("-2,25"); d.y.setText("3");
                assertEquals(new Coordinate(7.75,23),d.getResultConverter().call(d.insert).point());
                d.x.setText("NaN"); ((Button)d.getDialogPane().lookupButton(d.insert)).fire();
                assertFalse(d.error.getText().isBlank()); assertEquals("NaN",d.x.getText());
                assertNull(d.getResult()); return null;
            } finally { d.close(); }
        });
    }
}
