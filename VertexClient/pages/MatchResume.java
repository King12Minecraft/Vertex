package pages;

import games.GameWindowFactory;
import net.Message;
import net.MessageType;
import net.NetworkManager;

import javax.swing.JComponent;

/**
 * MatchResume
 * -----------
 * Puts a player back into the online match the server just handed back to them. Used after
 * a login that came with a pending match (AuthWindow), and after the automatic re-login that
 * follows a dropped connection (SessionRestorer) - one place, so both behave identically.
 * Client-only: the server side of this is mechanics.ReconnectRegistry / PairReconnect.
 */
public final class MatchResume
{
    private MatchResume() { }

    /**
     * Reconnection: if this login's response carried a pending match (see
     * ReconnectRegistry server-side, now backing TicTacToe/Connect Four/
     * every game in mechanics.ReconnectPolicy's enabled list), jumps straight into that game's window
     * instead of leaving the player on MainMenu with no idea their match
     * survived a disconnect. Deliberately does NOT wait for a separate server
     * push for this - the server already re-associated the match with this
     * session as part of handling the login itself, and everything needed to
     * resume is already sitting in loginResponse's fields, so this
     * reconstructs the equivalent of the messages a *fresh* match-found would
     * have sent (a MATCH_FOUND-shaped push then an MATCH_UPDATE-shaped one)
     * and feeds them to the freshly built window directly, purely locally -
     * avoiding any race with a real network push arriving before this window
     * exists to receive it. Each game uses its own MessageType pair (TicTacToe's
     * generic MATCH_FOUND/MATCH_UPDATE vs. e.g. Connect Four's CONNECT4_MATCH_
     * FOUND/CONNECT4_UPDATE) - reconnectMessageTypesFor() is the (a plain
     * per-game lookup) mapping.
     */
    public static void resumeIfPending(Message loginResponse)
    {
        String gameId = loginResponse.getReconnectGameId();
        if (gameId == null || loginResponse.getMatchId() == null)
        {
            return;
        }

        MessageType[] types = reconnectMessageTypesFor(gameId);
        if (types == null)
        {
            return;
        }

        java.util.function.Supplier<JComponent> factory = GameWindowFactory.factoryFor(gameId);
        if (factory == null)
        {
            return;
        }
        JComponent window = factory.get();
        MainMenu.getInstance().showResumedGame(window);

        // Ask the server to re-announce the match's chat room (if the game has one) so the chat dock comes back too.
        Message chatSync = new Message();
        chatSync.setType(MessageType.MATCH_CHAT_SYNC_REQUEST);
        NetworkManager.sendAsync(chatSync);

        if (!(window instanceof NetworkManager.PushListener))
        {
            return;
        }
        NetworkManager.PushListener listener = (NetworkManager.PushListener) window;

        Message found = new Message();
        found.setType(types[0]);
        found.setMatchId(loginResponse.getMatchId());
        found.setSymbol(loginResponse.getSymbol());
        found.setOpponentUsername(loginResponse.getOpponentUsername());
        found.setBoardState(loginResponse.getBoardState());
        if ("word-duel".equals(gameId))
        {
            // Word Duel's real MATCH_FOUND doesn't use symbol/boardState at all - it
            // carries its letters via triviaQuestion. WordDuelMatch.onReconnect()
            // packs them into ReconnectResult.boardState anyway (the only free
            // string slot that shape has), so unpack it back out into the field
            // WordDuelWindow actually reads. Harmless no-op for every other game,
            // which never reads triviaQuestion off a MATCH_FOUND push.
            found.setTriviaQuestion(loginResponse.getBoardState());
        }
        if ("rock-paper-scissors".equals(gameId))
        {
            // RPS has no board and no turn (moves are blind and simultaneous), so
            // neither symbol nor boardState carries anything meaningful by default -
            // RockPaperScissorsMatch.onReconnect() instead packs the running score
            // into boardState as "myScore:opponentScore" (see its own javadoc), which
            // gets unpacked here into the two score fields RockPaperScissorsWindow's
            // RPS_MATCH_FOUND handling already reads. Must happen before onPush below,
            // not after (unlike the battleship/rock-paper-scissors early-returns
            // further down, which only skip a later message rather than mutate this
            // one).
            String[] scores = loginResponse.getBoardState() != null
                ? loginResponse.getBoardState().split(":") : new String[0];
            if (scores.length == 2)
            {
                try
                {
                    found.setRpsMyScore(Integer.parseInt(scores[0]));
                    found.setRpsOpponentScore(Integer.parseInt(scores[1]));
                }
                catch (NumberFormatException ignored)
                {
                    // Falls back to the Message default (0-0) - matches what a
                    // genuinely fresh match-found already shows either way.
                }
            }
        }
        listener.onPush(found);

        if ("battleship".equals(gameId))
        {
            // Battleship's real MATCH_FOUND already uses exactly symbol ("MINE"/
            // "THEIRS") and boardState (own fleet layout) - the same shape the first
            // 5 adopters happen to use - so BattleshipMatch.onReconnect() packs
            // "whose turn is it now" straight into ReconnectResult.mySymbol and the
            // replay above already sets myTurn correctly. No second message: unlike
            // the other adopters, Battleship has no generic *_UPDATE type (only the
            // richly-shaped BATTLESHIP_FIRE_RESULT), and sending that with default/
            // null shot fields would misdraw a phantom hit on cell 0 rather than
            // just do nothing - so this game deliberately skips it instead.
            return;
        }

        if ("rock-paper-scissors".equals(gameId))
        {
            // Same reasoning as battleship above - there's no generic RPS *_UPDATE
            // type either, and the running score is already fully carried by the one
            // MATCH_FOUND message just pushed above.
            return;
        }

        // Corrects whose-turn state the match-found push alone can't express (it
        // always assumes a brand new match where the first symbol goes first) -
        // reuses the same *_UPDATE shape and handling every online game already
        // has for a live turn change.
        Message update = new Message();
        update.setType(types[1]);
        update.setMatchId(loginResponse.getMatchId());
        update.setSymbol(loginResponse.getReconnectTurnSymbol());
        update.setBoardState(loginResponse.getBoardState());
        if ("tetris-duel".equals(gameId))
        {
            // Tetris Duel's update is per-player: own grid (boardState, already set above), the
            // opponent's grid (chatText) and own score. TetrisDuelMatch.resume() packs the last
            // two into the turn slot as "score|opponentGrid".
            String[] parts = String.valueOf(loginResponse.getReconnectTurnSymbol()).split("\\|", 2);
            try { update.setScore(Integer.parseInt(parts[0])); } catch (NumberFormatException ignored) { }
            if (parts.length > 1)
            {
                update.setChatText(parts[1]);
            }
        }
        if ("typing-duel".equals(gameId))
        {
            // Typing Duel resumes into a round, not a board: the sentence rides in boardState
            // and "winsA:winsB|progA:progB" in the turn slot (TypingDuelMatch.resume()), so the
            // update is a ROUND_START carrying the sentence and round wins, followed by an
            // UPDATE with everyone's progress.
            String[] parts = String.valueOf(loginResponse.getReconnectTurnSymbol()).split("\\|");
            update.setTriviaQuestion(loginResponse.getBoardState());
            update.setTriviaScores(java.util.Arrays.asList(parts[0]));
            listener.onPush(update);
            if (parts.length > 1)
            {
                Message progress = new Message();
                progress.setType(MessageType.TYPINGDUEL_UPDATE);
                progress.setMatchId(loginResponse.getMatchId());
                progress.setTriviaScores(java.util.Arrays.asList(parts[1]));
                listener.onPush(progress);
            }
            return;
        }
        if ("memory-match".equals(gameId))
        {
            // Memory Match's update also carries the running "a:b" pair score, which a login
            // response has no field for - MemoryMatchMatch.resume() packs it after the turn
            // as "turn|a:b".
            String[] parts = String.valueOf(loginResponse.getReconnectTurnSymbol()).split("\\|");
            update.setSymbol(parts[0]);
            if (parts.length > 1)
            {
                update.setTriviaScores(java.util.Arrays.asList(parts[1]));
            }
        }
        if ("word-duel".equals(gameId))
        {
            // Same idea as above: Word Duel's real UPDATE carries progress via
            // triviaScores (a "mine:opponent" length tuple), not symbol/boardState.
            // See WordDuelMatch.onReconnect()'s javadoc for the full explanation of
            // why turnSymbol is repurposed to carry it.
            update.setTriviaScores(java.util.Arrays.asList(loginResponse.getReconnectTurnSymbol()));
        }
        listener.onPush(update);
    }

    /** {matchFoundType, updateType} for a reconnect-aware game's own message types, or null if gameId isn't one of them (no pending-match resume attempted in that case). */
    private static MessageType[] reconnectMessageTypesFor(String gameId)
    {
        if ("tictactoe-online".equals(gameId))
        {
            return new MessageType[] { MessageType.MATCH_FOUND, MessageType.MATCH_UPDATE };
        }
        if ("connect-four".equals(gameId))
        {
            return new MessageType[] { MessageType.CONNECT4_MATCH_FOUND, MessageType.CONNECT4_UPDATE };
        }
        if ("checkers".equals(gameId))
        {
            return new MessageType[] { MessageType.CHECKERS_MATCH_FOUND, MessageType.CHECKERS_UPDATE };
        }
        if ("dice-duel".equals(gameId))
        {
            return new MessageType[] { MessageType.DICEDUEL_MATCH_FOUND, MessageType.DICEDUEL_UPDATE };
        }
        if ("signal-grid".equals(gameId))
        {
            return new MessageType[] { MessageType.SIGNALGRID_MATCH_FOUND, MessageType.SIGNALGRID_UPDATE };
        }
        if ("fusion-grid".equals(gameId))
        {
            return new MessageType[] { MessageType.FUSIONGRID_MATCH_FOUND, MessageType.FUSIONGRID_UPDATE };
        }
        if ("air-hockey".equals(gameId))
        {
            return new MessageType[] { MessageType.AIRHOCKEY_MATCH_FOUND, MessageType.AIRHOCKEY_UPDATE };
        }
        if ("snake-arena".equals(gameId))
        {
            return new MessageType[] { MessageType.SNAKEARENA_MATCH_FOUND, MessageType.SNAKEARENA_UPDATE };
        }
        if ("tetris-duel".equals(gameId))
        {
            return new MessageType[] { MessageType.TETRISDUEL_MATCH_FOUND, MessageType.TETRISDUEL_UPDATE };
        }
        if ("card-rush".equals(gameId))
        {
            return new MessageType[] { MessageType.CARDRUSH_MATCH_FOUND, MessageType.CARDRUSH_UPDATE };
        }
        if ("typing-duel".equals(gameId))
        {
            return new MessageType[] { MessageType.TYPINGDUEL_MATCH_FOUND, MessageType.TYPINGDUEL_ROUND_START };
        }
        if ("memory-match".equals(gameId))
        {
            return new MessageType[] { MessageType.MEMORY_MATCH_FOUND, MessageType.MEMORY_UPDATE };
        }
        if ("chess".equals(gameId))
        {
            return new MessageType[] { MessageType.CHESS_MATCH_FOUND, MessageType.CHESS_UPDATE };
        }
        if ("reversi".equals(gameId))
        {
            return new MessageType[] { MessageType.REVERSI_MATCH_FOUND, MessageType.REVERSI_UPDATE };
        }
        if ("dots-and-boxes".equals(gameId))
        {
            return new MessageType[] { MessageType.DOTS_MATCH_FOUND, MessageType.DOTS_UPDATE };
        }
        if ("word-duel".equals(gameId))
        {
            return new MessageType[] { MessageType.WORDDUEL_MATCH_FOUND, MessageType.WORDDUEL_UPDATE };
        }
        if ("battleship".equals(gameId))
        {
            // types[1] is never actually sent for Battleship (see the "battleship"
            // branch in resumeMatchIfPending above) - BATTLESHIP_FIRE_RESULT is only
            // here so the array has a legal MessageType in both slots.
            return new MessageType[] { MessageType.BATTLESHIP_MATCH_FOUND, MessageType.BATTLESHIP_FIRE_RESULT };
        }
        if ("rock-paper-scissors".equals(gameId))
        {
            // types[1] is never actually sent for RPS either (see the
            // "rock-paper-scissors" branch in resumeMatchIfPending above) -
            // RPS_ROUND_RESULT is only here so the array has a legal MessageType in
            // both slots.
            return new MessageType[] { MessageType.RPS_MATCH_FOUND, MessageType.RPS_ROUND_RESULT };
        }
        return null;
    }
}
