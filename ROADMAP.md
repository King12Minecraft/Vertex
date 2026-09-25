# Vertex — Roadmap

The live, currently-maintained plan for what's built, in progress, and coming next.
(`GAMEHUB_MASTER_INSTRUCTIONS_v2.md`, `GAMEHUB_ROADMAP_v3.md`, and
`GAMEHUB_ALL_IDEAS_v4.md` are earlier historical planning docs, already marked
superseded — this file replaces them as the one to check for current plans.)

For what already exists and works today in detail, see [`FEATURES.md`](FEATURES.md)
and [`PROGRAM_STRUCTURE.md`](PROGRAM_STRUCTURE.md). This file is about what's
*next*, not a feature list.

---

## ✅ Done

- **Practice-mode AI for 11 games** — a shared `ai/search` engine (Minimax/alpha-beta)
  powering Connect Four, Reversi, Dots and Boxes, Checkers, Chess, and Signal Grid,
  plus 5 bespoke bots for games that don't fit that shape (Word Duel, Dice Duel,
  Fusion Grid, Memory Match). One reusable engine, not one-off AI per game — built to
  scale to hundreds of future games.
- **"Aurora Glass" reskin of the shared app shell** — buttons, toggles, base palette,
  sidebar, hero banner, top bar, game mode cards, profile header, dialogs. Every
  individual game's own in-game screen is untouched, as scoped, except the eventual
  flagship.
- **Build script** (`build.sh`/`build.bat`) — compiles and packages both projects into
  their jars with a proper manifest, replacing BlueJ's own export as the way that
  happens. BlueJ itself still works fine as an alternative.
- **`VertexServer` made genuinely headless** — no more embedding the full client UI;
  stripped from 353 files to 99 (found by compiling the entry point and keeping only
  what the compiler pulled in, not guesswork). Fixes a real crash risk on a
  real/cloud deployment with no display.
- **`VERSION` file + semver** — single source of truth for the version number, stamped
  into both jar manifests, ready to be read by the future website too.
- **`.gitignore` for server runtime data**, with bug reports/suggestions carved out as
  the one exception meant to flow back to the developer (see `Sync-Feedback.sh`/`.bat`).
- **Mandatory game rules/detail page before every Play** — `GameDetailDialog` is now
  the one gate every "Play" entry point goes through; no way to skip straight into a
  match without seeing the rules/controls first.
- **Moderator chat** — a live, server-role-gated staff-only channel.
- **Party Mode** ("funny effect mode") — an off-by-default toggle for a cosmetic
  cursor trail + confetti-on-win, purely decorative.
- **Embedded games proof-of-concept (Chess)** — every game used to open as its own
  separate `JFrame` window; Chess now embeds inside the main launcher window instead,
  via a new `Pages.GAME_HOST` slot in `MainMenu`'s existing `CardLayout`
  (`MainMenu.showGame(...)`/`returnToGames()`), with `games.EmbeddedGamePanel` as the
  replacement for the old windowClosing confirmation (MainMenu calls
  `requestLeave()` before navigating away mid-match, e.g. a Sidebar click). Verified
  visually under Xvfb: the actual board fills the available space (BorderLayout's
  CENTER region stretches it automatically, no extra sizing code needed) and the
  mode-select screen was re-centered in its own available space rather than left
  pinned in the top-left corner with dead space around it. Also fixed a real ordering
  bug caught during this conversion: the old code called `dispose()` unconditionally
  after a match-over dialog, even when a rematch had just swapped in a brand new
  window — the embedded version would have made that swap-then-immediately-undo
  visible as a jarring flicker back to the games list, since both actions now touch
  the same single game-host slot; fixed by only calling `returnToGames()` when no
  rematch was requested.
- **Reversi converted to the embedded pattern** — second game done this way, proving
  the pattern generalizes to a game with real differences from Chess (no existing
  leave-confirmation dialog to preserve/adapt, no existing Leave button on its board
  screen, its "Play Again" reuses the same panel instead of creating a new one).
  Found and fixed a new wrinkle Chess didn't have: Reversi's board is one
  custom-painted `JPanel` at a hardcoded pixel size (unlike Chess's plain
  `GridLayout(8,8)`), so it stayed pinned in a corner with dead space around it once
  embedded — fixed with the same `GridBagLayout` auto-centering wrapper already used
  for mode-select screens, applied to the board too.
- **Connect Four converted to the embedded pattern** — third game done this way, same
  shell shape as Reversi (no existing leave confirmation, fixed-pixel board needing
  the same centering fix), converted with no new surprises.
- **Signal Grid converted to the embedded pattern** — fourth game done this way; its
  board screen has an extra wrinkle (board + a row of 4 direction-fire buttons stacked
  together, not just a bare board), handled by wrapping that whole stacked group in one
  `GridBagLayout` centering wrapper rather than centering the board alone.
- **Tic-Tac-Toe converted to the embedded pattern** — fifth game done this way, and the
  first with pre-existing quirks worth preserving as-is rather than "fixing" mid-
  conversion: its practice mode never unregistered its network push listener even as
  its own window, so `requestLeave()` keeps that exact behavior rather than becoming
  more correct than the original by accident. Also caught a new variant of the by-now-
  familiar centering bug: its board was already wrapped in a centering container, but
  that container used `FlowLayout` (which only centers horizontally - vertically it's
  always top-aligned), so the board still sat pinned to the top of the much taller
  embedded space with dead space below it. Same fix as always - swapped for
  `GridBagLayout`, which centers on both axes.

- **Dots and Boxes converted to the embedded pattern** — sixth game done this way, same
  shape as Reversi/Connect Four/Signal Grid (no existing leave confirmation, fixed-pixel
  board needing the centering fix), no new surprises.

- **Checkers converted to the embedded pattern** — seventh game done this way and the
  last of the original `ai/search`-backed practice-mode games; same shape as
  Reversi/Connect Four/Signal Grid/Dots and Boxes, no new surprises. All 6 games with
  the shared `ai/search` engine are now embedded, alongside Chess.

- **Snake converted to the embedded pattern** — eighth game done this way, and the
  first offline/single-player game (no matchmaking, no network match to confirm
  leaving). Found and fixed a real regression while converting it: Snake is also the
  one game reachable from `OfflineHubWindow`'s pre-login "Play Offline" screen, where
  `MainMenu` doesn't exist yet - hardcoding `MainMenu.getInstance().returnToGames()`
  the way every other converted game does would have thrown a `NullPointerException`
  the moment a logged-out guest tried to leave a Snake game. Fixed with a
  `setReturnAction(Runnable)` escape hatch: defaults to the usual
  `MainMenu.getInstance().returnToGames()` when unset, but `OfflineHubWindow` now
  supplies its own small `CardLayout`-based "go back to the offline hub" callback
  instead, since it's a standalone `JFrame` with no `MainMenu` shell to hand off to.

- **10 more single-player offline games converted in one batch** — 2048, Minesweeper,
  Sudoku, Simon Says, Whack-a-Mole, Gem Match (Match Three), Lights Out, Peg Solitaire,
  Mancala, and Klondike Solitaire. All share the same simple shape (one screen, no
  matchmaking, `requestLeave()` with nothing to confirm), so the same recipe applied
  cleanly to every one: a `GridBagLayout` centering wrapper around the board (several
  of these were direct `BorderLayout.CENTER` children before - which stretches rather
  than centers a fixed-preferred-size board, the same class of bug found during the
  Reversi/Tic-Tac-Toe conversions, just discovered fresh in each of these since none
  had been embedded before), and a new "Leave" button added next to whatever
  restart/new-game button already existed. 18 games embedded total now.
- **10 more offline single-player games converted in a second batch** — Dino Dash,
  Tetris, Ping Pong, Crossing Road, Aim Trainer, Puzzle Quest, Yahtzee, Brick Breaker,
  Flappy Bird, and Galaxy Defender. Same recipe as the first offline batch throughout
  (`GridBagLayout` centering wrapper, a new "Leave" button, `requestLeave()` stopping
  whatever Swing `Timer` drives the game loop where one exists). Several of these
  (Dino Dash, Tetris, Ping Pong) had no "Leave"/"Close" affordance at all before -
  relied entirely on the OS window's close button, same gap Snake had - so this batch
  is where that pattern (wrap the play panel, add a bottom row with just a Leave
  button) got reused most. Yahtzee's center column (dice + Roll button) used
  `BoxLayout` inside `BorderLayout.CENTER` - stretches to fill the region without
  actually centering its content vertically, a `BoxLayout` cousin of the `FlowLayout`
  top-alignment bug found during the Tic-Tac-Toe conversion - fixed the same way,
  wrapped in a `GridBagLayout` centerer. 28 games embedded total now.
- **Bug found and fixed: `SpectateDialog`'s Chess "Watch" button was silently broken**
  by the original Chess embedding conversion — it still called
  `new ChessWindow(matchId); window.setVisible(true)`, which does nothing once
  `ChessWindow` is a `JPanel` instead of a `JFrame`. Caught by a deliberate sweep
  (grepping for `new <Window>(` across the whole client, excluding each window's own
  file and `GameLauncher.java`) run before converting Rock Paper Scissors, since RPS
  has the same spectate-window pattern in the same file. Fixed by routing through
  `MainMenu.getInstance().showGame(...)` like every other entry point. Worth
  remembering for every future conversion: check for *every* external construction
  site of a game window, not just `GameLauncher`'s.
- **Rock Paper Scissors converted to the embedded pattern** — 29 games embedded total
  now. More involved than the earlier batches: three ways into the same window (normal
  play, spectating an in-progress match via `SpectateDialog`, and a rematch-wait
  screen), plus a rematch flow with the same "swap-then-immediately-undo" risk found
  during the original Chess conversion (a rematch spawns a brand new embedded window,
  so the old window's own cleanup must skip `returnToGames()` when a rematch was just
  requested - fixed with the same `boolean[] rematchRequested` flag Chess uses).
  `SpectateDialog`'s RPS spectate line got the same `MainMenu.getInstance().showGame(...)`
  fix as the Chess bug above, fixed proactively this time instead of shipping broken
  first.

## 🔧 In Progress

- **Rolling embedded games out past the `ai/search` games and both offline-game
  batches** — the pattern is proven 29 times now (Chess, Reversi, Connect Four, Signal
  Grid, Tic-Tac-Toe, Dots and Boxes, Checkers, Snake, 2048, Minesweeper, Sudoku, Simon
  Says, Whack-a-Mole, Match Three, Lights Out, Peg Solitaire, Mancala, Klondike, Dino
  Dash, Tetris, Ping Pong, Crossing Road, Aim Trainer, Puzzle Quest, Yahtzee, Brick
  Breaker, Flappy Bird, Galaxy Defender, Rock Paper Scissors); the other ~18 games
  (mostly online multiplayer with matchmaking, plus a few real-time arcade games and
  Battleship's own spectate path in `SpectateDialog`) still open their own `JFrame`
  and need the same conversion, one at a time. Any future offline-capable game
  reachable from `OfflineHubWindow` needs the same `setReturnAction`-style treatment
  Snake got, not just the standard MainMenu-only conversion. **Standing check for
  every remaining conversion**: grep for every external construction site of that
  game's window (not just `GameLauncher.java`) before considering it done - Chess's
  spectate-path bug is exactly the kind of thing that slips through otherwise.
- Also still wanted: the rules/detail page (`GameDetailDialog`) should fill the
  screen instead of being a small popup, and a game should be able to have chat
  "popped out" alongside it while playing (with some games, like a Gartic-Phone-style
  drawing game, needing chat *restricted* rather than open, since free chat would let
  players just say the answer out loud).

## 📋 Planned — infrastructure & shared packages

- **`engine` package** — shared sprite/animation/game-loop/collision toolkit, built
  minimal-first (same approach as `ai/search`), aimed at making a *simple* new game
  buildable in a few hundred lines instead of starting from scratch each time.
  Includes a planned **pseudo-3D** capability (isometric/raycasting tricks in plain
  2D Java2D) — real 3D (JOGL/LWJGL, native libraries, a separate rendering pipeline)
  was deliberately ruled out as a different, much bigger project that would break the
  "one portable jar, no install" model everything else follows.
- **`economy` package additions** — an `EconomyKernel` any game can call in one line
  to grant coins/XP/unlock cosmetics, instead of reimplementing reward logic per game.
- **`achievements` kernel** — generic trigger-based unlock system, parallel to
  `EconomyKernel`.
- **`save` package** — generic save/load slots for games with persistent state
  (roguelike runs, farming/idle games in the concept backlog need this).
- **`matchmaking` kernel** — shared ELO/queue logic any new competitive game can
  register into.
- **Procedural/emergent character system** — a general engine capability (not tied to
  one game): a pool of name/trait/role combinations that get spawned into a game when
  specific triggers fire (a rival general emerges after you conquer 3 provinces, a
  rebel leader after a revolt, etc.) — free, rule-based, no LLM. Fits Dominion's
  leader/succession system especially well, but meant to be usable by other games too.

## 📋 Planned — social & community

- **Standalone Forums section** — Reddit-style boards (one per game, plus general
  discussion), separate from group chats.
- **Mutual friends** — shown on profiles, computed from the intersection of two
  friends lists.
- **Structured bug reports & suggestions** — `FeedbackDialog`/`GameSuggestionsPanel`
  already exist but are single free-text fields; add real title/description fields,
  plus steps-to-reproduce for bug reports specifically.
- **Slash commands + a free pattern-matching chatbot** — `/help`, `/rules chess`,
  `/theme`, `/challenge @friend`, etc. Deliberately *not* a real LLM chatbot (that
  costs money per message and needs an API key) — free and instant by design. A
  bring-your-own-API-key *option* for real chat later is a separate, explicitly
  opt-in idea, not the default.
- **Discord**: not something built into Vertex itself, but the user is setting up an
  actual Discord server for the project — possible follow-up integration ideas
  (a webhook posting changelog/version updates, a bot) once the specific ask is
  clearer.

## 📋 Planned — voice chat (v1 scope, not started)

A genuinely large, from-scratch feature — no audio code exists in the codebase yet.
Planned v1: raw/uncompressed PCM audio (`javax.sound.sampled`, built into the JDK) over
a new channel, **party-scoped only** (you have to be invited to the party to be in its
voice channel — the whole point being no random strangers can join), push-to-talk.
Compression, jitter buffering, and general audio quality are v2+ once the basic
pipeline is proven. Real-time audio wants UDP; the existing `NetworkManager` stack is
all TCP today, so this likely needs a new transport, not just a new message type.

## 📋 Planned — the website (Python/Flask, hosted on the user's Oracle Cloud instance)

Pages: Home/Changelog, Download (default landing page), Credits, a simplified public
"how it's built" overview, Features (reusing `FEATURES.md`). Reads the same `VERSION`
file the jars are stamped with, so the version number never drifts between the app and
the site.

## 🎲 Games backlog (separate, huge, not started)

314 total game concepts from the user's own backlog doc: the flagship **"Vertex:
Dominion"** (a persistent, whole-server nation-building strategy game — categorically
bigger than every other game combined, resolved on a daily server tick rather than
played as a quick match), 7 fully-detailed "Original Seven," and 306 more short-pitch
concepts across 38 categories. Also newly suggested: a Gartic-Phone-style
drawing/guessing game (needs restricted, not open, chat — see "Embedded games" above).
Still-deferred engine shapes flagged early on and never built: continuous-control
games (Air Hockey, Fight Arena), parallel-simulation games (Racing, Space Battle),
multiplayer bot-fill (Among Us, Trivia Blitz).

---

*Keep this file updated as things move between sections — that's the whole point of
having it.*
