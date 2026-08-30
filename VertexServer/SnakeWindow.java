import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * SnakeWindow
 * -----------
 * Standalone window for playing Snake: a mode-select screen (Classic
 * vs Wrap-Around), then the game itself. Launched from GamesPanel's
 * Play button once GamesPanel recognizes the "snake" gameId.
 */
public class SnakeWindow extends JFrame
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String PLAY = "PLAY";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private SnakePanel snakePanel;

    public SnakeWindow()
    {
        super("Vertex - Snake");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelect(), MODE_SELECT);

        getContentPane().add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private JPanel createModeSelect()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 320));

        final JLabel title = new JLabel("Snake");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        final JLabel subtitle = new JLabel("Choose a mode to start.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 30, 0));
        panel.add(subtitle);

        panel.add(modeButton("Classic", "Hitting a wall ends the game.", SnakeGame.Mode.CLASSIC));
        panel.add(Box.createVerticalStrut(14));
        panel.add(modeButton("Wrap-Around", "Pass through walls to the other side.", SnakeGame.Mode.WRAP_AROUND));

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            }
        });

        return panel;
    }

    private JPanel modeButton(String name, String description, final SnakeGame.Mode mode)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(2000, 90));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.enableTopAccent();

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        textCol.add(nameLabel);
        textCol.add(descLabel);
        card.add(textCol, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { startGame(mode); }
            public void mouseEntered(MouseEvent e)
            {
                card.setBackgroundRole(ThemeColor.BG_PANEL_HOVER);
                card.glow().animateIn();
            }
            public void mouseExited(MouseEvent e)
            {
                card.setBackgroundRole(ThemeColor.BG_PANEL);
                card.glow().animateOut();
            }
        });

        return card;
    }

    private void startGame(SnakeGame.Mode mode)
    {
        final SnakeGame game = new SnakeGame(mode);

        if (snakePanel != null)
        {
            snakePanel.stopTimer();
            cards.remove(snakePanel);
        }

        Runnable onGameOver = new Runnable()
        {
            public void run()
            {
                recordPlayed(game.getScore());
                SnakeGameOverDialog.show(snakePanel, game.getScore(), new SnakeGameOverDialog.Choice()
                {
                    public void onPlayAgain() { startGame(mode); }
                    public void onClose() { SnakeWindow.this.dispose(); }
                });
            }
        };

        snakePanel = new SnakePanel(game, onGameOver);
        game.start();

        cards.add(snakePanel, PLAY);
        cardLayout.show(cards, PLAY);
        pack();
        setLocationRelativeTo(null);
        snakePanel.requestFocusInWindow();
        snakePanel.startTimer();
    }

    /**
     * Fire-and-forget, sent once the game ends (so the final score is
     * known) - lets the server log this for "Continue Playing"/
     * "Trending" on the Games page, and award coins based on score
     * (see EconomyConfig.getSnakeReward). If not logged in (guest/
     * offline play from the login screen), queues it locally instead -
     * the server has no account to attribute it to yet - and it gets
     * sent once a real login succeeds (see GuestPlayTracker).
     */
    private void recordPlayed(int score)
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("snake", score);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("snake");
        request.setScore(score);
        NetworkManager.sendAsync(request);
    }
}
