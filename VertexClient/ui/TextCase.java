package ui;

/**
 * TextCase
 * --------
 * The restyled UI uses sentence case for titles ("Welcome back", not "WELCOME BACK"), but most call sites were
 * written with ALL-CAPS titles. sentence(...) converts only text that is entirely upper case - anything already
 * mixed-case (a game name, a changelog heading) is left exactly as written.
 */
public final class TextCase
{
    private TextCase() { }

    public static String sentence(String text)
    {
        if (text == null || text.isEmpty())
        {
            return text;
        }
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (Character.isLetter(c))
            {
                hasLetter = true;
                if (Character.isLowerCase(c))
                {
                    return text;   // already has its own casing
                }
            }
        }
        if (!hasLetter)
        {
            return text;
        }
        String lower = text.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
