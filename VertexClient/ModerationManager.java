import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ModerationManager
 * -----------------
 * Real chat moderation (mute, kick, ban) and the player report queue -
 * the "Chat Moderation (Phase 9)" and report-queue (Phase 14) items
 * that were flagged as outstanding. The message protocol for this
 * (MOD_* / REPORT_* types, muteDurationMinutes/reportReason/
 * reportDescriptions fields on Message) already existed from earlier
 * planning - this is what actually implements it.
 *
 * Mutes are in-memory only and time-limited (they're meant to be
 * short, temporary cooldowns - no need to survive a server restart).
 * Bans and reports are persisted, keyed on username (case-insensitive)
 * rather than account ID - unlike Friends, a ban/report needs to catch
 * someone even if they immediately rename to dodge it, and usernames
 * are what a moderator actually has in front of them when acting.
 */
public class ModerationManager
{
    private static final String BANS_FILE = "gamehub_bans.dat";
    private static final String REPORTS_FILE = "gamehub_reports.dat";

    private static class Report
    {
        String id;
        String reporterUsername;
        String reportedUsername;
        String reason;
        long timestamp;
        boolean resolved;
    }

    private final Map<String, Long> mutedUntil = new HashMap<String, Long>();
    private final Set<String> bannedUsernames = new HashSet<String>();
    private final List<Report> reports = new ArrayList<Report>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a");

    public ModerationManager()
    {
        loadBans();
        loadReports();
    }

    // ---- Mute (in-memory, time-limited) ----

    public synchronized void mute(String username, int minutes)
    {
        long expiry = System.currentTimeMillis() + Math.max(1, minutes) * 60000L;
        mutedUntil.put(username.toLowerCase(), expiry);
    }

    public synchronized void unmute(String username)
    {
        mutedUntil.remove(username.toLowerCase());
    }

    public synchronized boolean isMuted(String username)
    {
        Long expiry = mutedUntil.get(username.toLowerCase());
        if (expiry == null)
        {
            return false;
        }
        if (System.currentTimeMillis() > expiry)
        {
            mutedUntil.remove(username.toLowerCase());
            return false;
        }
        return true;
    }

    // ---- Ban (persisted) ----

    public synchronized void ban(String username)
    {
        bannedUsernames.add(username.toLowerCase());
        saveBans();
    }

    public synchronized void unban(String username)
    {
        bannedUsernames.remove(username.toLowerCase());
        saveBans();
    }

    public synchronized boolean isBanned(String username)
    {
        return bannedUsernames.contains(username.toLowerCase());
    }

    // ---- Reports (persisted) ----

    public synchronized void submitReport(String reporterUsername, String reportedUsername, String reason)
    {
        Report report = new Report();
        report.id = "report-" + System.currentTimeMillis() + "-" + reports.size();
        report.reporterUsername = reporterUsername;
        report.reportedUsername = reportedUsername;
        report.reason = (reason == null || reason.trim().isEmpty()) ? "(no reason given)" : reason.trim();
        report.timestamp = System.currentTimeMillis();
        report.resolved = false;
        reports.add(report);
        saveReports();
    }

    /** Pre-formatted as "reportId::displayText" - the moderator UI splits on "::" to keep the ID for resolving without showing it. */
    public synchronized List<String> getUnresolvedReportDescriptions()
    {
        List<String> result = new ArrayList<String>();
        for (int i = reports.size() - 1; i >= 0; i--)
        {
            Report report = reports.get(i);
            if (!report.resolved)
            {
                String display = dateFormat.format(new Date(report.timestamp)) + " - "
                    + report.reportedUsername + " reported by " + report.reporterUsername + ": " + report.reason;
                result.add(report.id + "::" + display);
            }
        }
        return result;
    }

    public synchronized boolean resolveReport(String reportId)
    {
        for (int i = 0; i < reports.size(); i++)
        {
            if (reports.get(i).id.equals(reportId))
            {
                reports.get(i).resolved = true;
                saveReports();
                return true;
            }
        }
        return false;
    }

    private void loadBans()
    {
        File file = new File(BANS_FILE);
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
                    bannedUsernames.add(line.trim().toLowerCase());
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load bans: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private void saveBans()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(BANS_FILE));
            for (String username : bannedUsernames)
            {
                writer.println(username);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save bans: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }

    private void loadReports()
    {
        File file = new File(REPORTS_FILE);
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
                String[] parts = line.split("\\|", 6);
                if (parts.length < 6)
                {
                    continue;
                }
                Report report = new Report();
                report.id = parts[0];
                report.reporterUsername = parts[1];
                report.reportedUsername = parts[2];
                report.timestamp = Long.parseLong(parts[3]);
                report.resolved = Boolean.parseBoolean(parts[4]);
                report.reason = parts[5];
                reports.add(report);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load reports: " + e.getMessage());
        }
        finally
        {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) { } }
        }
    }

    private void saveReports()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(REPORTS_FILE));
            for (int i = 0; i < reports.size(); i++)
            {
                Report report = reports.get(i);
                writer.println(report.id + "|" + report.reporterUsername + "|" + report.reportedUsername
                    + "|" + report.timestamp + "|" + report.resolved + "|" + report.reason);
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not save reports: " + e.getMessage());
        }
        finally
        {
            if (writer != null) writer.close();
        }
    }
}
