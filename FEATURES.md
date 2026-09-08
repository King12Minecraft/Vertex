# Vertex — Full Feature List

This is the single up-to-date reference for what Vertex actually does today.
The `GAMEHUB_*.md` files elsewhere in this repo are the original planning
documents from early in the project — useful as history, but they predate a
lot of what's below and shouldn't be treated as current. This file is.

---

## Games (19 playable, 1 coming soon)

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

### Solo tournaments
Battleship & Rock Paper Scissors only (both games always produce a clear winner): 4-player single-elimination brackets, browsable and joinable from the Tournaments page.

### Rematch
After any Chess, Battleship, or RPS match, challenge the same opponent again with one click.

### Single-player (no server required; wins/scores still tracked if logged in)
Snake, Tetris, 2048, Pong, Dino Dash, Crossing Road, Puzzle Quest, Aim Trainer, and Rock Paper Scissors against a simple AI. Snake, Tetris, and Dino Dash support pausing (**P**). Every game — online or solo — now renders with a fixed color palette that ignores your app theme choice (see Customization below); only the previous 4 games (mostly the newest ones) had this problem before it was fixed platform-wide.

### Coming soon
**Square Wars** — listed honestly as not yet built, rather than shown as playable with nothing behind it.

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

- **Real Admin Panel** — Players (every account, promote to Moderator / revert to Player — never grants Admin, which stays bootstrap-only) and Admin Log (a genuinely human-readable audit trail of approvals, removals, role changes).
- **Moderators** — a real, usable role now, not just an enum value with no UI to grant it.
- **Staff chat colors** — Admin and Moderator usernames render in a fixed color in chat, overriding any purchased cosmetic color.
- **Feedback system** — bug reports and suggestions, submitted in-app, viewable in-app (own feedback for regular players, all feedback for admins), each entry timestamped.
- **Custom game review queue** *(historical note — this whole system was later removed in favor of Game Suggestions above; kept here for the record of what existed)*.

---

## Multi-server sync

- **Anyone can host** — both `VertexClient` and `VertexServer` carry the full server engine.
- **One server is "main"** — first-come-first-served, password-locked once claimed.
- **Satellites** — run real games independently, but aren't the source of truth for accounts; delegate login to main on first sight, cache locally after.
- **Progress syncs back automatically** — coins, ELO, achievements push to main in the background; satellite keeps working if main is briefly unreachable.
- **Satellites never inherit admin.**

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
- **Confirm-before-close** on any active match, so an accidental click doesn't silently hand your opponent a win.
- **Low-end hardware support** — `-LowEnd` launcher scripts (smaller heap, serial GC) plus Performance Mode above.
- **Auto-detecting launchers** — the `.bat` files find Java themselves (checking common install locations, BlueJ's own bundled JDK included) instead of requiring it on PATH.

---

## Account & Login

- **Login and Create Account screens** — redesigned as centered cards with an ambient dual-tone glow background, matching current dark-mode gaming-platform design conventions.
- **Daily login rewards**, streak-tracked.
- **Bootstrap admin** — the very first account ever created on a fresh server becomes Admin automatically; no other path grants that role.
