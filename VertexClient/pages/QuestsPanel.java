package pages;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import economy.ChallengeProgressInfo;
import ui.StatusPill;
import ui.ThemedLabel;
import ui.ThinProgressBar;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * QuestsPanel
 * -----------
 * The challenge system's page (player-facing name: "Quests"): a summary card (how many are done, how many coins
 * are still on the table) over one card per reset period - Daily, Weekly, One-time - each listing its quests with
 * a progress bar and the coin reward. Same server data as ever (CHALLENGES_REQUEST / CHALLENGE_UPDATE); the
 * Sidebar's compact list still uses QuestRow. Colours per period come from the theme (accent / success / second
 * gradient colour) so it follows whichever palette is active.
 */
public class QuestsPanel extends PageScaffold implements NetworkManager.PushListener
{
    private final JPanel content = new JPanel();

    public QuestsPanel()
    {
        super("QUESTS", "Finish quests to earn coins. Daily ones reset every day, weekly ones every week.");
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        row(content);
        showMessage("Loading quests...");

        NetworkManager.addPushListener(this);
        loadQuests();
    }

    private void showMessage(String text)
    {
        content.removeAll();
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_BODY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(label);
        content.revalidate();
        content.repaint();
    }

    private void loadQuests()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CHALLENGES_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            renderQuests(response.getChallenges());
                        }
                        else
                        {
                            showMessage("Couldn't load your quests - are you logged in and connected?");
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderQuests(List<ChallengeProgressInfo> quests)
    {
        content.removeAll();
        if (quests == null || quests.isEmpty())
        {
            showMessage("No quests available.");
            return;
        }

        int done = 0;
        int coinsLeft = 0;
        for (ChallengeProgressInfo q : quests)
        {
            if (q.isCompleted()) done++;
            else coinsLeft += q.getRewardCoins();
        }
        content.add(summaryCard(done, quests.size(), coinsLeft));

        String[] periods = { "DAILY", "WEEKLY", "NONE" };
        String[] titles = { "DAILY QUESTS", "WEEKLY QUESTS", "ONE-TIME QUESTS" };
        for (int p = 0; p < periods.length; p++)
        {
            List<ChallengeProgressInfo> group = new ArrayList<ChallengeProgressInfo>();
            for (ChallengeProgressInfo q : quests)
            {
                String period = q.getResetPeriod() == null ? "DAILY" : q.getResetPeriod();
                if (period.equals(periods[p])) group.add(q);
            }
            if (group.isEmpty()) continue;
            content.add(Box.createVerticalStrut(16));
            content.add(periodCard(titles[p], periods[p], group));
        }
        content.revalidate();
        content.repaint();
    }

    private JPanel summaryCard(int done, int total, int coinsLeft)
    {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel big = new ThemedLabel(done + " of " + total + " quests complete", ThemeColor.TEXT_PRIMARY);
        big.setFont(UITheme.FONT_HEADING.deriveFont(22f));
        big.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(big);
        box.add(Box.createVerticalStrut(10));

        ThinProgressBar bar = new ThinProgressBar(done, total).height(10);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(bar);
        box.add(Box.createVerticalStrut(12));

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setBorder(new EmptyBorder(0, -8, 0, 0));
        chips.add(new StatusPill(coinsLeft > 0 ? coinsLeft + " coins still to earn" : "Everything claimed",
            ThemeManager.getColor(coinsLeft > 0 ? ThemeColor.ACCENT : ThemeColor.SUCCESS)));
        box.add(chips);

        SectionCard card = new SectionCard("YOUR PROGRESS").withGlow();
        card.content(box);
        return fit(card);
    }

    private JPanel periodCard(String title, String period, List<ChallengeProgressInfo> group)
    {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (int i = 0; i < group.size(); i++)
        {
            list.add(questLine(group.get(i), colorFor(period)));
            if (i < group.size() - 1)
            {
                list.add(Box.createVerticalStrut(14));
            }
        }
        return fit(new SectionCard(title).content(list));
    }

    private JPanel questLine(ChallengeProgressInfo q, Color periodColor)
    {
        JPanel line = new JPanel(new BorderLayout(12, 6));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel title = new ThemedLabel(q.getTitle() + (q.isCompleted() ? "  ✓" : ""),
            q.isCompleted() ? ThemeColor.SUCCESS : ThemeColor.TEXT_PRIMARY);
        title.setFont(UITheme.FONT_NAV_BOLD);
        JLabel desc = new ThemedLabel(q.getDescription(), ThemeColor.TEXT_MUTED);
        desc.setFont(UITheme.FONT_SMALL);
        textCol.add(title);
        textCol.add(desc);
        line.add(textCol, BorderLayout.CENTER);

        JLabel reward = new ThemedLabel("+" + q.getRewardCoins() + " coins", q.isCompleted() ? ThemeColor.TEXT_MUTED : ThemeColor.ACCENT);
        reward.setFont(UITheme.FONT_SMALL);
        line.add(reward, BorderLayout.EAST);

        JPanel barRow = new JPanel(new BorderLayout(10, 0));
        barRow.setOpaque(false);
        ThinProgressBar bar = new ThinProgressBar(q.getProgress(), q.getTarget())
            .fill(q.isCompleted() ? ThemeManager.getColor(ThemeColor.SUCCESS) : periodColor);
        JPanel barHolder = new JPanel(new java.awt.GridBagLayout());
        barHolder.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        barHolder.add(bar, c);
        barRow.add(barHolder, BorderLayout.CENTER);
        JLabel count = new ThemedLabel(q.getProgress() + " / " + q.getTarget(), ThemeColor.TEXT_SECONDARY);
        count.setFont(UITheme.FONT_SMALL);
        count.setPreferredSize(new Dimension(64, 14));
        count.setHorizontalAlignment(JLabel.RIGHT);
        barRow.add(count, BorderLayout.EAST);
        line.add(barRow, BorderLayout.SOUTH);
        return line;
    }

    private static Color colorFor(String period)
    {
        if ("WEEKLY".equals(period)) return ThemeManager.getColor(ThemeColor.SUCCESS);
        if ("NONE".equals(period)) return ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
        return ThemeManager.getColor(ThemeColor.ACCENT);
    }

    /** A card that keeps its own height inside the page's column. */
    private static JPanel fit(SectionCard card)
    {
        return PageScaffold.fullWidth(card);
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.CHALLENGE_UPDATE)
        {
            return;
        }
        if (message.getChallenges() == null || message.getChallenges().isEmpty())
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { loadQuests(); }
        });
    }
}
