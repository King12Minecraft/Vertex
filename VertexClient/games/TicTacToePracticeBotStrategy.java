package games;

import ai.BotStrategy;

/**
 * TicTacToePracticeBotStrategy
 * -----------------------------
 * The "smart" AI opponent for Tic-Tac-Toe Practice Mode, wrapped as an
 * ai.BotStrategy so it can be registered with AiKernel instead of
 * being called directly. Doesn't reimplement any logic - it's a thin
 * adapter around the existing TicTacToeAI.pickMove(), which already
 * takes a winning move if one exists, blocks the human's winning move
 * otherwise, then prefers center/corner/anywhere-open. This is Vertex's
 * first game migrated onto the shared ai package, proving the seam
 * with a real game rather than a synthetic example.
 */
public class TicTacToePracticeBotStrategy implements BotStrategy<char[], Integer>
{
    private final char aiSymbol;
    private final char humanSymbol;

    public TicTacToePracticeBotStrategy(char aiSymbol, char humanSymbol)
    {
        this.aiSymbol = aiSymbol;
        this.humanSymbol = humanSymbol;
    }

    public Integer chooseMove(char[] board)
    {
        return TicTacToeAI.pickMove(board, aiSymbol, humanSymbol);
    }
}
