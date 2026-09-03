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
public class AchievementsPanel extends RoundedPanel
{
    private final JPanel list;

    public AchievementsPanel()
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Achievements");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new EmptyBorder(0, 0, 16, 0));
        add(title, BorderLayout.NORTH);

        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        renderLocked(new HashSet<String>());
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
                        renderLocked(unlockedList == null ? new HashSet<String>() : new HashSet<String>(unlockedList));
                    }
                });
            }
        });
        worker.start();
    }

    private void renderLocked(Set<String> unlockedIds)
    {
        list.removeAll();

        List<AchievementDefinitions.Definition> all = AchievementDefinitions.getAll();
        int unlockedCount = 0;
        for (int i = 0; i < all.size(); i++)
        {
            if (unlockedIds.contains(all.get(i).id)) unlockedCount++;
        }

        JLabel progress = new JLabel(unlockedCount + " of " + all.size() + " unlocked");
        progress.setFont(UITheme.FONT_NAV_BOLD);
        progress.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setBorder(new EmptyBorder(0, 0, 16, 0));
        list.add(progress);

        for (int i = 0; i < all.size(); i++)
        {
            AchievementDefinitions.Definition def = all.get(i);
            boolean unlocked = unlockedIds.contains(def.id);
            list.add(buildCard(def, unlocked));
            list.add(Box.createVerticalStrut(8));
        }

        list.revalidate();
        list.repaint();
    }

    private JPanel buildCard(AchievementDefinitions.Definition def, boolean unlocked)
    {
        RoundedPanel card = new RoundedPanel(unlocked ? ThemeColor.BG_PANEL_HOVER : ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(2000, 64));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (unlocked)
        {
            card.enableTopAccent();
        }

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel((unlocked ? "\u2605 " : "\u2606 ") + def.name);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(unlocked ? ThemeColor.TEXT_PRIMARY : ThemeColor.TEXT_MUTED));

        JLabel descLabel = new JLabel(def.description);
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setBorder(new EmptyBorder(3, 0, 0, 0));

        textCol.add(nameLabel);
        textCol.add(descLabel);
        card.add(textCol, BorderLayout.CENTER);

        return card;
    }
}
