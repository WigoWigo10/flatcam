package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GerberLegacyTransformValidationTest {
    private static List<String> file(String directive){return List.of("%FSLAX45Y45*%","%MOMM*%",directive,"%ADD10C,0.254*%","D10*","X100000Y200000D03*","M02*");}
    @ParameterizedTest @ValueSource(strings={"%OFA1B0*%","%OFA0B-2*%","%SFA2B1*%","%SFA1B0.5*%","%ASAYBX*%","%IPNEG*%","%OFAoopsB0*%"})
    void refusesUnsupportedOrMalformedTransformsInsteadOfImportingUnchangedCopper(String directive){
        var error=assertThrows(GerberParseException.class,()->new GerberParser().parse(file(directive)));
        assertTrue(error.getMessage().contains(directive));
    }
    @ParameterizedTest @ValueSource(strings={"%OFA0B0*%","%OFA-0.000B+0*%","%OFA0*%","%SFA1B1*%","%SFA1.0*%","%ASAXBY*%","%IPPOS*%"})
    void neutralLegacyHeadersRetainTheirExistingGeometry(String directive){
        var expected=new GerberParser().parse(file("G04 no transform*"));var actual=new GerberParser().parse(file(directive));
        assertTrue(expected.solidGeometry().equalsExact(actual.solidGeometry()));
    }
}
