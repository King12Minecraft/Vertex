import javax.swing.JFrame;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * CustomGameLoader
 * ----------------
 * Loads and launches an uploaded custom game jar. Deliberately NO
 * sandboxing - the loaded class runs with exactly the same JVM
 * permissions Vertex itself has (see CustomGameStore's own javadoc on
 * the server for why that trade-off is acceptable here).
 *
 * The contract a custom game must follow is the same one every
 * built-in game already follows in practice (see GameLauncher, which
 * just does "new XWindow(); window.setVisible(true);" for each one) -
 * a public class extending JFrame with a public no-argument
 * constructor. Vertex instantiates it and shows it, nothing more
 * exotic than that - no need to also implement the separate Game
 * interface, which isn't actually what GameLauncher uses today.
 */
public class CustomGameLoader
{
    private CustomGameLoader()
    {
        // Static utility class - never instantiated.
    }

    /**
     * Writes the jar to a temp file, loads entryClassName from it, and
     * shows it as a window. Throws on any failure (bad class name,
     * class doesn't extend JFrame, no no-arg constructor, an exception
     * from the game's own constructor, ...) - the caller is responsible
     * for surfacing that, same try/catch-and-show-a-dialog pattern
     * GameLauncher itself uses for the built-in games.
     */
    public static void launch(byte[] jarBytes, String entryClassName) throws Exception
    {
        File tempJar = File.createTempFile("vertex-custom-", ".jar");
        tempJar.deleteOnExit();

        FileOutputStream out = new FileOutputStream(tempJar);
        try
        {
            out.write(jarBytes);
        }
        finally
        {
            out.close();
        }

        URLClassLoader loader = new URLClassLoader(
            new URL[] { tempJar.toURI().toURL() }, CustomGameLoader.class.getClassLoader());

        instantiateAndShow(loader, entryClassName);
    }

    /** Same launch, but straight from a directory of freshly-compiled .class files rather than a jar - used by CodeEditorWindow's "Compile & Run" (Publish is what actually packages a jar, for anything going to the server). */
    public static void launchFromClassesDir(File classesDir, String className) throws Exception
    {
        URLClassLoader loader = new URLClassLoader(
            new URL[] { classesDir.toURI().toURL() }, CustomGameLoader.class.getClassLoader());

        instantiateAndShow(loader, className);
    }

    private static void instantiateAndShow(URLClassLoader loader, String className) throws Exception
    {
        Class<?> clazz = Class.forName(className, true, loader);
        if (!JFrame.class.isAssignableFrom(clazz))
        {
            throw new IllegalStateException("\"" + className + "\" does not extend JFrame - it can't be launched as a game window.");
        }

        Constructor<?> constructor = clazz.getDeclaredConstructor();
        Object instance = constructor.newInstance();
        ((JFrame) instance).setVisible(true);
    }
}
