# GAMEHUB — ALL PROJECT IDEAS (v4 Consolidated)

Every feature discussed so far, in one place. Organized by category, with
a rough phase reference (see `GAMEHUB_ROADMAP_v3.md`) so nothing gets
lost. Nothing here is built yet except what's explicitly marked (Phase 1
UI shell + theme system).

---

## 1. Core Platform (original scope)

- One GameHub application, many games inside it
- Modular `Game` abstraction, shared platform systems
- Server-stored accounts, **permanent account ID** (never the username)
- Changeable usernames, roles survive username changes
- Roles: PLAYER → MODERATOR → ADMIN/OWNER, enforced server-side only
- General chat, private messages (account-ID based)
- Player colors
- Shared server-controlled coins
- Main shop
- Refreshable game library (server tells client what's new/updated/removed)
- Game downloads and updates, version checking
- Admin panel, moderator panel
- Multiple controlled game servers under one main server
- Offline/practice modes, pending-reward sync on reconnect
- Desktop packaging: shortcut, Start Menu, taskbar, custom icon

## 2. Notifications, Chat & Friends (v2 additions)

- **Notification Centre** — server-generated, account-ID based, covers
  friend requests, DMs, group invites, mod actions, updates, purchases,
  daily rewards, achievements
- **Expanded chat**: General Chat + user-created **Group Chats** (own
  owner/member roles, separate from platform roles) + Private Messages
- **Friend system**: requests (pending/accepted/declined), friend list,
  online/offline presence — deferred until Accounts exist

## 3. Economy & Shop

- Wallet, transactions, server-validated rewards (reward IDs, anti-duplicate)
- Main Shop, purchase validation
- **Username/player colors are purchasable** — owned colors vs. one
  active/equipped color, applied everywhere a username renders
  (top bar, chat, profile, multiplayer)
- Daily login rewards / streaks
- Coin transaction history in Profile
- Seasonal/limited-time shop items

## 4. Profile & Account Management

- Username change (account ID never changes)
- Avatar: preset icons/colors first; custom image upload is a **separate,
  later, moderation-heavy feature** — deliberately deferred
- Password change (re-verify current password server-side)
- **Login screen + Create Account screen** — needed early since the
  computer is shared by multiple people; placeholder/mock until real
  Accounts (Phase 3) + Server (Phase 5) exist

## 5. Theming & Visual Identity

- ✅ **Built (Phase 1)**: full theme system — `Theme` interface,
  `ThemeManager`, 3 starter palettes (Dark Navy, Midnight Purple, Ocean
  Teal), every component theme-aware and auto-repaints on switch
- Theme picker UI in Settings (system exists, picker screen comes later)
- Colorblind-friendly theme (just another `Theme` implementation)
- **Custom-drawn logo** — no external image files, drawn with Graphics2D,
  reused as the app/taskbar icon
- Rule: **every** UI element (dialogs, dropdowns, lists, scrollbars,
  context menus, tooltips) must be themed — no default Swing look anywhere

## 6. Downloads, Sync & Persistence

- **All games auto-download in the background whenever online** (every
  game has an AI practice mode, so there's no "offline-capable" flag —
  it's just all of them)
- Local game cache + manifest (what's installed, what version)
- Updates apply the same way — quietly re-download changed games when online
- **Cross-computer progress sync**: not just coins/achievements, but
  **mid-game save state** too (pause on one computer, resume on another)
  — `Game` needs `saveState()`/`loadState()`, server gets a `SaveManager`
- Offline play queues unsynced progress; syncs on reconnect
  (last-write-wins if two computers have conflicting unsynced saves)

## 7. Trust & Safety

- Server never trusts client-reported coins, permissions, or purchases
- Passwords hashed, never stored/sent in plain text
- Private messages: if admins can access them, the UI must say so honestly
- **Multi-account prevention**: soft-flag, not hard-block — rate-limit
  account creation per IP/session, flag possible duplicates for a
  moderator to review (Admin Panel), rather than automated hard denial,
  since shared lab/home computers have legitimate multi-account reasons
- Report system — users flag messages/players, feeds the Moderator Panel
- Anti-cheat flagging for suspicious reward/coin patterns

## 8. Networking

- Configurable server address, never hardcoded IPs
- **Two separate BlueJ projects**: `GameHubClient` and `GameHubServer`
- Shared classes (`GameInfo`, message formats, account shape) via a
  third `GameHubCommon` reference set, manually copied into both projects
  when changed (chosen over duplicating blind or a jar-library setup)
- **LAN-first** (school computer lab) — internet-from-home play is a
  deliberate later addition (needs port forwarding/dynamic DNS or a
  cloud VM, plus TLS once it's internet-facing) — not built yet
- Connection states shown in UI: CONNECTING / ONLINE / OFFLINE / RECONNECTING

## 9. UX Polish Backlog

- Toasts for minor confirmations instead of modal dialogs
- Coin-earn "+50 coins" float-up animation after a match
- Recently played / pinned-favorite games on the Games page
- Skeleton loading states instead of blank screens while waiting on the server
- Command palette (Ctrl+K) to jump to any page/friend/game
- Collapsible icon-only sidebar for smaller windows
- Right-click context menus on friends/messages
- Rich presence ("Playing Square Wars") in the Friends list
- Unread badges per chat channel
- Explicit "Are you sure?" confirms for destructive actions (leave group,
  delete message, change password) — distinct from informational OK dialogs
- First-login onboarding flow (avatar pick, starter color, quick tour)
- Don't lose an in-progress chat draft on disconnect — resend on reconnect
- Cached/grayed-out game list when offline instead of a blank Games page

## 10. Social/Community Backlog

- Achievements/badges, `AchievementManager`
- Per-game leaderboards
- Block/mute individual users (user-level, distinct from moderator mute)
- Read receipts, typing indicators
- Emoji reactions on chat messages
- Global search (games, users, chat, shop)
- Guilds/clans — bigger, more persistent than group chats
- Party system — invite friends straight into a game lobby
- Referral bonus for bringing a friend onto the platform

## 11a. Project Housekeeping (do near the end, not mid-build)

- **Organize the BlueJ diagram via `package.bluej` positions — NOT Java
  packages.** Earlier plan was to split classes into real Java packages
  (`gamehub.app`, `gamehub.theme`, etc.), but that's been dropped —
  everything stays in the default package, no `package` declarations,
  no import changes. Instead, BlueJ's `package.bluej` file (plain
  properties: `targetN.x`, `targetN.y`, `targetN.name`, ...) controls
  where each class icon sits on the diagram canvas. Setting these
  directly lays classes out in visual groups (app/window classes,
  theme system, reusable UI components, pages, accounts/auth) purely
  as a canvas arrangement — zero effect on compilation or behavior.
  Deliberately deferred until the class list stabilizes, since
  repositioning now would just mean redoing it every time a new class
  gets added.

## 11. Reliability/Ops Backlog

- Crash/bug report button
- MOTD (message of the day) banner on login, powered by Announcements
- Match/game history & personal stats per account

## 12. GAME CATALOG (full wishlist, organized by build complexity)

Every game requested, sorted by how big a build each genuinely is - not
by importance. This determines a sane build order more than anything
else. All of these implement the `Game` interface (Phase 6) and get
`saveState()`/`loadState()` for cross-device sync (Phase 12) - that
part of the architecture doesn't change no matter which game it is.

### Small (good candidates to build FIRST - proves the Game interface end-to-end)
- Tic-Tac-Toe (vs AI, difficulty levels)
- Rock Paper Scissors (vs AI)
- 2048
- Snake (multiple modes)
- Ping Pong (vs AI initially; 1v1 online is a later, separate build)
- Chrome Dino-style endless runner
- Battleship (AI fine, online later)
- Crossing Road (Frogger-style)

### Medium
- Tetris
- Candy Crush-style match-3
- Generic puzzle games (need concrete ideas per puzzle later)
- Aim trainer
- Atari Dash-style runner
- Car Racing (single-player/AI first)
- Pac-Man
- Zombie Survival (wave-based, offline)
- Casino games (see note below - multiple mini-games: e.g. slots,
  blackjack, roulette-style, all staking shop coins)
- Paper.io-style territory game
- Hill Climb Racing-style physics driving game

### Large (each is genuinely a multi-phase project on its own)
- Mario-style platformer (levels, physics, enemies, sprite animation)
- Fighting Arena, scalable 1v1 up to 6v6 (real-time netcode-heavy)
- FFA mode (depends on which base game it's a mode of)
- Co-op games (needs a concrete design - "co-op" isn't one game yet)
- Among Us-style social deduction (roles, tasks, voting - overlaps
  heavily with the Chat system, likely needs its own voice/text layer)
- Pokémon-style creature collector/battler (sprites, battle system,
  world map, persistent save data - one of the biggest asks here)
- Terraria-style sandbox (procedural world, building, survival - this
  is one of the largest scope games that exists; realistically a
  multi-year undertaking even approximated, not a "phase")

### Architecture note: online games need offline versions too
Rather than building two separate implementations per online game, the
cleaner approach is decoupling *game logic* from *player input source*:
each game's core simulation doesn't care whether a "player slot" is a
human, an AI bot, or a network connection - just a `PlayerController`
interface with three implementations (`HumanController`,
`AIController`, `NetworkController`). Offline mode = fill empty slots
with AI. This means building the online version mostly gets you the
offline version for free, instead of maintaining two codebases per
game. Recommend adopting this as a rule for every multiplayer-capable
game, starting with whichever one is built first.

### Casino games & match betting - a note worth flagging
Betting shop coins in casino-style mini-games, and betting coins on the
outcome of other matches, is virtual-currency-only (no real money) -
same category as loot-box/wagering mechanics common in many mainstream
games, not real-money gambling. Since this platform is meant for a
shared school computer likely used by minors, it may be worth an
`Admin`-level toggle to disable casino/betting features per-server, so
it's the platform owner's (your) call whether that fits the intended
audience/environment - not something to hardcode as always-on.
"Betting on match outcomes" is its own small system (a prediction
ticket tied to a match ID) - logged here as a distinct feature, not
solved yet.

### Coins - default award framework (draft, tune later)
- Small games: ~5-20 coins per win/completion, scaled to session length
- Medium games: ~20-75 coins, scaled to difficulty/duration
- Large multiplayer games: ~50-150 coins per match, scaled to match
  length and outcome
- All awards go through the existing reward-ID validation system
  (Section 19) - the client reports "I finished a match," the server
  decides the payout, never the reverse
- Casino games: stakes and payouts calculated server-side only, same
  reasoning as Section 33 (never trust client-reported outcomes)



✅ Phase 1 UI shell: `GameHub`, `MainMenu`, `Sidebar`, `TopBar`, 5
placeholder pages, full theme system (3 palettes).

🔜 Being planned next: custom-drawn logo, taskbar icon, and the
Login/Create-Account screen restructuring (`MainMenu` becomes a panel
inside a new top-level frame that swaps Login ↔ Main).

Everything else above is designed and sequenced (see the roadmap) but
not yet built.

## 13. Deferred: Opera GX-Style Reskin (after current phase work)

Requested but explicitly deferred until after Phase 8 (Multiplayer):

- Full visual direction: gradients, angular/faceted panel shapes, and
  glow/hover animations - a genuinely large shift from the current flat
  rounded-panel look, touching `RoundedPanel`, `ThemedButton`, and
  likely every themed component.
- 8-10 total theme modes (currently 3: Dark Navy, Midnight Purple,
  Ocean Teal) - needs `Theme`/`ThemeManager` extended to support
  gradient definitions, not just flat colors, since Opera GX's
  signature look relies on gradient accents rather than solid fills.
- This is a genuinely large undertaking - plan to scope it in stages
  (e.g. gradient support in the theme system first, then angular shapes,
  then animations) rather than attempting it in one pass.

## 14. Additional Backlog Items (logged, not yet built)

- **Online player count** - show how many accounts are currently
  connected (e.g. in the sidebar near the connection indicator, or top
  bar). Needs the server to track connected `ClientHandler` count and
  a new message type (or push) for the client to display it live.
- **Recovery email (optional)** - lets a user set a recovery email
  for password-reset purposes. Optional field on account creation or
  added later via Settings. Ties into the account-recovery idea logged
  earlier in this document; not yet designed in detail.
