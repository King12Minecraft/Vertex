import java.io.Serializable;

/**
 * ChallengeProgressInfo
 * ----------------------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. One challenge's definition plus this account's
 * live progress toward it, as sent to the client.
 */
public class ChallengeProgressInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String title;
    private final String description;
    private final int progress;
    private final int target;
    private final int rewardCoins;
    private final boolean completed;
    private final String resetPeriod;

    public ChallengeProgressInfo(String id, String title, String description, int progress,
                                  int target, int rewardCoins, boolean completed, String resetPeriod)
    {
        this.id = id;
        this.title = title;
        this.description = description;
        this.progress = progress;
        this.target = target;
        this.rewardCoins = rewardCoins;
        this.completed = completed;
        this.resetPeriod = resetPeriod;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getProgress() { return progress; }
    public int getTarget() { return target; }
    public int getRewardCoins() { return rewardCoins; }
    public boolean isCompleted() { return completed; }
    public String getResetPeriod() { return resetPeriod; }
}
