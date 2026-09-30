package support;

import net.ClientHandler;
import net.Message;
import net.MessageType;

import java.util.ArrayList;
import java.util.List;

/**
 * FakeClientHandler
 * ------------------
 * A ClientHandler with no socket, no managers and a fixed identity, that records
 * every message sent to it - for tests that need real handler objects (queueing,
 * rooms, matches) without a network. ClientHandler's constructor takes ~48 manager
 * arguments, all null here; only getLoggedInUsername/getAccountId/sendMessage are
 * overridden, so anything that touches a real manager would NPE (by design - a test
 * that needs one should say so).
 */
public class FakeClientHandler extends ClientHandler
{
    public final String username;
    public final Integer accountId;
    public final List<Message> sent = new ArrayList<Message>();

    public FakeClientHandler(String username, Integer accountId)
    {
        super(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
        this.username = username;
        this.accountId = accountId;
    }

    @Override
    public String getLoggedInUsername() { return username; }

    @Override
    public Integer getAccountId() { return accountId; }

    @Override
    public void sendMessage(Message message) { sent.add(message); }

    public Message lastOfType(MessageType type)
    {
        for (int i = sent.size() - 1; i >= 0; i--)
        {
            if (sent.get(i).getType() == type) return sent.get(i);
        }
        return null;
    }

    public int countOfType(MessageType type)
    {
        int count = 0;
        for (int i = 0; i < sent.size(); i++)
        {
            if (sent.get(i).getType() == type) count++;
        }
        return count;
    }
}
