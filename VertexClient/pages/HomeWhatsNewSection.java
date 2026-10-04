package pages;

import theme.ThemeColor;
import theme.UITheme;
import ui.ThemedLabel;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.util.List;

/**
 * HomeWhatsNewSection
 * -------------------
 * The newest entry of CHANGELOG.md (the file the Changelog page and the website also read): its heading and first
 * few changes, with a "Full changelog" link in the header. Data: ChangelogPanel.load() + ChangelogParser.parse(...).
 */
public class HomeWhatsNewSection extends HomeSectionPanel
{
    private static final int MAX_POINTS = 3;

    public HomeWhatsNewSection()
    {
        super("WHAT'S NEW", "The latest changes will show up here.");
        setHeaderAction("Full changelog >", new Runnable()
        {
            public void run() { goTo(Pages.CHANGELOG); }
        });
        refresh();
    }

    @Override
    public void refresh()
    {
        List<ChangelogParser.Entry> entries = ChangelogParser.parse(ChangelogPanel.load());
        if (entries.isEmpty())
        {
            return;
        }
        ChangelogParser.Entry latest = entries.get(0);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel heading = new ThemedLabel(latest.heading, ThemeColor.ACCENT);
        heading.setFont(UITheme.FONT_NAV_BOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(new EmptyBorder(0, 0, 6, 0));
        content.add(heading);

        for (int i = 0; i < latest.bullets.size() && i < MAX_POINTS; i++)
        {
            String text = latest.bullets.get(i).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            JLabel line = new ThemedLabel("<html><table width='700' cellpadding='0' cellspacing='0'><tr><td valign='top' width='14'>&bull;</td><td>"
                + text + "</td></tr></table></html>", ThemeColor.TEXT_PRIMARY);
            line.setFont(UITheme.FONT_BODY);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.setBorder(new EmptyBorder(0, 0, 4, 0));
            content.add(line);
        }
        setBodyContent(content);
    }
}
