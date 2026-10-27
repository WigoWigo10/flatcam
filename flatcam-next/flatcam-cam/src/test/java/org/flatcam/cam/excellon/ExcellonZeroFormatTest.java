package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExcellonZeroFormatTest {
    static List<String> file(String header,String... body) {
        var lines=new ArrayList<>(List.of("M48",header,"T1C0.01","%","T1")); lines.addAll(List.of(body)); lines.add("M30"); return lines;
    }
    @Test void shortLzTokensRetainLeadingZerosAndSignsAreNotDigits() {
        var inches=new ExcellonParser().parse(file("INCH,LZ","X0012Y-012"));
        assertEquals(.12,inches.drills().getFirst().x(),1e-12); assertEquals(-1.2,inches.drills().getFirst().y(),1e-12);
        var mm=new ExcellonParser().parse(file("METRIC,LZ","X0012Y+012"));
        assertEquals(1.2,mm.drills().getFirst().x(),1e-12); assertEquals(12,mm.drills().getFirst().y(),1e-12);
    }
    @Test void declaredZerosAndFormatOverrideDefaultsAndExplicitDecimalsAlwaysWin() {
        var lz=new ExcellonParser(new ExcellonParser.Options("MM",4,3,2,3,"LZ"));
        assertEquals(12,lz.parse(file("","X012Y1.234")).drills().getFirst().x());
        var tz=lz.parse(file("METRIC,TZ","X012Y1.234"));
        assertEquals(.012,tz.drills().getFirst().x(),1e-12); assertEquals(1.234,tz.drills().getFirst().y());
        for(var header:List.of("METRIC,LZ,00.0000",";FILE_FORMAT=2:4\nMETRIC,LZ",";Format=2.4\nMETRIC,LZ")) {
            // The list API accepts one logical line at a time, just as the file reader does.
            var lines=new ArrayList<String>(); for(String line:file(header,"X0012Y+0.5")) lines.addAll(line.lines().toList());
            var image=lz.parse(lines); assertEquals(.12,image.drills().getFirst().x(),1e-12); assertEquals(.5,image.drills().getFirst().y());
        }
        assertEquals(1.2,new ExcellonParser().parse(file("METRIC,LZ","X0012Y0.0")).drills().getFirst().x(),1e-12);
    }
    @Test void routedAndG85SlotsUseTheSameFormatAndModalCoordinatesAsDrills() {
        var image=new ExcellonParser().parse(file("INCH,LZ,00.0000","X0012Y0034","X002G85X003",
                "G00X004","M15","G01X005Y006","M16","G05","Y007"));
        assertEquals(List.of(new ExcellonImage.Drill(1,.12,.34),new ExcellonImage.Drill(1,.5,.7)),image.drills());
        assertEquals(List.of(new ExcellonImage.Slot(1,.2,.34,.3,.34),new ExcellonImage.Slot(1,.4,.34,.5,.6)),image.slots());
    }
    @Test void fullWidthCoordinatesKeepOldResultsForBothConventions() {
        for(String units:List.of("METRIC","INCH")) for(String zero:List.of("LZ","TZ")) {
            var image=new ExcellonParser().parse(file(units+","+zero+",000.000","X012340Y-000120"));
            assertEquals(12.34,image.drills().getFirst().x(),1e-12); assertEquals(-.12,image.drills().getFirst().y(),1e-12);
        }
    }
    @Test void malformedFormatsAndOptionsFailBeforeReturningAnyImage() {
        assertThrows(IllegalArgumentException.class,()->new ExcellonParser.Options("MM",4,3,0,3,"LZ"));
        assertThrows(IllegalArgumentException.class,()->new ExcellonParser.Options("MM",4,3,2,3,"XX"));
        for(String header:List.of("METRIC,LZ,0.0000000000","METRIC,BAD",";FILE_FORMAT=0:3"))
            assertThrows(ExcellonParseException.class,()->new ExcellonParser().parse(file(header,"X123Y456")));
    }
    @Test void exportPublicPythonNumberControls() throws Exception {
        var rows=new ArrayList<String>(); rows.add("units,zeros,upper,lower,token,value");
        for(String units:List.of("MM","IN")) for(String zero:List.of("LZ","TZ")) for(int upper:new int[]{2,3})
            for(String token:List.of("0012","-0012","+0123","012300","12","-000120")) {
                int lower=units.equals("MM") ? 3 : 4;
                var parser=new ExcellonParser(new ExcellonParser.Options(units,lower,lower,upper,upper,zero));
                var result=parser.parse(file("","X"+token+"Y0.0"));
                rows.add(units+","+zero+","+upper+","+lower+","+token+","+result.drills().getFirst().x());
            }
        java.nio.file.Files.write(java.nio.file.Path.of("target/excellon-zero-comparison.csv"),rows);
    }
}
