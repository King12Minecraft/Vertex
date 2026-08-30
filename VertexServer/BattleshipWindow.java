import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;

/**
 * BattleshipWindow
 * ----------------
 * Two ways to play: vs Player (1v1 online, server-authoritative - see
 * BattleshipMatch) or vs AI (fully local, uses BattleshipAI). Both
 * fleets are auto-placed randomly - no manual placement UI, keeping
 * this focused on the actual hunt-and-sink gameplay. Two 10x10 grids:
 * "My Fleet" (ships visible, shows where the opponent has hit you) and
 * "Enemy Waters" (clickable, shows only your own shot results).
 */
public class BattleshipWindow extends JFrame implements NetworkManager.PushListener
{
    private static final int SIZE = 10;
    private static final int[] SHIP_LENGTHS = { 5, 4, 3, 3, 2 };

    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Random random = new Random();

    private JLabel searchingLabel;
    private JLabel statusLabel;
    private final JButton[] myGridCells = new JButton[SIZE * SIZE];
    private final JButton[] enemyGridCells = new JButton[SIZE * SIZE];

    private boolean vsAi;
    private String matchId;
    private String opponentUsername;
    private boolean myTurn;
    private boolean isSpectator;
    private String spectatorPlayerA;
    private String spectatorPlayerB;

    // vs-AI local state
    private int[] myFleet;
    private int[] aiFleet;
    private int[] myShipHits;
    private int[] aiShipHits;
    private BattleshipAI ai;

    public BattleshipWindow()
    {
        this(null, null, null, null);
    }

    /** Watches an in-progress match without playing - unlike Chess/RPS, a Battleship spectator needs BOTH fleets revealed from the start (hidden information is the whole point for the players, not for someone just watching), so this reuses myGridCells/enemyGridCells as a fixed "Player A" / "Player B" pair instead of the player's relative "mine/enemy" framing. */
    public static BattleshipWindow forSpectating(String spectateMatchId, String playerAName, String playerBName)
    {
        return new BattleshipWindow(spectateMatchId, playerAName, playerBName, null);
    }

    /** Waits for the BATTLESHIP_MATCH_FOUND the server sends once a rematch is accepted - no queue, no spectating. */
    public static BattleshipWindow forRematchWait(String opponentUsername)
    {
        return new BattleshipWindow(null, null, null, opponentUsername);
    }

    private BattleshipWindow(String spectateMatchId, String playerAName, String playerBName, String rematchWaitOpponent)
    {
        super(spectateMatchId != null ? "Vertex - Battleship (Spectating)" : "Vertex - Battleship");
        isSpectator = spectateMatchId != null;
        boolean isRematchWait = rematchWaitOpponent != null;
        spectatorPlayerA = playerAName;
        spectatorPlayerB = playerBName;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

        getContentPane().add(cards, BorderLayout.CENTER);

        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        NetworkManager.addPushListener(this);

        if (isSpectator)
        {
            matchId = spectateMatchId;
            statusLabel.setText("Watching " + playerAName + " vs " + playerBName + "...");
            cardLayout.show(cards, BOARD);

            Message request = new Message();
            request.setType(MessageType.SPECTATE_REQUEST);
            request.setGameId("battleship");
            request.setMatchId(spectateMatchId);
            NetworkManager.sendAsync(request);
        }
        else if (isRematchWait)
        {
            statusLabel.setText("Waiting for " + rematchWaitOpponent + " to accept...");
            cardLayout.show(cards, BOARD);
        }
        else
        {
            cardLayout.show(cards, MODE_SELECT);
        }

        pack();
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                if (!isSpectator && !vsAi && matchId == null)
                {
                    leaveQueue();
                }
                NetworkManager.removePushListener(BattleshipWindow.this);
            }
        });
    }

    // ==================== Mode select ====================

    private JPanel createModeSelectScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(460, 320));

        JLabel title = new JLabel("Battleship");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Fleets are placed automatically - choose an opponent.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        JPanel tileRow = new JPanel(new GridLayout(1, 2, 16, 0));
        tileRow.setOpaque(false);
        tileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileRow.setMaximumSize(new Dimension(2000, 150));

        String lastMode = LastGameModeStore.getLastMode("battleship");

        tileRow.add(new GameModeCard("vs Player", "Matched with a real opponent, one game.",
            ThemeManager.getColor(ThemeColor.ACCENT_GRADIENT_START), "vsPlayer".equals(lastMode), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(false); }
            }));

        tileRow.add(new GameModeCard("vs AI", "Play against the computer - works fully offline.",
            ThemeManager.getColor(ThemeColor.TEXT_MUTED), "vsAi".equals(lastMode), new GameModeCard.ClickListener()
            {
                public void onClick() { chooseMode(true); }
            }));

        panel.add(tileRow);
        return panel;
    }

    private void chooseMode(boolean aiMode)
    {
        vsAi = aiMode;
        LastGameModeStore.setLastMode("battleship", aiMode ? "vsAi" : "vsPlayer");
        if (aiMode)
        {
            startVsAi();
        }
        else
        {
            cardLayout.show(cards, SEARCHING);
            pack();
            setLocationRelativeTo(null);
            findMatch();
        }
    }

    // ==================== Searching (vs Player) ====================

    private JPanel createSearchingScreen()
    {
        RoundedPanel panel = new RoundedPanel(ThemeColor.BG_APP, 0);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(380, 240));

        JLabel title = new JLabel("Battleship");
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
                leaveQueue();
                dispose();
            }
        });
        panel.add(cancel);

        return panel;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.BATTLESHIP_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.BATTLESHIP_LEAVE_QUEUE_REQUEST);
        NetworkManager.sendAsync(request);
    }

    // ==================== Board ====================

    private JPanel createBoardScreen()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        statusLabel = new JLabel(isSpectator ? "Watching..." : "Your turn - fire at Enemy Waters");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        statusLabel.setBorder(new EmptyBorder(0, 4, 12, 4));
        panel.add(statusLabel, BorderLayout.NORTH);

        JPanel gridsRow = new JPanel(new GridLayout(1, 2, 20, 0));
        gridsRow.setOpaque(false);

        String leftLabel = isSpectator ? "Player A's Fleet" : "My Fleet";
        String rightLabel = isSpectator ? "Player B's Fleet" : "Enemy Waters";
        gridsRow.add(buildGridColumn(leftLabel, myGridCells, false));
        gridsRow.add(buildGridColumn(rightLabel, enemyGridCells, !isSpectator));

        panel.add(gridsRow, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGridColumn(String title, final JButton[] cells, final boolean clickable)
    {
        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);

        JLabel label = new JLabel(title);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        column.add(label, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(SIZE, SIZE, 1, 1));
        grid.setPreferredSize(new Dimension(300, 300));
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            final int cellIndex = i;
            JButton cell = new JButton();
            cell.setFont(new Font("SansSerif", Font.BOLD, 12));
            cell.setFocusPainted(false);
            cell.setBorderPainted(false);
            cell.setBackground(new Color(60, 100, 150));
            if (clickable)
            {
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cell.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e) { handleFireClick(cellIndex); }
                });
            }
            cells[i] = cell;
            grid.add(cell);
        }
        column.add(grid, BorderLayout.CENTER);
        return column;
    }

    // ==================== vs AI (fully local) ====================

    private void startVsAi()
    {
        myFleet = new int[SIZE * SIZE];
        aiFleet = new int[SIZE * SIZE];
        myShipHits = new int[SHIP_LENGTHS.length];
        aiShipHits = new int[SHIP_LENGTHS.length];
        ai = new BattleshipAI();
        opponentUsername = "the computer";
        myTurn = true;

        placeFleetLocally(myFleet);
        placeFleetLocally(aiFleet);

        renderMyFleetGrid();
        clearEnemyGrid();
        statusLabel.setText("Your turn - fire at Enemy Waters");

        cardLayout.show(cards, BOARD);
        pack();
        setLocationRelativeTo(null);
    }

    private void placeFleetLocally(int[] fleet)
    {
        for (int i = 0; i < fleet.length; i++)
        {
            fleet[i] = -1;
        }
        for (int shipIndex = 0; shipIndex < SHIP_LENGTHS.length; shipIndex++)
        {
            placeShipLocally(fleet, shipIndex);
        }
    }

    private void placeShipLocally(int[] fleet, int shipIndex)
    {
        int length = SHIP_LENGTHS[shipIndex];
        while (true)
        {
            boolean horizontal = random.nextBoolean();
            int row = random.nextInt(SIZE);
            int col = random.nextInt(SIZE);

            if (horizontal && col + length > SIZE) continue;
            if (!horizontal && row + length > SIZE) continue;

            boolean fits = true;
            for (int i = 0; i < length; i++)
            {
                int r = horizontal ? row : row + i;
                int c = horizontal ? col + i : col;
                if (fleet[r * SIZE + c] != -1) { fits = false; break; }
            }
            if (!fits) continue;

            for (int i = 0; i < length; i++)
            {
                int r = horizontal ? row : row + i;
                int c = horizontal ? col + i : col;
                fleet[r * SIZE + c] = shipIndex;
            }
            return;
        }
    }

    private void handleFireClick(int cellIndex)
    {
        if (!myTurn)
        {
            return;
        }

        if (vsAi)
        {
            fireVsAi(cellIndex);
        }
        else
        {
            fireOnline(cellIndex);
        }
    }

    private void fireVsAi(int cellIndex)
    {
        if (enemyGridCells[cellIndex].getText().length() > 0)
        {
            return;
        }

        int shipIndex = aiFleet[cellIndex];
        String result;
        if (shipIndex == -1)
        {
            result = "MISS";
        }
        else
        {
            aiShipHits[shipIndex]++;
            result = aiShipHits[shipIndex] >= SHIP_LENGTHS[shipIndex] ? "SUNK" : "HIT";
        }
        markCell(enemyGridCells[cellIndex], result);

        if (allSunk(aiShipHits))
        {
            recordPlayed();
            GameHubDialog.show(this, "Battleship", "You sank the enemy fleet - you win!");
            dispose();
            return;
        }

        if ("MISS".equals(result))
        {
            myTurn = false;
            statusLabel.setText("Miss - the computer's turn...");
            javax.swing.Timer delay = new javax.swing.Timer(600, new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { aiTurn(); }
            });
            delay.setRepeats(false);
            delay.start();
        }
        else
        {
            statusLabel.setText(result + "! Fire again.");
        }
    }

    private void aiTurn()
    {
        while (true)
        {
            int cellIndex = ai.chooseNextShot();
            int shipIndex = myFleet[cellIndex];
            String result;
            if (shipIndex == -1)
            {
                result = "MISS";
            }
            else
            {
                myShipHits[shipIndex]++;
                result = myShipHits[shipIndex] >= SHIP_LENGTHS[shipIndex] ? "SUNK" : "HIT";
            }
            ai.reportResult(cellIndex, result);
            markCell(myGridCells[cellIndex], result);

            if (allSunk(myShipHits))
            {
                recordPlayed();
                GameHubDialog.show(this, "Battleship", "The computer sank your fleet - you lose.");
                dispose();
                return;
            }

            if ("MISS".equals(result))
            {
                myTurn = true;
                statusLabel.setText("Your turn - fire at Enemy Waters");
                return;
            }
        }
    }

    private boolean allSunk(int[] hits)
    {
        for (int i = 0; i < SHIP_LENGTHS.length; i++)
        {
            if (hits[i] < SHIP_LENGTHS[i])
            {
                return false;
            }
        }
        return true;
    }

    private void renderMyFleetGrid()
    {
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            myGridCells[i].setBackground(myFleet[i] == -1 ? new Color(60, 100, 150) : new Color(150, 150, 150));
            myGridCells[i].setText("");
        }
    }

    private void clearEnemyGrid()
    {
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            enemyGridCells[i].setBackground(new Color(60, 100, 150));
            enemyGridCells[i].setText("");
        }
    }

    private void markCell(JButton cell, String result)
    {
        if ("MISS".equals(result))
        {
            cell.setBackground(new Color(40, 70, 110));
            cell.setForeground(Color.WHITE);
            cell.setText("\u2022");
        }
        else
        {
            cell.setBackground(new Color(210, 80, 70));
            cell.setForeground(Color.WHITE);
            cell.setText("X");
        }
    }

    // ==================== vs Player (online) ====================

    private void fireOnline(int cellIndex)
    {
        if (enemyGridCells[cellIndex].getText().length() > 0)
        {
            return;
        }
        Message request = new Message();
        request.setType(MessageType.BATTLESHIP_FIRE_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(cellIndex);
        NetworkManager.sendAsync(request);
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isBattleshipType = type == MessageType.BATTLESHIP_MATCH_FOUND || type == MessageType.BATTLESHIP_FIRE_RESULT
            || type == MessageType.BATTLESHIP_MATCH_OVER || type == MessageType.SPECTATE_ENDED;
        if (!isBattleshipType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleBattleshipMessage(message); }
        });
    }

    private void handleBattleshipMessage(Message message)
    {
        if (message.getType() == MessageType.BATTLESHIP_MATCH_FOUND)
        {
            String symbol = message.getSymbol();
            if ("SPECTATOR_A".equals(symbol) || "SPECTATOR_B".equals(symbol))
            {
                JButton[] targetGrid = "SPECTATOR_A".equals(symbol) ? myGridCells : enemyGridCells;
                String fleetLayout = message.getBoardState();
                for (int i = 0; i < SIZE * SIZE; i++)
                {
                    targetGrid[i].setText("");
                    targetGrid[i].setBackground(fleetLayout.charAt(i) == '.' ? new Color(60, 100, 150) : new Color(150, 150, 150));
                }
                cardLayout.show(cards, BOARD);
                pack();
                setLocationRelativeTo(null);
                return;
            }

            matchId = message.getMatchId();
            opponentUsername = message.getOpponentUsername();
            myTurn = "MINE".equals(message.getSymbol());

            String fleetLayout = message.getBoardState();
            for (int i = 0; i < SIZE * SIZE; i++)
            {
                myGridCells[i].setText("");
                myGridCells[i].setBackground(fleetLayout.charAt(i) == '.' ? new Color(60, 100, 150) : new Color(150, 150, 150));
            }
            clearEnemyGrid();

            statusLabel.setText(myTurn ? "Your turn - fire at Enemy Waters" : "Waiting for " + opponentUsername + "...");
            cardLayout.show(cards, BOARD);
            pack();
            setLocationRelativeTo(null);
        }
        else if (message.getType() == MessageType.BATTLESHIP_FIRE_RESULT)
        {
            int cellIndex = message.getCellIndex();
            String result = message.getBattleshipResult();

            if (isSpectator)
            {
                boolean shotByPlayerA = message.getUsername().equalsIgnoreCase(spectatorPlayerA);
                // cellIndex is always on the TARGET's grid, i.e. the fleet opposite whoever fired.
                markCell(shotByPlayerA ? enemyGridCells[cellIndex] : myGridCells[cellIndex], result);
                statusLabel.setText(message.getUsername() + ": " + result);
                return;
            }

            boolean shotByMe = Session.isLoggedIn()
                && message.getUsername().equalsIgnoreCase(Session.getCurrentAccount().getUsername());

            markCell(shotByMe ? enemyGridCells[cellIndex] : myGridCells[cellIndex], result);

            // A hit/sunk keeps the shooter's turn; a miss passes it - matches the server's rule exactly.
            myTurn = shotByMe ? !"MISS".equals(result) : "MISS".equals(result);

            if (myTurn)
            {
                statusLabel.setText(shotByMe ? result + "! Fire again." : "Your turn - fire at Enemy Waters");
            }
            else
            {
                statusLabel.setText("Waiting for " + opponentUsername + "...");
            }
        }
        else if (message.getType() == MessageType.SPECTATE_ENDED)
        {
            GameHubDialog.show(this, "Battleship", "The match you were watching has ended.");
            dispose();
        }
        else if (message.getType() == MessageType.BATTLESHIP_MATCH_OVER)
        {
            recordPlayed();
            String result = message.getMatchResult();
            String text = "OPPONENT_LEFT".equals(result)
                ? opponentUsername + " left the game. You win by default!"
                : "WIN".equals(result) ? "You sank the enemy fleet - you win!"
                : "Your fleet was sunk - you lose.";
            final String finalOpponent = opponentUsername;
            GameHubDialog.showWithAction(this, "Battleship", text, "Rematch", new Runnable()
            {
                public void run() { requestRematch(finalOpponent); }
            });
            dispose();
        }
    }

    private void requestRematch(String opponent)
    {
        Message request = new Message();
        request.setType(MessageType.REMATCH_REQUEST);
        request.setToUsername(opponent);
        request.setGameId("battleship");
        NetworkManager.sendAsync(request);

        BattleshipWindow window = BattleshipWindow.forRematchWait(opponent);
        window.setVisible(true);
    }

    private void recordPlayed()
    {
        if (!Session.isLoggedIn())
        {
            GuestPlayTracker.recordGuestPlay("battleship", 0);
            return;
        }

        Message request = new Message();
        request.setType(MessageType.GAME_PLAYED_REQUEST);
        request.setGameId("battleship");
        NetworkManager.sendAsync(request);
    }
}
