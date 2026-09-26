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
  -> games (plugin-style match framework + ~30 online-game triples + ~20 offline games)
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
  login/account creation (first account ever created is auto-granted `Role.ADMIN`),
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

## games — the match framework, plus ~30 online-game triples and ~20 offline games

Two repeating shapes, not 179 individual designs:

1. **Offline/single-player** (Snake, Tetris, 2048, Minesweeper, Sudoku, Simon Says,
   Whack-a-Mole, Match Three, Maze Chase, Brick Breaker, Flappy Bird, Galaxy Defender,
   Word Guess, Bubble Shooter, Lights Out, Peg Solitaire, Klondike, Yahtzee, Mancala,
   Puzzle Quest, Dino Dash, Crossing Road, Aim Trainer, Pong, and more): a
   `<Name>Game.java` (pure state/logic) + `<Name>Window.java` (Swing host), sometimes a
   `<Name>Panel.java`. No server involvement.
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
  the only public method, shows `GameDetailDialog` (art, tags, difficulty, rules,
  a real Play button) instead of opening the game directly - there's no way to skip
  straight to playing. Package-private `openGame(...)` (callable only from
  `GameDetailDialog`'s own Play button, same package, once someone has actually seen
  that page) is now a single `GameWindowFactory.factoryFor(id)` lookup instead of a
  branch per game: `MainMenu.getInstance().showGame(factory.get())` when a factory
  exists, otherwise the same "not converted yet" notice `launch(...)` already shows
  for `comingSoon` ids. **All 51 games in the catalog** go through this path - every
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
  worth the churn for no behavior change. Also owns the one shared `ReconnectRegistry`
  instance (`getReconnectRegistry()`), passed into each `TicTacToeMatch` it constructs.
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
  parses it. Also owns a `ReconnectRegistry` instance of its own (`getReconnectRegistry()`)
  - every kernel-backed match type gets grace-period reconnect support for free the
  moment it adopts the kernel, added 2026-09-26 alongside the reconnect rollout below.
  Adopters: `CheckersMatchManager` and `ConnectFourMatchManager` (the original two,
  proving the shape generalizes and the `matchIdPrefix` divergence case), plus
  `ReversiMatchManager` and `DotsAndBoxesMatchManager` (converted from their own
  hand-rolled queues while adding reconnect, since they needed a registry anyway) - a
  wider rollout to the other ~22 `<Name>MatchManager` classes remains optional cleanup
  for whenever one is next touched, not a requirement (see `ROADMAP.md`). ELO
  deliberately isn't part of this - `LeaderboardManager`'s rating math is a separate,
  already-shared concern untouched by matchmaking queue mechanics.
- **`ReconnectRegistry.java`** — generic disconnect-grace-period mechanism, keyed by
  accountId (a brand-new `ClientHandler`/socket exists on reconnect, so accountId, not
  the handler reference, is the only stable identity). Any match class can adopt it by
  implementing the small `ReconnectableMatch` interface (`onReconnectTimeout()`,
  `onReconnect(newHandler)`, `attachToHandler(handler)`) and calling
  `beginGracePeriod(accountId, this)` from its own disconnect handling. Adopters:
  `TicTacToeMatch` (the original, via `MatchManager`'s own registry instance), plus
  `ConnectFourMatch`/`CheckersMatch`/`ReversiMatch`/`DotsAndBoxesMatch` (2026-09-26,
  each via its manager's `MatchmakingKernel`-supplied registry) - the same
  `disconnectedSlot`/grace-period/timeout shape in every one, differing only in how
  each match names its two player slots (`DotsAndBoxesMatch` uses a `List<ClientHandler>`
  and an index rather than two named fields, the one structurally different case).
  Chess is deliberately not yet adopted - its resign/draw-offer state interacts with a
  mid-grace-period reconnect in ways not yet designed, tracked in `ROADMAP.md` rather
  than guessed at. `ClientHandler.handleLogin()` tries every reconnect-aware match
  type's own registry in turn (`tryReconnectAllGames()`) and, if one had a match
  waiting, populates the `LOGIN_RESPONSE` with everything the client needs to resume
  (`reconnectGameId`/`reconnectTurnSymbol` plus the existing `matchId`/`symbol`/
  `opponentUsername`/`boardState` fields a match-found push already carries) - the
  client (`AuthWindow.resumeMatchIfPending`, via the small per-game
  `reconnectMessageTypesFor()` lookup covering all 5 adopters' own message-type pairs)
  reconstructs the equivalent of a fresh match-found + update locally from those fields
  and feeds them straight to a newly-built game window, deliberately not via a second
  server push to the reconnecting client's own socket (that race is explained in
  `ROADMAP.md`). A guest (no account) disconnect always forfeits immediately - no
  stable identity to grant a grace period against. Threading note load-bearing for
  anyone extending this to another game: `ReconnectRegistry`'s own lock must only ever
  be acquired either alone, or immediately before calling into the match (never the
  reverse) - see the class's own javadoc for the full reasoning.
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
- Shared dialog chrome reused across many games: `GamePickerDialog`, `GameDetailDialog`,
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
  shop catalog. The one place all economy numbers live.
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

Gating happens server-side in `ClientHandler` (`isAdmin()`/`isModeratorOrAdmin()`
checked at the top of every admin/mod handler); `handleAdminSetRole` can promote
PLAYER↔MODERATOR but refuses to grant/revoke ADMIN or touch another admin's role at all
(bootstrap-admin-only by design). Client-side, `pages/AdminPanel.java` and
`pages/ModeratorPanel.java` are gated the same way through `PermissionManager`, but
every such panel's javadoc repeats that this is UI convenience only.

## pages / ui / theme — the Swing client shell

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
`FriendsPanel`, `GameSuggestionsPanel`, `AdminPanel`, `ModeratorPanel`, `SettingsPanel`.
Supporting: `GlobalSearchField`, `NotificationBell`/`NotificationCenter`,
`QuickPlayDropdown`, `OfflineHubWindow` (currently just Snake), `WindowSizeMemory`.

**ui** = generic theme-aware Swing widget toolkit, no page-specific logic:
`RoundedPanel`, `ThemedButton`/`ThemedTextField`/`ThemedPasswordField`/
`ThemedTextArea`/`ThemedScrollBarUI`/`ToggleSwitch`/`StatusDot`/`StatusPill`,
`GameHubDialog` (themed replacement for raw `JOptionPane`), `DialogUtils`,
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

---

*Regenerated periodically as the codebase evolves — see the repo's commit history for
what's changed since this snapshot.*
