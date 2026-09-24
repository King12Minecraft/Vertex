package games;

import ai.search.GameModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ChessGameModel
 * --------------
 * The one file Chess needs to plug into the shared ai.search engine -
 * describes this game's rules only. This is the hardest of the games
 * on this engine so far: castling rights and en passant live outside
 * the board array (see ChessState), and checkmate/stalemate depend on
 * whose turn it is, not just the board. Every rule here mirrors
 * ChessMatch's own proven logic exactly (pieceCanReach, canCastle,
 * isPathClear, applyMove's side effects, isKingInCheckOnBoard,
 * isSquareAttacked) - a separate, decoupled port rather than a
 * rewrite from scratch, since ChessMatch itself is network-coupled
 * and its instance-field-based castling/en-passant state isn't
 * something a generic search can safely explore hypothetical futures
 * with directly.
 *
 * Player 0 = WHITE (moves first), player 1 = BLACK - same convention
 * ChessMatch uses. Pawns always auto-promote to a queen, matching
 * ChessMatch's own simplification.
 */
public class ChessGameModel implements GameModel<ChessState, ChessMove>
{
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    public static ChessState newBoard()
    {
        char[] board = new char[64];
        String backRank = "RNBQKBNR";
        for (int c = 0; c < 8; c++)
        {
            board[c] = backRank.charAt(c);
            board[8 + c] = 'P';
            board[48 + c] = 'p';
            board[56 + c] = Character.toLowerCase(backRank.charAt(c));
            for (int r = 2; r <= 5; r++)
            {
                board[r * 8 + c] = '.';
            }
        }
        return new ChessState(board, WHITE, false, false, false, false, false, false, -1);
    }

    @Override
    public List<ChessMove> legalMoves(ChessState state, int playerIndex)
    {
        boolean isWhite = playerIndex == WHITE;
        char[] b = state.board;
        List<ChessMove> moves = new ArrayList<ChessMove>();

        for (int from = 0; from < 64; from++)
        {
            char piece = b[from];
            if (piece == '.' || Character.isUpperCase(piece) != isWhite) continue;

            for (int to = 0; to < 64; to++)
            {
                if (from == to) continue;
                if (!pieceCanReach(b, from, to, state)) continue;

                char target = b[to];
                if (target != '.' && Character.isUpperCase(target) == isWhite) continue;

                char[] copy = b.clone();
                applyRawMove(copy, from, to);
                if (!isKingInCheckOnBoard(copy, isWhite, state))
                {
                    moves.add(new ChessMove(from, to));
                }
            }
        }
        return moves;
    }

    @Override
    public ChessState applyMove(ChessState state, ChessMove move, int playerIndex)
    {
        char[] b = state.board.clone();
        char piece = b[move.from];
        char upper = Character.toUpperCase(piece);
        boolean isWhite = Character.isUpperCase(piece);

        boolean whiteKingMoved = state.whiteKingMoved || (upper == 'K' && isWhite);
        boolean blackKingMoved = state.blackKingMoved || (upper == 'K' && !isWhite);
        boolean whiteRookAMoved = state.whiteRookAMoved || move.from == 0;
        boolean whiteRookHMoved = state.whiteRookHMoved || move.from == 7;
        boolean blackRookAMoved = state.blackRookAMoved || move.from == 56;
        boolean blackRookHMoved = state.blackRookHMoved || move.from == 63;

        int nextEnPassantTarget = -1;
        if (upper == 'P' && Math.abs(move.to / 8 - move.from / 8) == 2)
        {
            nextEnPassantTarget = (move.from + move.to) / 2;
        }

        applyRawMove(b, move.from, move.to);

        return new ChessState(b, 1 - playerIndex, whiteKingMoved, blackKingMoved,
            whiteRookAMoved, whiteRookHMoved, blackRookAMoved, blackRookHMoved, nextEnPassantTarget);
    }

    @Override
    public boolean isTerminal(ChessState state)
    {
        return legalMoves(state, state.turnPlayer).isEmpty();
    }

    @Override
    public int nextPlayer(ChessState state, int playerJustMoved)
    {
        return 1 - playerJustMoved;
    }

    @Override
    public Integer winner(ChessState state)
    {
        boolean isWhite = state.turnPlayer == WHITE;
        boolean inCheck = isKingInCheckOnBoard(state.board, isWhite, state);
        if (!inCheck) return null; // stalemate - a draw
        return isWhite ? BLACK : WHITE; // side to move is checkmated
    }

    @Override
    public int evaluate(ChessState state, int forPlayerIndex)
    {
        if (isTerminal(state))
        {
            Integer winner = winner(state);
            if (winner == null) return 0;
            return winner == forPlayerIndex ? 1_000_000 : -1_000_000;
        }

        boolean myWhite = forPlayerIndex == WHITE;
        int score = 0;
        for (char piece : state.board)
        {
            if (piece == '.') continue;
            int value = pieceValue(Character.toUpperCase(piece));
            boolean pieceIsWhite = Character.isUpperCase(piece);
            score += (pieceIsWhite == myWhite) ? value : -value;
        }
        return score;
    }

    private static int pieceValue(char type)
    {
        switch (type)
        {
            case 'P': return 100;
            case 'N': return 320;
            case 'B': return 330;
            case 'R': return 500;
            case 'Q': return 900;
            default:  return 0; // king - always present on both sides, contributes nothing to material difference
        }
    }

    /** Pattern-only legality (ignores whose turn it is and check) - mirrors ChessMatch.pieceCanReach exactly, with enPassantTarget/castling rights threaded through explicitly via `rights` instead of read from instance fields. */
    private static boolean pieceCanReach(char[] b, int from, int to, ChessState rights)
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
            if (Math.abs(dc) == 1 && dr == dir && b[to] == '.' && to == rights.enPassantTarget)
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
                return canCastle(b, from, to, isWhite, rights);
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

    private static boolean canCastle(char[] b, int from, int to, boolean isWhite, ChessState rights)
    {
        int row = isWhite ? 0 : 7;
        if (from != row * 8 + 4)
        {
            return false;
        }
        boolean kingMoved = isWhite ? rights.whiteKingMoved : rights.blackKingMoved;
        if (kingMoved)
        {
            return false;
        }
        if (isKingInCheckOnBoard(b, isWhite, rights))
        {
            return false;
        }

        boolean kingside = to % 8 == 6;
        if (kingside)
        {
            boolean rookMoved = isWhite ? rights.whiteRookHMoved : rights.blackRookHMoved;
            char expectedRook = isWhite ? 'R' : 'r';
            if (rookMoved || b[row * 8 + 5] != '.' || b[row * 8 + 6] != '.' || b[row * 8 + 7] != expectedRook)
            {
                return false;
            }
            return !isSquareAttacked(row * 8 + 5, !isWhite, b, rights) && !isSquareAttacked(row * 8 + 6, !isWhite, b, rights);
        }

        boolean queenside = to % 8 == 2;
        if (queenside)
        {
            boolean rookMoved = isWhite ? rights.whiteRookAMoved : rights.blackRookAMoved;
            char expectedRook = isWhite ? 'R' : 'r';
            if (rookMoved || b[row * 8 + 1] != '.' || b[row * 8 + 2] != '.' || b[row * 8 + 3] != '.'
                || b[row * 8 + 0] != expectedRook)
            {
                return false;
            }
            return !isSquareAttacked(row * 8 + 3, !isWhite, b, rights) && !isSquareAttacked(row * 8 + 2, !isWhite, b, rights);
        }

        return false;
    }

    private static boolean isPathClear(int from, int to, char[] b)
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

    /** Executes a move on the given board - including the side effects of castling (rook also moves) and en passant (the captured pawn, not on the destination square, is also removed) and promotion. Mirrors ChessMatch.applyMove exactly - it needs no enPassantTarget/castling-rights parameter at all, since en passant is detected here purely from "a pawn moved diagonally onto an empty square," which can only happen via en passant. */
    private static void applyRawMove(char[] b, int from, int to)
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

    private static boolean isKingInCheckOnBoard(char[] b, boolean whiteKing, ChessState rights)
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
        return isSquareAttacked(kingSquare, !whiteKing, b, rights);
    }

    private static boolean isSquareAttacked(int square, boolean byWhite, char[] b, ChessState rights)
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
            if (pieceCanReach(b, i, square, rights))
            {
                return true;
            }
        }
        return false;
    }
}
