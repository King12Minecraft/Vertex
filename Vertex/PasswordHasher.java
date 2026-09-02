import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher
{
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordHasher() { }

    public static String generateSalt()
    {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    public static String hash(String password, String salt)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = digest.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashedBytes);
        }
        catch (NoSuchAlgorithmException e) { throw new RuntimeException("Password hashing failed", e); }
        catch (UnsupportedEncodingException e) { throw new RuntimeException("Password hashing failed", e); }
    }

    public static boolean matches(String password, String salt, String expectedHash)
    {
        return hash(password, salt).equals(expectedHash);
    }
}
