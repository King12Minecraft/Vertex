package games;

import ai.AiKernel;

import java.util.Arrays;

/**
 * TicTacToePracticeMatch
 * -----------------------
 * A best-of-N series against TicTacToeAI, running entirely locally -
 * no server, no network, works fully offline. Mirrors the shape of the
 * server's TicTacToeMatch (round/series scoring, win-line reporting)
 * so TicTacToeWindow can reuse the same rendering and result-display
 * logic for both online and practice play. The human always plays X.
 *
 * The AI's move is now chosen via AiKernel rather than calling
 * TicTacToeAI.pickMove() directly - the underlying decision logic is
 * unchanged (TicTacToePracticeBotStrategy just wraps the same call),
 * but routing it through the shared ai package is what makes this
 * game's AI a registered, reusable strategy other tooling can inspect
 * or swap, instead of a one-off hardcoded call. This is Vertex's first
 * game on the new shared AI layer.
 */
public class TicTacToePracticeMatch
{
    private static final String AI_GAME_ID = "tictactoe-practice";

    static
    {
        // Registered once per JVM when this class first loads. AiKernel.register()
        // is safe to call more than once for the same id, so no "already registered"
        // guard is needed here.
        AiKernel.register(AI_GAME_ID,
            new TicTacToePracticeBotStrategy('O', 'X'),
            new TicTacToeRandomMoveStrategy());
    }

    public static class RoundResult
    {
        public final String winnerSymbol;
        public final int[] winLine;
        public final boolean seriesOver;
        public final int roundNumber;
        public final int scoreHuman;
        public final int scoreAI;

        RoundResult(String winnerSymbol, int[] winLine, boolean seriesOver,
                    int roundNumber, int scoreHuman, int scoreAI)
        {
            this.winnerSymbol = winnerSymbol;
            this.winLine = winLine;
            this.seriesOver = seriesOver;
            this.roundNumber = roundNumber;
            this.scoreHuman = scoreHuman;
            this.scoreAI = scoreAI;
        }
    }

    private final char[] board = new char[9];
    private final int bestOf;
    private final int roundsToWin;
    private boolean humanTurn = true;
    private int roundNumber = 1;
    private int scoreHuman = 0;
    private int scoreAI = 0;
    private boolean seriesDecided = false;

    public TicTacToePracticeMatch(int bestOf)
    {
        this.bestOf = bestOf;
        this.roundsToWin = bestOf / 2 + 1;
        Arrays.fill(board, '.');
    }

    public char[] getBoard() { return board; }
    public boolean isHumanTurn() { return humanTurn; }
    public int getRoundNumber() { return roundNumber; }
    public int getBestOf() { return bestOf; }
    public int getScoreHuman() { return scoreHuman; }
    public int getScoreAI() { return scoreAI; }
    public boolean isSeriesDecided() { return seriesDecided; }

    /** Attempts the human's move (always X). Returns a RoundResult if the round just ended, null if play continues. */
    public RoundResult humanMove(int index)
    {
        if (seriesDecided || !humanTurn || board[index] != '.')
        {
            return null;
        }
        board[index] = 'X';
        return checkAfterMove('X');
    }

    /** Plays the AI's move (always O). Only call when it's genuinely the AI's turn. */
    public RoundResult aiMove()
    {
        if (seriesDecided || humanTurn)
        {
            return null;
        }
        int move = AiKernel.<char[], Integer>chooseMove(AI_GAME_ID, board);
        board[move] = 'O';
        return checkAfterMove('O');
    }

    private RoundResult checkAfterMove(char justMoved)
    {
        int[] winLine = TicTacToeAI.checkWinner(board);
        boolean draw = winLine == null && !TicTacToeAI.hasEmptyCell(board);

        if (winLine == null && !draw)
        {
            humanTurn = !humanTurn;
            return null;
        }

        String winnerSymbol = winLine != null ? String.valueOf(justMoved) : null;
        if ("X".equals(winnerSymbol))
        {
            scoreHuman++;
        }
        else if ("O".equals(winnerSymbol))
        {
            scoreAI++;
        }

        boolean decided = scoreHuman >= roundsToWin || scoreAI >= roundsToWin || roundNumber >= bestOf;
        seriesDecided = decided;

        RoundResult result = new RoundResult(winnerSymbol, winLine, decided, roundNumber, scoreHuman, scoreAI);

        if (!decided)
        {
            roundNumber++;
            Arrays.fill(board, '.');
            humanTurn = (roundNumber % 2 == 1);
        }

        return result;
    }
}
