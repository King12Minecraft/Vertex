package pages;

import theme.ThemeColor;
import ui.PageHeader;
import ui.RoundedPanel;
import ui.ThemedScrollBarUI;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * PageScaffold
 * ------------
 * The frame every page shares: the page's background and side margins, a PageHeader (title, optional muted
 * subtitle, optional right-hand action) and a scrolling body of stacked rows - normally SectionCards. It exists so
 * that pages stop reinventing their own margins, scroll panes and card rows (they had drifted: some used a big
 * heading, some PageHeader, some no margin), and so a layout fix lands everywhere at once.
 *
 *   PageScaffold page = new PageScaffold("FRIENDS", "Who's around, and requests.");
 *   page.add(page.fullWidth(card));          // or split(left, right, 1, 2) / columns(3, a, b, c)
 *   page.gap(16);
 *
 * The body follows the viewport's width (so a wide card can't push a horizontal scrollbar in) and rows keep their
 * own preferred height (FitRow), so cards never stretch to fill spare height. Pages that need their own scrolling
 * (chat, a game catalogue) call setBody(component) to use the area below the header as-is.
 */
public class PageScaffold extends RoundedPanel
{
    private final PageHeader header;
    private final JPanel rows = new WidthTrackingPanel();
    private JScrollPane scroll;

    public PageScaffold(String title, String subtitle)
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        header = new PageHeader(title, subtitle);
        add(header, BorderLayout.NORTH);

        rows.setOpaque(false);
        rows.setBorder(new EmptyBorder(8, 0, 0, 0));
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    public PageScaffold(String title)
    {
        this(title, null);
    }

    public PageHeader header()
    {
        return header;
    }

    /** The stacked-rows container (add rows with row(...) / fullWidth / split / columns, spacing with gap). */
    public JPanel rows()
    {
        return rows;
    }

    /** Adds a row (any component); returns it. */
    public <T extends JComponent> T row(T component)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.add(component);
        return component;
    }

    /** A fixed vertical gap between rows. */
    public void gap(int pixels)
    {
        rows.add(Box.createVerticalStrut(pixels));
    }

    /** Replaces the scrolling rows with a component of the page's own (it handles its own scrolling). */
    public void setBody(JComponent component)
    {
        remove(scroll);
        add(component, BorderLayout.CENTER);
        revalidate();
    }

    // ---------------- row helpers ----------------

    /** One component spanning the full width, at its own preferred height. */
    public static JPanel fullWidth(JComponent component)
    {
        JPanel row = new FitRow(new BorderLayout());
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    /** Two components side by side, widths in the ratio leftWeight:rightWeight, equal heights, each at least 300px. */
    public static JPanel split(JComponent left, JComponent right, double leftWeight, double rightWeight)
    {
        JPanel row = new FitRow(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = leftWeight;
        c.insets = new Insets(0, 0, 0, 8);
        left.setMinimumSize(new Dimension(300, 0));
        right.setMinimumSize(new Dimension(300, 0));
        row.add(left, c);
        c.gridx = 1;
        c.weightx = rightWeight;
        c.insets = new Insets(0, 8, 0, 0);
        row.add(right, c);
        return row;
    }

    /** N equal columns (a grid of cards); a row of the grid stays as tall as its tallest card. */
    public static JPanel columns(int count, JComponent... items)
    {
        int rowsNeeded = (items.length + count - 1) / count;
        JPanel row = new FitRow(new GridLayout(rowsNeeded, count, 12, 12));
        for (int i = 0; i < items.length; i++)
        {
            row.add(items[i]);
        }
        for (int i = items.length; i < rowsNeeded * count; i++)
        {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            row.add(filler);
        }
        return row;
    }

    /**
     * A row whose maximum height always equals its current preferred height. A BoxLayout column otherwise hands spare
     * height to any child that allows it, which would stretch the cards - and a fixed maximum set at construction would
     * go stale the moment a card's content changes (a list gains a row). Asking at layout time avoids both.
     */
    public static class FitRow extends JPanel
    {
        public FitRow(LayoutManager layout)
        {
            super(layout);
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** The scroll view: follows the viewport's width instead of its own preferred width, so a wide row can't add a horizontal scrollbar. */
    public static class WidthTrackingPanel extends JPanel implements Scrollable
    {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return r.height - 32; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
