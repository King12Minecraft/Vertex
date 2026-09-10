package social;

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
    private final Map<String, BanRecord> bans = new HashMap<String, BanRecord>();

    /** A single ban's details, kept for admins to review (who was banned, why, by whom, when) rather than just a bare yes/no. */
    public static class BanRecord
    {
        public final String username;
        public final String reason;
        public final String bannedBy;
        public final long bannedAtMillis;

        public BanRecord(String username, String reason, String bannedBy, long bannedAtMillis)
        {
            this.username = username;
            this.reason = reason;
            this.bannedBy = bannedBy;
            this.bannedAtMillis = bannedAtMillis;
        }
    }
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

    public synchronized void ban(String username, String reason, String bannedBy)
    {
        bans.put(username.toLowerCase(), new BanRecord(username, reason, bannedBy, System.currentTimeMillis()));
        saveBans();
    }

    public synchronized void unban(String username)
    {
        bans.remove(username.toLowerCase());
        saveBans();
    }

    public synchronized boolean isBanned(String username)
    {
        return bans.containsKey(username.toLowerCase());
    }

    /** Newest first, for the admin ban list. */
    public synchronized List<BanRecord> getBans()
    {
        List<BanRecord> list = new ArrayList<BanRecord>(bans.values());
        java.util.Collections.sort(list, new java.util.Comparator<BanRecord>()
        {
            public int compare(BanRecord a, BanRecord b) { return Long.compare(b.bannedAtMillis, a.bannedAtMillis); }
        });
        return list;
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
                line = line.trim();
                if (line.isEmpty()) continue;

                // Pipe-delimited "username|reason|bannedBy|timestamp" going forward - but a ban
                // file from before reasons existed just has one bare username per line, so that
                // still loads correctly (as a ban with an empty reason) rather than breaking.
                String[] parts = line.split("\\|", -1);
                String username = parts[0];
                String reason = parts.length > 1 ? parts[1] : "";
                String bannedBy = parts.length > 2 ? parts[2] : "";
                long timestamp = parts.length > 3 ? parseLongSafe(parts[3]) : System.currentTimeMillis();
                bans.put(username.toLowerCase(), new BanRecord(username, reason, bannedBy, timestamp));
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

    private long parseLongSafe(String text)
    {
        try { return Long.parseLong(text); } catch (NumberFormatException e) { return System.currentTimeMillis(); }
    }

    private void saveBans()
    {
        PrintWriter writer = null;
        try
        {
            writer = new PrintWriter(new FileWriter(BANS_FILE));
            for (BanRecord ban : bans.values())
            {
                writer.println(ban.username + "|" + ban.reason.replace("|", " ").replace("\n", " ")
                    + "|" + ban.bannedBy + "|" + ban.bannedAtMillis);
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
