import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * CreateAccountPanel
 * -------------------
 * Account creation form shown inside AuthWindow. Sends the request to
 * the real server (Phase 5) via NetworkManager. The server decides the
 * permanent account ID and whether this is the first-account admin
 * bootstrap (Section 11/33) - the client just displays whatever the
 * server decided, it never assigns IDs or roles itself.
 */
public class CreateAccountPanel extends RoundedPanel
{
    private final ThemedTextField usernameField;
    private final ThemedPasswordField passwordField;
    private final ThemedPasswordField confirmField;
    private final JLabel errorLabel;

    public CreateAccountPanel(final LoginPanel.LoginSuccessListener successListener, final Runnable onSwitchToLogin)
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(60, 80, 60, 80));

        final JLabel title = new JLabel("Create Account");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);

        final JLabel subtitle = new JLabel("You'll get a permanent account ID - your username can change later.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        add(subtitle);

        add(fieldLabel("Username"));
        usernameField = new ThemedTextField("");
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(2000, 42));
        add(usernameField);
        add(Box.createVerticalStrut(16));

        add(fieldLabel("Password"));
        passwordField = new ThemedPasswordField();
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(2000, 42));
        add(passwordField);
        add(Box.createVerticalStrut(16));

        add(fieldLabel("Confirm Password"));
        confirmField = new ThemedPasswordField();
        confirmField.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmField.setMaximumSize(new Dimension(2000, 42));
        add(confirmField);
        add(Box.createVerticalStrut(10));

        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(errorLabel);
        add(Box.createVerticalStrut(10));

        final ThemedButton createButton = new ThemedButton("Create Account", true);
        createButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        createButton.setMaximumSize(new Dimension(2000, 44));
        createButton.setPreferredSize(new Dimension(200, 44));
        createButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attemptCreate(successListener); }
        });
        add(createButton);
        add(Box.createVerticalStrut(16));

        final JPanel switchRow = new JPanel();
        switchRow.setOpaque(false);
        switchRow.setLayout(new BoxLayout(switchRow, BoxLayout.X_AXIS));
        switchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel switchLabel = new JLabel("Already have an account?");
        switchLabel.setFont(UITheme.FONT_BODY);
        switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));

        ThemedButton switchButton = new ThemedButton("Log In", false);
        switchButton.setPreferredSize(new Dimension(120, 34));
        switchButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onSwitchToLogin.run(); }
        });

        switchRow.add(switchLabel);
        switchRow.add(Box.createHorizontalStrut(10));
        switchRow.add(switchButton);
        add(switchRow);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
                switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            }
        });
    }

    private JLabel fieldLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }

    private void attemptCreate(final LoginPanel.LoginSuccessListener successListener)
    {
        final String username = usernameField.getValue();
        final String password = passwordField.getValue();
        String confirm = confirmField.getValue();

        if (username.isEmpty() || username.length() < 3)
        {
            errorLabel.setText("Username must be at least 3 characters.");
            return;
        }
        if (password.length() < 6)
        {
            errorLabel.setText("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirm))
        {
            errorLabel.setText("Passwords don't match.");
            return;
        }

        errorLabel.setText("Connecting...");
        final CreateAccountPanel self = this;

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CREATE_ACCOUNT_REQUEST);
                request.setUsername(username);
                request.setPassword(password);

                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null)
                        {
                            errorLabel.setText("<html>Can't create an account while offline - the server needs to "
                                + "check the username and assign your account. You can still play Snake "
                                + "offline from the login screen; your score and coins will sync once you're "
                                + "back online and logged in.</html>");
                        }
                        else if (response.isSuccess())
                        {
                            errorLabel.setText(" ");
                            if (response.isBootstrapAdmin())
                            {
                                GameHubDialog.show(self, "Welcome, Administrator",
                                    "You created this account from the server's own computer, "
                                    + "so you've been made the platform Administrator.");
                            }
                            LoginPanel.showDailyRewardPopup(self, response);
                            successListener.onLoginSuccess(response.getAccount(), password);
                        }
                        else
                        {
                            errorLabel.setText(response.getErrorText());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g)
    {
        super.paintComponent(g);
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);
        GlowBackdrop.paint(g2, getWidth(), getHeight());
        g2.dispose();
    }
}
