import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InAppCompiler
 * -------------
 * The "write code in a screen itself" half of the Roblox-style custom
 * games feature (see CodeEditorWindow) - compiles a single source
 * file's text using the JDK's own javax.tools compiler API, entirely
 * in-process, no external javac process spawned.
 *
 * Only actually works when Vertex is running under a JDK, not a bare
 * JRE - ToolProvider.getSystemJavaCompiler() returns null on a JRE,
 * since no compiler ships with one. isAvailable() lets CodeEditorWindow
 * detect that up front and point people at "Upload Project" instead
 * (compile elsewhere - e.g. BlueJ - export a jar, upload that).
 */
public class InAppCompiler
{
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final Pattern ANY_CLASS_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)");

    public static class Result
    {
        public boolean success;
        public String className;
        public File outputDir;
        public String log;
    }

    private InAppCompiler()
    {
        // Static utility class - never instantiated.
    }

    public static boolean isAvailable()
    {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    /** The top-level class name in this source text - the "public class X" one if there is one, otherwise the first "class X" found. Null if no class declaration exists at all. */
    public static String extractClassName(String source)
    {
        Matcher publicMatch = PUBLIC_CLASS_PATTERN.matcher(source);
        if (publicMatch.find())
        {
            return publicMatch.group(1);
        }
        Matcher anyMatch = ANY_CLASS_PATTERN.matcher(source);
        if (anyMatch.find())
        {
            return anyMatch.group(1);
        }
        return null;
    }

    /** Compiles the given source text (a single file). BLOCKING - always call from a background thread. */
    public static Result compile(String source)
    {
        Result result = new Result();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            result.success = false;
            result.log = "Live compiling isn't available in this build (no JDK bundled with the app you're running) - "
                + "use \"Upload Project\" with a jar compiled elsewhere (e.g. BlueJ) instead.";
            return result;
        }

        String className = extractClassName(source);
        if (className == null)
        {
            result.success = false;
            result.log = "Could not find a class declaration - your code needs a line like \"public class MyGame extends JFrame\".";
            return result;
        }
        result.className = className;

        File workDir;
        File sourceDir;
        File outDir;
        try
        {
            workDir = File.createTempFile("vertex-code-", "");
            workDir.delete();
            workDir.mkdirs();
            sourceDir = new File(workDir, "src");
            sourceDir.mkdirs();
            outDir = new File(workDir, "out");
            outDir.mkdirs();
        }
        catch (IOException e)
        {
            result.success = false;
            result.log = "Could not create a temp directory to compile into: " + e.getMessage();
            return result;
        }
        workDir.deleteOnExit();

        File sourceFile = new File(sourceDir, className + ".java");
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(sourceFile);
            writer.write(source);
        }
        catch (IOException e)
        {
            result.success = false;
            result.log = "Could not write the source file: " + e.getMessage();
            return result;
        }
        finally
        {
            if (writer != null) { try { writer.close(); } catch (IOException ignored) { } }
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try
        {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(outDir));
        }
        catch (IOException e)
        {
            result.success = false;
            result.log = "Could not set the compiler's output directory: " + e.getMessage();
            return result;
        }

        Iterable<? extends JavaFileObject> units =
            fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(sourceFile));

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, units);
        boolean ok = task.call();

        try { fileManager.close(); } catch (IOException ignored) { }

        StringBuilder log = new StringBuilder();
        List<Diagnostic<? extends JavaFileObject>> list = diagnostics.getDiagnostics();
        for (int i = 0; i < list.size(); i++)
        {
            Diagnostic<? extends JavaFileObject> d = list.get(i);
            log.append("Line ").append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
        }

        result.success = ok;
        result.outputDir = outDir;
        result.log = ok ? "Compiled successfully." : log.toString();
        return result;
    }

    /** Packages every .class file under outDir (the entry class plus any inner/anonymous classes it generated) into an in-memory jar. */
    public static byte[] packageJar(File outDir) throws IOException
    {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        JarOutputStream jarOut = new JarOutputStream(byteOut);
        try
        {
            addClassFiles(outDir, "", jarOut);
        }
        finally
        {
            jarOut.close();
        }
        return byteOut.toByteArray();
    }

    private static void addClassFiles(File dir, String prefix, JarOutputStream jarOut) throws IOException
    {
        File[] files = dir.listFiles();
        if (files == null)
        {
            return;
        }
        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
            if (file.isDirectory())
            {
                addClassFiles(file, prefix + file.getName() + "/", jarOut);
                continue;
            }
            if (!file.getName().endsWith(".class"))
            {
                continue;
            }
            jarOut.putNextEntry(new JarEntry(prefix + file.getName()));
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            jarOut.write(bytes);
            jarOut.closeEntry();
        }
    }
}
