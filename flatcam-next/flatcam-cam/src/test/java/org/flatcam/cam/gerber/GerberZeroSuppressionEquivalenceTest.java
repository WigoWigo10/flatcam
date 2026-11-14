package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GerberZeroSuppressionEquivalenceTest {
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void fullWidthNumbersAndRoundedOutlineAgreeAcrossZeroSuppressionModes(String units){
        var leading=FormatSpec.parse("%FSLAX45Y45*%");var trailing=FormatSpec.parse("%FSTAX45Y45*%");
        assertEquals(leading.decodeX("004399991"),trailing.decodeX("004399991"),"A full-width coordinate is identical regardless of suppression mode");
        var body=List.of("%MO"+units+"*%","%ADD10C,0.254*%","D10*","X004399991Y002999994D02*","G75*","G03*","X004199992Y003199994I-000199999J0D01*","M02*");
        var l=new ArrayList<>(body);l.addFirst("%FSLAX45Y45*%");var t=new ArrayList<>(body);t.addFirst("%FSTAX45Y45*%");
        var first=new GerberParser().parse(l);var second=new GerberParser().parse(t);
        assertTrue(first.followGeometry().equalsExact(second.followGeometry()),"Equivalent numbers must not change the arc sampling");
        assertTrue(first.solidGeometry().equalsExact(second.solidGeometry()));
    }
}
