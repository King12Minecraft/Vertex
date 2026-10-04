package chat;

import support.Check;

/**
 * CalcParserTest
 * ---------------
 * The /calc command's local parser: arithmetic and precedence, brackets, unary minus and
 * right-associative powers, functions and constants, and - just as important - that bad or
 * hostile input produces a short message instead of an exception, hang or stack overflow.
 */
public class CalcParserTest
{
    private static String reply(String expr) { return CalcParser.reply("/calc " + expr); }

    private static void is(Check check, String expr, String expected)
    {
        String got = reply(expr);
        check.check("/calc " + expr + " -> " + expected + " (got: " + got + ")", got.equals(expr.trim() + " = " + expected));
    }

    private static void refused(Check check, String expr)
    {
        String got = reply(expr);
        check.check("/calc " + expr + " is refused politely (got: " + got + ")", got.startsWith("Can't calculate that: "));
    }

    public static void main(String[] args)
    {
        Check check = new Check();

        check.check("the command is recognised in any case and with spaces",
            CalcParser.isCalcCommand("/calc 1+1") && CalcParser.isCalcCommand("  /CALC 2 ") && CalcParser.isCalcCommand("/calc"));
        check.check("other text and other slash commands are not", !CalcParser.isCalcCommand("calc 1+1")
            && !CalcParser.isCalcCommand("/calculate 1") && !CalcParser.isCalcCommand("/help") && !CalcParser.isCalcCommand(null)
            && !CalcParser.isCalcCommand("hello /calc 1"));
        check.check("bare /calc gives a usage hint", reply("").startsWith("Usage:"));

        is(check, "2+3*4", "14");
        is(check, "(2+3)*4", "20");
        is(check, "10/4", "2.5");
        is(check, "7%3", "1");
        is(check, "2^10", "1024");
        is(check, "2^3^2", "512");            // right-associative: 2^(3^2)
        is(check, "-2^2", "-4");              // unary minus binds looser than ^
        is(check, "2^-1", "0.5");
        is(check, "1/3", "0.3333333333");
        is(check, "0.1+0.2", "0.3");
        is(check, "  3 *  ( 4 + 1 ) ", "15");
        is(check, "sqrt(16)+abs(-3)", "7");
        is(check, "round(2.5)+floor(2.9)+ceil(2.1)", "8");
        is(check, "max(3, 9)-min(3, 9)", "6");
        is(check, "log(1000)", "3");
        is(check, "ln(e)", "1");
        is(check, "sin(0)+cos(0)", "1");
        is(check, "2*pi", "6.283185307");

        refused(check, "1/0");
        refused(check, "5%0");
        refused(check, "sqrt(-1)");
        refused(check, "ln(0)");
        refused(check, "2+");
        refused(check, "(2+3");
        refused(check, "2+3)");
        refused(check, "abc");
        refused(check, "foo(2)");
        refused(check, "2 $ 3");
        refused(check, "10^1000");
        refused(check, "1.2.3");
        refused(check, "1e3");                // no scientific literals: 1 followed by the constant e
        refused(check, "max(1)");

        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 5000; i++) deep.append('(');
        deep.append('1');
        for (int i = 0; i < 5000; i++) deep.append(')');
        refused(check, deep.toString());       // must not overflow the stack
        refused(check, "1" + repeat("-", 300) + "1");   // over the length limit

        check.check("very large results use scientific form", reply("10^20").contains("e+20"));

        check.finish();
    }

    private static String repeat(String s, int n)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
