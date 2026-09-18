package games;

/**
 * PegSolitaireGame
 * ------------------
 * An original implementation of the centuries-old, public-domain "peg
 * solitaire" board game (the classic English 33-hole cross board) -
 * jump a peg over an adjacent peg into an empty hole two cells away,
 * removing the jumped peg, until no more jumps are possible. No
 * relation to any specific existing game's code or visual design; the
 * board layout itself (a plus-shaped cross of holes) is the standard,
 * centuries-old one, not anyone's original creation.
 *
 * Turn-based like Word Guess/Lights Out - no Swing Timer, the window
 * just repaints after each click. Selection is two-step: click a peg to
 * select it, then click a hole two cells away in the same row/column to
 * jump there (removing the peg in between); clicking anywhere invalid
 * just clears the current selection instead of erroring.
 */
public class PegSolitaireGame
{
    public static final int SIZE = 7;

    private final boolean[][] valid = new boolean[SIZE][SIZE];
    private final boolean[][] peg = new boolean[SIZE][SIZE];

    private int selectedRow = -1, selectedCol = -1;
    private int pegsRemoved;

    public PegSolitaireGame()
    {
        for (int r = 0; r < SIZE; r++)
        {
            for (int c = 0; c < SIZE; c++)
            {
                boolean cornerBlock = (r < 2 || r > 4) && (c < 2 || c > 4);
                valid[r][c] = !cornerBlock;
                peg[r][c] = valid[r][c];
            }
        }
        peg[3][3] = false; // classic English-board start: every hole filled except the center
    }

    public boolean isValidCell(int row, int col) { return valid[row][col]; }
    public boolean hasPeg(int row, int col) { return valid[row][col] && peg[row][col]; }
    public int getSelectedRow() { return selectedRow; }
    public int getSelectedCol() { return selectedCol; }
    public boolean hasSelection() { return selectedRow >= 0; }
    public int getPegsRemoved() { return pegsRemoved; }

    public int getPegsRemaining()
    {
        int count = 0;
        for (int r = 0; r < SIZE; r++)
        {
            for (int c = 0; c < SIZE; c++)
            {
                if (peg[r][c]) count++;
            }
        }
        return count;
    }

    public boolean isOver()
    {
        return !anyLegalMoveExists();
    }

    public boolean isWon()
    {
        return isOver() && getPegsRemaining() == 1;
    }

    public boolean isPerfectFinish()
    {
        return isWon() && peg[3][3];
    }

    /** A single click either selects a peg, attempts a jump if a peg's already selected, or clears the selection - never throws on an out-of-range or invalid click, it just treats it as "nothing useful here". */
    public void click(int row, int col)
    {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE || !valid[row][col]) return;
        if (isOver()) return;

        if (!hasSelection())
        {
            if (hasPeg(row, col))
            {
                selectedRow = row;
                selectedCol = col;
            }
            return;
        }

        if (row == selectedRow && col == selectedCol)
        {
            clearSelection();
            return;
        }

        if (tryJump(selectedRow, selectedCol, row, col))
        {
            clearSelection();
            return;
        }

        // Not a legal jump from the current selection - if the new cell has a
        // peg, just re-select it there instead; otherwise drop the selection.
        clearSelection();
        if (hasPeg(row, col))
        {
            selectedRow = row;
            selectedCol = col;
        }
    }

    private void clearSelection()
    {
        selectedRow = -1;
        selectedCol = -1;
    }

    private boolean tryJump(int fromRow, int fromCol, int toRow, int toCol)
    {
        if (!peg[fromRow][fromCol] || peg[toRow][toCol] || !valid[toRow][toCol]) return false;

        int dr = toRow - fromRow, dc = toCol - fromCol;
        boolean straightLineTwoAway = (dr == 0 && Math.abs(dc) == 2) || (dc == 0 && Math.abs(dr) == 2);
        if (!straightLineTwoAway) return false;

        int midRow = fromRow + dr / 2, midCol = fromCol + dc / 2;
        if (!valid[midRow][midCol] || !peg[midRow][midCol]) return false;

        peg[fromRow][fromCol] = false;
        peg[midRow][midCol] = false;
        peg[toRow][toCol] = true;
        pegsRemoved++;
        return true;
    }

    private boolean anyLegalMoveExists()
    {
        int[][] dirs = new int[][] { {0, 2}, {0, -2}, {2, 0}, {-2, 0} };
        for (int r = 0; r < SIZE; r++)
        {
            for (int c = 0; c < SIZE; c++)
            {
                if (!peg[r][c]) continue;
                for (int[] d : dirs)
                {
                    int toRow = r + d[0], toCol = c + d[1];
                    int midRow = r + d[0] / 2, midCol = c + d[1] / 2;
                    if (toRow < 0 || toRow >= SIZE || toCol < 0 || toCol >= SIZE) continue;
                    if (!valid[toRow][toCol] || peg[toRow][toCol]) continue;
                    if (!valid[midRow][midCol] || !peg[midRow][midCol]) continue;
                    return true;
                }
            }
        }
        return false;
    }

    /** More pegs cleared is worth more, with a solid bonus for the classic "down to one, and it's in the center" perfect finish. */
    public int getScore()
    {
        int score = pegsRemoved * 15;
        if (isWon()) score += 150;
        if (isPerfectFinish()) score += 150;
        return score;
    }
}
