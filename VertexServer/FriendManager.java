import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendManager
{
    private static final String FRIENDS_FILE = "gamehub_friends.dat";
    private static final String REQUESTS_FILE = "gamehub_friend_requests.dat";

    public enum SendResult { SENT, ALREADY_FRIENDS, ALREADY_PENDING, SELF, AUTO_ACCEPTED, NO_SUCH_USER }

    private final Set<String> friendPairs = new HashSet<String>();
    private final List<int[]> pendingRequests = new ArrayList<int[]>();

    private final ServerAccountStore accountStore;
    private final ChatManager chatManager;

    public FriendManager(ServerAccountStore accountStore, ChatManager chatManager)
    {
        this.accountStore = accountStore;
        this.chatManager = chatManager;
        loadFriends();
        loadRequests();
    }

    private String pairKey(int a, int b)
    {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return lo + ":" + hi;
    }

    public synchronized boolean areFriends(int a, int b)
    {
        return friendPairs.contains(pairKey(a, b));
    }

    private boolean hasPending(int requesterId, int targetId)
    {
        for (int i = 0; i < pendingRequests.size(); i++)
        {
            int[] pair = pendingRequests.get(i);
            if (pair[0] == requesterId && pair[1] == targetId) return true;
        }
        return false;
    }

    private void removePending(int requesterId, int targetId)
    {
        for (int i = 0; i < pendingRequests.size(); i++)
        {
            int[] pair = pendingRequests.get(i);
            if (pair[0] == requesterId && pair[1] == targetId)
            {
                pendingRequests.remove(i);
                return;
            }
        }
    }

    public synchronized SendResult sendRequest(String requesterUsername, String targetUsername)
    {
        Account requesterAccount = accountStore.findByUsername(requesterUsername);
        Account targetAccount = accountStore.findByUsername(targetUsername);
        if (requesterAccount == null || targetAccount == null)
        {
            return SendResult.NO_SUCH_USER;
        }

        int requesterId = requesterAccount.getAccountId();
        int targetId = targetAccount.getAccountId();

        if (requesterId == targetId)
        {
            return SendResult.SELF;
        }
        if (areFriends(requesterId, targetId))
        {
            return SendResult.ALREADY_FRIENDS;
        }

        if (hasPending(targetId, requesterId))
        {
            removePending(targetId, requesterId);
            friendPairs.add(pairKey(requesterId, targetId));
            saveFriends();
            saveRequests();
            notifyBothSidesAccepted(requesterAccount, targetAccount);
            return SendResult.AUTO_ACCEPTED;
        }

        if (hasPending(requesterId, targetId))
        {
            return SendResult.ALREADY_PENDING;
        }

        pendingRequests.add(new int[] { requesterId, targetId });
        saveRequests();

        ClientHandler targetHandler = chatManager.findByUsername(targetUsername);
        if (targetHandler != null)
        {
            Message notice = new Message();
            notice.setType(MessageType.FRIEND_REQUEST_RECEIVED);
            notice.setUsername(requesterAccount.getUsername());
            targetHandler.sendMessage(notice);
        }

        return SendResult.SENT;
    }

    public synchronized boolean acceptRequest(String accepterUsername, String requesterUsername)
    {
        Account accepterAccount = accountStore.findByUsername(accepterUsername);
        Account requesterAccount = accountStore.findByUsername(requesterUsername);
        if (accepterAccount == null || requesterAccount == null)
        {
            return false;
        }

        int accepterId = accepterAccount.getAccountId();
        int requesterId = requesterAccount.getAccountId();

        if (!hasPending(requesterId, accepterId))
        {
            return false;
        }

        removePending(requesterId, accepterId);
        friendPairs.add(pairKey(requesterId, accepterId));
        saveFriends();
        saveRequests();

        notifyBothSidesAccepted(requesterAccount, accepterAccount);
        return true;
    }

    public synchronized boolean declineRequest(String declinerUsername, String requesterUsername)
    {
        Account declinerAccount = accountStore.findByUsername(declinerUsername);
        Account requesterAccount = accountStore.findByUsername(requesterUsername);
        if (declinerAccount == null || requesterAccount == null)
        {
            return false;
        }

        removePending(requesterAccount.getAccountId(), declinerAccount.getAccountId());
        saveRequests();
        return true;
    }

    private void notifyBothSidesAccepted(Account requesterAccount, Account accepterAccount)
    {
        ClientHandler requesterHandler = chatManager.findByUsername(requesterAccount.getUsername());
        if (requesterHandler != null)
        {
            Message notice = new Message();
            notice.setType(MessageType.FRIEND_ACCEPTED_NOTICE);
            notice.setUsername(accepterAccount.getUsername());
            requesterHandler.sendMessage(notice);
        }

        ClientHandler accepterHandler = chatManager.findByUsername(accepterAccount.getUsername());
        if (accepterHandler != null)
        {
            Message notice = new Message();
            notice.setType(MessageType.FRIEND_ACCEPTED_NOTICE);
            notice.setUsername(requesterAccount.getUsername());
            accepterHandler.sendMessage(notice);
        }
    }

    public synchronized void broadcastPresenceChange(Account account, boolean nowOnline)
    {
        int accountId = account.getAccountId();
        List<String> allUsernames = accountStore.getAllUsernames();

        for (int i = 0; i < allUsernames.size(); i++)
        {
            String otherUsername = allUsernames.get(i);
            Account otherAccount = accountStore.findByUsername(otherUsername);
            if (otherAccount == null || otherAccount.getAccountId() == accountId)
            {
                continue;
            }
            if (!areFriends(accountId, otherAccount.getAccountId()))
            {
                continue;
            }

            ClientHandler otherHandler = chatManager.findByUsername(otherUsername);
            if (otherHandler != null)
            {
                Message notice = new Message();
                notice.setType(MessageType.FRIEND_STATUS_UPDATE);
                notice.setUsername(account.getUsername());
                notice.setOnline(nowOnline);
                otherHandler.sendMessage(notice);
            }
        }
    }

    public synchronized List<String> getFriendUsernames(int accountId)
    {
        List<String> result = new ArrayList<String>();
        List<String> allUsernames = accountStore.getAllUsernames();
        for (int i = 0; i < allUsernames.size(); i++)
        {
            Account other = accountStore.findByUsername(allUsernames.get(i));
            if (other != null && other.getAccountId() != accountId && areFriends(accountId, other.getAccountId()))
            {
                result.add(other.getUsername());
            }
        }
        return result;
    }

    public synchronized List<String> getPendingIncomingUsernames(int accountId)
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < pendingRequests.size(); i++)
        {
            int[] pair = pendingRequests.get(i);
            if (pair[1] == accountId)
            {
                Account requester = findById(pair[0]);
                if (requester != null)
                {
                    result.add(requester.getUsername());
                }
            }
        }
        return result;
    }

    private Account findById(int accountId)
    {
        List<String> allUsernames = accountStore.getAllUsernames();
        for (int i = 0; i < allUsernames.size(); i++)
        {
            Account account = accountStore.findByUsername(allUsernames.get(i));
            if (account != null && account.getAccountId() == accountId)
            {
                return account;
            }
        }
        return null;
    }

    private void loadFriends()
    {
        File file = new File(FRIENDS_FILE);
        if (!file.exists())
        {
            return;
        }
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.trim().isEmpty())
                {
                    friendPairs.add(line.trim());
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load friends: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private void saveFriends()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(FRIENDS_FILE));
            for (String pair : friendPairs)
            {
                writer.println(pair);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save friends: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private void loadRequests()
    {
        File file = new File(REQUESTS_FILE);
        if (!file.exists())
        {
            return;
        }
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|");
                if (parts.length < 2)
                {
                    continue;
                }
                pendingRequests.add(new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) });
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load friend requests: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private void saveRequests()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(REQUESTS_FILE));
            for (int i = 0; i < pendingRequests.size(); i++)
            {
                int[] pair = pendingRequests.get(i);
                writer.println(pair[0] + "|" + pair[1]);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save friend requests: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }
}
