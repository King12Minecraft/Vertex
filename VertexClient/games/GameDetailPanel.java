package games;
import ui.ThemedButton;
import theme.UITheme;
import theme.ThemeManager;
import theme.ThemeColor;
import ui.RoundedPanel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * GameDetailPanel
 * ----------------
 * The mandatory "about this game" step every Play action shows first, as a real
 * page inside MainMenu's CardLayout (Pages.GAME_DETAIL) rather than a modal popup -
 * this replaced GameDetailDialog, which was a full-window JDialog standing in for
 * exactly this. GameLauncher.launch(...) (the one entry point every Play button/
 * card/search result/invite goes through) and GamesPanel's art/name click targets
 * all reach it via MainMenu.showGameDetails(...). Art, tags, difficulty, the same
 * rules text GameRulesDialog shows, live queue count and a spectatable badge, and
 * the Play button that actually starts the game (GameLauncher.openGame(...)) -
 * there's still no way to skip straight to playing.
 *
 * Back (or Escape) calls onBack, which MainMenu wires to "return to whichever page
 * opened this" rather than a fixed destination.
 */
public class GameDetailPanel extends JPanel
{
    public GameDetailPanel(final GameInfo game, final Runnable onBack)
    {
        // Same dark backdrop with a centered content card the dialog used - "full
        // screen step" here means a real page in the flow, not stretching a rules
        // paragraph edge to edge (which would just hurt readability at a wide window).
        super(new GridBagLayout());
        setBackground(ThemeManager.getColor(ThemeColor.BG_APP));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(640, 620));
        root.enableTopAccent();
        root.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));
        add(root, new GridBagConstraints());

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

        // A table width, not "<body style='width:520px'>": on newer JDKs the CSS form is
        // ignored (the label reports ~676px wide and the text wraps too late, getting
        // clipped at the card edge) while HTML 3.2's table width is honored everywhere.
        JLabel descLabel = new JLabel("<html><table width='520' cellpadding='0' cellspacing='0'><tr><td>" + escapeHtml(GameRules.get(game.getGameId())) + "</td></tr></table></html>");
        descLabel.setFont(UITheme.FONT_BODY);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // An HTML JLabel's CSS "width" constrains where the text WRAPS, but its own
        // getPreferredSize() can still report a wider value (a real Swing quirk) - left
        // uncorrected, that inflated width bubbles up through body's BoxLayout and the
        // card's layout, which then squashes the art banner to a sliver to make up the
        // difference. Overriding just the width (keeping Swing's correctly-wrapped
        // height) fixes it at the source.
        Dimension descNatural = descLabel.getPreferredSize();
        descLabel.setPreferredSize(new Dimension(520, descNatural.height));
        body.add(descLabel);

        // body is far shorter than the card's full height - BorderLayout.NORTH keeps it
        // at its own preferred height instead of BoxLayout spreading the leftover space
        // as a gap between two stacked children (the same stretch-not-center bug hit
        // repeatedly on game boards).
        JPanel bodyWrapper = new JPanel(new BorderLayout());
        bodyWrapper.setOpaque(false);
        bodyWrapper.add(body, BorderLayout.NORTH);
        root.add(bodyWrapper, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(6, 30, 24, 30));

        ThemedButton back = new ThemedButton("Back", false);
        back.setPreferredSize(new Dimension(90, 38));
        back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onBack.run(); }
        });
        buttonRow.add(back);

        ThemedButton play = new ThemedButton(game.isComingSoon() ? "Coming Soon" : "Play", !game.isComingSoon());
        play.setEnabled(!game.isComingSoon());
        play.setPreferredSize(new Dimension(110, 38));
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { GameLauncher.openGame(GameDetailPanel.this, game); }
        });
        buttonRow.add(play);

        root.add(buttonRow, BorderLayout.SOUTH);

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction()
        {
            public void actionPerformed(ActionEvent e) { onBack.run(); }
        });
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
