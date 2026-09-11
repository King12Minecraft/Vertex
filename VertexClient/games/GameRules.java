package games;

import java.util.HashMap;
import java.util.Map;

/**
 * GameRules
 * ---------
 * A short "how to play" blurb per game, shown by GameRulesDialog from
 * a Rules button on every game card. Plain text written for this
 * project - covers the controls and win condition, not a full manual.
 */
public class GameRules
{
    private static final Map<String, String> RULES = new HashMap<String, String>();

    static
    {
        RULES.put("snake", "Steer with the arrow keys or WASD. Eat the food to grow and score - "
            + "running into a wall or your own tail ends the run. Press P to pause.");

        RULES.put("tictactoe-online", "Classic 3x3 grid, standard rules - take turns placing your "
            + "symbol, first to get three in a row (any direction) wins. A full board with no line "
            + "is a draw.");

        RULES.put("square-wars", "Click any cell on the shared grid to claim it for your color - "
            + "whether it's empty or an opponent's, it's yours the instant you click it. After 60 "
            + "seconds, whoever owns the most cells wins.");

        RULES.put("racing", "3-6 players race the same track. Arrow keys/WASD to steer and "
            + "accelerate. Grab shields (temporary protection), speed boosts, and coins along the "
            + "way. Ranked by finishing position.");

        RULES.put("puzzle-quest", "A sliding/logic puzzle - solve it in as few moves as possible. "
            + "Fewer moves is better, though there's no penalty for taking your time.");

        RULES.put("rock-paper-scissors", "Best of 5 rounds. Both players pick Rock, Paper, or "
            + "Scissors at the same time (blind - you can't see your opponent's choice until both "
            + "have picked). Rock beats Scissors, Scissors beats Paper, Paper beats Rock. First to "
            + "3 round wins takes the match.");

        RULES.put("pingpong", "Move your paddle up and down (arrow keys or W/S) to keep the ball "
            + "in play. Score by getting the ball past your opponent's paddle.");

        RULES.put("2048", "Arrow keys slide every tile on the board in that direction. Two tiles "
            + "with the same number merge into one with double the value when they collide. Reach "
            + "2048 to win - the board fills up fast, so plan your merges.");

        RULES.put("dino-dash", "An endless runner - press Space (or Up) to jump over obstacles. "
            + "Speed increases the longer you survive. Press P to pause.");

        RULES.put("tetris", "Standard Tetris - arrow keys to move/rotate falling pieces, Down to "
            + "drop faster. Clear full horizontal lines to score; the board filling to the top ends "
            + "the game. Press P to pause.");

        RULES.put("crossing-road", "Guide your character across lanes of moving traffic (and other "
            + "hazards) using the arrow keys. Getting hit ends the run - go as far as you can.");

        RULES.put("aim-trainer", "20 rounds - a target appears somewhere on screen and you have a "
            + "short window to click it before it disappears. Your score is how many you hit.");

        RULES.put("among-us", "A small group plays several rounds of social deduction - among the "
            + "group, one or more players are secretly impostors trying to avoid detection while "
            + "everyone else tries to identify them through discussion and voting.");

        RULES.put("zombie-survival", "2-4 players each fight the same seeded sequence of zombie "
            + "waves independently (WASD to move, aim and fire with the mouse). Survive all 8 waves "
            + "for the full reward - dying early still counts your kills.");

        RULES.put("fight-arena", "1v1, 2v2, 3v3, or free-for-all melee combat. Move and attack with "
            + "the controls shown in-match. Most knockouts (FFA) or your team's total knockouts "
            + "(team modes) wins.");

        RULES.put("chess", "Standard chess rules, including castling and en passant. Checkmate your "
            + "opponent's king to win; you can also resign or offer a draw mid-game.");

        RULES.put("battleship", "Classic hunt-and-sink. Place your fleet, then take turns calling "
            + "shots at your opponent's hidden grid. Sink every one of their ships before they sink "
            + "yours.");

        RULES.put("connect-four", "7x6 board. Take turns dropping a disc into a column - it falls to "
            + "the lowest open row. First to connect four in a row (any direction: horizontal, "
            + "vertical, or diagonal) wins.");

        RULES.put("checkers", "Standard American checkers on the dark squares. Move diagonally "
            + "forward one square, or jump an adjacent opponent piece to capture it. Capturing is "
            + "mandatory whenever it's available, and you must keep jumping with the same piece if "
            + "another capture opens up. Reach the far row to king a piece (kings move/capture in "
            + "any diagonal direction). Win by capturing all opposing pieces or leaving your "
            + "opponent with no legal move.");

        RULES.put("space-battle", "3-6 pilots each fly the same seeded sequence of asteroids and "
            + "enemy fighters independently. Rotate and thrust to fly, fire to shoot. Ranked by "
            + "score at the end of the time limit - 1st, 2nd, and 3rd place earn coins.");

        RULES.put("trivia-blitz", "2-6 players answer the same 8 general-knowledge questions. "
            + "Pick an answer before the timer runs out - correct answers score points, with a "
            + "bonus for answering faster. Highest total score after all questions wins.");

        RULES.put("minesweeper", "Left-click a cell to reveal it - a number shows how many mines "
            + "are in the 8 surrounding cells, or the cell opens up a wider empty area if there "
            + "are none nearby. Right-click to flag a cell you think hides a mine. Reveal every "
            + "non-mine cell to win - reveal a mine and it's game over.");

        RULES.put("dots-and-boxes", "Take turns drawing one line between two adjacent dots. "
            + "Complete the 4th side of a box and you claim it - and get to go again immediately. "
            + "Most boxes owned once every line is drawn wins.");
    }

    /** Falls back to a generic message for any game without a specific entry (shouldn't normally happen, but a missing lookup shouldn't crash the dialog). */
    public static String get(String gameId)
    {
        String rules = RULES.get(gameId);
        return rules != null ? rules : "No rules written for this game yet.";
    }
}
