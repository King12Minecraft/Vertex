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
 * LoginPanel
 * ----------
 * The login form shown inside AuthWindow. Sends credentials to the real
 * server (Phase 5) via NetworkManager - the client no longer hashes or
 * stores passwords itself at all, that's entirely server-side now
 * (Section 10/33).
 */
public class LoginPanel extends RoundedPanel
{
    /** Implemented by AuthWindow to know when to open MainMenu. */
    public interface LoginSuccessListener
    {
        void onLoginSuccess(Account account);
    }

    private final ThemedTextField usernameField;
    private final ThemedPasswordField passwordField;
    private final JLabel errorLabel;

    public LoginPanel(final LoginSuccessListener successListener, final Runnable onSwitchToCreate)
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(60, 80, 60, 80));

        final JLabel title = new JLabel("Log In");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);

        final JLabel subtitle = new JLabel("This computer is shared - log in with your own account.");
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
        add(Box.createVerticalStrut(10));

        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(errorLabel);
        add(Box.createVerticalStrut(10));

        final ThemedButton loginButton = new ThemedButton("Log In", true);
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(2000, 44));
        loginButton.setPreferredSize(new Dimension(200, 44));

        ActionListener loginAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attemptLogin(successListener); }
        };
        loginButton.addActionListener(loginAction);
        usernameField.addActionListener(loginAction);
        passwordField.addActionListener(loginAction);

        add(loginButton);
        add(Box.createVerticalStrut(16));

        final JPanel switchRow = new JPanel();
        switchRow.setOpaque(false);
        switchRow.setLayout(new BoxLayout(switchRow, BoxLayout.X_AXIS));
        switchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel switchLabel = new JLabel("No account yet?");
        switchLabel.setFont(UITheme.FONT_BODY);
        switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));

        ThemedButton switchButton = new ThemedButton("Create Account", false);
        switchButton.setPreferredSize(new Dimension(160, 34));
        switchButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onSwitchToCreate.run(); }
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

    private void attemptLogin(final LoginSuccessListener successListener)
    {
        final String username = usernameField.getValue();
        final String password = passwordField.getValue();

        if (username.isEmpty() || password.isEmpty())
        {
            errorLabel.setText("Enter a username and password.");
            return;
        }

        errorLabel.setText("Connecting...");

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.LOGIN_REQUEST);
                request.setUsername(username);
                request.setPassword(password);

                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null)
                        {
                            errorLabel.setText("Can't reach the server - is it running?");
                        }
                        else if (response.isSuccess())
                        {
                            errorLabel.setText(" ");
                            showDailyRewardPopup(LoginPanel.this, response);
                            successListener.onLoginSuccess(response.getAccount());
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

    /** Shared with CreateAccountPanel - Phase 11 daily login reward, shown as a small popup right after a successful login/account creation. No-op if today's reward was already claimed (dailyRewardCoins == 0). */
    static void showDailyRewardPopup(java.awt.Component anchor, Message response)
    {
        if (response.getDailyRewardCoins() > 0)
        {
            GameHubDialog.show(anchor, "Daily Login Reward",
                "+" + response.getDailyRewardCoins() + " coins! Day " + response.getLoginStreak() + " login streak.");
        }
    }
}
