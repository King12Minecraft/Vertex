# Vertex — Program Structure

Onboarding doc for the codebase: what each package does and how the pieces connect.
`VertexClient/` and `VertexServer/` are byte-identical except `README.md` — everything
below describes `VertexClient/`; read it once for both. A handful of protocol/data
classes are marked **(shared)** below: they carry a "SHARED (Common)" javadoc tag and
are the literal same file copied into both trees (`net/Message.java`,
`net/MessageType.java`, `net/NetworkConfig.java`, `games/GameInfo.java`,
`games/FileHash.java`, `account/Account.java`, `economy/ShopItemInfo.java`,
`economy/ChallengeProgressInfo.java`).

## Entry points

- **`Vertex.java`** — client entry point. Shows `ui/SplashScreen`, then
  `account/AuthWindow` (login), installs a last-resort uncaught-exception handler (plain
  `JOptionPane`, so it works even if theming itself is broken), and kicks off
  `net/ClientUpdateChecker` in the background.
- **`ServerMain.java`** — starts `net/GameServer` (the real multiplayer server socket)
  **and** simultaneously opens its own `AuthWindow`/client UI in the same process — a
  "server" is also a playable client pointed at itself, not a headless process.

## Architecture at a glance

```
net (protocol + dispatch)
  -> games (plugin-style match framework + ~30 online-game triples + ~20 offline games)
       -> economy / social / ai   (services the match layer calls into)
  -> pages / ui / theme            (Swing client shell)
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
- **`NetworkManager.java`** — client-side counterpart, one persistent socket, three
  modes: blocking request/response `send()`; fire-and-forget `sendAsync()` +
  `PushListener` for server-initiated pushes; offline queueing for `sendAsync` while
  disconnected, flushed on reconnect. The push-listener queue has no per-request
  correlation, so a few UI panels deliberately use blocking `send()` on a background
  thread instead of `sendAsync` + listen, to avoid one call stealing another's reply.
- **`NetworkConfig.java`** *(shared)* — shared mutable host/port config, set at runtime.
- **`ClientUpdateChecker.java`** / **`ClientUpdatePackage.java`** — auto-update: client
  hashes its running jar (`games/FileHash`) and asks the server if it's stale; server
  re-reads `Vertex.jar`'s hash whenever its mtime changes. Client stages
  `Vertex.jar.new`; an external native launcher swaps it in on next start.
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
- **`GameRegistry.java`** — the hardcoded master catalog (~47 `GameInfo` entries).
  `ClientHandler.handleGameList()` serves this, patching in live queue counts for a
  few games.
- **`GameManager.java`** — client-side cache of the fetched catalog.
- **`GameLauncher.java`** — the plugin **launch** dispatch: one
  `launch(Component, GameInfo)` mapping a game id to `new XxxWindow().setVisible(true)`,
  shared by every "Play" entry point (games page, quick-play dropdown, global search).
  Falls back to a "not converted yet" dialog for unknown/coming-soon ids.
- **`GameMetadata.java`** — presentation-only (difficulty, tags) for game detail
  dialogs; has no effect on matchmaking or gameplay.
- **`MatchManager.java`** — the original reference matchmaking manager (for
  `tictactoe-online`): FIFO pairing queue, constructs the Match, broadcasts queue-count
  updates. Every other `<Name>MatchManager` follows this same shape with per-game
  tuning.
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
  `ConnectDialog`, `HostServerDialog`, `ServerBrowserDialog`, `SpectateDialog`
  (Chess-only), `RematchOfferDialog`, `ReplayBrowserDialog`.
- **`TriviaLiveLookups.java`** — plain holder bundling the 5 `ai.knowledge`
  `CachingFactLookup` instances `GameServer` builds once, threaded through
  `TriviaMatchManager` into every `TriviaMatch`.

Bot-AI-bearing games bridge into the `ai` package: `TicTacToePracticeMatch` +
`TicTacToePracticeBotStrategy`/`TicTacToeRandomMoveStrategy`; `BattleshipWindow` +
`BattleshipBotStrategy`/`BattleshipAI`/`BattleshipRandomShotStrategy`;
`RockPaperScissorsWindow` + `RockPaperScissorsBotStrategy`/
`RockPaperScissorsFixedMoveStrategy`; `MazeChaseGame` +
`MazeChaseChaserBotStrategy`/`MazeChaseChaserRandomStrategy`/`MazeChaseChaserState` —
all register primary+fallback strategies with `ai.AiKernel`.

## ai — three independent toolkits, unified by one philosophy

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

## economy — coins, ratings, achievements, cosmetics

Hook-in pattern: `EconomyManager`, `GameHistoryManager`, `LeaderboardManager` are
constructor-injected into every `<Name>MatchManager`; each `<Name>Match` calls back into
them at match end. `AchievementManager` is wired as a secondary dependency *into* those
three (not into match classes directly), so achievement checks piggyback on calls that
already happen rather than needing their own call site in every match.

- **`EconomyManager.java`** — the coin-award entry point:
  `awardWin(ClientHandler, gameId)` looks up the reward via `EconomyConfig`, credits the
  account, logs via `TransactionManager`, records challenge progress. Also handles shop
  purchases (always validated server-side — never trust a client-reported balance) and
  daily login rewards.
- **`EconomyConfig.java`** — pure static config: per-game win-coin table, practice-mode
  score→coin formulas, placement rewards for Racing/Space Battle, the 7-day daily-login
  streak table, challenge definitions, shop catalog. The one place all economy numbers
  live.
- **`LeaderboardManager.java`** — per-game ELO (K=32, start 1200) for symmetric 1v1
  games; a pairwise-ELO approximation for Fight Arena's N-player matches; separate
  best-score tracking for score-based games. Among Us is deliberately excluded from ELO
  (asymmetric roles don't map to a symmetric skill rating).
- **`AchievementManager.java`** — permanent, silent unlocks computed from three
  already-tracked metrics: win counts, total plays, coin balance.
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
`FriendManager.java` (requests/list, presence broadcast on login/logout),
`ChatManager.java` (tracks connected clients by username, DM routing, broadcast),
`GroupChatManager.java`, `PartyManager.java`/`Party.java` (play-together groups,
feeding `TeamTournamentManager`), `ModerationManager.java` (mute/kick/ban, keyed by
username not accountId to catch renamers, plus a report queue), and dialogs
(`FriendPickerDialog`, `NewDirectMessageDialog`, `NewGroupDialog`, `GameInviteDialog`,
`ReportPlayerDialog`, `ScoreShareDialog`, `PartyDialog`).

## admin — moderation tooling, gated by Role

- **`AdminLog.java`** — append-only audit trail. A record of what happened, not an
  access-control mechanism — writes only happen after `ClientHandler` already verified
  the actor's role.
- **`FeedbackManager.java`**/`FeedbackDialog`/`FeedbackListDialog` — bug reports/
  suggestions about Vertex itself (distinct from `ModerationManager`'s player-conduct
  reports); admins see everything, everyone else sees only their own.
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
outgoing page, swap underneath, fade out over ~220ms) instead of snapping.
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
`ChamferShape` (the shared angular-cut-corner geometry behind the visual "reskin"),
`NavIcons`, `GameModeCard`/`HeroBanner`/`PageHeader`/`MarqueeBanner`/
`HoverGlowAnimator`/`SidebarButton`/`SplashScreen`/`WinLineOverlay`.

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
