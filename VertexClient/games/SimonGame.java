package games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SimonGame
 * ---------
 * Standard Simon Says memory-sequence rules, an original
 * implementation of the well-known, uncopyrightable game. The game
 * shows a growing sequence of 4 colors, one at a time; the player
 * repeats it back in the same order. Get it right and the sequence
 * grows by one more random color; get it wrong and the game ends.
 * Score is the length of the longest sequence successfully repeated.
 */
public class SimonGame
{
    public static final int COLOR_COUNT = 4;

    private final List<Integer> sequence = new ArrayList<Integer>();
    private final Random random = new Random();
    private int playerProgress = 0;
    private boolean gameOver = false;

    public SimonGame()
    {
        extendSequence();
    }

    public List<Integer> getSequence() { return sequence; }
    public boolean isGameOver() { return gameOver; }
    public int getScore() { return sequence.size() - 1; }

    private void extendSequence()
    {
        sequence.add(random.nextInt(COLOR_COUNT));
        playerProgress = 0;
    }

    /** Returns true if this pick was correct and the round should continue; false ends the game. Correctly repeating the whole sequence extends it by one more color for next round. */
    public boolean submitPick(int colorIndex)
    {
        if (gameOver) return false;

        if (sequence.get(playerProgress) != colorIndex)
        {
            gameOver = true;
            return false;
        }

        playerProgress++;
        if (playerProgress == sequence.size())
        {
            extendSequence();
        }
        return true;
    }
}
