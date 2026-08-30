# GAMEHUB — CONSOLIDATED ROADMAP (v3)

This merges the original v1 phase plan with the v2 additions (notifications,
expanded chat, friends, economy extras, profile management) and the feature
backlog into ONE ordered build sequence. This supersedes the separate v1
Section 34 and v2 Sections 42-49 for planning purposes — those sections still
hold the *design details*, this file just puts everything in the *order*
they get built.

Nothing here is being built yet. This is the plan.

---

## PHASE 1 — Project Foundation  *(in progress)*
    - GameHub project setup
    - README
    - Main application (GameHub.java)
    - Main window (MainMenu)
    - Main navigation (Sidebar, TopBar)
    - UI style + theming system (Theme interface, ThemeManager, starter themes)

## PHASE 2 — UI Foundation
    - Games screen (placeholder)
    - Chat screen (placeholder)
    - Shop screen (placeholder)
    - Profile screen (placeholder)
    - Settings screen (placeholder)
    - Connection indicator (CONNECTING / ONLINE / OFFLINE / RECONNECTING)
    - Refresh button (visual only, no registry yet)
    - Notification Centre icon in top bar (visual only, wired up in Phase 9)

## PHASE 3 — Account System
    - Account, permanent account ID
    - Username, password authentication, login/logout
    - Server-side account storage
    - Profile screen becomes real
    - Username change
    - Password change (current-password re-verification)
    - Avatar selection (preset icons/colors — custom upload deferred, Phase 17)
    - Player color selection

## PHASE 4 — Permissions
    - Player / Moderator / Admin roles
    - Server-side permission checks
    - Username-change testing against role persistence (core rule: roles
      attach to account ID, never username)

## PHASE 5 — Networking
    - Server, Client, NetworkManager
    - Connection handling
    - Authentication over the network
    - Disconnect handling, reconnection

## PHASE 6 — Game System
    - Game abstraction, GameInfo, GameManager
    - Game Registry
    - Game cards (Games screen becomes real)
    - Launching games

## PHASE 7 — First Old Game
    - Choose one existing game, inspect it
    - Plan conversion, adapt it, integrate it, test it

## PHASE 8 — Multiplayer
    - Multiplayer networking
    - Player synchronization, game state
    - Multiple players, disconnect/reconnect during play

## PHASE 9 — Chat, Social & Notifications
    - General chat
    - Private messages (account-ID based conversations)
    - Group chats (user-created, owner/member roles separate from
      platform roles)
    - Notification Centre goes live (chat mentions, group invites,
      mod actions)
    - Chat moderation: mute, kick, ban (platform-level)

## PHASE 10 — Friends
    - Friend requests (pending/accepted/declined)
    - Friend list
    - Online/offline presence status
    - Friend-related Notification Centre entries

## PHASE 11 — Economy
    - Wallet, coins, transactions
    - Server-validated rewards from games (reward IDs, anti-duplicate)
    - Main Shop, purchases, purchase validation
    - Daily login rewards / streaks
    - Coin transaction history in Profile

## PHASE 12 — Offline
    - Practice / single-player mode
    - Offline detection
    - Local saves
    - Pending reward records while offline
    - Synchronization when connectivity returns

## PHASE 13 — Updates
    - Game Registry refresh flow
    - New game / updated game / removed game detection
    - Version checking
    - Downloads, verification

## PHASE 14 — Admin & Moderation Tools
    - Player management, moderator management
    - Game management, publishing
    - Server status
    - Economy management
    - Announcements (also powers MOTD on login)
    - Report system (user reports feed the Moderator Panel queue)

## PHASE 15 — Multiple Servers
    - Server registration, server IDs
    - Game server selection
    - Main server coordination with multiple game servers

## PHASE 16 — Remaining Games
    - Convert remaining old games one at a time
    - Add new games

## PHASE 17 — Polish & Feature Backlog
    - Theme picker UI in Settings (system already exists from Phase 1)
    - Additional themes, incl. colorblind-friendly
    - Block/mute individual users (user-level)
    - Read receipts, unread badges, typing indicators
    - Emoji reactions in chat
    - Achievements/badges, AchievementManager
    - Per-game leaderboards
    - Match/game history & personal stats
    - Party system (invite friends into a game lobby)
    - Global search (games, users, chat, shop)
    - Pinned/favorite games
    - Onboarding/tutorial flow for new accounts
    - Anti-cheat flagging for suspicious reward patterns
    - LAN auto-discovery for local/computer-lab servers
    - Seasonal/limited-time shop items
    - Crash/bug report button
    - Referral bonus
    - Custom avatar image upload (moderation-heavy, deliberately deferred)
    - Guilds/clans (larger, persistent groups beyond group chats)

## PHASE 18 — Final Packaging
    - Package application, custom icon
    - Desktop shortcut, Start Menu shortcut, taskbar pinning
    - Test outside BlueJ
    - Test installation and updates end-to-end

---

## Notes on ordering

    - Friends (Phase 10) comes right after Chat/Notifications (Phase 9)
      because friend requests need the Notification Centre to feel real,
      and both depend on Accounts (Phase 3) already existing.
    - Economy (Phase 11) comes after Multiplayer/Games are functional
      (Phases 6-8), since "coins from games" needs games that can report
      results in the first place.
    - Everything in Phase 17 is intentionally late — these are all
      enhancements to systems that must already exist (chat, economy,
      accounts, games), not new foundations.
    - This order can shift if a feature turns out to be needed earlier
      for something else to work — flag it if that happens and we'll
      re-sequence deliberately rather than build out of order silently.

*End of consolidated roadmap.*
