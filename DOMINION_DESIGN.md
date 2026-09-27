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

## Future depth (post-V1) - what "basically real life" grows into

Brainstormed with Bipin 2026-09-26, explicitly **not** part of V1 and not to be
built until V1's core loop is actually playable and proven fun - this section
exists so the ambition is written down and won't get lost, not as a queue to
start working through immediately. Roughly ordered by what naturally unlocks
what, not by build priority:

- **Research & technology.** Nations invest treasury per tick into a tech tree -
  military (better troops, armor), economic (production multipliers),
  infrastructure (fortification, movement speed). This is also the natural gate
  in front of nukes and advanced espionage below - those should be *unlocked*
  through play, not available turn one.
- **Espionage.** Send a spy into a rival's territory for a hidden action: steal
  treasury, steal research progress, sabotage a building, gather intelligence
  (reveal real army strength/position - see fog of war below), incite unrest, or
  assassinate their ruler - a genuinely good tie-in to the procedural ruler/
  succession system, giving it a *second* trigger besides war/old age.
  Counter-espionage lets a nation catch spies; a caught spy is a real diplomatic
  incident (a significant Honor hit, arguably treated as an act of war without
  the usual one-tick warning, since the aggression already happened covertly).
- **Nuclear weapons.** The top of the military tech tree - deliberately slow and
  expensive to build (many ticks, large treasury cost), and visible once a
  nation gets close to completion rather than a silent surprise (real nuclear
  programs aren't secret forever, and that visibility is what makes deterrence
  a real mechanic instead of "biggest number wins quietly"). Using one should be
  catastrophic (a province wiped out for a long time, not just captured), carry
  a severe Honor penalty, and strongly pull other nations into an alliance
  against whoever used it - a "nuclear taboo," not a free-win button.
  Retaliation-capable nations should be able to strike back automatically.
- **Fog of war.** Possibly the single highest-leverage idea here: V1 as
  designed has full visibility (every nation sees the whole map and every
  army). Real geopolitics runs on uncertainty - making enemy army strength/
  position genuinely hidden unless scouted or spied on is what makes
  espionage *matter* rather than being a nice-to-have, and makes diplomacy
  involve real risk (no certainty an ally actually holds when it counts).
- **Government types.** Democracy/Autocracy/etc., each with real tradeoffs (a
  democracy might need a delay or "war support" check before declaring war but
  regrows Honor faster; an autocracy mobilizes instantly but Honor decays
  faster under prolonged war). Ties into ruler traits - gives "basically real
  life" its political-systems flavor, not just its military one.
- **Rebellions & civil unrest.** Sustained low Honor/stability triggers a
  revolt; a province (or several) can secede into a new breakaway nation, with
  a rebel leader spawned by the procedural character system. This is what
  makes losing wars and mismanaging a nation carry real stakes beyond "you
  have less land now."
- **Trade & economic warfare.** Trade agreements between allies boosting both
  economies, and the flip side - sanctions/embargoes as a non-military way to
  pressure a rival, which matters more once fog of war and Honor are real
  systems rather than just tracked numbers.
- **Multi-member nations.** Already flagged as a fast-follow above, but worth
  restating here: real diplomacy gets much more interesting once a "nation" is
  a council of several players who don't always agree, not just one person's
  unilateral decisions.

None of this contradicts V1's intentionally small scope - it's exactly the
depth V1 exists to make room for once the core loop is proven, not a
replacement for building V1 small first.

### Country concepts - what makes a nation feel like *your* nation

Brainstormed with Bipin 2026-09-27, same status as the list above (not V1,
not a build queue, just ambition written down). Where the list above is
mostly about conflict between nations, this one is about what makes a single
nation feel distinct and worth caring about over a long-running game:

- **National archetypes.** A nation leans Militarist/Mercantile/Diplomatic/
  Isolationist/Expansionist - a passive flavor rather than a hard class (a
  Militarist recruits cheaper but taxes less; a Mercantile nation produces
  more but has a lower army cap). Pairs naturally with government types above.
- **Capital city.** One province flagged as the capital, with a real cost to
  losing it - a stability/Honor hit, or past some threshold, risk of the
  nation collapsing if an enemy holds it long enough. Gives territory a
  hierarchy instead of every province mattering equally.
- **Culture/heritage.** A culture tag per nation; sharing one with a
  neighbor eases diplomacy (cheaper alliances, less friction); a different
  culture slows "assimilating" conquered provinces and raises their unrest.
- **Core vs. occupied territory.** A province held since founding is "core"
  (stable, no unrest); a freshly conquered one is "occupied" and generates
  unrest/rebellion risk until it's been held long enough to become core -
  the reason total conquest isn't automatically free or stable.
- **Vassals & puppet states.** A defeated nation can be reduced to a vassal
  (pays tribute, can't declare its own wars) instead of always being fully
  annexed - a middle ground between "destroy" and "ignore."
- **National ideas / a small focus tree.** A handful of one-time unlockable
  bonuses per nation (a Naval Focus, a Trade Focus, a Fortification Focus) -
  smaller and cheaper than the full tech tree, meant purely to make two
  nations that started identically feel different a hundred ticks in.
- **Stability, distinct from Honor.** Honor is how a nation is seen
  internationally; stability is how content its own population is. Low
  stability raises rebellion risk and hurts production - a separate lever
  from "did you break a treaty."
- **Population growth & migration.** Provinces slowly grow population per
  tick (capped by infrastructure); a war-ravaged province could lose
  population, with refugees plausibly able to flee toward peaceful neighbors.
- **Flags & cosmetic identity.** A nation's own color/flag/name flourish -
  mechanically inert, but this is a persistent thing players return to for a
  long time, and "this is *my* nation" matters for that kind of engagement.
- **Wonders.** A rare, expensive, one-per-nation (or one-per-map) building
  with a strong permanent bonus - a long-horizon aspirational goal beyond
  ordinary production buildings.
- **Alliance blocs.** Beyond one-to-one alliances, several nations forming a
  named bloc with shared benefits - a step toward real multi-nation politics
  once multi-member nations exist.

### Economy, governance & military depth - what "basically real life" grows into (part 2)

Brainstormed with Bipin 2026-09-27, same status as the two lists above (not V1,
not a build queue, just ambition written down - a fuller, more visually organized
version of this same content lives in a Claude Docs artifact Bipin has, "the
Dominion Rulebook," but this is the authoritative record). Where the first list is
about conflict and the second is about national identity, this one is about the
economy and government actually running underneath a nation, plus the geography
and military branches that came out of the same conversation.

**Population, jobs & sectors.** Population is assigned by the monarch across
sectors, each gated by a facility built first, more population producing more
output up to that facility's capacity: Agriculture (Farm - Food, feeds population,
no currency), Industry (Factory - a nation's own currency, see below), Education
(School - Research Points), Communications (Post House - Influence, and pollution
as a byproduct, see below), Resources (Mine - Oil/Ore/Timber, tradeable raw goods),
Healthcare (Hospital - cuts Sickness odds/severity, doesn't fully immunize it),
Sanitation (Waste Disposal Plant - reverses pollution). Research Points aren't just
a future tech-tree currency sitting idle - spending them is how a nation unlocks
entirely new sectors and facility types, so investing in education is literally
how a country creates jobs that didn't exist before.

**Pollution -> Fertility -> Agriculture, a real feedback loop.** Communications
facilities produce Influence and pollution together; unchecked pollution degrades
a province's Fertility (a proposed dimension separate from `Terrain`'s existing
yield/defense multipliers, for food/growth potential specifically), and degraded
Fertility cuts that province's Farm output. Sanitation is the only fix, and it's
an ongoing treasury cost, not a one-time purchase - tech growth stops being free.

**Currency: per-nation, and it floats.** Not one shared "Crowns" - every nation
mints its own currency with an exchange rate that floats with its actual
situation: weakens under an active war, unrest/rebellion, a United Nations
sanction, or thin treasury reserves relative to population; strengthens with high
Honor, stability, and a healthy trade balance. Research Points and Influence stay
flat, non-tradeable resources, untouched by this. A nation short on treasury can
take on national debt - borrowed from another nation directly, or a shared fund a
United Nations body maintains - at interest; unpaid debt is exactly the kind of
thing that tanks a currency further.

**Trade between nations.** One nation proposes a goods-for-goods or
goods-for-currency deal to another (same accept/decline shape diplomacy already
uses); a deal priced in another nation's currency converts at that day's exchange
rate. A deal with a nation you don't share a land border with needs a Navy to
carry it (see Geography & military below) - an island nation lives or dies by
this rule. A standing server-wide marketplace instead of only bilateral deals is a
believable later step, not promised here.

**Governance: laws as switches, not dials.** Every law is binary - on or off,
full effect and cost the instant it's flipped, nothing in between. Two concrete
worked examples: Mandatory Vaccination (compulsory child vaccination, strong
Sickness immunity, raises Unrest wherever enforced - the reference example for
what a law actually costs and buys) and Trade Tariffs (skims extra currency off
every trade deal, but other nations trade with you less). National Holidays are a
one-off declaration rather than a standing law - spends a day instead of
Influence, pauses production, drops Unrest. An elected Parliament reacts to the
monarch's laws (a stability bonus when governed with it, extra Unrest when
consistently governed against it) and can formally question a specific act (a
war, a law, a big spend) - the monarch answering it calms Unrest slightly,
ignoring it repeatedly raises it. Parliament never overrules or replaces the
monarch - see the locked "you are the permanent ruler" principle in the Country
concepts section above.

**The United Nations.** Distinct from a Federation (which merges nations into one
entity): nations stay fully independent, no pooled territory or treasury, no
single foreign policy - just a body they've agreed to belong to. Founding it needs
every invited nation to accept; joining an existing one needs a majority vote of
current members. Its main power is Sanctions: members vote to sanction a nation,
blocking it from member trade and weighing on its currency's exchange rate -
ties Diplomacy, Trade, and Currency into one lever instead of three systems that
never talk to each other.

**Geography & military: islands, fortifications, Navy and Air Force.** At
founding, a nation picks Island or Mainland - a real choice, not a dice roll. An
island province can never be reached by a land Army march; only a Navy can land
troops there or blockade it, and the same isolation cuts off land-adjacent trade
and migration until a Navy exists. A Fortress facility adds directly to a
province's defense multiplier (stacking with terrain), and on a coastal or island
province also raises the cost of a naval landing. Navy and Air Force are two new
branches beyond the land Army - not reskinned troops - paid in real ticks to
build rather than instant recruitment, closer in weight to a Wonder. Navy is the
only way to invade/blockade an island or trade across water; Air Force isn't
bound by march adjacency and can cheaply scout a distant province's stats without
needing any relation with its owner. Both get their own Research-funded upgrade
path - the first concrete example for the vague "tech tree" idea in the first
Future Depth list above.

**Migration, made realistic.** The actual driver is unemployment, not a vague
"unrest" pull: population outgrowing a nation's total job capacity across its
built facilities is what creates people looking to leave. They move toward a
*bordering* nation with open capacity, gradually, not to whichever nation is
richest on the map. Open Borders is a law (a switch, like any other) controlling
whether a nation accepts that immigration at all - a war-torn or sickness-ridden
neighbor's refugees stop at the border if it's off.

**A Wonder, with a concrete example.** The Golden Dome: one per nation, an
extreme treasury cost and many ticks to build, dwarfing any ordinary facility -
most nations will never casually build one. Reward is a permanent, visible
prestige structure with a strong Honor and stability bonus. Represents Wonders
generally; later ones can reuse the same extreme-cost/permanent-prestige
template with their own flavor.

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

1. ✅ **Done.** Server-side data model + `DominionTickEngine` core resolution
   logic, proven with unit tests (a fixed scenario: two nations, a declared war,
   an army march, a combat outcome, a province flipping ownership) - no
   networking or UI yet, same "prove the core logic in isolation first" approach
   `ai/search`'s `Minimax` took.
2. ✅ **Done.** Persistence (`DominionStore`, its own isolated flat file, per the
   Architecture Decision - not reusing `ServerAccountStore`'s format for an
   unrelated domain).
3. ✅ **Done.** Networking: new message types for founding a nation, viewing
   the map, queuing orders, viewing diplomacy state - additive to
   `MessageType`, doesn't touch any existing game's protocol. First slice
   (2026-09-26): `DOMINION_FOUND_NATION_REQUEST`/`RESPONSE` and
   `DOMINION_STATE_REQUEST`/`RESPONSE`, via a new whole-server `DominionManager`
   (see `PROGRAM_STRUCTURE.md`) - also the point where `dominion` joined
   CLAUDE.md's byte-identical client/server sync-rule list, since `Message`/
   `ClientHandler` (both already on it) started referencing `dominion.*` types
   directly. Second slice, same day: recruit army (`DOMINION_RECRUIT_ARMY_*`),
   queue a march (`DOMINION_QUEUE_MARCH_*`), declare war
   (`DOMINION_DECLARE_WAR_*`), and propose/respond to an Alliance or
   Non-Aggression proposal (`DOMINION_PROPOSE_RELATION_*`/
   `DOMINION_RESPOND_PROPOSAL_*`, backed by a new `DiplomaticProposal` class -
   the one piece of V1 state that's deliberately NOT broadcast in the full
   world snapshot, since a pending proposal is private between the two
   nations involved even before real fog of war exists). All account-
   validated server-side (an army/province a client claims to own is always
   re-verified, never trusted).
4. Client: the persistent nav tab (already a settled architecture decision), a
   map view, a nation dashboard, an orders-queue UI.
5. Only then: multi-member nations, additional resource types, unit-type variety,
   vassalage/trade, and the procedural ruler/succession layer - each a real,
   separately-scoped follow-up, not bundled into getting V1 playable.
