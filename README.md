# Vertex

A multiplayer gaming platform built in Java using BlueJ, as a school project.

## Structure

- `VertexClient/` — client-side BlueJ project (default package)
- `VertexServer/` — server-side BlueJ project, includes a full mirrored copy of the client so it can also run standalone in combined server+client mode
- `VertexClient.jar` / `VertexServer.jar` — pre-compiled, runnable JARs (no BlueJ needed to play — `java -jar VertexClient.jar`)
- `Run-VertexClient-LowEnd.*` / `Run-VertexServer-LowEnd.*` — launcher scripts with JVM flags tuned for low-end machines (capped heap, serial GC)

## Running it

**With BlueJ:** open `VertexServer` and `VertexClient` as separate projects, compile the server first, run `ServerMain`, then run `Vertex` from one or more client instances.

**Without BlueJ:** run the server with `java -jar VertexServer.jar`, then the client with `java -jar VertexClient.jar` (or use the low-end launcher scripts for weaker hardware).

The server listens on port 7777.

## Features

Full multiplayer platform with 16 games (Chess, Battleship, Rock Paper Scissors, Racing, Fight Arena, Among Us, Tic-Tac-Toe, and 9 single-player games), ELO ratings and leaderboards, achievements, a party/invite system, solo and team tournaments, spectator mode, match replay, a cosmetics shop, 11 selectable themes including an animated Glitch mode, and full social features (friends, chat, moderation tools).
