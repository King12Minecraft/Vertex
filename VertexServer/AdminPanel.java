import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * AdminPanel
 * ----------
 * Only reachable by ADMIN accounts (see Sidebar). Mock section cards for
 * now - real functionality lands with each section's actual phase:
 * Players/Games/Servers (Phase 14), Economy (Phase 11), Announcements
 * (Phase 14), Server Status (Phase 5).
 *
 * Reminder baked into every action here: this page being hidden from
 * non-admins is a UI convenience, not security. The server (Phase 5)
 * must independently verify ADMIN role for every request it receives,
 * never trusting what the client claims.
 */
public class AdminPanel extends RoundedPanel
{
    public AdminPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        PageHeader header = new PageHeader("ADMIN PANEL");
        add(header, BorderLayout.NORTH);

        add(createGrid(), BorderLayout.CENTER);
    }

    private JScrollPane createGrid()
    {
        JPanel grid = new JPanel(new GridLayout(0, 3, 18, 18));
        grid.setOpaque(false);

        grid.add(sectionCard("Players", "Manage accounts, roles, and bans.", "Phase 14"));
        grid.add(sectionCard("Games", "Publish, update, and manage games.", "Phase 6 / 13"));
        grid.add(sectionCard("Economy", "Coin balances and shop items.", "Phase 11"));
        grid.add(sectionCard("Announcements", "Post platform-wide notices.", "Phase 14"));
        grid.add(sectionCard("Server Status", "Live server health and stats.", "Phase 5"));
        grid.add(sectionCard("Chat Moderation", "Review reports, mute/ban.", "Phase 9 / 14"));

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private JPanel sectionCard(final String title, String description, final String arrivesIn)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setPreferredSize(new Dimension(220, 175));
        card.enableTopAccent();
        card.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { card.glow().animateIn(); }
            public void mouseExited(MouseEvent e)  { card.glow().animateOut(); }
        });

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_NAV_BOLD);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel("<html><body style='width:170px'>" + description + "</body></html>");
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descLabel.setBorder(new EmptyBorder(6, 0, 14, 0));

        final ThemedButton open = new ThemedButton("Open", false);
        open.setAlignmentX(Component.LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(500, 34));
        open.setPreferredSize(new Dimension(120, 34));
        open.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                GameHubDialog.show(open, title, "This section arrives in " + arrivesIn + ".");
            }
        });

        card.add(titleLabel);
        card.add(descLabel);
        card.add(open);

        return card;
    }
}
