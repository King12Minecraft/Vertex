# Vertex — AI Assistant Briefing

Read this first. It's meant to bring any AI assistant (or a new session of you)
up to speed on this project fast, without re-deriving everything from scratch.
If you're a person, not an AI, [`HOW_VERTEX_WORKS.md`](HOW_VERTEX_WORKS.md) is
the file written for you instead.

## What this project is

Vertex is a self-hosted multiplayer game platform (Java/Swing, built via BlueJ),
owned and run by **Bipin** (King12Minecraft on GitHub). ~50 original games, an
economy (coins/cosmetics/achievements), full social features (friends/chat/
parties/moderation), and a client-server architecture where anyone can run their
own independent server for their own group of friends — no central "Vertex Inc."
service, no cross-server anything. One paragraph more: [`HOW_VERTEX_WORKS.md`](HOW_VERTEX_WORKS.md).
Full detail: [`FEATURES.md`](FEATURES.md), [`PROGRAM_STRUCTURE.md`](PROGRAM_STRUCTURE.md).

## Where to look for what

| Question | Read this |
|---|---|
| What does Vertex do today, in full detail? | `FEATURES.md` |
| What's in progress / planned / deliberately not built, and why? | `ROADMAP.md` |
| How is the code organized, package by package? | `PROGRAM_STRUCTURE.md` |
| Plain-language "how does this whole thing work" | `HOW_VERTEX_WORKS.md` |
| Open questions that need Bipin's actual decision, not a guess | `BLOCKED_QUESTIONS.md` |
| Historical changelog (what got built each round, and why, going back to the start) | `VertexClient/DEVELOPMENT_LOG.md`, `VertexServer/DEVELOPMENT_LOG.md` |
| `Vertex: Dominion`'s design (warfare/diplomacy/economy/V1 scope/build order) | `DOMINION_DESIGN.md` |

`ROADMAP.md` is the one to check first for "what should I work on" — it has a
live "Done" section (most recent work first), an "In Progress" section, and
"Planned" sections broken out by area. It is kept current after essentially
every change; trust it over your own assumptions about project state.

## Non-negotiable architecture facts

- **Two projects, `VertexClient/` and `VertexServer/`, not one.** `VertexClient/`
  is the edit-source-of-truth for anything shared between them (networking, game
  rule engines, account/economy/social logic). **The sync rule:** every file
  under `net`/`account`/`social`/`admin`/`economy`/`games` (except each game's
  Window/Dialog classes, which are client-only) must stay byte-identical between
  the two trees. Edit in `VertexClient/`, copy the same file to the matching path
  in `VertexServer/`, diff to confirm. This has already caused two real bugs this
  project (a practice-reward fix and a `ClientHandler.java` drift) from someone
  forgetting the second half of that rule — check with `diff`, don't assume.
- **Hosting is `VertexServer.jar`-only, deliberately.** The ordinary client
  (`VertexClient.jar`) has no way to start a server from inside the app — that
  feature (`HostServerDialog`, a "Start Hosting" Settings button) was removed
  2026-09-25 at Bipin's explicit request. Do not reintroduce a client-side
  hosting feature without being asked.
- **Every game is original.** Some are inspired by well-known genres (a
  social-deduction game in the spirit of Among Us, a maze-chase in the spirit of
  Pac-Man) but built from scratch — no copied assets, art, or source from any
  other game, ever.
- **The embedded-games pattern.** Every game opens inside `MainMenu`'s
  `CardLayout` game-host slot via `EmbeddedGamePanel`/`GameWindowFactory`, not
  its own separate window/frame. This is a completed, platform-wide convention —
  any new game follows it from day one, no exceptions.
- **Server is sole authority.** Every match result, coin award, and privileged
  action is decided and re-verified server-side — a client can request something,
  never unilaterally grant it to itself. Don't design a feature that trusts a
  client-reported value for anything that matters (score, balance, role, etc.).
- **LAN-only for now, and no code signing on auto-update - both known, open
  security gaps**, tracked honestly in `BLOCKED_QUESTIONS.md`, not hidden. Don't
  "fix" either with a unilateral guess - they're real architecture/key-management
  decisions that need Bipin's input. A safe, non-architectural mitigation (like
  the deserialization allow-list filter, or the update-download hash check) is
  fine to add on your own judgment; changing the trust model itself is not.
- **Voice chat is explicitly out of scope.** Don't propose or build it unless
  Bipin asks again - it was deliberately cut from the roadmap.

## Working conventions established this project

- **Audit before building.** Before adding a feature, check whether it already
  exists under a different name (a maze-chase game already covers "Pac-Man"; a
  reward formula might already exist but just not be wired up). Several real bugs
  this session were "the fix already existed, it just wasn't being called" - find
  those before writing new code.
- **Update docs in the same unit of work as the code change**, not as an
  afterthought - `ROADMAP.md`'s "Done" section gets a new entry, `PROGRAM_STRUCTURE.md`
  gets updated if a class's role/behavior changed, every single time. This is
  established, not optional.
- **Verify before claiming done.** This project's standard is: compile both
  trees clean, then a real check beyond "it compiles" - a logic/unit test for
  pure logic, an Xvfb+Swing screenshot harness for anything visual (undecorated
  `JFrame` + `RoundedPanel` host, `host.printAll(g2)` into a `BufferedImage`,
  always call `System.exit(0)` explicitly at the end or the JVM hangs on a
  non-daemon AWT thread). Scratch test harnesses are throwaway - write them in a
  scratchpad/temp location, not committed to the repo.
- **Small, honestly-scoped commits over big rushed ones.** If something is
  genuinely too large/risky for one sitting (real-time position sync, a match
  reconnection/grace-period system, a full jar-signing scheme), it's fine to
  audit it, document the finding and a concrete plan in `ROADMAP.md`, and
  deliberately not build it yet - stated plainly, not silently skipped.
- **Never guess on a real design/architecture decision.** Record it in
  `BLOCKED_QUESTIONS.md` with enough context to answer quickly, make a
  reversible default assumption if one exists, and keep moving on independent
  work rather than stalling. Don't let one blocked question block everything else.
- **Commit messages explain *why*, not just what**, and end with the repo's
  standard attribution footer (see any recent commit for the exact format).

## Standing schedule (if you're an autonomous/scheduled session)

Bipin runs autonomous work sessions on Monday/Wednesday/Friday nights, roughly
11 PM–5 AM IST (he's in India), self-scheduled via a recurring trigger -
cut back from every night (2026-09-26) specifically to control weekly usage;
don't unilaterally add more nights back without him asking. Standing
instructions for those sessions: work continuously through `ROADMAP.md`'s
backlog and the games concept backlog; never stop for a blocked question
(record it, make a reversible assumption if one exists, move to independent
work); self-pace check-ins at roughly 45–60 minutes apart, not the tighter
20–30 minutes used earlier in this project (fewer, larger check-ins means
fewer billed round-trips for the same work) - a shorter gap is fine only when
genuinely waiting on something time-sensitive (a build, a test run); pace
token/usage consumption to use at most ~25–30% of the budget by the end of
the window (tightened 2026-09-26 from "leave roughly half unused" - same
weekly-usage reason as the schedule cutback), stopping at a clean checkpoint
once that cap is reached even if the window isn't over rather than continuing
to spend down the rest; keep the project buildable and fully committed+pushed
after every unit of work, never leave a broken intermediate state on `main`.

## A note on git hygiene

When staging many files across a batch of changes, verify `git status` shows a
clean tree (nothing unexpectedly still modified) **after** committing, not just
before - a bad pathspec or partial `git add` can silently fail and leave a commit
incomplete without erroring loudly. This project has hit that exact mistake once
(a hosting-removal commit that deleted a class but left a now-broken reference to
it, caught and fixed as an immediate follow-up commit) - don't repeat it.
