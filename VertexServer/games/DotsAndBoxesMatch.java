package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;
import economy.EconomyConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * DotsAndBoxesMatch
 * -----------------
 * Classic Dots and Boxes, 1v1 - an original implementation of the
 * well-known, decades-old, uncopyrightable game. 5x5 dot grid (4x4 =
 * 16 boxes). Players alternate drawing one line at a time between two
 * adjacent dots; completing the 4th side of a box claims it AND earns
 * another turn immediately (the real rule - without it, the game
 * would just be "whoever goes last wins," not an actual strategy
 * game). Most boxes when every line is drawn wins.
 *
 * Board state is 40 line slots (20 horizontal + 20 vertical) followed
 * by 16 box-owner slots, all as one flat char string - '.'  for an
 * undrawn line or unclaimed box, otherwise 'X' (line drawn) or the
 * owning player's index digit (box claimed). Line index -> meaning:
 * indices 0-19 are horizontal lines (5 rows x 4 per row, index =
 * row*4+col, sitting between dot (row,col) and dot (row,col+1));
 * indices 20-39 are vertical lines (4 rows x 5 per row, index =
 * 20 + row*5+col, sitting between dot (row,col) and dot (row+1,col)).
 * Box (r,c) for r,c in 0..3 is bordered by horizontal lines r*4+c
 * (top) and (r+1)*4+c (bottom), and vertical lines 20+r*5+c (left)
 * and 20+r*5+(c+1) (right).
 */
public class DotsAndBoxesMatch
{
    public static final int BOX_ROWS = 4;
    public static final int BOX_COLS = 4;
    public static final int LINE_COUNT = 40;
    public static final int BOX_COUNT = BOX_ROWS * BOX_COLS;
    private static final String GAME_ID = "dots-and-boxes";

    private final String matchId;
    private final List<ClientHandler> players;
    private final DotsAndBoxesMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final char[] lines = new char[LINE_COUNT];
    private final char[] boxOwners = new char[BOX_COUNT];
    private int turnPlayerIndex = 0;
    private boolean over = false;

    public DotsAndBoxesMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                              DotsAndBoxesMatchManager matchManager, EconomyManager economyManager,
                              LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.players = new ArrayList<ClientHandler>();
        this.players.add(playerA);
        this.players.add(playerB);
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        java.util.Arrays.fill(lines, '.');
        java.util.Arrays.fill(boxOwners, '.');
    }

    public void start()
    {
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.DOTS_MATCH_FOUND);
            msg.setMatchId(matchId);
            msg.setSymbol(String.valueOf(i));
            msg.setOpponentUsername(players.get(1 - i).getLoggedInUsername());
            msg.setBoardState(boardString());
            players.get(i).sendMessage(msg);
        }
    }

    public synchronized void drawLine(ClientHandler requester, int lineIndex)
    {
        if (over || lineIndex < 0 || lineIndex >= LINE_COUNT) return;
        int playerIndex = players.indexOf(requester);
        if (playerIndex < 0 || playerIndex != turnPlayerIndex) return;
        if (lines[lineIndex] != '.') return;

        lines[lineIndex] = 'X';
        int boxesCompleted = claimAnyCompletedBoxes(lineIndex, playerIndex);

        boolean allLinesDrawn = allLinesDrawn();
        if (!allLinesDrawn && boxesCompleted == 0)
        {
            // No box completed this turn - passes to the other player. Completing at least
            // one box grants another turn instead (same player stays turnPlayerIndex).
            turnPlayerIndex = 1 - turnPlayerIndex;
        }

        if (allLinesDrawn)
        {
            over = true;
            finish();
            return;
        }

        broadcastUpdate();
    }

    /** Returns how many boxes this line completed (0, 1, or 2 - a line can complete two boxes at once if it's the shared edge between them and both were already at 3/4 sides). */
    private int claimAnyCompletedBoxes(int lineIndex, int playerIndex)
    {
        int completed = 0;
        for (int boxIndex : boxesBorderedBy(lineIndex))
        {
            if (boxOwners[boxIndex] == '.' && isBoxComplete(boxIndex))
            {
                boxOwners[boxIndex] = (char) ('0' + playerIndex);
                completed++;
            }
        }
        return completed;
    }

    private boolean isBoxComplete(int boxIndex)
    {
        int r = boxIndex / BOX_COLS, c = boxIndex % BOX_COLS;
        int top = r * BOX_COLS + c;
        int bottom = (r + 1) * BOX_COLS + c;
        int left = 20 + r * (BOX_COLS + 1) + c;
        int right = 20 + r * (BOX_COLS + 1) + (c + 1);
        return lines[top] == 'X' && lines[bottom] == 'X' && lines[left] == 'X' && lines[right] == 'X';
    }

    /** Which box(es) a given line index borders - a horizontal line borders the box above and below it (if they exist); a vertical line borders the box to its left and right (if they exist). */
    private List<Integer> boxesBorderedBy(int lineIndex)
    {
        List<Integer> result = new ArrayList<Integer>();
        if (lineIndex < 20)
        {
            int row = lineIndex / BOX_COLS, col = lineIndex % BOX_COLS;
            if (row - 1 >= 0) result.add((row - 1) * BOX_COLS + col);
            if (row < BOX_ROWS) result.add(row * BOX_COLS + col);
        }
        else
        {
            int vIndex = lineIndex - 20;
            int row = vIndex / (BOX_COLS + 1), col = vIndex % (BOX_COLS + 1);
            if (col - 1 >= 0) result.add(row * BOX_COLS + (col - 1));
            if (col < BOX_COLS) result.add(row * BOX_COLS + col);
        }
        return result;
    }

    private boolean allLinesDrawn()
    {
        for (char c : lines) if (c == '.') return false;
        return true;
    }

    private void broadcastUpdate()
    {
        String state = boardString();
        for (int i = 0; i < players.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.DOTS_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            msg.setSymbol(String.valueOf(turnPlayerIndex));
            players.get(i).sendMessage(msg);
        }
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        int[] counts = new int[2];
        for (char owner : boxOwners)
        {
            if (owner == '0') counts[0]++;
            else if (owner == '1') counts[1]++;
        }

        String winnerResult;
        if (counts[0] == counts[1]) winnerResult = "DRAW";
        else winnerResult = counts[0] > counts[1] ? "0" : "1";

        recordRating(winnerResult);

        for (int i = 0; i < players.size(); i++)
        {
            ClientHandler player = players.get(i);
            boolean isWinner = String.valueOf(i).equals(winnerResult);

            if (isWinner)
            {
                economyManager.awardWin(player, GAME_ID);
            }

            Message msg = new Message();
            msg.setType(MessageType.DOTS_RESULT);
            msg.setMatchId(matchId);
            msg.setBoardState(boardString());
            msg.setScore(counts[i]);
            msg.setMatchResult(isWinner ? "WIN" : "DRAW".equals(winnerResult) ? "DRAW" : "LOSE");
            player.sendMessage(msg);
        }
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || players.get(0).getAccountId() == null || players.get(1).getAccountId() == null)
        {
            return;
        }
        double outcomeForFirst = "DRAW".equals(winnerResult) ? 0.5 : "0".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, players.get(0).getAccountId(), players.get(1).getAccountId(), outcomeForFirst);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        int leavingIndex = players.indexOf(who);
        if (leavingIndex < 0) return;
        ClientHandler remaining = players.get(1 - leavingIndex);

        Message msg = new Message();
        msg.setType(MessageType.DOTS_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }

    private String boardString()
    {
        return new String(lines) + new String(boxOwners);
    }
}
