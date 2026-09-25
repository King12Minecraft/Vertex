import net.GameServer;

/**
 * ServerMain
 * ----------
 * Entry point for the Vertex server - headless, no GUI. Earlier
 * versions of this class also opened a full Swing login window in the
 * same process (a "combined server+client" convenience mode for local
 * testing); that's gone now that VertexServer is meant to run
 * unattended on a real machine (e.g. a cloud VM with no display),
 * where opening a Swing window would throw HeadlessException and
 * crash the whole process instead of just failing to show a window.
 * Anyone who wants the old combined behavior runs VertexServer.jar
 * and VertexClient.jar side by side instead - two processes, same
 * machine, same effect, and it no longer requires VertexServer to
 * carry a full copy of the client's UI code to do it.
 */
public class ServerMain
{
    public static void main(String[] args)
    {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
        {
            public void uncaughtException(Thread t, Throwable e)
            {
                System.err.println("Unexpected error on thread " + t.getName() + ":");
                e.printStackTrace();
            }
        });

        GameServer server = new GameServer();
        boolean started = server.start();

        if (!started)
        {
            System.err.println("Could not start the server - is the port already in use by another running server?");
            System.exit(1);
        }

        // GameServer's own accept-loop thread is a daemon thread (by design -
        // it shouldn't be what keeps the JVM alive when embedded in the
        // client's combined-mode window, where a Swing thread did that job).
        // Standalone here, nothing else is running, so main() has to block
        // forever itself or the JVM exits the instant this method returns.
        while (true)
        {
            try
            {
                Thread.sleep(Long.MAX_VALUE);
            }
            catch (InterruptedException ignored)
            {
                // Not interrupted by anything in normal operation - keep waiting.
            }
        }
    }
}
