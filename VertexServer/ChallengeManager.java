import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeManager
{
    private static class ProgressEntry
    {
        int progress;
        boolean completed;
        LocalDate periodStart;
    }

    private final Map<Integer, Map<String, ProgressEntry>> progressByAccount = new HashMap<Integer, Map<String, ProgressEntry>>();

    public synchronized List<ChallengeProgressInfo> getProgressFor(Account account)
    {
        List<ChallengeDefinition> defs = EconomyConfig.getChallengeDefinitions();
        List<ChallengeProgressInfo> result = new ArrayList<ChallengeProgressInfo>();

        for (int i = 0; i < defs.size(); i++)
        {
            ChallengeDefinition def = defs.get(i);
            ProgressEntry entry = getOrCreateEntry(account.getAccountId(), def);
            result.add(toInfo(def, entry));
        }
        return result;
    }

    public synchronized List<ChallengeProgressInfo> recordWin(Account account, String gameId)
    {
        List<ChallengeDefinition> defs = EconomyConfig.getChallengeDefinitions();
        List<ChallengeProgressInfo> changed = new ArrayList<ChallengeProgressInfo>();

        for (int i = 0; i < defs.size(); i++)
        {
            ChallengeDefinition def = defs.get(i);
            if (!def.appliesToWin(gameId)) continue;

            ProgressEntry entry = getOrCreateEntry(account.getAccountId(), def);
            if (entry.completed) continue;

            entry.progress++;
            if (entry.progress >= def.target)
            {
                entry.progress = def.target;
                entry.completed = true;
                account.setCoins(account.getCoins() + def.rewardCoins);
            }

            changed.add(toInfo(def, entry));
        }
        return changed;
    }

    private ChallengeProgressInfo toInfo(ChallengeDefinition def, ProgressEntry entry)
    {
        return new ChallengeProgressInfo(def.id, def.title, def.description,
            entry.progress, def.target, def.rewardCoins, entry.completed, def.resetPeriod.name());
    }

    private ProgressEntry getOrCreateEntry(int accountId, ChallengeDefinition def)
    {
        Map<String, ProgressEntry> forAccount = progressByAccount.get(accountId);
        if (forAccount == null)
        {
            forAccount = new HashMap<String, ProgressEntry>();
            progressByAccount.put(accountId, forAccount);
        }

        ProgressEntry entry = forAccount.get(def.id);
        LocalDate currentPeriodStart = def.resetPeriod.periodStartFor(LocalDate.now());

        if (entry == null || !currentPeriodStart.equals(entry.periodStart))
        {
            entry = new ProgressEntry();
            entry.progress = 0;
            entry.completed = false;
            entry.periodStart = currentPeriodStart;
            forAccount.put(def.id, entry);
        }

        return entry;
    }
}
