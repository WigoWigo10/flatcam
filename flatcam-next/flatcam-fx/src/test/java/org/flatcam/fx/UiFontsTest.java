package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UiFontsTest {
    @Test void validRangesIncludeHiddenAxesAndPreserveFxDefaults() {
        assertDoesNotThrow(()->new UiFonts.Settings(8,8,0,8));
        assertDoesNotThrow(()->new UiFonts.Settings(40,40,40,40));
        assertEquals(new UiFonts.Settings(12,13,13,11),UiFonts.Settings.DEFAULT);
        assertEquals(27,UiFonts.rowHeight(27)); assertEquals(28,UiFonts.rowHeight(28));
        assertEquals(44,UiFonts.rulerLeft()); assertEquals(20,UiFonts.rulerTop());
    }
    @Test void invalidSizesCannotReachPersistenceOrTheStylesheet() {
        for(int size:new int[]{-1,0,7,41,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,()->new UiFonts.Settings(size,13,13,11));
            assertThrows(IllegalArgumentException.class,()->new UiFonts.Settings(12,size,13,11));
            assertThrows(IllegalArgumentException.class,()->new UiFonts.Settings(12,13,13,size));
        }
        assertThrows(IllegalArgumentException.class,()->new UiFonts.Settings(12,13,-1,11));
        assertThrows(IllegalArgumentException.class,()->new UiFonts.Settings(12,13,41,11));
    }
}
