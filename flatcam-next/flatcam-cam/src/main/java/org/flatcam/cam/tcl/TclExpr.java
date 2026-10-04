package org.flatcam.cam.tcl;

/**
 * A small arithmetic/comparison/logical expression evaluator for {@code if}, {@code while} and
 * the {@code expr} command - this dialect's deliberately reduced stand-in for Tcl's own
 * {@code expr}. Operands are either numbers (parsed with {@link Double#parseDouble}) or bare
 * strings; {@code ==}/{@code !=} fall back to string comparison when either side is not
 * numeric, every other operator requires both sides to be numeric. Grammar, loosest to
 * tightest binding (same order as Tcl/C):
 *
 * <pre>
 * expr       := or
 * or         := and ('||' and)*
 * and        := equality ('&&' equality)*
 * equality   := relational (('==' | '!=') relational)*
 * relational := additive (('&lt;' | '&gt;' | '&lt;=' | '&gt;=') additive)*
 * additive   := multiplicative (('+' | '-') multiplicative)*
 * multiplicative := unary (('*' | '/' | '%') unary)*
 * unary      := ('!' | '-' | '+')? primary
 * primary    := NUMBER | STRING | 'true' | 'false' | '(' expr ')'
 * </pre>
 *
 * <p>Callers are expected to run {@link TclInterpreter#substitute} on the raw condition/expression
 * text first, so by the time this class sees it every {@code $var} and {@code [cmd]} is already
 * resolved to a plain literal - this class itself knows nothing about variables or commands.
 */
final class TclExpr {

    private record Value(boolean numeric, double number, String text) {
        static Value of(double number) {
            return new Value(true, number, null);
        }

        static Value of(String text) {
            return new Value(false, Double.NaN, text);
        }

        String asText() {
            if (!numeric) return text;
            if (number == Math.rint(number) && !Double.isInfinite(number)
                    && Math.abs(number) < 1e15) {
                return Long.toString((long) number);
            }
            String formatted = Double.toString(number);
            return formatted;
        }

        boolean asBoolean() throws TclException {
            if (numeric) return number != 0;
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
            throw new TclException("expected boolean value but got \"" + text + "\"");
        }

        double asNumber() throws TclException {
            if (numeric) return number;
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw new TclException("expected number but got \"" + text + "\"");
            }
        }
    }

    private final String source;
    private int position;

    private TclExpr(String source) {
        this.source = source;
    }

    static boolean evaluateBoolean(String expression) throws TclException {
        return parse(expression).asBoolean();
    }

    static String evaluate(String expression) throws TclException {
        return parse(expression).asText();
    }

    private static Value parse(String expression) throws TclException {
        TclExpr parser = new TclExpr(expression);
        parser.skipSpaces();
        Value result = parser.parseOr();
        parser.skipSpaces();
        if (parser.position < parser.source.length()) {
            throw new TclException("syntax error in expression \"" + expression + "\"");
        }
        return result;
    }

    private Value parseOr() throws TclException {
        Value left = parseAnd();
        skipSpaces();
        while (lookingAt("||")) {
            position += 2;
            Value right = parseAnd();
            left = Value.of((left.asBoolean() || right.asBoolean()) ? 1 : 0);
            skipSpaces();
        }
        return left;
    }

    private Value parseAnd() throws TclException {
        Value left = parseEquality();
        skipSpaces();
        while (lookingAt("&&")) {
            position += 2;
            Value right = parseEquality();
            left = Value.of((left.asBoolean() && right.asBoolean()) ? 1 : 0);
            skipSpaces();
        }
        return left;
    }

    private Value parseEquality() throws TclException {
        Value left = parseRelational();
        skipSpaces();
        while (lookingAt("==") || lookingAt("!=")) {
            boolean equals = lookingAt("==");
            position += 2;
            Value right = parseRelational();
            boolean areEqual = left.numeric && right.numeric
                    ? left.number == right.number
                    : left.asText().equals(right.asText());
            left = Value.of((equals == areEqual) ? 1 : 0);
            skipSpaces();
        }
        return left;
    }

    private Value parseRelational() throws TclException {
        Value left = parseAdditive();
        skipSpaces();
        while (true) {
            String operator = null;
            if (lookingAt("<=") || lookingAt(">=")) {
                operator = source.substring(position, position + 2);
                position += 2;
            } else if (lookingAt("<") || lookingAt(">")) {
                operator = source.substring(position, position + 1);
                position += 1;
            } else {
                break;
            }
            Value right = parseAdditive();
            double a = left.asNumber();
            double b = right.asNumber();
            boolean result = switch (operator) {
                case "<" -> a < b;
                case ">" -> a > b;
                case "<=" -> a <= b;
                case ">=" -> a >= b;
                default -> throw new IllegalStateException(operator);
            };
            left = Value.of(result ? 1 : 0);
            skipSpaces();
        }
        return left;
    }

    private Value parseAdditive() throws TclException {
        Value left = parseMultiplicative();
        skipSpaces();
        while (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) {
            char operator = source.charAt(position++);
            Value right = parseMultiplicative();
            left = Value.of(operator == '+' ? left.asNumber() + right.asNumber() : left.asNumber() - right.asNumber());
            skipSpaces();
        }
        return left;
    }

    private Value parseMultiplicative() throws TclException {
        Value left = parseUnary();
        skipSpaces();
        while (position < source.length()
                && (source.charAt(position) == '*' || source.charAt(position) == '/' || source.charAt(position) == '%')) {
            char operator = source.charAt(position++);
            Value right = parseUnary();
            double a = left.asNumber();
            double b = right.asNumber();
            double result = switch (operator) {
                case '*' -> a * b;
                case '/' -> a / b;
                case '%' -> a % b;
                default -> throw new IllegalStateException(String.valueOf(operator));
            };
            left = Value.of(result);
            skipSpaces();
        }
        return left;
    }

    private Value parseUnary() throws TclException {
        skipSpaces();
        if (position < source.length() && source.charAt(position) == '!') {
            position++;
            Value value = parseUnary();
            return Value.of(value.asBoolean() ? 0 : 1);
        }
        if (position < source.length() && source.charAt(position) == '-') {
            position++;
            return Value.of(-parseUnary().asNumber());
        }
        if (position < source.length() && source.charAt(position) == '+') {
            position++;
            return parseUnary();
        }
        return parsePrimary();
    }

    private Value parsePrimary() throws TclException {
        skipSpaces();
        if (position >= source.length()) {
            throw new TclException("syntax error in expression \"" + source + "\": unexpected end");
        }
        char c = source.charAt(position);
        if (c == '(') {
            position++;
            Value value = parseOr();
            skipSpaces();
            if (position >= source.length() || source.charAt(position) != ')') {
                throw new TclException("syntax error in expression \"" + source + "\": missing \")\"");
            }
            position++;
            return value;
        }
        if (c == '"') {
            position++;
            StringBuilder text = new StringBuilder();
            while (position < source.length() && source.charAt(position) != '"') {
                text.append(source.charAt(position++));
            }
            if (position >= source.length()) {
                throw new TclException("syntax error in expression \"" + source + "\": missing closing \"");
            }
            position++;
            return Value.of(text.toString());
        }
        if (Character.isDigit(c) || (c == '.' && position + 1 < source.length() && Character.isDigit(source.charAt(position + 1)))) {
            int start = position;
            while (position < source.length()
                    && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                position++;
            }
            return Value.of(Double.parseDouble(source.substring(start, position)));
        }
        // Bare word: an identifier/boolean literal, or a string with no special characters.
        int start = position;
        while (position < source.length() && !isOperatorStart(source.charAt(position)) && !Character.isWhitespace(source.charAt(position))) {
            position++;
        }
        if (position == start) {
            throw new TclException("syntax error in expression \"" + source + "\" near character " + position);
        }
        return Value.of(source.substring(start, position));
    }

    private boolean isOperatorStart(char c) {
        return c == '(' || c == ')' || c == '<' || c == '>' || c == '=' || c == '!' || c == '&' || c == '|'
                || c == '+' || c == '-' || c == '*' || c == '/' || c == '%';
    }

    private boolean lookingAt(String token) {
        return source.regionMatches(position, token, 0, token.length());
    }

    private void skipSpaces() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }
}
