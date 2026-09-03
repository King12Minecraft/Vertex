import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.CodeSource;

/**
 * ClientUpdateChecker
 * --------------------
 * Client-only. Runs once in the background shortly after startup:
 * hashes the jar this app was actually launched from and asks the
 * server whether it's out of date (CLIENT_VERSION_CHECK_REQUEST). If
 * so, downloads the server's copy (CLIENT_UPDATE_DOWNLOAD_REQUEST)
 * and stages it as "Vertex.jar.new" right next to the running jar.
 *
 * Applying it just means closing and reopening the app - Vertex.exe
 * (see launcher.c) checks for that staged file on every launch and
 * swaps it in before the JVM even starts, so there's never a "can't
 * overwrite a file that's currently running" problem. This class
 * itself never restarts anything or interrupts whatever the person is
 * doing - it stages the file and posts a NotificationCenter entry,
 * same "quiet until there's something to say" pattern GameManager
 * already uses for "a game was added/updated."
 *
 * Skipped entirely when not running from a packaged jar (BlueJ, or
 * unpacked .class files) - there's no "my own jar" to hash or
 * replace, and that's a dev environment anyway, not something a
 * friend would be running.
 *
 * Uses blocking NetworkManager.send() on its own background thread,
 * same as every other one-shot request/response call in this
 * codebase (see AchievementsPanel's note on why - both new response
 * types here are in NetworkManager's RESPONSE_TYPES set and nothing
 * else ever requests them via sendAsync()/onPush, so there's no risk
 * of the misrouting bug that pattern caused elsewhere).
 */
public class ClientUpdateChecker
{
    private ClientUpdateChecker() { }

    public static void checkInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run() { runCheck(); }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private static void runCheck()
    {
        File jarFile = locateOwnJar();
        if (jarFile == null)
        {
            return;
        }

        byte[] currentBytes;
        try
        {
            currentBytes = Files.readAllBytes(jarFile.toPath());
        }
        catch (IOException e)
        {
            return;
        }
        String currentHash = FileHash.sha256Hex(currentBytes);

        Message versionRequest = new Message();
        versionRequest.setType(MessageType.CLIENT_VERSION_CHECK_REQUEST);
        versionRequest.setClientJarHash(currentHash);
        Message versionResponse = NetworkManager.send(versionRequest);

        if (versionResponse == null || !versionResponse.isSuccess() || !versionResponse.isUpdateAvailable())
        {
            return;
        }

        Message downloadRequest = new Message();
        downloadRequest.setType(MessageType.CLIENT_UPDATE_DOWNLOAD_REQUEST);
        Message downloadResponse = NetworkManager.send(downloadRequest);

        if (downloadResponse == null || !downloadResponse.isSuccess() || downloadResponse.getFileData() == null)
        {
            return;
        }

        File stagedFile = new File(jarFile.getParentFile(), jarFile.getName() + ".new");
        try
        {
            FileOutputStream out = new FileOutputStream(stagedFile);
            try
            {
                out.write(downloadResponse.getFileData());
            }
            finally
            {
                out.close();
            }
        }
        catch (IOException e)
        {
            return;
        }

        NotificationCenter.add("Update Ready",
            "A new version of Vertex has been downloaded. Close and reopen the app to finish updating.");
    }

    /** The jar file this app was actually launched from, or null if it isn't running from a packaged jar at all (BlueJ/unpacked classes - nothing to update). */
    private static File locateOwnJar()
    {
        try
        {
            // Uses its own class, not Vertex.class - both live in the same jar
            // once packaged, so the location is identical either way, and this
            // way ClientUpdateChecker has no compile-time dependency on Vertex
            // itself (kept it out of VertexServer/ regardless - see below).
            CodeSource source = ClientUpdateChecker.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null)
            {
                return null;
            }
            File file = new File(source.getLocation().toURI());
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar"))
            {
                return null;
            }
            return file;
        }
        catch (URISyntaxException e)
        {
            return null;
        }
    }
}
