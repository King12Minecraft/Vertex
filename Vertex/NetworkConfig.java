/**
 * NetworkConfig
 * -------------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. Central place for the server address/port so
 * neither project has hardcoded IPs scattered through it.
 *
 * Now mutable rather than fixed constants: the client asks for a
 * server address before connecting (see ConnectDialog), and the
 * server asks for a port to listen on before starting (see
 * ServerMain) - both call the setters here before anything touches
 * the network, rather than requiring a source-code edit and rebuild
 * just to point at a different server/port.
 */
public class NetworkConfig
{
    private static String serverHost = "localhost";
    private static int serverPort = 7777;

    public static String getServerHost() { return serverHost; }
    public static void setServerHost(String host) { serverHost = host; }

    public static int getServerPort() { return serverPort; }
    public static void setServerPort(int port) { serverPort = port; }

    /** Chat file attachments - checked both client-side (before sending) and server-side (defense in depth). */
    public static final int MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;

    /**
     * Temporary kill switch for the satellite-server system (hosting as a
     * satellite, satellite registration/sync, and the admin "Servers" page).
     * Flip back to true to re-enable - nothing else needs to change, every
     * checkpoint below reads this same flag.
     */
    public static final boolean SATELLITE_SERVERS_ENABLED = false;

    private NetworkConfig()
    {
        // Static constants holder - never instantiated.
    }
}
