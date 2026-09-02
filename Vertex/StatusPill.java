import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * StatusPill
 * ----------
 * A small rounded pill with a colored dot + label, e.g. "Online" in
 * green or "Coming Soon" in accent. Takes a literal Color rather than a
 * ThemeColor role, since the caller picks the specific status color.
 */
public class StatusPill extends JPanel
{
    private Color pillColor;

    public StatusPill(String text, Color color)
    {
        this.pillColor = color;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(new EmptyBorder(3, 10, 3, 10));

        StatusDot dot = new StatusDot(color, 7);
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(color);
        label.setBorder(new EmptyBorder(0, 6, 0, 0));

        add(dot);
        add(label);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        g2.setColor(new Color(pillColor.getRed(), pillColor.getGreen(), pillColor.getBlue(), 30));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }
}
