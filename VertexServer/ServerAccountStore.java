import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerAccountStore
{
    private static final String STORE_FILE = "gamehub_server_accounts.dat";
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private final List<Account> accounts = new ArrayList<Account>();
    private int nextAccountId = 1;

    private final Map<String, Integer> failedAttempts = new HashMap<String, Integer>();
    private final Map<String, Boolean> lockedOut = new HashMap<String, Boolean>();

    public enum LoginResult { SUCCESS, WRONG_PASSWORD, NO_SUCH_ACCOUNT, LOCKED_OUT }
    public enum ChangeResult { SUCCESS, WRONG_PASSWORD, NO_SUCH_ACCOUNT, USERNAME_TAKEN, USERNAME_TOO_SHORT, PASSWORD_TOO_SHORT }

    public ServerAccountStore()
    {
        load();
    }

    private void load()
    {
        File file = new File(STORE_FILE);
        if (!file.exists()) return;

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) continue;
                int id = Integer.parseInt(parts[0]);
                String username = parts[1];
                String hash = parts[2];
                String salt = parts[3];
                Role role = Role.valueOf(parts[4]);
                String color = parts[5];

                Account account = new Account(id, username, hash, salt, role);
                account.setPlayerColorName(color);

                if (parts.length >= 7 && !parts[6].isEmpty())
                {
                    account.setCoins(Integer.parseInt(parts[6]));
                }
                if (parts.length >= 8 && !parts[7].isEmpty())
                {
                    String[] itemIds = parts[7].split(",");
                    for (int j = 0; j < itemIds.length; j++)
                    {
                        if (!itemIds[j].isEmpty()) account.getOwnedItemIds().add(itemIds[j]);
                    }
                }
                if (parts.length >= 9 && !parts[8].isEmpty())
                {
                    account.setLastLoginDate(parts[8]);
                }
                if (parts.length >= 10 && !parts[9].isEmpty())
                {
                    account.setLoginStreak(Integer.parseInt(parts[9]));
                }
                if (parts.length >= 11 && !parts[10].isEmpty())
                {
                    account.setEquippedBadgeId(parts[10]);
                }

                accounts.add(account);
                if (id >= nextAccountId) nextAccountId = id + 1;
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load server accounts: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private synchronized void save()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(STORE_FILE));
            for (int i = 0; i < accounts.size(); i++)
            {
                Account a = accounts.get(i);
                writer.println(a.getAccountId() + "|" + a.getUsername() + "|" + a.getPasswordHash()
                    + "|" + a.getPasswordSalt() + "|" + a.getRole().name() + "|" + a.getPlayerColorName()
                    + "|" + a.getCoins() + "|" + joinItems(a.getOwnedItemIds())
                    + "|" + a.getLastLoginDate() + "|" + a.getLoginStreak() + "|" + a.getEquippedBadgeId());
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save server accounts: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private String joinItems(List<String> itemIds)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < itemIds.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append(itemIds.get(i));
        }
        return sb.toString();
    }

    public synchronized boolean usernameExists(String username)
    {
        for (int i = 0; i < accounts.size(); i++)
        {
            if (accounts.get(i).getUsername().equalsIgnoreCase(username)) return true;
        }
        return false;
    }

    public synchronized Account findByUsername(String username)
    {
        for (int i = 0; i < accounts.size(); i++)
        {
            if (accounts.get(i).getUsername().equalsIgnoreCase(username)) return accounts.get(i);
        }
        return null;
    }

    public synchronized Account findById(int accountId)
    {
        for (int i = 0; i < accounts.size(); i++)
        {
            if (accounts.get(i).getAccountId() == accountId) return accounts.get(i);
        }
        return null;
    }

    public synchronized boolean hasAnyAccounts()
    {
        return !accounts.isEmpty();
    }

    public synchronized boolean hasAdminAccount()
    {
        for (int i = 0; i < accounts.size(); i++)
        {
            if (accounts.get(i).getRole() == Role.ADMIN) return true;
        }
        return false;
    }

    public synchronized List<String> getAllUsernames()
    {
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < accounts.size(); i++)
        {
            result.add(accounts.get(i).getUsername());
        }
        return result;
    }

    public synchronized Account createAccount(String username, String password, Role role)
    {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(password, salt);
        Account account = new Account(nextAccountId, username, hash, salt, role);
        nextAccountId++;
        accounts.add(account);
        save();
        return account;
    }

    public synchronized void updateAccount(Account account)
    {
        save();
    }

    public synchronized LoginResult attemptLogin(String username, String password)
    {
        String key = username.toLowerCase();
        if (Boolean.TRUE.equals(lockedOut.get(key))) return LoginResult.LOCKED_OUT;

        Account account = findByUsername(username);
        if (account == null) return LoginResult.NO_SUCH_ACCOUNT;

        boolean matches = PasswordHasher.matches(password, account.getPasswordSalt(), account.getPasswordHash());
        if (matches)
        {
            failedAttempts.remove(key);
            return LoginResult.SUCCESS;
        }

        int attempts = failedAttempts.containsKey(key) ? failedAttempts.get(key) + 1 : 1;
        failedAttempts.put(key, attempts);
        if (attempts >= MAX_LOGIN_ATTEMPTS)
        {
            lockedOut.put(key, true);
            return LoginResult.LOCKED_OUT;
        }
        return LoginResult.WRONG_PASSWORD;
    }

    public synchronized ChangeResult changeUsername(String currentUsername, String currentPassword, String newUsername)
    {
        Account account = findByUsername(currentUsername);
        if (account == null) return ChangeResult.NO_SUCH_ACCOUNT;
        if (!PasswordHasher.matches(currentPassword, account.getPasswordSalt(), account.getPasswordHash()))
        {
            return ChangeResult.WRONG_PASSWORD;
        }
        if (newUsername == null || newUsername.length() < 3) return ChangeResult.USERNAME_TOO_SHORT;
        if (!newUsername.equalsIgnoreCase(currentUsername) && usernameExists(newUsername))
        {
            return ChangeResult.USERNAME_TAKEN;
        }
        account.setUsername(newUsername);
        save();
        return ChangeResult.SUCCESS;
    }

    public synchronized ChangeResult changePassword(String username, String currentPassword, String newPassword)
    {
        Account account = findByUsername(username);
        if (account == null) return ChangeResult.NO_SUCH_ACCOUNT;
        if (!PasswordHasher.matches(currentPassword, account.getPasswordSalt(), account.getPasswordHash()))
        {
            return ChangeResult.WRONG_PASSWORD;
        }
        if (newPassword == null || newPassword.length() < 6) return ChangeResult.PASSWORD_TOO_SHORT;

        String newSalt = PasswordHasher.generateSalt();
        String newHash = PasswordHasher.hash(newPassword, newSalt);
        account.setPassword(newHash, newSalt);
        save();
        return ChangeResult.SUCCESS;
    }
}
