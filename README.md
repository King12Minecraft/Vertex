# Vertex

A full multiplayer gaming platform built in Java, using BlueJ, as a school project. Sixteen games, ELO ratings, a party system, tournaments, and an optional multi-server sync setup where players can host their own servers while still sharing one account.

## Running it

**Without BlueJ (fastest way to just play):**
```
java -jar VertexServer.jar
```
then, in a separate terminal (or on another computer):
```
java -jar VertexClient.jar
```
The server has to be running before a client can connect. On a weaker machine, use `Run-VertexServer-LowEnd.bat`/`.sh` and `Run-VertexClient-LowEnd.bat`/`.sh` instead — same thing, with JVM flags tuned for lower memory and a lighter garbage collector.

**With BlueJ:** open `VertexServer` and `VertexClient` as separate projects, compile the server first, run `ServerMain`, then run `Vertex` from one or more client instances.

Either way, the first screen asks whether you want to **host a server** or **connect to one** — see below.

## Hosting, connecting, and the sync system

Vertex was originally one client talking to one server. It's since grown into something closer to a small distributed setup:

- **Anyone can host.** Both `VertexClient` and `VertexServer` carry the full server engine, so starting a server doesn't require the separate server project — the ordinary client can do it too.
- **One server is "main."** The first server anyone ever designates as main gets locked with a password right then (first-come, first-served) — after that, starting a NEW main server on that same machine requires that same password. This stops someone from accidentally (or deliberately) spinning up a second "main" server that isn't the real one.
- **Everyone else is a satellite.** A satellite server runs its own real games — matches, chat, everything — but isn't the source of truth for accounts. The first time someone logs into a satellite with an account that only exists on main, the satellite quietly checks with main, confirms the login, and caches a local copy so the game can actually track them.
- **Progress syncs back automatically.** Win a match, unlock an achievement, buy something from the shop — the satellite pushes that change to main in the background, no action needed. If main happens to be briefly unreachable, the satellite just keeps playing locally.
- **Satellites never inherit admin.** Being an admin on main does not make you an admin anywhere else. Every satellite-cached account is a regular player, always.
- **Admins can see every known satellite.** From the sidebar (admin accounts only — a new "Servers" page), you can see every server that has ever registered with yours, and when it was last seen.

Default port is **7777**, but this is fully configurable — hosting asks which port to use, and the in-app server switcher (Settings → Switch Server) lets you save and jump between servers without editing any code.

## The games

**Online multiplayer (real opponents, ELO-rated unless noted):**
- **Chess** — full rules including castling, en passant, checkmate/stalemate detection. Resign or offer a draw mid-game. Spectate any live match, or replay a finished one move-by-move.
- **Battleship** — classic hunt-and-sink, 1v1. Spectators see both fleets fully revealed (there's nothing to hide once you're just watching); replays step through every shot in order.
- **Rock Paper Scissors** — best of 5, simultaneous blind moves each round. Spectator and replay support included.
- **Racing** — 3 to 6 players, shared track, with power-ups: shields, speed boosts, and coin pickups. Not ELO-rated — scored and ranked on a leaderboard instead.
- **Fight Arena** — 1v1, 2v2, 3v3, or free-for-all. Queue solo or with a party (your party always ends up on the same team). 2v2/3v3 also support a lightweight team tournament: register your party, wait for an opposing party, winner takes it.
- **Among Us** — round-based social deduction with a small group.
- **Tic-Tac-Toe** — online ranked matches, or practice offline against a simple AI.

**Solo tournaments (Battleship & Rock Paper Scissors only, since both games always produce a clear winner):** 4-player single-elimination brackets, browsable and joinable from the Tournaments page.

**Rematch:** after any Chess, Battleship, or RPS match, challenge the same opponent again with one click — no need to re-queue.

**Single-player (no server needed to play, though wins/scores still get tracked if you're logged in):** Snake, Tetris, 2048, Pong, Dino Dash, Crossing Road, Puzzle Quest, Aim Trainer, and RPS against a simple AI. Snake, Tetris, and Dino Dash all support pausing mid-game (press **P**).

## Everything else

- **Accounts & progression** — coins, daily login streaks, an ELO rating per competitive game, a shop for cosmetics (username colors, chat badges), and 10 achievements tied to real milestones (first win, win counts, coin totals, total plays, racing placement).
- **Social** — friends (with search/filter and the ability to pin favorites to the top of the list), direct messages, group chat, party invites (with a shareable, copyable code), and a live badge on the sidebar when a friend comes online.
- **Notifications** — a bell icon with a live feed, achievement unlock toasts, and a one-click "Clear All."
- **Customization** — 11 full themes including an animated Glitch mode, with the mode-select screen remembering which option (e.g. "vs Player" vs "vs AI") you picked last time for each game.
- **Admin & moderation tools** — player management, bans, report review, and (new) a live view of every satellite server that's synced with yours.
- **Quality of life** — confirm-before-close on any active match (so an accidental click doesn't silently hand your opponent a win), and a low-end hardware mode for older machines.

## Repo structure

- `VertexClient/` — the client project, including the full server engine (so it can host too)
- `VertexServer/` — the server project, including a full mirrored copy of the client (so it can run standalone in combined mode)
- `VertexClient.jar` / `VertexServer.jar` — pre-built runnable JARs, rebuilt fresh with every push
- `Run-*-LowEnd.*` — launcher scripts tuned for weaker hardware
