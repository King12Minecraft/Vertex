package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import ui.RoundedPanel;
import ui.ThemedButton;
import theme.UITheme;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * DotsAndBoxesWindow
 * -------------------
 * Online Dots and Boxes, 1v1. Click near a line between two dots to
 * draw it - DotsAndBoxesMatch is the sole authority on which lines
 * are legal and whose turn it is; this window just forwards the
 * click (as a line index, found by proximity - see findNearestLine)
 * and redraws whatever board state comes back.
 */
public class DotsAndBoxesWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] PLAYER_COLORS = { new Color(220, 70, 70), new Color(70, 140, 220) };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private char[] lines = new char[DotsAndBoxesMatch.LINE_COUNT];
    private char[] boxOwners = new char[DotsAndBoxesMatch.BOX_COUNT];
    private boolean gameOver;

    public DotsAndBoxesWindow()
    {
        super("Vertex - Dots and Boxes");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));
        java.util.Arrays.fill(lines, '.');
        java.util.Arrays.fill(boxOwners, '.');

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

        getContentPane().add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                leaveMatch();
                NetworkManager.removePushListener(DotsAndBoxesWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(400, 240));

        JLabel title = new JLabel("Dots and Boxes");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("1v1 - complete a box's 4th side to claim it and go again.");
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
                pack();
                setLocationRelativeTo(null);
                findMatch();
            }
        });
        panel.add(playOnline);

        return panel;
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(360, 220));

        JLabel title = new JLabel("Dots and Boxes");
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
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
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

        boardPanel = new BoardPanel();
        wrap.add(boardPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.DOTS_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.DOTS_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void drawLine(int lineIndex)
    {
        if (gameOver || lines[lineIndex] != '.') return;
        Message request = new Message();
        request.setType(MessageType.DOTS_LINE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(lineIndex);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.DOTS_MATCH_FOUND || type == MessageType.DOTS_UPDATE
            || type == MessageType.DOTS_RESULT;
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

        if (type == MessageType.DOTS_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            updateStatus(0);
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.DOTS_UPDATE)
        {
            applyBoardState(message.getBoardState());
            updateStatus(Integer.parseInt(message.getSymbol()));
        }
        else if (type == MessageType.DOTS_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " boxes!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " boxes."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Dots and Boxes match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(lines, '.');
                    java.util.Arrays.fill(boxOwners, '.');
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { DotsAndBoxesWindow.this.dispose(); }
            });
        }
    }

    private void updateStatus(int turnPlayerIndex)
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(turnPlayerIndex == mySymbol ? "Your move" : opponentLabel + "'s move...");
    }

    private void applyBoardState(String boardState)
    {
        if (boardState != null && boardState.length() >= DotsAndBoxesMatch.LINE_COUNT + DotsAndBoxesMatch.BOX_COUNT)
        {
            lines = boardState.substring(0, DotsAndBoxesMatch.LINE_COUNT).toCharArray();
            boxOwners = boardState.substring(DotsAndBoxesMatch.LINE_COUNT).toCharArray();
        }
        boardPanel.repaint();
    }

    private class BoardPanel extends JPanel
    {
        private static final int SPACING = 56;
        private static final int MARGIN = 24;
        private static final int DOT_RADIUS = 5;
        private static final int CLICK_TOLERANCE = 12;

        BoardPanel()
        {
            int size = MARGIN * 2 + SPACING * DotsAndBoxesMatch.BOX_COLS;
            setPreferredSize(new Dimension(size, size));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int nearest = findNearestLine(e.getX(), e.getY());
                    if (nearest >= 0) drawLine(nearest);
                }
            });
        }

        /** Finds the closest undrawn line to a click point, within CLICK_TOLERANCE pixels - returns -1 if nothing is close enough. Checking every line each click is cheap (only 40 of them) and avoids needing to precompute/cache clickable regions. */
        private int findNearestLine(int mouseX, int mouseY)
        {
            int best = -1;
            double bestDist = CLICK_TOLERANCE;

            for (int i = 0; i < DotsAndBoxesMatch.LINE_COUNT; i++)
            {
                if (lines[i] != '.') continue;
                double dist;
                if (i < 20)
                {
                    int row = i / DotsAndBoxesMatch.BOX_COLS, col = i % DotsAndBoxesMatch.BOX_COLS;
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    int x2 = MARGIN + (col + 1) * SPACING, y2 = y1;
                    dist = distanceToSegment(mouseX, mouseY, x1, y1, x2, y2);
                }
                else
                {
                    int vIndex = i - 20;
                    int row = vIndex / (DotsAndBoxesMatch.BOX_COLS + 1), col = vIndex % (DotsAndBoxesMatch.BOX_COLS + 1);
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    int x2 = x1, y2 = MARGIN + (row + 1) * SPACING;
                    dist = distanceToSegment(mouseX, mouseY, x1, y1, x2, y2);
                }
                if (dist < bestDist)
                {
                    bestDist = dist;
                    best = i;
                }
            }
            return best;
        }

        private double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2)
        {
            double dx = x2 - x1, dy = y2 - y1;
            double lengthSq = dx * dx + dy * dy;
            double t = lengthSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
            double closestX = x1 + t * dx, closestY = y1 + t * dy;
            return Math.hypot(px - closestX, py - closestY);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int box = 0; box < DotsAndBoxesMatch.BOX_COUNT; box++)
            {
                if (boxOwners[box] == '.') continue;
                int r = box / DotsAndBoxesMatch.BOX_COLS, c = box % DotsAndBoxesMatch.BOX_COLS;
                int ownerIndex = boxOwners[box] - '0';
                Color color = (ownerIndex >= 0 && ownerIndex < PLAYER_COLORS.length) ? PLAYER_COLORS[ownerIndex] : Color.GRAY;
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
                g2.fillRect(MARGIN + c * SPACING + 2, MARGIN + r * SPACING + 2, SPACING - 4, SPACING - 4);
            }

            g2.setStroke(new java.awt.BasicStroke(4, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            for (int i = 0; i < DotsAndBoxesMatch.LINE_COUNT; i++)
            {
                if (lines[i] != 'X') continue;
                if (i < 20)
                {
                    int row = i / DotsAndBoxesMatch.BOX_COLS, col = i % DotsAndBoxesMatch.BOX_COLS;
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.drawLine(x1, y1, x1 + SPACING, y1);
                }
                else
                {
                    int vIndex = i - 20;
                    int row = vIndex / (DotsAndBoxesMatch.BOX_COLS + 1), col = vIndex % (DotsAndBoxesMatch.BOX_COLS + 1);
                    int x1 = MARGIN + col * SPACING, y1 = MARGIN + row * SPACING;
                    g2.setColor(ThemeManager.getColor(ThemeColor.ACCENT));
                    g2.drawLine(x1, y1, x1, y1 + SPACING);
                }
            }

            g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            for (int row = 0; row <= DotsAndBoxesMatch.BOX_ROWS; row++)
            {
                for (int col = 0; col <= DotsAndBoxesMatch.BOX_COLS; col++)
                {
                    int x = MARGIN + col * SPACING, y = MARGIN + row * SPACING;
                    g2.fillOval(x - DOT_RADIUS, y - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
                }
            }

            g2.dispose();
        }
    }
}
