# Vertex — Roadmap

The live, currently-maintained plan for what's built, in progress, and coming next.
(`GAMEHUB_MASTER_INSTRUCTIONS_v2.md`, `GAMEHUB_ROADMAP_v3.md`, and
`GAMEHUB_ALL_IDEAS_v4.md` are earlier historical planning docs, already marked
superseded — this file replaces them as the one to check for current plans.)

For what already exists and works today in detail, see [`FEATURES.md`](FEATURES.md)
and [`PROGRAM_STRUCTURE.md`](PROGRAM_STRUCTURE.md). This file is about what's
*next*, not a feature list.

A senior-architect-level strategic analysis of the whole platform (biggest
opportunities, 30+ new ideas beyond this roadmap, architecture breakthroughs,
security/reliability/scaling considerations, a persistent-game architecture proposal,
commercial analysis, and a dependency-ordered priority sequence) lives in a separate
Claude Docs artifact the user requested and reviewed - not duplicated here since it's
long-form analysis rather than a maintained plan, but its settled decisions get
recorded below as they're confirmed.

## 🏛️ Architecture Decisions

- **`Vertex: Dominion` gets its own persistent navigation tab**, not the `MainMenu`
  `CardLayout` game-host slot every other game uses. Confirmed by the user. Rationale
  (from the strategy analysis above): Dominion is a standing, server-scheduled
  daily-tick nation game with no "end" - it's checked in on repeatedly like a
  management sim, not launched-and-left like a match, so it needs a permanent nav
  destination (alongside Social/Settings) rather than being bent into the
  launch-and-play pattern just standardized across all 49 games this session. It
  should still share identity/social/friends with the rest of the platform, but keep
  its own isolated world-state store, currency, and tick-scheduler - not the match
  economy or `GameSession` lifecycle. Not yet implemented; recorded here as a settled
  design constraint for whenever Dominion work begins.
- **Nightly autonomous work sessions, 11:00 PM-5:00 AM IST, self-scheduled via a
  recurring trigger.** Work order: Audit → Architecture → Core Systems → Games →
  Multiplayer → Social → Progression/Economy → UI/UX → Security → Performance →
  Reliability → Accessibility → Distribution → Documentation → Testing → Final
  Polish, then repeat with a fresh refinement pass once the backlog is clear. Blocked
  questions needing the user's input are recorded in
  [`BLOCKED_QUESTIONS.md`](BLOCKED_QUESTIONS.md) rather than stopping the session;
  everything else keeps moving. Every session ends at a clean, buildable, committed
  checkpoint.

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
- **3 more offline single-player games converted** — Maze Chase, Word Guess, and
  Bubble Shooter, closing out the "offline/single-player" games package entirely
  (every remaining game left to convert is online-multiplayer). Same recipe as the
  rest of the offline batches, no new surprises. 32 games embedded total now.
- **Battleship converted to the embedded pattern** — 33 games embedded total now, and
  the second (after Rock Paper Scissors) with the multi-entry-point shape: normal
  play, a `SpectateDialog` "Watch" path, and a rematch-wait screen, plus the same
  rematch-flow `boolean[] rematchRequested` guard RPS needed. `SpectateDialog`'s
  Battleship spectate line got the same proactive `MainMenu.getInstance().showGame(...)`
  fix RPS's did, rather than shipping broken like Chess's first pass. Both AI-mode
  win/loss dialogs (no rematch offered there, always a plain `GameHubDialog.show(...)`)
  just needed their trailing `dispose()` swapped for `returnToGames()`, no ordering
  guard needed since neither path opens a new embedded window.
- **Memory Match converted to the embedded pattern** — 34 games embedded total now,
  back to the simpler single-entry-point shape (no spectating/rematch-wait screens).
  Same recipe as Reversi/Connect Four/Signal Grid throughout: unconditional
  `requestLeave()`, `GridBagLayout` centering on all three screens, a new "Leave"
  button on the board screen. No new wrinkles.
- **Word Duel converted to the embedded pattern** — 35 games embedded total now. Same
  recipe throughout, including the round screen (letters + text field + Submit) which
  never had a Leave affordance before and now gets one alongside the rest of its
  content, same as the other single-screen games without a bare "board" concept.
- **Dice Duel converted to the embedded pattern** — 36 games embedded total now. Same
  recipe as Reversi/Word Duel: unconditional `requestLeave()`, `GridBagLayout`
  centering on all three screens (the board screen's outer wrapper previously had its
  own fixed `setPreferredSize(420, 420)`, an unusual variant of the same stretch-not-
  center bug - removed now that the actual content column is centered directly), and
  a new "Leave" button on the board screen.
- **Typing Duel converted to the embedded pattern** — 37 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()` (never confirmed before
  leaving mid-match even as its own window), `GridBagLayout` centering on all three
  screens, a new "Leave" button on the round screen alongside the sentence/typing
  field/progress bars (it never had one before, same as Word Duel). No new wrinkles;
  `GameLauncher.java`'s `typing-duel` case was its only external construction site.
- **Racing converted to the embedded pattern** — 38 games embedded total now, and the
  first online-multiplayer game converted. Same recipe: unconditional
  `requestLeave()` (leaves the queue if mid-search, otherwise a no-op), all four
  screens (mode-select, searching, waiting, and the race track itself) wrapped in
  `GridBagLayout` centerers. The race screen was a new variant: `RacingPanel` (a
  fixed-size custom-painted canvas) is added straight to the `CardLayout`, rebuilt on
  every restart via `startRace(...)`, so the centering wrapper is rebuilt alongside
  it each time rather than created once in the constructor.
- **Among Us converted to the embedded pattern** — 39 games embedded total now. Same
  recipe: unconditional `requestLeave()` (leaves the queue only if still searching,
  matching the original's `windowClosing` exactly), all three screens (searching,
  task/kill screen, meeting) wrapped in `GridBagLayout` centerers. New wrinkle: the
  task/kill screen was originally built as a `JScrollPane` (its task list can scroll)
  returned directly to `CardLayout` rather than a plain `JPanel` - same stretch bug
  applies to a scroll pane as to any other direct `CardLayout` child, so it also got
  wrapped, changing `createGameScreen()`'s return type from `JScrollPane` to `JPanel`.
- **Fight Arena converted to the embedded pattern** — 40 games embedded total now.
  Same recipe: unconditional `requestLeave()` (leaves the queue only if still
  searching, matching the original's `windowClosing` exactly), mode-select and
  searching screens wrapped in `GridBagLayout` centerers. The live match screen was
  the bare-board variant: `FightArenaPanel`, a fixed-size (760px wide) custom-painted
  canvas fed by a server tick loop, was added directly to `BorderLayout.CENTER`
  inside the game screen's outer panel - needed its own small `GridBagLayout`
  centerer wrapper (`fightCenterer`) so it centers instead of stretching to fill the
  available area, same fix as every other bare game-board panel converted so far.
- **Square Wars converted to the embedded pattern** — 41 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()` (also stops the local
  countdown timer, matching the original `windowClosing` exactly), mode-select and
  searching screens wrapped in `GridBagLayout` centerers, and the board screen's
  bare `BoardPanel` (a fixed-size custom-painted grid claimed by clicking cells,
  added directly to `BorderLayout.CENTER`) given its own small `GridBagLayout`
  centerer (`boardCenterer`) - the same bare-board fix used for Fight Arena's canvas.
  No rematch guard needed: "Play Again" re-shows the `SEARCHING` card within the same
  window instead of opening a new one.
- **Trivia Blitz converted to the embedded pattern** — 42 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()`, all three screens
  (mode-select, searching, round) wrapped in `GridBagLayout` centerers. The round
  screen never had a Leave affordance before (only the 4 answer buttons and a scores
  area) and now gets one appended below the scores panel, same as Word Duel and
  Typing Duel's round screens.
- **Air Hockey converted to the embedded pattern** — 43 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers, and the board screen's bare `TablePanel`
  (a fixed-size custom-painted table fed by continuous server state updates, mouse
  position reported to steer the paddle) given its own `GridBagLayout` centerer
  (`tableCenterer`) - same bare-board fix as Fight Arena and Square Wars. Mouse
  coordinates used for paddle-position reporting stay relative to `TablePanel` itself
  regardless of the wrapper, so centering doesn't affect gameplay input.
- **`engine` package built** — the shared sprite/animation/game-loop/collision toolkit
  from the infrastructure backlog, built minimal-first (same approach as `ai/search`):
  `GameLoop` (named wrapper around the `javax.swing.Timer`-tick loop every offline game
  already hand-rolls), `Vector2` (immutable 2D vector math), `GameObject` (optional
  position/velocity/bounds base class), `Collision` (AABB/circle/circle-rect overlap
  tests plus side-detection and bounce response — the same checks `BrickBreakerGame`/
  `AirHockeyMatch` already hand-roll per-game, generalized), and `Sprite`/`SpriteSheet`/
  `Animation` for image-based sprite work (no game uses raster sprites today — every
  board is hand-painted with `Graphics2D` shapes — this is for whichever game wants
  bitmap art next). Also includes the planned **pseudo-3D** capability:
  `engine/pseudo3d/RayCaster` (Wolfenstein-3D-style DDA raycasting over a 2D grid map)
  and `engine/pseudo3d/IsometricProjection` (tile↔screen conversion plus the standard
  painter's-algorithm draw-order key) — both plain Java2D, no real 3D pipeline. Verified
  with a 39-check scratchpad smoke test (vector math identities, collision correctness,
  a procedurally-built sprite sheet animated through a full loop and a one-shot cycle,
  a raycaster render confirmed to paint actual wall pixels, an isometric round-trip, and
  a real `GameLoop` confirmed to fire ticks) plus a separate visual demo rendering both
  a raycaster corridor and an isometric tile grid to PNG. Client-only, not mirrored to
  `VertexServer` (confirmed via `/tmp/keep_files.txt`), same as `ui`/`theme`/`pages`.
  Infrastructure only for now - no shipped game uses it yet, same "prove it generalizes
  first" approach `ai/search` took before `ConnectFourGameModel` existed. See
  `PROGRAM_STRUCTURE.md`'s `engine` section for the full breakdown.
- **Snake Arena converted to the embedded pattern** — 44 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers, and the board screen's bare `ArenaPanel`
  (a fixed-size custom-painted grid, both snakes/food redrawn from server state)
  given its own `GridBagLayout` centerer (`arenaCenterer`) - same bare-board fix as
  Fight Arena, Square Wars, and Air Hockey.
- **Tetris Duel converted to the embedded pattern** — 45 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers. New variant: the board screen puts both
  players' boards side by side in a `FlowLayout.CENTER` row (`boardsRow`) - FlowLayout
  already centers its children horizontally, but added directly to
  `BorderLayout.CENTER` it stays top-aligned vertically once the host area is taller
  than the row needs (the same `FlowLayout` top-alignment bug found during Tic-Tac-Toe's
  conversion) - fixed by wrapping `boardsRow` itself in a `GridBagLayout` centerer
  (`boardsCenterer`) for true 2D centering rather than replacing the FlowLayout, since
  FlowLayout's own horizontal centering of the two boards is still exactly what's
  wanted.
- **Fusion Grid converted to the embedded pattern** — 46 games embedded total now.
  Second multi-entry-shape game after Rock Paper Scissors/Battleship, but the simpler
  variant: online + offline Practice mode (`ai.AiKernel`-backed bot), no spectating/
  rematch-wait factories, so no `rematchRequested` guard needed - "Play Again" in
  both modes just restarts within the same window (`startPracticeMatch()`/
  `findMatch()`), never opening a new embedded one. Same recipe otherwise:
  unconditional `requestLeave()` (skips `leaveMatch()` in practice mode, matching the
  original `windowClosing` exactly), mode-select/searching screens wrapped in
  `GridBagLayout` centerers, and the board screen's bare `BoardPanel` given its own
  `GridBagLayout` centerer (`boardCenterer`) - same bare-board fix as the other
  online-multiplayer conversions.
- **Card Rush converted to the embedded pattern** — 47 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers. Board screen was another Dice Duel-style
  variant: the outer `wrap` panel had its own hardcoded `setPreferredSize(460, 320)`
  (removed - meaningless once `CardLayout` stretches `wrap` itself to fill the host
  area regardless), and the actual `center` content column (the two piles plus the
  hand row) needed the `GridBagLayout` centering wrapper (`centerCenterer`) to stop
  it pinning to the top of the now-larger board area.
- **Zombie Survival converted to the embedded pattern** — 48 games embedded total
  now. Same shape as Racing: online + Practice mode, unconditional `requestLeave()`
  (leaves the queue only if online), mode-select/searching/waiting screens wrapped in
  `GridBagLayout` centerers, and the game screen's fixed-size `ZombieSurvivalPanel`
  rebuilt from scratch on every restart via `startGame(...)` - the centering wrapper
  (`gameWrapper`) is rebuilt alongside it each time, tracked via a new field so the
  old one can be removed from the `CardLayout` before a fresh one is added, same
  pattern as Racing's `raceWrapper`.
- **Space Battle converted to the embedded pattern** — 49 games embedded total now.
  The last remaining game: same exact shape as Racing/Zombie Survival (online +
  Practice mode, independent-simulation scoring, a fixed-size game canvas rebuilt on
  every restart via `startGame(...)` with its centering wrapper - `gameWrapper` -
  rebuilt alongside it). **Every game in the entire Vertex catalog is now embedded in
  MainMenu's game-host slot - the "fill the screen, no separate window" conversion
  that's been running throughout this session is complete.**
- **`GameLauncher`'s 49-branch `if/else` replaced by `GameWindowFactory`** — the
  strategy doc's Section 4/17 root-system fix, done as a purely mechanical extraction:
  every branch had the exact same shape by the end of the embedded-games rollout
  (`MainMenu.getInstance().showGame(new XxxWindow())`), so it collapsed cleanly into
  one `Map<String, Supplier<JComponent>>` (`GameWindowFactory`, client-only - unlike
  `GameInfo`/`GameRegistry`, which stay byte-identical on both client and server and
  so can never reference a Swing class). `GameLauncher.openGame` is now a single
  lookup instead of 49 branches. Verified with a smoke test constructing all 49
  windows via the factory and confirming zero exceptions, the exact expected 49-id
  set, and that an unknown id correctly returns `null` (falling through to the
  existing "not converted yet" notice). Note found during the audit for this: a
  `GameRegistry`/`GameInfo` catalog already existed server- and client-side (feeding
  the games list UI) before this session started - it was never disconnected, just
  never wired to the launch mechanism; that's genuinely the next step (capability
  flags: offline-capable, spectatable, min/max players), not a from-scratch build.
- **First `GameInfo` capability flag added: `spectatable`.** True only for Chess,
  Rock Paper Scissors, and Battleship (the three games with a real `SpectateDialog`
  "Watch" entry point) - false by default for the other 46. `GameRegistry.seed()`
  sets it via a small `markSpectatable(gameIds...)` helper after construction, same
  post-construction-setter convention `queueCount` already used, rather than adding a
  required 8th constructor parameter to all 49 call sites for something only 3 of
  them need. Verified with a scratchpad test confirming the spectatable set matches
  exactly `{chess, rock-paper-scissors, battleship}` out of all 49 games. Deliberately
  scoped to just this one flag tonight rather than the full capability set the
  strategy doc describes (offline-capable, min/max players, party-joinable) - `type`
  already mostly captures offline-capability ("Single Player" vs "Multiplayer" vs
  "Single/Multiplayer"), so adding a redundant parallel flag for that wasn't
  justified; the others don't have a concrete consumer yet and building them
  speculatively ahead of one was explicitly flagged as an anti-pattern to avoid in
  the strategy doc's own "Things NOT to build" section.
- **Fixed a real economy bug found during the audit: 14 offline games paid zero coins
  on completion despite already having a reward formula defined.** `EconomyConfig
  .getPracticeReward` has had formulas for Simon Says, Whack-a-Mole, Match Three,
  Maze Chase, Brick Breaker, Flappy Bird, Galaxy Defender, Word Guess, Bubble
  Shooter, Lights Out, Peg Solitaire, Klondike, Yahtzee, and Mancala for a while -
  but `ClientHandler.handleGamePlayed`'s dispatch only ever called it for 6 games
  (Pong/2048/Dino Dash/Tetris/Crossing Road/Aim Trainer), so those 14 games'
  formulas were simply never reached. Fixed by replacing the hand-maintained 6-game
  `OR` chain with a safe default fallback: any `gameId` not otherwise special-cased
  now goes through `awardPracticeScore`, which itself already no-ops safely (via
  `getPracticeReward` returning 0) for any id with no formula - meaning this also
  can't accidentally reward an online game that sends `GAME_PLAYED_REQUEST` purely
  for history tracking (Chess, Racing, etc. all correctly still get 0), and any
  *future* offline game only needs a `getPracticeReward` entry, never a
  `ClientHandler` edit. Verified with a coverage test confirming all 20 intended
  games get a nonzero reward and every online/history-only game still gets exactly
  0. One real gap surfaced by the same audit, left open rather than guessed at:
  Sudoku has no reward formula at all (not a wiring bug, a genuine missing design
  decision) - recorded in `BLOCKED_QUESTIONS.md`.
- **New game: Hill Climb** — 50 games in the catalog now. An original "drive a simple
  vehicle across procedurally rolling hills on a limited fuel tank" implementation
  (`HillClimbGame`/`HillClimbWindow`), picked from the games backlog instead of the
  also-listed Pac-Man concept once the audit noticed Maze Chase is already
  functionally a from-scratch Pac-Man (maze, pellets, chasers, frightened mode) -
  building a second one would have been pure duplication. The first game in this
  codebase actually built on the `engine` package (`Vector2` for the slope/gravity
  math, `GameLoop` for the tick loop) rather than just having it sit unused. Added
  through every layer tonight's earlier refactors made a single-file change each:
  one `GameWindowFactory` entry (no `GameLauncher` edit needed), one `GameRegistry`
  entry (client + server), one `EconomyConfig.getPracticeReward` formula (no
  `ClientHandler` edit needed, since the safe-default fallback from the economy fix
  above already covers any new offline game automatically). Caught and fixed a real
  balance problem during testing rather than shipping it: an early version also
  flipped the car over past a steep-slope-at-speed threshold, but that triggered far
  too readily during ordinary hard-throttle climbing (an unfair "gotcha" death, not
  a reckless-driving penalty) - cut for a solid, fully-tested fuel-only version
  instead of shipping a half-tuned mechanic. Verified with an 11-check logic test
  (terrain bounds, distance/fuel/score behavior, idle vs. throttling, tick() being a
  no-op once over) plus an Xvfb/Swing screenshot harness confirming the terrain,
  car, and HUD render correctly both at rest and mid-drive.
- **Dead connection-error message fixed across 25 game windows.** Every
  practice/find-match screen used `if (!NetworkManager.sendAsync(request)) { ...set an
  error label... }`, but `sendAsync()` is designed to always return `true` - it queues
  offline instead of failing, on purpose, so `sendAsync`-based optimistic UI (like
  chat) never has to check a return value. That made the error branch on all 25 files
  dead code: a player with no server connection just saw "Searching..." spin forever
  with no explanation. Fixed at the source instead of patching each call site
  individually - added `NetworkManager.describeIfNotReady()`, which reads the
  already-existing `ConnectionState` (OFFLINE/CONNECTING/RECONNECTING/ONLINE) and
  returns a player-facing message for the first three and `null` when ONLINE, then
  swapped all 25 files' dead `if (!sent)` checks for a call to it right after
  `sendAsync()`. Verified with a reflection-based state test (all four states return
  the right thing) and an Xvfb/Swing harness that drove real `TicTacToeWindow` and
  `ChessWindow` instances to their searching screen (covering both the
  `searchingLabel` and the one-off `statusLabel` variable-name variants used across
  the 25 files) and confirmed the correct message renders.
- **4 online games' wins never counted toward the generic win-count challenges
  (Win 1 Today / Win 3 Today / Champion).** Same shape of bug as the offline-rewards
  fix above, found the same way: traced every caller of the economy layer rather than
  assuming coverage. `challengeManager.recordWin()` - the only thing that advances
  those challenges - was only ever called from `EconomyManager.awardWin()`. Racing,
  Space Battle, Square Wars, and Trivia Blitz don't have a single
  `EconomyConfig.getWinReward(gameId)`-driven winner the way a 2-player match does
  (placement games, and games where ties split the prize among several winners), so
  they route through `awardRacingPlacement`/`awardSpaceBattlePlacement`/`awardCoins`
  instead - none of which touched `challengeManager` at all. A player who only ever
  played those 4 games could never complete "Win any 1 online match", and since
  `ChallengeManager.recordWin` grants its own coin bonus on completion (on top of the
  match's own reward), this was a real missed-coins bug, not just a progress-bar
  display issue. Fixed by extracting `awardWin`'s post-reward challenge/message logic
  into a shared `recordOnlineWin()` and calling it from `awardRacingPlacement`/
  `awardSpaceBattlePlacement` (only when `place == 1` - that's this race's "win"),
  plus a new `awardMatchWinCoins()` for the tie-splitting games (Square Wars, Trivia
  Blitz) to use instead of the generic `awardCoins`. Verified with a coverage test
  that drives the real `ChallengeManager` for all 4 previously-unreachable game IDs
  and confirms `daily-win-1` now completes, plus a control case proving an unrelated
  game-specific challenge (`tictactoe-win-5`) stays untouched.
- **9 more silent/stale connection-error spots found beyond the 25-file sweep,
  fixed the same way.** The earlier connection-status fix only grepped for the
  `sendAsync`/`if (!sent)` dead-code pattern; a follow-up pass grepping for
  `NetworkManager.send(` (the blocking variant, which genuinely returns `null` on
  failure) and for the literal stale string `"Can't reach the server - is it
  running?"` turned up two more categories of the same underlying problem: (1) two
  more `sendAsync` dead-code spots in `ChatPanel` (send message, send file) that the
  first sweep missed entirely, and (2) 7 spots (`GamesPanel` refresh,
  `LeaderboardPanel`, `FriendsPanel` load + add-friend, `ShopPanel` load + select +
  purchase, `NewGroupDialog`, `LoginPanel`, `ChangeUsernameDialog`,
  `ChangePasswordDialog`) that already showed *some* error on a null `send()`
  response, just the same generic hardcoded string instead of an accurate one, or
  (worse, for `FriendsPanel.loadFriendData()` and `ShopPanel.loadShopItems()`)
  showed nothing at all - a completely blank Friends/Shop page with no explanation
  when offline. All 9 now use `NetworkManager.describeIfNotReady()` the same way the
  25-file fix did. `ChatPanel`'s two spots now also always clear the input (matching
  `sendAsync`'s documented queue-and-optimistically-proceed contract) and, only when
  actually offline, add a reassuring "will send once reconnected" note rather than
  looking like the message/file was silently dropped. Verified: clean compile, plus a
  harness that constructs real `FriendsPanel`/`LeaderboardPanel`/`ShopPanel`
  instances with nothing listening on the configured host:port and confirms each one
  now shows the connection message instead of hanging on "Loading..." or staying
  blank.

## 🔧 In Progress

- **Embedded-games rollout: complete.** All 49 games in the catalog now open inside
  MainMenu's game-host slot instead of their own JFrame - every offline/single-player
  game, both spectate/tournament-capable games (Chess, Rock Paper Scissors,
  Battleship), and all twelve online-multiplayer games (Racing, Among Us, Fight
  Arena, Square Wars, Trivia Blitz, Air Hockey, Snake Arena, Tetris Duel, Fusion
  Grid, Card Rush, Zombie Survival, Space Battle). Any future offline-capable game
  reachable from `OfflineHubWindow` needs the same `setReturnAction`-style treatment
  Snake got, not just the standard MainMenu-only conversion. **Standing check for any
  future game/window**: grep for every external construction site of that game's
  window (not just `GameLauncher.java`) before considering it done - Chess's
  spectate-path bug is exactly the kind of thing that slips through otherwise.
- Also still wanted: the rules/detail page (`GameDetailDialog`) should fill the
  screen instead of being a small popup, and a game should be able to have chat
  "popped out" alongside it while playing (with some games, like a Gartic-Phone-style
  drawing game, needing chat *restricted* rather than open, since free chat would let
  players just say the answer out loud).

- **Reconnection audit finding, confirmed real, design drafted, implementation
  deliberately deferred.** Checked `TicTacToeMatch.handleDisconnect` (and the
  identical pattern repeated across all 30 `currentXxxMatch` fields on
  `ClientHandler`, each with its own `handleDisconnect(ClientHandler)`): any
  disconnect at all - a genuine quit or a one-second wifi hiccup, indistinguishable
  today - immediately ends the match and declares the other player the winner via
  `MATCH_OVER`/`OPPONENT_LEFT`. Confirms exactly what the platform strategy doc's
  Multiplayer Breakthroughs section predicted. Concrete plan for a real fix (not
  implemented tonight - see below for why):
  1. Give match classes a short grace state instead of ending immediately on
     disconnect: mark the player's slot "disconnected," start a timer (~30-60s),
     notify the *other* player "opponent disconnected, waiting..." instead of an
     immediate win.
  2. On login, check whether the account has a match awaiting reconnect (a small
     `accountId -> pending match` registry, likely on `MatchManager` or similar) -
     if so, re-associate the new `ClientHandler` with the match object (replacing
     the stale reference) and cancel the grace timer.
  3. If the timer expires with no reconnect, finalize exactly as today
     (`OPPONENT_LEFT`).
  4. Client-side: every online game's `Window` needs a new "opponent
     disconnected, waiting" UI state distinct from match-over, and the app needs a
     "you have a match in progress, rejoin?" flow on login/reconnect.
  **Why this wasn't attempted tonight despite being real and important**: it's
  genuinely cross-cutting (new message types, server-side timer/state logic,
  client UI changes, eventually all 30 match types) and state-sensitive in a way
  that's easy to get subtly wrong under time pressure - a half-built version could
  leave matches stuck in limbo, which is a worse outcome than today's honest
  instant-forfeit. This needs a proper, focused session (prove it on one simple
  turn-based game first, the same "prove it generalizes, then expand" approach used
  for `ai/search`/`engine`), not a rushed addition alongside a dozen other changes
  in one night.

## 📋 Planned — infrastructure & shared packages

- **A real `ui` empty/loading/error placeholder component.** After fixing the
  FriendsPanel/LeaderboardPanel/ShopPanel silent-failure bugs, all three now hand-roll
  their own near-identical "clear this container and drop in a muted label" logic. Three
  real call sites is enough repetition to justify a shared `ui` primitive (something
  like a `PlaceholderPanel` a caller sets to loading/empty/error state) instead of a
  fourth hand-rolled copy next time - not done now since the current fix isn't broken,
  just slightly duplicated, and a refactor of working code carries real risk for no
  user-facing benefit on its own. Worth doing the next time a panel needs the same
  three states.
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

## ❌ Not planned — voice chat

Previously listed here as a planned v1 feature (party-scoped PCM audio over a new
channel). The user has since said voice chat is being removed from scope - no audio
code was ever built (this was plan-only), so there's nothing to remove from the
codebase, just this section. Do not re-propose voice chat.

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
