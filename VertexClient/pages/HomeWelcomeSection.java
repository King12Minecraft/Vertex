package pages;

import account.Account;
import account.Session;
import economy.ChallengeProgressInfo;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.StatusPill;
import ui.ThemedLabel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Calendar;
import java.util.List;

/**
 * HomeWelcomeSection
 * ------------------
 * The hero card at the top of Home: a time-of-day greeting with your name, two chips (your daily-login streak and
 * coin balance) and, beside them, how far along your active quests are.
 *
 * Data: the name, coins and streak come from the logged-in Account (account.Session) - the streak is what the
 * server computed when you logged in (logging in is how the daily reward is claimed), and the card re-reads the
 * account whenever Session announces a change (coins move when you win or buy something). Quests are
 * CHALLENGES_REQUEST -> ChallengeProgressInfo; the first three unfinished ones are shown with a progress bar.
 */
public class HomeWelcomeSection extends HomeSectionPanel
{
    private final JLabel greeting = new ThemedLabel(" ", ThemeColor.TEXT_PRIMARY);
    private final JLabel subline = new ThemedLabel(" ", ThemeColor.TEXT_MUTED);
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel quests = new JPanel();

    public HomeWelcomeSection()
    {
        super("WELCOME BACK", "");
        setGlow(true);
        setHeaderAction("All quests >", new Runnable()
        {
            public void run() { goTo(Pages.QUESTS); }
        });

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        greeting.setFont(UITheme.FONT_HEADING);
        greeting.setAlignmentX(Component.LEFT_ALIGNMENT);
        subline.setFont(UITheme.FONT_BODY);
        subline.setAlignmentX(Component.LEFT_ALIGNMENT);
        subline.setBorder(new EmptyBorder(2, 0, 12, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(greeting);
        left.add(subline);
        left.add(chips);
        left.add(Box.createVerticalGlue());

        quests.setOpaque(false);
        quests.setLayout(new BoxLayout(quests, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new GridLayout(1, 2, 28, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(quests);
        setBodyContent(row);

        Session.addListener(new Runnable()
        {
            public void run() { SwingUtilities.invokeLater(new Runnable() { public void run() { renderAccount(); } }); }
        });
        ThemeManager.addListener(new Runnable()
        {
            public void run() { renderAccount(); }
        });
        renderAccount();
        showQuestMessage("Loading your quests...");
    }

    private void renderAccount()
    {
        Account account = Session.getCurrentAccount();
        String name = account == null ? "Guest" : account.getUsername();
        greeting.setText(greetingFor(name));

        chips.removeAll();
        if (account != null)
        {
            int streak = account.getLoginStreak();
            subline.setText(streak > 1 ? "You've logged in " + streak + " days in a row - keep it going!"
                : "Good to see you. Play a game or finish a quest to earn coins.");
            if (streak > 0)
            {
                chips.add(new StatusPill("Day " + streak + " streak", ThemeManager.getColor(ThemeColor.ACCENT)));
            }
            chips.add(new StatusPill(account.getCoins() + " coins", ThemeManager.getColor(ThemeColor.SUCCESS)));
        }
        else
        {
            subline.setText("Log in to track quests, streaks and coins.");
        }
        chips.revalidate();
        chips.repaint();
    }

    private static String greetingFor(String name)
    {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = hour < 5 ? "Up late" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        return part + ", " + name;
    }

    @Override
    public void refresh()
    {
        renderAccount();
        if (!Session.isLoggedIn())
        {
            showQuestMessage("Quests appear here once you're logged in.");
            return;
        }
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CHALLENGES_REQUEST);
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { renderQuests(response); }
                });
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void renderQuests(Message response)
    {
        quests.removeAll();
        if (response == null || !response.isSuccess() || response.getChallenges() == null)
        {
            showQuestMessage("Couldn't load your quests right now.");
            return;
        }

        JLabel title = new ThemedLabel("QUESTS IN PROGRESS", ThemeColor.TEXT_MUTED);
        title.setFont(UITheme.FONT_SMALL);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        quests.add(title);

        List<ChallengeProgressInfo> all = response.getChallenges();
        int shown = 0;
        for (int i = 0; i < all.size() && shown < 3; i++)
        {
            ChallengeProgressInfo q = all.get(i);
            if (!q.isCompleted())
            {
                quests.add(questLine(q));
                quests.add(Box.createVerticalStrut(8));
                shown++;
            }
        }
        if (shown == 0)
        {
            JLabel done = new ThemedLabel("All caught up - every quest is done. Nice!", ThemeColor.SUCCESS);
            done.setFont(UITheme.FONT_BODY);
            done.setAlignmentX(Component.LEFT_ALIGNMENT);
            quests.add(done);
        }
        quests.revalidate();
        quests.repaint();
    }

    private JPanel questLine(ChallengeProgressInfo q)
    {
        JPanel line = new JPanel(new BorderLayout(0, 4));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(4000, 40));

        JLabel name = new ThemedLabel(q.getTitle(), ThemeColor.TEXT_PRIMARY);
        name.setFont(UITheme.FONT_BODY);
        JLabel count = new ThemedLabel(q.getProgress() + " / " + q.getTarget(), ThemeColor.TEXT_MUTED);
        count.setFont(UITheme.FONT_SMALL);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(name, BorderLayout.WEST);
        top.add(count, BorderLayout.EAST);

        ui.ThinProgressBar bar = new ui.ThinProgressBar(q.getProgress(), Math.max(q.getTarget(), 1));
        bar.setPreferredSize(new Dimension(10, 6));

        line.add(top, BorderLayout.NORTH);
        line.add(bar, BorderLayout.CENTER);
        return line;
    }

    private void showQuestMessage(String text)
    {
        quests.removeAll();
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_BODY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        quests.add(label);
        quests.revalidate();
        quests.repaint();
    }
}
