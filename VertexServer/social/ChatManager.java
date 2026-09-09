package social;
import net.NetworkConfig;
import net.Message;
import net.ClientHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatManager
{
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final List<ClientHandler> connectedClients = new ArrayList<ClientHandler>();
    private final Map<String, ClientHandler> byUsername = new HashMap<String, ClientHandler>();

    public synchronized void register(ClientHandler client, String username)
    {
        if (!connectedClients.contains(client)) connectedClients.add(client);
        if (username != null) byUsername.put(username.toLowerCase(), client);
    }

    public synchronized void unregister(ClientHandler client, String username)
    {
        connectedClients.remove(client);
        if (username != null) byUsername.remove(username.toLowerCase());
    }

    public synchronized ClientHandler findByUsername(String username)
    {
        if (username == null) return null;
        return byUsername.get(username.toLowerCase());
    }

    public synchronized List<String> getOnlineUsernames()
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < connectedClients.size(); i++)
        {
            String username = connectedClients.get(i).getLoggedInUsername();
            if (username != null)
            {
                result.add(username);
            }
        }
        return result;
    }

    public synchronized void broadcastToAll(Message message)
    {
        for (int i = 0; i < connectedClients.size(); i++)
        {
            connectedClients.get(i).sendMessage(message);
        }
    }

    public static String trimText(String text)
    {
        if (text == null)
        {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH)
        {
            trimmed = trimmed.substring(0, MAX_MESSAGE_LENGTH);
        }
        return trimmed;
    }

    public static byte[] validateFile(byte[] fileData)
    {
        if (fileData == null || fileData.length == 0)
        {
            return null;
        }
        if (fileData.length > NetworkConfig.MAX_FILE_SIZE_BYTES)
        {
            return null;
        }
        return fileData;
    }
}
