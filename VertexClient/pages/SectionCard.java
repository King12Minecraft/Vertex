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
 * SectionCard
 * -----------
 * The app's standard card (the look first built for the Home page, now used by every page): a rounded panel with
 * a title row - an optional "see all"-style link on the right - over a body. Pages put one of these per logical
 * group instead of drawing their own boxes, so spacing, type and colours stay consistent and follow the theme.
 *
 * Build content and call setBodyContent(...) (or use the public wrappers when used directly, e.g.
 * new SectionCard("FRIENDS").content(panel)). Colours come from ThemeColor roles, never literals; setGlow(true)
 * gives a "hero" card a soft accent glow behind its content. refresh() is a hook for cards that load data
 * (Home calls it on a timer); network calls belong on a background thread with the UI update on the Swing thread.
 */
public class SectionCard extends RoundedPanel
{
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel header = new JPanel(new BorderLayout())
    {
        // keep the title row at its own height when the card is stretched taller than its content (the body takes the rest)
        @Override
        public java.awt.Dimension getMaximumSize()
        {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    };
    private JLabel actionLink;
    private JLabel heading;
    private boolean glow = false;

    public SectionCard(String title, String placeholderText)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 20, 18, 20));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 12, 0));
        heading = new ThemedLabel(title, ThemeColor.TEXT_SECONDARY);
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

    /** Changes the card's title. */
    public void setTitle(String title)
    {
        heading.setText(title);
    }

    /** A card with a title and no placeholder text (the body is filled by content(...)). */
    public SectionCard(String title)
    {
        this(title, "");
    }

    /** Public form of setBodyContent for pages that build a card inline; returns this for chaining. */
    public SectionCard content(JComponent content)
    {
        setBodyContent(content);
        return this;
    }

    /** Public form of setHeaderAction; returns this for chaining. */
    public SectionCard action(String text, Runnable onClick)
    {
        setHeaderAction(text, onClick);
        return this;
    }

    /** Public form of setGlow; returns this for chaining. */
    public SectionCard withGlow()
    {
        setGlow(true);
        return this;
    }

    /** Replaces whatever is in the body (the placeholder line at first) with content. */
    protected void setBodyContent(JComponent content)
    {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    /** A small link at the right of the title row, e.g. "See all >". Pass null text to remove it. */
    protected void setHeaderAction(String text, final Runnable onClick)
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
    protected void setGlow(boolean glow)
    {
        this.glow = glow;
        repaint();
    }

    /** Go to one of the sidebar pages (a Pages key). */
    public static void goTo(String pageKey)
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
