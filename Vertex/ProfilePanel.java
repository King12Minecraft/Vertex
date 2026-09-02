import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.GeneralPath;
import java.util.List;

/**
 * ProfilePanel
 * ------------
 * Real account data: username, permanent account ID, role, and coins
 * come from Session/Account. Games-played/achievements stay
 * placeholders until their respective systems exist. Refreshes
 * automatically if the username changes elsewhere (Settings).
 *
 * The header is a real "hero card" - the launcher-style gradient
 * chamfered treatment established by HeroBanner, sized for a player
 * profile instead of a featured game. Every launcher (Steam, Epic,
 * Discord) treats its own profile page this way.
 */
public class ProfilePanel extends RoundedPanel
{
    private JLabel nameLabel;
    private StatusPill rolePill;
    private RoundedPanel accountIdStat;
    private RoundedPanel coinsStat;
    private HeroCard heroCard;

    public ProfilePanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setBorder(new EmptyBorder(24, 0, 0, 0));

        heroCard = new HeroCard();
        heroCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroCard.setMaximumSize(new Dimension(4000, 170));
        wrap.add(heroCard);
        wrap.add(Box.createVerticalStrut(20));

        RoundedPanel statsCard = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        statsCard.setLayout(new BorderLayout());
        statsCard.setBorder(new EmptyBorder(24, 24, 24, 24));
        statsCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsCard.add(createStatsGrid(), BorderLayout.CENTER);
        wrap.add(statsCard);
        wrap.add(Box.createVerticalStrut(16));
        wrap.add(createTransactionHistoryRow());

        add(wrap, BorderLayout.NORTH);

        Session.addListener(new Runnable()
        {
            public void run() { refreshAccountInfo(); }
        });
    }

    /** The gradient chamfered "player card" header - avatar, name, role, all on a launcher-style hero background. */
    private class HeroCard extends JPanel
    {
        HeroCard()
        {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(0, 28, 0, 28));

            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

            JLabel avatar = createAvatarCircle(74);

            JPanel nameCol = new JPanel();
            nameCol.setOpaque(false);
            nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
            nameCol.setBorder(new EmptyBorder(0, 20, 0, 0));

            nameLabel = new JLabel(currentUsername());
            nameLabel.setFont(UITheme.FONT_HEADING.deriveFont(26f));
            nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            applyNameColor();

            rolePill = new StatusPill(currentRole(), ThemeManager.getColor(ThemeColor.ACCENT));
            rolePill.setAlignmentX(Component.LEFT_ALIGNMENT);

            nameCol.add(Box.createVerticalGlue());
            nameCol.add(nameLabel);
            nameCol.add(Box.createVerticalStrut(8));
            nameCol.add(rolePill);
            nameCol.add(Box.createVerticalGlue());

            row.add(avatar);
            row.add(nameCol);

            add(row, BorderLayout.WEST);

            ThemeManager.addListener(new Runnable()
            {
                public void run() { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            int w = getWidth();
            int h = getHeight();
            int cut = 20;

            GeneralPath shape = ChamferShape.build(0, 0, w, h, cut);
            g2.setClip(shape);

            Color start = ThemeManager.getColor(ThemeColor.BG_PANEL);
            Color end = ThemeManager.getColor(ThemeColor.BG_SIDEBAR);
            LinearGradientPaint base = new LinearGradientPaint(
                0, 0, Math.max(w, 1), Math.max(h, 1), new float[] {0f, 1f}, new Color[] {start, end});
            g2.setPaint(base);
            g2.fillRect(0, 0, w, h);

            Color accent = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
            RadialGradientPaint glow = new RadialGradientPaint(
                w * 0.12f, h * 0.5f, Math.max(Math.max(w, h) * 0.6f, 1f),
                new float[] {0f, 1f},
                new Color[] {
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)
                });
            g2.setPaint(glow);
            g2.fillRect(0, 0, w, h);

            g2.setClip(null);
            g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(shape);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private JLabel createAvatarCircle(final int diameter)
    {
        JLabel avatar = new JLabel(currentUsername().substring(0, 1).toUpperCase(), SwingConstants.CENTER)
        {
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                Color start = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START);
                Color end = ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_END);
                LinearGradientPaint gradient = new LinearGradientPaint(
                    0, 0, getWidth(), getHeight(), new float[] {0f, 1f}, new Color[] {start, end});
                g2.setPaint(gradient);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatar.setPreferredSize(new Dimension(diameter, diameter));
        avatar.setMaximumSize(new Dimension(diameter, diameter));
        avatar.setFont(UITheme.FONT_HEADING.deriveFont(28f));
        avatar.setForeground(ThemeManager.getColor(ThemeColor.BG_APP));
        return avatar;
    }

    private JPanel createTransactionHistoryRow()
    {
        RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(4000, 66));

        JLabel label = new JLabel("Coin Transaction History");
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        card.add(label, BorderLayout.WEST);

        final ThemedButton view = new ThemedButton("View", false);
        view.setPreferredSize(new Dimension(90, 34));
        view.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { showTransactionHistory(view); }
        });
        card.add(view, BorderLayout.EAST);

        return card;
    }

    private void showTransactionHistory(final Component anchor)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.TRANSACTION_HISTORY_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        List<String> lines = response != null ? response.getTransactionDescriptions() : null;
                        if (lines == null || lines.isEmpty())
                        {
                            GameHubDialog.show(anchor, "Coin Transaction History", "No transactions yet.");
                        }
                        else
                        {
                            GameHubDialog.show(anchor, "Coin Transaction History",
                                "Most recent " + lines.size() + " transactions:", lines);
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private JPanel createStatsGrid()
    {
        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(false);

        accountIdStat = statCard("Account ID", currentAccountId());
        grid.add(accountIdStat);
        grid.add(statCard("Role", currentRole()));
        coinsStat = statCard("Coins", currentCoins());
        grid.add(coinsStat);
        grid.add(statCard("Games Played", "0"));
        grid.add(statCard("Achievements", "0"));

        return grid;
    }

    private RoundedPanel statCard(String label, String value)
    {
        RoundedPanel stat = new RoundedPanel(ThemeColor.BG_SIDEBAR, 10);
        stat.setLayout(new BoxLayout(stat, BoxLayout.Y_AXIS));
        stat.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel labelText = new JLabel(label);
        labelText.setFont(UITheme.FONT_SMALL);
        labelText.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        labelText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueText = new JLabel(value);
        valueText.setName("value");
        valueText.setFont(UITheme.FONT_NAV_BOLD);
        valueText.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        valueText.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueText.setBorder(new EmptyBorder(4, 0, 0, 0));

        stat.add(labelText);
        stat.add(valueText);
        return stat;
    }

    private void refreshAccountInfo()
    {
        nameLabel.setText(currentUsername());
        applyNameColor();
        updateStatValue(accountIdStat, currentAccountId());
        updateStatValue(coinsStat, currentCoins());
    }

    /** Uses the account's purchased/selected color if one is set, otherwise the theme's default text color. */
    private void applyNameColor()
    {
        Color custom = Session.isLoggedIn()
            ? PlayerColorRegistry.resolve(Session.getCurrentAccount().getPlayerColorName())
            : null;
        nameLabel.setForeground(custom != null ? custom : ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
    }

    private void updateStatValue(RoundedPanel stat, String value)
    {
        for (int i = 0; i < stat.getComponentCount(); i++)
        {
            Component c = stat.getComponent(i);
            if ("value".equals(c.getName()) && c instanceof JLabel)
            {
                ((JLabel) c).setText(value);
            }
        }
    }

    private String currentCoins()
    {
        return Session.isLoggedIn() ? String.valueOf(Session.getCurrentAccount().getCoins()) : "0";
    }

    private String currentUsername()
    {
        return Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : "Guest";
    }

    private String currentRole()
    {
        return Session.isLoggedIn() ? Session.getCurrentAccount().getRole().name() : "PLAYER";
    }

    private String currentAccountId()
    {
        return Session.isLoggedIn() ? String.format("%06d", Session.getCurrentAccount().getAccountId()) : "Not yet assigned";
    }
}
