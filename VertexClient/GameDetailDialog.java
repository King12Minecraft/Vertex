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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * GameDetailDialog
 * -----------------
 * A dedicated "about this game" view - larger art, tags (2D,
 * Multiplayer, Online, etc. - GameMetadata), difficulty, the same
 * description text GameRulesDialog shows, and a Play button right
 * there so you don't need to close this and hunt for the card's own
 * Play button separately. Opened by clicking a card's art/name
 * (rather than replacing the card's own quick-action buttons, which
 * stay as direct shortcuts for Play/Rules/Pin).
 */
public class GameDetailDialog
{
    public static void show(Component anchor, final GameInfo game)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(420, 460));
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(22, 22, 10, 22));

        JPanel art = new GameCardArt(game.getGameId());
        art.setAlignmentX(Component.LEFT_ALIGNMENT);
        art.setMaximumSize(new Dimension(2000, 140));
        art.setPreferredSize(new Dimension(376, 140));
        body.add(art);
        body.add(Box.createVerticalStrut(16));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_HEADING.deriveFont(22f));
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
        body.add(tagRow);
        body.add(Box.createVerticalStrut(16));

        JLabel descLabel = new JLabel("<html><body style='width:360px'>" + escapeHtml(GameRules.get(game.getGameId())) + "</body></html>");
        descLabel.setFont(UITheme.FONT_BODY);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(descLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(6, 22, 18, 22));

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
                GameLauncher.launch(dialog.getOwner(), game);
            }
        });
        buttonRow.add(play);

        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
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
