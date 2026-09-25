# Vertex Server

This is the **VertexServer** BlueJ project — built and shipped as
`VertexServer.jar` (`Main-Class: ServerMain`). Headless: no GUI, no client code at
all. Runs unattended on a real machine (a cloud VM with no display included, or
just a spare PC left on) rather than opening a login window.

**This folder is a partial synced copy, not where edits happen.**
`VertexClient/` is the edit source of truth for anything genuinely shared
(networking, game rule engines, account/economy/social data) — change it there
first, then copy the changed file here too, and diff the two to confirm they still
match. If you're changing UI-only code (a page, a dialog, a theme, a game's Window
class), it only exists in `VertexClient/` — there's nothing to copy here. See
[`VertexClient/README.md`](../VertexClient/README.md)'s "Keeping the two projects
in sync" section for the full rule and why it matters (it's already caused one
real bug).

For what Vertex actually is and how the whole platform fits together, start at the
root [`README.md`](../README.md) or, for a no-coding-required explanation,
[`HOW_VERTEX_WORKS.md`](../HOW_VERTEX_WORKS.md). This file is specifically about
*this folder*.

## Why this folder only has ~140 files

`ServerMain` doesn't open any client UI — it just starts `net.GameServer` and
blocks the main thread forever (the accept loop itself runs on a daemon thread).
Its real dependency set — networking, game rule engines and match managers, and
the account/economy/social data layer; no `ui`, `theme`, or `pages` package, no
game Window/Dialog classes, no practice-mode AI — was found mechanically: compile
just `ServerMain.java` with `-sourcepath` pointing at the full `VertexClient/` tree
and keep whatever the compiler actually pulls in, rather than guessing.

## How to run it

**Without BlueJ:**
```
java -jar VertexServer.jar
```
Prints `Vertex server listening on port 7777` to the console — no window opens.
Connect to it from a `VertexClient.jar` (or another `VertexServer.jar`'s "Connect
to a Server" option) to actually log in and play.

**With BlueJ:** open this folder as a project, compile all classes, right-click
`ServerMain` → `void main(String[] args)` → leave the argument blank.

If no admin exists yet on this server, the first account created **from a
loopback (localhost) connection** automatically becomes admin — a remote player
joining before the server operator creates their own account can never
accidentally end up with it instead.

## Security

Passwords are salted + hashed (never stored in plain text), there are no
hardcoded credentials, repeated failed logins lock an account out, and every
privileged action re-checks the requester's role server-side rather than trusting
what the client claims. Deserialization off the raw socket is allow-list filtered
(`net.VertexSerializationFilter`) rather than accepting any class a peer sends.
**Known, open gaps** (tracked in [`BLOCKED_QUESTIONS.md`](../BLOCKED_QUESTIONS.md),
not silently ignored): no TLS yet (LAN-only for now, by design), and the client
auto-update mechanism has no code signing.

## THE Economy Config — `economy/EconomyConfig.java`

The one file to edit to change Vertex's entire economy: win rewards, practice-mode
score-to-coin formulas, the daily login streak curve, challenge/quest definitions,
shop items. Every number that affects how many coins something pays lives here,
not scattered across individual game files.

## Development history

[`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) is this folder's old README — a
chronological, append-only log of what was built each round going back to the
start of the project. Useful for the reasoning behind old decisions; not current
documentation (this file, and the ones linked above, are).
