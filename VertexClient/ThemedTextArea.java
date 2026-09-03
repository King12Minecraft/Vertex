import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * ThemedTextArea
 * ---------------
 * Multi-line sibling of ThemedTextField - same placeholder-swap trick,
 * same RoundedPanel wrapper, but wraps a scrollable JTextArea instead of
 * a JTextField for anything longer than a single line (e.g. a bug
 * report or suggestion body - see FeedbackDialog).
 */
public class ThemedTextArea extends RoundedPanel
{
    private final JTextArea area;
    private final String placeholder;

    public ThemedTextArea(String placeholder, int rows)
    {
        super(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_BUTTON);
        this.placeholder = placeholder;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 14, 10, 10));

        area = new JTextArea(rows, 0);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UITheme.FONT_BODY);
        area.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        area.setCaretColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        area.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setOpaque(false);
        area.setText(placeholder);

        area.addFocusListener(new FocusAdapter()
        {
            public void focusGained(FocusEvent e)
            {
                if (area.getText().equals(ThemedTextArea.this.placeholder))
                {
                    area.setText("");
                    area.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                }
                glow().animateIn();
            }
            public void focusLost(FocusEvent e)
            {
                if (area.getText().trim().isEmpty())
                {
                    area.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                    area.setText(ThemedTextArea.this.placeholder);
                }
                glow().animateOut();
            }
        });

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                area.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
                area.setForeground(isShowingPlaceholder()
                    ? ThemeManager.getColor(ThemeColor.TEXT_MUTED)
                    : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            }
        });
    }

    private boolean isShowingPlaceholder()
    {
        return area.getText().equals(placeholder);
    }

    /** Returns the entered text, or an empty string if only the placeholder is showing. */
    public String getValue()
    {
        return isShowingPlaceholder() ? "" : area.getText().trim();
    }
}
