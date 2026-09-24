package games;

import ai.BotStrategy;

/**
 * WordDuelBotStrategy
 * --------------------
 * Word Duel's practice-mode "opponent" - not built on the ai.search
 * engine (Connect Four/Reversi/Dots and Boxes/Checkers/Chess/Signal
 * Grid all use), since Word Duel isn't adversarial or turn-based at
 * all: both players work independently against the same fixed letter
 * draw within a time limit, so there's no opponent move to search
 * against or react to - just "find the best word from these letters,"
 * a plain search problem. Stateless (letters are the only input a
 * decision ever needs), so one shared instance is safe to reuse
 * across every practice round, same as TicTacToePracticeBotStrategy.
 */
public class WordDuelBotStrategy implements BotStrategy<String, String>
{
    @Override
    public String chooseMove(String letters)
    {
        String best = WordDuelWordList.bestWordFrom(letters);
        return best != null ? best : "";
    }
}
