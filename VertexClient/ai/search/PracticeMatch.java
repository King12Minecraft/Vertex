package ai.search;

import ai.AiKernel;

/**
 * PracticeMatch
 * -------------
 * The one offline-match turn/state wrapper every search-based game's
 * "Practice" mode uses - entirely local, no server, no network. Holds
 * the current state, whose turn it is, and whether the match is over,
 * driving everything through a GameModel so this class never knows
 * any particular game's rules. Games no longer write their own
 * `<Name>PracticeMatch` class for this; only their GameModel and a
 * `new PracticeMatch<...>(...)` call from the game's Window.
 *
 * The bot's move is asked for via AiKernel.chooseMove(aiGameId, ...),
 * not a raw BotStrategy - so every practice match still gets
 * AiKernel's try-primary-then-fallback safety net, same as every
 * other bot in Vertex. The caller (the game's Window class, typically
 * in a static initializer) is responsible for having already called
 * AiKernel.register(aiGameId, new GenericBotStrategy&lt;...&gt;(model, ...),
 * new RandomMoveStrategy&lt;...&gt;(model, ...)) before constructing one of
 * these.
 */
public class PracticeMatch<S, M>
{
    private final GameModel<S, M> model;
    private final String aiGameId;
    private final int humanPlayerIndex;
    private final int botPlayerIndex;

    private S state;
    private int currentPlayer;
    private boolean over;
    private Integer winnerIndex;

    public PracticeMatch(GameModel<S, M> model, S initialState, String aiGameId,
                          int humanPlayerIndex, int botPlayerIndex, int firstPlayer)
    {
        this.model = model;
        this.state = initialState;
        this.aiGameId = aiGameId;
        this.humanPlayerIndex = humanPlayerIndex;
        this.botPlayerIndex = botPlayerIndex;
        this.currentPlayer = firstPlayer;
    }

    public S getState() { return state; }
    public boolean isOver() { return over; }
    /** null once over means a draw - meaningless while the match is still in progress. */
    public Integer getWinnerIndex() { return winnerIndex; }
    public boolean isHumanTurn() { return !over && currentPlayer == humanPlayerIndex; }
    public int getCurrentPlayer() { return currentPlayer; }

    /** Every legal move for whoever's turn it currently is - lets a game's Window detect a forced-pass-only turn (a game-specific concept, e.g. Reversi's PASS sentinel) without PracticeMatch itself needing to know what "pass" means. */
    public java.util.List<M> legalMovesForCurrentPlayer()
    {
        return model.legalMoves(state, currentPlayer);
    }

    /** Attempts the human's move. Returns true if it was legal and applied (the match may now be over, or it may be the bot's turn), false if illegal or it isn't the human's turn. */
    public boolean humanMove(M move)
    {
        if (over || currentPlayer != humanPlayerIndex)
        {
            return false;
        }
        if (!model.legalMoves(state, humanPlayerIndex).contains(move))
        {
            return false;
        }
        applyAndAdvance(move, humanPlayerIndex);
        return true;
    }

    /** Plays the bot's move via AiKernel's registered strategy for aiGameId. Only call when it's genuinely the bot's turn and the match isn't over. */
    public void botMove()
    {
        if (over || currentPlayer != botPlayerIndex)
        {
            return;
        }
        M move = AiKernel.<S, M>chooseMove(aiGameId, state);
        applyAndAdvance(move, botPlayerIndex);
    }

    /** Returns the registered strategy's suggested move for whoever's turn it currently is, without applying it - for a Hint button. Reuses the exact same AiKernel-registered strategy the bot opponent itself plays with. */
    public M suggestMove()
    {
        return AiKernel.<S, M>chooseMove(aiGameId, state);
    }

    private void applyAndAdvance(M move, int mover)
    {
        state = model.applyMove(state, move, mover);
        if (model.isTerminal(state))
        {
            over = true;
            winnerIndex = model.winner(state);
        }
        else
        {
            currentPlayer = model.nextPlayer(state, mover);
        }
    }
}
