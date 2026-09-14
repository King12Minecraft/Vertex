package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * FusionGridWindow
 * ----------------
 * Online ranked Fusion Grid, 1v1 - click an empty cell to place your
 * current tile there. FusionGridMatch is the sole authority on
 * whether a placement is legal, what merges, and how much it scores;
 * this window just sends the click and redraws whatever board state
 * comes back.
 */
public class FusionGridWindow extends JFrame implements NetworkManager.PushListener
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private static final Color[] PLAYER_COLORS = { new Color(90, 170, 230), new Color(230, 110, 100) };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private JLabel nextTileLabel;
    private JLabel scoreLabel;

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private int[] cellValues = new int[FusionGridMatch.GRID_SIZE * FusionGridMatch.GRID_SIZE];
    private int[] cellOwners = new int[FusionGridMatch.GRID_SIZE * FusionGridMatch.GRID_SIZE];
    private int nextTileValue = 2;
    private int scoreA, scoreB;
    private boolean myTurn;
    private boolean gameOver;

    public FusionGridWindow()
    {
        super("Vertex - Fusion Grid");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));
        java.util.Arrays.fill(cellOwners, -1);

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
                NetworkManager.removePushListener(FusionGridWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 240));

        JLabel title = new JLabel("Fusion Grid");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - place tiles, merge your own, block theirs.");
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

        JLabel title = new JLabel("Fusion Grid");
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

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        top.add(statusLabel);

        nextTileLabel = new JLabel("Next tile: 2");
        nextTileLabel.setFont(UITheme.FONT_SMALL);
        nextTileLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        top.add(nextTileLabel);

        scoreLabel = new JLabel("You: 0    Opponent: 0");
        scoreLabel.setFont(UITheme.FONT_SMALL);
        scoreLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        scoreLabel.setBorder(new EmptyBorder(2, 0, 12, 0));
        top.add(scoreLabel);

        wrap.add(top, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        wrap.add(boardPanel, BorderLayout.CENTER);

        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.FUSIONGRID_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.FUSIONGRID_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void placeTile(int index)
    {
        if (gameOver || !myTurn || cellOwners[index] != -1) return;
        Message request = new Message();
        request.setType(MessageType.FUSIONGRID_PLACE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(index);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.FUSIONGRID_MATCH_FOUND || type == MessageType.FUSIONGRID_UPDATE
            || type == MessageType.FUSIONGRID_RESULT;
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

        if (type == MessageType.FUSIONGRID_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyState(message.getBoardState());
            myTurn = mySymbol == 0;
            updateStatus();
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.FUSIONGRID_UPDATE)
        {
            applyState(message.getBoardState());
            myTurn = Integer.parseInt(message.getSymbol()) == mySymbol;
            updateStatus();
        }
        else if (type == MessageType.FUSIONGRID_RESULT)
        {
            applyState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " points!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " points."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Fusion Grid match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(cellOwners, -1);
                    java.util.Arrays.fill(cellValues, 0);
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { FusionGridWindow.this.dispose(); }
            });
        }
    }

    /** "nextTileValue|owner:index:value,owner:index:value,...|scoreA,scoreB" - see FusionGridMatch.stateString(). */
    private void applyState(String state)
    {
        if (state == null) return;
        String[] parts = state.split("\\|", -1);
        if (parts.length < 3) return;

        nextTileValue = Integer.parseInt(parts[0]);
        nextTileLabel.setText("Next tile: " + nextTileValue);

        java.util.Arrays.fill(cellOwners, -1);
        java.util.Arrays.fill(cellValues, 0);
        if (!parts[1].isEmpty())
        {
            for (String entry : parts[1].split(","))
            {
                String[] fields = entry.split(":");
                if (fields.length == 3)
                {
                    int owner = Integer.parseInt(fields[0]);
                    int index = Integer.parseInt(fields[1]);
                    int value = Integer.parseInt(fields[2]);
                    cellOwners[index] = owner;
                    cellValues[index] = value;
                }
            }
        }

        String[] scoreParts = parts[2].split(",");
        if (scoreParts.length == 2)
        {
            scoreA = Integer.parseInt(scoreParts[0]);
            scoreB = Integer.parseInt(scoreParts[1]);
        }
        int myScore = mySymbol == 0 ? scoreA : scoreB;
        int theirScore = mySymbol == 0 ? scoreB : scoreA;
        scoreLabel.setText("You: " + myScore + "    Opponent: " + theirScore);

        boardPanel.repaint();
    }

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your move" : opponentLabel + "'s move...");
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 56;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * FusionGridMatch.GRID_SIZE, CELL * FusionGridMatch.GRID_SIZE));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    if (row >= 0 && row < FusionGridMatch.GRID_SIZE && col >= 0 && col < FusionGridMatch.GRID_SIZE)
                    {
                        placeTile(row * FusionGridMatch.GRID_SIZE + col);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);

            for (int row = 0; row < FusionGridMatch.GRID_SIZE; row++)
            {
                for (int col = 0; col < FusionGridMatch.GRID_SIZE; col++)
                {
                    int index = row * FusionGridMatch.GRID_SIZE + col;
                    int x = col * CELL, y = row * CELL;

                    g2.setColor(new Color(40, 42, 52));
                    g2.fillRect(x, y, CELL - 2, CELL - 2);

                    int owner = cellOwners[index];
                    if (owner != -1)
                    {
                        g2.setColor(PLAYER_COLORS[owner]);
                        g2.fillRoundRect(x + 4, y + 4, CELL - 10, CELL - 10, 8, 8);
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                        String text = String.valueOf(cellValues[index]);
                        int textWidth = g2.getFontMetrics().stringWidth(text);
                        g2.drawString(text, x + (CELL - textWidth) / 2, y + CELL / 2 + 7);
                    }
                }
            }
            g2.dispose();
        }
    }
}
