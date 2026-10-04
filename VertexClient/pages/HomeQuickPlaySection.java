package pages;

import games.GameCardArt;
import games.GameInfo;
import games.GameLauncher;
import games.GameManager;
import theme.ThemeColor;
import theme.UITheme;
import ui.ThemedButton;
import ui.ThemedLabel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * HomeQuickPlaySection
 * --------------------
 * One big "Play" suggestion. The rule for what it suggests (a reversible default - change pick() to change it):
 *   1. the online game with the most players waiting in its queue right now (you get a match straight away);
 *   2. otherwise the online game you played most recently;
 *   3. otherwise the first online game in the catalog.
 * Clicking Play goes through GameLauncher like every other Play button, so the game's detail page still appears
 * first. "Pick another" goes to the Games page. Queue counts come from the cached catalog (GameManager), which the
 * Games page refreshes; the recent ids are handed in by HomePanel (one history request for the whole Home page).
 */
public class HomeQuickPlaySection extends HomeSectionPanel
{
    private final JPanel content = new JPanel(new BorderLayout(14, 0));
    private List<String> recentGameIds = new ArrayList<String>();

    public HomeQuickPlaySection()
    {
        super("QUICK PLAY", "");
        setGlow(true);
        setHeaderAction("Pick another >", new Runnable()
        {
            public void run() { goTo(Pages.GAMES); }
        });
        content.setOpaque(false);
        setBodyContent(content);

        GameManager.addListener(new Runnable()
        {
            public void run() { SwingUtilities.invokeLater(new Runnable() { public void run() { rebuild(); } }); }
        });
        rebuild();
    }

    public void setRecentGameIds(List<String> ids)
    {
        this.recentGameIds = ids == null ? new ArrayList<String>() : new ArrayList<String>(ids);
        rebuild();
    }

    @Override
    public void refresh()
    {
        rebuild();
    }

    /** The suggestion, and why. Returns null when no online game is available yet (catalog not loaded). */
    private GameInfo pick(String[] reasonOut)
    {
        List<GameInfo> online = new ArrayList<GameInfo>();
        for (GameInfo g : GameManager.getCachedGames())
        {
            if (g.isOnline() && !g.isComingSoon()) online.add(g);
        }
        if (online.isEmpty())
        {
            return null;
        }

        GameInfo busiest = null;
        for (GameInfo g : online)
        {
            if (g.getQueueCount() > 0 && (busiest == null || g.getQueueCount() > busiest.getQueueCount()))
            {
                busiest = g;
            }
        }
        if (busiest != null)
        {
            reasonOut[0] = busiest.getQueueCount() == 1 ? "1 player is waiting - you'd match straight away"
                : busiest.getQueueCount() + " players are waiting - you'd match straight away";
            return busiest;
        }

        for (String id : recentGameIds)
        {
            for (GameInfo g : online)
            {
                if (g.getGameId().equals(id))
                {
                    reasonOut[0] = "One of your recent games";
                    return g;
                }
            }
        }
        reasonOut[0] = "A good place to start";
        return online.get(0);
    }

    private void rebuild()
    {
        content.removeAll();
        String[] reason = { "" };
        final GameInfo game = pick(reason);

        if (game == null)
        {
            JLabel loading = new ThemedLabel("Finding a game for you...", ThemeColor.TEXT_MUTED);
            loading.setFont(UITheme.FONT_BODY);
            content.add(loading, BorderLayout.NORTH);
        }
        else
        {
            GameCardArt art = new GameCardArt(game.getGameId());
            art.setPreferredSize(new Dimension(96, 80));
            content.add(art, BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel name = new ThemedLabel(game.getName(), ThemeColor.TEXT_PRIMARY);
            name.setFont(UITheme.FONT_HEADING.deriveFont(20f));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel why = new ThemedLabel("<html><table width='170' cellpadding='0' cellspacing='0'><tr><td>" + reason[0] + "</td></tr></table></html>",
                ThemeColor.TEXT_MUTED);
            why.setFont(UITheme.FONT_SMALL);
            why.setAlignmentX(Component.LEFT_ALIGNMENT);
            why.setBorder(new EmptyBorder(2, 0, 10, 0));

            final ThemedButton play = new ThemedButton("Play now", true);
            play.setAlignmentX(Component.LEFT_ALIGNMENT);
            play.setPreferredSize(new Dimension(150, 38));
            play.setMaximumSize(new Dimension(150, 38));
            play.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { GameLauncher.launch(play, game); }
            });
            text.add(name);
            text.add(why);
            text.add(play);
            content.add(text, BorderLayout.CENTER);
        }
        content.revalidate();
        content.repaint();
    }
}
