package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

/** Parses format snapshots without opening dialogs or touching the user's preferences. */
class CamExportDialogTest {
    @BeforeEach @AfterEach void store() { ToolDefaults.useStore(ToolDefaults.memoryStore()); }

    @Test void sharedPreferencesStartWithPythonDefaultsAndRetainLegacyChoices() {
        assertEquals(org.flatcam.cam.gerber.GerberExporter.Format.flatcamDefaults(),CamExportDialog.loadGerber());
        assertEquals(ExcellonExporter.Format.flatcamDefaults(),CamExportDialog.loadExcellon());
        CamExportDialog.migrateLegacyFormats("MM;3;6;T","MM;ndec;3;5;TZ;G85");
        assertEquals(new org.flatcam.cam.gerber.GerberExporter.Format("MM",3,6,false),CamExportDialog.loadGerber());
        assertEquals(new ExcellonExporter.Format("MM",false,3,5,false,ExcellonExporter.SlotStyle.G85),CamExportDialog.loadExcellon());
        var saved=ToolDefaults.export(); ToolDefaults.resetAll(); ToolDefaults.importFrom(saved);
        assertEquals("T",ToolDefaults.choice("export.gerber.zeros"));
        assertEquals(5,CamExportDialog.loadExcellon().decimalDigits());
        for(String key:List.of("export.gerber.integer","export.excellon.decimals"))
            for(String bad:List.of("0","7","2.5")) assertThrows(IllegalArgumentException.class,()->ToolDefaults.set(key,true,bad));
    }

    @Test void preferredFormatsProduceReopenableConvertedGerberAndDrillFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var factory=new org.locationtech.jts.geom.GeometryFactory();
        var polygon=factory.toGeometry(new org.locationtech.jts.geom.Envelope(25.4,50.8,25.4,76.2));
        var gerber=org.flatcam.cam.gerber.GerberImage.of("MM",java.util.Map.of(),polygon,polygon,java.util.Map.of());
        var drills=org.flatcam.cam.excellon.ExcellonImage.of("MM",java.util.Map.of(1,2.54),
                List.of(new org.flatcam.cam.excellon.ExcellonImage.Drill(1,25.4,50.8)),
                List.of(new org.flatcam.cam.excellon.ExcellonImage.Slot(1,25.4,25.4,50.8,76.2)),null);
        for(String units:List.of("MM","IN")) for(boolean zeros:List.of(true,false)) {
            double factor=units.equals("MM") ? 1 : 1/25.4;
            CamExportDialog.saveGerber(new org.flatcam.cam.gerber.GerberExporter.Format(units,3,5,zeros));
            var gerberFile=directory.resolve("copper-"+units+"-"+zeros+".gbr");
            new org.flatcam.cam.gerber.GerberExporter().write(gerber,CamExportDialog.loadGerber(),gerberFile);
            String text=java.nio.file.Files.readString(gerberFile);
            assertTrue(text.contains("%FS"+(zeros ? "L" : "T")+"AX35Y35*%"));
            var reopened=new org.flatcam.cam.gerber.GerberParser().parse(text.lines().toList());
            assertEquals(units,reopened.units());
            assertEquals(25.4*factor,reopened.bounds()[0],1e-5);
            assertEquals(76.2*factor,reopened.bounds()[3],1e-5);
            for(boolean decimal:List.of(true,false)) for(var slots:ExcellonExporter.SlotStyle.values()) {
                CamExportDialog.saveExcellon(new ExcellonExporter.Format(units,decimal,3,5,zeros,slots));
                var drillFile=directory.resolve("holes-"+units+"-"+zeros+"-"+decimal+"-"+slots+".drl");
                new ExcellonExporter().write(drills,CamExportDialog.loadExcellon(),drillFile);
                var output=java.nio.file.Files.readString(drillFile);
                var restored=new org.flatcam.cam.excellon.ExcellonParser().parse(output.lines().toList());
                assertEquals(units,restored.units()); assertEquals(1,restored.drills().size()); assertEquals(1,restored.slots().size());
                assertEquals(25.4*factor,restored.drills().getFirst().x(),1e-5);
                assertEquals(50.8*factor,restored.slots().getFirst().x2(),1e-5);
                assertEquals(76.2*factor,restored.slots().getFirst().y2(),1e-5);
                assertEquals(2.54*factor,restored.toolDiameters().get(1),1e-5);
            }
        }
    }
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
