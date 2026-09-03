import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * ClientUpdatePackage
 * --------------------
 * Server-only. Backs the client auto-update feature: whatever
 * "Vertex.jar" happens to be sitting in this server's own working
 * directory (right next to VertexServer.jar/VertexServer.exe) is
 * treated as "the latest client" - see ClientHandler's
 * CLIENT_VERSION_CHECK_REQUEST/CLIENT_UPDATE_DOWNLOAD_REQUEST
 * handlers, and ClientUpdateChecker on the client side.
 *
 * To push a fix or a new game out to everyone playing on this server,
 * just drop a freshly-built Vertex.jar into this folder, overwriting
 * the old one - no server restart needed. This class re-reads and
 * re-hashes the file whenever its last-modified time changes, so a
 * hot-swapped jar is picked up on the very next request that asks.
 *
 * If no Vertex.jar is present here at all, getCurrentHash()/
 * getCurrentBytes() just return null - clients are told "up to date"
 * rather than the check breaking for anyone. Auto-update is opt-in
 * by simply choosing to keep a Vertex.jar next to the server or not.
 */
public class ClientUpdatePackage
{
    private static final String JAR_FILE = "Vertex.jar";

    private static long cachedMtime = -1;
    private static byte[] cachedBytes;
    private static String cachedHash;

    private ClientUpdatePackage() { }

    private static synchronized void refreshIfNeeded()
    {
        File file = new File(JAR_FILE);
        if (!file.exists())
        {
            cachedBytes = null;
            cachedHash = null;
            cachedMtime = -1;
            return;
        }

        long mtime = file.lastModified();
        if (mtime == cachedMtime && cachedBytes != null)
        {
            return;
        }

        try
        {
            byte[] bytes = Files.readAllBytes(file.toPath());
            cachedBytes = bytes;
            cachedHash = FileHash.sha256Hex(bytes);
            cachedMtime = mtime;
        }
        catch (IOException e)
        {
            System.err.println("Could not read " + JAR_FILE + " for client auto-update: " + e.getMessage());
        }
    }

    /** Null if no Vertex.jar sits next to this server - auto-update serving simply isn't set up. */
    public static synchronized String getCurrentHash()
    {
        refreshIfNeeded();
        return cachedHash;
    }

    /** Null under the same condition as getCurrentHash(). */
    public static synchronized byte[] getCurrentBytes()
    {
        refreshIfNeeded();
        return cachedBytes;
    }
}
