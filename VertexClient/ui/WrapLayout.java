package ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * WrapLayout
 * ----------
 * A FlowLayout whose preferred height accounts for wrapping. The stock FlowLayout reports the height of a single
 * row, so inside a vertical BoxLayout (or scroll view) a row of cards that wraps onto a second line gets clipped.
 * This one measures how many rows fit in the container's current width and reports that height; a container using
 * it should sit in something that follows the viewport width (PageScaffold's scroll body does).
 */
public class WrapLayout extends FlowLayout
{
    public WrapLayout(int align, int hgap, int vgap)
    {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target)
    {
        return measure(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target)
    {
        Dimension d = measure(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension measure(Container target, boolean preferred)
    {
        synchronized (target.getTreeLock())
        {
            int available = target.getWidth();
            if (available <= 0)
            {
                available = Integer.MAX_VALUE;   // not laid out yet: one long row, corrected on the next pass
            }
            Insets insets = target.getInsets();
            int maxWidth = available - (insets.left + insets.right + getHgap() * 2);

            int width = 0;
            int height = insets.top + insets.bottom + getVgap() * 2;
            int rowWidth = 0;
            int rowHeight = 0;
            for (int i = 0; i < target.getComponentCount(); i++)
            {
                Component c = target.getComponent(i);
                if (!c.isVisible())
                {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > maxWidth)
                {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            return new Dimension(width + insets.left + insets.right + getHgap() * 2, height);
        }
    }
}
