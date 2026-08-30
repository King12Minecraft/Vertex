# Vertex Client — README

This is the **GameHubClient** BlueJ project. It's built alongside a
separate **GameHubServer** project (created in Phase 5). See
`GAMEHUB_MASTER_INSTRUCTIONS_v2.md`, `GAMEHUB_ROADMAP_v3.md`, and
`GAMEHUB_ALL_IDEAS_v4.md` for the full design, phase plan, and complete
feature backlog this project follows.

## Status: Phase 1-9 ✅ + Economy ✅ + Reskin v2 ✅ + Combined Server ✅ + Offline Sync/Quests/Login Fix ✅ + Checkbox Groups + Colors + Guest Play ✅ + Snake Rewards ✅ + Chat File Uploads ✅ + Launcher Hero Banner ✅ + Launcher Chrome Everywhere ✅ + Logo Redesign + Offline Hub ✅ + Splash Screen + Page Transitions ✅ + Games-Hub Reskin (UI pass) ✅ + Tic-Tac-Toe Series + Live Queue Counts ✅ + Mutual Round Picking + Splash Fix + Racing/Puzzle Quest ✅ + Tic-Tac-Toe Practice Mode ✅ + Bug Fixes + Admin Player Lists ✅ + Phase 10: Friends ✅ + Phase 11: Daily Rewards + Transaction History ✅ + Real Bug Fix + Phase 9 Chat Moderation ✅ + Games Home/All-Games Split + Pinning + Queue-Text Fix ✅ + TopBar Online Count + Account Menu ✅ + Phase 13: Update Detection ✅ + 4 New Small Games ✅ + Racing Online + Puzzle Quest Instructions ✅ + Racing Set Finish + Placement Rewards ✅ + Friend-Direct Chat ✅ + 3 More Small Games ✅ + Among Us (Big Game) ✅ + Fight Arena (Big Game) ✅ + Mode-Select Redesign + Invites + Chess ✅

**This round bundled four separate asks. Scoped honestly rather than
attempting everything at full size:**

1. **Bloxd.io-inspired mode-select redesign** - new shared
   `GameModeCard` (big tile, colored band, title+description - the
   general "big clickable tile grid" pattern, no copied assets or
   content), applied to Racing, Tic-Tac-Toe, and Fight Arena's
   mode-select screens, replacing the old thin horizontal rows.
2. **Invite to game** - new "Invite" button on every Friends row,
   opening `GamePickerDialog` (any online-capable game) which sends a
   `GAME_INVITE`. The recipient sees `GameInviteDialog` (Join/Dismiss)
   via a new global listener in `MainMenu`. Scoped honestly: this is a
   *nudge*, not a private match - clicking Join just opens that game
   normally (mode-select/queue), same as launching it from the Games
   page, since Vertex's matchmaking is public-queue based rather than
   room/lobby based. **Group Chat invites were left out** - `ChatPanel`
   doesn't retain group membership after creation, and building that
   properly needs a way to fetch it from the server, which felt worth
   flagging rather than rushing.
3. **Embedding every game in the main window instead of separate
   popups (with Chat still reachable while playing) - explicitly NOT
   attempted.** This would mean migrating all 13+ existing game
   windows into panel-based components wired into the main
   `CardLayout`, plus handling continued execution while navigated
   away (tricky specifically for Fight Arena's live tick loop and
   every timer-based game). That's a larger undertaking than anything
   built so far, including Fight Arena, and deserves its own focused
   effort rather than being squeezed in alongside three other asks.
4. **Chess** - `ChessMatchManager`/`ChessMatch` (server) +
   `ChessWindow` (client, Unicode piece glyphs, no image assets
   needed). Full standard piece movement, check detection, checkmate/
   stalemate, pawn auto-promotion to queen. **Scoped down**: no
   castling, no en passant (still fully recognizable, playable chess
   without them), no local AI/practice mode (online-only - a real
   chess engine is a substantial project on its own). One new
   `Message` field (`chessToSquare`); everything else reuses fields
   already built for Tic-Tac-Toe.



**Fight Arena - the most expensive build in this project, by design.**
Confirmed up front: unlike Racing (same-seed instead of live sync) or
Among Us (round-based voting instead of a live map), a real-time
brawler's entire appeal *is* the live reflex combat - there's no
cheaper substitute that preserves the fun the way those other
substitutions did. So this is the one game with a genuine continuous
server tick loop (~15/sec, a daemon `Thread` per match) rather than
Vertex's usual event-driven request/response pattern.

**Original mechanics, inspired only by the general genre** (side-view
arena battle games with team modes) - no names, art, dialogue, or
other specific content copied from any reference game. Kept
deliberately simple to stay achievable: single flat arena, no jumping,
no platforms, no weapon pickups - movement plus one melee attack.
Server is fully authoritative (owns every player's position/health,
resolves every hit, broadcasts a full snapshot every tick); clients do
zero local physics prediction, they just render whatever the last tick
said.

**Four separate queues** - 1v1, 2v2, 3v3, and Chaos Mode (free-for-all,
3-8 players) - a player picks which one to join rather than being
pooled with everyone. Team modes start the instant an even split is
possible; Chaos Mode uses the same immediate-start pattern as
Racing/Among Us. First to 10 KOs wins (combined team score in team
modes, individual in Chaos Mode). **Multiple matches of any mode run
concurrently** - same `Map<matchId, Match>` pattern every other game
here already used, so this "just worked" once built the same way.

**New server-only classes**: `FightArenaMatchManager`, `FightMatch`
(the tick loop). **New client**: `FightArenaWindow` (mode-select/
queue/game/result) + `FightArenaPanel` (pure renderer + key input, no
physics of its own). 6 new `MessageType`s; per-tick player state is
packed into compact pipe-delimited strings (e.g.
`"username|x|health|facingRight|attackFlash|alive"`) rather than
adding a dozen more `Message` fields, since a full roster needs to be
sent every ~66ms.



**Among Us - the biggest "big game" built so far, scoped honestly.**
Real Among Us is a live 2D map with continuous movement and spatial
"isolated together" kill detection - building that would mean
broadcasting every player's position many times a second to every
other player, the exact same problem already avoided for Racing (same
seed instead of live position sync) and Tic-Tac-Toe (turn-based). So
this is a **round-based social deduction game** instead - the genre
Among Us itself borrowed from (Mafia/Werewolf) - keeping the actual
tension (hidden roles, sabotage, suspicion, voting) without simulating
a live map.

**How a match works:** 4-8 players queue up (`AmongUsMatchManager`,
same immediate-start pattern as Racing - no grace timer). Roles are
assigned secretly (1 Impostor for 4-5 players, 2 for 6-8) - Crewmates
get a real 4-task checklist, Impostors get a decoy list so they can
blend in. Impostors can kill any living Crewmate at any time; a kill
(or a manually-called emergency meeting) immediately opens a meeting
where every living player votes to eject someone or skip - majority
wins, a tie ejects no one. Crewmates win by finishing every real task
or ejecting every Impostor; Impostors win once they equal or outnumber
the remaining Crewmates.

**What's deliberately NOT built**, stated plainly rather than
silently: no live map/movement, no spatial "walk to a task" mechanic
(tasks are just a checklist), no in-match text chat (players can use
Vertex's existing Chat page during a meeting if they want to
discuss), no sabotage/vent mechanics beyond the kill itself. This is
the core deduction loop done honestly, not a full recreation.

**New server-only classes**: `AmongUsMatchManager`, `AmongUsMatch` (the
state machine - roles, tasks, kills, meetings, voting, win
conditions). **New client**: `AmongUsWindow`, handling every phase
(queue → task/kill screen → meeting/voting → result) via an internal
`CardLayout`, same pattern as `RacingWindow`/`TicTacToeWindow`. 11 new
`MessageType`s, and a matching set of `Message` fields, all prefixed
`among*` for clarity; kill/vote targets reuse the existing
`toUsername` field rather than adding more.



**Second batch of small games** (per "big games one at a time, small
games 2-5 at a time"): unlike last batch, no pre-planned scaffolding
was waiting this time - `GameLauncher`/`GameRegistry` were fully
consistent beforehand, so all 7 files here are new.

1. **Aim Trainer** (`AimTrainerWindow`, single file) - click the
   target before it times out, 20 rounds, score = hits.
2. **Crossing Road** (`CrossingRoadGame`/`CrossingRoadPanel`/
   `CrossingRoadWindow`) - Frogger-style, move up through lanes of
   traffic to reach the top. Reaching it counts a crossing and resets
   you to the bottom with slightly faster traffic (same continuous-
   ramp shape as Racing/Dino/Snake) - the only way the run ends is a
   crash.
3. **Tetris** (`TetrisGame`/`TetrisPanel`/`TetrisWindow`) - the 7
   standard tetrominoes, basic rotation (naive - rotates if the new
   orientation doesn't collide, no full SRS wall-kick system), line
   clearing, next-piece preview, speed ramps up as lines clear.

All single-player only, no coin rewards, matching Racing/Puzzle
Quest's existing precedent rather than Snake's.



**Friends can now be messaged directly, without going through Chat.**
New "Message" button on every friend row opens `FriendChatDialog` - a
small, focused 1:1 chat window, separate from the Chat page entirely.
It reuses the exact same `PRIVATE_MESSAGE` protocol Chat's DM tab
already used, so the two are fully interoperable: a message sent from
here shows up in Chat's DM tab for the recipient, and vice versa - this
is a second, quicker entry point to the same underlying conversation,
not a separate messaging system. Same as every DM in Vertex, it's
live/session-only (no server-side history), so the dialog always opens
empty. Non-modal, so Friends stays usable and more than one friend's
chat can be open at once. The Chat page itself is untouched - General
chat, Groups, and the DM tab all still work exactly as before.



**Racing redesigned again this round** - from "1v1, whoever survives
longest" to a real set-finish race with ranked rewards, per your
request. Scoping decisions made along the way, stated plainly:

1. **Set finish line.** `RacingGame.FINISH_FRAMES` (~30 seconds of
   play) replaces endless survival - reach it without crashing and
   you've "finished," in both Practice and Online mode. The speed
   ramp-up is unchanged, it just now has an end.
2. **3-6 player races, not 1v1** - needed for real 1st/2nd/3rd places.
   A race starts the moment 3 players are queued, taking up to 6 if
   more happen to be waiting at that instant. No grace-period timer to
   let stragglers join right as a race starts - that would need its
   own scheduled-task machinery on the server, and this keeps things
   simple. The tradeoff: queue right as a race of exactly 3 begins and
   you wait for the next one.
3. **Ranking**: everyone who reaches the finish line ranks above
   everyone who crashes; finishers sorted by fastest time, crashers by
   who survived longest. Only 1st/2nd/3rd earn coins (50/30/15).
4. **"Username color" reward tie-in, not a new "cars" shop category.**
   Building a whole separate car-cosmetics system (its own shop
   section, ownership tracking, purchase flow) would be a genuinely
   large separate feature. Instead: your car in Racing now renders in
   whatever username color you've already purchased in the Shop
   (`PlayerColorRegistry`, which already existed) - so the coins you
   win racing spend on the exact same cosmetic system that already
   colors your name everywhere else, and you see it reflected on your
   car. If a dedicated car-skin system is wanted later, that's a
   distinct, sizeable feature worth scoping on its own.



**Racing is now online** - scoped honestly rather than attempting true
live multiplayer, which would mean broadcasting both players'
positions many times a second (a fundamentally bigger real-time
protocol than anything else in Vertex, including Tic-Tac-Toe's
turn-based one). Instead: both racers get an **identical shared seed**
(`RacingGame`'s new seeded constructor), race independently on the
exact same obstacle sequence, and report their final score - whoever
survived longer wins. This is a real competition against a real
opponent, just resolved asynchronously instead of frame-by-frame.

- **New server-only `RacingMatchManager` + `RacingMatch`** - cloned
  from the existing Tic-Tac-Toe matchmaking structure (`MatchManager`/
  `TicTacToeMatch`) rather than invented fresh, since 1v1 pairing logic
  is already solved there.
- **`RacingWindow`** now has the same Mode-Select → Searching → Play
  flow as `TicTacToeWindow`, including a live "N in queue" count on
  the searching screen - reuses the existing `QUEUE_UPDATE` broadcast
  (just a different `queueGameId`), no new broadcast mechanism needed.
- **`GamesPanel`'s queue-count badge** (fixed a few rounds back for
  Tic-Tac-Toe) now also covers Racing. **`GameRegistry`'s** Racing
  entry updated to `online=true` so it shows correctly under the
  Multiplayer filter and gets a green "Online" status pill.
- **One new `Message` field** (`raceSeed`) - everything else (matchId,
  score, matchResult: WIN/LOSE/DRAW/OPPONENT_LEFT) reuses fields that
  already existed for Tic-Tac-Toe.

**Puzzle Quest now explains itself** - added a plain instruction line
("Click a tile next to the blank space to slide it...") directly on
the window, since nobody could tell what to do. Caught and fixed a
real layout bug of my own while adding it: my first attempt placed the
instructions in `BorderLayout.SOUTH`, which was already occupied by
the button row and would have silently dropped one of them - fixed by
wrapping the header and instructions together in `NORTH` instead.



**Batch of small single-player games (per your "big games one at a
time, small games 2-5 at a time" instruction).** Found that
`GameLauncher` and `GameRegistry` already had dispatch entries and
registry rows for exactly these 4 games, pointing at classes that
didn't exist - same pre-planned-but-unbuilt pattern as chat moderation
a few rounds back. On checking further, 7 of the 8 needed files
**already existed on disk, fully written and correct** - I read and
verified every one against the actual API surface (`ThemeColor`
constants, `UITheme` fonts, `GuestPlayTracker`/`SnakeGameOverDialog`
signatures, etc.) rather than assuming and rewriting, which is what
kept this round cheap. `package.bluej` and the client→server sync were
already correct for those 7; only one small gap needed fixing.

1. **Rock Paper Scissors** (`RockPaperScissorsWindow`, single file -
   no animation loop needed, purely event-driven) - the one real gap:
   it wasn't recording play history like every other game does. Added
   the same `GAME_PLAYED_REQUEST`/`GuestPlayTracker` pattern (score =
   wins) that Racing/Puzzle Quest/Pong/2048/Dino all already use.
2. **Ping Pong vs AI** (`PongGame` + `PongPanel` + `PongWindow`) -
   already complete, matches the Racing three-file pattern exactly.
3. **2048** (`Merge2048Game` + `Merge2048Window`) - already complete;
   notably uses theme-aware tile coloring (derived from the current
   theme's accent color) rather than hardcoded 2048-brand colors, so
   it looks right across every theme.
4. **Dino Dash** (`DinoGame` + `DinoWindow`, with the panel as a
   private inner class - no separate file needed) - already complete,
   matches the Racing pattern.

None of the 4 have coin rewards, matching Racing/Puzzle Quest's
existing precedent (only Snake currently has a reward curve).



**Checked the roadmap first** (as usual) - Phase 11 and Phase 12
(Offline) were both already fully done in earlier rounds, so the next
phase with real unfinished work is **Phase 13 - Updates**. One file
touched: `GameManager.java`.

- **`GameManager.refresh()`** now compares the freshly-fetched game
  list against the previously cached one and posts a real Notification
  Centre entry for anything **new** (a game ID that wasn't there
  before), **updated** (same ID, different `version` string), or
  **removed** (was there, now isn't). Skipped entirely on the very
  first fetch of a session, since there's nothing to compare against
  yet - everything would incorrectly look "new."
- **Scoped honestly**: Phase 13 also lists "Downloads, verification."
  That doesn't apply to how this codebase is built - every game's code
  is compiled directly into the app, there's no plugin/download
  architecture to hook a "download the new version" step into. Adding
  a fake download flow would be dishonest busywork, so it's left out
  rather than stubbed.



**Three files touched this round: `TopBar`, `Sidebar`, `MainMenu`.**

1. **Live "N Online" count added to the TopBar, next to the
   notification bell** - this is what was actually meant by "player
   count near notifications"; nothing there was broken, it simply
   didn't exist yet. Reuses the already-existing
   `ONLINE_USERS_REQUEST` (the same one the group-member picker uses)
   - no new server protocol needed. Refreshes on open and every 20s via
   a `javax.swing.Timer`.
2. **Profile and Settings moved out of the Sidebar into an account
   menu on the TopBar.** Clicking the username (top-right) now opens a
   small popup with Profile/Settings, matching the common "click your
   name for account options" pattern. `TopBar` now takes a
   `NavigationListener` in its constructor (same interface `Sidebar`
   already used) so it can trigger navigation directly.
   `Sidebar` is now purely game-related nav (Games, Quests, Friends,
   Chat, Shop, Moderation).



**Workflow change starting this round, per direct request:** stopped
recreating every server-only file each round from scratch. The last
delivered zip is now unzipped to restore full project state cheaply,
only the files that actually change get touched, and the folders stay
extracted for reuse next round instead of being deleted. Net effect:
same verified output, far less regenerated content per round.

**Games page split into two views** (`GamesPanel` restructured, one
file):
1. **Home** - hero banner, "Continue Playing" (real recent-play
   history, unchanged), and new "Quick Play" - a personal pinned
   shortlist backed by the new `PinnedGamesStore` (local
   `java.util.prefs.Preferences`, no server round-trip - this is a
   convenience feature, not account data).
2. **All Games** - the full catalog behind filter chips: All /
   Trending / Offline / Multiplayer. Trending reuses the
   already-fetched trending list; Offline/Multiplayer are derived
   client-side from `GameInfo.isOnline()` - no new server protocol
   needed for any of the filtering.

Every card on either view has a Pin/Unpin toggle, so the Quick Play
list can be built from wherever a game is found. `ThemedButton`
gained a `setPrimary(boolean)` method (it was constructor-only before)
so the two tabs and four filter chips can show which one is active.

**Fixed the queue-count overflow bug** - the label had no width
constraint of its own inside a `BoxLayout` column, so it could render
wider than the card and spill outside it. Now wrapped in a fixed-width
HTML span plus an explicit `setMaximumSize`, so it can never overflow
regardless of the count's length.



**Two things this round: a real bug fix, and closing out Phase 9's
last open item (chat moderation).**

### Bug fix
`FriendsPanel` called `addFriendField.getText()`/`.setText("")` -
`ThemedTextField` doesn't have those methods, it has `getValue()`/
`clear()` instead (it's a `RoundedPanel` wrapper around an internal
text field, not a `JTextField` itself). Fixed both call sites, then
checked every other `ThemedTextField` usage across the codebase for
the same mistake - none found.

### Phase 9: Chat Moderation - now real
The message protocol for this (`MOD_*`/`REPORT_*` types,
`muteDurationMinutes`/`reportReason`/`reportDescriptions` fields on
`Message`) had already been planned out in earlier scaffolding but
never actually wired up - that's what "fix the moderation" turned out
to mean on inspection, and what this closes out.

1. **New `ModerationManager`** (server-only) - mute (in-memory,
   time-limited - these are meant to be short cooldowns, no need to
   survive a restart), ban (persisted), and the player report queue
   (persisted). Bans/mutes are keyed on username rather than account
   ID, unlike Friends - a ban needs to catch someone even if they
   immediately try to rename around it.
2. **Real enforcement, not just admin buttons** - banned usernames are
   rejected at login and account creation; muted users get a clear
   notice instead of having their message silently dropped; kicking a
   player calls a new `ClientHandler.forceDisconnect()` that closes
   their socket, which naturally triggers the same cleanup path as any
   other disconnect.
3. **`ModeratorPanel`** now has real Mute/Kick/Ban buttons on every
   online player row, Ban/Unban on every all-time player row, and a
   live Reports queue with a Resolve button per report.
4. **New `ReportPlayerDialog`** - a themed dialog (matching
   `NewDirectMessageDialog`'s style, not a raw `JOptionPane`) reachable
   from a "Report a Player" button on the Chat page header, so the
   report queue actually has a way to receive reports from players.
5. **Global moderation notices** - `MainMenu` now listens for
   `ERROR_NOTICE` (mute notices, shown via Notification Centre) and
   `FORCE_DISCONNECT_NOTICE` (kick/ban, shown immediately) regardless
   of which page is currently open, since either can land at any time.

**Roadmap-wise, Phase 9 is now fully closed.**



**Closed out the remaining two items in Phase 11 (Economy)** - wallet,
coins, shop, and server-validated game rewards were already built in
earlier rounds; this round was specifically the two things the
roadmap still listed as open: daily login rewards/streaks, and a coin
transaction history in Profile.

1. **Daily login rewards + streaks.** `Account` gained `lastLoginDate`
   and `loginStreak`. `EconomyManager.applyDailyLoginReward()` runs on
   every login and account creation - a no-op if today's reward was
   already claimed, otherwise it checks whether yesterday was the last
   claimed day (streak continues) or there was a gap (streak resets to
   1), then pays out from a 7-day curve (`EconomyConfig.
   getDailyLoginReward`: 10 → 15 → 20 → 25 → 30 → 40 → 50 coins,
   cycling weekly). Login/account creation now show a small "+N coins!
   Day X streak" popup right after signing in.
2. **Coin transaction history.** New `TransactionManager` (server-only)
   - a persisted, per-account log of every coin change: match wins,
   Snake score rewards, shop purchases, daily rewards. Every existing
   coin-changing path in `EconomyManager` now logs a transaction
   alongside its existing behavior - no reward logic changed, just an
   audit trail added on top. `ProfilePanel` has a new "Coin
   Transaction History" card with a "View" button showing the recent
   log, most recent first.

**Roadmap-wise, Phase 11 is now fully closed.** Remaining open items
across earlier phases, tracked honestly rather than dropped: chat
moderation actions (mute/kick/ban, Phase 9). Phase 16 (Remaining
Games) stays deferred per instruction.



**Went to the roadmap (`GAMEHUB_ROADMAP_v3.md`) rather than guessing
what "next phase" meant.** Phase 16 (Remaining Games) is what's being
deferred per the last message. The next phase with real unfinished
work in sequence is **Phase 10 - Friends** (Phase 9's chat/social
system is mostly built already - the one gap, chat moderation actions
like mute/kick/ban, is still an open item, flagged below rather than
silently skipped).

### Phase 10: Friends - built
- **Friend requests** - send by username, live push notification to
  the target (`FRIEND_REQUEST_RECEIVED`), accept/decline. If two
  people send each other a request before either responds, the second
  request completes the friendship immediately instead of creating a
  redundant pending entry (`FriendManager.sendRequest`'s
  `AUTO_ACCEPTED` case) - mutual interest shouldn't need an extra
  round trip.
- **Friend list** with live online/offline presence - a status dot per
  friend, updated via `FRIEND_STATUS_UPDATE` pushes broadcast on every
  login and disconnect (`FriendManager.broadcastPresenceChange`),
  scoped so only actual friends of the person going online/offline get
  notified, not everyone.
- **Notification Centre integration** - incoming requests and
  acceptances both post a real notification, not just a UI refresh.
- **New `FriendsPanel`** page + sidebar entry (with a new two-person
  `NavIcons` glyph), reachable like every other page.
- **Everything keyed on permanent account ID, never username** - the
  roadmap's core rule for this phase. `FriendManager`'s persisted
  friendship/request files store account-ID pairs, so a friendship
  survives either side changing their username.

### Still open from Phase 9 (flagged, not silently dropped)
Chat moderation actions (mute/kick/ball) - `ModeratorPanel`'s "Chat
Moderation" card still says "Phase 9" because that specific piece
never got built, even though the rest of chat/social has been done for
a while.



**This round was a large mixed request - bug fixes, an admin feature,
and a very long new-games wishlist. Scoping it honestly:**

### Fixed
1. **Tic-Tac-Toe online - rounds removed, back to single-match play.**
   `TicTacToeMatch` reverted to one game, start to finish - no
   round-selection step, no series scoring. The best-of-N feature was
   a genuine source of confusion in real online play, so it's gone
   entirely for online matches. Practice Mode's *local* round picker
   (`TicTacToePracticeMatch`) is untouched - it's a fully separate,
   client-only code path with nothing to synchronize, so nothing about
   it could have caused the online issue.
2. **Player/queue count bug found and fixed.** The count badge was
   being shown for *any* game marked "Multiplayer" - including Square
   Wars and Zombie Survival, which have no real matchmaking behind
   them at all. Their count was permanently stuck at 0 since nothing
   ever updates it, which is almost certainly what looked broken. Now
   the badge only appears on Tic-Tac-Toe Online, the one game with a
   real queue, and renders bolder/more visible.

### Added
3. **Admin: real online-players and all-registered-players lists.**
   New `ADMIN_PLAYER_LIST_REQUEST` - the server independently verifies
   the requester is actually a moderator/admin (`ClientHandler.
   handleAdminPlayerList`) before returning anything, never trusting
   the client's own role claim. `ModeratorPanel` now shows two live,
   refreshable columns: everyone online right now, and every account
   that has ever registered on this server.

### Scoped down - direct and honest about why
4. **"Make Racing online"** - not done. Racing is single-player by
   design; converting it to real-time multiplayer means continuously
   syncing player positions many times per second, which is a
   fundamentally different (and much larger) protocol than anything
   built so far, including Tic-Tac-Toe. This needs to be its own
   focused piece of work, not a line item alongside everything else
   requested here.
5. **The full new-games list is not built.** Some of what's listed -
   Terraria, fighting arenas with 1v1 through 6v6 team support, Among
   Us, FFA/co-op modes, casino games with real-money-style betting on
   match outcomes - are each independently large projects, months of
   work in a real studio, not something addable in a single response
   alongside everything else here. Building rushed, half-working
   versions of a dozen games would leave the whole platform worse off
   than focusing on a few done properly. If there's a genuine next
   priority from that list - one or two specific games - say which
   ones and that becomes the next round's actual focus, sized and
   built properly rather than stubbed.



**"Make all games have a practice mode that works online and offline"
- scoped honestly.** Snake, Racing, and Puzzle Quest already satisfied
this: none of them ever needed a server connection to actually play -
only the post-game history/coin report is queued if you're offline
(`GuestPlayTracker`). The real gap was **Tic-Tac-Toe Online**, which
was inherently multiplayer with no way to play at all without a live
human opponent. That's the one genuinely new thing built this round.

- **New `TicTacToeAI`** - win-detection plus a medium-difficulty
  opponent (win if possible → block the human's win → take center →
  take a corner → anywhere open), entirely local, no network
  involvement at all. Deliberately beatable - the point is practice,
  not an unwinnable wall.
- **New `TicTacToePracticeMatch`** - a full best-of-N series against
  the AI, running 100% client-side. Mirrors the shape of the server's
  match logic (round/series scoring, winning-line reporting) so the
  window can reuse the exact same rendering and result screens for
  both modes.
- **`TicTacToeWindow` now opens on a mode-select screen** - "Play
  Online" (the existing matchmaking flow, unchanged) or "Practice
  Mode" (new, works with zero connection). Practice Mode reuses the
  same round-count picker, live score line, and real strike-through
  win line (`WinLineOverlay`) as online play - the only thing that
  differs is who's actually deciding the moves.
- **Deliberately NOT recorded into shared play history/trending** -
  those numbers represent real player activity; folding in solo
  practice-vs-bot completions would make "Trending" and win counts
  misleading. Practice Mode sends nothing to the server at all.



**Three fixes/additions from direct feedback on the previous round.**

1. **Round selection now requires mutual agreement, not a race.**
   "First pick wins" was genuinely confusing to actually play with -
   fixed it properly rather than patching the symptom. `TicTacToeMatch.
   chooseRounds` now records each player's pick separately (`pickX`,
   `pickO`) and only finalizes once **both have picked the same
   value** - broadcasting the live state to both players either way
   (`Message` gained `roundsPickX`/`roundsPickO`/`roundsFinalized`), so
   you always see what your opponent currently has selected and can
   change your own pick anytime before you agree. The picker screen
   now shows "You picked Best of 3. Username hasn't picked yet." (or
   similar) instead of guessing who should click first.
2. **Splash screen fixed** - the actual bug: the logo (drawn at
   y=84, 140px tall, so its bottom landed at y=224) and the wordmark
   text (baseline at y≈244) were positioned with fragile percentage-
   of-window-size math that left them almost touching. Rewritten with
   clean fixed-pixel spacing (the window size is a constant anyway, so
   percentages bought nothing but fragility) - proper gaps between
   logo, wordmark, tagline, progress bar, and status text, plus a
   subtle border so it doesn't look like a flat, edgeless popup.
3. **Racing and Puzzle Quest are now real, playable games** - not
   placeholders. To be direct about scope: **Square Wars and Zombie
   Survival are still not implemented** - both are marked multiplayer
   in the registry, and building real-time multiplayer action games
   (continuous state sync, not turn-based like Tic-Tac-Toe) is a much
   bigger technical undertaking than either of these two were. They
   still show the existing honest "hasn't been converted yet" message
   rather than a half-built broken mode.
   - **Racing** (`RacingGame`/`RacingPanel`/`RacingWindow`) - a lane-
     based endless dodge runner. Arrow keys or A/D to switch lanes,
     obstacles speed up over time, score = distance survived.
   - **Puzzle Quest** (`PuzzleQuestGame`/`PuzzleTileButton`/
     `PuzzleQuestWindow`) - the classic 4x4 sliding "15-puzzle".
     Shuffled by performing random valid slides from the solved state
     (not by randomizing tile positions directly), which guarantees
     every shuffle is actually solvable - a real property of this
     puzzle class, not a nice-to-have. Tracks move count, offers a
     fresh shuffle without closing the window.
   - Both single-player, same guest/offline-play pattern as Snake
     (`GuestPlayTracker`), both record play history for "Continue
     Playing"/"Trending". Neither currently earns coins - only Snake
     has that (confirmed reasoning: Snake's outcome can't be
     manipulated through the network protocol; extending coin rewards
     to these two is a separate decision to make explicitly, not
     something to quietly add here).



**The gameplay/protocol work queued from the UI-first pass, now done.**

1. **Tic-Tac-Toe: real strike-through line + best-of-N series.** Full
   rework of `TicTacToeMatch` (server) and `TicTacToeWindow` (client):
   - After matchmaking, both players see the same "Choose Match
     Length" screen (Best of 1/3/5) instead of jumping straight into
     play. Whichever player picks first sets it for both, server-side
     and thread-safe (`synchronized chooseRounds` - first caller wins,
     later picks are silently ignored) - the choice is broadcast to
     both via the new `ROUNDS_UPDATE` message, so the player who
     didn't pick still sees what was chosen.
   - Each round's winner is now reported with the actual winning
     3-cell line (`Message.winningLine`), not just a text result. New
     `WinLineOverlay` draws a real geometric strike-through (a glowing
     line through the three cells' centers) in a `JLayeredPane` sitting
     above the board grid - not a per-cell highlighted border like
     before.
   - Clear, unambiguous result text: **"X WINS THE ROUND"** /
     **"O WINS THE SERIES!"** with a "(You)" or opponent-name
     qualifier, plus a running "Round 2 of 3 • You 1 - 0 Username"
     score line. If the series isn't decided, the board resets in
     place for the next round automatically (same two players, no
     re-matchmaking) - starting player alternates each round for
     fairness.
   - Coins are still awarded per individual round win (unchanged
     reward semantics) - a best-of-5 sweep pays out proportionally
     more than a single round, not a single lump sum at series end.
2. **Live "N in queue" counts.** `GameInfo` gained a `queueCount`
   field, populated server-side from `MatchManager`'s real waiting-
   list size (only Tic-Tac-Toe Online has genuine matchmaking right
   now - everything else honestly shows nothing rather than a fake
   "0 in queue" for games with no real queue system). `MatchManager`
   now broadcasts a `QUEUE_UPDATE` push (via `ChatManager.
   broadcastToAll`, a new method reusing its existing connected-clients
   registry) every time the waiting count changes, so the badge on the
   Games page updates live without polling.



**Reskinned to match a specific reference** (games-hub aesthetic: near-
black backgrounds, one bold flat neon cyan accent, big bold condensed
titles, full-width solid CTA bars). This was explicitly scoped as
"UI first" - the gameplay asks from the same request (Tic-Tac-Toe win
strike-through + round picker, live queue counts per game) are queued
for the next round, not done here.

1. **Default theme recolored** (`DarkNavyTheme`) - near-black
   backgrounds (was navy-tinted dark blue) and a single bold flat neon
   cyan accent (was a blue gradient). The fixed brand colors in
   `GameLogo.renderIcon` and `SplashScreen` (which intentionally don't
   follow the live theme) were updated to match, so there's no jarring
   mismatch between the boot sequence and the in-app look.
2. **Sidebar is genuinely icon-only now, expanding on hover** - this
   was the one thing that directly conflicted between the written
   request and the reference images (reference used a bottom bar);
   confirmed with the user first rather than guessing. Collapses to
   64px (icons only) by default, smoothly animates out to 220px on
   hover (revealing each button's label, the "GAMEHUB" wordmark, the
   quest mini-list, and the connection status text), and back on
   mouse-out. New `NavIcons` - simple flat glyph icons (gamepad, quest
   flag, chat bubble, shop bag, profile silhouette, gear, shield) drawn
   entirely in `Graphics2D`, same "no image files" principle as every
   other icon in the app.
3. **Admin page removed** - it was functionally redundant with
   Moderation (an admin already has every moderator permission via
   `PermissionManager`), so the separate nav entry and page
   registration are gone. `AdminPanel.java` itself is left in place
   but now unreferenced, rather than deleted outright, to avoid a
   riskier same-turn deletion.
4. **`HeroBanner` redesigned to match the reference's signature move**:
   a small cyan tracked-caps kicker sitting above a huge bold white
   title, both anchored bottom-left, and - the standout element from
   the reference - a **full-width solid cyan CTA bar flush against the
   very bottom edge** ("▶ PLAY NOW"), not a small pill button floating
   in the text block.

**Queued for next round (explicitly not done here, since this was UI-
first):** Tic-Tac-Toe's win strike-through line + clear "X/O wins"
result display + a pre-match round-count picker both players can see;
live "N in queue" counts shown per game on the Games page. Both are
real gameplay/protocol work, not styling.



**Scoping note on this round:** "change the UI completely, Opera GX
style" is too broad to responsibly claim done across all 85 files in
one pass. What's concrete and genuinely new here - and what was
missing entirely before - is real animation: a proper loading screen
and smooth transitions between pages. That's what got built, done
well, rather than another diffuse pass. Angular chamfers, gradients,
and glow accents (the actual "Opera GX" visual vocabulary) were
already built up across earlier rounds (Sections on Reskin, Launcher
Hero Banner, Launcher Chrome Everywhere) - this round is about motion,
not another look pass.

1. **`SplashScreen`** (new) - an animated loading screen shown at
   startup, before the login window: the new hexagon+G logo, a soft
   glow, and a progress bar that fills over ~1.4s. Wired into both
   entry points - `Vertex.main()` and `ServerMain.main()` - via
   `SplashScreen.showThenRun(Runnable)`, so both the client-only and
   combined-server launches get it identically.
2. **Smooth crossfade transitions between pages** - `MainMenu`'s
   page-switching no longer snaps instantly. A screenshot of the
   outgoing page is taken, the actual page swap happens immediately
   underneath (so the new page is live right away, no input delay),
   and the screenshot fades out over the top (~220ms) to reveal it -
   a standard, lightweight Swing technique (`JLayeredPane` + a
   `BufferedImage` snapshot + `AlphaComposite`), no extra libraries.
   Every sidebar navigation click (Games, Quests, Chat, Shop, Profile,
   Settings, Moderation, Admin) now transitions this way.



**Two more, addressing specific feedback from the previous round:**

1. **`GameLogo` was genuinely redesigned**, not just tweaked. The old
   mark (a rounded chamfered-square badge with a d-pad cross cut into
   it) is gone. The new mark is a **hexagonal "hub" silhouette** - a
   six-sided shape instead of a rounded square, more geometric and a
   nod to connectivity/hub imagery - with a bold letterform **"G"**
   (an open ring plus a crossbar, like a real G) cut into it in the
   background color. Still 100% `Graphics2D` shapes, no image file,
   same public API (`GameLogo(size)`, `renderIcon(size)`) so every
   call site - Sidebar, AuthHeader, window/taskbar icon - picks up the
   new mark automatically with no other changes needed.
2. **"Play Offline" now opens a real hub, not a hardcoded shortcut.**
   New `OfflineHubWindow` - a proper landing screen (matching the
   launcher chrome from previous rounds, including `PageHeader`) that
   lists every game genuinely playable with no account or server
   connection. Snake is the only entry today (Tic-Tac-Toe Online is
   inherently multiplayer - there's no opponent to play against
   offline, so it can't honestly appear here), but the architecture is
   a real grid now, not a special case: adding another offline-capable
   game later means adding one card, not restructuring anything. To be
   direct about scope: this fixes the *navigation* ("proceed to a hub,
   not jump straight into gameplay"), not the *catalog* - it doesn't
   fabricate new playable games that don't exist yet.



**Extended the launcher pass to the rest of the pages** (aesthetic
only, same as last round): every page now shares consistent chrome
instead of just Games having the special treatment.

- **`PageHeader`** - new shared component: page title + the same
  accent-gradient underline already on TopBar/Sidebar, with an
  optional right-side action slot (used by GamesPanel for its Refresh
  button) and a settable title for pages whose header changes at
  runtime (ChatPanel's current channel name). Swapped into `GamesPanel`,
  `ChatPanel`, `ShopPanel` (previously had no page title at all),
  `QuestsPanel`, `SettingsPanel` (previously had no page title either),
  `AdminPanel`, and `ModeratorPanel`.
- **`ProfilePanel` got a real hero-card treatment** - the natural
  "player card" moment every launcher has (Steam, Epic, Discord all
  treat their own profile page this way). The avatar/name/role header
  is now a gradient chamfered card with a soft accent glow, matching
  the same visual language as `HeroBanner`, instead of sitting on a
  flat panel. The gradient avatar circle also got upgraded from a flat
  fill to match.

Every page in the app now shares the same launcher-style chrome
vocabulary (gradient accents, chamfered corners, consistent header
treatment) rather than just the Games page standing apart.



**Aesthetic-only pass toward a real "launcher" look** (Steam/Epic/
Battle.net style) - explicitly scoped down from "everything" to what
actually defines that look: the Games page got a genuine hero-banner
treatment (the single biggest visual signature of a launcher home
screen), plus lighter "chrome" polish on the Sidebar and TopBar. Other
pages (Chat, Shop, Quests, Profile, Settings, Admin/Moderator) are
unchanged this round, both functionally and visually - flagged clearly
rather than spreading a partial pass across everything.

- **`HeroBanner`** - large featured-game banner at the top of the Games
  page: full-width chamfered panel, gradient background with a soft
  accent glow, the featured game's own icon rendered huge and faint
  off to one side (reusing `GameCardArt`'s per-game icon drawing at
  scale via a new `GameCardArt.paintIconOnly(...)` static entry point -
  no separate hero artwork to maintain), large title/type/status text,
  and one prominent "▶ Play Now" button. Auto-picks the top trending
  game once that data loads, falling back to the first playable game,
  then any game at all - and re-picks live whenever the game list or
  trending data refreshes.
- **`Sidebar`** gained a subtle top-to-bottom depth gradient and a
  thin accent-gradient divider line down its right edge - a small but
  real launcher "chrome" detail (Discord/Steam nav rails almost always
  have this instead of a flat panel edge).
- **`TopBar`** gained the matching accent-gradient divider along its
  bottom edge, for visual consistency with the Sidebar.

*(The rest of the pages got their turn the following round - see the
entry above.)*



**Chat now supports file attachments**, capped at 2MB
(`NetworkConfig.MAX_FILE_SIZE_BYTES`), and the server never writes any
of it to disk - files are relayed exactly like any other message
field and only exist in memory for the duration of delivery, matching
the existing "no message history/persistence" limitation chat already
had.

- **New "Attach" button** next to Send in `ChatPanel`, works in
  General, DMs, and Groups alike (same routing every other message
  already uses). Picks a file, checks the size client-side before even
  attempting to send, reads it into memory, and sends it as its own
  message (with any typed text alongside it, or standalone).
- **Server-side size validation too** - `ChatManager.validateFile()`
  drops anything over the limit even if a modified client tried to
  bypass the client-side check, so no connected client can be flooded
  with an oversized payload by a bad actor.
- **Received files show as a clickable chip** ("📎 filename.ext (size)")
  - click it to save to disk via a normal file-save dialog. Nothing
  auto-saves anywhere.
- **Protocol change**: `Message` gained `fileData` (byte[]) and
  `fileName` fields. `ChatManager.broadcast`, `GroupChatManager.
  sendGroupMessage`, and the private-message handler in `ClientHandler`
  were all updated to carry these through, plus one behavior change:
  they now allow empty chat text as long as a file is attached
  (previously empty text always meant "nothing to send").



**Snake now pays real coins, based on score.** Confirmed directly: unlike
other Practice Mode games, Snake's outcome can't be manipulated through
the network protocol - there's no opponent or server-authoritative
state to fake, it's a purely local game - so it's exempt from the
"Practice Mode pays 0" rule the rest of the economy follows.

- **`EconomyConfig.getSnakeReward(score)`** - 1 coin per 5 points,
  capped at 25 coins per game (both numbers live in the same
  admin-editable class as everything else). Only wins pay in
  multiplayer, but Snake pays on every completed game since there's no
  "win/lose" concept, just a score.
- **`GAME_PLAYED_REQUEST` now carries a `score` field**, and fires at
  game-OVER instead of game-START (previously it fired at start, before
  any score existed at all) - `SnakeWindow` moved the call into the
  game-over callback. A useful side effect: someone who opens Snake and
  quits without ever finishing a round no longer gets a history entry
  or reward - only a genuinely completed game counts, which is also a
  small natural defense against "farm by opening/closing repeatedly."
- **`GuestPlayTracker` now queues score alongside each play**, so
  offline/guest Snake sessions still get their coins once you log back
  in - not just their history entry, as documented in the previous
  round's (now superseded) "history only, no coins" caveat.



**Four more additions this round:**

1. **Checkbox-style group member picker.** `NewGroupDialog` no longer
   asks you to type comma-separated usernames - it fetches everyone
   currently online (`ONLINE_USERS_REQUEST`) and shows real checkboxes
   to pick from.
2. **Purchased username colors are now actually visible**, closing a
   limitation flagged back in the Economy pass. `PlayerColorRegistry`
   resolves a purchased color ID to its real `Color`, and it's applied
   to: your own username in the `TopBar` and `ProfilePanel`, the
   `ShopPanel` now has a real Select/Selected flow (not just "Owned"),
   and - the bigger piece - **other people's usernames in chat now show
   their color too**. That required a protocol change: `Message`
   gained a `senderColorId` field, and `ChatManager`/`GroupChatManager`
   now look up and propagate the sender's selected color on every
   message (general, private, and group).
3. **Quests are now color-coded by reset period** - daily quests use
   the theme accent, weekly uses success green, one-time/permanent
   quests use the gradient's second color. All colors still come from
   the active theme, so this holds up correctly across all 10 palettes
   rather than hardcoding a look that might clash with some of them.
4. **Offline account creation now has a clear, honest message and a
   real fallback.** Trying to create an account while offline explains
   *why* (the server has to check the username and assign the account -
   that can't be faked locally) and points to the new **"Play Offline
   (Snake)" button** on the login screen, which needs no account or
   connection at all. Plays made this way are queued locally
   (`GuestPlayTracker`) and get attributed to your account the moment
   you actually log in - **this syncs both play history and coins**
   (see the Snake Rewards entry below - confirmed directly that Snake's
   score can't be manipulated through the network protocol, so it's
   exempt from the Practice Mode coin-free rule other games follow).



**Five more additions this round:**

1. **Real offline queueing.** `NetworkManager` now queues fire-and-forget
   messages (chat, group messages, play-history pings) instead of just
   failing when the server's unreachable, and a background reconnect
   thread retries every 4 seconds with no user action needed - once it
   succeeds, the whole queue flushes automatically. **Scope note:** true
   request/response calls (login, purchases, account changes) still
   need a live connection when made - there's a real answer to wait
   for that can't be faked locally, same as a bank app can't "queue" a
   login. `ConnectionIndicator` now shows "Offline - N pending" while
   messages are queued.
2. **Bootstrap-admin fix.** Admin status now follows "created from the
   server's own machine" (a loopback/localhost connection - i.e. via
   the combined `ServerMain` program), not "whoever registers first."
   A remote player joining before the server operator creates their
   own account can never accidentally become admin. Login is still
   required either way - this only changes which account gets the
   role.
3. **Quests, on their own main tab.** The Challenges system (unchanged
   underneath) now has a real home: `QuestsPanel`, a full page reached
   via a new Sidebar nav item, plus `QuestRow` - a shared component
   used both there (full detail) and in a new **always-visible
   in-progress mini-list in the Sidebar itself** (compact, top 3
   incomplete quests, updates live via `CHALLENGE_UPDATE`). Shop lost
   its embedded Challenges section since Quests replaced it - Shop is
   purely for spending now.
4. **Real per-game icons, hand-drawn, no external files.** `GameCardArt`
   now draws an actual distinct vector icon per game (snake curve,
   tic-tac-toe grid, checkered flag, puzzle piece, etc.) instead of a
   generic letter watermark - same "Graphics2D only, nothing loaded
   from disk" principle as `GameLogo`.
5. **Login screen background bug fixed.** The actual root cause of
   "ugly login screen": `AuthWindow`'s root panel and the header/footer
   rows around it never had a themed background set, so they were
   showing Swing's default light-grey behind the dark-themed card -
   a jarring clash against the rest of the app. Now themed and
   live-updates with theme changes. Also gave `GameHubDialog` (every
   alert in the app) the same gradient top-accent treatment cards
   already have.



**Five more additions this round:**

1. **Themed scrollbars everywhere.** `ThemedScrollBarUI` (a minimal flat-track, rounded-pill-thumb scrollbar) replaces the default OS-grey bars in every `JScrollPane` in the app - Chat, Shop, Settings, Admin, Moderator, Games.
2. **Theme picker is now a dropdown**, not a grid. `ThemeDropdown` replaced the old `ThemeSwatchButton` grid entirely (that class is now removed) - click it, see every theme with its own gradient swatch, click one to apply.
3. **Quick-play dropdown in the top bar.** `QuickPlayDropdown` lists every game and launches it directly, without needing to visit the Games page first. `GameLauncher` was extracted from `GamesPanel`'s Play button so both share the exact same launch logic (including the error-visibility safety net) rather than duplicating it.
4. **Gamified game cards.** `GameCardArt` gives each card's header a gradient chamfered background (matching the button/logo angular treatment) with the game's initial letter as a translucent watermark - a cheap way to give every card visual identity without needing per-game custom artwork. Applies to placeholder "Coming Soon" cards too, not just Snake/Tic-Tac-Toe.
5. **Real "Continue Playing" + "Trending" sections on the Games page**, backed by genuine server-side tracking:
   - **`GameHistoryManager`** (server) - records every play event with **real file persistence** (`gamehub_play_history.dat`, appended on every play, loaded back on startup) - this is real tracking, not a session-only placeholder.
   - Tic-Tac-Toe matches record automatically when `MatchManager` pairs two players (the server already knows this happens - no extra client message needed). **Snake** has no other server interaction at all, so `SnakeWindow` now sends a fire-and-forget `GAME_PLAYED_REQUEST` when a game starts, specifically so it can be tracked too.
   - "Trending" = all-time global play counts across every account, highest first. "Continue Playing" = this account's most recent distinct games. Both sections only appear when there's actually something to show.
6. **More "gamified" touches on Snake/Tic-Tac-Toe specifically:**
   - Tic-Tac-Toe: the winning 3-in-a-row now glows once a match ends (computed client-side from the final board - no protocol change needed).
   - Snake: a brief fading accent flash sweeps the board when food is eaten, on top of the smooth movement and food pulse already there.

**Known limitations, on purpose for now:** trending is all-time, not time-windowed (e.g. "trending this week") - a real windowed calculation is a reasonable follow-up if all-time counts start feeling stale. Purchased shop colors still aren't visually applied anywhere (unchanged limitation from the Economy pass).



**This response adds three things on top of the already-completed
Reskin v2** (branded pulsing `AuthHeader`, `RoundedPanel.glow()` +
`enableTopAccent()` applied broadly across game/shop/admin/moderator
cards, focus-glow on text fields - all already done and verified
working):

1. **Combined server+client program.** `ServerMain` now does two
   things: starts the server (`GameServer.start()` binds the port
   synchronously, then hands the accept loop to a background daemon
   thread instead of blocking), then immediately opens the login
   window (`AuthWindow`) in the same process. Since
   `NetworkConfig.SERVER_HOST` already defaults to `"localhost"`, this
   embedded client connects to the server running right there
   automatically - no special-casing needed. Whoever runs the server
   can log in and play immediately, no separate `GameHubClient`
   required (though running one separately still works too, for other
   players).
   - **Structural consequence:** `GameHubServer` now contains a full
     copy of the client's UI classes (everything except `Vertex.java`,
     since `ServerMain` replaced that entry point's role). If a client
     UI file changes going forward, copy it into the server project
     too - same discipline already used for the smaller shared
     protocol files, just at a larger scale now.
2. **`GlowBackdrop`** - a small additional soft ambient corner glow
   painted behind the Login/Create Account form panels themselves
   (separate from `AuthHeader`'s focused pulsing glow around the logo
   above them) - the two layer together rather than conflict.
3. **Game animations**:
   - **Snake** now moves with smooth interpolation instead of snapping
     grid-cell to grid-cell - a fast render timer (~60fps) blends each
     segment's drawn position between its previous and next cell based
     on elapsed time, decoupled from the slower game-logic timer. Food
     also gets a subtle pulsing glow.
   - **Tic-Tac-Toe** cells are now a custom-painted component
     (`TicTacToeCellButton`) instead of a plain text button, so placing
     an X or O animates in with a smooth scale-up reveal.



**Applied to the "chrome," not everywhere.** Opera GX's own UI applies
its neon/angular look to browser chrome (tabs, sidebar, buttons) while
page content stays clean - the same restraint applies here. Gradients,
chamfered (angular-cut) corners, and animated glow live on **primary
buttons, the logo, and the sidebar's selected-nav accent bar**.
Content cards (game cards, chat panels, shop items) deliberately stay
the clean rounded style - applying the loud treatment to literally
everything would hurt readability, not help it.

What this adds:

- **`Theme` gained `accentGradientStart()`/`accentGradientEnd()`** -
  every theme now defines a two-color gradient pair, not just a flat
  accent color.
- **7 new themes** (10 total now): Crimson Red, Toxic Green, Sunset
  Orange, Cyberpunk Pink, Ice Blue, Blood Moon, Gold Rush - alongside
  the original Dark Navy, Midnight Purple, Ocean Teal.
- **Real theme picker, finally live** in Settings → Appearance - a
  grid of clickable gradient swatches, one per theme. This was
  originally scoped for "Phase 17," but since the whole reskin makes
  the picker actually worth having now, it went in with this pass.
- **`ThemedButton` reskinned** - primary buttons now have a gradient
  fill, angular chamfered corners, and a smooth animated glow that
  fades in/out on hover (`HoverGlowAnimator` - a small reusable
  Timer-based fade helper). Secondary buttons stay simple on purpose.
- **`GameLogo` reskinned** - gradient-filled chamfered badge instead of
  a flat rounded square, both in the sidebar and the window/taskbar
  icon.
- **`SidebarButton`'s selected-state accent bar** is now a gradient
  instead of a flat color.

**Not done in this pass, still queued next:** game-specific animations
(Snake's movement, Tic-Tac-Toe piece placement) - that's its own
focused follow-up, since each game's rendering needs individual
animation work rather than a shared component change. Party system and
spectator mode also remain queued.



**Real economy now.** Coins are earned by winning online matches
(never from Practice Mode games - Snake, Racing, Puzzle Quest all pay
0 by design, so there's no solo-farming loop), spent in a real Shop,
and tracked through full challenges with progress bars.

**The single admin-editable class**: `EconomyConfig.java` on the
server. Every win-reward amount, every challenge, and every shop item
lives there. Nothing else needs to change to rebalance the economy -
just edit that one file's data.

What this adds:

- **`Account` gained `coins` and `ownedItemIds`** - both persist to
  `gamehub_server_accounts.dat` alongside everything else already
  there (backward-compatible file format - old save files still load
  fine, just default to 0 coins / no items).
- **Real Shop** (`ShopPanel` rewritten) - fetches real items from the
  server, shows your real live balance, and Buy actually works
  (server-validated: can't buy what you can't afford, can't buy the
  same item twice).
- **Real Challenges** - full server-side progress tracking with
  DAILY/WEEKLY/permanent reset periods, shown with live progress bars.
  Completing one pays out automatically.
- **Match wins pay out** - `TicTacToeMatch` now calls
  `EconomyManager.awardWin(...)` when there's a winner (draws pay
  nothing), which awards coins AND updates any matching challenge
  progress in the same step. Forfeiting by disconnecting/leaving counts
  as a loss for you and a win for the opponent.
- **Live updates everywhere** - `TopBar`'s coin display and
  `ProfilePanel`'s Coins stat both update instantly when you win a
  match, via the same push pattern chat/matches already use
  (`WALLET_UPDATE`).

**Known limitations, on purpose for now:**
- Challenge progress is in-memory only - lost on server restart (same
  limitation as chat/matches). Coins and owned items DO persist
  properly, since they live on `Account` itself.
- Purchased username colors aren't visually applied anywhere yet
  (chat, profile display, etc.) - the economy/ownership side is fully
  real, the "make it show up" visual wiring is a follow-up.
- No group management actions, no moderation - still backlog.

**Still queued** (explicitly deferred to keep this from becoming
another Phase-8-sized single response): Party/lobby system, spectator
mode (watch others' live matches), and the Opera GX reskin +
animations. These come next, one at a time.



**All of Phase 9 is done this round** - General Chat (already live),
plus Private Messages, Group Chats, and a real Notification Centre.
Moderation tools (mute/kick/ban) are the one piece still deferred to
Phase 14, since they need the Admin/Moderator panels to actually do
something, not just show placeholder cards.

What this adds on top of General Chat:

- **`ChatPanel` redesigned** with a channel sidebar - General, plus a
  DM entry per active conversation, plus a Group entry per group
  you're in. All three channel types share the same message-rendering
  and send logic; only the outgoing `MessageType` differs
  (`CHAT_MESSAGE` / `PRIVATE_MESSAGE` / `GROUP_MESSAGE`).
- **Private messages** - `+ DM` button, enter a username, starts a
  conversation. Routed account-to-account by username lookup
  server-side (Section 23's account-ID-based principle is
  approximated here via username, since usernames are still unique and
  simpler to route by than IDs for this stage - worth revisiting once
  Friends (Phase 10) needs real account-ID relationships).
- **Group chats** - `+ Group` button, name it, list usernames to add
  (comma-separated). Creator becomes the owner (tracked server-side,
  not yet used for any permission checks - no rename/kick-member/
  delete-group actions built yet). Only members who are online *at
  creation time* actually get added - no offline invite/persistence.
- **Real Notification Centre** (`NotificationCenter`) - the bell in the
  top bar now shows genuine unread counts and real notifications: a
  new DM (when you're not already viewing that conversation) or being
  added to a group. General chat deliberately does NOT generate
  notifications - it would be far too noisy.

**Known limitations, on purpose for now:**
- No message history/persistence anywhere - every channel starts blank
  each session, DMs to offline users simply aren't delivered.
- No moderation (mute/kick/ban) - Phase 14.
- No rate limiting beyond a 500-character cap per message.
- Group membership can't be changed after creation yet (no
  add/remove-member, no rename, no delete).



The "no Play button" mystery is solved. It was never an exception -
the debug logging confirmed the server data was always correct
(`comingSoon=false`). The actual bug: `GamesPanel`'s game cards had a
fixed height (220px) that wasn't tall enough for their real content
(art + name + type + status pill + Play button). Swing doesn't expand
a container to fit its children - it just compresses everything into
the space given, which pushed the Play button outside the visible
card entirely. No crash, no error, just an invisible/unreachable
button. Fixed by giving the cards enough room (220→320px), and applied
the same defensive fix to `ShopPanel`, `AdminPanel`, and
`ModeratorPanel`'s cards, which had the same tight-margin risk.

## Diagnostic patch (error visibility, kept - still useful going forward)

After several rounds of "Tic-Tac-Toe won't launch" with no error visible
anywhere, two safety nets were added instead of guessing further:

- **`GamesPanel`'s Play button** now wraps game-launch code in
  try/catch - if `SnakeWindow`/`TicTacToeWindow` construction throws
  anything, a real error dialog shows the exception instead of the
  button silently doing nothing. Also prints each card's
  `gameId`/`comingSoon` values to the console for debugging.
- **`Vertex.main()`** installs a last-resort uncaught exception
  handler (`Thread.setDefaultUncaughtExceptionHandler`) - any exception
  that slips through anywhere else now shows a plain error dialog
  instead of vanishing.

Next time something fails, it should be visible on screen, not silent -
whatever that dialog says is the real next thing to fix.

## Status: Phase 1-7 ✅ + Phase 8 ✅ (Multiplayer - Tic-Tac-Toe Online)

**Post-Phase-8 bug fix (Swing threading):** the Games page was loading
empty. Root cause: `GameManager.refresh()` runs on a background thread
(so the UI doesn't freeze while waiting on the network), but it was
notifying listeners - including `GamesPanel` rebuilding its grid -
*directly on that background thread*. Swing components must only be
touched from the Swing event thread; updates from any other thread can
silently fail to render instead of crashing, which is exactly what
looked like an empty page. Fixed: both `GameManager.notifyListeners()`
and `NetworkManager.setState()` (same bug, affecting the connection
indicator) now dispatch via `SwingUtilities.invokeLater(...)` before
touching any UI. `TicTacToeWindow` already did this correctly for match
messages - this fix brings the other two in line with that pattern.


**Admin account note:** account IDs now start at 1 (displayed
zero-padded, e.g. `000001`) instead of `100000`. The bootstrap-admin
account is now cleanly `000001` for whoever creates the very first
account. **No password is hardcoded anywhere** - if you want a specific
admin username, just be the one who creates the first account through
the normal Create Account screen. Hardcoding a real credential into
source code was declined - see the conversation for why (Section 33,
plus the practical risk of that credential ending up in a shared file
forever).

**Real multiplayer now works, proven with 2-player online
Tic-Tac-Toe.** This required restructuring `NetworkManager`: the old
version only supported "ask a question, wait for the answer." Real-time
match play needs the server to push messages the client didn't
explicitly ask for (e.g. "your opponent moved") - so `NetworkManager`
now runs a background listener thread that routes incoming messages
either to whoever's waiting for a direct reply, or to a registered
`MatchListener` for live match events.

What Phase 8 adds:

- **`NetworkManager` rework** - `send()` (blocking request/response, for
  Login/Create Account/Game List/account changes) and `sendAsync()` +
  `MatchListener` (fire-and-forget + server push, for match play)
- **`TicTacToeGame`**, **`TicTacToeWindow`** - matchmaking screen, then
  a live 3x3 board driven entirely by server messages. The client never
  decides who won - it only displays what the server confirms.
- Server gained **`MatchManager`** (matchmaking queue + active matches)
  and **`TicTacToeMatch`** (authoritative board, turn enforcement, win
  detection) - the server rejects out-of-turn or invalid moves and
  computes the winner itself (Section 13/33), never trusting a
  client-reported outcome.
- **Disconnect handling**: if a player closes the app or loses
  connection mid-match, the server notifies the opponent
  (`MATCH_OVER` / `"OPPONENT_LEFT"`) and cleans up the match, rather
  than leaving them stuck waiting forever.
- `GameRegistry` gained `tictactoe-online` as a real, playable
  multiplayer entry.

**Known simplification:** `NetworkManager` supports only ONE active
`MatchListener` at a time (a single global slot), which is fine while
only one multiplayer game window is open at once - revisit if Vertex
ever needs multiple simultaneous live matches in different windows.

**Deferred:** the Opera GX-style reskin (gradients, angular shapes,
glow animations, 8-10 theme modes) was requested but explicitly
postponed until after this phase - logged in
`GAMEHUB_ALL_IDEAS_v4.md` Section 13.

**Snake is playable.** Built fresh (no old code to convert), fully
theme-aware, launched from the Games page for real. This is the first
game that actually proves the whole pipeline end-to-end: `Game`
interface (Phase 6) → `GameRegistry` on the server → `GameManager` on
the client → a real launchable window.

What Phase 7 adds:

- **`SnakeGame`** - pure game logic/state, implements `Game`. Two
  modes: Classic (walls kill you) and Wrap-Around (pass through to the
  other side). Speed increases gradually as the score grows.
- **`SnakePanel`** - rendering + keyboard input (arrows or WASD).
  Every color comes from `ThemeManager` - switch themes mid-game and
  the board recolors live, same as everywhere else in the app.
- **`SnakeWindow`** - standalone window: mode-select screen, then
  gameplay, hosted in the same custom-dialog/themed-button style as
  the rest of Vertex (not default Swing look anywhere).
- **`SnakeGameOverDialog`** - themed Game Over popup with Play Again /
  Close, reusable pattern for other games' game-over screens later.
- `GamesPanel`'s Play button now special-cases `gameId.equals("snake")`
  to actually launch it; every other game still shows the honest
  "hasn't been converted yet" notice.
- Server's `GameRegistry` now lists Snake as a real (`comingSoon =
  false`) entry, alongside the remaining placeholder games.

**Not yet wired:** coins for playing, and cross-device save/resume
(`saveState()`/`loadState()` are implemented but not connected to
anything - that's Phase 11 and Phase 12 respectively). The full game
catalog and build-order plan lives in `GAMEHUB_ALL_IDEAS_v4.md` Section
12 - Snake was chosen as the simplest proof-of-concept; the next game
picks up from that list whenever you're ready.

**The Games page is now real.** It fetches its list from the server's
`GameRegistry` over the network via `GameManager`, instead of hardcoded
mock data. Refresh actually re-fetches. The games themselves are still
placeholders - nothing is playable yet, that's Phase 7 (the first
converted game).

What Phase 6 adds:

- **`GameInfo`** - SHARED (Common) class describing one game, sent from
  server to client (same pattern as `Account`)
- **`GameManager`** - client-side cache of the game list; `refresh()`
  is a blocking network call, always run from a background thread
- **`GameRegistry`** (server-only) - the authoritative game list.
  Currently seeded with the same 5 placeholder games as before, but now
  served over the network instead of hardcoded in the client
- **`Game`** - the interface every real game will implement, starting
  in Phase 7. Nothing implements it yet - it's scaffolding so that
  phase has a concrete contract to build against, including
  `saveState()`/`loadState()` for the cross-device sync planned in
  Phase 12
- Play buttons now correctly say a game "hasn't been converted yet"
  instead of referencing a system that's now actually built

**This is the big structural phase.** Vertex is now genuinely two
projects, as the architecture always intended:

- **GameHubClient** (this project) - the UI, no accounts stored here at
  all anymore
- **GameHubServer** (separate project, see its own README) - the real,
  authoritative account store (Section 10)

**You must run the server before the client can log in.** There is no
local fallback anymore - accounts only exist on the server now. Start
`ServerMain` (in GameHubServer) first, then run `Vertex` (in this
project). The login screen shows a live connection indicator so it's
obvious if the server isn't reachable.

What moved / changed:

- `AccountManager.java`, `AuthenticationManager.java`,
  `PasswordHasher.java` - **removed from this project entirely**, moved
  to GameHubServer (as `ServerAccountStore` + `PasswordHasher`). The
  client no longer hashes or stores a single password - it sends the
  plain password over the socket and the server verifies it.
- **New shared (Common) classes** - `Account`, `Role`, `Message`,
  `MessageType`, `NetworkConfig` - identical copies exist in both
  projects. If you change one, copy it into the other manually (the
  chosen approach from the original architecture discussion).
- **New `NetworkManager`** - the client's one persistent socket
  connection to the server. `send(...)` blocks, so it's always called
  from a background thread (see `LoginPanel`/`CreateAccountPanel`),
  never the Swing event thread.
- **`ConnectionIndicator` is finally real** - Sidebar, Settings, and the
  Login screen all reflect `NetworkManager`'s actual state
  (CONNECTING/ONLINE/OFFLINE/RECONNECTING) instead of a hardcoded
  OFFLINE placeholder.
- Login lockout and the first-account-becomes-ADMIN bootstrap both moved
  server-side (`ServerAccountStore`) - the client just displays what the
  server decided.

**Known limitation, on purpose for now:** plain sockets, no TLS.
Passwords travel unencrypted across the LAN during this phase. That's
acceptable for a trusted school-lab network but must not go
internet-facing as-is - TLS is explicitly a later addition when
internet play (Testing Step 6) gets built.

**Phase 4 note:** since there's still no server, this phase has the same
tension Phase 3 did - "server-side permission checks" (Section 13)
can't really exist yet. What's built now is **client-side role display
only**: Admin/Moderation nav items show or hide based on the logged-in
account's role. This is explicitly a UI convenience, not security -
every class involved (`PermissionManager`, `AdminPanel`,
`ModeratorPanel`) says so in its comments. The real enforcement has to
be rebuilt server-side in Phase 5 - the server must never trust what
the client claims about its own role.

What Phase 4 adds:

- **`PermissionManager`** - centralizes `isAdmin(account)` /
  `isAtLeastModerator(account)` checks instead of scattering raw role
  comparisons across the UI
- **Admin bootstrap**: the very first account ever created on a machine
  becomes `ADMIN` automatically (a deliberate one-time step, not a
  seeded default password - Section 33). Every account after that
  defaults to `PLAYER`. There is still no way to *promote* an existing
  account to admin/moderator - that's a Phase 14 (Admin Tools) feature.
- **Admin Panel** and **Moderator Panel** pages - mock section cards
  only, shown in the sidebar only to accounts with the right role
- **Username-change role persistence confirmed**: role lives on
  `Account`, completely independent of the `username` field (Section
  12), so changing your username in Settings never touches your role -
  this was already true structurally since Phase 3, just noted here as
  validated.

**Phase 3 note:** this reverses an earlier decision. The plan was to
wait for the real server (Phase 5) before building Login, but the call
was made to build it now anyway, using a **temporary local file-based
account store**. This entire storage layer gets replaced in Phase 5 -
Section 10 requires real accounts to be server-stored, and nothing in
this local store is trustworthy the way a real deployment needs. The
Login/Create Account **screens** themselves should carry over mostly
unchanged once that swap happens - only what verifies the password
changes.

What Phase 3 adds:

- **Login screen + Create Account screen** (`AuthWindow`, `LoginPanel`,
  `CreateAccountPanel`) - the app now starts here, not at MainMenu
- **Permanent numeric account IDs** (`AccountManager`, starting at
  100000) - usernames can change without ever touching this ID
- Passwords are **salted + SHA-256 hashed** (`PasswordHasher`), never
  stored or compared in plain text
- **Login lockout** after 5 failed attempts per username
  (`AuthenticationManager`) - the admin-security idea from earlier,
  applied to all accounts for now since there's no role distinction in
  the lockout logic yet
- New accounts always default to `PLAYER` - there is deliberately no
  signup path to `ADMIN`
- `Session` holds who's currently logged in and notifies `TopBar` /
  `ProfilePanel` to refresh automatically on login/logout/username
  change
- **Change Username** and **Change Password** now actually work
  (`ChangeUsernameDialog`, `ChangePasswordDialog`) - password change
  requires re-entering the current password first
- **Log Out** button in Settings, returns to the Login screen

Storage file: `gamehub_local_accounts.dat`, created next to wherever
the project runs from. Delete it to reset all local accounts.

Still not here: real server-backed accounts, role enforcement
(Phase 4), and an actual admin-account creation path (currently no way
to become ADMIN at all - intentional, until Phase 4 designs how that
should work).

What exists right now:

- Main application window (`Vertex` → `MainMenu`)
- Sidebar navigation (Games / Chat / Shop / Profile / Settings)
- Top bar with page title, notification bell, placeholder account info
- **Custom-drawn logo** (`GameLogo`) - no external image file anywhere;
  also used as the window/taskbar icon
- A full **theming system** (3 starter themes, switchable at runtime,
  no picker UI yet — that's Phase 17)
- **All 5 pages now have real content** (mock data, nothing wired to a
  server yet):
  - **Games** — card grid, Refresh button (shows a themed notice)
  - **Chat** — message area + input row (Send shows a themed notice)
  - **Shop** — username color grid (Buy shows a themed notice)
  - **Profile** — account card + stat grid, all mock values
  - **Settings** — startup toggle, current theme readout, connection
    status, account placeholder buttons
- **Connection indicator** (`ConnectionIndicator`/`ConnectionState`) —
  supports CONNECTING/ONLINE/OFFLINE/RECONNECTING, defaults to OFFLINE
- **Notification bell** in the top bar — opens a themed popup of mock
  notifications (real Notification Centre backend is Phase 9)
- A set of reusable themed building blocks now exist for later phases
  to reuse: `ThemedButton`, `ThemedTextField`, `GameHubDialog` (replaces
  `JOptionPane` everywhere), `StatusDot`, `StatusPill`, `ToggleSwitch`

What's deliberately NOT here yet:

- **No Login/Create Account screen.** Considered for this phase (shared
  lab computer), but the decision was to wait until the real server
  (Phase 5) exists rather than build a temporary local-only account
  store. The app shows a "Guest" placeholder in the top bar for now.
- No networking, no server, no real games — every button that would
  need one shows a themed "this arrives in Phase X" notice instead
- No theme picker UI in Settings (the system underneath is done; the
  picker screen is Phase 17)

## BlueJ version

Confirmed: **BlueJ 5.5.0**. Nothing in this codebase needs anything
special for that version — it's plain Java with no external libraries,
which is exactly what BlueJ compiles most easily. No project settings
beyond a normal new BlueJ project are needed.

- **`GameCardData`, `ShopItemData`** mock data holders were removed as
  each area went real (Games in Phase 6, Shop in the Economy pass) -
  if you see references to them elsewhere, they're stale.

## How to run this in BlueJ

**Recommended - one combined program:**
1. Open the `GameHubServer` folder as a BlueJ project, compile all
   classes.
2. Right-click `ServerMain` → `void main(String[] args)` → leave
   blank. The server starts AND a login window opens in the same
   process - log in (or create your account). If no admin exists yet
   on this server, the account you create *from this loopback
   connection* automatically becomes admin - a remote player joining
   first can never accidentally grab that role (see the Status section
   above).

**To add more players**, open `GameHubClient` as a separate BlueJ
project (or run it on another computer on the LAN), compile, and run
`Vertex` → `main`. It connects to whatever `NetworkConfig.SERVER_HOST`
points to (defaults to `"localhost"` - change it for a non-local
server).

## Class overview

| Class | Purpose |
|---|---|
| `Vertex` | Client-only entry point (`main`) - NOT present in `GameHubServer`, since `ServerMain` there does this job combined with starting the server |
| `MainMenu` | Main window; wires Sidebar + TopBar + page switching; sets the window/taskbar icon |
| `Sidebar` | Left navigation column - icon-only collapsed, animates open to show labels on hover |
| `SidebarButton` | Single nav entry - icon always visible, label shown only when Sidebar is expanded |
| `NavIcons` | Flat Graphics2D glyph icons for each nav entry (gamepad, quest flag, chat bubble, shop bag, profile, gear, shield) |
| `TopBar` | Header bar: page title, username, live coin balance, notification bell |
| `GameLogo` | Custom-drawn hexagonal "G" hub mark; also renders the window/taskbar icon |
| `Pages` | Page key constants |
| `PageHeader` | Shared page title header with accent-gradient underline, used across all pages for consistent launcher chrome |
| `NavigationListener` | Callback interface: Sidebar → MainMenu |
| `GamesPanel`, `QuestsPanel`, `ChatPanel`, `ShopPanel`, `ProfilePanel`, `SettingsPanel` | The six real pages - `GamesPanel` now has Home/All-Games tabs and filter chips; `ProfilePanel` has a real Coin Transaction History card (Phase 11) |
| `PinnedGamesStore` | Local "Quick Play" pin list (`java.util.prefs.Preferences`) - no server round-trip |
| `RoundedPanel` | Reusable themed rounded-corner panel - `glow()` and `enableTopAccent()` are opt-in reskin extras used broadly across cards |
| `GlowBackdrop` | Soft ambient corner glow painted behind hero screens (Login/Create Account) |
| `UITheme` | Fonts and spacing constants (NOT colors) |
| `Theme` | Interface every color theme implements, including the gradient pair |
| `ThemeManager` | Holds the active theme + the registry of all available themes; components look up colors through this |
| `ThemeColor` | Enum of color "roles" |
| `DarkNavyTheme`, `MidnightPurpleTheme`, `OceanTheme`, `CrimsonRedTheme`, `ToxicGreenTheme`, `SunsetOrangeTheme`, `CyberpunkPinkTheme`, `IceBlueTheme`, `BloodMoonTheme`, `GoldRushTheme` | The 10 themes |
| `ThemeDropdown` | Dropdown theme picker used in Settings - replaced the old grid |
| `GameCardArt` | Gradient chamfered card header art, used on every game card - also exposes `paintIconOnly(...)` so HeroBanner can reuse the icons at scale |
| `HeroBanner` | Large featured-game banner at the top of the Games page - launcher home-screen treatment |
| `GameLauncher` | Shared game-launch logic used by both GamesPanel and QuickPlayDropdown |
| `QuickPlayDropdown` | Top bar quick-play dropdown - launch any game without visiting the Games page |
| `ThemedScrollBarUI` | Custom themed scrollbar, applied to every JScrollPane in the app |
| `ChamferShape` | Shared chamfered-rectangle geometry builder |
| `HoverGlowAnimator` | Reusable smooth glow fade-in/out on hover/focus, used throughout the reskin |
| `AuthHeader` | Branded login/create-account header - logo with a continuous pulsing glow, wordmark |
| `ThemedButton` | Primary buttons: gradient fill, chamfered corners, animated glow. Secondary: simple flat/outlined |
| `ThemedTextField`, `ThemedPasswordField` | Themed inputs, glow on focus |
| `GameHubDialog` | Shared themed modal (replaces `JOptionPane`) |
| `StatusDot`, `StatusPill` | Small status dot / colored pill (game status, roles, etc.) |
| `ToggleSwitch` | Themed on/off pill switch |
| `ConnectionIndicator`, `ConnectionState` | Connection status dot + label (CONNECTING/ONLINE/OFFLINE/RECONNECTING) |
| `NotificationBell` | Top bar bell - real live notifications from `NotificationCenter` |
| `Account`, `Role` | SHARED (Common) - account data model (incl. coins, owned items) and role enum, identical copy in GameHubServer |
| `Session` | Holds the logged-in account; notifies listeners on change |
| `AuthWindow`, `LoginPanel`, `CreateAccountPanel` | Login/Create Account screens shown before MainMenu |
| `ChangeUsernameDialog`, `ChangePasswordDialog` | Account management dialogs used from Settings |
| `PermissionManager` | Centralized role checks (client-side UI only, not security) |
| `AdminPanel` | Role-gated page, glowing section cards - no longer reachable from the Sidebar (Admin nav entry removed as redundant with Moderation), file left in place but unreferenced |
| `ModeratorPanel` | Role-gated page - real Mute/Kick/Ban on the online-players list, real Ban/Unban on the all-players list, and a live Reports queue with Resolve buttons - all server-verified role checks |
| `NetworkManager` | Client's connection to the server - one persistent socket, reconnect logic, multi-listener push routing |
| `Message`, `MessageType` | SHARED (Common) - the client-server protocol, identical copy in GameHubServer |
| `NetworkConfig` | SHARED (Common) - server host/port, max chat file attachment size, identical copy in GameHubServer |
| `GameInfo` | SHARED (Common) - one game's metadata, identical copy in GameHubServer |
| `GameManager` | Client-side cache of the game list, fetched from the server |
| `Game` | Interface every real game implements |
| `SnakeGame`, `SnakePanel`, `SnakeWindow`, `SnakeGameOverDialog` | Snake - logic, smoothly-interpolated rendering, window, game-over screen |
| `TicTacToeGame`, `TicTacToeWindow`, `TicTacToeCellButton` | The first real multiplayer game - matchmaking straight into a single match (no round selection - removed after causing confusion in real online play), live board driven by server pushes, animated cell reveal, plus a local Practice Mode vs AI |
| `WinLineOverlay` | Draws the actual strike-through line through the winning three cells, layered above the board grid |
| `TicTacToeAI` | Local win-detection + a beatable medium-difficulty opponent, no network involvement |
| `TicTacToePracticeMatch` | Local best-of-N series vs the AI - mirrors the server match's shape so the window can reuse the same rendering |
| `RacingGame`, `RacingPanel`, `RacingWindow` | Lane-based dodge runner with a set finish line - Online mode is 3-6 players, ranked by finish time (or distance survived if crashed), 1st/2nd/3rd earn coins; car color follows your purchased username color |
| `PuzzleQuestGame`, `PuzzleTileButton`, `PuzzleQuestWindow` | Real single-player game - classic 4x4 sliding 15-puzzle, always-solvable shuffle |
| `RockPaperScissorsWindow` | Rock Paper Scissors vs a random-pick AI - single file, no animation loop needed |
| `PongGame`, `PongPanel`, `PongWindow` | Ping Pong vs AI - beatable capped-speed AI paddle |
| `Merge2048Game`, `Merge2048Window` | 2048 - theme-aware tile coloring, not hardcoded brand colors |
| `DinoGame`, `DinoWindow` | Chrome Dino-style runner - Space/Up to jump, speed ramps up over time |
| `AimTrainerWindow` | Click the target before it times out - 20 rounds, single file |
| `CrossingRoadGame`, `CrossingRoadPanel`, `CrossingRoadWindow` | Frogger-style lane crossing - continuous, resets with faster traffic after each crossing, ends on crash |
| `TetrisGame`, `TetrisPanel`, `TetrisWindow` | Classic falling-block puzzle - 7 tetrominoes, basic rotation, line clearing, next-piece preview |
| `AmongUsWindow` | Round-based social deduction (4-8 players) - secret roles, task checklist, kills, meeting/voting, win conditions - not a live map, see the Status section for why |
| `FightArenaWindow`, `FightArenaPanel` | Real-time synced brawler - 1v1/2v2/3v3/Chaos Mode FFA, server-authoritative tick loop, melee only, no local physics prediction |
| `ChessWindow` | Online-only chess - standard rules minus castling/en passant, check/checkmate/stalemate, auto-queen promotion, Unicode piece glyphs (no image assets) |
| `GameModeCard` | Shared big-tile mode-select card used by Racing/Tic-Tac-Toe/Fight Arena |
| `GameInviteDialog`, `GamePickerDialog` | Invite-to-game - Join/Dismiss popup for the recipient, game picker for the sender |
| `ChatPanel` | Real Chat - General, Private Messages, Group Chats, and file attachments (2MB cap, never stored server-side), sidebar-driven |
| `FriendsPanel` | Phase 10 - friend requests (send/accept/decline), friend list with live online/offline presence, Notification Centre integration, "Message" button per friend opens `FriendChatDialog` |
| `FriendChatDialog` | Focused 1:1 chat with one friend, opened directly from Friends - reuses the same PRIVATE_MESSAGE protocol as Chat's DM tab, fully interoperable with it |
| `NewDirectMessageDialog`, `NewGroupDialog` | Start a DM / create a group chat |
| `ReportPlayerDialog` | Real report submission - feeds ModeratorPanel's Reports queue, themed like the other dialogs (not a raw JOptionPane) |
| `NotificationCenter` | Real notification storage, feeds the top bar bell |
| `QuestsPanel` | Dedicated Quests tab - full challenge list with progress bars, color-coded by reset period |
| `PlayerColorRegistry` | Resolves a purchased color ID to its real `Color`, used anywhere a username is rendered |
| `GuestPlayTracker` | Queues Snake plays made while not logged in (offline/guest), flushed to the server on next real login |
| `OfflineHubWindow` | What "Play Offline" actually opens - a real grid of offline-capable games, not a hardcoded jump to Snake |
| `SplashScreen` | Animated loading screen shown at startup - logo, glow, progress fill, before the login window |
| `QuestRow` | Shared quest row - full detail (Quests page) and compact (Sidebar mini-list) modes |
| `ShopPanel` | Real economy - live balance, real purchasing (Quests moved out to their own page) |
| `ShopItemInfo`, `ChallengeProgressInfo` | SHARED (Common) - shop item and challenge data, identical copy in GameHubServer |

## About the logo / taskbar icon

`GameLogo` draws its mark entirely with `Graphics2D` shapes (a
hexagonal gradient "hub" silhouette with a background-colored "G"
letterform cut into it) — no `.png`/`.jpg` file is loaded from disk
anywhere. The same drawing logic is reused two ways:

- As a live component in the sidebar (theme-aware, recolors if the
  theme changes)
- Via `GameLogo.renderIcon(size)`, which renders the same mark into a
  `BufferedImage` used for `JFrame.setIconImage(...)` — this is what
  shows in the window's title bar and in the Windows taskbar while the
  app is running.

**Important distinction:** setting the window icon is not the same as
being able to *pin* the app to the taskbar. Pinning a bare `.jar` works
poorly on Windows. Real pinning needs the app packaged as an actual
`.exe` with an embedded icon — that's Phase 16 (Final Packaging), done
with `jpackage` after BlueJ, not something BlueJ itself produces. The
icon built now will carry straight into that packaging step.

## Admin account security

- **Never trust the client** for permissions or data (core rule,
  Section 13/33) - every privileged action is re-checked server-side,
  not just hidden in the UI. Implemented throughout.
- **Login lockout** after 5 failed attempts - implemented
  (`ServerAccountStore`).
- **No hardcoded/default admin password ever.** Admin is granted to
  whichever account is first created via a loopback connection (i.e.
  through the combined `ServerMain` program) - see the Status section
  above for the full reasoning. Nothing is baked into the source code.
- Passwords salted + SHA-256 hashed, never stored or transmitted in
  plain text (transmission is still plain-text-over-socket at the
  transport level, though, since there's no TLS yet - see "Networking
  scope" below).
- **Not yet built:** an audit log of admin actions (who did what,
  when) - still a backlog item.

## Server/Client split

`GameHubClient` is the **client-only** project - what you'd run on a
computer that's just playing, not hosting.

`GameHubServer` now contains BOTH the server logic AND a full copy of
the client's UI classes, so `ServerMain` can start the server and open
a login window in the same process (see "How to run" above). This
means `GameHubServer` and `GameHubClient` share almost their entire
codebase now, not just the small `Account`/`Role`/`Message`/
`MessageType`/`NetworkConfig`/`GameInfo`/`ShopItemInfo`/
`ChallengeProgressInfo` set from earlier phases - **if you change any
client UI file, copy it into `GameHubServer` too**, or the two will
drift out of sync. `GameHubServer` additionally has its own
server-only classes (`ServerMain`, `GameServer`, `ClientHandler`,
`ServerAccountStore`, `GameRegistry`, `MatchManager`, `TicTacToeMatch`,
`ChatManager`, `GroupChatManager`, `EconomyConfig`, `ChallengeManager`,
`EconomyManager`, `GameHistoryManager`, `PasswordHasher`) that
`GameHubClient` never needs.

## Networking scope

LAN-first (school computer lab). Internet play (for friends at home) is
a deliberately later addition — don't add TLS/internet-facing code
until that phase is reached.

---
*Keep this file updated whenever architecture changes.*
