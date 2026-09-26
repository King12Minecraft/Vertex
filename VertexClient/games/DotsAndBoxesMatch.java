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
    private final ReconnectRegistry reconnectRegistry;

    private final char[] lines = new char[LINE_COUNT];
    private final char[] boxOwners = new char[BOX_COUNT];
    private int turnPlayerIndex = 0;
    private boolean over = false;

    /** null when nobody is in a reconnect grace period, otherwise the index (0 or 1) of whichever player's socket just dropped - same pattern as TicTacToeMatch.disconnectedSlot, just index-based since this game already tracks players as a 2-element list rather than named fields. */
    private Integer disconnectedIndex = null;

    public DotsAndBoxesMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                              DotsAndBoxesMatchManager matchManager, EconomyManager economyManager,
                              LeaderboardManager leaderboardManager, ReconnectRegistry reconnectRegistry)
    {
        this.matchId = matchId;
        this.players = new ArrayList<ClientHandler>();
        this.players.add(playerA);
        this.players.add(playerB);
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        this.reconnectRegistry = reconnectRegistry;
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
        if (disconnectedIndex != null) return;
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
        for (int i = 0; i < players.size(); i++)
        {
            sendUpdate(players.get(i));
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

    public void handleDisconnect(ClientHandler who)
    {
        Integer accountIdToRegister = null;
        ClientHandler remainingForNotice = null;
        boolean bothNowGone = false;

        synchronized (this)
        {
            if (over) return;
            int leavingIndex = players.indexOf(who);
            if (leavingIndex < 0) return;

            if (disconnectedIndex != null)
            {
                over = true;
                bothNowGone = true;
            }
            else if (who.getAccountId() == null)
            {
                over = true;
                ClientHandler remaining = players.get(1 - leavingIndex);
                sendAbandonedResult(remaining);
                economyManager.awardWin(remaining, GAME_ID);
                matchManager.endMatch(matchId);
                return;
            }
            else
            {
                disconnectedIndex = leavingIndex;
                remainingForNotice = players.get(1 - leavingIndex);
                accountIdToRegister = who.getAccountId();

                Message notice = new Message();
                notice.setType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
                notice.setMatchId(matchId);
                notice.setBoardState(boardString());
                notice.setErrorText("Opponent disconnected - waiting to reconnect (up to 45s)...");
                remainingForNotice.sendMessage(notice);
            }
        }

        if (bothNowGone)
        {
            matchManager.endMatch(matchId);
            return;
        }

        // See ReconnectRegistry's class-level threading note - never call this while
        // holding this match's own lock.
        reconnectRegistry.beginGracePeriod(accountIdToRegister, new ReconnectRegistry.ReconnectableMatch()
        {
            public void onReconnectTimeout()
            {
                DotsAndBoxesMatch.this.onReconnectTimeout();
            }

            public ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
            {
                return DotsAndBoxesMatch.this.onReconnect(newHandler);
            }

            public void attachToHandler(ClientHandler handler)
            {
                handler.setCurrentDotsAndBoxesMatch(DotsAndBoxesMatch.this);
            }
        });
    }

    private synchronized void onReconnectTimeout()
    {
        if (over) return;
        over = true;
        ClientHandler remaining = players.get(1 - disconnectedIndex);
        disconnectedIndex = null;
        matchManager.endMatch(matchId);
        sendAbandonedResult(remaining);
        economyManager.awardWin(remaining, GAME_ID);
    }

    /** Called by ReconnectRegistry.tryReconnect() while it holds the registry's own lock - must never call back into the registry from here. */
    private synchronized ReconnectRegistry.ReconnectResult onReconnect(ClientHandler newHandler)
    {
        if (over || disconnectedIndex == null)
        {
            return null;
        }

        int reconnectedIndex = disconnectedIndex;
        players.set(reconnectedIndex, newHandler);
        disconnectedIndex = null;

        ClientHandler opponent = players.get(1 - reconnectedIndex);
        sendUpdate(opponent);

        return new ReconnectRegistry.ReconnectResult(matchId, GAME_ID, String.valueOf(reconnectedIndex),
            opponent.getLoggedInUsername(), boardString(), String.valueOf(turnPlayerIndex));
    }

    private void sendUpdate(ClientHandler to)
    {
        Message msg = new Message();
        msg.setType(MessageType.DOTS_UPDATE);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setSymbol(String.valueOf(turnPlayerIndex));
        to.sendMessage(msg);
    }

    private void sendAbandonedResult(ClientHandler remaining)
    {
        Message msg = new Message();
        msg.setType(MessageType.DOTS_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);
    }

    private String boardString()
    {
        return new String(lines) + new String(boxOwners);
    }
}
