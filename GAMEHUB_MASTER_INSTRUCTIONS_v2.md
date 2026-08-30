# GAMEHUB — MASTER PROJECT INSTRUCTIONS (v2)

This is an update to the original GameHub master instructions. Everything from
v1 still applies. This version adds: a Notification Centre, an expanded chat
system (general chat, user-created group chats, private messages), a friend
system, an expanded shop/economy, and account management features
(username/avatar change, password change). It also folds in a backlog of
suggested features for later phases.

Treat this document, together with the original v1 sections not repeated
here, as the current source of truth. Update this file whenever architecture
changes (see original Section 40).

---

## 42. NOTIFICATION CENTRE (NEW SYSTEM)

GameHub has a central Notification Centre, accessible from the top bar
(e.g. a bell icon with an unread badge).

Notifications are server-authoritative and tied to the permanent account ID,
not the username — consistent with the core account rule in Section 11.

### Notification types (initial set)

    - Friend request received
    - Friend request accepted
    - New private message
    - Mentioned in a group/general chat
    - Added to a group chat
    - Moderation action taken against you (mute/kick/ban) — see Section 13
      on server-side enforcement; the notice itself is informational
    - Game update available
    - Shop purchase confirmation
    - Daily reward available (see Section 44)
    - Achievement unlocked (see Section 46)

### Rules

    1. The server generates and stores notifications, not the client.
    2. Notifications must reference account IDs, never usernames, for the
       same reason chat and friends do (Section 11, Section 23).
    3. Unread state is tracked server-side so it's consistent across devices.
    4. Notifications should be dismissible individually and in bulk
       ("mark all as read").

### Suggested classes

    Notification
    NotificationManager
    NotificationType (enum)

---

## 43. CHAT SYSTEM (EXPANDS ORIGINAL SECTION 22/23)

GameHub's chat system now has three tiers:

    1. General Chat   - one shared public channel (as in v1)
    2. Group Chats    - user-created, multi-member chat rooms
    3. Private Messages - one-to-one DMs (as in v1, account-ID based)

### Shared architecture

All three should share a common abstraction so the UI and server logic
aren't duplicated three times:

    ChatChannel (abstract concept)
      |
      +-- GeneralChat        (one instance, everyone auto-joined)
      +-- GroupChat          (many instances, user-created)
      +-- PrivateConversation (exactly 2 participants, account-ID based)

Each channel conceptually needs: participant list (by account ID), message
history, and its own moderation rules.

### Group chats specifically

    - Any player can create a group chat (unless restricted later).
    - The creator becomes the group's OWNER for that specific chat.
    - Group-level roles are SEPARATE from platform-wide roles
      (Section 12). A platform ADMIN is not automatically a group owner,
      and a group owner is not a platform moderator.
    - Suggested group-level permissions: invite members, remove members,
      rename the group, mute a member within that group only, delete
      the group (owner only).
    - Group membership changes (added/removed) should trigger a
      Notification Centre entry for affected users.

### Server responsibilities (unchanged principle from Section 13)

    - The server enforces who can post in which channel.
    - The server enforces group-level permissions server-side, the same
      way platform roles are enforced — never trust the client to decide
      "I am the group owner."
    - Platform moderators/admins retain their existing moderation powers
      (mute/kick/ban) across all channel types, per Section 28/13.

### Suggested classes

    ChatChannel (interface or abstract class)
    GeneralChat
    GroupChat
    GroupMembership   (accountId + group-level role)
    PrivateMessage    (as in v1)
    ChatManager

---

## 44. FRIEND SYSTEM (NEW SYSTEM — DEFERRED UNTIL ACCOUNTS EXIST)

As agreed, this is implemented once online account creation (Phase 3) is
in place. Documented now so the architecture accounts for it early.

    - Friend relationships are stored server-side, by account ID pair.
    - A friend request is a distinct object from an established friendship,
      so it can be pending / accepted / declined.
    - Sending, accepting, and declining a friend request should each
      generate a Notification Centre entry for the relevant account.
    - Suggested: online/offline presence status, visible to friends only
      by default (privacy-friendly default; consider a settings toggle
      for "appear offline").

### Suggested classes

    FriendRequest   (fromAccountId, toAccountId, status)
    FriendList
    PresenceManager (tracks who is currently online, for friends UI)

---

## 45. SHOP & COIN ECONOMY (EXPANDS ORIGINAL SECTIONS 20/21)

Confirms and extends the v1 economy design:

    - Coins are earned by playing games and validated server-side via
      reward IDs, exactly as described in v1 Section 19 (never trust a
      client-reported coin amount).
    - The Main Shop spends coins on cosmetic/platform items (player
      colors, avatars, effects) as in v1.
    - NEW: Daily login rewards — a small server-tracked coin bonus for
      logging in, with a streak counter. This gives the economy a source
      of income that isn't gameplay-dependent, so new/casual users aren't
      locked out of the shop.
    - NEW: Coin transaction history should be viewable in Profile, for
      transparency (source of every coin gain/spend).

### Suggested classes

    Wallet
    Transaction
    Shop
    ShopItem
    DailyRewardManager

---

## 46. PROFILE / ACCOUNT MANAGEMENT (EXPANDS ORIGINAL SECTION 10)

### Username change

    - Changing a username never changes the account ID (core rule,
      Section 11). All roles, friends, messages, purchases, and
      achievements stay attached to the account ID.
    - Server should validate uniqueness and reasonable format
      server-side before accepting a change.

### Avatar change

    - Start with a set of preset avatar icons/colors (ties into the
      existing Player Colors feature, Section 24).
    - Custom image upload can come later — note it introduces moderation
      requirements (inappropriate image review) that preset avatars avoid,
      so it's a bigger feature than it first looks and should be its own
      phase, not bundled into the initial Profile screen.

### Password change

    - Requires re-entering the CURRENT password, verified server-side,
      before a new one is accepted — never let the client just declare
      "here is the new password" unauthenticated.
    - New password is hashed the same way as at registration (Section 33)
      — never stored or transmitted in plain text.
    - Suggested: a password recovery path (security question or, later,
      email-based reset) since there's no in-person account recovery
      once this is used outside a single classroom/LAN context.

---

## 47. ADDITIONAL FEATURES BACKLOG (SUGGESTED, NOT YET SCHEDULED)

These are worth keeping in mind so early architecture doesn't accidentally
block them later. None of these are being built yet — they're recorded here
so they're not forgotten and so class design leaves room for them.

    - Block / mute individual users (user-level, distinct from moderator
      mute — Section 28). Should suppress chat/DMs from a blocked account
      without needing moderator involvement.
    - Report system: users flag a message or player; reports feed into
      the existing Moderator Panel (Section 28) as a queue.
    - Read receipts / unread badges for chat and notifications.
    - Typing indicators in chat.
    - Achievements/badges — already referenced as account data in v1
      Section 10; worth formalizing with an AchievementManager.
    - Per-game leaderboards, built on top of the Game Registry and
      Accounts systems.
    - Emoji reactions on chat messages.
    - Search: friends, chat history, shop items.
    - Group chat roles beyond owner (e.g. "can invite" as a delegated
      permission) if group chats grow large.

---

## 48. UPDATED RECOMMENDED CLASS ARCHITECTURE (ADDITIONS TO SECTION 30)

    NOTIFICATIONS:
        Notification
        NotificationManager

    CHAT (expanded):
        ChatChannel
        GeneralChat
        GroupChat
        GroupMembership
        PrivateMessage
        ChatManager

    FRIENDS:
        FriendRequest
        FriendList
        PresenceManager

    ECONOMY (expanded):
        Wallet
        Transaction
        Shop
        ShopItem
        DailyRewardManager

    FUTURE (backlog, Section 47):
        AchievementManager
        LeaderboardManager
        ReportManager
        BlockList

---

## 49. UPDATED "DO NOT FORGET" ADDITIONS (EXTENDS SECTION 41)

    31. Notifications are server-generated and tied to account ID.
    32. Group chats have their own owner/member roles, separate from
        platform roles (ADMIN/MODERATOR/PLAYER).
    33. Friend requests are pending objects, not instant friendships.
    34. Username changes never alter the account ID.
    35. Password changes require re-verifying the current password
        server-side.
    36. Custom avatar uploads are a bigger feature than preset avatars
        (moderation implications) — treat as a separate later phase.
    37. Daily rewards and coin transaction history support the economy
        without ever trusting client-reported coin values.

---

*End of GameHub Master Instructions v2 additions.*
