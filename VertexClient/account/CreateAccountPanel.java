package account;
import theme.GlowBackdrop;
import ui.GameHubDialog;
import net.NetworkManager;
import net.MessageType;
import net.Message;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedPasswordField;
import ui.ThemedTextField;

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
 * CreateAccountPanel
 * -------------------
 * Account creation form shown inside AuthWindow - same centered-card
 * treatment as the redesigned LoginPanel, for visual parity when
 * switching between the two. Sends the request to the real server via
 * NetworkManager. The server decides the permanent account ID and
 * whether this is the first-account admin bootstrap - the client just
 * displays whatever the server decided, it never assigns IDs or roles
 * itself.
 */
public class CreateAccountPanel extends JPanel
{
    private final ThemedTextField usernameField;
    private final ThemedPasswordField passwordField;
    private final ThemedPasswordField confirmField;
    private final JLabel errorLabel;
    private final ThemedButton createButton;

    public CreateAccountPanel(final LoginPanel.LoginSuccessListener successListener, final Runnable onSwitchToLogin)
    {
        setOpaque(false);
        setLayout(new java.awt.GridBagLayout());

        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(38, 40, 34, 40));
        card.setPreferredSize(new Dimension(400, 480));
        card.enableTopAccent();

        final JLabel title = new JLabel("Create your account");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);

        final JLabel subtitle = new JLabel("You'll get a permanent account ID - your username can change later.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 26, 0));
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
        card.add(Box.createVerticalStrut(16));

        card.add(fieldLabel("Confirm Password"));
        confirmField = new ThemedPasswordField();
        confirmField.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmField.setMaximumSize(new Dimension(2000, 42));
        card.add(confirmField);
        card.add(Box.createVerticalStrut(10));

        errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(10));

        createButton = new ThemedButton("Create Account", true);
        createButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        createButton.setMaximumSize(new Dimension(2000, 46));
        createButton.setPreferredSize(new Dimension(2000, 46));
        createButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attemptCreate(successListener); }
        });
        card.add(createButton);
        card.add(Box.createVerticalStrut(20));

        final JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        switchRow.setOpaque(false);
        switchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JLabel switchLabel = new JLabel("Already have an account?");
        switchLabel.setFont(UITheme.FONT_SMALL);
        switchLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        switchRow.add(switchLabel);

        javax.swing.JButton switchButton = new javax.swing.JButton("Log in");
        switchButton.setFont(UITheme.FONT_SMALL.deriveFont(java.awt.Font.BOLD));
        switchButton.setForeground(ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START));
        switchButton.setFocusPainted(false);
        switchButton.setBorderPainted(false);
        switchButton.setContentAreaFilled(false);
        switchButton.setOpaque(false);
        switchButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        switchButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { onSwitchToLogin.run(); }
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
        createButton.setEnabled(false);
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
                        createButton.setEnabled(true);

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
        // Solid base first - see LoginPanel's identical fix for why (GlowBackdrop's gradient
        // fades to fully transparent at the edges, and this panel is non-opaque).
        g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
        g2.fillRect(0, 0, getWidth(), getHeight());
        GlowBackdrop.paint(g2, getWidth(), getHeight());
        g2.dispose();
    }
}
