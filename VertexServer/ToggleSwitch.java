import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ToggleSwitch
 * ------------
 * A pill-shaped on/off switch, theme-aware, used in place of the
 * default JCheckBox for settings toggles.
 */
public class ToggleSwitch extends JButton
{
    private boolean on;

    public ToggleSwitch(boolean initialOn)
    {
        this.on = initialOn;
        setPreferredSize(new Dimension(46, 26));
        setMaximumSize(new Dimension(46, 26));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                on = !on;
                repaint();
            }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    public boolean isOn()
    {
        return on;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(on ? ThemeManager.getColor(ThemeColor.ACCENT) : ThemeManager.getColor(ThemeColor.BORDER));
        g2.fillRoundRect(0, 0, w, h, h, h);

        int knobDiameter = h - 6;
        int knobX = on ? w - knobDiameter - 3 : 3;
        g2.setColor(on ? ThemeManager.getColor(ThemeColor.BG_APP) : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        g2.fillOval(knobX, 3, knobDiameter, knobDiameter);

        g2.dispose();
    }
}
