package pages;
import economy.AchievementDefinitions;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import ui.ThemedScrollBarUI;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.ThemedLabel;
import ui.ThinProgressBar;
import javax.swing.JComponent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AchievementsPanel
 * -----------------
 * Every achievement, unlocked ones visually distinct from locked ones.
 * Fetches the unlocked set via ACHIEVEMENTS_REQUEST; the full list of
 * what achievements exist comes from AchievementDefinitions (a
 * client-side mirror of the server's list, since the server only ever
 * sends which IDs are unlocked, not the full definitions).
 *
 * Fetched with a blocking NetworkManager.send() on a background thread
 * rather than sendAsync()+PushListener - ACHIEVEMENTS_RESPONSE is only
 * ever sent by the server as a direct reply to ACHIEVEMENTS_REQUEST,
 * never as an unprompted push, so there's nothing to listen for here.
 * (An earlier version of this panel did use sendAsync()+onPush, which
 * was the actual bug: NetworkManager's pendingResponses queue has no
 * per-request correlation, so an ACHIEVEMENTS_RESPONSE that nobody's
 * blocking send() was waiting for would sit in that queue and get
 * handed to whichever *unrelated* blocking call happened to poll()
 * next - misrouting a totally different request's real response one
 * slot further down the line, and so on for every call after it. That
 * surfaced as things like the Games screen crashing with a
 * NullPointerException right after login.)
 */
public class AchievementsPanel extends PageScaffold
{
    private final JPanel list = new JPanel();

    public AchievementsPanel()
    {
        super("ACHIEVEMENTS", "Unlock them by playing - locked ones show how far along you are.");
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        row(list);

        renderLocked(new HashSet<String>(), new java.util.HashMap<String, Integer>());
        fetchInBackground();
    }

    private void fetchInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ACHIEVEMENTS_REQUEST);
                final Message response = NetworkManager.send(request);

                javax.swing.SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null || !response.isSuccess())
                        {
                            return;
                        }
                        List<String> unlockedList = response.getUnlockedAchievementIds();
                        renderLocked(unlockedList == null ? new HashSet<String>() : new HashSet<String>(unlockedList),
                            parseMetrics(response.getAchievementMetrics()));
                    }
                });
            }
        });
        worker.start();
    }

    /** "wins:chess:3" -> {"wins:chess": 3}; "coins:450" -> {"coins": 450} - the metric key is everything before the final colon, matching how Definition.metricKey is written (see its own javadoc). */
    private java.util.Map<String, Integer> parseMetrics(List<String> raw)
    {
        java.util.Map<String, Integer> map = new java.util.HashMap<String, Integer>();
        if (raw == null) return map;
        for (int i = 0; i < raw.size(); i++)
        {
            String entry = raw.get(i);
            int lastColon = entry.lastIndexOf(':');
            if (lastColon < 0) continue;
            try
            {
                map.put(entry.substring(0, lastColon), Integer.parseInt(entry.substring(lastColon + 1)));
            }
            catch (NumberFormatException ignored) { }
        }
        return map;
    }

    private void renderLocked(Set<String> unlockedIds, java.util.Map<String, Integer> metrics)
    {
        list.removeAll();

        List<AchievementDefinitions.Definition> all = AchievementDefinitions.getAll();
        int unlockedCount = 0;
        for (int i = 0; i < all.size(); i++)
        {
            if (unlockedIds.contains(all.get(i).id)) unlockedCount++;
        }

        JPanel summary = new JPanel();
        summary.setOpaque(false);
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        JLabel big = new ThemedLabel(unlockedCount + " of " + all.size() + " unlocked", ThemeColor.TEXT_PRIMARY);
        big.setFont(UITheme.FONT_HEADING.deriveFont(22f));
        big.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(big);
        summary.add(Box.createVerticalStrut(10));
        ThinProgressBar bar = new ThinProgressBar(unlockedCount, Math.max(all.size(), 1)).height(10);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(bar);
        list.add(PageScaffold.fullWidth(new SectionCard("YOUR COLLECTION").withGlow().content(summary)));
        list.add(Box.createVerticalStrut(16));

        // unlocked first, then the rest in definition order
        java.util.List<JComponent> tiles = new java.util.ArrayList<JComponent>();
        for (int pass = 0; pass < 2; pass++)
        {
            for (int i = 0; i < all.size(); i++)
            {
                AchievementDefinitions.Definition def = all.get(i);
                boolean unlocked = unlockedIds.contains(def.id);
                if (unlocked == (pass == 0))
                {
                    tiles.add(buildCard(def, unlocked, metrics));
                }
            }
        }
        list.add(PageScaffold.columns(2, tiles.toArray(new JComponent[0])));

        list.revalidate();
        list.repaint();
    }

    private JComponent buildCard(AchievementDefinitions.Definition def, boolean unlocked, java.util.Map<String, Integer> metrics)
    {
        boolean showProgress = !unlocked && def.metricKey != null;
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        if (unlocked)
        {
            card.enableTopAccent();
        }

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel((unlocked ? "\u2605 " : "\u2606 ") + def.name);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(unlocked ? ThemeColor.ACCENT : ThemeColor.TEXT_SECONDARY));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new ThemedLabel(def.description, ThemeColor.TEXT_MUTED);
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setBorder(new EmptyBorder(3, 0, 0, 0));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(nameLabel);
        textCol.add(descLabel);

        if (showProgress)
        {
            Integer current = metrics.get(def.metricKey);
            int currentValue = current != null ? Math.min(current, def.target) : 0;
            JPanel progressRow = new JPanel(new BorderLayout(8, 0));
            progressRow.setOpaque(false);
            progressRow.setBorder(new EmptyBorder(8, 0, 0, 0));
            progressRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel barHolder = new JPanel(new java.awt.GridBagLayout());
            barHolder.setOpaque(false);
            java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
            c.fill = java.awt.GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            barHolder.add(new ThinProgressBar(currentValue, def.target), c);
            progressRow.add(barHolder, BorderLayout.CENTER);
            JLabel fraction = new ThemedLabel(currentValue + "/" + def.target, ThemeColor.TEXT_MUTED);
            fraction.setFont(UITheme.FONT_SMALL.deriveFont(10f));
            progressRow.add(fraction, BorderLayout.EAST);
            textCol.add(progressRow);
        }

        card.add(textCol, BorderLayout.CENTER);
        return card;
    }
}
