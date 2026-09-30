package pages;

import theme.ThemeColor;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedLabel;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * HomeSectionPanel
 * ----------------
 * The base for one card on the Home page (see HomePanel). Each Home section is its own small
 * subclass - HomeWelcomeSection, HomeContinueSection, HomeQuickPlaySection, HomeFriendsSection,
 * HomeTournamentsSection, HomeWhatsNewSection - so one can be designed and filled in without
 * touching the others. Right now they are PLACEHOLDERS: a title and a friendly line saying what
 * will live there; each subclass's own javadoc lists the data that's already available for it and
 * what would still need building. Nothing here is final design.
 *
 * To fill one in: build the real content, call setBodyContent(...) to swap out the placeholder line,
 * and put any data loading in refresh() (called when Home is built and on its refresh timer) -
 * network calls belong on a background thread, with the UI update back on the Swing thread
 * (NetworkManager.send blocks). Colours come from ThemeColor roles, never literals.
 */
public abstract class HomeSectionPanel extends RoundedPanel
{
    private final JPanel body = new JPanel(new BorderLayout());
    private final JLabel placeholder;

    protected HomeSectionPanel(String title, String placeholderText)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(16, 20, 16, 20));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new ThemedLabel(title, ThemeColor.TEXT_SECONDARY);
        heading.setFont(UITheme.FONT_NAV_BOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(heading);

        body.setOpaque(false);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        placeholder = new ThemedLabel(placeholderText, ThemeColor.TEXT_MUTED);
        placeholder.setFont(UITheme.FONT_BODY);
        body.add(placeholder, BorderLayout.NORTH);
        add(body);
    }

    /** Replaces the placeholder line with real content. */
    protected final void setBodyContent(JComponent content)
    {
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    /** Called when Home is created and on its refresh timer. Placeholders have nothing to load; a real section fetches here (background thread) and updates on the Swing thread. */
    public void refresh()
    {
    }
}
