import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * OfflineHubWindow
 * ----------------
 * What "Play Offline" on the login screen actually opens - a proper
 * landing screen, not a direct jump into a specific game. Lists every
 * game that's genuinely playable with no account or server connection
 * (currently just Snake, since Tic-Tac-Toe Online is inherently
 * multiplayer and has no opponent to play against offline). Built so
 * more offline-capable games can be added to the grid later without
 * any further restructuring - the grid, not a hardcoded launch, is the
 * real fix here.
 */
public class OfflineHubWindow extends JFrame
{
    public OfflineHubWindow()
    {
        super("Vertex - Offline Mode");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 440);
        setMinimumSize(new Dimension(520, 380));
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
        setIconImage(GameLogo.renderIcon(64));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(0, 28, 24, 28));

        root.add(new PageHeader("OFFLINE MODE"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel blurb = new JLabel("<html><body style='width:460px'>No account or server connection "
            + "needed for these. Anything you play here is remembered and synced - history and any "
            + "coins earned - the moment you log in.</body></html>");
        blurb.setFont(UITheme.FONT_BODY);
        blurb.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
        blurb.setBorder(new EmptyBorder(0, 0, 20, 0));
        content.add(blurb);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.add(buildOfflineCard("snake", "Snake", "Classic snake, Classic and Wrap-Around modes."));
        content.add(grid);

        root.add(content, BorderLayout.CENTER);
        setContentPane(root);

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private JPanel buildOfflineCard(final String gameId, String name, String description)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.enableTopAccent();
        card.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { card.glow().animateIn(); }
            public void mouseExited(MouseEvent e)  { card.glow().animateOut(); }
        });

        JPanel art = new GameCardArt(gameId);
        art.setPreferredSize(new Dimension(200, 90));
        card.add(art, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(14, 0, 0, 0));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UITheme.FONT_NAV_BOLD);
        nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel("<html><body style='width:180px'>" + description + "</body></html>");
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descLabel.setBorder(new EmptyBorder(4, 0, 10, 0));

        final ThemedButton play = new ThemedButton("Play", true);
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setMaximumSize(new Dimension(500, 36));
        play.setPreferredSize(new Dimension(180, 36));
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { launch(gameId); }
        });

        info.add(nameLabel);
        info.add(descLabel);
        info.add(Box.createVerticalGlue());
        info.add(play);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private void launch(String gameId)
    {
        if ("snake".equals(gameId))
        {
            SnakeWindow window = new SnakeWindow();
            window.setVisible(true);
        }
        // Future offline-capable games get their launch case added here.
    }
}
