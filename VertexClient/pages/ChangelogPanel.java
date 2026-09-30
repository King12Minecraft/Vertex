package pages;

import theme.ThemeColor;
import theme.UITheme;
import ui.PageHeader;
import ui.PlaceholderPanel;
import ui.RoundedPanel;
import ui.ThemedLabel;
import ui.ThemedScrollBarUI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * ChangelogPanel
 * --------------
 * "What's new": the entries of CHANGELOG.md, newest first, one card per entry. The same file the
 * website's Changelog page reads, so the two can't disagree. Bundled into VertexClient.jar by
 * build.sh; when running from source it is read from the working directory or the folder above.
 */
public class ChangelogPanel extends RoundedPanel
{
    private static final String FILE_NAME = "CHANGELOG.md";
    /** Wrapped text needs an explicit table width - a body style width is ignored (see the UI notes in .cursor/rules). */
    private static final int TEXT_WIDTH = 640;

    public ChangelogPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));
        add(new PageHeader("CHANGELOG"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(8, 0, 24, 0));

        List<ChangelogParser.Entry> entries = ChangelogParser.parse(load());
        if (entries.isEmpty())
        {
            PlaceholderPanel.show(content, "The changelog couldn't be loaded.");
        }
        else
        {
            for (ChangelogParser.Entry entry : entries)
            {
                content.add(card(entry));
                content.add(Box.createVerticalStrut(16));
            }
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel card(ChangelogParser.Entry entry)
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(TEXT_WIDTH + 80, Integer.MAX_VALUE));

        JLabel heading = new ThemedLabel(entry.heading, ThemeColor.ACCENT);
        heading.setFont(UITheme.FONT_NAV_BOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(new EmptyBorder(0, 0, 8, 0));
        card.add(heading);

        for (String bullet : entry.bullets)
        {
            JLabel line = new ThemedLabel("<html><table width='" + TEXT_WIDTH + "'><tr><td valign='top' width='14'>&bull;</td><td>"
                + escapeHtml(bullet) + "</td></tr></table></html>", ThemeColor.TEXT_PRIMARY);
            line.setFont(UITheme.FONT_BODY);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.setBorder(new EmptyBorder(0, 0, 6, 0));
            card.add(line);
        }
        return card;
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The bundled copy (a packaged jar) first, then the file next to the working directory or the repo root (running from source). */
    static String load()
    {
        try
        {
            InputStream in = ChangelogPanel.class.getResourceAsStream("/" + FILE_NAME);
            if (in == null)
            {
                File file = new File(FILE_NAME);
                if (!file.isFile()) file = new File("..", FILE_NAME);
                if (!file.isFile()) return null;
                in = new FileInputStream(file);
            }
            try
            {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            }
            finally
            {
                in.close();
            }
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
