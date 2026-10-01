package pages;

import games.GameInfo;
import games.GameManager;
import theme.ThemeColor;
import theme.UITheme;
import ui.PlaceholderPanel;
import ui.ThemedButton;
import ui.ThemedLabel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * HomeContinueSection
 * -------------------
 * "Continue playing": the games you pinned first, then the ones you played most recently, as clickable tiles
 * (HomeGameTile) - as many as fit the available width, at least two. Clicking a tile opens that game's detail
 * page; the star on a tile pins/unpins it (PinnedGames). A new account with nothing pinned or played sees a
 * friendly nudge towards the Games page instead of an empty box.
 *
 * Data: the recent game ids are fetched once by HomePanel (GAME_HISTORY_REQUEST) and handed in through
 * setRecentGameIds, so Home makes one history request, not two; game names/status come from the cached catalog
 * (GameManager) and the queue count shows how many players are waiting in an online game.
 */
public class HomeContinueSection extends HomeSectionPanel
{
    private static final int GAP = 10;
    private static final int MAX_TILES = 8;

    private final JPanel tileRow = new JPanel();
    private List<String> recentGameIds = new ArrayList<String>();

    public HomeContinueSection()
    {
        super("CONTINUE PLAYING", "Loading your games...");
        setHeaderAction("All games >", new Runnable()
        {
            public void run() { goTo(Pages.ALL_GAMES); }
        });

        tileRow.setOpaque(false);
        tileRow.setLayout(new BoxLayout(tileRow, BoxLayout.X_AXIS));
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        setBodyContent(tileRow);

        // Rebuild when the pin list or the catalog changes, and when the width changes (how many tiles fit).
        PinnedGames.addListener(new Runnable()
        {
            public void run() { rebuildOnSwingThread(); }
        });
        GameManager.addListener(new Runnable()
        {
            public void run() { rebuildOnSwingThread(); }
        });
        addComponentListener(new ComponentAdapter()
        {
            public void componentResized(ComponentEvent e) { rebuild(); }
        });
        rebuild();
    }

    /** Called by HomePanel whenever it has fresh history (most recent first). */
    public void setRecentGameIds(List<String> ids)
    {
        this.recentGameIds = ids == null ? new ArrayList<String>() : new ArrayList<String>(ids);
        rebuild();
    }

    private void rebuildOnSwingThread()
    {
        javax.swing.SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { rebuild(); }
        });
    }

    private void rebuild()
    {
        tileRow.removeAll();

        // pinned first, then recent games that aren't already shown
        List<GameInfo> shown = new ArrayList<GameInfo>();
        List<String> ids = new ArrayList<String>(PinnedGames.getAll());
        for (String id : recentGameIds)
        {
            if (!ids.contains(id)) ids.add(id);
        }
        for (String id : ids)
        {
            GameInfo info = GameManager.findCachedGame(id);
            if (info != null && !info.isComingSoon())
            {
                shown.add(info);
            }
        }

        if (shown.isEmpty())
        {
            tileRow.add(emptyState());
        }
        else
        {
            int usable = Math.max(getWidth() - 40, 2 * (HomeGameTile.WIDTH + GAP));
            int fit = Math.max(2, Math.min(MAX_TILES, (usable + GAP) / (HomeGameTile.WIDTH + GAP)));
            for (int i = 0; i < shown.size() && i < fit; i++)
            {
                GameInfo game = shown.get(i);
                if (i > 0) tileRow.add(Box.createHorizontalStrut(GAP));
                tileRow.add(new HomeGameTile(game, statusFor(game)));
            }
            tileRow.add(Box.createHorizontalGlue());
        }
        tileRow.revalidate();
        tileRow.repaint();
    }

    private static String statusFor(GameInfo game)
    {
        if (game.isOnline() && game.getQueueCount() > 0)
        {
            return game.getQueueCount() + " waiting";
        }
        return game.getType();
    }

    private JPanel emptyState()
    {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentY(Component.TOP_ALIGNMENT);
        JLabel text = new ThemedLabel("<html><table width='340' cellpadding='0' cellspacing='0'><tr><td>Nothing here yet - the games you play and the ones you pin with the star will show up here.</td></tr></table></html>", ThemeColor.TEXT_MUTED);
        text.setFont(UITheme.FONT_BODY);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setBorder(new EmptyBorder(0, 0, 10, 0));
        ThemedButton browse = new ThemedButton("Browse games", true);
        browse.setAlignmentX(Component.LEFT_ALIGNMENT);
        browse.setPreferredSize(new Dimension(170, 34));
        browse.setMaximumSize(new Dimension(170, 34));
        browse.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { goTo(Pages.GAMES); }
        });
        box.add(text);
        box.add(browse);
        return box;
    }
}
