package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonExporter;
import org.junit.jupiter.api.Test;

/** Parses format snapshots without opening dialogs or touching the user's preferences. */
class CamExportDialogTest {
    @Test void gerberFormatsSupportBothUnitsAndZeroConventions() {
        assertEquals(new org.flatcam.cam.gerber.GerberExporter.Format("MM", 3, 6, true),
                CamExportDialog.parseGerberFormat("MM;3;6;L"));
        assertEquals(new org.flatcam.cam.gerber.GerberExporter.Format("IN", 2, 4, false),
                CamExportDialog.parseGerberFormat("IN;2;4;T"));
    }

    @Test void missingOrStaleGerberFormatUsesTheDialogDefaults() {
        for (String saved : new String[] {null, "", "MM;3", "BAD;3;4;L", "MM;x;4;L", "MM;3;0;L", "MM;7;4;L"})
            assertEquals(org.flatcam.cam.gerber.GerberExporter.Format.flatcamDefaults(), CamExportDialog.parseGerberFormat(saved));
    }
    @Test void excellonDecimalFormatRetainsRememberedUnitsPrecisionAndSlotStyle() {
        assertEquals(new ExcellonExporter.Format("MM", true, 3, 5, true, ExcellonExporter.SlotStyle.G85),
                CamExportDialog.parseExcellonFormat("MM;dec;3;5;LZ;G85"));
    }

    @Test void excellonSuppressedFormatRetainsDigitCountsZerosAndRoutedSlots() {
        assertEquals(new ExcellonExporter.Format("IN", false, 2, 6, false, ExcellonExporter.SlotStyle.ROUTED),
                CamExportDialog.parseExcellonFormat("IN;ndec;2;6;TZ;ROUTED"));
    }

    @Test void absentOrStaleExcellonFormatUsesTheSameDefaultsAsTheDialog() {
        for (String saved : List.of("", "MM;dec", "BAD;dec;3;4;LZ;G85", "MM;dec;7;4;LZ;G85",
                "MM;dec;3;0;LZ;G85", "IN;dec;x;4;LZ;ROUTED", "IN;dec;2;4;LZ;unknown"))
            assertEquals(ExcellonExporter.Format.flatcamDefaults(), CamExportDialog.parseExcellonFormat(saved), saved);
    }
}
