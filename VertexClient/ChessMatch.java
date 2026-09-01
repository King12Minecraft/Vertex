import java.util.ArrayList;
import java.util.List;

/**
 * ChessMatch
 * ----------
 * Full standard chess rules: all piece movement, check detection,
 * checkmate/stalemate, pawn auto-promotion to queen, castling
 * (kingside and queenside, with all standard legality checks), and en
 * passant. Board is a 64-char string, index = row*8+col, row 0 =
 * White's home rank, row 7 = Black's home rank. Uppercase = White,
 * lowercase = Black, '.' = empty. Server is fully authoritative -
 * validates every move before applying it.
 */
public class ChessMatch
{
    private final String matchId;
    private final ClientHandler whitePlayer;
    private final ClientHandler blackPlayer;
    private final ChessMatchManager matchManager;
    private final LeaderboardManager leaderboardManager;
    private final ReplayManager replayManager;
    private final List<ClientHandler> spectators = new ArrayList<ClientHandler>();
    private final List<String> snapshots = new ArrayList<String>();

    private final char[] board = new char[64];
    private boolean whiteTurn = true;
    private boolean over = false;
    private boolean drawOfferPending = false;

    private boolean whiteKingMoved;
    private boolean blackKingMoved;
    private boolean whiteRookAMoved;
    private boolean whiteRookHMoved;
    private boolean blackRookAMoved;
    private boolean blackRookHMoved;

    /** The square a pawn just skipped over with a double-move, capturable en passant only on the very next move. -1 when none is available. */
    private int enPassantTarget = -1;

    public ChessMatch(String matchId, ClientHandler whitePlayer, ClientHandler blackPlayer, ChessMatchManager matchManager, LeaderboardManager leaderboardManager, ReplayManager replayManager)
    {
        this.matchId = matchId;
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.matchManager = matchManager;
        this.leaderboardManager = leaderboardManager;
        this.replayManager = replayManager;
        setupBoard();
        snapshots.add(boardString());
    }

    private void setupBoard()
    {
        String backRank = "RNBQKBNR";
        for (int c = 0; c < 8; c++)
        {
            board[0 * 8 + c] = backRank.charAt(c);
            board[1 * 8 + c] = 'P';
            board[6 * 8 + c] = 'p';
            board[7 * 8 + c] = Character.toLowerCase(backRank.charAt(c));
            for (int r = 2; r <= 5; r++)
            {
                board[r * 8 + c] = '.';
            }
        }
    }

    public void start()
    {
        sendMatchFound(whitePlayer, "WHITE", blackPlayer.getLoggedInUsername());
        sendMatchFound(blackPlayer, "BLACK", whitePlayer.getLoggedInUsername());
        broadcastUpdate();
    }

    private void sendMatchFound(ClientHandler to, String color, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHESS_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setSymbol(color);
        msg.setOpponentUsername(opponentUsername);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void makeMove(ClientHandler requester, int from, int to)
    {
        if (over)
        {
            return;
        }

        boolean isWhite = requester == whitePlayer;
        if (isWhite != whiteTurn)
        {
            sendRejected(requester, "It's not your turn.");
            return;
        }
        if (from < 0 || from > 63 || to < 0 || to > 63 || from == to)
        {
            sendRejected(requester, "Invalid move.");
            return;
        }

        char piece = board[from];
        if (piece == '.' || Character.isUpperCase(piece) != isWhite)
        {
            sendRejected(requester, "That's not your piece.");
            return;
        }

        if (!isLegalMove(from, to, isWhite))
        {
            sendRejected(requester, "That move isn't legal.");
            return;
        }

        updateCastlingRights(from, piece);
        int nextEnPassantTarget = computeNextEnPassantTarget(from, to, piece);
        applyMove(board, from, to);
        enPassantTarget = nextEnPassantTarget;
        whiteTurn = !whiteTurn;
        snapshots.add(boardString());

        boolean opponentInCheck = isKingInCheck(!isWhite);
        boolean opponentHasMoves = hasAnyLegalMove(!isWhite);

        if (!opponentHasMoves)
        {
            over = true;
            broadcastResult(opponentInCheck ? (isWhite ? "WHITE" : "BLACK") : null);
            matchManager.endMatch(matchId);
        }
        else
        {
            broadcastUpdate();
        }
    }

    private void updateCastlingRights(int from, char piece)
    {
        char upper = Character.toUpperCase(piece);
        if (upper == 'K')
        {
            if (Character.isUpperCase(piece)) whiteKingMoved = true; else blackKingMoved = true;
        }
        if (from == 0) whiteRookAMoved = true;
        if (from == 7) whiteRookHMoved = true;
        if (from == 56) blackRookAMoved = true;
        if (from == 63) blackRookHMoved = true;
    }

    private int computeNextEnPassantTarget(int from, int to, char piece)
    {
        if (Character.toUpperCase(piece) == 'P' && Math.abs(to / 8 - from / 8) == 2)
        {
            return (from + to) / 2;
        }
        return -1;
    }

    private boolean isLegalMove(int from, int to, boolean isWhite)
    {
        if (!pieceCanReach(from, to, board))
        {
            return false;
        }
        char target = board[to];
        if (target != '.' && Character.isUpperCase(target) == isWhite)
        {
            return false;
        }

        char[] copy = board.clone();
        applyMove(copy, from, to);
        return !isKingInCheckOnBoard(copy, isWhite);
    }

    /** Pattern-only legality (ignores whose turn it is and check) - used both for real move validation and for scanning what's "attacking" a square. */
    private boolean pieceCanReach(int from, int to, char[] b)
    {
        char piece = b[from];
        char pieceType = Character.toUpperCase(piece);
        boolean isWhite = Character.isUpperCase(piece);
        int fr = from / 8, fc = from % 8;
        int tr = to / 8, tc = to % 8;
        int dr = tr - fr, dc = tc - fc;

        if (pieceType == 'P')
        {
            int dir = isWhite ? 1 : -1;
            int startRow = isWhite ? 1 : 6;
            if (dc == 0 && dr == dir && b[to] == '.')
            {
                return true;
            }
            if (dc == 0 && dr == 2 * dir && fr == startRow && b[to] == '.' && b[from + 8 * dir] == '.')
            {
                return true;
            }
            if (Math.abs(dc) == 1 && dr == dir && b[to] != '.' && Character.isUpperCase(b[to]) != isWhite)
            {
                return true;
            }
            if (Math.abs(dc) == 1 && dr == dir && b[to] == '.' && to == enPassantTarget)
            {
                return true;
            }
            return false;
        }
        if (pieceType == 'N')
        {
            return (Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2);
        }
        if (pieceType == 'K')
        {
            if (Math.abs(dr) <= 1 && Math.abs(dc) <= 1 && (dr != 0 || dc != 0))
            {
                return true;
            }
            if (dr == 0 && Math.abs(dc) == 2)
            {
                return canCastle(from, to, isWhite, b);
            }
            return false;
        }
        if (pieceType == 'R')
        {
            return (dr == 0 || dc == 0) && isPathClear(from, to, b);
        }
        if (pieceType == 'B')
        {
            return Math.abs(dr) == Math.abs(dc) && isPathClear(from, to, b);
        }
        if (pieceType == 'Q')
        {
            return (dr == 0 || dc == 0 || Math.abs(dr) == Math.abs(dc)) && isPathClear(from, to, b);
        }
        return false;
    }

    private boolean canCastle(int from, int to, boolean isWhite, char[] b)
    {
        int row = isWhite ? 0 : 7;
        if (from != row * 8 + 4)
        {
            return false;
        }
        boolean kingMoved = isWhite ? whiteKingMoved : blackKingMoved;
        if (kingMoved)
        {
            return false;
        }
        if (isKingInCheckOnBoard(b, isWhite))
        {
            return false;
        }

        boolean kingside = to % 8 == 6;
        if (kingside)
        {
            boolean rookMoved = isWhite ? whiteRookHMoved : blackRookHMoved;
            char expectedRook = isWhite ? 'R' : 'r';
            if (rookMoved || b[row * 8 + 5] != '.' || b[row * 8 + 6] != '.' || b[row * 8 + 7] != expectedRook)
            {
                return false;
            }
            return !isSquareAttacked(row * 8 + 5, !isWhite, b) && !isSquareAttacked(row * 8 + 6, !isWhite, b);
        }

        boolean queenside = to % 8 == 2;
        if (queenside)
        {
            boolean rookMoved = isWhite ? whiteRookAMoved : blackRookAMoved;
            char expectedRook = isWhite ? 'R' : 'r';
            if (rookMoved || b[row * 8 + 1] != '.' || b[row * 8 + 2] != '.' || b[row * 8 + 3] != '.'
                || b[row * 8 + 0] != expectedRook)
            {
                return false;
            }
            return !isSquareAttacked(row * 8 + 3, !isWhite, b) && !isSquareAttacked(row * 8 + 2, !isWhite, b);
        }

        return false;
    }

    private boolean isPathClear(int from, int to, char[] b)
    {
        int fr = from / 8, fc = from % 8;
        int tr = to / 8, tc = to % 8;
        int stepR = Integer.signum(tr - fr);
        int stepC = Integer.signum(tc - fc);
        int r = fr + stepR, c = fc + stepC;
        while (r != tr || c != tc)
        {
            if (b[r * 8 + c] != '.')
            {
                return false;
            }
            r += stepR;
            c += stepC;
        }
        return true;
    }

    /** Executes a move on the given board - including the side effects of castling (rook also moves) and en passant (the captured pawn, which isn't on the destination square, is also removed). Used for both the real board and hypothetical check-detection copies, so both always reflect these side effects consistently. */
    private void applyMove(char[] b, int from, int to)
    {
        char piece = b[from];
        char pieceType = Character.toUpperCase(piece);
        boolean isWhite = Character.isUpperCase(piece);

        if (pieceType == 'P' && (to % 8) != (from % 8) && b[to] == '.')
        {
            int capturedPawnSquare = isWhite ? to - 8 : to + 8;
            b[capturedPawnSquare] = '.';
        }

        b[to] = piece;
        b[from] = '.';

        if (pieceType == 'P' && (to / 8 == 0 || to / 8 == 7))
        {
            b[to] = isWhite ? 'Q' : 'q';
        }

        if (pieceType == 'K' && Math.abs((to % 8) - (from % 8)) == 2)
        {
            int row = from / 8;
            if (to % 8 == 6)
            {
                b[row * 8 + 5] = b[row * 8 + 7];
                b[row * 8 + 7] = '.';
            }
            else if (to % 8 == 2)
            {
                b[row * 8 + 3] = b[row * 8 + 0];
                b[row * 8 + 0] = '.';
            }
        }
    }

    private boolean isKingInCheck(boolean whiteKing)
    {
        return isKingInCheckOnBoard(board, whiteKing);
    }

    private boolean isKingInCheckOnBoard(char[] b, boolean whiteKing)
    {
        char kingChar = whiteKing ? 'K' : 'k';
        int kingSquare = -1;
        for (int i = 0; i < 64; i++)
        {
            if (b[i] == kingChar)
            {
                kingSquare = i;
                break;
            }
        }
        if (kingSquare == -1)
        {
            return false;
        }
        return isSquareAttacked(kingSquare, !whiteKing, b);
    }

    private boolean isSquareAttacked(int square, boolean byWhite, char[] b)
    {
        for (int i = 0; i < 64; i++)
        {
            char piece = b[i];
            if (piece == '.' || Character.isUpperCase(piece) != byWhite)
            {
                continue;
            }
            char pieceType = Character.toUpperCase(piece);
            // King-castling reach shouldn't itself count as "attacking" a square (avoids
            // infinite recursion through canCastle's own check-safety tests) - a bare
            // one-step King threat is all that matters for attack-scanning purposes.
            if (pieceType == 'K')
            {
                int dr = Math.abs(i / 8 - square / 8);
                int dc = Math.abs(i % 8 - square % 8);
                if (dr <= 1 && dc <= 1 && (dr != 0 || dc != 0))
                {
                    return true;
                }
                continue;
            }
            if (pieceCanReach(i, square, b))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyLegalMove(boolean isWhite)
    {
        for (int from = 0; from < 64; from++)
        {
            char piece = board[from];
            if (piece == '.' || Character.isUpperCase(piece) != isWhite)
            {
                continue;
            }
            for (int to = 0; to < 64; to++)
            {
                if (from == to)
                {
                    continue;
                }
                if (pieceCanReach(from, to, board))
                {
                    char target = board[to];
                    if (target != '.' && Character.isUpperCase(target) == isWhite)
                    {
                        continue;
                    }
                    char[] copy = board.clone();
                    applyMove(copy, from, to);
                    if (!isKingInCheckOnBoard(copy, isWhite))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void broadcastUpdate()
    {
        sendUpdate(whitePlayer);
        sendUpdate(blackPlayer);
        for (int i = 0; i < spectators.size(); i++)
        {
            sendUpdate(spectators.get(i));
        }
    }

    /** Adds a read-only watcher and immediately sends them the current board state, so they don't have to wait for the next move to see anything. Spectators never call makeMove() (only whitePlayer/blackPlayer are ever checked there), so there's nothing else needed to keep them read-only. */
    public synchronized void addSpectator(ClientHandler spectator)
    {
        spectators.add(spectator);
        sendUpdate(spectator);
    }

    public String getWhiteUsername() { return whitePlayer.getLoggedInUsername(); }
    public String getBlackUsername() { return blackPlayer.getLoggedInUsername(); }

    private void sendUpdate(ClientHandler to)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHESS_UPDATE);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());
        msg.setSymbol(whiteTurn ? "WHITE" : "BLACK");
        to.sendMessage(msg);
    }

    /** winnerColor is null for a stalemate (draw); otherwise "WHITE" or "BLACK" - checkmate. */
    private void broadcastResult(String winnerColor)
    {
        recordRating(winnerColor);
        saveReplay(winnerColor);
        sendResult(whitePlayer, winnerColor);
        sendResult(blackPlayer, winnerColor);
        notifySpectatorsEnded();
    }

    private void notifySpectatorsEnded()
    {
        for (int i = 0; i < spectators.size(); i++)
        {
            Message ended = new Message();
            ended.setType(MessageType.SPECTATE_ENDED);
            ended.setMatchId(matchId);
            spectators.get(i).sendMessage(ended);
        }
    }

    private void saveReplay(String winnerColor)
    {
        if (replayManager == null)
        {
            return;
        }
        String result = winnerColor == null ? "DRAW" : winnerColor;
        replayManager.save("chess", whitePlayer.getLoggedInUsername(), blackPlayer.getLoggedInUsername(), result, snapshots);
    }

    private void recordRating(String winnerColor)
    {
        if (leaderboardManager == null || whitePlayer.getAccountId() == null || blackPlayer.getAccountId() == null)
        {
            return;
        }
        double outcomeForWhite = winnerColor == null ? 0.5 : "WHITE".equals(winnerColor) ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch("chess", whitePlayer.getAccountId(), blackPlayer.getAccountId(), outcomeForWhite);
    }

    private void sendResult(ClientHandler to, String winnerColor)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHESS_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setBoardState(boardString());

        if (winnerColor == null)
        {
            msg.setMatchResult("DRAW");
        }
        else
        {
            boolean toIsWhite = to == whitePlayer;
            boolean toIsWinner = ("WHITE".equals(winnerColor) && toIsWhite) || ("BLACK".equals(winnerColor) && !toIsWhite);
            msg.setMatchResult(toIsWinner ? "WIN" : "LOSE");
        }
        to.sendMessage(msg);
    }

    private void sendRejected(ClientHandler to, String reason)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHESS_MOVE_REJECTED);
        msg.setMatchId(matchId);
        msg.setErrorText(reason);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void resign(ClientHandler requester)
    {
        if (over)
        {
            return;
        }
        boolean requesterIsWhite = requester == whitePlayer;
        over = true;
        recordRating(requesterIsWhite ? "BLACK" : "WHITE");
        saveReplay(requesterIsWhite ? "BLACK" : "WHITE");
        sendEndResult(whitePlayer, requesterIsWhite ? "LOSE" : "WIN", "RESIGNED");
        sendEndResult(blackPlayer, requesterIsWhite ? "WIN" : "LOSE", "RESIGNED");
        notifySpectatorsEnded();
        matchManager.endMatch(matchId);
    }

    /** Relays the offer to the other player - doesn't end anything itself, that only happens if they accept. A fresh offer can't be sent while one is already pending, and offering doesn't cost you your move. */
    public synchronized void offerDraw(ClientHandler requester)
    {
        if (over || drawOfferPending)
        {
            return;
        }
        drawOfferPending = true;
        ClientHandler recipient = (requester == whitePlayer) ? blackPlayer : whitePlayer;

        Message msg = new Message();
        msg.setType(MessageType.CHESS_DRAW_OFFERED);
        msg.setMatchId(matchId);
        recipient.sendMessage(msg);
    }

    public synchronized void respondToDraw(ClientHandler requester, boolean accept)
    {
        if (over || !drawOfferPending)
        {
            return;
        }
        drawOfferPending = false;

        if (accept)
        {
            over = true;
            recordRating(null);
            saveReplay(null);
            sendEndResult(whitePlayer, "DRAW", "DRAW_AGREED");
            sendEndResult(blackPlayer, "DRAW", "DRAW_AGREED");
            notifySpectatorsEnded();
            matchManager.endMatch(matchId);
        }
        else
        {
            ClientHandler offerer = (requester == whitePlayer) ? blackPlayer : whitePlayer;
            Message msg = new Message();
            msg.setType(MessageType.CHESS_DRAW_DECLINED);
            msg.setMatchId(matchId);
            offerer.sendMessage(msg);
        }
    }

    /** Same shape as handleDisconnect's own OPPONENT_LEFT message - a labeled end reason distinct from the plain WIN/LOSE/DRAW that checkmate/stalemate use, so the client doesn't have to guess (or worse, wrongly say "Checkmate!" for a resignation). */
    private void sendEndResult(ClientHandler to, String result, String endReason)
    {
        Message msg = new Message();
        msg.setType(MessageType.CHESS_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult(result);
        msg.setErrorText(endReason);
        msg.setBoardState(boardString());
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over)
        {
            return;
        }
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == whitePlayer) ? blackPlayer : whitePlayer;
        Message msg = new Message();
        msg.setType(MessageType.CHESS_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        msg.setBoardState(boardString());
        remaining.sendMessage(msg);
        notifySpectatorsEnded();
    }

    private String boardString()
    {
        return new String(board);
    }
}
