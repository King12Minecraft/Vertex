package games;
import ui.ThemedButton;
import theme.UITheme;
import theme.ThemeManager;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.DialogUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * GameDetailDialog
 * -----------------
 * The mandatory "about this game" page every Play action shows first
 * - GameLauncher.launch(...) (the one entry point every Play
 * button/card/search-result/invite across the app already goes
 * through) opens this instead of the game directly. Larger art, tags
 * (2D, Multiplayer, Online, etc. - GameMetadata), difficulty, the same
 * rules/controls text GameRulesDialog shows, live queue count and a
 * spectatable badge when relevant (GameInfo already tracks both -
 * this was the one screen that never surfaced them), and the actual
 * Play button - clicking it is what calls GameLauncher.openGame(...)
 * and starts the game for real. There's no separate way to skip
 * straight to playing.
 *
 * Fills the owner window instead of floating as a small fixed-size
 * card (a real gap until this pass - see ROADMAP.md) - this is still a
 * modal JDialog, not a true embedded CardLayout step the way Mode/
 * Lobby/Playing are for every game, since that would mean auditing and
 * rewriting every one of this dialog's call sites (search results,
 * game cards, invites) to hand off into MainMenu's game-host slot
 * instead - a much larger, separately-scoped change tracked in
 * ROADMAP.md rather than folded in here. This gets the immediate
 * "feels like a small popup, not a real step in the flow" complaint
 * fixed with much less risk: same call sites, same trigger, just a
 * dialog that actually fills the screen instead of floating on it.
 */
public class GameDetailDialog
{
    public static void show(Component anchor, final GameInfo game)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        // A dark backdrop filling the whole owner window, with the actual content
        // card centered inside it - "full screen" here means a real full-screen step
        // in the flow, not literally stretching a rules paragraph edge to edge (which
        // would just hurt readability at a wide window size).
        JPanel backdrop = new JPanel(new GridBagLayout());
        backdrop.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        dialog.setContentPane(backdrop);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(640, 620));
        root.enableTopAccent();
        root.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));
        backdrop.add(root, new GridBagConstraints());

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(30, 30, 10, 30));

        JPanel art = new GameCardArt(game.getGameId());
        art.setAlignmentX(Component.LEFT_ALIGNMENT);
        art.setMaximumSize(new Dimension(2000, 200));
        art.setPreferredSize(new Dimension(580, 200));
        body.add(art);
        body.add(Box.createVerticalStrut(18));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_HEADING.deriveFont(28f));
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(name);
        body.add(Box.createVerticalStrut(10));

        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        tagRow.setOpaque(false);
        tagRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagRow.setBorder(new EmptyBorder(0, -6, 0, 0));

        String difficulty = GameMetadata.getDifficulty(game.getGameId());
        Color difficultyColor = GameMetadata.EASY.equals(difficulty) ? new Color(90, 210, 120)
            : GameMetadata.HARD.equals(difficulty) ? new Color(230, 90, 90) : new Color(230, 200, 60);
        tagRow.add(tagPill(difficulty, difficultyColor));

        List<String> tags = GameMetadata.getTags(game.getGameId());
        for (int i = 0; i < tags.size(); i++)
        {
            tagRow.add(tagPill(tags.get(i), ThemeManager.getColor(ThemeColor.TEXT_MUTED)));
        }
        if (game.isSpectatable())
        {
            tagRow.add(tagPill("Spectatable", ThemeManager.getColor(ThemeColor.ACCENT)));
        }
        if (game.isOnline() && game.getQueueCount() > 0)
        {
            String waiting = game.getQueueCount() == 1 ? "1 player waiting" : game.getQueueCount() + " players waiting";
            tagRow.add(tagPill(waiting, new Color(90, 210, 120)));
        }
        body.add(tagRow);
        body.add(Box.createVerticalStrut(18));

        JLabel descLabel = new JLabel("<html><body style='width:520px'>" + escapeHtml(GameRules.get(game.getGameId())) + "</body></html>");
        descLabel.setFont(UITheme.FONT_BODY);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // An HTML JLabel's CSS "width" reliably constrains where the text WRAPS, but
        // its own getPreferredSize() can still report a wider value than that (a real
        // Swing quirk, not tied to any one game's rules text) - left uncorrected, that
        // inflated width bubbles up through body's BoxLayout and root's GridBagLayout,
        // which then compresses the whole card back down to its actual available
        // width and - since GameCardArt is the only child with real compressible slack
        // - visibly squashes the art banner to a sliver to make up the difference.
        // Overriding just the width (keeping Swing's own correctly-wrapped height)
        // fixes this at the source rather than chasing it through the layout chain.
        Dimension descNatural = descLabel.getPreferredSize();
        descLabel.setPreferredSize(new Dimension(520, descNatural.height));
        body.add(descLabel);

        // body's content (art + name + tags + description) is far shorter than root's
        // full height at this larger dialog size - adding it directly to
        // BorderLayout.CENTER would stretch it to fill that space, and since a plain
        // JLabel/JPanel's default maximum size is unbounded, BoxLayout would then
        // distribute that leftover space AS a gap between two of the stacked children
        // rather than leaving it at the bottom - the same stretch-not-center layout bug
        // this session has repeatedly hit and fixed on game boards, here on a dialog
        // instead. BorderLayout.NORTH is the standard, predictable idiom for exactly
        // this - a GridBagLayout wrapper was tried first but reliably undersized body
        // below its own reported preferred height for reasons that didn't repay
        // further chasing; NORTH's well-established contract (stretch width to the
        // container, keep height at the component's own preferred size) has neither
        // problem.
        JPanel bodyWrapper = new JPanel(new BorderLayout());
        bodyWrapper.setOpaque(false);
        bodyWrapper.add(body, BorderLayout.NORTH);
        root.add(bodyWrapper, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(6, 30, 24, 30));

        ThemedButton close = new ThemedButton("Close", false);
        close.setPreferredSize(new Dimension(90, 38));
        close.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });
        buttonRow.add(close);

        ThemedButton play = new ThemedButton(game.isComingSoon() ? "Coming Soon" : "Play", !game.isComingSoon());
        play.setEnabled(!game.isComingSoon());
        play.setPreferredSize(new Dimension(110, 38));
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                dialog.dispose();
                GameLauncher.openGame(dialog.getOwner(), game);
            }
        });
        buttonRow.add(play);

        root.add(buttonRow, BorderLayout.SOUTH);

        if (owner != null && owner.isShowing() && owner.getWidth() > 0 && owner.getHeight() > 0)
        {
            dialog.setSize(owner.getSize());
            dialog.setLocationRelativeTo(owner);
        }
        else
        {
            // No owner window yet (or it hasn't been laid out/shown) - fall back to a
            // sane fixed size centered on screen rather than a degenerate 0x0 dialog.
            dialog.setSize(new Dimension(900, 700));
            dialog.setLocationRelativeTo(null);
        }
        dialog.setVisible(true);
    }

    private static JPanel tagPill(String text, Color color)
    {
        RoundedPanel pill = new RoundedPanel(ThemeColor.BG_APP, 12);
        pill.setLayout(new BorderLayout());
        pill.setBorder(new EmptyBorder(4, 10, 4, 10));

        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL.deriveFont(11f));
        label.setForeground(color);
        pill.add(label, BorderLayout.CENTER);

        return pill;
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
