package pages;

import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedLabel;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * HomeSectionPanel
 * ----------------
 * The base for one card on the Home page (see HomePanel): a title row (with an optional "see all" link on the
 * right) over a body. Each Home section is its own small subclass - HomeWelcomeSection, HomeContinueSection,
 * HomeQuickPlaySection, HomeFriendsSection, HomeTournamentsSection, HomeWhatsNewSection - so one can change
 * without touching the others.
 *
 * A subclass builds its content and calls setBodyContent(...); data loading goes in refresh(), which HomePanel
 * calls when Home is built and every 30 seconds. Network calls belong on a background thread with the UI update
 * back on the Swing thread (NetworkManager.send blocks). Colours come from ThemeColor roles, never literals;
 * setGlow(true) gives the two "hero" cards (Welcome, Quick play) a soft accent glow behind their content.
 */
public abstract class HomeSectionPanel extends RoundedPanel
{
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel header = new JPanel(new BorderLayout());
    private JLabel actionLink;
    private boolean glow = false;

    protected HomeSectionPanel(String title, String placeholderText)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 20, 18, 20));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 12, 0));
        JLabel heading = new ThemedLabel(title, ThemeColor.TEXT_SECONDARY);
        heading.setFont(UITheme.FONT_NAV_BOLD);
        header.add(heading, BorderLayout.WEST);
        add(header);

        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel placeholder = new ThemedLabel(placeholderText, ThemeColor.TEXT_MUTED);
        placeholder.setFont(UITheme.FONT_BODY);
        body.add(placeholder, BorderLayout.NORTH);
        add(body);
    }

    /** Replaces whatever is in the body (the placeholder line at first) with content. */
    protected final void setBodyContent(JComponent content)
    {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    /** A small link at the right of the title row, e.g. "See all >". Pass null text to remove it. */
    protected final void setHeaderAction(String text, final Runnable onClick)
    {
        if (actionLink != null)
        {
            header.remove(actionLink);
            actionLink = null;
        }
        if (text != null)
        {
            actionLink = new ThemedLabel(text, ThemeColor.ACCENT);
            actionLink.setFont(UITheme.FONT_SMALL);
            actionLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            actionLink.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
            header.add(actionLink, BorderLayout.EAST);
        }
        header.revalidate();
        header.repaint();
    }

    /** Soft accent glow behind the content - for the cards that should stand out. */
    protected final void setGlow(boolean glow)
    {
        this.glow = glow;
        repaint();
    }

    /** Go to one of the sidebar pages (a Pages key). */
    protected static void goTo(String pageKey)
    {
        MainMenu menu = MainMenu.getInstance();
        if (menu != null)
        {
            menu.onNavigate(pageKey);
        }
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        if (!glow)
        {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        int w = getWidth();
        int h = getHeight();
        g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, UITheme.RADIUS_PANEL, UITheme.RADIUS_PANEL));
        Color accent = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
        float radius = Math.max(Math.max(w, h) * 0.75f, 1f);
        g2.setPaint(new RadialGradientPaint(w * 0.1f, h * 0.2f, radius, new float[] { 0f, 1f },
            new Color[] { new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60),
                          new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0) }));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    /** Called when Home is created and on its refresh timer. A section that loads data does it here (background thread), updating the UI on the Swing thread. */
    public void refresh()
    {
    }
}
