# How Vertex Works (no coding knowledge required)

This explains the whole project in plain language. You shouldn't need to open a
single code file to understand what Vertex is, how it's put together, or what a
player actually experiences. If you want more technical detail afterward, the end
of this file points you to the right next document for whatever you're curious
about.

---

## 1. What Vertex actually is, in one paragraph

Vertex is a game platform — a bit like a private, self-hosted mini version of
Steam or Discord's game features, except **you own the whole thing**. There's no
company running it, no central "Vertex Inc." server, no account tied to some
outside company. Instead, one person runs the "server" program (the referee /
host), and everyone else runs the "client" program (what you actually see and
play on) and connects to that server, the same way you'd connect to a friend's
Minecraft server. Whoever's server you're connected to is where your account,
your coins, your friends list, and your game history actually live.

## 2. The two programs, explained like you've never coded

Vertex is really **two separate programs** that talk to each other over a network
connection:

- **The Server** (`VertexServer.jar`) — the "brain." It doesn't have a window or
  any graphics at all — you just start it and it sits there quietly, listening
  for players to connect. It keeps track of everyone's accounts, who's playing
  what, who won, who owns which cosmetic items, and so on. Think of it as the
  game master that everyone answers to.
- **The Client** (`VertexClient.jar`) — the actual app a player opens. This is
  the thing with buttons, menus, and the games themselves. On its own it can't do
  much — it needs to connect to a Server to log in, matchmake, chat, and save
  progress. A handful of games can be played completely offline too (see section
  4), but anything involving another real person needs a Server to broker it.

**Important: only the Server program can host.** Only someone deliberately
running `VertexServer.jar` is acting as a host — the ordinary player app has no
"start a server" button hidden anywhere in it. If you want to run a server for
your friends, you (or whoever's machine is going to stay on) runs
`VertexServer.jar` specifically.

**Why build it this way instead of one big program?** Two reasons. First, it
mirrors exactly how you'd want it to actually work: one trusted machine (a spare
PC, a cheap cloud server, whatever) stays on and holds everyone's data, while
everyone else just opens the app and plays — nobody else needs to keep anything
running. Second, it means the Server can run on a machine with no monitor at all
(a "headless" machine, like a lot of cheap cloud hosting), since it never needs to
draw anything on screen.

## 3. What happens when you open the app and play a game

1. You double-click `Vertex.exe` (or run the jar file). A splash screen appears,
   then a login screen.
2. The app tries to reach whichever server address it's configured for (there's
   an in-app way to save and switch between different servers' addresses — handy
   if you play on more than one friend group's server). If it can't reach
   anything, it tells you plainly instead of hanging silently, and still lets you
   play the handful of fully-offline games.
3. You log in (or make an account — the very first account ever created on a
   brand-new server automatically becomes the admin).
4. From the main menu you can browse ~50 games, chat with friends, check a shop
   for cosmetic items you've earned, look at leaderboards, and more.
5. Pick a game. If it's a real-time multiplayer game, the app finds you an
   opponent (or you queue up as a group, depending on the game) and the Server
   referees the actual match — it's the final word on who won, so nobody can
   cheat by tampering with their own copy of the app. If it's a single-player
   game, it mostly just runs locally on your machine, and reports your score back
   to the Server afterward so it counts toward your coins and history.
6. Coins, achievements, and progress are all tracked by the Server and tied to
   your account, so they're waiting for you the next time you log in — from any
   computer, as long as it can reach the same server.

## 4. What "offline" means here

A handful of games (Snake being the flagship example) need no opponent and no
live connection at all — you can play them with zero server reachable, and if
you're logged out entirely, your scores are quietly remembered on your own
computer and handed to the server the next time you log in, so you don't lose
credit for playing while offline. Every other game either needs a real opponent
(so it needs the Server to matchmake you) or has an offline "Practice Mode"
against a simple built-in computer opponent for a subset of games.

## 5. Where the "50 games" actually come from

Every game in Vertex is original code written for this project — nothing is
copied from another game's assets, art, or source code. Some are inspired by
well-known game *genres* (a social-deduction game in the spirit of Among Us, a
battle-royale-style shooter, a maze-chase game in the spirit of Pac-Man) but
built from scratch with original mechanics, not reskins of something else.
Roughly a third are real-time multiplayer with a skill rating (like chess.com's
rating system), a chunk more are score/placement-based multiplayer (like a
racing leaderboard), and the rest are solo games you can jump into with one
click, no opponent needed.

## 6. The economy, in plain terms

Winning matches and playing solo games earns **coins** — a currency with no
connection to real money at all, spendable only on cosmetic stuff (username
colors, chat badges, profile frames). There's also a daily login bonus that
grows the more days in a row you log in, achievements that unlock permanently
once you hit certain milestones, and small daily/weekly challenges ("win 3
matches today") for a bit of extra coin. None of this affects how well you
actually play — it's all cosmetic, on purpose.

## 7. The social side

Friends, direct messages, group chats, parties (queue up for a game together
with friends), and a moderation system (an admin/moderator can mute, kick, or
ban someone who's misbehaving, and players can report bad behavior for a human
to review) — all of it lives on whichever server you're connected to, the same
way your account does.

## 8. Who can see what, and who's in charge

The person who creates the very first account on a brand-new server
automatically becomes that server's **admin** — full control: manage roles,
review reports, ban/unban people. An admin can also promote trusted players to
**moderator**, who get a smaller set of powers (mute, kick, resolve reports) but
not full admin control. This is all per-server — being an admin on your server
doesn't mean anything on someone else's server.

## 9. Security, explained without jargon

- **Passwords are never stored as plain readable text** — they're scrambled in a
  one-way way (like a paper shredder — easy to turn a password into scrambled
  text, practically impossible to turn the scrambled text back into the
  password) before ever touching disk.
- **The server is the referee, not the players' own apps.** Every match result,
  every coin awarded, every privileged action is decided and double-checked by
  the Server itself — a player's own copy of the app can *ask* for something,
  but never unilaterally *grant* it. This is what stops someone from hacking
  their own client to just say "I won" or "give me 10,000 coins."
- **Known, honestly-tracked weak spots** (see `BLOCKED_QUESTIONS.md` for the
  full detail): the connection between client and server isn't encrypted yet
  (fine on a home network/LAN, not yet meant for the open internet), and the
  auto-update feature that keeps everyone's app current doesn't yet
  cryptographically verify updates came from a trusted source. Both are
  real, tracked, unfinished work — not secrets, not ignored.

## 10. What's built vs. what's still just an idea

- **Built and working today:** everything described above — 50 games, accounts,
  the economy, social features, moderation, an in-app auto-update checker, 11
  visual themes, and more. [`FEATURES.md`](FEATURES.md) is the exhaustive list.
- **Actively being worked on / planned next:** [`ROADMAP.md`](ROADMAP.md) is the
  living plan — what's in progress, what's next, and what's been deliberately
  *not* built yet and why (rushing a half-working feature is treated as worse
  than being honest that it isn't done).
- **Big future ideas, not yet started:** a large backlog of additional game
  concepts, a public website, and more — also in `ROADMAP.md`.

## 11. Where to go next

- Want the exhaustive feature list? → [`FEATURES.md`](FEATURES.md)
- Want to know what's being worked on right now, or what's planned? →
  [`ROADMAP.md`](ROADMAP.md)
- Want to actually run the app? → the root [`README.md`](README.md)'s
  "Running it" section
- Want the technical, code-level tour (for an actual programmer)? →
  [`PROGRAM_STRUCTURE.md`](PROGRAM_STRUCTURE.md)
- Want open questions that need a real person's decision, not a guess? →
  [`BLOCKED_QUESTIONS.md`](BLOCKED_QUESTIONS.md)
