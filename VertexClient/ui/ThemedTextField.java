package ui;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * ThemedTextField
 * ----------------
 * A themed single-line text input with a placeholder, wrapped in a
 * RoundedPanel so it matches the rest of the app instead of looking
 * like a default Swing text field.
 */
public class ThemedTextField extends RoundedPanel
{
    private final JTextField field;
    private final String placeholder;
    private boolean focused = false;

    public ThemedTextField(String placeholder)
    {
        super(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_BUTTON);
        this.placeholder = placeholder;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 14, 0, 14));
        setPreferredSize(new Dimension(100, 42));

        field = new JTextField();
        field.setFont(UITheme.FONT_BODY);
        field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        field.setCaretColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        field.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setOpaque(false);
        field.setText(placeholder);

        field.addFocusListener(new FocusAdapter()
        {
            public void focusGained(FocusEvent e)
            {
                if (field.getText().equals(ThemedTextField.this.placeholder))
                {
                    field.setText("");
                    field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                }
            }
            public void focusLost(FocusEvent e)
            {
                if (field.getText().isEmpty())
                {
                    field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                    field.setText(ThemedTextField.this.placeholder);
                }
            }
        });

        add(field, BorderLayout.CENTER);

        field.addFocusListener(new FocusAdapter()
        {
            public void focusGained(FocusEvent e) { focused = true; repaint(); }
            public void focusLost(FocusEvent e)  { focused = false; repaint(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                field.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
                field.setForeground(isShowingPlaceholder()
                    ? ThemeManager.getColor(ThemeColor.TEXT_MUTED)
                    : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            }
        });
    }

    private boolean isShowingPlaceholder()
    {
        return field.getText().equals(placeholder);
    }

    /** Returns the entered text, or an empty string if only the placeholder is showing. */
    public String getValue()
    {
        return isShowingPlaceholder() ? "" : field.getText().trim();
    }

    public void addActionListener(ActionListener listener)
    {
        field.addActionListener(listener);
    }

    /** Fires on every keystroke, not just Enter - for live filtering as the person types. Doesn't distinguish real edits from the placeholder text being swapped in/out on focus change, but callers filtering against getValue() (which already returns "" while the placeholder is showing) are unaffected either way. */
    public void addChangeListener(final Runnable listener)
    {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { listener.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { listener.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { listener.run(); }
        });
    }

    /** Turns typing on/off. setEnabled() on this component only affects the wrapper panel, not the JTextField inside it, so it can't be used for this. */
    public void setInputEnabled(boolean enabled)
    {
        field.setEnabled(enabled);
    }

    public void clear()
    {
        field.setText(placeholder);
        field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
    }

    /** Pre-fills real, editable text (styled like something the person typed, not the placeholder) - for cases like ConnectDialog where a sensible default should already be sitting in the field rather than requiring the person to type it from scratch. */
    public void setValue(String value)
    {
        field.setText(value);
        field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
    }

    /** The focus indicator: an accent outline (the flat redesign has no glow to show focus with). */
    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        super.paintComponent(g);
        if (focused)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
            g2.setStroke(new java.awt.BasicStroke(1.6f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, UITheme.RADIUS_BUTTON, UITheme.RADIUS_BUTTON);
            g2.dispose();
        }
    }
}
