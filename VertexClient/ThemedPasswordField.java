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
 *
 * Includes a Show/Hide toggle so what you typed can actually be
 * checked before submitting - particularly useful on the Create
 * Account and Change Password screens, where a typo isn't just
 * inconvenient, it locks you out.
 */
public class ThemedPasswordField extends RoundedPanel
{
    private final JPasswordField field;
    private final ThemedButton toggle;
    private boolean visible = false;
    private final char defaultEchoChar;

    public ThemedPasswordField()
    {
        super(ThemeColor.BG_SIDEBAR, UITheme.RADIUS_BUTTON);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 14, 0, 6));
        setPreferredSize(new Dimension(100, 42));

        field = new JPasswordField();
        field.setFont(UITheme.FONT_BODY);
        field.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        field.setCaretColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        field.setBackground(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setOpaque(false);
        defaultEchoChar = field.getEchoChar();

        add(field, BorderLayout.CENTER);

        toggle = new ThemedButton("Show", false);
        toggle.setPreferredSize(new Dimension(58, 30));
        toggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                visible = !visible;
                field.setEchoChar(visible ? (char) 0 : defaultEchoChar);
                toggle.setText(visible ? "Hide" : "Show");
            }
        });
        add(toggle, BorderLayout.EAST);

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
