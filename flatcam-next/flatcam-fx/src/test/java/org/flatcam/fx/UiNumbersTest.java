package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class UiNumbersTest {
    @Test void precisionIsUnitSpecificAndDoesNotChangeTheStoredNumber() {
        var settings = new UiNumbers.Settings("IN",2,8);
        double value = 1.2345678912345678;
        long original = Double.doubleToRawLongBits(value);
        assertEquals("1.23",settings.format(value,"MM"));
        assertEquals("1.23456789",settings.format(value,"in"));
        assertEquals(original,Double.doubleToRawLongBits(value));
        assertEquals("1.2345678912345680",new UiNumbers.Settings("MM",16,16).format(value,"MM"));
        assertEquals("0.00000001",settings.format(.00000001,"IN"));
    }
    @Test void settingsRejectUnsupportedUnitsAndPrecision() {
        assertThrows(IllegalArgumentException.class, () -> new UiNumbers.Settings("cm",4,4));
        assertThrows(IllegalArgumentException.class, () -> new UiNumbers.Settings(null,4,4));
        for (int digits : new int[]{-1,0,1,17,Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new UiNumbers.Settings("MM",digits,4));
            assertThrows(IllegalArgumentException.class, () -> new UiNumbers.Settings("IN",4,digits));
        }
        assertEquals("MM",UiNumbers.Settings.DEFAULT.initialUnits());
        assertEquals(4,UiNumbers.Settings.DEFAULT.metricDecimals());
        assertEquals(4,UiNumbers.Settings.DEFAULT.inchDecimals());
    }
    @Test void formattingDoesNotChangeTheGlobalLocale() {
        Locale before = Locale.getDefault();
        assertEquals("1234.50",new UiNumbers.Settings("MM",2,6).format(1234.5,"MM"));
        assertEquals(before,Locale.getDefault());
    }
}
