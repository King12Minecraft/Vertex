package games;
import net.MessageType;
import net.Message;
import net.ClientHandler;
import economy.GameHistoryManager;
import social.ChatManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MatchmakingKernel
 * -----------------
 * The FIFO "pair the first two waiting players" queue every one of the
 * ~26 online games' own `<Name>MatchManager` classes had hand-rolled -
 * comparing several side by side (this session: TicTacToeMatch's
 * MatchManager, ConnectFourMatchManager, CheckersMatchManager) found
 * them structurally identical: the same waiting list, the same
 * find/cancel/queue-count/endMatch shape, the same broadcastQueueCount
 * push - varying only in which concrete Match class gets constructed
 * and which ClientHandler setter attaches it. This kernel is that
 * shared shape; a `<Name>MatchManager` becomes a thin wrapper supplying
 * just the two things that actually vary, via {@link PairHandler}.
 *
 * Deliberately generic over the match type M rather than assuming
 * anything about it - the kernel never constructs, starts, or inspects
 * a match itself, it only tracks the waiting queue and the
 * matchId -> match map. Everything match-type-specific (construction,
 * starting, and attaching the result to each ClientHandler's own
 * currentXxxMatch-style field so a later disconnect is routed
 * correctly) is the caller's PairHandler's job.
 *
 * Adopted so far by CheckersMatchManager as the proof this generalizes
 * (see ROADMAP.md) - the same "prove it on one, don't roll out
 * everywhere at once" approach ai/search, engine, EconomyKernel,
 * GameWindowKernel, and reconnection all took. The other ~25
 * MatchManager classes are unchanged and equally correct; adopting this
 * is optional cleanup for whenever one of them is next touched, not a
 * requirement.
 */
public class MatchmakingKernel<M>
{
    /** Supplies the two things that vary per game - everything else (the queue itself) is generic. */
    public interface PairHandler<M>
    {
        /** Constructs AND starts the match (calls its own start()-equivalent) - the kernel never assumes a common "Match" interface exists. */
        M pair(String matchId, ClientHandler playerA, ClientHandler playerB);

        /** Attaches match to handler's own currentXxxMatch-style field, so ClientHandler.run()'s disconnect handling reaches it later. */
        void attach(ClientHandler handler, M match);
    }

    private final List<ClientHandler> waitingPlayers = new ArrayList<ClientHandler>();
    private final Map<String, M> activeMatches = new HashMap<String, M>();
    private int nextMatchId = 1;

    private final String gameId;
    private final String matchIdPrefix;
    private final GameHistoryManager gameHistoryManager;
    private final ChatManager chatManager;
    private final PairHandler<M> pairHandler;

    public MatchmakingKernel(String gameId, GameHistoryManager gameHistoryManager, ChatManager chatManager, PairHandler<M> pairHandler)
    {
        this(gameId, gameId, gameHistoryManager, chatManager, pairHandler);
    }

    /**
     * matchIdPrefix is usually the same as gameId, but not always - Connect Four's
     * matches are "connect4-1"/"connect4-2" while its GAME_ID (used for QUEUE_UPDATE
     * and history tracking) is "connect-four". Kept as a separate parameter rather
     * than assuming they match, to preserve each existing game's exact matchId format
     * (an opaque string the client only ever compares for equality, never parses -
     * so this is a cosmetic-only distinction, but preserving it exactly means
     * adopting this kernel is guaranteed zero observable behavior change).
     */
    public MatchmakingKernel(String gameId, String matchIdPrefix, GameHistoryManager gameHistoryManager, ChatManager chatManager, PairHandler<M> pairHandler)
    {
        this.gameId = gameId;
        this.matchIdPrefix = matchIdPrefix;
        this.gameHistoryManager = gameHistoryManager;
        this.chatManager = chatManager;
        this.pairHandler = pairHandler;
    }

    public synchronized void findMatch(ClientHandler player)
    {
        if (waitingPlayers.contains(player))
        {
            return;
        }

        if (!waitingPlayers.isEmpty())
        {
            ClientHandler opponent = waitingPlayers.remove(0);
            String matchId = matchIdPrefix + "-" + (nextMatchId++);
            M match = pairHandler.pair(matchId, opponent, player);
            activeMatches.put(matchId, match);
            pairHandler.attach(opponent, match);
            pairHandler.attach(player, match);

            recordPlay(opponent);
            recordPlay(player);

            broadcastQueueCount();
        }
        else
        {
            waitingPlayers.add(player);
            broadcastQueueCount();
        }
    }

    private void recordPlay(ClientHandler handler)
    {
        if (handler.getLoggedInUsername() != null && handler.getAccountId() != null)
        {
            gameHistoryManager.recordPlay(handler.getAccountId(), gameId);
        }
    }

    public synchronized void cancelWaiting(ClientHandler player)
    {
        boolean removed = waitingPlayers.remove(player);
        if (removed)
        {
            broadcastQueueCount();
        }
    }

    public synchronized void endMatch(String matchId)
    {
        activeMatches.remove(matchId);
    }

    public synchronized int getQueueCount()
    {
        return waitingPlayers.size();
    }

    private void broadcastQueueCount()
    {
        Message msg = new Message();
        msg.setType(MessageType.QUEUE_UPDATE);
        msg.setQueueGameId(gameId);
        msg.setQueueCount(waitingPlayers.size());
        chatManager.broadcastToAll(msg);
    }
}
