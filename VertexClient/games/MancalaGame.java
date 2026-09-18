package games;

/**
 * MancalaGame
 * -----------
 * An original implementation of "Kalah" - the modern, standard variant
 * of the ancient, centuries-old public-domain Mancala family of sowing
 * games. The board layout (two rows of 6 pits plus a store/kalah at
 * each end), sowing direction, capture-on-empty-landing rule, and
 * bonus-turn-on-own-store rule are all standard, generic rules of this
 * game family - not anyone's copyrightable code or expression. This is
 * a from-scratch implementation played against a simple built-in AI,
 * not a copy of any specific existing Mancala program.
 *
 * Board layout (pit indices 0-13, going counter-clockwise):
 *   0-5   = player's 6 pits
 *   6     = player's store
 *   7-12  = AI's 6 pits
 *   13    = AI's store
 *
 * Turn-based like Lights Out/Peg Solitaire - no Swing Timer. A move is
 * a single call to playerMove(pitIndex); the AI's reply (if any) is
 * resolved synchronously inside that same call so the window only ever
 * needs to repaint once per player action.
 */
public class MancalaGame
{
    public static final int PITS_PER_SIDE = 6;
    public static final int PLAYER_STORE = 6;
    public static final int AI_STORE = 13;
    public static final int BOARD_SIZE = 14;
    private static final int STARTING_SEEDS = 4;

    private final int[] pits = new int[BOARD_SIZE];
    private boolean playerTurn = true;
    private boolean gameOver;
    private String lastMessage = "";

    public MancalaGame()
    {
        for (int i = 0; i < BOARD_SIZE; i++)
        {
            pits[i] = (i == PLAYER_STORE || i == AI_STORE) ? 0 : STARTING_SEEDS;
        }
    }

    public int getPit(int index) { return pits[index]; }
    public boolean isPlayerTurn() { return playerTurn; }
    public boolean isOver() { return gameOver; }
    public String getLastMessage() { return lastMessage; }
    public int getPlayerScore() { return pits[PLAYER_STORE]; }
    public int getAiScore() { return pits[AI_STORE]; }

    public boolean isValidPlayerMove(int pit)
    {
        return !gameOver && playerTurn && pit >= 0 && pit < PITS_PER_SIDE && pits[pit] > 0;
    }

    /** Plays the given pit for the human player, then - if the turn passes to the AI - resolves the AI's move(s) synchronously before returning, so the caller only needs to repaint once. */
    public void playerMove(int pit)
    {
        if (!isValidPlayerMove(pit)) return;

        boolean bonusTurn = sow(pit, true);
        lastMessage = "";
        checkGameOver();
        if (gameOver) return;

        if (bonusTurn)
        {
            lastMessage = "You landed in your store - go again!";
            return; // still the player's turn
        }

        playerTurn = false;
        // The AI keeps taking bonus turns (landing in its own store) until it either
        // ends its turn normally or the game ends - exactly the same rule as the player.
        while (!playerTurn && !gameOver)
        {
            int aiPit = chooseAiMove();
            if (aiPit < 0) { playerTurn = true; break; } // no legal move somehow - hand back to player defensively
            boolean aiBonus = sow(aiPit, false);
            checkGameOver();
            if (gameOver) return;
            if (!aiBonus) playerTurn = true;
        }
    }

    /**
     * Sows the seeds from the given pit around the board, dropping one in
     * every pit passed (skipping the opponent's store), and applies the
     * capture rule if the last seed lands in a previously-empty pit on the
     * mover's own side. Returns true if the mover earned a bonus turn by
     * landing their final seed exactly in their own store.
     */
    private boolean sow(int startPit, boolean isPlayer)
    {
        int seeds = pits[startPit];
        pits[startPit] = 0;

        int ownStore = isPlayer ? PLAYER_STORE : AI_STORE;
        int opponentStore = isPlayer ? AI_STORE : PLAYER_STORE;

        int index = startPit;
        int lastIndex = -1;
        while (seeds > 0)
        {
            index = (index + 1) % BOARD_SIZE;
            if (index == opponentStore) continue; // skip the opponent's store entirely
            pits[index]++;
            seeds--;
            lastIndex = index;
        }

        if (lastIndex == ownStore)
        {
            return true; // bonus turn
        }

        // Capture rule: if the last seed landed in a pit that was empty just before
        // this seed (i.e. it now holds exactly 1) and that pit is on the mover's own
        // side (not either store), sweep it plus the directly-opposite pit into the
        // mover's store.
        boolean lastOnOwnSide = isPlayer
            ? (lastIndex >= 0 && lastIndex < PITS_PER_SIDE)
            : (lastIndex >= PITS_PER_SIDE + 1 && lastIndex < AI_STORE);
        if (lastOnOwnSide && pits[lastIndex] == 1)
        {
            int oppositeIndex = oppositePitOf(lastIndex);
            if (pits[oppositeIndex] > 0)
            {
                pits[ownStore] += pits[lastIndex] + pits[oppositeIndex];
                pits[lastIndex] = 0;
                pits[oppositeIndex] = 0;
            }
        }

        return false;
    }

    private int oppositePitOf(int index)
    {
        // Pit i on the player's side (0-5) sits opposite AI pit (12 - i), and vice versa -
        // the two rows of 6 face each other and index 0/7 are the two pits nearest the stores.
        return 12 - index;
    }

    /** Very simple greedy AI: prefer a move that lands exactly in its own store (bonus turn); otherwise prefer a move that captures the most seeds; otherwise just play the pit with the most seeds. Never throws if the AI has no legal move - returns -1. */
    private int chooseAiMove()
    {
        int bestPit = -1;
        int bestPriority = -1; // 2 = bonus turn, 1 = capture, 0 = plain move
        int bestSecondary = -1;

        for (int pit = PITS_PER_SIDE + 1; pit < AI_STORE; pit++)
        {
            if (pits[pit] <= 0) continue;

            int seeds = pits[pit];
            int landing = (pit + seeds) % BOARD_SIZE;
            // Account for skipping the player's store while walking around, same as sow() does.
            int stepsNeeded = seeds;
            int index = pit;
            while (stepsNeeded > 0)
            {
                index = (index + 1) % BOARD_SIZE;
                if (index == PLAYER_STORE) continue;
                stepsNeeded--;
            }
            landing = index;

            int priority;
            int secondary;
            if (landing == AI_STORE)
            {
                priority = 2;
                secondary = seeds;
            }
            else if (landing > PITS_PER_SIDE && landing < AI_STORE && pits[landing] == 0)
            {
                int opposite = oppositePitOf(landing);
                int captureAmount = pits[opposite]; // +1 for the landed seed itself, but relative comparisons only need this
                priority = 1;
                secondary = captureAmount;
            }
            else
            {
                priority = 0;
                secondary = seeds;
            }

            if (priority > bestPriority || (priority == bestPriority && secondary > bestSecondary))
            {
                bestPriority = priority;
                bestSecondary = secondary;
                bestPit = pit;
            }
        }

        return bestPit;
    }

    private void checkGameOver()
    {
        boolean playerEmpty = true, aiEmpty = true;
        for (int i = 0; i < PITS_PER_SIDE; i++) if (pits[i] > 0) playerEmpty = false;
        for (int i = PITS_PER_SIDE + 1; i < AI_STORE; i++) if (pits[i] > 0) aiEmpty = false;

        if (playerEmpty || aiEmpty)
        {
            // Whichever side still has seeds sweeps them all into their own store.
            for (int i = 0; i < PITS_PER_SIDE; i++) { pits[PLAYER_STORE] += pits[i]; pits[i] = 0; }
            for (int i = PITS_PER_SIDE + 1; i < AI_STORE; i++) { pits[AI_STORE] += pits[i]; pits[i] = 0; }
            gameOver = true;
        }
    }

    public boolean didPlayerWin() { return gameOver && pits[PLAYER_STORE] > pits[AI_STORE]; }
    public boolean isTie() { return gameOver && pits[PLAYER_STORE] == pits[AI_STORE]; }

    /** Winning margin scales the score; a tie or loss still gives a small consolation amount for having played, floored at 0. */
    public int getScore()
    {
        if (!gameOver) return 0;
        int margin = pits[PLAYER_STORE] - pits[AI_STORE];
        if (margin > 0) return 100 + margin * 10;
        if (margin == 0) return 40;
        return Math.max(0, 20 + margin * 2);
    }
}
