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

        RULES.put("sudoku", "Fill the 9x9 grid so every row, column, and 3x3 box contains the "
            + "digits 1-9 with no repeats. Click a cell then type a number to fill it in - the "
            + "bold given digits can't be changed.");

        RULES.put("simon-says", "Watch the colors flash in sequence, then click them back in the "
            + "same order. Get it right and the sequence grows by one more color; get it wrong and "
            + "the game ends. Your score is how many rounds you reached.");

        RULES.put("whack-a-mole", "Moles pop up at random across the grid for a shrinking window "
            + "of time - click one before it ducks back down to score. Runs for 30 seconds and "
            + "gets faster as it goes.");

        RULES.put("match-three", "Swap two adjacent gems to line up 3 or more of the same color in "
            + "a row or column - they clear, everything above drops down, and new gems fill in from "
            + "the top. Chains that clear more gems in one cascade score extra. You get 20 swaps; "
            + "your final score is what you cleared.");

        RULES.put("maze-chase", "Move through the maze collecting every dot while dodging the "
            + "three chasers. Grab a big glowing pellet in a corner and the chasers turn blue and "
            + "become huntable for a few seconds - eat one for a big bonus. You start with 3 lives; "
            + "losing one resets everyone's position but keeps the dots you've already cleared.");

        RULES.put("brick-breaker", "Move the paddle to keep the ball in play and smash the wall of "
            + "bricks above - higher rows are worth more points. Miss the ball and you lose a life; "
            + "you start with 3. Clear every brick to win.");

        RULES.put("flappy-bird", "Space, Up, or a click flaps upward; gravity pulls you back down "
            + "the rest of the time. Thread the gaps between the scrolling pipes - clipping a pipe "
            + "or hitting the floor or ceiling ends the run. Your score is how many gaps you clear.");

        RULES.put("galaxy-defender", "Arrow keys or A/D move your ship, Space fires. Clear each wave "
            + "of enemies before they reach your line - they march side to side, stepping down and "
            + "speeding up every time they hit an edge, and fire back at you. You have 3 lives; losing "
            + "them all, or letting any enemy reach your ship's row, ends the run.");

        RULES.put("word-guess", "Guess the hidden 5-letter word in 6 tries. Type letters and press "
            + "Enter to submit a guess - green means that letter's in the right spot, yellow means "
            + "it's in the word but the wrong spot, gray means it's not in the word at all. The "
            + "on-screen keyboard remembers what you've learned about every letter.");

        RULES.put("bubble-shooter", "Aim with the mouse (or Left/Right), click or Space to fire. "
            + "Match 3 or more of the same color to pop them, and anything left floating with no "
            + "connection back to the ceiling falls too. Clear the whole board to win - if a shot "
            + "settles too close to the shooter, the run ends.");

        RULES.put("lights-out", "Click any light to toggle it and its up/down/left/right neighbors. "
            + "Turn every light off to solve the puzzle - fewer moves means a better score.");

        RULES.put("peg-solitaire", "The classic cross-shaped board, every hole filled but the "
            + "center. Click a peg, then click a hole two spaces away in the same row or column to "
            + "jump it there, removing the peg you jumped over. Keep jumping until no jump is left - "
            + "getting down to a single peg wins, and finishing with that last peg in the center is "
            + "the classic perfect solve.");

        RULES.put("klondike", "The traditional Klondike solitaire. Click the stock to draw a card. "
            + "Click a card to pick it up - a tableau card brings everything face-up below it along "
            + "with it - then click where to move it: a foundation (same suit, building up Ace to "
            + "King) or another tableau column (one rank lower, opposite color, or a King onto an "
            + "empty column). Get every card onto the foundations to win.");

        RULES.put("dots-and-boxes", "Take turns drawing one line between two adjacent dots. "
            + "Complete the 4th side of a box and you claim it - and get to go again immediately. "
            + "Most boxes owned once every line is drawn wins.");

        RULES.put("reversi", "8x8 board. Place a piece so it flanks one or more of your opponent's "
            + "pieces in a straight line (any direction) between your new piece and another piece "
            + "of yours already on the board - every flanked piece flips to your color. If you have "
            + "no legal move, your turn is skipped. Most pieces once neither player can move wins.");

        RULES.put("memory-match", "16 face-down cards, 8 matching pairs. On your turn, flip two "
            + "cards - find a match and you keep them and go again; miss, and they flip back "
            + "face-down and it's your opponent's turn. Most pairs once every card is matched wins.");

        RULES.put("air-hockey", "Move your paddle to hit the puck into your opponent's goal - "
            + "you defend the goal on your side of the table. First to 7 goals wins.");

        RULES.put("word-duel", "Both players get the same 9 random letters. You have 60 seconds "
            + "to type out the longest real word you can build using only those letters (each "
            + "letter only as many times as it appears in the draw). Only your longest valid word "
            + "counts - keep trying better ones as time allows. Longest word when time runs out wins.");

        RULES.put("dice-duel", "Roll 5 dice. You can re-roll any subset of them up to twice, "
            + "then lock your final roll into one of 7 scoring categories (Ones through Sixes, or "
            + "Three of a Kind) - each category can only be used once. Once both players have "
            + "filled all 7 categories, highest total score wins.");

        RULES.put("snake-arena", "Two snakes, one shared arena, live. Use the arrow keys to steer - "
            + "eat food to grow. Crash into a wall, yourself, or the other snake and you're out; "
            + "the other player wins. A head-on collision with each other is a draw.");

        RULES.put("tetris-duel", "Standard Tetris rules on your own board, but clearing 2 or more "
            + "lines at once sends garbage rows to your opponent's board. Last board still standing "
            + "wins - top out (stack reaches the top) and you lose.");

        RULES.put("fusion-grid", "6x6 grid. Each turn you're given a random tile (2, 4, or 8) - "
            + "place it on any empty cell. If it lands next to a tile of the SAME value that's also "
            + "YOURS, they merge into a doubled tile and you score that new value - this can chain "
            + "into further merges. A same-value tile owned by your opponent never merges, it just "
            + "blocks you. Once the grid is full, highest total score wins.");

        RULES.put("typing-duel", "Both players get the exact same sentence at the exact same "
            + "moment - type it out correctly as fast as you can. Live progress bars show how far "
            + "along you and your opponent are. First to finish a round correctly wins it; first to "
            + "win 3 rounds wins the match.");

        RULES.put("signal-grid", "8x8 grid. On your turn, place one node of your color in an empty "
            + "cell, then fire it in one direction (up/down/left/right). The signal travels until it "
            + "hits the edge or another node - if that node is your opponent's, it flips to your "
            + "color; if it's yours, nothing happens. Game ends when the grid is full - most nodes "
            + "of your color wins.");

        RULES.put("card-rush", "No turns - play as fast as you can! Play a card from your hand onto "
            + "either shared center pile if it's exactly one rank higher or lower than that pile's "
            + "top card. Your hand refills from your own stock after every play. If nobody can move, "
            + "the piles automatically refresh. First to play every card in your hand and stock wins.");
    }

    /** Falls back to a generic message for any game without a specific entry (shouldn't normally happen, but a missing lookup shouldn't crash the dialog). */
    public static String get(String gameId)
    {
        String rules = RULES.get(gameId);
        return rules != null ? rules : "No rules written for this game yet.";
    }
}
