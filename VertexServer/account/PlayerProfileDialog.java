package account;
import economy.AvatarFrameRegistry;
import games.GameManager;
import games.GameInfo;
import economy.AchievementDefinitions;
import economy.PlayerColorRegistry;
import economy.AvatarCache;
import net.NetworkManager;
import net.MessageType;
import ui.ThemedButton;
import ui.ThemedScrollBarUI;
import theme.UITheme;
import theme.ThemeManager;
import theme.ThemeColor;
import ui.RoundedPanel;
import ui.DialogUtils;
import net.Message;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.util.List;

/**
 * PlayerProfileDialog
 * --------------------
 * Read-only view of another account: avatar (with their equipped
 * frame animation), username in their role/cosmetic color, badge,
 * ratings across every rated game they've played, and unlocked
 * achievements. No coin balance shown - that's private, this is a
 * public-facing view, same distinction handlePlayerProfile's own
 * javadoc makes server-side.
 */
public class PlayerProfileDialog
{
    public static void show(Component anchor, String username)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        DialogUtils.enableEscapeToClose(dialog);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(380, 480));
        root.enableTopAccent();
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        final JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(22, 22, 10, 22));

        JLabel loading = new JLabel("Loading profile...");
        loading.setFont(UITheme.FONT_BODY);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        body.add(loading);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(6, 22, 16, 22));
        ThemedButton close = new ThemedButton("Close", true);
        close.setPreferredSize(new Dimension(90, 36));
        close.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent e) { dialog.dispose(); }
        });
        buttonRow.add(close);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);

        fetchInBackground(username, body, dialog);
        dialog.setVisible(true);
    }

    private static void fetchInBackground(final String username, final JPanel body, final JDialog dialog)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.PLAYER_PROFILE_REQUEST);
                request.setUsername(username);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { render(response, body, dialog); }
                });
            }
        });
        worker.start();
    }

    private static void render(Message response, JPanel body, JDialog dialog)
    {
        body.removeAll();

        if (response == null || !response.isSuccess())
        {
            JLabel error = new JLabel(response != null && response.getErrorText() != null
                ? response.getErrorText() : "Could not reach the server.");
            error.setFont(UITheme.FONT_BODY);
            error.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            body.add(error);
            body.revalidate();
            body.repaint();
            dialog.pack();
            return;
        }

        final String username = response.getUsername();
        final String frameId = response.getItemId();

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        final ProfileAvatar avatar = new ProfileAvatar();
        AvatarCache.get(username, new AvatarCache.Listener()
        {
            public void onLoaded(Image image) { avatar.setImage(image); }
        });
        avatar.setFrameId(frameId);
        header.add(avatar, BorderLayout.WEST);

        JPanel nameCol = new JPanel();
        nameCol.setOpaque(false);
        nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));

        Color roleColor = "ADMIN".equals(response.getSenderRole()) ? new Color(230, 90, 90)
            : "MODERATOR".equals(response.getSenderRole()) ? new Color(90, 170, 230) : null;
        Color customColor = PlayerColorRegistry.resolve(response.getSenderColorId());
        String badgeGlyph = PlayerColorRegistry.resolveBadgeGlyph(response.getSenderBadgeId());

        JLabel name = new JLabel((badgeGlyph != null ? badgeGlyph + " " : "") + username);
        name.setFont(UITheme.FONT_HEADING.deriveFont(20f));
        name.setForeground(roleColor != null ? roleColor
            : customColor != null ? customColor : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameCol.add(name);

        if (response.getSenderRole() != null && !"PLAYER".equals(response.getSenderRole()))
        {
            JLabel roleLabel = new JLabel(response.getSenderRole());
            roleLabel.setFont(UITheme.FONT_SMALL);
            roleLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            nameCol.add(roleLabel);
        }

        header.add(nameCol, BorderLayout.CENTER);
        body.add(header);
        body.add(Box.createVerticalStrut(20));

        body.add(sectionLabel("RATINGS"));
        List<String> ratings = response.getSyncRatings();
        if (ratings == null || ratings.isEmpty())
        {
            body.add(mutedLine("No rated games played yet."));
        }
        else
        {
            for (int i = 0; i < ratings.size(); i++)
            {
                String[] parts = ratings.get(i).split(":", -1);
                if (parts.length < 2) continue;
                body.add(kvLine(gameDisplayName(parts[0]), parts[1]));
            }
        }
        body.add(Box.createVerticalStrut(18));

        body.add(sectionLabel("ACHIEVEMENTS"));
        List<String> unlocked = response.getUnlockedAchievementIds();
        if (unlocked == null || unlocked.isEmpty())
        {
            body.add(mutedLine("No achievements unlocked yet."));
        }
        else
        {
            for (int i = 0; i < unlocked.size(); i++)
            {
                String achName = AchievementDefinitions.findName(unlocked.get(i));
                body.add(mutedLine("\u2605 " + (achName != null ? achName : unlocked.get(i))));
            }
        }

        body.revalidate();
        body.repaint();
        dialog.pack();
    }

    private static String gameDisplayName(String gameId)
    {
        GameInfo info = GameManager.findCachedGame(gameId);
        return info != null ? info.getName() : gameId;
    }

    private static JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    private static JLabel mutedLine(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_BODY);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static JPanel kvLine(String label, String value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 24));

        JLabel left = new JLabel(label);
        left.setFont(UITheme.FONT_BODY);
        left.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(left, BorderLayout.WEST);

        JLabel right = new JLabel(value);
        right.setFont(UITheme.FONT_NAV_BOLD);
        right.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private static class ProfileAvatar extends JPanel
    {
        private Image image;
        private String frameId;
        private final Runnable animationTick = new Runnable()
        {
            public void run() { repaint(); }
        };

        ProfileAvatar()
        {
            setPreferredSize(new Dimension(64, 64));
            setOpaque(false);
            AvatarFrameRegistry.addAnimationListener(animationTick);
        }

        void setImage(Image image) { this.image = image; repaint(); }
        void setFrameId(String frameId) { this.frameId = frameId; repaint(); }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
            g2.fillOval(0, 0, getWidth(), getHeight());
            if (image != null)
            {
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
                g2.setClip(null);
            }
            AvatarFrameRegistry.paintFrame(g2, 0, 0, getWidth(), frameId);
            g2.dispose();
        }
    }
}
