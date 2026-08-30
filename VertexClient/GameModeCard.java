import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * GameModeCard
 * ------------
 * A big, tile-style mode-select card - inspired by the large visual
 * mode-thumbnail grids common in browser io-games (a big colored tile
 * per mode, title + short description, grid layout), not any specific
 * copied assets or content. Replaces the earlier thin horizontal row
 * cards previously used for mode selection across Racing, Among Us,
 * Fight Arena, and Tic-Tac-Toe - one shared component so all four look
 * and behave consistently rather than each reinventing it.
 */
public class GameModeCard extends RoundedPanel
{
    public interface ClickListener
    {
        void onClick();
    }

    public GameModeCard(String title, String description, Color accentColor, final ClickListener listener)
    {
        this(title, description, accentColor, false, listener);
    }

    /** showLastPlayedBadge adds a small "Last played" tag in the corner - a hint about which mode you picked last time, not an auto-selection, so switching modes is always just as easy as picking the other card. */
    public GameModeCard(String title, String description, Color accentColor, boolean showLastPlayedBadge, final ClickListener listener)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(190, 150));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enableTopAccent();

        JPanel colorBand = new JPanel();
        colorBand.setBackground(accentColor);
        colorBand.setPreferredSize(new Dimension(0, 8));
        add(colorBand, BorderLayout.NORTH);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setBorder(new EmptyBorder(18, 16, 16, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_HEADING.deriveFont(20f));
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));

        JLabel descLabel = new JLabel("<html><body style='width:150px'>" + description + "</body></html>");
        descLabel.setFont(UITheme.FONT_SMALL);
        descLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        descLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        textCol.add(titleLabel);
        textCol.add(descLabel);

        if (showLastPlayedBadge)
        {
            JLabel badge = new JLabel("Last played");
            badge.setFont(UITheme.FONT_SMALL.deriveFont(10f));
            badge.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            badge.setBorder(new EmptyBorder(10, 0, 0, 0));
            textCol.add(badge);
        }

        add(textCol, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { listener.onClick(); }
            public void mouseEntered(MouseEvent e) { glow().animateIn(); }
            public void mouseExited(MouseEvent e) { glow().animateOut(); }
        });
    }
}
