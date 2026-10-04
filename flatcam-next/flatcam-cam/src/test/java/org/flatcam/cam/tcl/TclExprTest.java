package org.flatcam.cam.tcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct coverage of the expression grammar, beyond what flows through TclInterpreterTest's "expr". */
class TclExprTest {

    @Test
    void arithmeticFollowsStandardPrecedence() throws TclException {
        assertEquals("14", TclExpr.evaluate("2 + 3 * 4"));
        assertEquals("20", TclExpr.evaluate("(2 + 3) * 4"));
        assertEquals("1", TclExpr.evaluate("7 % 2"));
        assertEquals("2.5", TclExpr.evaluate("5 / 2.0"));
    }

    @Test
    void unaryMinusAndPlusBindTighterThanBinaryOperators() throws TclException {
        assertEquals("-1", TclExpr.evaluate("-1"));
        assertEquals("3", TclExpr.evaluate("5 + -2"));
        assertEquals("-6", TclExpr.evaluate("-2 * 3"));
        assertEquals("5", TclExpr.evaluate("+5"));
    }

    @Test
    void relationalAndEqualityOperators() throws TclException {
        assertTrue(TclExpr.evaluateBoolean("3 > 2"));
        assertFalse(TclExpr.evaluateBoolean("3 < 2"));
        assertTrue(TclExpr.evaluateBoolean("2 <= 2"));
        assertTrue(TclExpr.evaluateBoolean("2 >= 2"));
        assertTrue(TclExpr.evaluateBoolean("2 == 2"));
        assertTrue(TclExpr.evaluateBoolean("2 != 3"));
    }

    @Test
    void logicalOperatorsShortCircuitToBooleanValues() throws TclException {
        assertTrue(TclExpr.evaluateBoolean("1 && 1"));
        assertFalse(TclExpr.evaluateBoolean("1 && 0"));
        assertTrue(TclExpr.evaluateBoolean("0 || 1"));
        assertFalse(TclExpr.evaluateBoolean("0 || 0"));
        assertTrue(TclExpr.evaluateBoolean("!0"));
        assertFalse(TclExpr.evaluateBoolean("!1"));
    }

    @Test
    void stringsCompareLexicallyWhenNotNumeric() throws TclException {
        assertTrue(TclExpr.evaluateBoolean("\"abc\" == \"abc\""));
        assertFalse(TclExpr.evaluateBoolean("\"abc\" == \"xyz\""));
        assertTrue(TclExpr.evaluateBoolean("\"abc\" != \"xyz\""));
    }

    @Test
    void trueAndFalseLiteralsAreBooleans() throws TclException {
        assertTrue(TclExpr.evaluateBoolean("true"));
        assertFalse(TclExpr.evaluateBoolean("false"));
        assertTrue(TclExpr.evaluateBoolean("true && true"));
    }

    @Test
    void integerResultsFormatWithoutADecimalPoint() throws TclException {
        assertEquals("6", TclExpr.evaluate("2 * 3"));
        assertEquals("2.5", TclExpr.evaluate("5 / 2.0"));
    }

    @Test
    void relationalOperatorsRejectNonNumericOperands() {
        assertThrows(TclException.class, () -> TclExpr.evaluate("\"abc\" > 1"));
    }

    @Test
    void unbalancedParenthesesAreASyntaxError() {
        assertThrows(TclException.class, () -> TclExpr.evaluate("(1 + 2"));
    }

    @Test
    void trailingGarbageAfterAValidExpressionIsASyntaxError() {
        assertThrows(TclException.class, () -> TclExpr.evaluate("1 + 2 3"));
    }

    @Test
    void emptyExpressionIsASyntaxError() {
        assertThrows(TclException.class, () -> TclExpr.evaluate(""));
    }
}
