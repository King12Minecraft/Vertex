import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * AvatarStore
 * -----------
 * One PNG file per account (avatars/<username>.png), whether it came
 * from an upload or the in-app paint tool (see AvatarEditorDialog -
 * both produce the same 128x128 PNG bytes by the time they get here,
 * so the server doesn't need to know which one was used). Deliberately
 * simple - no thumbnails, no versioning, just "the current avatar for
 * this username," same one-file-per-thing approach CustomGameStore's
 * jar-per-game used before it was removed.
 */
public class AvatarStore
{
    private static final String AVATAR_DIR = "avatars";
    public static final int MAX_AVATAR_BYTES = 400 * 1024;

    public AvatarStore()
    {
        File dir = new File(AVATAR_DIR);
        if (!dir.exists())
        {
            dir.mkdirs();
        }
    }

    /** Returns false if pngBytes is null/empty or over the size cap - the client is expected to have already resized to 128x128 before sending, so a legitimate avatar should never come close to the limit. */
    public boolean save(String username, byte[] pngBytes)
    {
        if (pngBytes == null || pngBytes.length == 0 || pngBytes.length > MAX_AVATAR_BYTES)
        {
            return false;
        }
        try
        {
            FileOutputStream out = new FileOutputStream(fileFor(username));
            out.write(pngBytes);
            out.close();
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Could not save avatar for " + username + ": " + e.getMessage());
            return false;
        }
    }

    /** Null if this account has never set an avatar. */
    public byte[] load(String username)
    {
        File file = fileFor(username);
        if (!file.exists())
        {
            return null;
        }
        try
        {
            FileInputStream in = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length)
            {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            in.close();
            return bytes;
        }
        catch (IOException e)
        {
            System.err.println("Could not load avatar for " + username + ": " + e.getMessage());
            return null;
        }
    }

    private File fileFor(String username)
    {
        // Usernames are already validated at account-creation time (alphanumeric-ish),
        // but normalize to lowercase so the filename can't collide/differ only by case.
        return new File(AVATAR_DIR, username.toLowerCase() + ".png");
    }
}
