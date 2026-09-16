package games;

import net.ClientHandler;
import net.Message;
import net.MessageType;
import economy.EconomyManager;
import economy.LeaderboardManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CardRushMatch
 * -------------
 * Card Rush - a real-time 1v1 card game, an original design using a
 * standard 52-card deck (not copyrightable - the deck itself and
 * "play adjacent ranks" mechanics are common building blocks, this
 * particular combination is original). Each player has a 5-card hand
 * and a 20-card personal stock; two shared center piles start with
 * one card each. Either player, at any time (no turns), can play a
 * card from their hand onto either center pile if it's exactly one
 * rank higher or lower than that pile's current top card (no
 * Ace-King wraparound). After a successful play, the hand refills to
 * 5 from that player's own stock if any remains.
 *
 * If neither player has a legal move after a play, the match is
 * "stuck" - each player's own stock (if non-empty) automatically
 * flips one fresh card onto ITS OWN center pile (player A refreshes
 * pile 1, player B refreshes pile 2) to break the deadlock, no
 * player action needed. First to empty both hand AND stock wins; if
 * the game reaches a true deadlock (nobody can move and both stocks
 * are empty) with cards still in hand, fewer total remaining cards
 * wins, and an equal count is a draw.
 */
public class CardRushMatch
{
    private static final int HAND_SIZE = 5;
    private static final int STOCK_SIZE = 20;
    private static final String GAME_ID = "card-rush";

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final CardRushMatchManager matchManager;
    private final EconomyManager economyManager;
    private final LeaderboardManager leaderboardManager;

    private final List<Integer> handA = new ArrayList<Integer>();
    private final List<Integer> handB = new ArrayList<Integer>();
    private final List<Integer> stockA = new ArrayList<Integer>();
    private final List<Integer> stockB = new ArrayList<Integer>();
    private int centerPile1, centerPile2;
    private boolean over = false;

    public CardRushMatch(String matchId, ClientHandler playerA, ClientHandler playerB,
                          CardRushMatchManager matchManager, EconomyManager economyManager,
                          LeaderboardManager leaderboardManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.leaderboardManager = leaderboardManager;
        dealDeck();
    }

    /** Card values are 1 (Ace) through 13 (King), 4 of each - suit doesn't affect legality (only rank does), so it's tracked purely for the client's card-face rendering via cardId = rank*10+suitIndex, decoded client-side. */
    private void dealDeck()
    {
        List<Integer> deck = new ArrayList<Integer>();
        for (int rank = 1; rank <= 13; rank++)
        {
            for (int suit = 0; suit < 4; suit++)
            {
                deck.add(rank * 10 + suit);
            }
        }
        Collections.shuffle(deck);

        int index = 0;
        for (int i = 0; i < HAND_SIZE; i++) handA.add(deck.get(index++));
        for (int i = 0; i < HAND_SIZE; i++) handB.add(deck.get(index++));
        for (int i = 0; i < STOCK_SIZE; i++) stockA.add(deck.get(index++));
        for (int i = 0; i < STOCK_SIZE; i++) stockB.add(deck.get(index++));
        centerPile1 = deck.get(index++);
        centerPile2 = deck.get(index++);
    }

    public void start()
    {
        sendMatchFound(playerA, "A", playerB.getLoggedInUsername());
        sendMatchFound(playerB, "B", playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String symbol, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.CARDRUSH_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(symbol);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(stateString());
        to.sendMessage(msg);
    }

    private int rankOf(int cardId) { return cardId / 10; }

    /** pileNumber is 1 or 2. Removes the card from the requester's hand if the play is legal, refills their hand from their own stock, then checks for a stuck deadlock and resolves it before broadcasting. Illegal or out-of-turn-less (there's no turn) requests are just silently ignored - the requester's own hand not changing is the natural feedback. */
    public synchronized void playCard(ClientHandler requester, int cardId, int pileNumber)
    {
        if (over || (pileNumber != 1 && pileNumber != 2)) return;
        boolean isA = requester == playerA;
        List<Integer> hand = isA ? handA : handB;
        if (!hand.contains(cardId)) return;

        int pileTop = pileNumber == 1 ? centerPile1 : centerPile2;
        if (Math.abs(rankOf(cardId) - rankOf(pileTop)) != 1) return;

        hand.remove(Integer.valueOf(cardId));
        if (pileNumber == 1) centerPile1 = cardId; else centerPile2 = cardId;

        refillHand(isA);
        resolveStuckIfNeeded();

        if (isGameOver())
        {
            over = true;
            finish();
            return;
        }

        broadcastUpdate();
    }

    private void refillHand(boolean isA)
    {
        List<Integer> hand = isA ? handA : handB;
        List<Integer> stock = isA ? stockA : stockB;
        while (hand.size() < HAND_SIZE && !stock.isEmpty())
        {
            hand.add(stock.remove(0));
        }
    }

    private boolean hasAnyLegalMove(List<Integer> hand)
    {
        for (int card : hand)
        {
            if (Math.abs(rankOf(card) - rankOf(centerPile1)) == 1) return true;
            if (Math.abs(rankOf(card) - rankOf(centerPile2)) == 1) return true;
        }
        return false;
    }

    private void resolveStuckIfNeeded()
    {
        if (hasAnyLegalMove(handA) || hasAnyLegalMove(handB)) return;

        if (!stockA.isEmpty()) centerPile1 = stockA.remove(0);
        if (!stockB.isEmpty()) centerPile2 = stockB.remove(0);
    }

    private boolean isGameOver()
    {
        boolean aDone = handA.isEmpty() && stockA.isEmpty();
        boolean bDone = handB.isEmpty() && stockB.isEmpty();
        if (aDone || bDone) return true;

        // True deadlock: nobody can move and neither stock can refresh a pile.
        return !hasAnyLegalMove(handA) && !hasAnyLegalMove(handB) && stockA.isEmpty() && stockB.isEmpty();
    }

    private void broadcastUpdate()
    {
        String state = stateString();
        for (ClientHandler player : new ClientHandler[] { playerA, playerB })
        {
            Message msg = new Message();
            msg.setType(MessageType.CARDRUSH_UPDATE);
            msg.setMatchId(matchId);
            msg.setBoardState(state);
            player.sendMessage(msg);
        }
    }

    /** "centerPile1,centerPile2|handA-comma-list|handB-comma-list|stockA.size|stockB.size" - each player's own window shows their own hand from the matching half, and only the OTHER hand's card COUNT (not its cards) for a fair "how close are they" read without seeing their actual hand. */
    private String stateString()
    {
        return centerPile1 + "," + centerPile2 + "|"
            + joinInts(handA) + "|" + joinInts(handB) + "|"
            + stockA.size() + "," + stockB.size();
    }

    private String joinInts(List<Integer> values)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++)
        {
            if (i > 0) sb.append(",");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private void finish()
    {
        matchManager.endMatch(matchId);

        boolean aDone = handA.isEmpty() && stockA.isEmpty();
        boolean bDone = handB.isEmpty() && stockB.isEmpty();
        String winnerResult;
        if (aDone || bDone)
        {
            winnerResult = aDone ? "A" : "B";
        }
        else
        {
            int remainingA = handA.size() + stockA.size();
            int remainingB = handB.size() + stockB.size();
            winnerResult = remainingA == remainingB ? "DRAW" : (remainingA < remainingB ? "A" : "B");
        }

        recordRating(winnerResult);
        if (!"DRAW".equals(winnerResult))
        {
            economyManager.awardWin("A".equals(winnerResult) ? playerA : playerB, GAME_ID);
        }

        sendResult(playerA, winnerResult);
        sendResult(playerB, winnerResult);
    }

    private void recordRating(String winnerResult)
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = "DRAW".equals(winnerResult) ? 0.5 : "A".equals(winnerResult) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch(GAME_ID, playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void sendResult(ClientHandler to, String winnerResult)
    {
        Message msg = new Message();
        msg.setType(MessageType.CARDRUSH_RESULT);
        msg.setMatchId(matchId);
        msg.setBoardState(stateString());
        boolean toIsWinner = (to == playerA && "A".equals(winnerResult)) || (to == playerB && "B".equals(winnerResult));
        msg.setMatchResult("DRAW".equals(winnerResult) ? "DRAW" : (toIsWinner ? "WIN" : "LOSE"));
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.CARDRUSH_RESULT);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(stateString());
        remaining.sendMessage(msg);

        economyManager.awardWin(remaining, GAME_ID);
    }
}
