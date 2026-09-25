# Vertex Client

This is the **VertexClient** BlueJ project — built and shipped as `VertexClient.jar`
(`Main-Class: Vertex`). This is what someone who just wants to play runs, and it's
the **edit source of truth**: if you're changing a network message type, a game's
rule engine, or an account/economy/social class, change it here first, then copy
the same file into `VertexServer/` too (see "Keeping the two projects in sync"
below). UI-only code (a page, a dialog, a theme, a game's Window class) only ever
lives here — there's nothing to copy for those.

For what Vertex actually is and how the whole platform fits together, start at the
root [`README.md`](../README.md) or, for a no-coding-required explanation,
[`HOW_VERTEX_WORKS.md`](../HOW_VERTEX_WORKS.md). This file is specifically about
*this folder*.

## What's in this folder

Everything the client needs to run as a full, playable app — every page, every
game's window/rendering code, every theme, and a full copy of the server's own
logic (see below for why). [`PROGRAM_STRUCTURE.md`](../PROGRAM_STRUCTURE.md) has
the complete package-by-package breakdown; the short version:

- `net`, `account`, `social`, `admin`, `economy`, `games` (rule engines + match
  managers) — the server logic. Present here as the edit-source-of-truth copy that
  gets synced into `VertexServer/` (see below) — **not** something the shipped
  client exposes to players. `VertexClient.jar` has no way to start a server; only
  `VertexServer.jar` does.
- `ui`, `theme`, `pages` — everything client-only: reusable widgets, the theming
  system, and the app's top-level navigable pages (Home, Games, Chat, Settings...).
- `games/` also holds every game's actual Window/Dialog rendering class, and the
  shared game-launching infrastructure (`GameWindowFactory`, `GameRegistry`,
  `GameLauncher`).
- `ai.search`/`ai.grid`/`ai.steering` — offline practice-mode bot opponents, client-
  only. `ai.knowledge` is the one `ai` subpackage the server also needs (live Trivia
  Blitz lookups).
- `Vertex.java` — the actual entry point `java -jar VertexClient.jar` runs.
  `ServerMain.java` also lives here (the edit-source-of-truth copy of
  `VertexServer.jar`'s real entry point) but is never what `VertexClient.jar` runs.

## Hosting is not a client feature

There used to be an in-app "Start Hosting" button (Settings → Hosting Server,
`HostServerDialog.java`) that let the ordinary client spin up a real server
in-process. **Removed 2026-09-25** — hosting is exclusively a `VertexServer.jar`
thing now. A player running `VertexClient.jar` has no way to host anything from
inside the app.

## Keeping the two projects in sync

`VertexServer/` isn't a separate codebase — it's a trimmed copy of whatever this
folder's server-side packages contain (see the root README's "Repo structure"
section for exactly which packages and why). **The rule:** every file under
`net`/`account`/`social`/`admin`/`economy`/`games` (except each game's Window/Dialog
classes, which are client-only) needs to stay byte-identical between the two
folders. Changed one of those here? Copy the same file into `VertexServer/` at the
matching path, and diff the two to confirm. This has already caused one real,
found-and-fixed bug (see `ROADMAP.md`'s "Done" section, 2026-09-25) — a fix landed
in `VertexServer/` and silently never made it here, even though `ServerMain.java`
running from this folder is just as real a server as the dedicated one. Worth
double-checking, not just assuming.

## Building and running

See the root [`README.md`](../README.md)'s "Running it" section — covers running
from a pre-built jar, from BlueJ, or building fresh jars with `build.sh`/`build.bat`.

## Development history

[`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md) is this folder's old README — a
chronological, append-only log of what was built each round going back to the
start of the project. Useful for the reasoning behind old decisions; not current
documentation (this file, and the ones linked above, are).
