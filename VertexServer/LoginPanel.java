import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * LoginPanel
 * ----------
 * The login form shown inside AuthWindow, redesigned as a centered
 * card (RoundedPanel with a top accent glow, the same "this is a
 * distinct surface" treatment every game/shop card in the app uses)
 * floating over AuthWindow's own glow backdrop, instead of the old
 * plain field-stack sitting directly on the window background.
 *
 * Sends credentials to the real server via NetworkManager - the
 * client never hashes or stores passwords itself, that's entirely
 * server-side.
 */
public class LoginPanel extends JPanel
{
    /** Implemented by AuthWindow to know when to open MainMenu. */
    public interface LoginSuccessListener
    {
        void onLoginSuccess(Account account, String password);
    }

    private final ThemedTextField usernameField;
    private final ThemedPasswordField passwordField;
    private final JLabel errorLabel;
    private final ThemedButton loginButton;

    public LoginPanel(final LoginSuccessListener successListener, final Runnable onSwitchToCreate)
    {
        setOpaque(false);
        setLayout(new java.awt.GridBagLayout());

        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(38, 40, 34, 40));
        card.setPreferredSize(new Dimension(380, 400));
        card.enableTopAccent();

        final JLabel title = new JLabel("Welcome back");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        final JLabel subtitle = new JLabel("This computer is shared - log in with your own account.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 28, 0));
        card.add(subtitle);

        card.add(fieldLabel("Username"));
        usernameField = new ThemedTextField("");
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernameField.setMaximumSize(new Dimension(2000, 42));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(16));

        card.add(fieldLabel("Password"));
        passwordField = new ThemedPasswordField();
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordField.setMaximumSize(new Dimension(2000, 42));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(10));

        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(10));

        loginButton = new ThemedButton("Log In", true);
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.setMaximumSize(new Dimension(2000, 46));
        loginButton.setPreferredSize(new Dimension(2000, 46));

        ActionListener loginAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attemptLogin(successListener); }
        };
        loginButton.addActionListener(loginAction);
        usernameField.addActionListener(loginAction);
        passwordField.addActionListener(loginAction);

        card.add(loginButton);
        card.add(Box.createVerticalStrut(20));

        final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        switchRow.setOpaque(false);
        switchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel switchLabel = new JLabel("New here?");
        switchLabel.setFont(UITheme.FONT_SMALL);
        switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        switchRow.add(switchLabel);

        javax.swing.JButton switchButton = new javax.swing.JButton("Create an account");
        switchButton.setFont(UITheme.FONT_SMALL.deriveFont(java.awt.Font.BOLD));
        switchButton.setForeground(ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START));
        switchButton.setFocusPainted(false);
        switchButton.setBorderPainted(false);
        switchButton.setContentAreaFilled(false);
        switchButton.setOpaque(false);
        switchButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        switchButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onSwitchToCreate.run(); }
        });
        switchRow.add(switchButton);
        card.add(switchRow);

        add(card);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
                switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            }
        });
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
        loginButton.setEnabled(false);

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
                        loginButton.setEnabled(true);

                        if (response == null)
                        {
                            errorLabel.setText("Can't reach the server - is it running?");
                        }
                        else if (response.isSuccess())
                        {
                            errorLabel.setText(" ");
                            showDailyRewardPopup(LoginPanel.this, response);
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

    /** Shared with CreateAccountPanel - the daily login reward popup, shown right after a successful login/account creation. No-op if today's reward was already claimed (dailyRewardCoins == 0). */
    static void showDailyRewardPopup(java.awt.Component anchor, Message response)
    {
        if (response.getDailyRewardCoins() > 0)
        {
            GameHubDialog.show(anchor, "Daily Login Reward",
                "+" + response.getDailyRewardCoins() + " coins! Day " + response.getLoginStreak() + " login streak.");
        }
    }
}
