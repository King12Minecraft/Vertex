# Vertex — Program Structure

Onboarding doc for the codebase: what each package does and how the pieces connect.
(This file covers the Java client/server codebase only. The public website
(`website/`) is a separate Python/Flask project with its own `website/README.md`.)
`VertexClient/` and `VertexServer/` are **no longer byte-identical** — `VertexServer/`
now only carries the ~100 files `ServerMain` actually needs (found by compiling just
that entry point against the full tree and keeping whatever the compiler pulled in,
not guesswork): `net`, `account`, `social`, `admin`, `economy`, `games` (rule
engines/match managers, no Window/Dialog classes), and `ai.knowledge` only. The entire
`ui`, `theme`, and `pages` packages, every game's Window/Dialog class, and the whole
`ai.search`/`ai.grid`/`ai.steering` practice-mode bot engine exist only in
`VertexClient/`. Everything below describes `VertexClient/` (the fuller codebase);
where a package/class also exists in `VertexServer/`, it's noted inline. A handful of
protocol/data classes are marked **(shared)** below: they carry a "SHARED (Common)"
javadoc tag and are the literal same file copied into both trees (`net/Message.java`,
`net/MessageType.java`, `net/NetworkConfig.java`, `games/GameInfo.java`,
`games/FileHash.java`, `account/Account.java`, `economy/ShopItemInfo.java`,
`economy/ChallengeProgressInfo.java`).

**That `(shared)` tag list is not the full picture, and has already caused one real
bug** (a practice-reward fix that only landed in `VertexServer/` and silently missed
`VertexClient/` - found and fixed 2026-09-25). The actual rule: every file under
`net`/`account`/`social`/`admin`/`economy`/`games` (the same packages `ServerMain`
needs, minus each game's Window/Dialog classes) needs to stay byte-identical between
the two trees, not just the handful with the javadoc tag - because
`VertexClient/ServerMain.java` is the edit-source-of-truth copy of the same class
`VertexServer.jar`'s manifest actually runs, and (until 2026-09-25) a
now-removed in-app "Host a Server" feature also ran this exact server logic live
from inside the ordinary client. Any server-side bug fix or behavior change in
those packages must be copied to the same path under `VertexClient/` too, checked
with a diff, not just assumed. `ClientHandler.java` is the biggest example that
isn't in the `(shared)` tag list despite needing to be.

**The client itself can no longer host a server (removed 2026-09-25).** The
in-app "Start Hosting" button (`SettingsPanel`) and `HostServerDialog.java` are
gone - hosting is exclusively a `VertexServer.jar` thing now, never something a
player can switch on from the regular client. `VertexClient/` still carries the
full server-side packages (`net`/`account`/`social`/`admin`/`economy`/`games`)
because it remains the edit-source-of-truth copy that gets synced into
`VertexServer/`, and because `VertexClient/ServerMain.java` exists as that same
copy of the dedicated-server entry point - neither of those is a player-facing
hosting feature.

## Entry points

- **`Vertex.java`** — client entry point (`VertexClient/` only). Shows
  `ui/SplashScreen`, then `account/AuthWindow` (login), installs a last-resort
  uncaught-exception handler (plain `JOptionPane`, so it works even if theming itself
  is broken), and kicks off `net/ClientUpdateChecker` in the background.
- **`ServerMain.java`** — server entry point, in both trees but not identical between
  them (see above). Starts `net/GameServer` (the real multiplayer server socket) and
  blocks the main thread forever (`GameServer`'s accept loop is a daemon thread, so
  something non-daemon has to keep the JVM alive) — headless, no GUI, no window,
  logs to the console. Earlier versions also opened an `AuthWindow`/client UI in the
  same process ("a server is also a playable client pointed at itself"); that's gone
  now that `VertexServer/` is meant to run unattended on a real machine, where opening
  a Swing window would throw `HeadlessException`. The old combined behavior is still
  available by running `VertexServer.jar` and `VertexClient.jar` side by side.

## Architecture at a glance

```
net (protocol + dispatch)
  -> games (plugin-style match framework + ~30 online-game triples + ~21 offline games)
       -> economy / social / ai   (services the match layer calls into - ai.knowledge
                                    only for the server; the rest is client-only)
  -> pages / ui / theme            (Swing client shell - VertexClient/ only)
account / admin                    (identity + role gating, cross-cutting client & server)
```

A client message becomes a running game like this: `ClientHandler` reads a `Message` off
the socket → dispatches by `MessageType` (e.g. `FIND_MATCH_REQUEST`) → the matching
`<Name>MatchManager.findMatch(this)` pairs the player and constructs a `<Name>Match` →
`ClientHandler.setCurrentXxxMatch(match)` stores it on the connection → subsequent
`MAKE_MOVE_REQUEST`s route straight to `currentMatch.handleMove(...)`.

## net — networking and the central message dispatcher

- **`GameServer.java`** — the composition root. Constructs every manager (accounts,
  economy, leaderboard, achievements, chat, party, friends, moderation, feedback,
  replay, tournaments, avatars, admin log, and one `<Name>MatchManager` per online
  game — ~30 of them), wires cross-manager dependencies (e.g.
  `leaderboardManager.setAchievementManager(...)`), builds the `ai.knowledge` trivia
  fact-lookup stack and threads it into `TriviaMatchManager`, then runs the accept loop:
  each new `Socket` becomes a `ClientHandler` on its own `Thread`, given references to
  every manager.
- **`ClientHandler.java`** (~2900 lines) — one instance per connected client. Reads
  `Message`s in a loop and dispatches through a large `MessageType` → private
  `handleXxx` chain (100+ request types). Holds per-connection state: logged-in
  account, and one `current<Game>Match` field per online game. On disconnect, cancels
  waiting/active matches on every manager so nothing hangs server-side. Also handles
  login/account creation (signing up only ever creates a `Role.PLAYER` - the first admin comes from `account/AdminBootstrap` at the server console),
  moderation and admin actions (role re-checked here, server-side, for every privileged
  request), avatars, feedback, and client auto-update.
- **`Message.java`** / **`MessageType.java`** *(shared)* — the wire protocol: one
  multi-field `Message` class carrying whichever optional fields a given `MessageType`
  needs, rather than one class per message type.
- **`VertexSerializationFilter.java`** *(shared)* — a `java.io.ObjectInputFilter`
  allow-list applied to every `ObjectInputStream` that reads a `Message` off a raw
  socket (`ClientHandler` and `NetworkManager` both call
  `in.setObjectInputFilter(VertexSerializationFilter.FILTER)` right after
  constructing their stream). Closes the classic unfiltered-`readObject()` Java
  deserialization RCE class of bug - without it, either side would deserialize
  whatever class graph the other end sends, no matter what. Adding a new
  custom-type field to `Message.java` (or to one of the types it already carries)
  means adding it to the filter's allow-list too, or that field silently stops
  deserializing.
- **`NetworkManager.java`** — client-side counterpart, one persistent socket, three
  modes: blocking request/response `send()`; fire-and-forget `sendAsync()` +
  `PushListener` for server-initiated pushes; offline queueing for `sendAsync` while
  disconnected, flushed on reconnect. The push-listener queue has no per-request
  correlation, so a few UI panels deliberately use blocking `send()` on a background
  thread instead of `sendAsync` + listen, to avoid one call stealing another's reply.
  `sendAsync()` always returns `true` (queues rather than fails) - callers that need
  to tell the player the server isn't reachable should call
  `describeIfNotReady()` instead of checking `sendAsync`'s return value; it returns a
  player-facing string for OFFLINE/CONNECTING/RECONNECTING and `null` when ONLINE. All
  25 find-match screens across the games package use this pattern.
- **`NetworkConfig.java`** *(shared)* — shared mutable host/port config, set at runtime.
- **`ClientUpdateChecker.java`** / **`ClientUpdatePackage.java`** — auto-update: client
  hashes its running jar (`games/FileHash`) and asks the server if it's stale; server
  re-reads `Vertex.jar`'s hash whenever its mtime changes. Client stages
  `Vertex.jar.new`; an external native launcher swaps it in on next start. Before
  staging, the client re-hashes the download against `Message.newJarHash` (the
  server's own jar hash, sent alongside the version-check response) and refuses to
  stage it on a mismatch - an integrity check against a corrupted transfer only, not
  an authenticity one. **No code signing and no TLS yet** - the single most severe
  security gap flagged in the platform strategy analysis, tracked as an open
  question in `BLOCKED_QUESTIONS.md` rather than guessed at.
- **`ConnectionState.java`** / **`ConnectionIndicator.java`** — connection-status enum
  + colored-dot widget. **`NavigationListener.java`** — callback interface letting
  `pages/Sidebar` report nav clicks without knowing how paging works.

## games — the match framework, plus ~30 online-game triples and ~21 offline games

Two repeating shapes, not 179 individual designs:

1. **Offline/single-player** (Snake, Tetris, 2048, Number Nest, Minesweeper, Sudoku,
   Simon Says, Whack-a-Mole, Match Three, Maze Chase, Brick Breaker, Flappy Bird,
   Galaxy Defender, Word Guess, Bubble Shooter, Lights Out, Peg Solitaire, Klondike,
   Yahtzee, Mancala, Puzzle Quest, Dino Dash, Crossing Road, Aim Trainer, Pong, and
   more): a `<Name>Game.java` (pure state/logic) + `<Name>Window.java` (Swing host),
   sometimes a `<Name>Panel.java`. No server involvement. **Number Nest** (added
   2026-09-27) is the games-backlog's `Threes`/`1010!`-genre concept - a 5x5 grid,
   place one offered piece at a time into any empty cell (nothing slides, unlike
   2048), merging with at most one orthogonally-adjacent equal-value neighbor per
   placement (fixed scan order, deliberately no cascade); game over exactly when the
   grid is full, since placement never needs adjacency the way 2048's slide does.
   `NumberNestGame`/`NumberNestWindow` follow `Merge2048Game`/`Merge2048Window`'s
   exact split and share its tile-color formula for visual consistency between the
   two number-merge games. **Sky Hopper** (added 2026-09-29) is the games backlog's
   vertical-climber concept (`Doodle Jump` genre) - bounce automatically off
   procedurally generated platforms (normal/moving/breakable/spring, weighted
   55/20/15/10%) as a camera that only ever scrolls up follows the player past a
   fixed screen fraction; falling below the bottom of the current view ends the run.
   World coordinates use a screen-like y-down convention throughout (climbing
   decreases y) so the physics and the renderer share the same numbers with no unit
   conversion, unlike `HillClimbGame`'s separate `PIXELS_PER_UNIT` scale.
   `SkyHopperGame`/`SkyHopperWindow` follow the same `<Name>Game`/`<Name>Window`
   split and `engine.GameLoop` usage `HillClimbGame`/`HillClimbWindow` established.
2. **Online/multiplayer** (~30: Tic-Tac-Toe, Connect Four, Checkers, Chess, Battleship,
   Reversi, Dots and Boxes, Rock Paper Scissors, Memory Match, Air Hockey, Word Duel,
   Dice Duel, Snake Arena, Tetris Duel, Fusion Grid, Typing Duel, Signal Grid, Card
   Rush, Square Wars, Racing, Among Us, Zombie Survival, Fight Arena, Space Battle,
   Trivia Blitz): a **triple** — `<Name>Match.java` (server-authoritative rules/state),
   `<Name>MatchManager.java` (matchmaking queue, constructs the Match, wired with
   whichever of `EconomyManager`/`GameHistoryManager`/`ChatManager`/`LeaderboardManager`/
   `AchievementManager`/`ReplayManager`/`PartyManager` it needs), `<Name>Window.java`
   (client UI). Four sub-patterns for how a Match actually runs, by game:
   - **Turn-locked/state-broadcast** (Chess, Battleship, Connect Four, Checkers,
     Reversi, ...): validate → apply → broadcast new state.
   - **Shared-seed independent simulation** (Racing, Zombie Survival, Space Battle):
     every player gets the same RNG seed, plays an identical procedurally-generated run
     **locally with no position sync**, and reports only a final outcome — a deliberate
     scalability tradeoff over broadcasting positions 60×/sec.
   - **Real continuous tick loop** (Fight Arena's `FightMatch`) — the one game with a
     genuine ~15/sec server tick thread and full-authoritative snapshot broadcast.
   - **Shared live grid, discrete claims** (`SquareWarsMatch`) — not turn-locked; any
     player can claim any cell any time, server validates and broadcasts per claim.

Framework/shared classes worth knowing (read these instead of the ~30 game triples):

- **`Game.java`** — the interface offline games notionally implement (`getInfo()`,
  `start()`, `pause()`, `saveState()`/`loadState()`).
- **`GameInfo.java`** *(shared)* — one catalog entry: id, name, category, mode label,
  online/comingSoon flags, version.
- **`GameRegistry.java`** — the hardcoded master catalog (49 `GameInfo` entries,
  byte-identical between `VertexClient` and `VertexServer` - never references Swing,
  since the server compiles this file too with no UI classes available to it).
  `ClientHandler.handleGameList()` serves this, patching in live queue counts for a
  few games. One capability flag so far: `spectatable` (true only for Chess, Rock
  Paper Scissors, Battleship - the three games with a real `SpectateDialog` "Watch"
  entry point), set via a small `markSpectatable(...)` helper called after the list
  is built rather than a required constructor parameter every other call site would
  have to pass. `type` ("Single Player"/"Multiplayer"/"Single/Multiplayer") already
  mostly covers offline-capability, so a separate redundant flag for that wasn't
  added. Further capability flags (min/max players, party-joinable) are deliberately
  not built ahead of a concrete feature that needs them.
- **`GameManager.java`** — client-side cache of the fetched catalog.
- **`GameWindowFactory.java`** — client-only (unlike `GameRegistry`/`GameInfo`,
  since every entry here references a `*Window` constructor that only exists in
  `VertexClient`): a `Map<String, Supplier<JComponent>>` from game id to "how to
  build its embedded window." Replaces what used to be a 49-branch `if/else` in
  `GameLauncher.openGame` - by the end of the embedded-games rollout, every single
  branch had the exact same shape (`MainMenu.getInstance().showGame(new
  XxxWindow())`), so it collapsed mechanically into one map. Verified with a smoke
  test constructing all 49 windows through the factory and confirming the id set
  matches exactly, nothing throws, and an unknown id returns `null`.
- **`GameWindowKernel.java`** — two static helpers for the Swing boilerplate every
  embedded game window has hand-rolled at least once (many several times) since the
  embedded-games conversion: `centered(JComponent)` wraps content in a non-opaque
  `GridBagLayout` panel so it renders at its own preferred size instead of stretching
  to fill a `CardLayout`/`BorderLayout.CENTER` slot (the exact bug this session hit
  and fixed, in a slightly different disguise, on nearly every one of the 49 game
  conversions); `center(Container, JComponent)` is the same fix for the other common
  shape, where a screen's own outer panel should do the centering itself rather than
  being wrapped by a new one. `leaveButton(Runnable)` builds the standard themed
  "Leave" button, with the actual leave action left to the caller (most call
  `MainMenu.getInstance().returnToGames()`; `SnakeWindow` routes through its own
  `setReturnAction` callback instead). Deliberately a static helper, not a base class
  every window must extend - the existing ~51 windows have too much individual shape
  to retrofit onto one shared superclass safely in one pass; adopted opportunistically
  so far by `ReversiWindow`/`BrickBreakerWindow` as the proof it generalizes (one
  online-multiplayer game, one offline game), not yet rolled out further.
- **`GameLauncher.java`** — the plugin **launch** dispatch, and the mandatory
  rules-page gate every "Play" entry point (games page, quick-play dropdown, global
  search, hero banner, game invites) already shares: `launch(Component, GameInfo)`,
  the only public method, shows the in-app game-detail page (`GameDetailPanel` via
  `MainMenu.showGameDetails(...)`: art, tags, difficulty, rules, a real Play button)
  instead of opening the game directly - there's no way to skip
  straight to playing. Package-private `openGame(...)` (callable only from
  `GameDetailPanel`'s own Play button, same package, once someone has actually seen
  that page) is now a single `GameWindowFactory.factoryFor(id)` lookup instead of a
  branch per game: `MainMenu.getInstance().showGame(factory.get())` when a factory
  exists, otherwise the same "not converted yet" notice `launch(...)` already shows
  for `comingSoon` ids. **All 53 games in the catalog** go through this path - every
  offline/single-player game (including Hill Climb, the first new game added after
  the embedded-games rollout - see the "Done" section of `ROADMAP.md` for what it
  demonstrates), both games with `SpectateDialog`/tournament support (Chess, Rock
  Paper Scissors, Battleship), and every online-multiplayer game (including
  Telephone, `games/TelephoneWindow.java` - a Gartic-Phone-style draw/guess
  chain, see `ROADMAP.md`'s "Done" section).
  A game reachable from more than one place (those same three `SpectateDialog`
  "Watch" entry points, separate from `GameLauncher`) needs every one of those
  call sites updated, not just the main one - a real bug (Chess's spectate path silently doing nothing once
  `ChessWindow` became a `JPanel`) shipped from missing this the first time
  and had to be found and fixed separately; Rock Paper Scissors's and
  Battleship's own spectate lines were fixed proactively in the same
  conversion instead.
  Snake is also reachable pre-login from `pages/OfflineHubWindow.java`
  ("Play Offline" on the login screen), which has no `MainMenu` to hand off
  to - see `SnakeWindow.setReturnAction(...)` below.
- **`EmbeddedGamePanel.java`** - implemented by a game panel embedded in
  `MainMenu`'s game-host slot rather than opened as its own window (`ChessWindow` is
  the first). One method, `requestLeave()`: `MainMenu` calls it before navigating away
  from a hosted game (e.g. a Sidebar click mid-match) - the embedded replacement for
  the old per-window `windowClosing` confirmation, since there's no window-close event
  once a game isn't its own window. Return `false` to intercept (show a confirm
  dialog, then call `MainMenu.getInstance().returnToGames()` yourself if confirmed)
  rather than letting navigation proceed silently mid-match. A conversion gotcha
  found while converting Reversi (the second game done this way, after Chess):
  a board that fills its space automatically because it's laid out with a real
  layout manager (Chess's is a plain `GridLayout(8,8)` of cell components) needs
  no extra work, but a board that's one custom-painted `JPanel` drawing itself at
  a hardcoded pixel size (Reversi's is) keeps that same size once embedded and
  just sits pinned in a corner of the much bigger game-host slot with dead space
  around it - fixed the same way an over-small mode-select screen is fixed:
  wrap it in a `GridBagLayout` panel with no constraints (auto-centers) rather
  than trying to make the paint code itself size-aware, which would also risk
  breaking its pixel-based mouse-click math. Tic-Tac-Toe (fifth game
  converted) turned up a variant of the same bug: its board was already
  wrapped in a centering container, but that container used `FlowLayout`,
  which only centers its children horizontally - vertically it always
  top-aligns - so the board stayed pinned to the top with dead space below
  once embedded. Same `GridBagLayout` fix applies; the lesson generalizes to
  any pre-existing "centering" wrapper, not just ones that are missing
  entirely. Snake (eighth game converted, and the first offline/single-
  player one) turned up a different kind of gotcha: it's also reachable
  pre-login from `pages/OfflineHubWindow.java`'s "Play Offline" screen,
  which is its own standalone `JFrame` with no `MainMenu` to hand off to -
  hardcoding `MainMenu.getInstance().returnToGames()` the way every other
  converted game does would `NullPointerException` for a logged-out guest.
  `SnakeWindow.setReturnAction(Runnable)` fixes this: unset, it falls back to
  the usual `MainMenu.getInstance().returnToGames()`; `OfflineHubWindow`
  supplies its own small `CardLayout` "go back to the hub" callback instead.
  Any future offline-capable game added to `OfflineHubWindow` needs the same
  treatment.
- **`GameMetadata.java`** — presentation-only (difficulty, tags) for game detail
  dialogs; has no effect on matchmaking or gameplay.
- **`MatchManager.java`** — the original reference matchmaking manager (for
  `tictactoe-online`): FIFO pairing queue, constructs the Match, broadcasts queue-count
  updates. Every other `<Name>MatchManager` still follows this same shape with per-game
  tuning, though it's now also available as `MatchmakingKernel` (below) for a new
  adopter to build on top of instead of hand-rolling it fresh - `MatchManager` itself
  stays hand-rolled since retrofitting the reconnection-registry-owning original isn't
  worth the churn for no behavior change. Its `getReconnectRegistry()` returns the server-wide
  `mechanics.ReconnectRegistry.shared()` (since 2026-09-30), passed into each `TicTacToeMatch`.
- **`MatchmakingKernel.java`** — the FIFO waiting-queue shape every `<Name>MatchManager`
  hand-rolled, extracted generic over the match type via a small `PairHandler`
  interface (`pair(matchId, a, b)` constructs+starts the match; `attach(handler, match)`
  wires it to that handler's own `currentXxxMatch`-style field). The kernel itself
  never constructs, starts, or inspects a match - it only tracks the waiting list and
  the `matchId -> match` map, delegating everything match-type-specific to the
  `PairHandler`. Takes an optional `matchIdPrefix` separate from `gameId` (defaults to
  `gameId` if the 4-arg constructor is used) - found necessary retrofitting Connect
  Four, whose matches are `"connect4-N"` while its `GAME_ID` (used for `QUEUE_UPDATE`/
  history tracking) is `"connect-four"`; preserved exactly rather than silently
  changed, even though the client only ever compares `matchId` for equality and never
  parses it. Its `getReconnectRegistry()` returns the shared `mechanics.ReconnectRegistry` (was an instance of its own before 2026-09-30)
  - every kernel-backed match type gets grace-period reconnect support for free the
  moment it adopts the kernel, added 2026-09-26 alongside the reconnect rollout below.
  Adopters: `CheckersMatchManager` and `ConnectFourMatchManager` (the original two,
  proving the shape generalizes and the `matchIdPrefix` divergence case), `Reversi-
  MatchManager` and `DotsAndBoxesMatchManager` (converted from their own hand-rolled
  queues while adding reconnect, since they needed a registry anyway), and
  `DiceDuelMatchManager`/`TypingDuelMatchManager` (2026-09-29 - plain 2-player FIFO
  managers with no rematch/spectate/tournament glue, textbook conversions; hit the
  same `matchIdPrefix` divergence Connect Four did - each game's real matchId format
  has no hyphen while its `GAME_ID` does, and a first-pass conversion using the
  kernel's short constructor would have silently changed it server-wide, caught by
  `VertexServerTests/games/MatchmakingKernelAdoptersTest.java` before landing).
  Neither Dice Duel's nor Typing Duel's `Match` class takes a `ReconnectRegistry`
  (neither game has adopted reconnect), so unlike `CheckersMatchManager` these two
  don't expose `kernel.getReconnectRegistry()`. Also `AirHockeyMatchManager`/
  `MemoryMatchMatchManager` (2026-09-29 - same textbook shape, same
  `matchIdPrefix` divergence pattern: real matchIds "airhockey-"/"memory-" vs.
  `GAME_ID`s "air-hockey"/"memory-match"; this round applied the lesson
  proactively and hardcoded the correct prefixes from the start rather than
  hitting the bug again). Neither's `Match` class takes a `ReconnectRegistry`
  either, so neither exposes `kernel.getReconnectRegistry()`. Also
  `SignalGridMatchManager`/`FusionGridMatchManager` (2026-09-29, same day's work) -
  identical shape and the same `matchIdPrefix` divergence ("signalgrid-"/
  "fusiongrid-" vs. `GAME_ID`s "signal-grid"/"fusion-grid"), prefixes hardcoded
  correctly from the start; neither's `Match` class takes a `ReconnectRegistry`
  either. Also `CardRushMatchManager`/`SnakeArenaMatchManager`/
  `TetrisDuelMatchManager` (2026-09-29, same day's work) - identical shape and
  the same `matchIdPrefix` divergence ("cardrush-"/"snakearena-"/"tetrisduel-"
  vs. `GAME_ID`s "card-rush"/"snake-arena"/"tetris-duel"), prefixes hardcoded
  correctly from the start; none of the three `Match` classes take a
  `ReconnectRegistry` either. `RacingMatchManager`/`SpaceBattleMatchManager`
  (3-6 player group races)/`SquareWarsMatchManager` (2-4 player groups) and
  `ChessMatchManager` (spectator support the kernel doesn't have) were checked
  and screened out as not drop-in fits for this kernel as it exists today. A
  wider rollout to the remaining ~13 `<Name>MatchManager` classes remains
  optional cleanup, adopted a couple at a time whenever convenient, not a
  requirement (see `ROADMAP.md` for good next candidates and which games are
  deliberately NOT drop-in fits as the
  kernel exists today). ELO
  deliberately isn't part of this - `LeaderboardManager`'s rating math is a separate,
  already-shared concern untouched by matchmaking queue mechanics.
- **`ReconnectRegistry.java`** — **moved to `mechanics/` on 2026-09-30, and the adopter history below is now partly out of date: every game listed as "not adopting" further up (Dice Duel, Typing Duel, Air Hockey, Memory Match, Signal Grid, Fusion Grid, Card Rush, Snake Arena, Tetris Duel) and Chess have since adopted it - see the `mechanics` section for the current state and the 30-second window (the 45s mentioned below is historical).** Generic disconnect-grace-period mechanism, keyed by
  accountId (a brand-new `ClientHandler`/socket exists on reconnect, so accountId, not
  the handler reference, is the only stable identity). Any match class can adopt it by
  implementing the small `ReconnectableMatch` interface (`onReconnectTimeout()`,
  `onReconnect(newHandler)`, `attachToHandler(handler)`) and calling
  `beginGracePeriod(accountId, this)` from its own disconnect handling. Adopters:
  `TicTacToeMatch` (the original, via `MatchManager`'s own registry instance),
  `ConnectFourMatch`/`CheckersMatch`/`ReversiMatch`/`DotsAndBoxesMatch` (2026-09-26,
  each via its manager's `MatchmakingKernel`-supplied registry), and `WordDuelMatch`
  (2026-09-27, via its own direct registry field on `WordDuelMatchManager` - proof that
  adopting this doesn't require `MatchmakingKernel` migration at all, the same
  stand-alone shape `MatchManager` always used). The same `disconnectedSlot`/
  grace-period/timeout shape in every one, differing only in how each match names its
  two player slots (`DotsAndBoxesMatch` uses a `List<ClientHandler>` and an index
  rather than two named fields; `WordDuelMatch` uses a nullable `Boolean` for the same
  reason `TicTacToeMatch` uses a char - two named fields, not a list). (Chess was
  not adopted at the time - its resign/draw-offer state interacted with a
  mid-grace-period reconnect in ways not yet designed, tracked in `ROADMAP.md` rather
  than guessed at.) `ClientHandler.handleLogin()` tries every reconnect-aware match
  type's own registry in turn (`tryReconnectAllGames()`) and, if one had a match
  waiting, populates the `LOGIN_RESPONSE` with everything the client needs to resume
  (`reconnectGameId`/`reconnectTurnSymbol` plus the existing `matchId`/`symbol`/
  `opponentUsername`/`boardState` fields a match-found push already carries) - the
  client (`pages/MatchResume.resumeIfPending` - was `AuthWindow.resumeMatchIfPending` until 2026-09-30 - via the small per-game
  `reconnectMessageTypesFor()` lookup covering all 8 adopters' own message-type pairs)
  reconstructs the equivalent of a fresh match-found + update locally from those fields
  and feeds them straight to a newly-built game window, deliberately not via a second
  server push to the reconnecting client's own socket (that race is explained in
  `ROADMAP.md`). A guest (no account) disconnect always forfeits immediately - no
  stable identity to grant a grace period against. Threading note load-bearing for
  anyone extending this to another game: `ReconnectRegistry`'s own lock must only ever
  be acquired either alone, or immediately before calling into the match (never the
  reverse) - see the class's own javadoc for the full reasoning.
  **A real limit found adding the 6th adopter (Word Duel, 2026-09-27):**
  `resumeMatchIfPending()`'s generic reconstruction only ever sets `symbol`/
  `boardState` on the synthetic push messages - correct for the first 5 adopters
  purely because they're all flat-board grid games whose real protocol happens to fit
  those exact two fields, not because the mechanism is actually generic. Word Duel's
  real protocol uses `triviaQuestion`/`triviaScores` instead, so `WordDuelMatch.
  onReconnect()` repurposes `ReconnectResult`'s `boardState` slot to carry the match's
  letters and its `turnSymbol` slot to carry the reconnecting player's own
  "mine:opponent" length tuple (see that method's javadoc), and
  `resumeMatchIfPending()` gained one small `if ("word-duel".equals(gameId))` branch
  to unpack them back into the fields `WordDuelWindow` actually reads. **Battleship now
  joins the reconnect-aware games too** (`BattleshipMatch`/`BattleshipMatchManager` gained
  the same `ReconnectRegistry`/grace-period shape; `fire()` now also rejects a shot
  attempt server-side while the opponent is mid-grace-period, matching `TicTacToeMatch`'s
  freeze check) - its real MATCH_FOUND already uses plain symbol/boardState, so
  `resumeMatchIfPending()` only needed a "skip the generic second UPDATE message"
  branch rather than a repurposing one (Battleship has no generic `*_UPDATE` type, only
  the richly-shaped `BATTLESHIP_FIRE_RESULT`); the still-connected opponent's "waiting to
  reconnect" UI is cleared via that same message type sent with a `cellIndex` of -1 as a
  sentinel (`BattleshipWindow` recognizes it as a resync, not a real shot). Honestly-
  flagged gap: `ReconnectRegistry.ReconnectResult` has no slot for "every past shot," so
  the *reconnecting* player's own two grids repaint from a fresh fleet-layout MATCH_FOUND
  rather than replaying their hit-marker history - cosmetic only, server-side state
  (whose turn, which cells are already fired) is never wrong. **Rock Paper Scissors is the
  8th adopter (2026-09-29)** (`RockPaperScissorsMatch`/`RockPaperScissorsMatchManager`
  gained the same shape; `submitMove()` now rejects a move server-side while the opponent
  is mid-grace-period, same freeze check as Battleship's `fire()`) - a third distinct
  per-game accommodation, not a repeat of either earlier one: RPS has no board and no
  turn at all (moves are blind and simultaneous), so neither `mySymbol` nor `boardState`
  carries anything meaningful by default - `RockPaperScissorsMatch.onReconnect()` instead
  packs the running score into `ReconnectResult.boardState` as `"myScore:opponentScore"`,
  which `resumeMatchIfPending()`'s new `if ("rock-paper-scissors".equals(gameId))` branch
  unpacks into the synthetic push's `rpsMyScore`/`rpsOpponentScore` fields before it's
  replayed (client-side, `RockPaperScissorsWindow`'s `RPS_MATCH_FOUND` handling was
  reading those two fields as an unconditional reset to 0-0 - now reads them off the
  message instead, a one-line fix that costs a genuinely fresh match nothing since those
  fields are simply unset/0 on one). No generic `*_UPDATE` message needed either, same as
  Battleship: the live "resume" push to the still-connected opponent just reuses
  `RPS_MATCH_FOUND` itself (with real `rpsMyScore`/`rpsOpponentScore` set, unlike the
  login-response DTO's repurposed boardState) since there's no board/turn state a second
  message would need to correct. Verified with a 15-check scratch test covering the same
  six scenarios Battleship's did. That's all 7 of the platform's 2-player,
  forfeit-based online games covered. Trivia Blitz is the one online-multiplayer
  game left with no reconnection story, but audited (2026-09-29) and found NOT to
  be a small next adopter: it's 2-6 players and `TriviaMatch.handleDisconnect()`
  already deliberately keeps a match running (score locked in) rather than
  forfeiting on a disconnect, so `ReconnectRegistry`'s one-opponent/grace-period/
  forfeit shape doesn't actually fit it - see `BLOCKED_QUESTIONS.md` for the real,
  un-guessed design question this raises. Genuinely continuous-simulation games
  (Racing, Space Battle, Air Hockey, Fight Arena, Zombie Survival) and Chess
  (deliberately not yet adopted, see above) remain separate, lower-priority cases.
- **`TournamentManager.java`** — 4-player single-elimination bracket for Battleship and
  Rock Paper Scissors only (both always produce a decisive winner). **`TeamTournament-
  Manager.java`** — team version for Fight Arena's 2v2/3v3, registered by whole
  `social.Party`, deliberately a single decisive match rather than a full bracket.
- **`ReplayManager.java`** — Chess-only for now, persists full board snapshots per
  match, stepped Prev/Next client-side. Battleship and Rock Paper Scissors have their
  own separately-shaped replay viewers, not generalized through `ReplayManager`.
- **`FileHash.java`** *(shared)* — SHA-256 helper backing the auto-update jar-hash
  check. **`GameRules.java`**/`GameRulesDialog.java` — static per-game "how to play"
  text + popup.
- **`GameDetailPanel.java`** (replaced `GameDetailDialog`, 2026-09-29) — the "about
  this game" step as a real page in `MainMenu`'s `CardLayout` (`Pages.GAME_DETAIL`), not
  a modal popup. `MainMenu.showGameDetails(game)` builds one and remembers which page
  opened it; Back/Escape returns there (Games page if it was opened from inside a running
  game). Client-only. Wraps its rules text with an HTML table width because
  `<body style='width:..'>` is ignored on newer JDKs.
- Shared dialog chrome reused across many games: `GamePickerDialog`,
  `ConnectDialog`, `ServerBrowserDialog`, `SpectateDialog`
  (Chess-only), `RematchOfferDialog`, `ReplayBrowserDialog`. (`HostServerDialog` was
  removed 2026-09-25 along with the in-app hosting feature - see above.)
- **`TriviaLiveLookups.java`** — plain holder bundling the 5 `ai.knowledge`
  `CachingFactLookup` instances `GameServer` builds once, threaded through
  `TriviaMatchManager` into every `TriviaMatch`.

Bot-AI-bearing games bridge into the `ai` package: `TicTacToePracticeMatch` +
`TicTacToePracticeBotStrategy`/`TicTacToeRandomMoveStrategy`; `BattleshipWindow` +
`BattleshipBotStrategy`/`BattleshipAI`/`BattleshipRandomShotStrategy`;
`RockPaperScissorsWindow` + `RockPaperScissorsBotStrategy`/
`RockPaperScissorsFixedMoveStrategy`; `MazeChaseGame` +
`MazeChaseChaserBotStrategy`/`MazeChaseChaserRandomStrategy`/`MazeChaseChaserState` —
all register primary+fallback strategies with `ai.AiKernel`. `ConnectFourWindow`,
`ReversiWindow`, `DotsAndBoxesWindow`, `CheckersWindow`, `ChessWindow`, and
`SignalGridWindow` do too, but via the shared `ai/search` engine instead of bespoke
per-game bot classes: each brings only a `GameModel` adapter
(`ConnectFourGameModel`/`ReversiGameModel`/`DotsAndBoxesGameModel`/`CheckersGameModel`/
`ChessGameModel`/`SignalGridGameModel`) and registers `ai.search.GenericBotStrategy` +
`ai.search.RandomMoveStrategy` as its primary/fallback pair. Checkers and Chess are
parameterized over small custom state/move pairs (not a plain `char[]`/`Integer` like
the other four) since their real rules need more than a board: Checkers' mandatory-
multi-jump rule needs to know which piece must keep capturing; Chess needs castling
rights, the en passant target square, and (uniquely among all six) whose turn it
currently is, since checkmate/stalemate depend on that and not just the board.
`ChessWindow` previously had no offline mode at all ("a real chess engine is a much
bigger undertaking on its own" - no longer true once the shared engine already existed
for other games); it gained a mode-select screen, matching the rest, rather than the
single always-online layout it had before. Memory Match, Fusion Grid, Word Duel, and
Dice Duel don't fit this engine's perfect-information, deterministic-transition
assumptions (hidden card values, random future draws, or randomness mid-decision), so
each got its own bespoke heuristic bot instead of a `GameModel`. Word Duel
(`WordDuelBotStrategy`/`WordDuelFallbackStrategy`, scoring words via
`WordDuelWordList.bestWordFrom`/`anyWordFrom`) and Fusion Grid (`FusionGridBotStrategy`/
`FusionGridFallbackStrategy`, scoring each empty cell via a pure `simulateCascade`
replay of the real merge rule) are still plain `ai.BotStrategy` registered with
`AiKernel` — a bot turn there really is one `chooseMove` call, non-adversarial (Word
Duel) or heuristic-scored (Fusion Grid), so the existing framework fits as-is. Dice
Duel (`DiceDuelBotStrategy`) and Memory Match (`MemoryMatchBotStrategy`) are standalone
classes called directly by their `Window`, not through `AiKernel`, because a turn there
is inherently more than one decision: Dice Duel's `chooseDiceToHold`/`chooseCategory`
(greedy against `DiceDuelMatch.scoreFor`) happen across up to two rerolls: and Memory
Match's `chooseFirstFlip`/`chooseSecondFlip` need a private, per-match `known` map fed
by an `observe(index, symbol)` call on *every* flip (the bot's own and the human's) so
it only "remembers" what has actually been shown on screen — the one genuinely
hidden-information game here, and deliberately not omniscient. `MemoryMatchWindow`'s
practice mode runs on its own `MemoryMatchPracticeMatch` rather than the shared
`ai/search` `PracticeMatch`, for the same reason: that class assumes both players see
one shared state, which doesn't hold when part of the state (face-down cards) is
genuinely secret.

## dominion — Vertex: Dominion's core simulation + networking

Full design in `DOMINION_DESIGN.md`. Now on CLAUDE.md's byte-identical sync-rule
list (added 2026-09-26 the moment `net/Message.java`/`net/ClientHandler.java`
started referencing `dominion.*` types directly for the networking layer below) -
`VertexClient` carries the whole package byte-identical even though, like
`TicTacToeMatch` and every other match class, the client doesn't actually run the
authoritative logic itself.

**Data model** (all `Serializable` - see "Networking" below): `Province` (one grid
tile - terrain, owning nation id, orthogonal-only adjacency), `Nation` (V1: one
account per nation - treasury, honor, no council/members yet; `isValidName()`
rejects `"|"` and enforces a 2-30 character letters/digits/spaces/apostrophes/
hyphens format, the same pipe-delimited-save-format-corruption bug class
`ServerAccountStore.isValidUsernameFormat` already guards against for usernames),
`Army` (single generic unit type, a `marchOrderTargetProvinceId` holding a queued
order until the next tick), `RelationType`/`DiplomaticRelation` (NEUTRAL/ALLIANCE/
NON_AGGRESSION/WAR between two nations - a declared WAR is deliberately not
immediately active, `effectiveFromTick` is always current tick + 1, a strategic-
pacing choice not a technical one).

**Simulation**: `DominionWorld` (all live state in memory, plus `foundNation()` -
validates the name, that the account doesn't already have a Nation, and that the
target province is unclaimed, then allocates a Nation id and claims the province;
`toSnapshot()` builds the client-facing `DominionSnapshot`) and `DominionTickEngine`
(the once-per-day resolution pass: advances the clock, then resolves every army's
standing march order - an uncontested claim onto unclaimed territory, a simple
relocation onto the army's own territory, or a decisive win/loss combat resolution
when marching into contested enemy territory during an active war - combat power
is troop count x a terrain defense multiplier, no partial attrition or unit-type
variety yet, matching V1's intentionally small scope). Proven with a 16-check test
covering exactly the scenario the design's build order calls for (two nations, a
declared war, an army march, a combat outcome, a province flipping ownership) plus
edge cases.

**Persistence**: `DominionStore` (save/load for a `DominionWorld` - its own
isolated flat file `gamehub_dominion.dat`, same pipe-delimited/type-tagged-line/
backward-compatible-by-field-count convention as `ServerAccountStore`, one line
per record with a leading tag `TICK`/`PROVINCE`/`NATION`/`ARMY`/`RELATION` since
this one file holds every entity type rather than one file per type). Proven with
a 20-check test: a full save-then-reload round trip for every entity type
including a declared-but-not-yet-active war (must survive exactly, not get
recomputed relative to the new tick), a real mid-game scenario continuing to
resolve correctly after a reload, and loading with no file present yielding a
fresh empty world rather than erroring.

**Networking** (build order step 3, now complete - 2026-09-26): `DominionManager`
is the single whole-server front door - unlike every other game's per-match
manager, there is exactly one `DominionWorld` for the entire server (loaded once
at startup via `DominionStore`; seeds a deterministic placeholder 10x10 map on
first launch if the loaded world has no provinces, since an empty world has
nothing to found a Nation on - the real map size/terrain distribution are still
"deliberately not decided" per `DOMINION_DESIGN.md`, pending playtesting). Seven
message-type pairs: `DOMINION_FOUND_NATION_*`/`DOMINION_STATE_*` (first slice -
found a nation, fetch a full world snapshot to render) and, same day,
`DOMINION_RECRUIT_ARMY_*`/`DOMINION_QUEUE_MARCH_*`/`DOMINION_DECLARE_WAR_*`/
`DOMINION_PROPOSE_RELATION_*`/`DOMINION_RESPOND_PROPOSAL_*` (second slice - every
remaining V1 order type). Every one of `DominionWorld`'s account-facing methods
(`recruitArmy`/`queueMarchForAccount`/`declareWarForAccount`/`proposeRelation`/
`respondToProposal`) independently re-verifies ownership server-side - an army,
province, or Nation id a client claims is never trusted, same "server is sole
authority" rule as every match game. `queueMarch`/`declareWar` themselves are now
package-private internal mutators; the account-validated `*ForAccount` wrappers
are the only entry points `ClientHandler` (or a test) should call.

`DominionSnapshot` is the client-facing DTO for state responses - V1 has no fog
of war (see `DOMINION_DESIGN.md`'s Future Depth section) so province/nation/army/
relation data deliberately includes everything, not just the requester's own. The
real domain objects (`Province`/`Nation`/`Army`/`DiplomaticRelation`) double as
the wire format directly rather than a parallel read-only view hierarchy, since
V1 has nothing to hide yet - a real DTO split becomes worth it once fog of war is
built. The one exception is `getMyProposals()`: a new `DiplomaticProposal` class
(a pending Alliance/Non-Aggression offer awaiting accept/reject) is scoped to
just the requesting account's own Nation (`DominionWorld.toSnapshot(accountId)`)
rather than broadcast to everyone - a proposal genuinely is private between the
two nations involved, the one place V1 already needs "not everyone sees
everything" before real fog of war exists. `DominionStore` persists proposals too
(a `PROPOSAL` line, same format), so a pending proposal survives a server
restart.

Every concrete class reachable from a serialized `Message` (including
`DominionSnapshot`/`DiplomaticProposal` and everything inside them, plus boxed
`Integer` for the several nullable id fields) is explicitly allow-listed in
`VertexSerializationFilter` - forgetting an entry here is a real, previously-
undocumented failure mode (`VertexSerializationFilter`'s own javadoc: a missing
class "silently fails to deserialize"), so both slices were verified with *real*
`ObjectOutputStream`/`ObjectInputStream` round trips through the actual filter
(not just a compile check), across a 29-check test (first slice) and a 51-check
test (second slice) also covering every success/failure outcome of every new
`DominionWorld` method and a `DominionStore` round trip that now includes
proposals.

**Offline-war protection and a real tick schedule (2026-09-27):** two design
facts settled the same night they came up, closing the gap between
`DOMINION_DESIGN.md` and what actually ran. `DeclareWarResult` gained
`TARGET_OFFLINE`; `declareWarForAccount` now takes a `targetIsOnline` boolean and
refuses to declare war on it (checked after `TARGET_NOT_FOUND`, since a
nonexistent Nation should never even ask whether it's "online") - nobody should
come home from being away to find themselves attacked while they couldn't
respond. `DominionWorld` itself has no view of who's connected, so it can't
compute that boolean; `ClientHandler.isDominionNationOnline()` does, the same way
`FriendManager`'s online-status features already do - look up the target
Nation's `accountId` via the new `DominionManager.getNation(id)`, resolve the
`Account` via `ServerAccountStore.findById()`, then check
`ChatManager.findByUsername()` for a live session. Separately, `DominionManager`
now actually runs the tick: a daemon thread (`startTickScheduler()`/`tickLoop()`,
the same infinite-loop-with-sleep shape `GameServer.acceptLoop()` already uses,
rather than this codebase's first `Timer`/`ScheduledExecutorService`) calls
`DominionTickEngine.resolveTick()` every 20 real minutes (one in-game day, per
the design brief) and persists the result - before this, nothing ever advanced
the clock outside a test. A tick's exception is caught and logged rather than
left to silently cancel every future tick, the same reasoning `acceptLoop`'s own
comment gives for a transient accept() failure. Adding a second kind of thread
that mutates the one whole-server `DominionWorld` (a tick, concurrently with any
in-flight `ClientHandler` request) surfaced a real, previously-latent gap: none
of `DominionManager`'s methods were synchronized, meaning two `ClientHandler`
threads (or a tick and a request) could already race on `world`'s internal maps
before this - every public method that touches `world` is now `synchronized`,
closing that race rather than deepening it. Verified with a 13-check test:
`TARGET_OFFLINE` returned for an offline target and not for an online one, that
`TARGET_NOT_FOUND`/`NO_NATION`/`CANNOT_DECLARE_ON_SELF` still take priority over
the online check exactly as before, and that a fresh `DominionManager` starts
its tick thread and serves `getNation()`/`declareWar()` correctly without
blocking on it. Both trees compile clean and stay byte-identical.

Next: the client's persistent nav tab/map UI (build order step 4) - nothing
client-visible exists yet, all of this is server request-handling only.

## ai — four independent toolkits, unified by one philosophy

Nearly every class in this package states the same rule in its javadoc: **advisory
only, never touches real game state directly** — a bot proposes a move, the game's own
Match/Game logic validates and applies it like any other input.

- **Bot-move framework** — `BotStrategy.java` (one-method interface: `chooseMove(state)`,
  must not mutate state) and `AiKernel.java`, the single shared entry point every bot
  goes through. `register(gameId, primary, fallback)` + `chooseMove(gameId, state)` for
  strategies safe to share as one long-lived instance (e.g. Tic-Tac-Toe Practice).
  `applySafely(primary, fallback, state)` is the lower-level primitive for bots needing
  per-match private memory (e.g. Battleship's hunt/target memory — a fresh strategy
  instance per match). Either way: try primary, fall back silently on any
  `RuntimeException` so a buggy "smart" strategy can never stall a live match; if both
  fail, throws `AiDecisionException`. Used by Tic-Tac-Toe Practice, Battleship, Rock
  Paper Scissors, Maze Chase.
- **`ai/search`** — the shared search engine every game plugs into for a real
  algorithmic AI opponent, without writing its own search or bot-plumbing code.
  `GameModel<S, M>` is the *only* thing a game implements: `legalMoves`, `applyMove`,
  `isTerminal`, `nextPlayer`, `evaluate`, `winner` — pure rules, no AI logic. A forced
  pass (e.g. Reversi) is just a synthetic move in `legalMoves`, so passing is a
  per-game rule detail the engine never needs to know about. `Minimax.java` is the one
  alpha-beta search algorithm, shared by every game rather than reimplemented per game.
  `GenericBotStrategy`/`RandomMoveStrategy` are the one primary/fallback `BotStrategy`
  pair every game registers with `AiKernel`, parameterized by that game's own
  `GameModel` — no `<Name>BotStrategy` class per game. `PracticeMatch<S, M>` is the one
  offline-match turn/state wrapper every practice mode uses (apply move → check
  terminal → `nextPlayer`, plus `suggestMove()` for a Hint button that reuses the exact
  same registered strategy as the bot opponent) — no `<Name>PracticeMatch` class per
  game either. Adding AI to a new game costs one small `GameModel` adapter (e.g.
  `ConnectFourGameModel.java`, `ReversiGameModel.java` — each under 200 lines) plus a
  few lines wiring a "Practice" `GameModeCard` into that game's `Window`; everything
  else is shared infrastructure, written once. Existing Tic-Tac-Toe/Battleship/Rock
  Paper Scissors bots predate this and are left on their own bespoke code rather than
  retrofitted, since they already work.
- **`ai/grid`** — generic pathfinding, deliberately independent of any one game's own
  direction type (an adapter class translates). `GridDirection.java` (UP/DOWN/LEFT/
  RIGHT), `GridPathfinder.java` (4-directional BFS over a caller-supplied obstacle
  callback: `firstStepTowards(...)` for shortest-path chasing, `distanceField(...)` for
  a full distance map used by "flee to the farthest reachable cell"). Recomputes from
  scratch each call — fine at Vertex's grid sizes (Maze Chase is 19×15). Used by
  `MazeChaseChaserBotStrategy`/`MazeChaseChaserState`, replacing an old one-step
  Manhattan-distance heuristic that could walk chasers into walls that looked closer
  in a straight line but weren't reachable that way.
- **`ai/steering`** — `Steering.java`, pure closed-form vector math (`seek()`/`flee()`).
  No `AiKernel` registration — nothing here can throw, so there's no fallback to wire
  up. Used by `ZombieSurvivalGame` for each zombie's per-frame movement toward the
  player.
- **`ai/knowledge`** — the live trivia lookup system, used exclusively by Trivia Blitz.
  `FactSource.java` (interface: `lookup(subject)`, returns `null` on *any* failure —
  unknown subject, no network, timeout, malformed response — collapsed uniformly since
  the caller's fallback is the same regardless of cause). `CachingFactLookup.java`
  (cache-first: check `FactCache` → on miss call the `FactSource` → retry once on
  failure → persist a hit; `keyPrefix` namespaces categories sharing one cache format).
  `FactCache.java` (flat pipe-delimited on-disk store per category, loaded once,
  appended immediately per new entry). `WikidataSparqlClient.java` (generic client for
  Wikidata's public SPARQL endpoint; runs arbitrary SELECT text, extracts the first
  result row via a small regex rather than a full JSON library).
  `WikidataFactSource.java` (a `FactSource` wrapping one SPARQL query template with a
  `{subject}` placeholder + result-variable name — one reusable class parameterized per
  category, escaped against SPARQL string-literal injection).
  `RestCountriesCapitalSource.java` (a `FactSource` hitting restcountries.com for
  country→capital, hand-rolled regex JSON extraction). `GameServer` wires one
  `WikidataSparqlClient` + 5 `CachingFactLookup`s (capital via RestCountries;
  company-founding-year/inventor/historical-event-year/city→country via Wikidata SPARQL
  templates) into a `TriviaLiveLookups`, threaded into `TriviaMatchManager`.
  **Status as of this doc**: the real HTTP/SPARQL calls in `RestCountriesCapitalSource`
  and `WikidataSparqlClient` have not yet been exercised against the live internet from
  any environment available so far (both the original dev sandbox and this session's
  own environment restrict outbound HTTPS to an allowlist that excludes
  restcountries.com/query.wikidata.org) — only the surrounding cache/retry/fallback
  orchestration has been verified, against fake `FactSource`s. Needs real-network
  verification (e.g. via BlueJ) before being trusted in a live match.

## engine — shared sprite/animation/game-loop/collision toolkit

Client-only (like `ui`/`theme`/`pages` — not mirrored into `VertexServer`, confirmed via
`/tmp/keep_files.txt`), and unused by any shipped game so far: built minimal-first,
same philosophy as `ai/search` — a small number of focused, independently useful
classes rather than one big framework, aimed at making a *simple* new game buildable in
a few hundred lines instead of starting every game from scratch. Every existing game in
this codebase already hand-rolls its own version of this exact plumbing (a private
`javax.swing.Timer` field, its own `ballX`/`ballVx`-style physics, its own AABB overlap
checks) — `engine` doesn't retrofit those working games, it's for whichever game reaches
for it next.

- **`GameLoop.java`** — a named wrapper around the same `javax.swing.Timer`-driven tick
  loop every offline game already hand-rolls (`BrickBreakerWindow`, `FightArenaPanel`,
  `TetrisWindow`, ...) — same mechanism (fires on the EDT, so game state/physics/repaint
  all stay safely on the UI thread with zero extra synchronization), just one shared
  name/API. `Ticker.tick(dtSeconds)` hands real elapsed time for frame-rate-independent
  motion; a game that prefers the existing codebase convention of moving a fixed amount
  every tick (`BrickBreakerGame.ballX += ballVx`) can simply ignore `dt` — both styles
  work with the same loop. Deliberately not a second thread or a fixed-timestep-with-
  catch-up loop — this codebase has no game needing sub-frame accuracy, and a second
  thread touching Swing components would break the single-threaded rule Swing needs
  everywhere else in Vertex.
- **`Vector2.java`** — immutable 2D vector (`add`/`subtract`/`scale`/`dot`/`length`/
  `normalize`/`rotate`/`reflect`/`angle`). Most existing games move objects with plain
  `double x/y/vx/vy` fields instead (see `BrickBreakerGame`, `AirHockeyWindow`) — that's
  still fine, and `GameObject` uses that same convention for its own position/velocity.
  `Vector2` is for where plain doubles get awkward: `pseudo3d`'s raycasting needs real
  vector rotation for the camera direction/plane, and `Collision.bounceOffSide` returns
  one for angled bounce responses.
- **`GameObject.java`** — optional base class for a moving, collidable thing (a ball, a
  paddle, an enemy). Plain `x`/`y`/`vx`/`vy`/`width`/`height`/`alive` fields matching
  every existing game's own convention; `update(dt)` integrates velocity into position by
  default (override for gravity/AI-steering/scripted motion), `bounds()` returns a
  `Rectangle2D.Double` for `Collision`'s checks. Entirely optional — a game can keep its
  own plain fields and call `Collision`'s static methods directly instead.
- **`Collision.java`** — the same handful of overlap tests and bounce responses every
  physics-y game already hand-rolls per-game (`BrickBreakerGame.checkPaddleCollision`/
  `checkBrickCollision`, `AirHockeyMatch`'s puck-vs-paddle), written once. Static utility
  like `Minimax`/`GridPathfinder` — `aabbOverlap`, `circleOverlap`, `circleRectOverlap`
  (clamp-the-circle's-center approach, more accurate near corners than approximating a
  circle as its own bounding box the way `BrickBreakerGame` does today), `overlapSide`
  (which edge of a rect a circle is hitting from — the exact question
  `checkBrickCollision` answers by hand), and `bounceOffSide` (flips `vx`/`vy` off that
  side, returned as a `Vector2`).
- **`Sprite.java`/`SpriteSheet.java`/`Animation.java`** — no game in this codebase
  currently draws image-based sprites (every board/piece is hand-painted with
  `Graphics2D` `fillRect`/`fillOval`/etc. — see `ReversiWindow`, `BrickBreakerWindow`);
  these exist for the games that will want actual bitmap art without each one
  reinventing "load an image, slice a sheet into frames, cycle them over time." `Sprite`
  wraps one `BufferedImage` frame (also useful for a game that renders complex vector
  art once into an offscreen buffer and blits it every frame instead of repainting the
  same shapes every tick, the way `GameLogo` caches its rendered icon). `SpriteSheet`
  slices a grid-laid-out image into frames, loading from the classpath
  (`Class.getResourceAsStream`, the same portable-jar-friendly approach
  `GameLogo.loadSource()` uses for `vertex_logo.png`) or wrapping an already-loaded
  `BufferedImage` for procedurally-drawn sheets. `Animation` advances through a frame
  sequence on the same `dt` `GameLoop` hands its `Ticker`; loops by default, or
  `loop=false` for a one-shot effect (an explosion) with `isFinished()` to know when to
  remove it.
- **`engine/pseudo3d`** — the planned pseudo-3D capability: isometric/raycasting tricks
  in plain Java2D, deliberately not real 3D (JOGL/LWJGL/a native rendering pipeline was
  ruled out as breaking the "one portable jar, no install" model everything else
  follows). `RayCaster.java` — classic Wolfenstein-3D-style raycasting via the standard
  DDA grid-traversal algorithm: one ray per screen column against a 2D `int[][]` map,
  rendered as vertical wall slices with a flat per-cell color (darkened on one wall
  orientation for cheap directional shading) rather than real textures. The caller owns
  the camera (`posX`/`posY`, a unit `dirX`/`dirY`, and a `planeX`/`planeY` perpendicular
  to `dir` whose length sets the field of view) — rotating the view is just rotating
  `dir` and `plane` together by the same angle via `Vector2.rotate`. `IsometricProjection
  .java` — converts tile `(col,row)` to on-screen pixel position for the classic 2:1
  diamond-tile look and back (`screenToTile`, for mouse picking), plus `drawOrder(col,
  row)`, the standard isometric painter's-algorithm sort key (draw increasing
  `col+row` first so nearer tiles correctly overlap farther ones without a real depth
  buffer).
- **Verification**: a scratchpad smoke test (`EngineSmokeTest.java`, not part of the
  shipped codebase) exercises every class directly — vector math identities, collision
  overlap/bounce correctness, a procedurally-built sprite sheet sliced and animated
  through a full loop cycle plus a one-shot animation finishing correctly, a raycaster
  render against a small test map confirmed to actually paint wall-colored pixels, an
  isometric tile↔screen round-trip, and a real `GameLoop` started/stopped/confirmed to
  fire real ticks — 39/39 checks passed. A separate visual demo
  (`RayCasterVisualDemo.java`) rendered both a raycaster corridor view and an isometric
  tile grid to PNG and was eyeballed to confirm they actually look like the intended
  pseudo-3D style, not just "some pixel matched."
- **First real adopter: `HillClimbGame`/`HillClimbWindow`** (see `ROADMAP.md`'s "Done"
  section) uses `Vector2` (resolving gravity along the terrain's slope angle via
  `.rotate`) and `GameLoop` for its tick loop — proof the design generalizes to an
  actual game, the same validation step `ai/search` went through with
  `ConnectFourGameModel` before being trusted more broadly.
- **Not built yet** (still just `ROADMAP.md` ideas, not started): a particle system for
  effects (explosions, bursts) — skipped for this minimal-first pass since
  `ConfettiOverlay` already covers the one existing burst-effect need and a generic
  particle system isn't yet justified by a second use case. `Sprite`/`SpriteSheet`/
  `Animation` and `engine/pseudo3d` still have no adopter game yet - `HillClimbGame`
  only needed `Vector2`/`GameLoop` so far.

## economy — coins, ratings, achievements, cosmetics

Hook-in pattern: `EconomyManager`, `GameHistoryManager`, `LeaderboardManager` are
constructor-injected into every `<Name>MatchManager`; each `<Name>Match` calls back into
them at match end. `AchievementManager` is wired as a secondary dependency *into* those
three (not into match classes directly), so achievement checks piggyback on calls that
already happen rather than needing their own call site in every match.

- **`EconomyManager.java`** — the coin-award entry point:
  `awardWin(ClientHandler, gameId)` looks up the reward via `EconomyConfig`, credits the
  account, logs via `TransactionManager`, records challenge progress. Placement games
  (Racing, Space Battle) don't have a single `awardWin`-shaped winner, so they go through
  `awardPlacement(player, gameId, place, activityLabel)` (only 1st place counts as a
  win) instead — one method now, not two byte-identical copies (`awardRacingPlacement`/
  `awardSpaceBattlePlacement` used to duplicate each other exactly; merged 2026-09-26,
  see `EconomyKernel.java`). Tie-splitting games (Square Wars, Trivia Blitz) go through
  `awardMatchWinCoins` instead, since their per-winner split is genuinely computed
  per-game. All three funnel into the same shared `recordOnlineWin()` tail as `awardWin`,
  so every online game's win reaches `ChallengeManager` the same way. Also handles shop
  purchases (always validated server-side — never trust a client-reported balance) and
  daily login rewards.
- **`EconomyKernel.java`** — the one class a new game's server-side code should actually
  read to answer "how do I pay this player?", added 2026-09-26 after finding several of
  `EconomyManager`'s per-game methods (`awardSnakeScore`, `awardPuzzleQuestCompletion`,
  `awardMinesweeperCompletion`) were near- or byte-identical copies of its own already-
  generic `awardCoins`/`awardPracticeScore` — genuine duplication a new game's author
  had no way to notice without reading all of them first. A static facade (same
  "static utility over an existing manager" shape as `PerformanceMode`/`EconomyConfig`,
  not an injected instance) exposing exactly four reward shapes: `awardCompletion`
  (score-scaled offline game — add the formula to `EconomyConfig.getPracticeReward`
  and nothing else needs an edit), `awardFlatCompletion` (solved-or-not games with no
  meaningful score, like Sudoku/Minesweeper/Puzzle Quest), `awardMatchWin` (a standard
  single-winner online match), and `awardPlacement` (race/FFA placement, top 3 only).
  A fifth, rarer shape (several players tie and split a pool) has no wrapper yet — call
  `EconomyManager.awardMatchWinCoins` directly, since only two games need it and the
  split math is genuinely per-game. Existing `EconomyManager.awardWin(...)` call sites
  across the ~30 match classes were deliberately left as direct calls rather than mass-
  migrated to `EconomyKernel.awardMatchWin` — that method is already a clean, non-
  duplicated single entry point, so routing it through the kernel too would be a large,
  purely cosmetic rewrite with no bug to fix; new code goes through `EconomyKernel`,
  existing direct `EconomyManager` calls remain equally correct underneath.
- **`EconomyConfig.java`** — pure static config: per-game win-coin table, practice-mode
  score→coin formulas (Snake folded in here 2026-09-26 — see `EconomyKernel.java`, it
  used to be its own `getSnakeReward()` special case), one shared placement-reward table
  for Racing/Space Battle, the 7-day daily-login streak table, challenge definitions,
  shop catalog. The one place all economy numbers live. **`getWinReward()`'s table was
  missing a "trivia-blitz" entry until 2026-09-29** — `TriviaMatch` had always looked
  it up to compute its winner pot, so the game had been silently paying zero coins
  since it shipped; found by grepping every `Match` class for its actual
  `EconomyManager.awardWin(...)`/`EconomyConfig.getWinReward(...)` call site to get
  the real list of 24 games this table needs to cover (see `ROADMAP.md`'s "Done"
  section), not by guessing from the games list. `VertexServerTests/economy/
  EconomyConfigTest.java` now locks that full set in.
- **`LeaderboardManager.java`** — per-game ELO (K=32, start 1200) for symmetric 1v1
  games; a pairwise-ELO approximation for Fight Arena's N-player matches; separate
  best-score tracking for score-based games. Among Us is deliberately excluded from ELO
  (asymmetric roles don't map to a symmetric skill rating).
- **`AchievementManager.java`** — permanent, silent unlocks computed from three
  already-tracked metrics: win counts, total plays, coin balance. Data-driven since
  2026-09-26 (the "achievements kernel" requested alongside `EconomyKernel`): each
  `Definition` carries its own trigger - a threshold on a named metric
  (`"wins:chess" >= 5`) or a one-shot event key (`"racing:place1"`) - and the two
  generic entry points `checkThreshold(accountId, metric, currentValue)`/
  `checkEvent(accountId, eventKey)` unlock whatever `Definition`s match, so a new
  achievement is one `Definition` line, never a new method or another branch in a
  hand-maintained if-chain. The original 6 named check methods
  (`checkWinAchievements`, `checkRacingPlacement`, ...) stay as thin wrappers over
  those two - unlike `EconomyKernel`, no separate facade class was needed here, since
  this public API was already clean rather than duplicated; existing call sites
  needed zero changes.
- **`GameHistoryManager.java`** — records every play event, feeds "Recently
  Played"/"Trending" and achievement play-count checks.
- **`TransactionManager.java`** — flat coin-transaction audit log.
- **`ChallengeManager.java`** + `ChallengeProgressInfo.java` *(shared)* — daily/weekly/
  permanent challenge progress; definitions come from `EconomyConfig`.
- Cosmetics/avatars: `AvatarStore` (server, one PNG per account), `AvatarCache` (client
  fetch cache), `AvatarEditorDialog` (in-app paint tool), `AvatarFrameRegistry`/
  `PlayerColorRegistry` (client caches of shop cosmetics), `ShopItemDefinition` (server
  entry) / `ShopItemInfo.java` *(shared)*.
- Local-only convenience stores (`java.util.prefs.Preferences`, no server round trip):
  `PinnedGamesStore`, `PinnedFriendsStore`, `LastGameModeStore`, `SavedServersStore`,
  `GuestPlayTracker` (queues logged-out Snake plays, flushed on next login),
  `FpsCounterSetting`, `NotificationSoundSetting`, `PerformanceMode` (checked in
  `theme/UITheme.applyAntialiasing()`, `GlitchEffectOverlay`, and real-time games' tick
  intervals).
- **`FpsTracker.java`** — per-panel frame-rate counter. **`AchievementToast.java`** —
  non-modal achievement-unlock notification.

## account vs social — identity/auth vs. relationships/presence

`account` never imports `social`; `social` (`FriendManager`, `ModerationManager`)
imports `account` to resolve who a username belongs to. `GameServer` composes both
independently and hands both into `ClientHandler`.

**account** = who you are and how you prove it:
`Account.java` *(shared)* (permanent `accountId` that never changes even across
username changes, plus role/coins/cosmetics), `Role.java` (PLAYER/MODERATOR/ADMIN, on
Account not username), `PasswordHasher.java` (SHA-256 + per-account salt),
`ServerAccountStore.java` (server-side account CRUD/login, lockout after failed
attempts), `Session.java` (client-side "who's logged in"), `PermissionManager.java`
(**client-side-only** convenience checks — its javadoc stresses this is not security;
`ClientHandler` re-checks role server-side for every privileged request), plus the
auth/profile UI (`AuthWindow`, `LoginPanel`, `CreateAccountPanel`, `ChangePassword-
Dialog`, `ChangeUsernameDialog`, `ProfilePanel`/`PlayerProfileDialog` — no coin balance
shown on other players' profiles).

**social** = relationships and live communication once you're in:
`FriendManager.java` (requests/list, presence broadcast on login/logout,
`getMutualFriendUsernames(a, b)` powering the profile dialog's "Mutual Friends"
section),
`ChatManager.java` (tracks connected clients by username, DM routing, broadcast),
`GroupChatManager.java`, `PartyManager.java`/`Party.java` (play-together groups,
feeding `TeamTournamentManager`), `ModerationManager.java` (mute/kick/ban, keyed by
username not accountId to catch renamers, plus a report queue), and dialogs
(`FriendPickerDialog`, `NewDirectMessageDialog`, `NewGroupDialog`, `GameInviteDialog`,
`ReportPlayerDialog`, `ScoreShareDialog`, `PartyDialog`, `ModChatDialog` - a staff-only
channel reachable from `ModeratorPanel`, gated server-side by
`ClientHandler.isModeratorOrAdmin()` for both sending and who a `MOD_CHAT_MESSAGE`
gets broadcast to; no message history yet, only what's sent while it's open).

## mechanics — shared cross-game systems

Added 2026-09-30, shared (byte-identical) between both trees. The home for behavior that is
the same for many games and shouldn't be re-implemented in each - the first residents are the
reconnect pieces; the next candidates are listed in `ROADMAP.md`.

- **`ReconnectPolicy.java`** - the rules, in one place: the grace window (`GRACE_SECONDS = 30`,
  `GRACE_MS`), the exception table (`EXCEPTIONS`, game id -> reason; the eight group games -
  Racing, Space Battle, Square Wars, Zombie Survival, Among Us, Telephone, Trivia Blitz,
  Fight Arena - where "forfeit" doesn't apply), `isEnabled(gameId)`, and
  `canReconnect(handler, gameId)`: true only for a logged-in player who actually *dropped*
  (`!handler.isLeavingVoluntarily()`) in a game that isn't an exception. Moving a game in or out
  is a one-line change here. `waitingNotice()` is the text the waiting player sees.
- **`ReconnectRegistry.java`** (moved here from `games/`) - the grace-period timer, keyed by
  accountId, plus `ReconnectableMatch` and `ReconnectResult`. There is **one server-wide
  instance, `ReconnectRegistry.shared()`** (a player can only be waiting in one match), so
  `ClientHandler.tryReconnectAllGames()` is a single call and a new game needs no plumbing at
  login. The managers' `getReconnectRegistry()` still exist and return the shared one. Lock
  order rule (unchanged): register with it only *after* releasing the match's own lock.
- **`PairReconnect.java`** - the whole two-player disconnect / timeout / resume state machine,
  written once. A match creates one (`new PairReconnect(this, GAME_ID, host[, resumeDelayMs])`),
  its `handleDisconnect` becomes `reconnect.handleDisconnect(who)`, and each action starts with
  `if (reconnect.isPaused()) return;` (real-time games use `isHeld()`, which also covers the
  resume delay). The game supplies only a small `Host`: `matchId`, `isOver`, `player`/`setPlayer`
  (the two slots are non-final so a returning player's new handler can take theirs),
  `stateString` (for the waiting notice), `forfeit(remaining)` (what "the match ends because of a
  drop" looks like - result message, coins), `resume(slot, opponent)` (update the waiting player,
  describe the match for the returning one) and `attach`. `refreshNotice()` re-sends the notice
  for a match whose state changes during the pause (Memory Match's mismatch timer).
  `resumeDelayMs` is for the real-time games: 3s of extra freeze after a return, so the
  returning player's rebuilt window is up before the puck/snakes/pieces move.
- **Adopters of `PairReconnect`:** Dice Duel, Signal Grid, Fusion Grid, Memory Match, Typing Duel,
  Card Rush (turn-based / simple), Air Hockey, Snake Arena, Tetris Duel (real-time). **Chess**
  (`ChessMatch`, via the helper's `onPaused` hook for its draw offer). The eight earlier adopters (Tic-Tac-Toe, Connect Four, Checkers, Reversi, Dots and Boxes,
  Word Duel, Battleship, Rock Paper Scissors) also hand-written, now reading their window from
  `ReconnectPolicy`. Game-specific packing into `ReconnectResult` (a login response has no field
  for these): Memory Match `turn|a:b`; Typing Duel sentence in `boardState` and
  `winsA:winsB|progA:progB` in `turnSymbol`; Tetris Duel own grid in `boardState` and
  `score|opponentGrid` in `turnSymbol`; Word Duel / Battleship / RPS as described in the
  `games` section. `pages/MatchResume` unpacks all of them.
- **Getting a dropped player back in** (the part that makes any of this matter): the client
  (`net/NetworkManager`) now notices a drop as soon as its listener thread ends on a still-live
  connection - not only when a later send fails - re-opens the socket, and runs a hook;
  `pages/SessionRestorer` (installed by `MainMenu`) uses it to log in again with the credentials
  cached in memory for this run, and `pages/MatchResume` (extracted from `AuthWindow`, which now
  calls it too) rebuilds the game window from the login response via `MainMenu.showResumedGame`,
  which first unhooks any stale window *without* sending a leave. Server side, `ClientHandler.
  handleLogin` takes over the same account's older session's reconnect-eligible matches
  (`releaseMatchesForTakeover()`) before trying to resume - a dead connection the server hasn't
  noticed (Wi-Fi drops send no close) still has its match bound to it. Group games are not
  touched by a takeover.
- **Dead-connection detection (2026-09-30):** `PING_REQUEST`/`PONG` every 8s from
  `NetworkManager`'s heartbeat thread; `ClientHandler` sets a 25s `SO_TIMEOUT` and treats a
  timeout as a disconnect (so the grace period starts for the opponent's benefit), the client a
  30s one on its own socket. A client that never pings is dropped after 25s idle.
- **Countdown and chat:** `ui/ReconnectCountdown` (client-only) ticks the notice's "up to 30s"
  down on every game window and stops by itself when the label is rewritten.
  `chat/MatchChatRoom` keeps a static registry of open rooms; login calls `rejoin(handler)` for a
  resumed match, and `MatchChatRoom.sendStateTo` answers the client's `MATCH_CHAT_SYNC_REQUEST`
  (sent by `MatchResume` after the window is up) so the dock comes back.
- **Chess** now uses `PairReconnect` too (its `onPaused` hook clears a pending draw offer). The
  eight earliest adopters keep their hand-written state machine deliberately.

## chat — in-match chat rooms, with per-game restrictions

(Also home of `CalcParser`, the local evaluator behind `/calc` - used by `pages/ChatPanel` and `MatchChatDock`, never sent to the server; shared between the trees only so `CalcParserTest` can reach it.)

Added 2026-09-29. A small chat room shared by everyone in one live match - separate
from the DMs/group chats above - shown as a dock beside the game. **Server-authoritative**:
a client asks to send (`MATCH_CHAT_SEND_REQUEST`), the room decides.

- **`ChatRestriction.java`** *(shared)* — `OPEN` or `LOCKED`.
- **`MatchChatRoom.java`** *(shared)* — members + current restriction. `post(sender, text)`
  relays to every member only if the room is open, the sender is a member and the text
  isn't empty (trimmed to the same 500-char cap as DMs); `lock()`/`unlock()`/`leave()`/
  `close()`/`closeAfter(ms)`. Every state change is pushed as `MATCH_CHAT_STATE`
  ("OPEN"/"LOCKED"/"CLOSED"), which is also how a client learns a room exists. Attaches
  itself to each member's `ClientHandler` (`getMatchChatRoom()`), which is how
  `handleMatchChat` finds it; mute and flood limits are applied there first, exactly as
  for DMs.
- **`GameChatPolicies.java`** *(shared)* — **the "package to restrict easily"**: one table of
  which games have chat and how it starts. Give a game chat = add one line; restrict it =
  make the line `LOCKED` and call `room.unlock()` from the game; remove chat = delete the
  line. **Opt-in on purpose** (a game not listed has none), so a game where free chat
  would leak hidden information never gets it by accident. `openRoom(matchId, gameId,
  members)` creates and announces a room, or returns null for an unlisted game.
- **`MatchChatDock.java`** *(client-only)* — the Swing dock. `MainMenu` creates it on the
  first `MATCH_CHAT_STATE` for a match, feeds it `MATCH_CHAT_MESSAGE`s, ignores other
  match ids, and drops it whenever the game-host slot is cleared. It only mirrors the
  server (a locked room disables its input) - enforcement is `MatchChatRoom.post`.
  Collapsible to a thin strip with an unread count.

**Who has it today:** the 14 games on `MatchmakingKernel` (Checkers, Connect Four, Reversi,
Dots and Boxes, Word Duel, Dice Duel, Typing Duel, Air Hockey, Memory Match, Signal Grid,
Fusion Grid, Card Rush, Snake Arena, Tetris Duel - `MatchmakingKernel` opens a room when
it pairs two players and keeps it open 60s after `endMatch` for a "gg"), and **Telephone**
(starts `LOCKED` because free chat would let players say the answer out loud;
`TelephoneMatch` unlocks it when the reveal starts and keeps it 10 minutes). Not yet:
Tic-Tac-Toe, Chess, Battleship, Rock Paper Scissors, and every group/real-time game -
each needs its `Match` to call `GameChatPolicies.openRoom(...)`/`room.close()` itself.

## forum — Forums (boards, threads, replies)

Added 2026-09-29, shown as the **Forums** tab in the sidebar (`Pages.FORUMS`). A board per
game (every id in `GameRegistry`) plus **General**; a thread is a title + opening post, with
flat (non-nested) replies. Reading works for anyone connected; posting needs a login.

- **`ForumCodec.java`** *(shared)* — one-line, tab-separated records with every field escaped
  (backslash/tab/newline/CR), used for both the on-disk file and the lists sent to clients.
  Because no field can contain a raw tab or line break, a post can't split into a second
  record or forge extra fields on reload - the same bug class already fixed once in
  `GameSuggestionStore`. `threadSummaryLine`/`postLine` are the wire forms.
- **`ForumThread.java`/`ForumPost.java`** *(shared)* — the data. Everything the store hands
  out is a copy.
- **`ForumStore.java`** *(shared)* — threads + posts in memory, persisted to
  `gamehub_forums.txt` (git-ignored runtime data via the existing `gamehub_*.txt` rule),
  rewritten via a temp file + move on every change. Strictly increasing timestamps so
  "newest activity first" never ties. Load skips unparseable records, orphan posts and
  threads with no opening post instead of failing.
- **`ForumService.java`** *(shared)* — the rules: valid boards only (fixed at startup); title
  1-100 chars forced onto one line; post 1-2000 chars keeping line breaks but dropping
  control characters; too-long text is **rejected with a message, never silently cut**; a
  locked thread takes no replies; 500 posts per thread; a board lists at most 100 threads.
  Content only - who is asking is `ClientHandler`'s job.
- **`ClientHandler`** — `handleForum*` (`FORUM_*_REQUEST` -> `FORUM_RESPONSE`): reads are open;
  posting needs login, not muted, and the `forum` flood bucket (3 posts / 30s, shared by new
  threads and replies); **delete and lock are moderator/admin only, decided from the
  account's stored role on the server**, and each is written to `AdminLog`.
- **`ForumsPanel.java`** *(client-only, `pages/`)* — board list, thread list with composer, thread
  view with reply box; re-fetches whenever the page is shown. It only hides buttons that
  would be refused; the server enforces everything. `NetworkManager.RESPONSE_TYPES` must list
  `FORUM_RESPONSE` (without it every request times out as an unsolicited push).

Not in the first version: votes, editing, images, notifications, nested replies.

## admin — moderation tooling, gated by Role

- **`AdminLog.java`** — append-only audit trail. A record of what happened, not an
  access-control mechanism — writes only happen after `ClientHandler` already verified
  the actor's role.
- **`FeedbackManager.java`**/`FeedbackDialog`/`FeedbackListDialog` — bug reports/
  suggestions about Vertex itself (distinct from `ModerationManager`'s player-conduct
  reports); admins see everything, everyone else sees only their own. Structured since
  2026-09-26: a required title, a description, and (bug reports only, optional) steps
  to reproduce - not just one free-text box. The saved `gamehub_feedback.txt` stays
  genuinely human-readable (a `Title: ` line and a `Steps to reproduce:` section, both
  optional per-entry) with a backward-compatible parser fallback for entries written
  before these fields existed.
- **`GameSuggestionStore.java`** — public community wishlist of game ideas (text
  pitches only — no uploaded/executed code).

**A real entry-forgery bug found and fixed across all three stores (security
pass, 2026-09-27):** `GameSuggestionStore` and `AdminLog` both persist one entry
per physical line (`load()` treats every `readLine()` result as its own entry);
neither stripped embedded `\n`/`\r` from the free text a caller supplies before
formatting and writing that line, so a suggestion (or, lower severity since only
admins/mods can reach it, an admin's typed action reason) containing a line
break could forge an entirely separate, indistinguishable-looking entry under
any fake `"[date] username: ..."` prefix of the attacker's choosing - including
impersonating another real player - the moment the file next reloads (a server
restart). `GameSuggestionStore` is the more serious of the two: it's the public,
everyone-sees-it wishlist, reachable by any logged-in player, not just staff.
Fixed by stripping/replacing embedded newlines in the free-text field before it
ever reaches the one-line format. `FeedbackManager`'s block-based format (a
`DELIMITER` line, not one-line-per-entry) already tolerates embedded newlines
within an entry by design, but had the same bug in miniature: a submission
whose title/text/steps happened to contain a line that was *exactly* the
64-dash `DELIMITER` string would falsely end that entry early on the next
reload, misparsing everything after it as a second, differently-attributed
entry - narrower (needs an exact 64-dash line, not just any newline) but
fully deterministic once triggered. Fixed by escaping any such line in a
submission before it's stored, changing nothing about how ordinary embedded
newlines behave. Verified with an 11-check test proving each store still
produces exactly one entry (not two) both immediately after submission and
after a real save-then-reload round trip through a fresh instance, that no
forged entry is retrievable at any point, and (for `FeedbackManager`) that
ordinary multi-line free text still round-trips intact - a regression check
that the fix doesn't change legitimate behavior. Mirrored byte-identical
across both trees (all three files are in the sync-rule's `admin` package);
both compile clean.

Gating happens server-side in `ClientHandler` (`isAdmin()`/`isModeratorOrAdmin()`
checked at the top of every admin/mod handler); `handleAdminSetRole` can promote
PLAYER↔MODERATOR but refuses to grant/revoke ADMIN or touch another admin's role at all
(bootstrap-admin-only by design). Client-side, `pages/AdminPanel.java` and
`pages/ModeratorPanel.java` are gated the same way through `PermissionManager`, but
every such panel's javadoc repeats that this is UI convenience only.

## pages / ui / theme — the Swing client shell

**Sidebar (restructured 2026-09-30):** `pages/Sidebar` keeps the logo row, quest mini-list and status row pinned and puts the
navigation in a `JScrollPane`; entries are grouped (`beginGroup` -> an inner `Group` with a `GroupHeader` and a body panel):
Home / Play / Progress / Social / Shop & Community / Account. Collapsed state is stored in `Preferences` (`collapsed.<key>`),
selecting a page inside a collapsed group re-opens it, and a collapsed group's header echoes a button's badge
(`SidebarButton.isShowingBadge`). `ui/NavIcons` gained Home, Forums, Suggest-a-Game, Dominion and Changelog glyphs.

**Caption Chaos (2026-10-01):** `games/CaptionChaosMatchManager` (3-8 player queue, starts at 3) -> `CaptionChaosMatch` (phases WRITING -> VOTING -> RESULT x3 -> OVER, server `Timer`s that also end early when every connected player has acted; `CaptionChaosPrompts` is the prompt pool), `net/ClientHandler` hooks (`CAPTIONCHAOS_*` requests, `currentCaptionChaosMatch`, leave/disconnect), client `games/CaptionChaosWindow`.

**Obfuscated build (2026-10-01):** `build.sh --obfuscate` -> `VertexClient-release.jar` via ProGuard (`proguard/vertex-client.pro`); `proguard/WireCompatCheck.java`
verifies every shared Serializable class still matches the plain jar (name, fields, serialVersionUID). Adding a new shared package means adding it to both
the `.pro` keep rules and `WireCompatCheck.SHARED`. Shared data classes should declare `serialVersionUID` (an implicit one changes when methods are renamed).

**Match chat for the hand-written managers (2026-10-01):** `chat/MatchChatRooms` (per manager, per game id: `open(matchId, a, b)` on match start,
`close(matchId)` from `endMatch`, 60s grace) is what `MatchManager` (Tic-Tac-Toe), `ChessMatchManager`, `BattleshipMatchManager` and
`RockPaperScissorsMatchManager` use; `MatchmakingKernel` still does the same inline. `GameChatPolicies` stays the one place that says which games get chat.

**Page kit (2026-10-01):** every top-level page extends `pages/PageScaffold` (a `PageHeader` with title/subtitle/right-hand action, side
margins, a scroll body that tracks the viewport width so nothing adds a horizontal scrollbar; row helpers `fullWidth`/`split` (min 300px per
side)/`columns` over `FitRow`s whose height follows their content; `setBody` for pages that scroll themselves) and is built from
`pages/SectionCard`s (the old Home card, generalised: title, optional link, body, optional accent glow; `HomeSectionPanel` is now a thin subclass).
New shared pieces: `ui/ThinProgressBar`, `ui/InitialBadge`, `ui/WrapLayout`, `pages/PickerItem`; `ThemedButton.getPreferredSize` is never
narrower than its label. Rebuilt on it: Quests, Leaderboards, Achievements, Friends, Shop, Tournaments, Changelog, Suggest a Game, Settings,
Profile, Stats, Forums, Games, Moderation, Admin; Chat got a subtitle and wider sidebar. Dominion is untouched (deferred). The Login/Create
Account window paints one glow behind the whole window (`AuthWindow`) instead of per panel. `pages/PinnedGames` is the single pin list, backed by
`economy.PinnedGamesStore` (the Games page's Pin buttons and Home's pins are the same list).

**Home (restructured 2026-09-30):** `pages/HomePanel` = the scrolling ticker, then the six redesign sections, then Top Players
(the old Recently Played / Explore rows were removed 2026-10-01). The six are subclasses of `pages/HomeSectionPanel` (card with title + body, `setBodyContent`, `refresh()` called
on build and every 30s): `HomeWelcomeSection`, `HomeContinueSection`, `HomeQuickPlaySection`, `HomeFriendsSection`, `HomeTournamentsSection`
- all real as of 2026-10-01 (`HomeWelcomeSection` quests via `CHALLENGES_REQUEST`; `HomeFriendsSection` and `HomeTournamentsSection`
are `NetworkManager.PushListener`s that ask with `sendAsync`, the tournament list being a push-only type; `HomeQuickPlaySection.pick()` holds the
suggestion rule; `HomeContinueSection` renders `HomeGameTile`s - pinned first, from `pages/PinnedGames`, a per-computer `Preferences` list -
then recent games). `HomePanel` lays them out with `fullWidth`/`split` rows (`FitRow`: height follows content, so cards never stretch) inside a
`WidthTrackingPanel` scroll view (follows the viewport width; split cards keep a 300px minimum). `GameDetailPanel` has the "Pin to Home" button.
`.cursor/prompts/home-redesign.md` is the superseded Cursor brief.

**Stats (added 2026-09-30):** `STATS_REQUEST`/`STATS_RESPONSE` handled by `ClientHandler.handleStats` (public by name; own stats
need a login) from `GameHistoryManager.getPlayCountsByGame` and `LeaderboardManager.getStatsRowsForAccount`; new `Message` fields
`statsTotalPlays/statsPlayCounts/statsGameRows/statsAchievementCount`. Client: `pages/StatsPanel` (`Pages.STATS`, opened by
`MainMenu.showStats(username)`, Back returns to the opener), the Stats card and real numbers in `account/ProfilePanel`, a "Full stats"
button in `account/PlayerProfileDialog`. **`NetworkManager.RESPONSE_TYPES` is now guarded by `net/ResponseTypesTest`** (see the tests section).

**Changelog (added 2026-09-30, client-only):** `pages/ChangelogPanel` renders the entries of the root `CHANGELOG.md`
(one hand-written file shared with `website/app.py`); `pages/ChangelogParser` parses it (`## ` entry, `- ` bullet,
two-space continuation, `<!-- -->` ignored). The file is bundled into `VertexClient.jar` by `build.sh`/`build.bat`
and read from the classpath first, then the working directory / parent folder when run from source. `Pages.CHANGELOG`,
a sidebar entry and a `NavIcons` glyph.

**Cursors (added 2026-09-30, client-only):** `ui/CursorArtwork` draws the cursor sets in code (arrow, link, text per
set; `CursorSet` enum incl. SYSTEM and APP = follows the theme); `ui/CursorManager` (installed from `Vertex.main`)
applies the saved choice via a global AWT mouse listener that swaps Swing's default/hand/text cursors on whatever is
under the pointer and remembers originals so SYSTEM restores exactly; `ui/CursorPicker` is the Settings control.
Stored in `Preferences` under `CursorManager`'s package node (per computer).

**pages** (`MainMenu.java` is the shell): a `JFrame` with `Sidebar` (west) + `TopBar`
(north) + a `CardLayout` content area (center) holding one panel per
`pages/Pages.java` key. `MainMenu` implements `net/NavigationListener` so `Sidebar`
reports nav clicks without knowing how paging works; page switches crossfade (snapshot
outgoing page, swap underneath, fade out over ~220ms) instead of snapping. One of
those keys, `Pages.GAME_HOST`, is special: it's the single slot an embedded game
occupies (see `games/EmbeddedGamePanel.java`), filled/cleared via
`MainMenu.showGame(JComponent)`/`returnToGames()` rather than being a fixed panel
registered up front like every other page. `onNavigate(...)` (the guard-checked path
`Sidebar` clicks go through) and the private `switchToPage(...)` (the actual card
swap, used internally by `showGame`/`returnToGames` too) are deliberately separate:
`onNavigate` asks the currently-hosted game's `requestLeave()` before leaving
`GAME_HOST`, but `showGame`/`returnToGames` skip that ask since by the time either
runs, leaving has already been decided one way or another - going through
`onNavigate` there would ask a second time.
`Sidebar.java` gates its Moderation entry via `PermissionManager.isAtLeastModerator(...)`
(a separate Admin entry was removed as redundant, since Role is hierarchical).
`TopBar.java` holds page title, live online-count, notification bell, account menu.
Page panels: `HomePanel`, `GamesPanel` (Home/All-Games tabs, pin toggles), `ShopPanel`,
`LeaderboardPanel`, `AchievementsPanel`, `QuestsPanel`, `TournamentsPanel`, `ChatPanel`,
`FriendsPanel`, `GameSuggestionsPanel`, `AdminPanel`, `ModeratorPanel`, `SettingsPanel`,
`DominionPanel` (`Pages.DOMINION` - Dominion's own persistent nav destination, a
`Sidebar` entry like Friends/Chat/Shop, deliberately not `Pages.GAME_HOST`; see the
`dominion` package section above for why). Its own content, not a match-game
window, so it's the one page panel with no `VertexServer` counterpart to keep
byte-identical - the same client-only exemption every game's `<Name>Window`/
`<Name>Dialog` class already gets. Rebuilds its content wholesale from a fresh
`DominionSnapshot` on load/Refresh (same "clear and rebuild" shape
`LeaderboardPanel.renderLeaderboard()` already uses) rather than diffing - there's
no push-based live update for Dominion yet (no broadcast-on-tick mechanism, unlike
a real match), so staleness between an explicit Refresh is an accepted, honest V1
gap, not a bug.
Supporting: `GlobalSearchField`, `NotificationBell`/`NotificationCenter`,
`QuickPlayDropdown`, `OfflineHubWindow` (currently just Snake), `WindowSizeMemory`.

**ui** = generic theme-aware Swing widget toolkit, no page-specific logic:
`RoundedPanel`, `ThemedButton`/`ThemedTextField`/`ThemedPasswordField`/
`ThemedTextArea`/`ThemedScrollBarUI`/`ToggleSwitch`/`StatusDot`/`StatusPill`,
`GameHubDialog` (themed replacement for raw `JOptionPane`), `DialogUtils`,
`PlaceholderPanel` (a static helper: `show(container, text)` clears a container
and drops in one muted-text label for the "couldn't load"/"nothing here yet"
state every list-backed panel eventually needs; `mutedLabel(text)` alone for a
caller still mid-way through building a container's other children - the shared
version of what `FriendsPanel`/`LeaderboardPanel`/`ShopPanel` each hand-rolled
separately before this existed),
`ChamferShape` (angular cut-corner geometry - now used only by `GameCardArt`'s
per-game icon glyphs, deliberately out of scope for the reskin below; no
longer used by any shared shell component), `NavIcons`,
`GameModeCard`/`HeroBanner`/`PageHeader`/`MarqueeBanner`/`HoverGlowAnimator`/
`SidebarButton`/`SplashScreen`/`WinLineOverlay`, and Party Mode's two purely
cosmetic effects: `CursorTrailOverlay` (attached once, whole-app, from
`MainMenu` - a global `AWTEventListener` on `Toolkit`, the correct way to
observe mouse motion regardless of which component actually received it,
rather than a listener on the frame itself which would only ever see events
landing on the frame and never on any child component) and `ConfettiOverlay`
(call `burst(anchor)` from any real "you won" moment - `MemoryMatchWindow` is
the first hookup, other games can adopt the same one-liner later). Both use
the same click-through trick (`contains(x, y)` always returns `false` on the
painted panel - the standard lightweight way to make a Swing overlay
non-interactive without manual mouse-event redispatching) and both are
governed by `economy.PartyMode` (off by default, a `Preferences`-backed
toggle in Settings, same pattern as `PerformanceMode`).
`ScreenBreakOverlay` (added 2026-09-29, per Bipin's explicit "no exceptions"
request) is the opposite kind of overlay - attached once, whole-app, from
`MainMenu`, but deliberately input-*blocking*, not click-through: it sets
itself as the frame's glass pane and attaches real (if empty) mouse/key
listeners specifically so events don't fall through. A single 1-second
`javax.swing.Timer` counts active screen time (paused while the window is
unfocused or minimized via `MainMenu.isActive()`/`ICONIFIED`) and, every 20
real minutes of it, shows a full-screen 30-second countdown that can't be
dismissed early - no close button, no Escape binding, no Settings toggle. If
`MainMenu.isGameInProgress()` (a new one-line method reading the existing
`currentPageKey` field - true while `Pages.GAME_HOST` is showing) is true
when the threshold hits, the break is deferred and rechecked once a second
until the game ends, rather than interrupting an active match. Client-only
by design - a wellness/UI feature, not shared game/account/economy logic, so
it's not part of the byte-identical sync list.

**The "Aurora Glass" reskin** moves the shared app shell - not any individual
game's own board/HUD screen - off the earlier flat, chamfered "Opera GX" look
toward softer rounded shapes with an ambient accent glow. Concept direction
was worked out as Gemini image-gen prompts, then as a real CSS/HTML design
canvas (a Claude Artifact, not checked into this repo) before being
translated into Swing code, so the Swing changes could be checked against an
actual rendered reference rather than eyeballed from a description.
Reskinned: `UITheme` (`RADIUS_PANEL` 14->18, `RADIUS_BUTTON` 10->14),
`ThemedButton` (primary buttons are now a rounded outline-in-accent-color over
a faint accent tint, with an ambient glow always present at rest and
brightening on hover, instead of a solid gradient-filled chamfered shape -
`chamferedRect()` was removed as dead code), `ToggleSwitch` (soft glow behind
the pill when on), `DarkNavyTheme` (richer near-black background, slightly
lighter/visible border), `SidebarButton` (the selected nav item is now an
inset glow ring around the whole pill instead of a solid accent bar down the
left edge), `HeroBanner` (rounded corners via `RoundRectangle2D` instead of
`ChamferShape`; the old full-width solid CTA bar flush against the bottom
edge is now a `ThemedButton`-style rounded outline-glow pill sized to its own
label, sitting under the title), `TopBar` (the bottom divider is now a plain
1px neutral border line instead of a solid accent-gradient bar),
`GameModeCard` (its per-mode color band is now custom-painted with rounded
top corners matching the card, instead of a square-cornered `JPanel` poking
out past the card's now-larger radius; the redundant, already-hidden
`enableTopAccent()` call was removed), and `ProfilePanel`'s `HeroCard` (same
`ChamferShape` -> `RoundRectangle2D` swap as `HeroBanner`). `GameHubDialog` and
`ShopPanel`'s cards needed no changes - both already built entirely from
`RoundedPanel`/`ThemedButton`, so they picked up the reskin automatically,
and their `enableTopAccent()` top stripes already correctly respect rounded
corners. Verified visually at each step via a throwaway Swing harness
rendering the changed components under Xvfb and capturing them with
`Component.printAll()` into a PNG - not just a clean compile. Every
individual game's own in-game screen is explicitly out of scope for this pass
(per the user) except the flagship game ("Vertex: Dominion," a persistent
nation-building strategy concept from the games backlog doc) once it's
actually built - it doesn't exist as a game yet.

**theme** = the color-system layer: `Theme.java` (interface: every color a screen might
need) + `ThemeColor.java` (color "roles" like `BG_PANEL`/`TEXT_MUTED` — components ask
for a role, never hardcode a `Color`) + `ThemeManager.java` (static holder for the
active theme + registry; listeners held as `WeakReference`s to avoid leaking game
windows that forget to unregister). Ten themes (`DarkNavyTheme` default,
`CrimsonRedTheme`, `OceanTheme`, `MidnightPurpleTheme`, `SunsetOrangeTheme`,
`ToxicGreenTheme`, `IceBlueTheme`, `GoldRushTheme`, `CyberpunkPinkTheme`,
`BloodMoonTheme`, `GlitchTheme` paired with `GlitchEffectOverlay` for its animated
distortion — the theme itself stays colors-only). `UITheme.java` = fonts/spacing,
theme-independent. `GameColors.java` = a **separate, fixed** palette for in-game
rendering that deliberately ignores the active Theme, so switching app theme doesn't
repaint the inside of Snake/Tetris/etc. `ThemeDropdown.java` = the settings picker.

`ui` widgets consume `theme` (via `ThemeManager.getColor(role)`); `pages` panels are
built from `ui` widgets; `NavIcons` lives in `ui`, consumed by `pages/Sidebar`.

## VertexServerTests — the committed regression suite

A sibling to `VertexServer/` and `VertexClient/`, not nested inside either -
`build.sh` globs every `.java` file under those two trees straight into the
shipped jars, so a test class living there would ship to every user's install.
Compiled against `VertexServer`'s freshly-built classes on the classpath and run
by `test.sh` (repo root, sibling to `build.sh`), which fails (nonzero exit) if any
test reports a failure. No external test framework (JUnit etc.) - matches this
project's zero-external-dependency philosophy (`build.sh` is plain `javac` + `jar`,
and both trees still open/run directly in BlueJ).

- **`support/Check.java`** - the one shared harness class every test uses: a
  `check(label, condition)` call per assertion, a running `checks`/`failures`
  count, and `finish()` as the last line of `main()` (prints the summary, calls
  `System.exit(1)` on any failure) - formalizes the exact hand-rolled pattern every
  prior scratch test already reinvented, not a new framework.
- **`net/VertexSerializationFilterTest.java`** - a REAL `ObjectOutputStream`/
  `ObjectInputStream` round trip through `VertexSerializationFilter.FILTER` (not
  just a compile check): a `Message` carrying an `Account` and a full
  `DominionSnapshot` (every `dominion.*` class the filter allow-lists, nested
  three deep through a `List`) deserializes correctly, and a deliberately
  non-allow-listed `java.util.HashMap` is actually rejected with an
  `InvalidClassException` - proves the filter's deny-by-default `"!*"` tail
  really denies, not just that the config string reads that way.
- **`admin/GameSuggestionStoreTest.java`/`AdminLogTest.java`/
  `FeedbackManagerTest.java`** - each submits an adversarial payload (an embedded
  newline for the first two's one-line-per-entry format, an exact 64-dash
  delimiter line for `FeedbackManager`'s block format) through the real
  `submit()`/`log()` API, forces a real save-then-reload round trip via a second
  fresh instance, and asserts the reloaded entries are byte-for-byte identical to
  the pre-reload list - the unambiguous proof nothing split into a second, forged
  entry. (An earlier version of the first two instead scanned for the *absence* of
  a `"] admin:"`-shaped substring, which false-failed against correct behavior -
  that exact forged-looking text is still legitimately present inline, as part of
  the one correctly-merged entry the fix produces, since the fix strips the
  newline rather than the attacker's characters. Caught and fixed before this
  landed, not shipped flaky.)
- **`chat/MatchChatRoomTest.java`** (2026-09-29) - the in-match chat rules the server
  must enforce: a `LOCKED` room (Telephone) delivers nothing until unlocked, non-members
  and empty/over-long text are handled, leaving/closing detaches everyone, a player
  joining a new match's room is moved out of their old one, `closeAfter` really closes,
  and the `MatchmakingKernel` really opens a room for its games (via Dice Duel).
  **`support/FakeClientHandler.java`** is the shared socket-less `ClientHandler` these
  and `MatchmakingKernelAdoptersTest` use (moved out of that test's nested class). Mute
  and flood limits live in `ClientHandler.handleMatchChat` and need real managers, so
  they are not covered here.
- **`forum/ForumServiceTest.java`** and **`forum/ForumHandlerTest.java`** (2026-09-29). The
  first covers the store and rules: boards, title/body cleaning and length caps, ordering,
  lock/delete, the post and list caps, persistence and id continuity across a reload,
  damaged files, and the forgery round trip (a body containing record text stays ONE post).
  The second drives the **real `ClientHandler`** (real account store, moderation manager and
  admin log) to pin the security rules: reading needs no login, posting does, a muted
  player and a flooder are refused, and delete/lock are moderator/admin only - a refused
  attempt leaves no admin-log entry, an accepted one does.
- **`dominion/DominionStoreTest.java`** - a full save/load round trip for every
  entity type `DominionStore` persists, including a declared-but-not-yet-active
  war whose `effectiveFromTick` must survive exactly rather than being
  reinterpreted relative to whatever tick the reloaded world starts at, and
  loading with no file present yielding a fresh empty world rather than an error.
- **`games/MatchmakingKernelAdoptersTest.java`** (added alongside Dice Duel/Typing
  Duel's `MatchmakingKernel` conversion, 2026-09-29) - added because it actually
  caught a real bug before it shipped: a first-pass conversion of both managers
  would have silently changed their matchId format (using the kernel's short
  constructor defaults `matchIdPrefix` to `gameId`, but each game's real format
  has no hyphen while `GAME_ID` does), and this test asserts the exact prefix a
  real match-found message carries, not just that pairing happens at all - a
  correctness-critical regression this project would otherwise have no way to
  catch the next time this kernel gets a new adopter.
- **`economy/EconomyConfigTest.java`** (added 2026-09-29) - same "it actually
  caught a real bug" bar as the test above: Trivia Blitz had been silently
  paying its winner(s) zero coins since it shipped (`getWinReward()`'s table was
  missing a "trivia-blitz" entry the game's own code had always looked up - see
  `EconomyConfig.java`'s note and `ROADMAP.md`'s "Done" section). Asserts every
  one of the 24 game ids that actually call `EconomyManager.awardWin(...)`/
  `EconomyConfig.getWinReward(...)` gets a real, nonzero reward rather than
  silently falling through to the "unrecognized id" default - the exact
  category of bug this file's own zero-coins incident was.
- **`mechanics/ReconnectRegistryTest.java`** (2026-09-30) - the grace-period mechanism
  itself (timeout fires exactly once, a reconnect inside the window cancels it, nothing
  pending, cancel, a second grace period replaces the first, a refused reconnect) plus
  `ReconnectPolicy` (30s, the exception list, guests, Chess enabled), using the
  package-visible short-grace constructor so nothing waits out 30 real seconds.
- **`mechanics/PairReconnectGamesTest.java`** (2026-09-30) - the shared `PairReconnect` helper
  as wired into Dice Duel, Signal Grid, Fusion Grid, Memory Match, Typing Duel, Card Rush, Air
  Hockey, Snake Arena and Tetris Duel: one scenario per game through a small adapter (drop
  pauses and notifies, the other player's move is held, reconnect swaps the new handler in and
  reports game/slot/opponent/turn, timeout forfeits once, guest and deliberate Leave forfeit
  immediately, both-gone ends quietly). Real-time games check that ticks stop during the pause
  and stay stopped through the resume delay; Memory Match checks its mismatch timer refreshes
  the notice instead of faking a resume; Typing Duel checks a round that comes due while
  someone is away starts on their return.
- **`games/ChessReconnectTest.java`** (2026-09-30) - the same for Chess's hand-written version,
  plus a pending draw offer being cleared on a drop and a new login taking over a stale session.
- **`net/ResponseTypesTest.java`** (2026-09-30) - reads `VertexServer/net/ClientHandler.java` for every `..._RESPONSE` type
  the server sets and requires each to be in `NetworkManager.RESPONSE_TYPES` (except the two deliberately-push tournament
  lists); a missing one makes a blocking `send()` wait 10s while holding the global lock. `test.sh` compiles the client-only
  `NetworkManager` (with `-sourcepath VertexClient`) for it and exports `VERTEX_REPO_ROOT`.
- **`economy/StatsTest.java`** (2026-09-30) - play counts, rating/record/best-score rows and the real `handleStats` handler.
- **`account/AdminBootstrapTest.java`** (2026-09-30) - first-run admin setup (console, environment, bad input, no console).
- **`chat/CalcParserTest.java`** (2026-09-30) - the `/calc` parser.
- **`economy/PracticeRewardLimiterTest.java`, `economy/GamePlayedHandlerTest.java`, `games/CardRushHiddenInfoTest.java`** (2026-09-30) - the
  offline-reward rate limit (15s per game per account, 300 coins/day, resets next day), the real `GAME_PLAYED_REQUEST` handler (game ids
  that aren't `[a-z0-9-]{1,40}` are dropped; repeated claims pay once), and that Card Rush never sends a player the other's hand.
- All five tests that touch a flat-file store hardcoding a relative file name
  (`GameSuggestionStore`/`AdminLog`/`FeedbackManager`/`DominionStore` all do -
  same pattern as `ServerAccountStore`) run from their own fresh temp working
  directory (`test.sh`'s job, not the test classes' own) so they never touch this
  repo or collide with each other.
- **Deliberately a small first batch, not a mandate to graduate every future
  scratch test** - the reversible default from the 2026-09-28 testing-
  infrastructure audit: the security/correctness-critical ones now, quick
  one-off UI/visual checks stay scratch-and-discard. Whether *every* future test
  should eventually graduate is still open - see `ROADMAP.md`.

---

*Regenerated periodically as the codebase evolves — see the repo's commit history for
what's changed since this snapshot.*
