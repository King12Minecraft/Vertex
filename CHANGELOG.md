# Vertex changelog

<!--
  ONE hand-written file, read by the app's Changelog page AND the website's Changelog page,
  so they never disagree. Player-facing wording, plain text (no markdown inside a line).
  Format: a "## " line starts an entry (newest first; put the date or version in it), each
  change is a "- " bullet, and a line indented by two spaces continues the bullet above it.
  build.sh / build.bat copy this file into VertexClient.jar.
-->

## 2026-09-30 - Reconnecting, match chat, Forums and a calculator
- If your connection drops in the middle of a match, the match now waits up to 30 seconds for you instead of ending. The app logs you back in by itself and puts you straight back on the board, and your opponent sees a live countdown. Works in Chess, Tic-Tac-Toe, Connect Four, Checkers, Reversi, Dots and Boxes, Word Duel, Battleship, Rock Paper Scissors, Dice Duel, Typing Duel, Memory Match, Signal Grid, Fusion Grid, Card Rush, Air Hockey, Snake Arena and Tetris Duel.
- Clicking Leave is still a real leave - it ends the match straight away.
- Chat with your opponent while you play: a chat panel now sits beside 14 online games and Telephone. Fold it away any time; it stays open for a minute after the match so you can say "gg".
- New Forums tab: a board for every game plus General. Start a thread, reply, and read everything even while logged out.
- New game page: pressing Play on a game now shows what the game is, its rules and its difficulty first, and Back takes you to where you came from.
- Type /calc 2+3*4 in any chat and only you see the answer.
- Pick your mouse cursor in Settings: Crystal, Ember, Neon, Mono, or one that follows your colour theme.
- New Changelog page in the app (you're looking at it).
- Fixed: Trivia Blitz never paid its winners any coins. It does now.

## 2026-09-29 - Screen breaks, Sky Hopper and more reconnecting
- Every 20 minutes of playing, a 30-second look-away break. If you're mid-match it waits until the match ends.
- New game: Sky Hopper, a vertical climber.
- Battleship and Rock Paper Scissors can now be rejoined after a dropped connection.
- Hill Climb, Number Nest and Telephone now show proper rules on their game pages.

## 1.0.0
- 51 original games, from quick single-player rounds to full online-ranked matches.
- Telephone - a draw-and-guess party game for 4-8 players.
- Every game opens inside the main window instead of a separate popup.
- A real economy: coins, a shop, daily login rewards and achievements.
- Friends, chat, parties and moderation tools built in.
- 11 visual themes, including a low-end performance mode for older computers.
