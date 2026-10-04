package org.flatcam.cam.tcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TclArgsTest {

    @Test
    void positionalWordsComeBeforeAnyOption() throws TclException {
        TclArgs args = TclArgs.parse(List.of("gerber_file", "-dia", "1.2", "-outname", "cutout_geo"));
        assertEquals(1, args.positionalCount());
        assertEquals("gerber_file", args.positional(0));
        assertEquals("1.2", args.option("dia"));
        assertEquals("cutout_geo", args.option("outname"));
    }

    @Test
    void missingPositionalArgumentIsAnError() {
        TclArgs args = TclArgs.parse(List.of());
        assertThrows(TclException.class, () -> args.positional(0));
    }

    @Test
    void positionalOrDefaultFallsBackWhenAbsent() {
        TclArgs args = TclArgs.parse(List.of("only_one"));
        assertEquals("only_one", args.positionalOrDefault(0, "fallback"));
        assertEquals("fallback", args.positionalOrDefault(1, "fallback"));
    }

    @Test
    void anOptionWithNoFollowingValueIsRecordedAsAbsentNotBlank() {
        TclArgs args = TclArgs.parse(List.of("name", "-combine"));
        assertFalse(args.has("combine"));
        assertThrows(TclException.class, () -> args.requireOption("combine"));
    }

    @Test
    void anOptionFollowedByAnotherOptionIsAlsoAbsent() {
        TclArgs args = TclArgs.parse(List.of("-first", "-second", "value"));
        assertFalse(args.has("first"));
        assertEquals("value", args.option("second"));
    }

    @Test
    void requireOptionThrowsWhenMissingEntirely() {
        TclArgs args = TclArgs.parse(List.of("name"));
        TclException error = assertThrows(TclException.class, () -> args.requireOption("dia"));
        assertTrue(error.getMessage().contains("dia"));
    }

    @Test
    void numericAccessorsParseAndValidate() throws TclException {
        TclArgs args = TclArgs.parse(List.of("name", "-dia", "1.2", "-passes", "3"));
        assertEquals(1.2, args.requireDouble("dia"), 1e-9);
        assertEquals(3, args.intOrDefault("passes", -1));
        assertEquals(-1, args.intOrDefault("missing", -1));
        assertEquals(2.5, args.doubleOrDefault("missing", 2.5), 1e-9);
    }

    @Test
    void numericAccessorsRejectNonNumericText() {
        TclArgs args = TclArgs.parse(List.of("name", "-dia", "not-a-number"));
        assertThrows(TclException.class, () -> args.requireDouble("dia"));
    }

    @Test
    void booleanAccessorRecognizesTrueOneAndYes() {
        assertTrue(TclArgs.parse(List.of("-combine", "True")).booleanOrDefault("combine", false));
        assertTrue(TclArgs.parse(List.of("-combine", "1")).booleanOrDefault("combine", false));
        assertTrue(TclArgs.parse(List.of("-combine", "yes")).booleanOrDefault("combine", false));
        assertFalse(TclArgs.parse(List.of("-combine", "False")).booleanOrDefault("combine", true));
        assertTrue(TclArgs.parse(List.of()).booleanOrDefault("combine", true));
    }

    @Test
    void rejectUnknownOptionsFlagsAnythingNotDeclared() {
        TclArgs args = TclArgs.parse(List.of("name", "-dia", "1.2", "-bogus", "x"));
        assertThrows(TclException.class, () -> args.rejectUnknownOptions(Set.of("dia", "outname")));
    }

    @Test
    void rejectUnknownOptionsAcceptsOnlyDeclaredNames() throws TclException {
        TclArgs args = TclArgs.parse(List.of("name", "-dia", "1.2"));
        args.rejectUnknownOptions(Set.of("dia", "outname"));
    }
}
