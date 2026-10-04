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
public class ChangelogPanel extends PageScaffold
{
    private static final String FILE_NAME = "CHANGELOG.md";
    /** Wrapped text needs an explicit table width - a body style width is ignored (see the UI notes in .cursor/rules). */
    private static final int TEXT_WIDTH = 640;

    public ChangelogPanel()
    {
        super("CHANGELOG", "What changed in Vertex, newest first.");

        List<ChangelogParser.Entry> entries = ChangelogParser.parse(load());
        if (entries.isEmpty())
        {
            JPanel empty = new JPanel();
            empty.setOpaque(false);
            empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
            PlaceholderPanel.show(empty, "The changelog couldn't be loaded.");
            row(empty);
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                row(PageScaffold.fullWidth(card(entries.get(i), i == 0)));
                gap(16);
            }
        }
    }

    private SectionCard card(ChangelogParser.Entry entry, boolean latest)
    {
        JPanel bullets = new JPanel();
        bullets.setOpaque(false);
        bullets.setLayout(new BoxLayout(bullets, BoxLayout.Y_AXIS));
        for (String bullet : entry.bullets)
        {
            JLabel line = new ThemedLabel("<html><table width='" + TEXT_WIDTH + "'><tr><td valign='top' width='14'>&bull;</td><td>"
                + escapeHtml(bullet) + "</td></tr></table></html>", ThemeColor.TEXT_PRIMARY);
            line.setFont(UITheme.FONT_BODY);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.setBorder(new EmptyBorder(0, 0, 6, 0));
            bullets.add(line);
        }
        SectionCard card = new SectionCard(entry.heading.toUpperCase() + (latest ? "   -   LATEST" : "")).content(bullets);
        return latest ? card.withGlow() : card;
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
