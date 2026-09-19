package org.flatcam.cam.gerber;

/**
 * Evaluates one aperture-macro numeric field, e.g. {@code "1.08239X$1"}
 * (literal 1.08239 times macro variable $1). Supports +, -, x/X (multiply),
 * / (divide), unary minus, parentheses, decimal literals and $n variables -
 * enough for the one macro (OC8, a primitive-5 polygon) in the Fase 0
 * fixture corpus. Not a general Gerber macro-language implementation.
 */
final class MacroExpression {

    private final String text;
    private final double[] variables;
    private int pos;

    private MacroExpression(String text, double[] variables) {
        this.text = text;
        this.variables = variables;
    }

    static double evaluate(String expression, double[] variables) {
        MacroExpression parser = new MacroExpression(expression.trim(), variables);
        double result = parser.parseExpression();
        parser.skipSpaces();
        if (parser.pos != parser.text.length()) {
            throw new GerberParseException("Unexpected trailing input in macro expression: " + expression);
        }
        return result;
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (peek() == '+') {
                pos++;
                value += parseTerm();
            } else if (peek() == '-') {
                pos++;
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseFactor();
        while (true) {
            skipSpaces();
            char c = peek();
            if (c == 'x' || c == 'X' || c == '*') {
                pos++;
                value *= parseFactor();
            } else if (c == '/') {
                pos++;
                value /= parseFactor();
            } else {
                return value;
            }
        }
    }

    private double parseFactor() {
        skipSpaces();
        char c = peek();
        if (c == '-') {
            pos++;
            return -parseFactor();
        }
        if (c == '+') {
            pos++;
            return parseFactor();
        }
        if (c == '(') {
            pos++;
            double value = parseExpression();
            skipSpaces();
            expect(')');
            return value;
        }
        if (c == '$') {
            pos++;
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            int index = Integer.parseInt(text.substring(start, pos));
            if (index < 1 || index > variables.length) {
                throw new GerberParseException("Macro variable $" + index + " out of range in: " + text);
            }
            return variables[index - 1];
        }
        int start = pos;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new GerberParseException("Cannot parse macro expression: " + text);
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new GerberParseException("Expected '" + c + "' in macro expression: " + text);
        }
        pos++;
    }
}
