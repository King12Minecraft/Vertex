import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * NotificationBell
 * -----------------
 * Top bar bell icon. Reads live data from NotificationCenter (Phase 9)
 * - populated by real DM/group events, no longer mock content.
 * Opening the popup marks everything read.
 */
public class NotificationBell extends JButton
{
    private boolean hover = false;

    public NotificationBell()
    {
        setPreferredSize(new Dimension(40, 40));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });

        addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { showPopup(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });

        NotificationCenter.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void showPopup()
    {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ThemeManager.getColor(ThemeColor.BG_PANEL));
        content.setBorder(new EmptyBorder(16, 16, 14, 16));
        content.setPreferredSize(new Dimension(300, 260));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(2000, 24));
        headerRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel header = new JLabel("NOTIFICATIONS");
        header.setFont(UITheme.FONT_NAV_BOLD);
        header.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        headerRow.add(header, BorderLayout.WEST);

        List<NotificationCenter.NotificationItem> items = NotificationCenter.getAll();

        if (!items.isEmpty())
        {
            JLabel clearAll = new JLabel("Clear All");
            clearAll.setFont(UITheme.FONT_SMALL);
            clearAll.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            clearAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            clearAll.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    NotificationCenter.clearAll();
                    popup.setVisible(false);
                }
            });
            headerRow.add(clearAll, BorderLayout.EAST);
        }

        content.add(headerRow);

        if (items.isEmpty())
        {
            JLabel empty = new JLabel("No notifications yet.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            content.add(empty);
        }
        else
        {
            int shown = 0;
            for (int i = 0; i < items.size() && shown < 6; i++)
            {
                if (shown > 0)
                {
                    content.add(Box.createVerticalStrut(10));
                }
                content.add(row(items.get(i).title, items.get(i).body));
                shown++;
            }
        }

        popup.add(content);
        popup.show(this, getWidth() - 300, getHeight() + 8);

        NotificationCenter.markAllRead();
    }

    private JPanel row(String title, String message)
    {
        RoundedPanel r = new RoundedPanel(ThemeColor.BG_SIDEBAR, 10);
        r.setLayout(new BoxLayout(r, BoxLayout.Y_AXIS));
        r.setBorder(new EmptyBorder(10, 12, 10, 12));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(280, 80));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.FONT_NAV_BOLD);
        titleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel msgLabel = new JLabel("<html><body style='width:220px'>" + message + "</body></html>");
        msgLabel.setFont(UITheme.FONT_SMALL);
        msgLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        msgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        msgLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        r.add(titleLabel);
        r.add(msgLabel);
        return r;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        if (hover)
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL_HOVER));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        }

        int cx = getWidth() / 2;
        int cy = getHeight() / 2 - 1;

        g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(cx - 8, cy - 8, 16, 14, 0, 180);
        g2.drawLine(cx - 8, cy - 1, cx - 8, cy + 5);
        g2.drawLine(cx + 8, cy - 1, cx + 8, cy + 5);
        g2.drawLine(cx - 10, cy + 5, cx + 10, cy + 5);
        g2.fillOval(cx - 2, cy + 8, 4, 4);

        if (NotificationCenter.getUnreadCount() > 0)
        {
            g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
            g2.fillOval(getWidth() - 15, 3, 10, 10);
        }

        g2.dispose();
    }
}
