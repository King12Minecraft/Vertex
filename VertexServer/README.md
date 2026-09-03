# Vertex Server — README

> **This folder is a synced build copy, not where edits happen.**
> `Vertex/` (the repo root's sibling folder) is the source of truth -
> every file here is an exact copy of one there. If you're changing
> code, edit it in `Vertex/` first, then copy the changed file into
> both `VertexClient/` and `VertexServer/` before committing. See the
> root `README.md`'s "Repo structure" section for why the three
> folders exist and why they're kept identical rather than trimmed
> down to a "real" client/server split.

This is the **GameHubServer** BlueJ project. It contains BOTH the
server logic and a full copy of the client's UI classes, so it can run
as one combined program: start the server AND open a login screen in
the same process.

## How to run it

1. Open this folder as a BlueJ project, compile all classes.
2. Right-click `ServerMain` → `void main(String[] args)` → leave
   blank.
3. The server starts, then an animated splash screen appears (~1.4s),
   then the login window opens - all in the same process. Log in (or
   create your account) and play immediately.

If no admin exists yet on this server, the account you create *from
this loopback connection* automatically becomes admin. Other players
can still connect from a separate `GameHubClient` project on the LAN.

## Why this project has ~145 files now

`ServerMain.main()` calls `AuthWindow` directly, so every client UI
class it depends on - effectively the whole client - has an identical
copy here too. **If you change any GameHubClient UI file, copy it into
this project too**, or the two will drift out of sync. The only client
file deliberately NOT copied here is `Vertex.java`.

## New this round: mode-select redesign + invites + Chess

This round bundled four separate asks - scoped honestly rather than
attempting everything at full size.

- **Mode-select redesign** (client-only) - `GameModeCard`, a shared
  big-tile mode-select component, applied to Racing/Tic-Tac-Toe/Fight
  Arena's mode-select screens.
- **Invite to game** - new `GAME_INVITE` message type. `ClientHandler`
  relays it directly to the target if online (fire-and-forget, no
  persistence, same as every DM-style message here). Client-side:
  `GameInviteDialog` (recipient), `GamePickerDialog` (sender, wired
  into Friends rows), plus a new global listener in `MainMenu`. Group
  Chat invites were left out - `ChatPanel` doesn't retain group
  membership after creation, and that needed flagging rather than a
  rushed workaround.
- **Chess** - new `ChessMatchManager` + `ChessMatch` (1v1 matchmaking,
  same pattern as `MatchManager`/`TicTacToeMatch`). Full standard piece
  movement, path-blocking for sliding pieces, check detection
  (a move that leaves your own king in check is rejected), checkmate/
  stalemate detection, pawn auto-promotion to queen. Deliberately no
  castling, no en passant, no local AI - `ClientHandler` constructor
  arity changed again (added as the last parameter) - 3 new request
  handlers, disconnect cleanup added.
- **Embedding every game in the main window instead of separate
  popups** - explicitly NOT attempted this round. That would mean
  migrating every existing game window into the client's `CardLayout`
  page system and handling continued execution while navigated away -
  a bigger undertaking than anything built so far, including Fight
  Arena's tick loop, and it deserves its own dedicated effort.

## New this round: Fight Arena (the most expensive build here, by design)

The one game with a genuinely continuous server tick loop, not the
event-driven request/response pattern everything else uses - real-time
combat has no cheaper substitute the way Racing (same-seed) or Among
Us (round-based voting) did, since live reflex combat against other
players *is* the entire appeal. Confirmed that scope directly before
building.

- **New `FightArenaMatchManager`** - four separate queues (1v1, 2v2,
  3v3, Chaos/FFA); team modes start on an even split, FFA uses the
  same immediate-start (min 3, max 8) pattern as Racing/Among Us.
- **New `FightMatch`** - a daemon `Thread` per match ticking ~15/sec
  (`Thread.sleep(66)` between ticks, not a `javax.swing.Timer` since
  this runs server-side). Fully server-authoritative: applies each
  player's latest known input, resolves melee hits (simple facing +
  range check), handles respawns, checks the win condition, broadcasts
  a full snapshot every tick. Deliberately simple mechanics - no
  jumping, no platforms, no weapon pickups, movement plus one attack -
  to keep this achievable.
- **Multiple concurrent matches "just worked"** once built with the
  same `Map<matchId, Match>` pattern every other match-based game here
  already uses - nothing special needed for that part.
- **`ClientHandler`** constructor arity changed again (added as the
  last parameter); 3 new request handlers, `handleDisconnect` added to
  the disconnect path.
- Per-tick state avoids adding a dozen more `Message` fields by
  packing each player into one pipe-delimited string
  (`"username|x|health|facingRight|attackFlash|alive"`) - a full
  roster needs to go out roughly 15 times a second, so keeping the
  payload compact and the field count low both matter here more than
  anywhere else in the codebase.

## New this round: Among Us (round-based, not a live map)

The biggest "big game" so far. Real Among Us needs continuous
position broadcasting for movement and spatial kill detection - the
same problem already avoided for Racing. This is a round-based social
deduction game instead (the genre Among Us itself borrowed from) - see
the client README's Status section for the full reasoning and what's
deliberately not built.

- **New `AmongUsMatchManager`** - 4-8 player matchmaking, same
  immediate-start pattern as `RacingMatchManager` (no grace timer).
- **New `AmongUsMatch`** - the actual state machine: role assignment
  (1 Impostor per 4-5 players, 2 for 6-8), per-player task lists (real
  for Crewmates, decoy for Impostors), kill handling, meeting/voting
  (majority ejects, ties eject no one), and both win conditions
  (Crewmates: all tasks done or all Impostors ejected; Impostors:
  equal or outnumber remaining Crewmates).
- **`GameServer`** constructs `AmongUsMatchManager`; **`ClientHandler`**
  constructor arity changed again (added as the last parameter) - 6
  new request handlers, plus among-us cleanup added to the disconnect
  path (`handleDisconnect` on `AmongUsMatch`, same pattern as
  Racing/Tic-Tac-Toe).
- **`GameRegistry`** gained a real `among-us` row - `square-wars` and
  `zombie-survival` were deliberately left as placeholders rather than
  repurposed, in case those specific named games are still wanted
  later.
- Kill and vote targets reuse the existing `toUsername` field instead
  of adding dedicated ones - null/empty on a vote means "skip."

## New this round: 3 more small games (client-only, GameRegistry entries added)

`AimTrainerWindow`, `CrossingRoadGame`/`CrossingRoadPanel`/
`CrossingRoadWindow`, `TetrisGame`/`TetrisPanel`/`TetrisWindow` - all 7
files are genuinely new this time (unlike last batch, no pre-planned
scaffolding was waiting). `GameRegistry` gained 3 rows
(`tetris`/`crossing-road`/`aim-trainer`); `GameLauncher` dispatches to
all 3. All single-player, no server-side logic changed. See the client
README for the full breakdown.

## New this round: Friend-direct chat (client-only)

No server-side change at all - new `FriendChatDialog`, a focused 1:1
chat opened via a "Message" button on each Friends row. Reuses the
existing `PRIVATE_MESSAGE` protocol Chat's DM tab already used, so
it's fully interoperable with it rather than a separate messaging
system - no new message types, no server-side history (matches every
other DM in Vertex). See the client README for the full breakdown.

## New this round: Racing set-finish + placement rewards

Racing changed from 1v1 survival to **3-6 player races with a set
finish line and ranked 1st/2nd/3rd coin rewards**.

- **`RacingMatchManager`** rewritten for N players (`MIN_RACERS=3`,
  `MAX_RACERS=6`) - a race starts the instant 3 are queued, taking up
  to 6 if more are already waiting. No grace-period timer for
  stragglers - deliberately kept simple rather than adding scheduled-
  task machinery for a countdown window.
- **`RacingMatch`** rewritten to rank N racers instead of comparing
  just two: finishers rank above crashers, finishers sorted fastest-
  first, crashers by distance survived. Awards coins via the new
  `EconomyManager.awardRacingPlacement(racer, place)` -
  `EconomyConfig.getRacingPlacementReward(place)` returns 50/30/15 for
  1st/2nd/3rd, 0 otherwise.
- **`ClientHandler`**'s `handleRaceFinished` now passes both
  `isRaceFinished()` and the frame count (via the reused `score`
  field) to `RacingMatch.reportFinished(who, finished, frameCount)`.
- **`GameServer`** passes `economyManager` into
  `RacingMatchManager`'s constructor - arity changed again if you're
  diffing.
- **No new "car cosmetics" shop system built** - the car's color in
  Racing now reads the player's already-purchased username color
  (`PlayerColorRegistry`, client-side) instead. A dedicated car-skin
  economy would be a separate, larger feature; see the client README
  for the full reasoning.

## New this round: Racing online + Puzzle Quest instructions

- **New `RacingMatchManager` + `RacingMatch`** (server-only) - 1v1
  matchmaking cloned from `MatchManager`/`TicTacToeMatch`'s existing
  structure. Deliberately NOT live position sync (see the class
  comment on `RacingMatch` for the full reasoning) - both racers get
  the same seed via `RacingGame`'s new seeded constructor, race
  independently, and report their final score
  (`RACE_FINISHED_REQUEST`). Whoever survived longer wins
  (`RACE_RESULT`).
- **`GameServer`** now constructs `RacingMatchManager` alongside the
  other managers. **`ClientHandler`** constructor arity changed again
  (added `RacingMatchManager` as the last parameter) - check the
  wiring if you're diffing. New handlers: `RACE_FIND_MATCH_REQUEST`,
  `RACE_LEAVE_QUEUE_REQUEST`, `RACE_FINISHED_REQUEST`. Disconnect
  cleanup now also cancels any pending Racing queue wait and notifies
  a racing opponent if one is mid-match (same `OPPONENT_LEFT` pattern
  Tic-Tac-Toe already uses).
- **`GameRegistry`**'s `racing` row changed to `online=true`, version
  bumped to `1.1` - which will also trigger last round's Phase 13
  update-detection, showing a real "Game Updated" notification the
  next time a client refreshes the game list. Small nice side-effect
  of the two features actually going together.
- Puzzle Quest fix is client-only - see the client README.

## New this round: 4 small games (client-only, `GameRegistry` already had entries)

`RockPaperScissorsWindow`, `PongGame`/`PongPanel`/`PongWindow`,
`Merge2048Game`/`Merge2048Window`, `DinoGame`/`DinoWindow` - 7 of the 8
files were already present and correct on disk; only
`RockPaperScissorsWindow` needed a small fix (it wasn't recording play
history like the others). See the client README for the full
breakdown - included here only because this project carries a full
copy of the client UI. No server-side logic changed - `GameRegistry`
already had rows for all 4 game IDs.

## New this round: Phase 13 update detection (client-only)

No server-side change - `GameManager.refresh()` now compares the
fetched game list against the previous one and posts real Notification
Centre entries for new/updated/removed games (version comparison via
the existing `GameInfo.version` field, no new protocol needed). The
"downloads/verification" half of Phase 13 doesn't apply to this
codebase's architecture (all game code is compiled in statically, no
plugin/download system exists) and is left out rather than faked. See
the client README for the full explanation.

## New this round: TopBar online count + account menu (client-only)

No server-side change at all - `TopBar` now shows a live "N Online"
count next to the notification bell (reuses the existing
`ONLINE_USERS_REQUEST`, no new protocol), and Profile/Settings moved
from `Sidebar` into a click-the-username account menu on `TopBar`.
`TopBar`'s constructor now takes a `NavigationListener`. See the
client README for the full breakdown - included here only because this
project carries a full copy of the client UI.

## New this round: Games Home/All-Games split + queue-text fix (client-only)

No server-side change at all - `GamesPanel` restructured into two tabs
(Home with pinned Quick Play + Continue Playing; All Games with filter
chips), new `PinnedGamesStore` (local `Preferences`, no protocol
needed), and the queue-count label overflow bug fixed. `ThemedButton`
gained `setPrimary(boolean)` for the tab/filter active states. See the
client README for the full breakdown - included here only because this
project carries a full copy of the client UI.

**Workflow note:** starting this round, unchanged server-only files
are no longer regenerated each round - the last delivered zip is
unzipped to restore state, and only files that actually change get
touched.

## New this round: bug fix + Phase 9 chat moderation (now real)

### Bug fix (client-only, no server change)
`FriendsPanel` called `ThemedTextField.getText()`/`.setText("")`,
methods that don't exist on that class (`getValue()`/`clear()` are the
real ones). See the client README for the full explanation - included
here only because this project carries a full copy of the client UI.

### Chat moderation
The `MOD_*`/`REPORT_*` message types and `Message` fields
(`muteDurationMinutes`, `reportReason`, `reportDescriptions`) already
existed from earlier planning - this round is what actually implements
the logic behind them.

- **New `ModerationManager`** - mute (`Map<String, Long>`, in-memory,
  time-limited - no need to survive a restart), ban (`Set<String>`,
  persisted to `gamehub_bans.dat`), and the report queue (persisted to
  `gamehub_reports.dat`). Keyed on username (case-insensitive), not
  account ID - unlike `FriendManager`, a ban has to catch someone even
  if they immediately rename to dodge it.
- **`GameServer`** now constructs `ModerationManager` and passes it
  into `ClientHandler` - constructor arity changed again, check the
  wiring if you're diffing.
- **`ClientHandler.handleLogin`/`handleCreateAccount`** check
  `moderationManager.isBanned(...)` before anything else.
- **`handleChatMessage`/`handlePrivateMessage`/`handleGroupMessage`**
  check `isMuted(...)` first - if muted, an `ERROR_NOTICE` goes back
  to the sender instead of the message being broadcast.
- **New `forceDisconnect(reason)`** on `ClientHandler` - sends a
  `FORCE_DISCONNECT_NOTICE` then closes the socket. The blocked
  `in.readObject()` in that client's own `run()` throws on the closed
  socket, which falls through to the exact same cleanup path as any
  other disconnect - no special-cased kick logic needed beyond that.
- **New handlers**: `MOD_MUTE_REQUEST`, `MOD_UNMUTE_REQUEST`,
  `MOD_KICK_REQUEST`, `MOD_BAN_REQUEST`, `MOD_UNBAN_REQUEST`,
  `REPORT_SUBMIT_REQUEST`, `REPORT_LIST_REQUEST`,
  `REPORT_RESOLVE_REQUEST` - all gated by the same
  `isModeratorOrAdmin()` server-side role check already used for the
  admin player-list feature.

## Previous rounds (still current)

- Phase 10: Friends (`FriendManager`) - requests, presence, keyed on
  account ID (contrast with Moderation's username-keying above).
- Phase 11: daily login rewards/streaks, coin transaction history
  (`TransactionManager`).
- Tic-Tac-Toe: rounds removed (single match only), Practice Mode vs
  local AI, real strike-through win line.
- Admin player lists, live queue counts, Racing/Puzzle Quest, the
  games-hub reskin.

## THE Economy Config - `EconomyConfig.java`

The one file an admin edits to change Vertex's economy: win rewards,
Snake score curve, daily login curve, quests, shop items. Moderation
has no economy hooks - a ban/mute/kick doesn't touch coins.

## Server-only classes (not present in GameHubClient)

| Class | Purpose |
|---|---|
| `ServerMain` | Entry point - starts the server, shows the splash screen, then opens the login window |
| `GameServer` | Binds the port, constructs every manager (now including `ModerationManager`), hands the accept loop to a background thread |
| `ClientHandler` | Per-client connection thread; login/chat/kick paths now check moderation state, plus the full set of mod/report handlers |
| `ServerAccountStore` | Account store: persistence (incl. login streak), lockout, `hasAdminAccount()`, coins, owned items |
| `GameRegistry` | The game list |
| `MatchManager` | Matchmaking + live queue-count broadcasting |
| `RacingMatchManager`, `RacingMatch` | Racing's own matchmaking - 3-6 players, same-seed race, ranked by finish time/distance survived, 1st/2nd/3rd earn coins |
| `AmongUsMatchManager`, `AmongUsMatch` | Among Us's matchmaking + state machine - 4-8 players, secret roles, tasks, kills, meeting/voting, win conditions |
| `FightArenaMatchManager`, `FightMatch` | Fight Arena's matchmaking (4 separate queues) + real-time tick loop - the only continuously-running game loop on the server |
| `ChessMatchManager`, `ChessMatch` | Chess's 1v1 matchmaking + full move validation, check/checkmate/stalemate detection |
| `TicTacToeMatch` | One authoritative single match (rounds removed), winning-line reporting |
| `ChatManager` | General Chat broadcast + online-user registry + `broadcastToAll` |
| `GroupChatManager` | Group creation, membership, message routing |
| `EconomyConfig` | THE admin config - win rewards, Snake reward curve, daily login curve, quests, shop items |
| `ChallengeManager` | Per-account quest/challenge progress tracking |
| `EconomyManager` | Wallet + purchase + reward facade, incl. Snake scoring and daily login reward |
| `GameHistoryManager` | Real, persisted play history - recent + trending |
| `PasswordHasher` | Salted SHA-256 hashing |
| `FriendManager` | Friend requests, accepted friendships, presence broadcasting - keyed on account ID |
| `TransactionManager` | Persisted coin transaction history per account |
| `ModerationManager` | Mute (in-memory), ban (persisted), report queue (persisted) - keyed on username |

Everything else in this project is an identical copy of the
`GameHubClient` UI classes - see the client README for those.

## Not done yet (flagged honestly)

Phase 13 (Updates/version checking), Phase 14's remaining admin tools
beyond what's built (game management, server status, economy
management UI, announcements), Phase 15 (Multiple Servers), Phase 16
(Remaining Games - explicitly deferred), Phase 17 (Polish backlog),
Phase 18 (Final Packaging).

## Security

Passwords salted+hashed, no hardcoded credentials, login lockout,
server-validated everything - moderation actions (mute/kick/ban,
report resolution) are all gated by a real server-side role check
(`isModeratorOrAdmin()`), never trusting the client's own claim. Bans
are checked at both login and account creation, so a banned username
can't just make a fresh account with the same name to get back in
immediately. No TLS - LAN only.
