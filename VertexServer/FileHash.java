import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * FileHash
 * --------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. One tiny static helper: SHA-256 of a byte array,
 * as a lowercase hex string. Used by the client auto-update check
 * (see ClientUpdateChecker and ClientUpdatePackage) so a client can
 * ask "does my jar already match the server's?" by sending a short
 * hash string instead of the whole file, and only actually transfer
 * the jar bytes when the answer is no.
 */
public class FileHash
{
    private FileHash() { }

    public static String sha256Hex(byte[] data)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            // SHA-256 is a required algorithm on every JDK - this never happens in practice.
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
