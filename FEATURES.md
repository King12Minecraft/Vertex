# Vertex — Full Feature List

This is the single up-to-date reference for what Vertex actually does today.
The `GAMEHUB_*.md` files elsewhere in this repo are the original planning
documents from early in the project — useful as history, but they predate a
lot of what's below and shouldn't be treated as current. This file is.

---

## Games (53 playable)

### Online multiplayer, ELO-rated
- **Chess** — full rules including castling, en passant, checkmate/stalemate. Resign or offer a draw mid-game. Spectate live matches, replay finished ones move-by-move.
- **Battleship** — classic hunt-and-sink, 1v1. Spectators see both fleets revealed; replays step through every shot.
- **Rock Paper Scissors** — best of 5, simultaneous blind moves. Spectator and replay support.
- **Connect Four** — classic 7×6 drop-and-connect, standard rules.
- **Checkers** — standard American rules: mandatory captures, mandatory multi-jump continuation, kinging.
- **Tic-Tac-Toe** — ranked online, or practice offline against a simple AI.
- **Fight Arena** — 1v1, 2v2, 3v3, or free-for-all. Queue solo or with a party (party always lands on the same team). 2v2/3v3 support a lightweight team tournament.

### Online multiplayer, placement/score-based (not ELO)
- **Racing** — 3–6 players, shared track, power-ups (shields, speed boosts, coin pickups). Ranked on a leaderboard; 1st/2nd/3rd earn coins.
- **Zombie Survival** — 2–4 players, co-op wave shooter. Everyone gets the same seeded zombie spawn sequence and fights it out independently; survive all 8 waves for the full coin reward.
- **Space Battle** — 3–6 players, arcade dogfight against asteroids/enemy fighters over a fixed time limit. Ranked by score; 1st/2nd/3rd earn coins.
- **Among Us** — round-based social deduction with a small group.
- **Trivia Blitz** — 2–6 players answer the same 8 general-knowledge questions; correct answers score points with a speed bonus, highest total wins. Disconnecting doesn't forfeit the match for everyone else - your score just stays locked in at whatever you'd earned (see `BLOCKED_QUESTIONS.md` for why this genuinely-different-shaped game hasn't adopted the 2-player reconnection grace period below).

### Solo tournaments
Battleship & Rock Paper Scissors only (both games always produce a clear winner): 4-player single-elimination brackets, browsable and joinable from the Tournaments page.

### Rematch
After any Chess, Battleship, or RPS match, challenge the same opponent again with one click.

### Online multiplayer, ELO-rated (added since the counts above were last updated)
- **Square Wars**, **Dots and Boxes**, **Reversi/Othello**, **Memory Match**, **Air Hockey**, **Word Duel**, **Dice Duel**, **Snake Arena**, **Competitive Tetris**, **Fusion Grid**, **Typing Duel**, **Signal Grid**, **Card Rush** — original 1v1 implementations, each ELO-rated the same way Chess/Battleship/RPS are. See each game's in-app rules (or `GameRules.java`) for how it plays.

### Online multiplayer, for fun (no scoring, no winner)
- **Telephone** — 4–8 players, a Gartic-Phone-style draw/guess chain. Everyone writes a starting phrase, then the phrase gets passed player to player, alternating "draw what you were just handed" and "guess what this drawing shows" each round, until every chain has gone all the way around the table. Ends with a reveal - step through every chain from its original phrase to its final, usually-mangled result. Purely social - everyone gets a flat coin reward just for playing, no ranking involved.

### Single-player (no server required; wins/scores still tracked if logged in)
Snake, Tetris, 2048, Number Nest, Pong, Dino Dash, Crossing Road, Puzzle Quest, Aim Trainer, Minesweeper, Sudoku, Simon Says, Whack-a-Mole, Gem Match, Maze Chase, Brick Breaker, Flappy Bird, Galaxy Defender, Word Guess, Bubble Shooter, Lights Out, Peg Solitaire, Klondike Solitaire, Yahtzee, Mancala (vs. a built-in AI), Rock Paper Scissors against a simple AI, **Hill Climb** — drive a simple two-wheel vehicle across procedurally-generated rolling hills, managed by a limited fuel tank; score is how far you get before running dry - and **Sky Hopper** — an original vertical climber (`Doodle Jump` genre): bounce up a procedurally-generated tower of platforms, some moving, some breakable, some one-time springs, without falling off the bottom of the screen; score is how high you climb. Both share an `engine` package (`GameLoop`/`Vector2`) for their physics/game-loop plumbing. Number Nest is an original number-merge puzzle distinct from 2048 - place one piece at a time into a 5x5 grid instead of sliding the whole board. Snake, Tetris, and Dino Dash support pausing (**P**). Every game — online or solo — now renders with a fixed color palette that ignores your app theme choice (see Customization below); only the previous 4 games (mostly the newest ones) had this problem before it was fixed platform-wide.

---

## Vertex: Dominion (early, V1 in progress)

A persistent, whole-server nation-building strategy game - not a match you queue
for and leave, a standing thing reached from its own permanent nav entry
(alongside Friends/Chat/Shop) that you check in on repeatedly. Time advances on
a daily tick (every 20 real minutes); orders queued during the day all resolve
together when it ticks. Full design and current build status:
[`DOMINION_DESIGN.md`](DOMINION_DESIGN.md).

What's actually usable today: found a nation on any unclaimed province (a small
seeded map), see a dashboard (treasury, Honor, current day) and a color-coded
map of every nation's territory, recruit armies, march them, declare war on
another nation (not possible while its ruler is offline - a fixed grace period
protects a nation whose player just isn't home right now), and propose or
respond to Alliance/Non-Aggression pacts with other nations. No push-based live
updates yet - a Refresh button re-fetches the current state instead. Everything
past this V1 slice (population, jobs, a real economy, currency, trade, laws,
diplomacy bodies, islands, navies, and a great deal more) is designed and
written down, not built yet - see the design doc's "Future depth" sections.

---

## Economy

- **Coins** from: every online multiplayer game listed above (win rewards or placement rewards depending on the game), every single-player game (score-scaled rewards, or a flat reward for Puzzle Quest which has no score concept), and daily login streaks.
- **Shop** — cosmetics: username colors and chat badges, purchasable with coins.
- **Achievements** — unlocked on real milestones (first win, win counts, coin totals, total plays, racing placement, surviving all Zombie Survival waves, 1st place in Space Battle/Connect Four/Checkers-adjacent games where applicable).
- **Leaderboards** — ELO ratings for rated games, best-score leaderboards for placement-based games.
- **High-score sharing** — after a genuine achievement (a good score, a win, a placement), copy a shareable summary to clipboard or send it straight to a friend as a chat message.

---

## Avatars

- **Upload an image** — pick a file, preview it circular-cropped, resize on save.
- **Paint one directly** — a real drawing canvas in-app: color swatches, custom color picker, adjustable brush size, click-and-drag painting.
- Both produce the same 128×128 PNG; shown today in Settings, cached client-side (`AvatarCache`) for reuse anywhere avatars are shown (chat messages, chat sidebar).

---

## Social & Chat

- **Friends** — add/search/filter, pin favorites to the top of the list, live badge when a friend comes online.
- **Forums** — a Forums tab in the sidebar with a board for every game plus General. Start a thread (a title and your first post), reply to others, and read everything even while logged out (posting needs a login). Text is limited to 100 characters for a title and 2000 for a post, and you get a clear message instead of a silent cut-off if you go over. Muted players can't post, and there's a limit of 3 posts per 30 seconds. Moderators and admins can lock a thread and delete replies or whole threads; those actions are recorded in the admin log. Not included yet: votes, editing posts, images, reply notifications.
- **Direct messages & group chats** — General Chat was removed entirely (client and server); Private Messages and Group Chats are the only channels now.
- **Chat UI (Discord-style)** — avatars next to messages (grouped: consecutive messages from the same sender within 5 minutes share one avatar/header instead of repeating it), timestamps, flush hover-highlighted rows instead of chat bubbles, a "#"-tile or avatar icon per sidebar entry, a rounded pill-style input bar.
- **Typing indicators** — "X is typing..." in DMs and groups, throttled and auto-hiding.
- **Unread indicators** — a dot on a conversation's sidebar icon when it has an unseen message.
- **File sharing** — attach a file via the Attach button, or drag-and-drop it straight onto the message list.
- **Notification sounds** — a short system beep on new DMs/group messages/invites (toggle in Settings).
- **Friends pre-populate DMs** — every friend shows up as a conversation automatically, not just ones you've already messaged.
- **Party system** — invites with a shareable, copyable code.
- **Notifications** — bell icon with a live feed, achievement unlock toasts, one-click "Clear All."
- **Game suggestions** — a community wishlist ("Suggest a Game" page) where anyone can pitch an idea in plain text; replaced the old upload-and-run-arbitrary-code custom games feature entirely.
- **Report a player** — flag another account for moderator/admin review.

---

## Admin & Moderation

- **Real Admin Panel** — Players (every account, promote to Moderator / revert to Player — never grants Admin, which is only created at the server console) and Admin Log (a genuinely human-readable audit trail of approvals, removals, role changes).
- **Moderators** — a real, usable role now, not just an enum value with no UI to grant it.
- **Staff chat colors** — Admin and Moderator usernames render in a fixed color in chat, overriding any purchased cosmetic color.
- **Feedback system** — bug reports and suggestions, submitted in-app, viewable in-app (own feedback for regular players, all feedback for admins), each entry timestamped.
- **Custom game review queue** *(historical note — this whole system was later removed in favor of Game Suggestions above; kept here for the record of what existed)*.

---

## Shared AI layer (in progress)

- **`ai` package** — a shared, reusable intelligence layer every game can plug into, instead of each game hardcoding its own bot logic. Lives alongside the other 9 packages (`net`, `account`, `theme`, `ui`, `pages`, `economy`, `social`, `admin`, `games`) and is kept byte-identical between `VertexClient/` and `VertexServer/` like everything else.
- **`AiKernel`** — the one shared entry point every game's AI goes through. A game registers a primary strategy (its real "smart" logic) together with a deliberately trivial fallback strategy under its own game ID; `AiKernel.chooseMove(...)` always tries the primary first and silently falls back if it ever throws, so a bug in one game's bot logic can never stall or crash a live match. The AI layer only ever *proposes* a move — the calling game's own already-validated logic is what actually applies it, so every game (and, for multiplayer, the server) stays authoritative over its own rules.
- **`BotStrategy<S, A>`** — the single interface every bot/opponent strategy implements, generic over whatever state type (`S`) and move type (`A`) a given game needs; no assumptions about board shape, turn order, or scoring baked into the interface itself.
- **`AiKernel.applySafely(...)`** — a lower-level, registry-free version of the same primary/fallback safety net, for a bot that needs its own private memory for a single match (like Battleship's shot history) rather than one long-lived shared instance.
- **Games migrated so far: Tic-Tac-Toe Practice Mode and Battleship's vs-AI opponent.** Tic-Tac-Toe's existing AI (`TicTacToeAI.pickMove`) is now a registered `BotStrategy`, with a "pick any open cell at random" fallback. Battleship's hunt/target AI is now wrapped the same way via `applySafely`, with its own trivial random-shot fallback — plus a ground-truth "already fired" reconciliation between the two, so a fallback-chosen shot can never later get double-fired by the primary. Both migrations verified with zero behavior change: 260 + 549 automated checks (exact-decision regression tests, hundreds of simulated full games, and reflection-driven scenario tests for the hunt/target and fallback logic specifically).
- **What's next** — this is the first slice of a longer-term plan: shared pathfinding/NPC behavior for arcade games, a Knowledge Engine for word/trivia games (replacing each game's own embedded word list), procedural map/level generation, and player-behavior analytics/difficulty scaling, all built on this same kernel. Not yet built — intentionally staged so each piece is proven before the next is added.

---

## Hosting

- **Hosting is a dedicated-server thing** — only `VertexServer.jar` (`ServerMain`) starts a server. `VertexClient.jar`, the program a player actually runs, has no "host a server" feature in it at all *(the in-app "Start Hosting" button and `HostServerDialog` were removed 2026-09-25 — nobody should be able to spin up a server just by clicking something in the regular client)*.
- **One server, one account store** — no cross-server syncing to reason about. Whichever server you connect to is the full source of truth for its own accounts, matches, and progress.
- **Multi-server sync** *(historical note — the "main server" + synced "satellite servers" system this section used to describe was removed; kept here for the record of what existed. Each server now just stands on its own, which also simplifies hosting one always-on, e.g. on a cloud VM.)*

---

## Customization & Performance

- **11 themes**, including an animated Glitch mode.
- **Games ignore theme choice** — every game renders with a fixed palette (`GameColors`) regardless of which of the 11 app themes is active; only surrounding UI chrome (menus, buttons, dialogs) still follows theme.
- **Performance Mode** (Settings toggle) — antialiasing off app-wide, in-game frame rate halved (60fps → 30fps) on every real-time game, a couple of always-running decorative background timers skipped.
- **FPS Counter** (Settings toggle) — live, color-coded frame-rate readout in the corner of every real-time game.
- **Mode-select memory** — the game mode you picked last time (e.g. "vs Player" vs "vs AI") is remembered per game.

---

## Quality of life

- **Global search** — a search box in the top bar; matches game names (launches directly) and friend usernames (navigates to Messages) as you type.
- **Escape closes every dialog** — all ~19 custom popups in the app, not just their own Close/Cancel button.
- **Auto-reconnect** — a dropped server connection is retried automatically.
- **Match reconnection (30 seconds)** — if your connection drops mid-match, the match pauses for up to 30 seconds instead of forfeiting, and the app logs you back in by itself and puts you straight back on the board. Covers Chess, Tic-Tac-Toe, Connect Four, Checkers, Reversi, Dots and Boxes, Word Duel, Battleship, Rock Paper Scissors, Dice Duel, Typing Duel, Memory Match, Signal Grid, Fusion Grid, Card Rush, Air Hockey, Snake Arena and Tetris Duel. Your opponent sees a live "waiting to reconnect (27s left)" countdown while you're gone, and your match chat comes back with you; a connection that dies silently (Wi-Fi off) is detected within about 25 seconds; in the real-time games (Air Hockey, Snake Arena, Tetris Duel) the game freezes and stays frozen for a few seconds after you're back so you can see what's happening. If the 30 seconds run out you forfeit as before. Clicking Leave is a deliberate choice and forfeits straight away. Guests don't get this (no account to log back in as), and the group games (Racing, Space Battle, Square Wars, Zombie Survival, Among Us, Telephone, Trivia Blitz, Fight Arena) are left as they were - see ROADMAP.md.
- **Confirm-before-close** on any active match, so an accidental click doesn't silently hand your opponent a win.
- **`/calc`** — type `/calc 2+3*4` in a DM, group chat or match chat and the answer appears on your screen only (nothing is sent to anyone). Supports + - * / % ^, brackets, decimals, pi and e, and sqrt, abs, round, floor, ceil, ln, log, sin, cos, tan, min, max.
- **Match chat** — in 14 online 1v1 games (Checkers, Connect Four, Reversi, Dots and Boxes, Word Duel, Dice Duel, Typing Duel, Air Hockey, Memory Match, Signal Grid, Fusion Grid, Card Rush, Snake Arena, Tetris Duel) and Telephone, a chat panel docks beside the game so you can talk to whoever you're playing. It can be collapsed to a thin strip (with an unread count) and stays open about a minute after the match for a "gg". Telephone's chat is locked while the draw/guess chain runs, so nobody can say the answer out loud, and opens when the reveal starts. Mutes and the chat flood limit apply as everywhere else.
- **Game details is a page, not a popup** — clicking a game (or Play from anywhere) opens its details page inside the app; Back (or Escape) returns to wherever you came from.
- **Mandatory screen break** — every 20 minutes of active screen time, a full-screen overlay forces a non-skippable 30-second look-away break before you can keep using the app; if you're mid-match it waits until the match ends rather than interrupting it. No way to dismiss or opt out early - it's a health nudge, not a suggestion.
- **Low-end hardware support** — `-LowEnd` launcher scripts (smaller heap, serial GC) plus Performance Mode above.
- **Auto-detecting launchers** — the `.bat` files find Java themselves (checking common install locations, BlueJ's own bundled JDK included) instead of requiring it on PATH.

---

## Account & Login

- **Login and Create Account screens** — redesigned as centered cards with an ambient dual-tone glow background, matching current dark-mode gaming-platform design conventions.
- **Daily login rewards**, streak-tracked.
- **First-run admin setup** — on a fresh server the operator chooses the Admin username and password at the server's own console (or via `VERTEX_ADMIN_USER`/`VERTEX_ADMIN_PASSWORD` for an unattended start). Signing up never grants Admin, so reaching a new server first gives no power; no other path grants that role.
