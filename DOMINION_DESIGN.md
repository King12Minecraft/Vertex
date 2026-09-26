# Vertex: Dominion — Design Brief

Status: **design decided, implementation not yet started.** This is the concrete
design ROADMAP.md's Architecture Decisions entry pointed at ("recorded here as a
settled design constraint for whenever Dominion work begins") - that begins now.

Directive from Bipin (2026-09-26): warfare and diplomacy, "basically real life."
Everything below is my own design filling in the specifics under that steer - flag
anything that doesn't match what you had in mind and it changes, same as any other
design decision on this project.

## What this is

A persistent, whole-server nation-building strategy game - not a quick match, a
standing thing every account can found or join and check in on repeatedly, like a
management sim. Original mechanics (asynchronous daily-tick nation strategy is a
long-established genre shape, same "inspired by the genre, built from scratch" rule
as every other game here - no copied content/mechanics from any specific existing
game). Confirmed already in `ROADMAP.md`: own persistent nav tab (not the
`MainMenu` game-host slot), own isolated world-state store, own currency, own
tick-scheduler - separate from the match economy and `GameSession` lifecycle
every other game shares.

## Core loop

Time advances via a **daily tick** (a real-world day, server-configured local time,
default midnight server-local) - not real-time simulation. Players queue orders
throughout the day (recruit, build, move an army, propose a treaty, declare war);
at tick time, every nation's queued orders resolve together, simultaneously, so
no player has a "who clicked first" advantage within the same tick. This sidesteps
the continuous-control/real-time-sync engine work every other genuinely real-time
game here would need (flagged as a "still-deferred engine shape" in `ROADMAP.md`'s
games backlog) - Dominion simply doesn't need it by design.

## Systems

**Territory.** The world is a fixed-size grid of Provinces (square grid, not hex -
simpler to render with Swing, and adjacency math is trivial). Each Province has a
terrain type (affects production) and is either unclaimed or controlled by exactly
one Nation. Provinces are adjacent to their grid neighbors; armies and territory
capture move along adjacency.

**Nations.** A Nation owns territory, has a treasury (Dominion's own currency - see
Architecture Decisions - never shared with platform coins from mini-games), and
population (an aggregate figure, capped by owned territory, gating recruitment and
production). V1: one account founds and solely controls one Nation (see V1 scope
below for why multi-member nations are deferred).

**Warfare.** A Nation recruits Army units from population + treasury. Declaring war
on another Nation takes effect starting the *next* tick (a one-tick warning, not an
instant sneak attack - a deliberate strategic-pacing choice, not a technical
limitation). Once at war, an army can be ordered to march into an adjacent
enemy-owned province; at tick resolution, if an army arrives at a defended
province, combat resolves via troop count x unit-type modifier x terrain/
fortification modifier for both sides - the loser's army is destroyed, and if the
attacker had an "occupy" order and won, the province's ownership flips. All
resolution is server-side only, same as every other game's "server is sole
authority" rule - a client never unilaterally decides a battle's outcome, only
requests one.

**Diplomacy.** Proposals sent nation-to-nation: Alliance (mutual defense - an
ally's declared war pulls you in unless you decline, at an Honor cost for
declining), Non-Aggression Pact (can't declare war on each other while active).
Accepting/rejecting is the target nation's leader's call. Breaking an active pact
is always possible but costs Honor (a nation-level reputation stat) - low Honor
doesn't hard-block anything in V1, it's tracked and shown so it can gate real
things later (who's willing to ally with you, government-type unlocks) once
there's a reason to build that gate.

**Economy.** Each owned Province produces Dominion's currency per tick, scaled by
terrain and any buildings on it. Armies cost per-tick upkeep; unpaid upkeep causes
desertion (a soft failure state, not a hard block on going broke). A Province can
build a small number of building types (more production, a fortification bonus,
faster recruitment) - V1 keeps this to a short, fixed list rather than a full tech
tree.

**Leadership & succession (deferred to a fast-follow, not V1).** The planned
"Procedural/emergent character system" (already in `ROADMAP.md`'s infrastructure
backlog, not yet built) is the natural home for Nation rulers: a ruler with
name/trait combinations (a Warmonger buffs combat, a Diplomat gets better treaty
terms, a Merchant boosts production), replaced by a successor when triggers fire
(a lost war, a stability collapse, a fixed term ending). This is Dominion's
real reason to exist for that system rather than a speculative one, but it's
explicitly **not** part of V1 - see below for why.

## V1 scope - what actually gets built first

Small and honestly-scoped, not the full vision at once, matching how everything
else on this project got built:

- Fixed-size grid map (something like 10x10-20x20 depending on how it looks once
  rendered), pre-seeded with unclaimed provinces of a few terrain types.
- One account = one Nation. Multi-member nations (councils, invites, ranks) are a
  real feature but a *social* one layered on top of a working single-owner game,
  not a prerequisite for the core loop to be playable - deferred to a fast-follow.
- One unified currency (no separate resource types like food/ore/wood yet - terrain
  just changes the yield rate of the one currency). Multiple resource types are a
  believable v2 addition once the single-currency loop is proven fun.
- One generic Army unit type (no unit-type variety yet - the combat formula still
  has terrain/fortification/troop-count depth without needing multiple unit types
  to start).
- Warfare: declare war (next-tick effective), march, tick-resolved combat, capture
  on win - as described above.
- Diplomacy: Alliance and Non-Aggression Pact only. Vassalage and trade agreements
  are real ideas, deferred.
- No procedural ruler/succession layer yet - nations are directly player-controlled
  with no trait bonuses in V1. Building two large, unproven systems
  (Dominion itself, and the procedural character system) simultaneously is the kind
  of risk this project avoids; get the core warfare/diplomacy loop proven and fun
  first, then give the character system a real, working consumer to attach to.
- One daily tick, server-configured time.

## Data model sketch (server-authoritative; client gets read-only DTOs to render)

- `Province` - id, grid position, terrain type, owning nation id (nullable),
  building list, current yield.
- `Nation` - id, owning account id, name, treasury, Honor, owned province ids,
  army list.
- `Army` - id, owning nation id, troop count, current location (province id or
  "marching to X"), standing order for the next tick.
- `DiplomaticRelation` - nation A id, nation B id, type (ALLIANCE / NON_AGGRESSION /
  WAR / NEUTRAL), effective-from tick (for the one-tick war-declaration delay).
- `DominionTickEngine` - server-only, runs once per configured tick: resolves
  pending army movements/combat, applies production, applies upkeep/desertion,
  applies any diplomatic state transitions (a declared war becoming effective),
  then persists the new state and (later) notifies affected clients.

## Deliberately not decided here (still open, on purpose)

- Exact map size and terrain-type list - a numbers/content decision better made
  once the core loop is running and can actually be played with different values,
  not guessed at on paper now.
- Exact combat formula constants - same reasoning; needs playtesting to tune, not
  a one-shot guess.
- Whether/how Dominion integrates with existing Social (party/friends) systems for
  things like alliance chat - a real question, deferred until the core loop exists
  to attach it to.

## Build order (once implementation starts)

1. Server-side data model + `DominionTickEngine` core resolution logic, proven with
   unit tests (a fixed scenario: two nations, a declared war, an army march, a
   combat outcome, a province flipping ownership) - no networking or UI yet, same
   "prove the core logic in isolation first" approach `ai/search`'s `Minimax` took.
2. Persistence (its own isolated store, per the Architecture Decision - not reusing
   `ServerAccountStore`'s file format for an unrelated domain).
3. Networking: new message types for founding a nation, viewing the map, queuing
   orders, viewing diplomacy state - additive to `MessageType`, doesn't touch any
   existing game's protocol.
4. Client: the persistent nav tab (already an settled architecture decision), a map
   view, a nation dashboard, an orders-queue UI.
5. Only then: multi-member nations, additional resource types, unit-type variety,
   vassalage/trade, and the procedural ruler/succession layer - each a real,
   separately-scoped follow-up, not bundled into getting V1 playable.
