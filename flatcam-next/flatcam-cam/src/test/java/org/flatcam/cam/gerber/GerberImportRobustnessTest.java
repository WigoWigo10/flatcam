package org.flatcam.cam.gerber;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flatcam.cam.excellon.ExcellonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class GerberImportRobustnessTest {
    private static List<String> file(String... commands){var lines=new ArrayList<>(List.of("%FSLAX45Y45*%","%MOMM*%","%ADD10C,0.254*%","D10*"));lines.addAll(List.of(commands));return lines;}
    @ParameterizedTest @ValueSource(strings={"G1","G01","G2","G02","G3","G03"})
    void acceptsOnlyIsolatedBareInterpolationModes(String mode){
        String draw=mode.endsWith("1")?"X0Y100000D01*":"X0Y100000I-100000J0D01*";
        var expected=new GerberParser().parse(file("X100000Y0D02*","G75*",mode+"*",draw,"M02*"));
        var actual=new GerberParser().parse(file("X100000Y0D02*","G75*",mode,draw,"M02*"));
        assertTrue(expected.solidGeometry().equalsExact(actual.solidGeometry()));
        assertTrue(expected.followGeometry().equalsExact(actual.followGeometry()));
    }
    @ParameterizedTest @ValueSource(strings={"G01X0Y0D01","G10","G01junk","X0Y0D01"})
    void doesNotAddTerminatorsToArbitraryMalformedCommands(String command){
        assertThrows(GerberParseException.class,()->new GerberParser().parse(file(command,"M02*")));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void acceptsUtf8BomBeforeACommentOrTheFormat(boolean comment){
        var lines=file("X0Y0D03*","M02*");if(comment)lines.addFirst("G04 UTF-8 export*");
        var expected=new GerberParser().parse(lines);lines.set(0,"\uFEFF"+lines.getFirst());
        var actual=new GerberParser().parse(lines);assertTrue(expected.solidGeometry().equalsExact(actual.solidGeometry()));
    }
    @Test void acceptsUtf8BomInRelatedExcellonImport(){
        var lines=new ArrayList<>(List.of("M48","METRIC,LZ,000.000","T01C0.8","%","T01","X000000Y000000","M30"));
        var expected=new ExcellonParser().parse(lines);lines.set(0,"\uFEFFM48");
        assertTrue(expected.solidGeometry().equalsExact(new ExcellonParser().parse(lines).solidGeometry()));
    }
    @ParameterizedTest @ValueSource(strings={"M02*","M30*"})
    void ignoresCompleteCommandsAndGarbageAfterEndOfFile(String end){
        var expected=new GerberParser().parse(file("X0Y0D03*",end));
        var extra=new GerberParser().parse(file("X0Y0D03*",end,"X900000Y900000D03*"));
        assertTrue(expected.solidGeometry().equalsExact(extra.solidGeometry()),"No copper may appear after EOF");
        assertTrue(expected.solidGeometry().equalsExact(new GerberParser().parse(file("X0Y0D03*",end,"%unterminated trailing text")).solidGeometry()));
    }
    @ParameterizedTest @ValueSource(strings={"outside","nested","unclosed"})
    void rejectsMalformedRegionsWithADomainErrorInsteadOfDroppingArtwork(String variant){
        var lines=file();
        if(variant.equals("outside"))lines.add("G37*");
        else {lines.addAll(List.of("G36*","X0Y0D02*","X100000Y0D01*","X100000Y100000D01*"));if(variant.equals("nested"))lines.add("G36*");}
        lines.add("M02*");assertThrows(GerberParseException.class,()->new GerberParser().parse(lines));
    }
    @ParameterizedTest @ValueSource(strings={"M00*","M01*"})
    void optionalStopsDoNotEndTheFile(String stop){
        assertEquals(2,new GerberParser().parse(file("X0Y0D03*",stop,"X100000Y100000D03*","M02*")).shapes().size());
    }
    @Test void bomIsNotSilentlyRemovedFromTheMiddleOfACommand(){
        assertThrows(GerberParseException.class,()->new GerberParser().parse(file("X0\uFEFFY0D03*","M02*")));
    }
    @Test void bareInterpolationDoesNotRepeatAModalFlash(){
        assertEquals(1,new GerberParser().parse(file("X0Y0D03*","G01","M02*")).shapes().size());
    }
    @Test void excellonAlsoDistinguishesExplicitZeroFromAnOmittedAxis(){
        var image=new ExcellonParser().parse(List.of("M48","METRIC,LZ,000.000","T01C0.8","%","T01","X002000Y003000","X000000","Y000000","M30"));
        assertEquals(3,image.totalDrills());assertEquals(0,image.drills().get(1).x());assertEquals(3,image.drills().get(1).y());assertEquals(0,image.drills().get(2).y());
    }
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void explicitZerosDifferFromOmittedAxesAndZeroPaddingPreservesGeometry(String units){
        var lines=file("X100000Y50000D02*","X0D01*","Y0D01*","M02*");lines.set(1,"%MO"+units+"*%");
        var image=new GerberParser().parse(lines);var paths=image.followGeometry();
        assertEquals(0,paths.getEnvelopeInternal().getMinX());assertEquals(0,paths.getEnvelopeInternal().getMinY());
        assertEquals(1,paths.getEnvelopeInternal().getMaxX());assertEquals(.5,paths.getEnvelopeInternal().getMaxY());
        var padded=new GerberParser().parse(List.of("%FSLAX45Y45*%","%MO"+units+"*%","%ADD10C,0.254*%","D10*","X000100000Y000050000D02*","X000000000D01*","Y000000000D01*","M02*"));
        assertTrue(image.followGeometry().equalsExact(padded.followGeometry()));
        var first=image.shapes().getFirst().followGeometry().getCoordinates();
        assertEquals(.5,first[first.length-1].y,"Omitted Y retains previous coordinate");
        assertEquals(0,first[first.length-1].x,"Explicit X0 resets it");
    }
}
