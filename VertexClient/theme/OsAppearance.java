package theme;

import java.io.InputStream;

/**
 * OsAppearance
 * ------------
 * Best-effort "is the operating system in dark mode?" - read once at start-up so the default theme matches the
 * desktop. Windows (registry), macOS (defaults) and GNOME-style Linux (gsettings) are asked with a short timeout;
 * anything that fails, times out or is unknown counts as light. Only the default is affected: picking a theme in
 * Settings overrides it.
 */
final class OsAppearance
{
    private OsAppearance() { }

    static boolean isDark()
    {
        try
        {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win"))
            {
                String out = run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
                return out.contains("0x0");
            }
            if (os.contains("mac"))
            {
                return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase().contains("dark");
            }
            return run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").toLowerCase().contains("dark");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static String run(String... command) throws Exception
    {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS))
        {
            process.destroyForcibly();
            return "";
        }
        InputStream in = process.getInputStream();
        byte[] buffer = new byte[1024];
        int n = in.read(buffer);
        return n > 0 ? new String(buffer, 0, n) : "";
    }
}
