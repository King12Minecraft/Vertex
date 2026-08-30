import javax.swing.BorderFactory;
import javax.swing.JPasswordField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * ThemedPasswordField
 * --------------------
 * A themed masked password input, styled like ThemedTextField but using
 * JPasswordField. No in-field placeholder trick - a label above it in
 * the form is clearer for password fields than placeholder text that
 * would just be masked anyway.
 */
public class ThemedPasswordField extends RoundedPanel
{
    private final JPasswordField field;

    public ThemedPasswordField()
    {
        super(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_BUTTON);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 14, 0, 14));
        setPreferredSize(new Dimension(100, 42));

        field = new JPasswordField();
        field.setFont(UITheme.FONT_BODY);
        field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        field.setCaretColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        field.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setOpaque(false);

        add(field, BorderLayout.CENTER);

        field.addFocusListener(new FocusAdapter()
        {
            public void focusGained(FocusEvent e) { glow().animateIn(); }
            public void focusLost(FocusEvent e)  { glow().animateOut(); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                field.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
                field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            }
        });
    }

    public String getValue()
    {
        return new String(field.getPassword());
    }

    public void addActionListener(ActionListener listener)
    {
        field.addActionListener(listener);
    }

    public void clear()
    {
        field.setText("");
    }
}
