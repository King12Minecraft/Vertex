package chat;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * CalcParser
 * ----------
 * The one slash command Vertex has: "/calc 2+3*4" in any chat box shows the answer to the
 * sender only. Evaluated locally by a small hand-written parser - no script engine, no
 * server round trip, nothing sent to anyone - so it can't be used to spam a channel or reach
 * anything but arithmetic. Supports + - * / % ^ (right-associative; -2^2 is -4), brackets,
 * decimals, the constants pi and e, and sqrt abs round floor ceil ln log sin cos tan min max
 * (trig in radians, log base 10). Shared between the trees only so the tests can reach it.
 */
public final class CalcParser
{
    private CalcParser() { }

    private static final int MAX_LENGTH = 200;
    private static final int MAX_DEPTH = 60;

    /** A calculation the parser understood but can't give an answer to (bad syntax, divide by zero, overflow). */
    public static class CalcException extends Exception
    {
        public CalcException(String message) { super(message); }
    }

    /** True for "/calc" on its own or followed by an expression (any case, surrounding spaces ignored). */
    public static boolean isCalcCommand(String text)
    {
        if (text == null)
        {
            return false;
        }
        String t = text.trim();
        return t.equalsIgnoreCase("/calc") || t.regionMatches(true, 0, "/calc ", 0, 6);
    }

    /** The line to show the sender for a "/calc ..." command - the answer, a usage hint, or why it can't be done. */
    public static String reply(String commandText)
    {
        String expression = commandText.trim().substring(5).trim();
        if (expression.isEmpty())
        {
            return "Usage: /calc 2+3*4   (also sqrt(16), 2^10, 7%3, 10/4, pi)";
        }
        try
        {
            return expression + " = " + format(evaluate(expression));
        }
        catch (CalcException e)
        {
            return "Can't calculate that: " + e.getMessage();
        }
    }

    public static double evaluate(String expression) throws CalcException
    {
        if (expression.length() > MAX_LENGTH)
        {
            throw new CalcException("that's too long");
        }
        Parser parser = new Parser(expression);
        double value = parser.parseExpression(0);
        parser.skipSpaces();
        if (!parser.atEnd())
        {
            throw new CalcException("unexpected \"" + parser.peek() + "\"");
        }
        if (Double.isNaN(value))
        {
            throw new CalcException("that isn't a number");
        }
        if (Double.isInfinite(value))
        {
            throw new CalcException("the result is too large");
        }
        return value;
    }

    /** 14 -> "14", 2.5 -> "2.5", 1/3 -> "0.3333333333"; very large or tiny values in scientific form. */
    static String format(double value)
    {
        if (value == 0)
        {
            return "0";
        }
        double abs = Math.abs(value);
        if (abs >= 1e15 || abs < 1e-9)
        {
            return String.format(java.util.Locale.US, "%.6e", value);
        }
        if (value == Math.rint(value))
        {
            return Long.toString((long) value);
        }
        return new BigDecimal(value).round(new MathContext(10)).stripTrailingZeros().toPlainString();
    }

    private static final class Parser
    {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }
        char peek() { return s.charAt(pos); }

        void skipSpaces()
        {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        private boolean accept(char c)
        {
            skipSpaces();
            if (pos < s.length() && s.charAt(pos) == c)
            {
                pos++;
                return true;
            }
            return false;
        }

        /** expression = term (('+'|'-') term)* */
        double parseExpression(int depth) throws CalcException
        {
            if (depth > MAX_DEPTH) throw new CalcException("too many brackets");
            double value = parseTerm(depth);
            while (true)
            {
                if (accept('+')) value += parseTerm(depth);
                else if (accept('-')) value -= parseTerm(depth);
                else return value;
            }
        }

        /** term = unary (('*'|'/'|'%') unary)* */
        private double parseTerm(int depth) throws CalcException
        {
            double value = parseUnary(depth);
            while (true)
            {
                if (accept('*')) value *= parseUnary(depth);
                else if (accept('/'))
                {
                    double divisor = parseUnary(depth);
                    if (divisor == 0) throw new CalcException("can't divide by zero");
                    value /= divisor;
                }
                else if (accept('%'))
                {
                    double divisor = parseUnary(depth);
                    if (divisor == 0) throw new CalcException("can't divide by zero");
                    value %= divisor;
                }
                else return value;
            }
        }

        /** unary = ('-'|'+') unary | power ;  power = primary ('^' unary)?  - so -2^2 = -(2^2) and 2^-1 works */
        private double parseUnary(int depth) throws CalcException
        {
            if (depth > MAX_DEPTH) throw new CalcException("too many brackets");
            if (accept('-')) return -parseUnary(depth + 1);
            if (accept('+')) return parseUnary(depth + 1);
            double base = parsePrimary(depth);
            if (accept('^'))
            {
                return Math.pow(base, parseUnary(depth + 1));
            }
            return base;
        }

        private double parsePrimary(int depth) throws CalcException
        {
            skipSpaces();
            if (atEnd()) throw new CalcException("the expression ends too soon");
            char c = peek();

            if (c == '(')
            {
                pos++;
                double value = parseExpression(depth + 1);
                if (!accept(')')) throw new CalcException("missing closing bracket");
                return value;
            }
            if (Character.isDigit(c) || c == '.')
            {
                return parseNumber();
            }
            if (Character.isLetter(c))
            {
                int start = pos;
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
                String name = s.substring(start, pos).toLowerCase();
                if (name.equals("pi")) return Math.PI;
                if (name.equals("e")) return Math.E;
                if (!accept('(')) throw new CalcException("\"" + name + "\" isn't something I know");
                double first = parseExpression(depth + 1);
                if (name.equals("min") || name.equals("max"))
                {
                    if (!accept(',')) throw new CalcException(name + " needs two numbers");
                    double second = parseExpression(depth + 1);
                    if (!accept(')')) throw new CalcException("missing closing bracket");
                    return name.equals("min") ? Math.min(first, second) : Math.max(first, second);
                }
                if (!accept(')')) throw new CalcException("missing closing bracket");
                return applyFunction(name, first);
            }
            throw new CalcException("unexpected \"" + c + "\"");
        }

        private double parseNumber() throws CalcException
        {
            int start = pos;
            boolean dot = false;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || (s.charAt(pos) == '.' && !dot)))
            {
                if (s.charAt(pos) == '.') dot = true;
                pos++;
            }
            try
            {
                return Double.parseDouble(s.substring(start, pos));
            }
            catch (NumberFormatException e)
            {
                throw new CalcException("\"" + s.substring(start, pos) + "\" isn't a number");
            }
        }

        private double applyFunction(String name, double x) throws CalcException
        {
            if (name.equals("sqrt"))
            {
                if (x < 0) throw new CalcException("no square root of a negative number");
                return Math.sqrt(x);
            }
            if (name.equals("abs")) return Math.abs(x);
            if (name.equals("round")) return Math.rint(x) == x ? x : Math.floor(x + 0.5);
            if (name.equals("floor")) return Math.floor(x);
            if (name.equals("ceil")) return Math.ceil(x);
            if (name.equals("ln") || name.equals("log"))
            {
                if (x <= 0) throw new CalcException("the logarithm needs a number above zero");
                return name.equals("ln") ? Math.log(x) : Math.log10(x);
            }
            if (name.equals("sin")) return Math.sin(x);
            if (name.equals("cos")) return Math.cos(x);
            if (name.equals("tan")) return Math.tan(x);
            throw new CalcException("\"" + name + "\" isn't something I know");
        }
    }
}
