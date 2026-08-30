/**
 * NetworkConfig
 * -------------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. Central place for the server address/port so
 * neither project has hardcoded IPs scattered through it (Section 16).
 *
 * For LAN testing across multiple computers: change SERVER_HOST on the
 * CLIENT copy to the server computer's LAN IP address (e.g.
 * "192.168.1.42"). The server itself doesn't need to know its own IP -
 * it just listens on SERVER_PORT on all network interfaces.
 */
public class NetworkConfig
{
    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 7777;

    /** Chat file attachments - checked both client-side (before sending) and server-side (defense in depth). */
    public static final int MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;

    private NetworkConfig()
    {
        // Static constants holder - never instantiated.
    }
}
