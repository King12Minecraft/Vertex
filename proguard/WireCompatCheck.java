import java.io.File;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * WireCompatCheck <plain.jar> <obfuscated.jar>
 *
 * Guards the one way obfuscation can break the game without a compile error: the client and server talk with
 * Java serialization, which needs the class names, field names and serialVersionUIDs to match on both sides.
 * For every Serializable class the client shares with the server (the packages listed below) this checks that the
 * obfuscated jar still has a class of the same name with the same serial version and the same serialised fields.
 * Exit code 1 and a list of problems if anything differs. Nothing is initialised or run - classes are only loaded.
 */
public class WireCompatCheck
{
    /** Packages the server also contains (see CLAUDE.md's sync rule). Keep in step with proguard/vertex-client.pro. */
    private static final String[] SHARED = { "account", "admin", "ai", "chat", "dominion", "economy", "forum",
        "games", "mechanics", "net", "social" };

    public static void main(String[] args) throws Exception
    {
        URLClassLoader plain = new URLClassLoader(new URL[] { new File(args[0]).toURI().toURL() }, null);
        URLClassLoader obf = new URLClassLoader(new URL[] { new File(args[1]).toURI().toURL() }, null);
        int checked = 0;
        Set<String> problems = new TreeSet<String>();
        JarFile jar = new JarFile(args[0]);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements())
        {
            String name = entries.nextElement().getName();
            if (!name.endsWith(".class") || !isShared(name))
            {
                continue;
            }
            String className = name.substring(0, name.length() - 6).replace('/', '.');
            Class<?> p;
            try
            {
                p = Class.forName(className, false, plain);
            }
            catch (Throwable t)
            {
                continue;   // not loadable on its own (e.g. needs a display class) - nothing to compare
            }
            // Swing components are Serializable too but never sent anywhere; only data classes matter here.
            if (!Serializable.class.isAssignableFrom(p) || java.awt.Component.class.isAssignableFrom(p))
            {
                continue;
            }
            checked++;
            Class<?> o;
            try
            {
                o = Class.forName(className, false, obf);
            }
            catch (Throwable t)
            {
                problems.add(className + ": missing from the obfuscated jar (renamed?)");
                continue;
            }
            ObjectStreamClass sp = ObjectStreamClass.lookup(p);
            ObjectStreamClass so = ObjectStreamClass.lookup(o);
            if (sp != null && so != null && sp.getSerialVersionUID() != so.getSerialVersionUID())
            {
                problems.add(className + ": serialVersionUID differs");
            }
            if (!fields(p).equals(fields(o)))
            {
                problems.add(className + ": serialised fields differ  plain=" + fields(p) + "  obfuscated=" + fields(o));
            }
        }
        jar.close();
        if (problems.isEmpty())
        {
            System.out.println("WireCompatCheck OK - " + checked + " shared Serializable classes match the plain build.");
            return;
        }
        System.out.println("WireCompatCheck FAILED:");
        for (String problem : problems)
        {
            System.out.println("  " + problem);
        }
        System.exit(1);
    }

    private static boolean isShared(String entryName)
    {
        for (String pkg : SHARED)
        {
            if (entryName.startsWith(pkg + "/"))
            {
                return true;
            }
        }
        return false;
    }

    /** Non-static, non-transient fields as "name:type" - what Java serialization writes. */
    private static Set<String> fields(Class<?> c)
    {
        Set<String> result = new TreeSet<String>();
        for (Field f : c.getDeclaredFields())
        {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) && !Modifier.isTransient(m))
            {
                result.add(f.getName() + ":" + f.getType().getName());
            }
        }
        return result;
    }
}
