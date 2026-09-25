package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * AirHockeyWindow
 * ---------------
 * Online ranked Air Hockey, 1v1. Move your mouse over the table to
 * move your paddle - reports are throttled to roughly the server's
 * own physics tick rate rather than firing on every pixel of mouse
 * movement. AirHockeyMatch is the sole authority on the puck/paddles/
 * score; this window just sends paddle position reports and redraws
 * whatever state comes back.
 *
 * Deliberately not perspective-flipped per player (Player A always
 * renders at the bottom of the table, Player B always at the top, on
 * both screens) - a full "always see your own paddle at the bottom"
 * flip would mean mirroring every coordinate the server sends, which
 * isn't worth the complexity for what's still a fully playable, fair
 * game either way (both players see the exact same table).
 */
public class AirHockeyWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";
    private static final long MOVE_REPORT_INTERVAL_MS = 30;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private TablePanel tablePanel;
    private JLabel statusLabel;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private boolean gameOver;
    private long lastMoveSentAt = 0;

    private double puckX, puckY, paddleAX, paddleAY, paddleBX, paddleBY;
    private int scoreA, scoreB;

    public AirHockeyWindow()
    {
        setLayout(new BorderLayout());

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);

        NetworkManager.addPushListener(this);
    }

    @Override
    public boolean requestLeave()
    {
        leaveMatch();
        NetworkManager.removePushListener(this);
        return true;
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Air Hockey");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - move your mouse to steer your paddle.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        ThemedButton playOnline = new ThemedButton("Find Match", true);
        playOnline.setAlignmentX(Component.LEFT_ALIGNMENT);
        playOnline.setMaximumSize(new Dimension(2000, 42));
        playOnline.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                cardLayout.show(cards, SEARCHING);
                findMatch();
            }
        });
        panel.add(playOnline);

        return wrapper;
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(360, 220));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Air Hockey");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Looking for an opponent...");
        searchingLabel.setFont(UITheme.FONT_SUBHEAD);
        searchingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        searchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchingLabel.setBorder(new EmptyBorder(10, 0, 30, 0));
        panel.add(searchingLabel);

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                leaveMatch();
                MainMenu.getInstance().returnToGames();
            }
        });
        panel.add(cancel);

        return wrapper;
    }

    private JPanel createBoardScreen()
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        wrap.setBorder(new EmptyBorder(20, 20, 20, 20));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
        wrap.add(statusLabel, BorderLayout.NORTH);

        tablePanel = new TablePanel();
        JPanel tableCenterer = new JPanel(new GridBagLayout());
        tableCenterer.setOpaque(false);
        tableCenterer.add(tablePanel, new GridBagConstraints());
        wrap.add(tableCenterer, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.AIRHOCKEY_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.AIRHOCKEY_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void reportPaddlePosition(double x, double y)
    {
        long now = System.currentTimeMillis();
        if (now - lastMoveSentAt < MOVE_REPORT_INTERVAL_MS) return;
        lastMoveSentAt = now;

        Message request = new Message();
        request.setType(MessageType.AIRHOCKEY_MOVE_REQUEST);
        request.setMatchId(matchId);
        request.setChatText(x + "," + y);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.AIRHOCKEY_MATCH_FOUND || type == MessageType.AIRHOCKEY_UPDATE
            || type == MessageType.AIRHOCKEY_RESULT;
        if (!isType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleServerMessage(message); }
        });
    }

    private void handleServerMessage(Message message)
    {
        MessageType type = message.getType();

        if (type == MessageType.AIRHOCKEY_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            statusLabel.setText("You are " + ("A".equals(mySymbol) ? "bottom" : "top") + " - " + opponentUsername + " is opposite");
            cardLayout.show(cards, BOARD);
        }
        else if (type == MessageType.AIRHOCKEY_UPDATE)
        {
            applyState(message.getBoardState());
            statusLabel.setText("You: " + ("A".equals(mySymbol) ? scoreA : scoreB)
                + "   Opponent: " + ("A".equals(mySymbol) ? scoreB : scoreA));
        }
        else if (type == MessageType.AIRHOCKEY_RESULT)
        {
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won!"
                : "LOSE".equals(result) ? "You lost."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won an Air Hockey match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    cardLayout.show(cards, SEARCHING);
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
        }
    }

    private void applyState(String state)
    {
        if (state == null) return;
        try
        {
            String[] parts = state.split(",");
            puckX = Double.parseDouble(parts[0]);
            puckY = Double.parseDouble(parts[1]);
            paddleAX = Double.parseDouble(parts[2]);
            paddleAY = Double.parseDouble(parts[3]);
            paddleBX = Double.parseDouble(parts[4]);
            paddleBY = Double.parseDouble(parts[5]);
            scoreA = Integer.parseInt(parts[6]);
            scoreB = Integer.parseInt(parts[7]);
        }
        catch (Exception ignored) { }
        tablePanel.repaint();
    }

    private class TablePanel extends JPanel
    {
        TablePanel()
        {
            setPreferredSize(new Dimension((int) AirHockeyMatch.TABLE_WIDTH, (int) AirHockeyMatch.TABLE_HEIGHT));
            addMouseMotionListener(new MouseMotionAdapter()
            {
                public void mouseMoved(MouseEvent e) { reportPosition(e); }
                public void mouseDragged(MouseEvent e) { reportPosition(e); }
            });
        }

        private void reportPosition(MouseEvent e)
        {
            if (gameOver || matchId == null) return;
            reportPaddlePosition(e.getX(), e.getY());
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            g2.setColor(new Color(20, 30, 45));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(60, 75, 100));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
            g2.drawOval(getWidth() / 2 - 40, getHeight() / 2 - 40, 80, 80);

            int goalHalf = (int) AirHockeyMatch.GOAL_HALF_WIDTH;
            g2.setColor(new Color(230, 90, 90));
            g2.fillRect(getWidth() / 2 - goalHalf, 0, goalHalf * 2, 6);
            g2.setColor(new Color(90, 170, 230));
            g2.fillRect(getWidth() / 2 - goalHalf, getHeight() - 6, goalHalf * 2, 6);

            drawCircle(g2, paddleBX, paddleBY, AirHockeyMatch.PADDLE_RADIUS, new Color(90, 170, 230));
            drawCircle(g2, paddleAX, paddleAY, AirHockeyMatch.PADDLE_RADIUS, new Color(230, 90, 90));
            drawCircle(g2, puckX, puckY, AirHockeyMatch.PUCK_RADIUS, Color.WHITE);

            g2.dispose();
        }

        private void drawCircle(Graphics2D g2, double x, double y, double radius, Color color)
        {
            g2.setColor(color);
            g2.fillOval((int) (x - radius), (int) (y - radius), (int) (radius * 2), (int) (radius * 2));
        }
    }
}
