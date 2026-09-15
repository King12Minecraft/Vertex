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
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * SignalGridWindow
 * ----------------
 * Online ranked Signal Grid, 1v1 - click an empty cell to select
 * where you'll place your node, then click one of the 4 direction
 * buttons to fire it that way (both choices travel together in one
 * move). SignalGridMatch is the sole authority on what the signal
 * actually hits; this window just sends the placement+direction and
 * redraws whatever board state comes back.
 */
public class SignalGridWindow extends JFrame implements NetworkManager.PushListener
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

    private String matchId;
    private int mySymbol = -1;
    private String opponentUsername;
    private int[] owners = new int[SignalGridMatch.GRID_SIZE * SignalGridMatch.GRID_SIZE];
    private int selectedIndex = -1;
    private boolean myTurn;
    private boolean gameOver;

    public SignalGridWindow()
    {
        super("Vertex - Signal Grid");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));
        java.util.Arrays.fill(owners, -1);

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
                NetworkManager.removePushListener(SignalGridWindow.this);
            }
        });
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 240));

        JLabel title = new JLabel("Signal Grid");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - place a node, fire it, capture what it hits.");
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

        JLabel title = new JLabel("Signal Grid");
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

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        boardPanel = new BoardPanel();
        JPanel boardWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        boardWrap.setOpaque(false);
        boardWrap.add(boardPanel);
        center.add(boardWrap);
        center.add(javax.swing.Box.createVerticalStrut(12));

        JPanel directionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        directionRow.setOpaque(false);
        directionRow.add(directionButton("UP", "\u2191"));
        directionRow.add(directionButton("DOWN", "\u2193"));
        directionRow.add(directionButton("LEFT", "\u2190"));
        directionRow.add(directionButton("RIGHT", "\u2192"));
        center.add(directionRow);

        wrap.add(center, BorderLayout.CENTER);
        return wrap;
    }

    private ThemedButton directionButton(final String direction, String arrow)
    {
        ThemedButton button = new ThemedButton(arrow, false);
        button.setPreferredSize(new Dimension(56, 44));
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { fireInDirection(direction); }
        });
        return button;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SIGNALGRID_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.SIGNALGRID_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void selectCell(int index)
    {
        if (gameOver || !myTurn || owners[index] != -1) return;
        selectedIndex = index;
        statusLabel.setText("Node selected - pick a direction to fire it.");
        boardPanel.repaint();
    }

    private void fireInDirection(String direction)
    {
        if (gameOver || !myTurn || selectedIndex < 0) return;
        Message request = new Message();
        request.setType(MessageType.SIGNALGRID_FIRE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(selectedIndex);
        request.setSymbol(direction);
        NetworkManager.sendAsync(request);
        selectedIndex = -1;
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.SIGNALGRID_MATCH_FOUND || type == MessageType.SIGNALGRID_UPDATE
            || type == MessageType.SIGNALGRID_RESULT;
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

        if (type == MessageType.SIGNALGRID_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = Integer.parseInt(message.getSymbol());
            opponentUsername = message.getOpponentUsername();
            applyBoardState(message.getBoardState());
            myTurn = mySymbol == 0;
            updateStatus();
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (type == MessageType.SIGNALGRID_UPDATE)
        {
            applyBoardState(message.getBoardState());
            myTurn = Integer.parseInt(message.getSymbol()) == mySymbol;
            selectedIndex = -1;
            updateStatus();
        }
        else if (type == MessageType.SIGNALGRID_RESULT)
        {
            applyBoardState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won with " + message.getScore() + " nodes!"
                : "LOSE".equals(result) ? "You lost - " + message.getScore() + " nodes."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);
            boardPanel.repaint();

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Signal Grid match on Vertex!" : null;
            SnakeGameOverDialog.show(this, message.getScore(), text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    java.util.Arrays.fill(owners, -1);
                    cardLayout.show(cards, SEARCHING);
                    pack();
                    setLocationRelativeTo(null);
                    findMatch();
                }
                public void onClose() { SignalGridWindow.this.dispose(); }
            });
        }
    }

    private void applyBoardState(String boardState)
    {
        if (boardState == null) return;
        String[] parts = boardState.split(",");
        for (int i = 0; i < owners.length && i < parts.length; i++)
        {
            try { owners[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { }
        }
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        String opponentLabel = opponentUsername != null ? opponentUsername : "opponent";
        statusLabel.setText(myTurn ? "Your move - click an empty cell" : opponentLabel + "'s move...");
    }

    private class BoardPanel extends JPanel
    {
        private static final int CELL = 44;

        BoardPanel()
        {
            setPreferredSize(new Dimension(CELL * SignalGridMatch.GRID_SIZE, CELL * SignalGridMatch.GRID_SIZE));
            addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e)
                {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    if (row >= 0 && row < SignalGridMatch.GRID_SIZE && col >= 0 && col < SignalGridMatch.GRID_SIZE)
                    {
                        selectCell(row * SignalGridMatch.GRID_SIZE + col);
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

            for (int row = 0; row < SignalGridMatch.GRID_SIZE; row++)
            {
                for (int col = 0; col < SignalGridMatch.GRID_SIZE; col++)
                {
                    int index = row * SignalGridMatch.GRID_SIZE + col;
                    int x = col * CELL, y = row * CELL;

                    g2.setColor(index == selectedIndex ? new Color(70, 74, 50) : new Color(38, 40, 50));
                    g2.fillRect(x, y, CELL - 2, CELL - 2);

                    int owner = owners[index];
                    if (owner != -1)
                    {
                        g2.setColor(PLAYER_COLORS[owner]);
                        g2.fillOval(x + 8, y + 8, CELL - 20, CELL - 20);
                    }
                }
            }
            g2.dispose();
        }
    }
}
