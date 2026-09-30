package net;

import support.Check;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ResponseTypesTest
 * ------------------
 * Guards a bug class that has now bitten four times: a request the client makes with a blocking
 * NetworkManager.send() only gets its answer if the reply's type is listed in
 * NetworkManager.RESPONSE_TYPES - otherwise the reply is delivered as a push, send() waits out its
 * full 10 seconds, and (because send() holds a global lock while it waits) every other request in
 * the app stalls behind it. It happened to the Forums, the Dominion page (which quietly delayed
 * every other panel's first load by 10s on every start), equipping a frame, and would have to
 * Stats. This reads the server's own source for every "..._RESPONSE" type it sets and requires each
 * to be registered, except the two deliberately left out (broadcast-and-request types - see the
 * note above RESPONSE_TYPES).
 */
public class ResponseTypesTest
{
    /** Sent both as a direct reply AND as an unsolicited broadcast, so deliberately routed as pushes (see NetworkManager's note). */
    private static final Set<String> INTENTIONALLY_PUSH = new HashSet<String>(Arrays.asList(
        "TOURNAMENT_LIST_RESPONSE", "TEAM_TOURNAMENT_LIST_RESPONSE"));

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        String root = System.getenv("VERTEX_REPO_ROOT");
        check.check("test.sh provides VERTEX_REPO_ROOT", root != null);
        if (root == null)
        {
            check.finish();
            return;
        }

        String source = read(root + "/VertexServer/net/ClientHandler.java");
        Set<String> sent = new TreeSet<String>();
        Matcher m = Pattern.compile("setType\\(MessageType\\.(\\w+_RESPONSE)\\)").matcher(source);
        while (m.find())
        {
            sent.add(m.group(1));
        }
        check.check("found the server's response types (" + sent.size() + ")", sent.size() > 40);

        Field f = NetworkManager.class.getDeclaredField("RESPONSE_TYPES");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<MessageType> registered = (Set<MessageType>) f.get(null);

        for (String name : sent)
        {
            if (INTENTIONALLY_PUSH.contains(name))
            {
                check.check(name + " is deliberately NOT registered", !registered.contains(MessageType.valueOf(name)));
            }
            else
            {
                check.check(name + " is in NetworkManager.RESPONSE_TYPES", registered.contains(MessageType.valueOf(name)));
            }
        }

        check.finish();
    }

    private static String read(String path) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
