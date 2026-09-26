# Vertex — Roadmap

The live, currently-maintained plan for what's built, in progress, and coming next.
(`GAMEHUB_MASTER_INSTRUCTIONS_v2.md`, `GAMEHUB_ROADMAP_v3.md`, and
`GAMEHUB_ALL_IDEAS_v4.md` are earlier historical planning docs, already marked
superseded — this file replaces them as the one to check for current plans.)

For what already exists and works today in detail, see [`FEATURES.md`](FEATURES.md)
and [`PROGRAM_STRUCTURE.md`](PROGRAM_STRUCTURE.md). This file is about what's
*next*, not a feature list.

A senior-architect-level strategic analysis of the whole platform (biggest
opportunities, 30+ new ideas beyond this roadmap, architecture breakthroughs,
security/reliability/scaling considerations, a persistent-game architecture proposal,
commercial analysis, and a dependency-ordered priority sequence) lives in a separate
Claude Docs artifact the user requested and reviewed - not duplicated here since it's
long-form analysis rather than a maintained plan, but its settled decisions get
recorded below as they're confirmed.

## 🏛️ Architecture Decisions

- **`Vertex: Dominion` gets its own persistent navigation tab**, not the `MainMenu`
  `CardLayout` game-host slot every other game uses. Confirmed by the user. Rationale
  (from the strategy analysis above): Dominion is a standing, server-scheduled
  daily-tick nation game with no "end" - it's checked in on repeatedly like a
  management sim, not launched-and-left like a match, so it needs a permanent nav
  destination (alongside Social/Settings) rather than being bent into the
  launch-and-play pattern just standardized across all 49 games this session. It
  should still share identity/social/friends with the rest of the platform, but keep
  its own isolated world-state store, currency, and tick-scheduler - not the match
  economy or `GameSession` lifecycle. Not yet implemented; recorded here as a settled
  design constraint for whenever Dominion work begins.
- **Nightly autonomous work sessions, 11:00 PM-5:00 AM IST, self-scheduled via a
  recurring trigger.** Work order: Audit → Architecture → Core Systems → Games →
  Multiplayer → Social → Progression/Economy → UI/UX → Security → Performance →
  Reliability → Accessibility → Distribution → Documentation → Testing → Final
  Polish, then repeat with a fresh refinement pass once the backlog is clear. Blocked
  questions needing the user's input are recorded in
  [`BLOCKED_QUESTIONS.md`](BLOCKED_QUESTIONS.md) rather than stopping the session;
  everything else keeps moving. Every session ends at a clean, buildable, committed
  checkpoint.

---

## ✅ Done

- **`GameDetailDialog` fills the owner window instead of floating as a small fixed-size
  card** - the other half of the "Also still wanted" note under the embedded-games
  rollout, alongside reconnection above. Now sizes itself to match the owner window
  (falls back to a sane fixed size if there's no shown owner yet) with a centered
  content card on a full backdrop, rather than a tiny 420x460 popup - genuinely feels
  like a real step in the Discover -> Details -> Play flow now, not an afterthought
  overlay. Used the extra room for real information `GameInfo` already tracks but this
  screen never surfaced: a live queue-count badge ("3 players waiting") for online
  games with players currently queued, and a "Spectatable" badge for the 3 games that
  support it (Chess, Rock Paper Scissors, Battleship). Still a modal `JDialog`, not a
  true embedded `CardLayout` step the way Mode/Lobby/Playing are for every game - doing
  that would mean auditing and rewriting all 3 of this dialog's call sites to hand off
  into `MainMenu`'s game-host slot instead, a separately-scoped, larger change recorded
  below rather than folded into this pass.
  Found and fixed two real layout bugs while verifying visually, not shipped guessed:
  (1) the same stretch-not-center `BorderLayout.CENTER` bug this session has hit
  repeatedly on game boards, here on a dialog - the content's `BoxLayout` block was far
  shorter than the enlarged dialog's height, and since a plain `JLabel`/`JPanel`'s
  default maximum size is unbounded, `BoxLayout` distributed the leftover space as a
  gap in the *middle* of the content instead of leaving it at the bottom. A
  `GridBagLayout`-with-`weighty` wrapper (the usual fix for this class of bug elsewhere
  in the app) reliably undersized the content below its own reported preferred height
  for reasons not worth chasing further - switched to the simpler, more predictable
  `BorderLayout.NORTH` idiom instead (guarantees preferred height, stretches only
  width), which had neither problem. (2) A genuine Swing quirk: an HTML `JLabel`'s CSS
  `width` reliably constrains where the text *wraps*, but its own `getPreferredSize()`
  can still report a wider value than that - the inflated width bubbled up through the
  layout chain and, since the art banner was the only component with real
  compressible slack, visibly squashed it to a sliver to compensate. Fixed at the
  source by overriding just the label's reported preferred width (keeping Swing's own
  correctly-wrapped height) rather than chasing the symptom through the layout tree.
  Verified with an Xvfb/Swing harness (including a raw component-bounds dump used to
  root-cause both bugs against real numbers rather than guessing from screenshots
  alone) confirming: the dialog matches its owner's size, the art/name/tags/
  description all render at their correct sizes with no gap or squashing, the queue-
  count and spectatable badges appear correctly and only when applicable, and Chess's
  "Hard" difficulty color and Tic-Tac-Toe's "Easy" both render correctly. Single
  client-only file (`*Dialog` classes aren't part of the client/server sync
  discipline); compiles clean. All 3 existing call sites (`GameLauncher`, two in
  `GamesPanel`) untouched - this only changed what happens inside `GameDetailDialog
  .show(...)` itself.
- **Reconnection grace period - built and proven on Tic-Tac-Toe, the "prove it on one
  game first" step the audit deliberately deferred earlier this session.** Every
  online match previously ended the instant either socket dropped - a genuine quit and
  a one-second wifi hiccup were indistinguishable, both an immediate forfeit. Built a
  generic `games.ReconnectRegistry` (keyed by accountId, since a brand-new
  `ClientHandler`/socket exists on reconnect with no continuity except the same
  account logging back in) that any match type can adopt via a small
  `ReconnectableMatch` interface (`onReconnectTimeout()`, `onReconnect(newHandler)`,
  `attachToHandler(handler)`), and wired `TicTacToeMatch` up to it as the one concrete
  proof this generalizes - same "shared engine, proven on one real game" approach
  `ai/search`/`engine` both used before scaling out.
  How it works: a disconnect from a player with a real account (guests still forfeit
  immediately - no stable identity to reconnect against) freezes the match instead of
  ending it - the remaining player gets a new `OPPONENT_DISCONNECTED_NOTICE` push
  ("waiting to reconnect, up to 45s"), and the server independently rejects any move
  attempt during the freeze (never trusts the client's own input-blocking). If that
  account logs back in within 45 seconds, `ClientHandler.handleLogin()` calls
  `ReconnectRegistry.tryReconnect()`, which swaps the stale `ClientHandler` reference
  inside the match for the new one, re-attaches the new handler's `currentMatch` field
  (so a *second* disconnect on the resumed session is still correctly handled - a real
  gap caught and fixed during design, not just the happy path), and hands back
  everything the login response needs to resume play. The client reconstructs the
  equivalent of a fresh `MATCH_FOUND` + `MATCH_UPDATE` locally from the login
  response's fields and feeds them straight to a newly-built game window - deliberately
  NOT a separate server push to the reconnecting client's own socket for this, since
  tracing the exact call order proved a real race: `onReconnect()` writing directly to
  the new socket during `handleLogin()` would go out on the wire *before* the
  `LOGIN_RESPONSE` itself (both writes happen inside the same synchronous `handle()`
  call), meaning that push could arrive and be silently dropped before any window
  exists to receive it. If the grace window expires with no reconnect, the match
  finalizes exactly as before (`OPPONENT_LEFT`, remaining player awarded the win).
  Two lock-ordering hazards found and fixed by design, not by luck: (1)
  `TicTacToeMatch.handleDisconnect()` must call `ReconnectRegistry.beginGracePeriod()`
  only *after* releasing its own monitor, since `tryReconnect()` and the grace-timer's
  own callback both acquire the registry's lock first and then the match's - nesting
  the other way anywhere would risk a classic two-thread deadlock; (2) a
  both-players-disconnect-during-the-same-grace-period edge case (finalizes cleanly
  with no reward, nothing left dangling) is handled explicitly rather than by accident.
  Verified with a 23-check state-machine test (`ReconnectionTest.java`, using a real
  `ClientHandler` subclass built via its actual constructor with every manager
  dependency null - safe since that constructor is pure field assignment - so every
  assertion runs against the real production classes) covering: the ordinary happy
  path is unaffected, a guest disconnect still forfeits immediately, a real reconnect
  correctly swaps the handler and resumes play (confirmed by having the *new* handler
  actually make a move, not just checking the returned data), a grace-period timeout
  finalizes correctly, a reconnect attempt after the window closed finds nothing
  pending, both-players-gone finalizes cleanly, a move during the freeze is rejected
  server-side, and the re-attached handler's own later disconnect is correctly
  honored. Plus an Xvfb/Swing visual check confirming the resumed board renders with
  the right pieces and correct turn state, and that the pause/unpause status text
  updates correctly in a live match. Mirrored byte-identical across both trees; both
  compile clean. Scoped deliberately to Tic-Tac-Toe only tonight (not all ~30 match
  types) - the remaining games can adopt the same registry by implementing
  `ReconnectableMatch`, following this file as the template, whenever that's picked up
  next; nothing about the shared registry itself needs to change to support them.
- **Reliability audit: 3 real fixes** — the methodology's Reliability pass, following
  Security and Performance. An audit of exception-handling blast radius, resource
  leaks, thread-safety of shared managers, and server startup/shutdown robustness
  found the platform's error handling is mostly solid (every spot-checked match
  handler already null/bounds-checks before use, and every heavily-shared manager's
  mutators are already `synchronized` with no check-then-act races), but three
  genuine bugs:
  1. **`ChatManager.unregister()` had no identity check** - it deleted the
     `username -> ClientHandler` mapping unconditionally. Nothing currently stops the
     same account logging in twice concurrently (a separate, deliberately untouched
     question - see below); if that happened, the second login's `register()` call
     overwrites the first's mapping, and if the *first* (now-stale) session
     disconnects afterward, its `unregister()` call would delete the *second*,
     still-live session's mapping - silently making that connected player unreachable
     by username (private messages, friend notices, mod ban/kick-by-username, party
     invites all fail quietly) until they happened to re-register. Fixed by only
     removing the mapping if it still points at the disconnecting client. Deliberately
     did NOT add concurrent-login prevention itself - that's a real trust-model
     question (does an account get kicked from its old session on a new login, or
     could that break a future reconnect-after-a-flaky-connection flow the roadmap
     already has drafted?) rather than something to guess at alongside a reliability
     bugfix.
  2. **`GameServer.acceptLoop()` died on the first transient accept() error.** Any
     `IOException` from `serverSocket.accept()` - even a transient one like "too many
     open files" under a connection burst - broke the accept loop forever, while the
     server process itself keeps running indefinitely (`ServerMain` just sleeps) -
     so the server would look alive from the outside while silently refusing every
     new connection, needing a manual restart to notice and fix. Fixed to only break
     when the socket is actually closed (today that never happens deliberately - no
     shutdown hook exists yet - so this exclusively removes the crash-on-transient-
     error behavior); any other IOException is logged and the loop keeps accepting,
     with a brief pause to avoid a tight spin if the same error recurs immediately.
  3. **`AvatarStore.save()`/`load()` leaked file handles on an I/O error mid-operation**
     - `close()` was a plain sequential call after the read/write, so an exception
     partway through skipped it. Fixed with try-with-resources on both methods.
  Also confirmed real: server save methods (`ServerAccountStore`, `FriendManager`,
  etc.) are synchronous write-through on every mutation with no batching/debounce, so
  a hard kill was never a data-loss risk beyond the single in-flight write - no fix
  needed there.
  Verified: a throwaway test (`ChatManagerTest.java`, using `Unsafe.allocateInstance`
  to get two distinct `ClientHandler` references without needing their full
  ~40-dependency constructor) reproduces the exact double-login race and confirms the
  second session's mapping survives the first session's stale `unregister()` call; a
  second throwaway test (`AcceptLoopLogicTest.java`) replicates `acceptLoop`'s control
  flow with a scriptable fake `accept()` and confirms the loop survives repeated
  transient failures and only stops once the socket is actually closed. Mirrored
  byte-identical across both trees; both compile clean.
- **Performance Mode: closed the gap where 7 real-time games ignored the toggle** —
  audited earlier this session and deliberately deferred behind the security pass;
  picked back up now that the methodology reaches Performance/Reliability.
  `PerformanceMode.getTickIntervalMs()` halves a real-time game's tick rate (60fps ->
  30fps) when the low-end-hardware toggle is on, and PROGRAM_STRUCTURE.md already
  documented this as applying to "real-time games' tick intervals" in general - but
  `BrickBreakerWindow`, `FlappyBirdWindow`, `GalaxyDefenderWindow`,
  `BubbleShooterWindow`, `MazeChaseWindow`, `HillClimbWindow`, and (found during this
  pass, not in the original 6) `WhackAMoleWindow` all constructed their `Timer`/
  `GameLoop` with a raw hardcoded interval, never calling the shared helper - so
  Performance Mode silently did nothing for any of them, exactly the "claimed but not
  actually wired up" pattern this session has repeatedly found elsewhere. Fixed by
  wrapping each one's existing tick constant in `PerformanceMode.getTickIntervalMs(...)`,
  the same one-line pattern already used correctly by `SnakePanel` and 7 other games.
  Deliberately left `AirHockeyMatch.physicsTimer` alone - that's a server-authoritative
  shared match simulation (a `*Match` class, not a client-only `*Window`), and
  `PerformanceMode` is explicitly a local, per-computer client display preference; it
  running inside the server process would mean one machine's own weak-hardware setting
  altering the match's physics rate for every connected player, not a Performance Mode
  bug to fix. Verified with an Xvfb/Swing screenshot harness: `BrickBreakerWindow`
  rendered correctly with Performance Mode both off and on, and directly confirmed the
  fix is real (not just compiling) - the ball visibly traveled a shorter distance in
  the on-screenshot after the same 600ms wall-clock delay, proving the Timer is
  actually ticking at half rate; `WhackAMoleWindow` also confirmed to render correctly
  post-fix. All client-only `*Window` changes, so no `VertexServer` mirror needed;
  compiles clean.
- **Security: chat/social flood protection + a group-creation CPU-exhaustion fix** — an
  audit (part of the same "harden security like crazy" pass) found `ClientHandler.run()`'s
  read loop has zero built-in throttle: a client could spam private messages, group
  messages, group creation, or friend requests in a tight loop at no cost. Deliberately
  did NOT add a blanket per-connection rate limit across every message type, since
  real-time games (Among Us, Fight Arena, etc.) legitimately send frequent state-update
  messages and a blanket cap risks throttling normal gameplay - instead added a small
  per-connection sliding-window limiter (`ClientHandler.isFloodLimited(bucket, max,
  windowMs)`) scoped only to social actions: chat (private + group messages share one
  bucket so switching message type doesn't dodge the cap; 10/5s), group creation (3/60s),
  and friend requests (5/30s), each independent so spamming one doesn't false-trigger
  another's cooldown. The audit also found a genuine CPU-exhaustion vector completely
  separate from rate limiting: `GroupChatManager.createGroup()` looped over the client-
  supplied member list with no size cap, calling a lookup per entry - a single
  GROUP_CREATE_REQUEST with an enormous member list (still well within
  VertexSerializationFilter's much looser global limits) would force a huge amount of
  server CPU work from one request. Fixed with a `MAX_REQUESTED_MEMBERS = 50` cap
  independent of the flood limiter (real groups never need more invitees than that
  anyway). The same audit confirmed two other candidate areas were already solid:
  every other server-side file write/read uses a fixed filename, not a client-controlled
  one (no other path-traversal surface remains beyond the already-fixed AvatarStore), and
  6 spot-checked admin/moderator actions all correctly re-verify the caller's role
  server-side rather than trusting the client. Verified: a throwaway test
  (`FloodTest.java`) confirms a 500,000-entry requested-member list still returns in a
  few milliseconds instead of iterating in full; a standalone copy of the sliding-window
  algorithm (`SlidingWindowLogicTest.java`) confirms N sends are allowed, the N+1th is
  blocked, access returns once the window passes, and separate buckets don't cross-
  contaminate. Mirrored byte-identical across both trees; both compile clean.
- **Security: login lockout made actually temporary, plus closed a username-enumeration
  oracle** — two more findings from the same hardening pass as the username-format fix
  below. (1) `attemptLogin()`'s lockout was a permanent flag (`Map<String, Boolean>`)
  that nothing ever cleared - the client-facing message said "temporarily locked" but an
  attacker who knew or guessed any real username could permanently deny that player
  login with 5 wrong passwords, with no recovery short of a server restart. Fixed by
  storing a lockout *expiry timestamp* (`Map<String, Long>`, 15 minutes) instead of a
  boolean - `attemptLogin()` now clears an expired lockout and gives that key a clean
  attempt count. (2) A missing username returned `NO_SUCH_ACCOUNT` immediately, before
  the attempt-counting/lockout logic ran - an unlimited, unthrottled way to check which
  usernames exist on the server. Fixed by routing a missing account through the same
  counter/lockout path as a wrong password for that same key, and by unifying the
  client-visible message for both cases to "Incorrect username or password." (previously
  "No account with that username." vs "Incorrect password." - a direct tell). Verified
  with a throwaway test (`LockoutTest.java`): confirms 4 wrong attempts stay
  `WRONG_PASSWORD`, the 5th locks the account, a correct password is still rejected
  while locked, access is restored once the lockout window passes (simulated by
  rewinding the stored expiry rather than sleeping 15 real minutes), and a nonexistent
  username also locks out after 5 attempts instead of responding forever.
- **Security: username format validation, closing a path-traversal + save-file-corruption
  hole** — `ServerAccountStore.createAccount()`/`changeUsername()` only ever checked
  username *length* (≥3 chars), with zero check on which characters were allowed. Two
  real, independent exploits shared that one root cause: (1) `AvatarStore.fileFor(username)`
  builds a filesystem path directly from the raw username (`new File(AVATAR_DIR,
  username.toLowerCase() + ".png")`) - a username like `../../etc/passwd` or containing
  `/` would let a client write (or later read) a PNG-content file *outside* the avatars
  folder, a classic path-traversal write; (2) `ServerAccountStore.save()`/`load()` persist
  accounts in a hand-rolled `|`-delimited line format - a literal `|` in a username would
  silently corrupt that account's saved line and shift every field after it on the next
  server restart. Fixed with one shared choke point: `ServerAccountStore.isValidUsernameFormat(String)`,
  a `^[A-Za-z0-9_-]{3,20}$` regex, enforced at all three points a username can be set or
  used - `ClientHandler.handleCreateAccount()` (new `USERNAME_INVALID_FORMAT` error path,
  since account creation doesn't route through `changeUsername`), `ServerAccountStore.changeUsername()`
  itself, and defense-in-depth inside `AvatarStore.save()`/`load()` (independent backstop
  even if a caller upstream ever forgets to check). Verified with a throwaway test
  (`UsernameValidationTest.java`) exercising 22 cases - every path-traversal/pipe/space/
  null-byte/too-long/too-short variant rejected, every normal alphanumeric/underscore/
  hyphen username of valid length still accepted. Prompted by an explicit "upgrade
  security like crazy" request ahead of a friend attempting to penetration-test the
  platform.
- **Practice-mode AI for 11 games** — a shared `ai/search` engine (Minimax/alpha-beta)
  powering Connect Four, Reversi, Dots and Boxes, Checkers, Chess, and Signal Grid,
  plus 5 bespoke bots for games that don't fit that shape (Word Duel, Dice Duel,
  Fusion Grid, Memory Match). One reusable engine, not one-off AI per game — built to
  scale to hundreds of future games.
- **"Aurora Glass" reskin of the shared app shell** — buttons, toggles, base palette,
  sidebar, hero banner, top bar, game mode cards, profile header, dialogs. Every
  individual game's own in-game screen is untouched, as scoped, except the eventual
  flagship.
- **Build script** (`build.sh`/`build.bat`) — compiles and packages both projects into
  their jars with a proper manifest, replacing BlueJ's own export as the way that
  happens. BlueJ itself still works fine as an alternative.
- **`VertexServer` made genuinely headless** — no more embedding the full client UI;
  stripped from 353 files to 99 (found by compiling the entry point and keeping only
  what the compiler pulled in, not guesswork). Fixes a real crash risk on a
  real/cloud deployment with no display.
- **`VERSION` file + semver** — single source of truth for the version number, stamped
  into both jar manifests, ready to be read by the future website too.
- **`.gitignore` for server runtime data**, with bug reports/suggestions carved out as
  the one exception meant to flow back to the developer (see `Sync-Feedback.sh`/`.bat`).
- **Mandatory game rules/detail page before every Play** — `GameDetailDialog` is now
  the one gate every "Play" entry point goes through; no way to skip straight into a
  match without seeing the rules/controls first.
- **Moderator chat** — a live, server-role-gated staff-only channel.
- **Party Mode** ("funny effect mode") — an off-by-default toggle for a cosmetic
  cursor trail + confetti-on-win, purely decorative.
- **Embedded games proof-of-concept (Chess)** — every game used to open as its own
  separate `JFrame` window; Chess now embeds inside the main launcher window instead,
  via a new `Pages.GAME_HOST` slot in `MainMenu`'s existing `CardLayout`
  (`MainMenu.showGame(...)`/`returnToGames()`), with `games.EmbeddedGamePanel` as the
  replacement for the old windowClosing confirmation (MainMenu calls
  `requestLeave()` before navigating away mid-match, e.g. a Sidebar click). Verified
  visually under Xvfb: the actual board fills the available space (BorderLayout's
  CENTER region stretches it automatically, no extra sizing code needed) and the
  mode-select screen was re-centered in its own available space rather than left
  pinned in the top-left corner with dead space around it. Also fixed a real ordering
  bug caught during this conversion: the old code called `dispose()` unconditionally
  after a match-over dialog, even when a rematch had just swapped in a brand new
  window — the embedded version would have made that swap-then-immediately-undo
  visible as a jarring flicker back to the games list, since both actions now touch
  the same single game-host slot; fixed by only calling `returnToGames()` when no
  rematch was requested.
- **Reversi converted to the embedded pattern** — second game done this way, proving
  the pattern generalizes to a game with real differences from Chess (no existing
  leave-confirmation dialog to preserve/adapt, no existing Leave button on its board
  screen, its "Play Again" reuses the same panel instead of creating a new one).
  Found and fixed a new wrinkle Chess didn't have: Reversi's board is one
  custom-painted `JPanel` at a hardcoded pixel size (unlike Chess's plain
  `GridLayout(8,8)`), so it stayed pinned in a corner with dead space around it once
  embedded — fixed with the same `GridBagLayout` auto-centering wrapper already used
  for mode-select screens, applied to the board too.
- **Connect Four converted to the embedded pattern** — third game done this way, same
  shell shape as Reversi (no existing leave confirmation, fixed-pixel board needing
  the same centering fix), converted with no new surprises.
- **Signal Grid converted to the embedded pattern** — fourth game done this way; its
  board screen has an extra wrinkle (board + a row of 4 direction-fire buttons stacked
  together, not just a bare board), handled by wrapping that whole stacked group in one
  `GridBagLayout` centering wrapper rather than centering the board alone.
- **Tic-Tac-Toe converted to the embedded pattern** — fifth game done this way, and the
  first with pre-existing quirks worth preserving as-is rather than "fixing" mid-
  conversion: its practice mode never unregistered its network push listener even as
  its own window, so `requestLeave()` keeps that exact behavior rather than becoming
  more correct than the original by accident. Also caught a new variant of the by-now-
  familiar centering bug: its board was already wrapped in a centering container, but
  that container used `FlowLayout` (which only centers horizontally - vertically it's
  always top-aligned), so the board still sat pinned to the top of the much taller
  embedded space with dead space below it. Same fix as always - swapped for
  `GridBagLayout`, which centers on both axes.

- **Dots and Boxes converted to the embedded pattern** — sixth game done this way, same
  shape as Reversi/Connect Four/Signal Grid (no existing leave confirmation, fixed-pixel
  board needing the centering fix), no new surprises.

- **Checkers converted to the embedded pattern** — seventh game done this way and the
  last of the original `ai/search`-backed practice-mode games; same shape as
  Reversi/Connect Four/Signal Grid/Dots and Boxes, no new surprises. All 6 games with
  the shared `ai/search` engine are now embedded, alongside Chess.

- **Snake converted to the embedded pattern** — eighth game done this way, and the
  first offline/single-player game (no matchmaking, no network match to confirm
  leaving). Found and fixed a real regression while converting it: Snake is also the
  one game reachable from `OfflineHubWindow`'s pre-login "Play Offline" screen, where
  `MainMenu` doesn't exist yet - hardcoding `MainMenu.getInstance().returnToGames()`
  the way every other converted game does would have thrown a `NullPointerException`
  the moment a logged-out guest tried to leave a Snake game. Fixed with a
  `setReturnAction(Runnable)` escape hatch: defaults to the usual
  `MainMenu.getInstance().returnToGames()` when unset, but `OfflineHubWindow` now
  supplies its own small `CardLayout`-based "go back to the offline hub" callback
  instead, since it's a standalone `JFrame` with no `MainMenu` shell to hand off to.

- **10 more single-player offline games converted in one batch** — 2048, Minesweeper,
  Sudoku, Simon Says, Whack-a-Mole, Gem Match (Match Three), Lights Out, Peg Solitaire,
  Mancala, and Klondike Solitaire. All share the same simple shape (one screen, no
  matchmaking, `requestLeave()` with nothing to confirm), so the same recipe applied
  cleanly to every one: a `GridBagLayout` centering wrapper around the board (several
  of these were direct `BorderLayout.CENTER` children before - which stretches rather
  than centers a fixed-preferred-size board, the same class of bug found during the
  Reversi/Tic-Tac-Toe conversions, just discovered fresh in each of these since none
  had been embedded before), and a new "Leave" button added next to whatever
  restart/new-game button already existed. 18 games embedded total now.
- **10 more offline single-player games converted in a second batch** — Dino Dash,
  Tetris, Ping Pong, Crossing Road, Aim Trainer, Puzzle Quest, Yahtzee, Brick Breaker,
  Flappy Bird, and Galaxy Defender. Same recipe as the first offline batch throughout
  (`GridBagLayout` centering wrapper, a new "Leave" button, `requestLeave()` stopping
  whatever Swing `Timer` drives the game loop where one exists). Several of these
  (Dino Dash, Tetris, Ping Pong) had no "Leave"/"Close" affordance at all before -
  relied entirely on the OS window's close button, same gap Snake had - so this batch
  is where that pattern (wrap the play panel, add a bottom row with just a Leave
  button) got reused most. Yahtzee's center column (dice + Roll button) used
  `BoxLayout` inside `BorderLayout.CENTER` - stretches to fill the region without
  actually centering its content vertically, a `BoxLayout` cousin of the `FlowLayout`
  top-alignment bug found during the Tic-Tac-Toe conversion - fixed the same way,
  wrapped in a `GridBagLayout` centerer. 28 games embedded total now.
- **Bug found and fixed: `SpectateDialog`'s Chess "Watch" button was silently broken**
  by the original Chess embedding conversion — it still called
  `new ChessWindow(matchId); window.setVisible(true)`, which does nothing once
  `ChessWindow` is a `JPanel` instead of a `JFrame`. Caught by a deliberate sweep
  (grepping for `new <Window>(` across the whole client, excluding each window's own
  file and `GameLauncher.java`) run before converting Rock Paper Scissors, since RPS
  has the same spectate-window pattern in the same file. Fixed by routing through
  `MainMenu.getInstance().showGame(...)` like every other entry point. Worth
  remembering for every future conversion: check for *every* external construction
  site of a game window, not just `GameLauncher`'s.
- **Rock Paper Scissors converted to the embedded pattern** — 29 games embedded total
  now. More involved than the earlier batches: three ways into the same window (normal
  play, spectating an in-progress match via `SpectateDialog`, and a rematch-wait
  screen), plus a rematch flow with the same "swap-then-immediately-undo" risk found
  during the original Chess conversion (a rematch spawns a brand new embedded window,
  so the old window's own cleanup must skip `returnToGames()` when a rematch was just
  requested - fixed with the same `boolean[] rematchRequested` flag Chess uses).
  `SpectateDialog`'s RPS spectate line got the same `MainMenu.getInstance().showGame(...)`
  fix as the Chess bug above, fixed proactively this time instead of shipping broken
  first.
- **3 more offline single-player games converted** — Maze Chase, Word Guess, and
  Bubble Shooter, closing out the "offline/single-player" games package entirely
  (every remaining game left to convert is online-multiplayer). Same recipe as the
  rest of the offline batches, no new surprises. 32 games embedded total now.
- **Battleship converted to the embedded pattern** — 33 games embedded total now, and
  the second (after Rock Paper Scissors) with the multi-entry-point shape: normal
  play, a `SpectateDialog` "Watch" path, and a rematch-wait screen, plus the same
  rematch-flow `boolean[] rematchRequested` guard RPS needed. `SpectateDialog`'s
  Battleship spectate line got the same proactive `MainMenu.getInstance().showGame(...)`
  fix RPS's did, rather than shipping broken like Chess's first pass. Both AI-mode
  win/loss dialogs (no rematch offered there, always a plain `GameHubDialog.show(...)`)
  just needed their trailing `dispose()` swapped for `returnToGames()`, no ordering
  guard needed since neither path opens a new embedded window.
- **Memory Match converted to the embedded pattern** — 34 games embedded total now,
  back to the simpler single-entry-point shape (no spectating/rematch-wait screens).
  Same recipe as Reversi/Connect Four/Signal Grid throughout: unconditional
  `requestLeave()`, `GridBagLayout` centering on all three screens, a new "Leave"
  button on the board screen. No new wrinkles.
- **Word Duel converted to the embedded pattern** — 35 games embedded total now. Same
  recipe throughout, including the round screen (letters + text field + Submit) which
  never had a Leave affordance before and now gets one alongside the rest of its
  content, same as the other single-screen games without a bare "board" concept.
- **Dice Duel converted to the embedded pattern** — 36 games embedded total now. Same
  recipe as Reversi/Word Duel: unconditional `requestLeave()`, `GridBagLayout`
  centering on all three screens (the board screen's outer wrapper previously had its
  own fixed `setPreferredSize(420, 420)`, an unusual variant of the same stretch-not-
  center bug - removed now that the actual content column is centered directly), and
  a new "Leave" button on the board screen.
- **Typing Duel converted to the embedded pattern** — 37 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()` (never confirmed before
  leaving mid-match even as its own window), `GridBagLayout` centering on all three
  screens, a new "Leave" button on the round screen alongside the sentence/typing
  field/progress bars (it never had one before, same as Word Duel). No new wrinkles;
  `GameLauncher.java`'s `typing-duel` case was its only external construction site.
- **Racing converted to the embedded pattern** — 38 games embedded total now, and the
  first online-multiplayer game converted. Same recipe: unconditional
  `requestLeave()` (leaves the queue if mid-search, otherwise a no-op), all four
  screens (mode-select, searching, waiting, and the race track itself) wrapped in
  `GridBagLayout` centerers. The race screen was a new variant: `RacingPanel` (a
  fixed-size custom-painted canvas) is added straight to the `CardLayout`, rebuilt on
  every restart via `startRace(...)`, so the centering wrapper is rebuilt alongside
  it each time rather than created once in the constructor.
- **Among Us converted to the embedded pattern** — 39 games embedded total now. Same
  recipe: unconditional `requestLeave()` (leaves the queue only if still searching,
  matching the original's `windowClosing` exactly), all three screens (searching,
  task/kill screen, meeting) wrapped in `GridBagLayout` centerers. New wrinkle: the
  task/kill screen was originally built as a `JScrollPane` (its task list can scroll)
  returned directly to `CardLayout` rather than a plain `JPanel` - same stretch bug
  applies to a scroll pane as to any other direct `CardLayout` child, so it also got
  wrapped, changing `createGameScreen()`'s return type from `JScrollPane` to `JPanel`.
- **Fight Arena converted to the embedded pattern** — 40 games embedded total now.
  Same recipe: unconditional `requestLeave()` (leaves the queue only if still
  searching, matching the original's `windowClosing` exactly), mode-select and
  searching screens wrapped in `GridBagLayout` centerers. The live match screen was
  the bare-board variant: `FightArenaPanel`, a fixed-size (760px wide) custom-painted
  canvas fed by a server tick loop, was added directly to `BorderLayout.CENTER`
  inside the game screen's outer panel - needed its own small `GridBagLayout`
  centerer wrapper (`fightCenterer`) so it centers instead of stretching to fill the
  available area, same fix as every other bare game-board panel converted so far.
- **Square Wars converted to the embedded pattern** — 41 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()` (also stops the local
  countdown timer, matching the original `windowClosing` exactly), mode-select and
  searching screens wrapped in `GridBagLayout` centerers, and the board screen's
  bare `BoardPanel` (a fixed-size custom-painted grid claimed by clicking cells,
  added directly to `BorderLayout.CENTER`) given its own small `GridBagLayout`
  centerer (`boardCenterer`) - the same bare-board fix used for Fight Arena's canvas.
  No rematch guard needed: "Play Again" re-shows the `SEARCHING` card within the same
  window instead of opening a new one.
- **Trivia Blitz converted to the embedded pattern** — 42 games embedded total now.
  Same recipe throughout: unconditional `requestLeave()`, all three screens
  (mode-select, searching, round) wrapped in `GridBagLayout` centerers. The round
  screen never had a Leave affordance before (only the 4 answer buttons and a scores
  area) and now gets one appended below the scores panel, same as Word Duel and
  Typing Duel's round screens.
- **Air Hockey converted to the embedded pattern** — 43 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers, and the board screen's bare `TablePanel`
  (a fixed-size custom-painted table fed by continuous server state updates, mouse
  position reported to steer the paddle) given its own `GridBagLayout` centerer
  (`tableCenterer`) - same bare-board fix as Fight Arena and Square Wars. Mouse
  coordinates used for paddle-position reporting stay relative to `TablePanel` itself
  regardless of the wrapper, so centering doesn't affect gameplay input.
- **`engine` package built** — the shared sprite/animation/game-loop/collision toolkit
  from the infrastructure backlog, built minimal-first (same approach as `ai/search`):
  `GameLoop` (named wrapper around the `javax.swing.Timer`-tick loop every offline game
  already hand-rolls), `Vector2` (immutable 2D vector math), `GameObject` (optional
  position/velocity/bounds base class), `Collision` (AABB/circle/circle-rect overlap
  tests plus side-detection and bounce response — the same checks `BrickBreakerGame`/
  `AirHockeyMatch` already hand-roll per-game, generalized), and `Sprite`/`SpriteSheet`/
  `Animation` for image-based sprite work (no game uses raster sprites today — every
  board is hand-painted with `Graphics2D` shapes — this is for whichever game wants
  bitmap art next). Also includes the planned **pseudo-3D** capability:
  `engine/pseudo3d/RayCaster` (Wolfenstein-3D-style DDA raycasting over a 2D grid map)
  and `engine/pseudo3d/IsometricProjection` (tile↔screen conversion plus the standard
  painter's-algorithm draw-order key) — both plain Java2D, no real 3D pipeline. Verified
  with a 39-check scratchpad smoke test (vector math identities, collision correctness,
  a procedurally-built sprite sheet animated through a full loop and a one-shot cycle,
  a raycaster render confirmed to paint actual wall pixels, an isometric round-trip, and
  a real `GameLoop` confirmed to fire ticks) plus a separate visual demo rendering both
  a raycaster corridor and an isometric tile grid to PNG. Client-only, not mirrored to
  `VertexServer` (confirmed via `/tmp/keep_files.txt`), same as `ui`/`theme`/`pages`.
  Infrastructure only for now - no shipped game uses it yet, same "prove it generalizes
  first" approach `ai/search` took before `ConnectFourGameModel` existed. See
  `PROGRAM_STRUCTURE.md`'s `engine` section for the full breakdown.
- **Snake Arena converted to the embedded pattern** — 44 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers, and the board screen's bare `ArenaPanel`
  (a fixed-size custom-painted grid, both snakes/food redrawn from server state)
  given its own `GridBagLayout` centerer (`arenaCenterer`) - same bare-board fix as
  Fight Arena, Square Wars, and Air Hockey.
- **Tetris Duel converted to the embedded pattern** — 45 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers. New variant: the board screen puts both
  players' boards side by side in a `FlowLayout.CENTER` row (`boardsRow`) - FlowLayout
  already centers its children horizontally, but added directly to
  `BorderLayout.CENTER` it stays top-aligned vertically once the host area is taller
  than the row needs (the same `FlowLayout` top-alignment bug found during Tic-Tac-Toe's
  conversion) - fixed by wrapping `boardsRow` itself in a `GridBagLayout` centerer
  (`boardsCenterer`) for true 2D centering rather than replacing the FlowLayout, since
  FlowLayout's own horizontal centering of the two boards is still exactly what's
  wanted.
- **Fusion Grid converted to the embedded pattern** — 46 games embedded total now.
  Second multi-entry-shape game after Rock Paper Scissors/Battleship, but the simpler
  variant: online + offline Practice mode (`ai.AiKernel`-backed bot), no spectating/
  rematch-wait factories, so no `rematchRequested` guard needed - "Play Again" in
  both modes just restarts within the same window (`startPracticeMatch()`/
  `findMatch()`), never opening a new embedded one. Same recipe otherwise:
  unconditional `requestLeave()` (skips `leaveMatch()` in practice mode, matching the
  original `windowClosing` exactly), mode-select/searching screens wrapped in
  `GridBagLayout` centerers, and the board screen's bare `BoardPanel` given its own
  `GridBagLayout` centerer (`boardCenterer`) - same bare-board fix as the other
  online-multiplayer conversions.
- **Card Rush converted to the embedded pattern** — 47 games embedded total now.
  Same recipe: unconditional `requestLeave()`, mode-select and searching screens
  wrapped in `GridBagLayout` centerers. Board screen was another Dice Duel-style
  variant: the outer `wrap` panel had its own hardcoded `setPreferredSize(460, 320)`
  (removed - meaningless once `CardLayout` stretches `wrap` itself to fill the host
  area regardless), and the actual `center` content column (the two piles plus the
  hand row) needed the `GridBagLayout` centering wrapper (`centerCenterer`) to stop
  it pinning to the top of the now-larger board area.
- **Zombie Survival converted to the embedded pattern** — 48 games embedded total
  now. Same shape as Racing: online + Practice mode, unconditional `requestLeave()`
  (leaves the queue only if online), mode-select/searching/waiting screens wrapped in
  `GridBagLayout` centerers, and the game screen's fixed-size `ZombieSurvivalPanel`
  rebuilt from scratch on every restart via `startGame(...)` - the centering wrapper
  (`gameWrapper`) is rebuilt alongside it each time, tracked via a new field so the
  old one can be removed from the `CardLayout` before a fresh one is added, same
  pattern as Racing's `raceWrapper`.
- **Space Battle converted to the embedded pattern** — 49 games embedded total now.
  The last remaining game: same exact shape as Racing/Zombie Survival (online +
  Practice mode, independent-simulation scoring, a fixed-size game canvas rebuilt on
  every restart via `startGame(...)` with its centering wrapper - `gameWrapper` -
  rebuilt alongside it). **Every game in the entire Vertex catalog is now embedded in
  MainMenu's game-host slot - the "fill the screen, no separate window" conversion
  that's been running throughout this session is complete.**
- **`GameLauncher`'s 49-branch `if/else` replaced by `GameWindowFactory`** — the
  strategy doc's Section 4/17 root-system fix, done as a purely mechanical extraction:
  every branch had the exact same shape by the end of the embedded-games rollout
  (`MainMenu.getInstance().showGame(new XxxWindow())`), so it collapsed cleanly into
  one `Map<String, Supplier<JComponent>>` (`GameWindowFactory`, client-only - unlike
  `GameInfo`/`GameRegistry`, which stay byte-identical on both client and server and
  so can never reference a Swing class). `GameLauncher.openGame` is now a single
  lookup instead of 49 branches. Verified with a smoke test constructing all 49
  windows via the factory and confirming zero exceptions, the exact expected 49-id
  set, and that an unknown id correctly returns `null` (falling through to the
  existing "not converted yet" notice). Note found during the audit for this: a
  `GameRegistry`/`GameInfo` catalog already existed server- and client-side (feeding
  the games list UI) before this session started - it was never disconnected, just
  never wired to the launch mechanism; that's genuinely the next step (capability
  flags: offline-capable, spectatable, min/max players), not a from-scratch build.
- **First `GameInfo` capability flag added: `spectatable`.** True only for Chess,
  Rock Paper Scissors, and Battleship (the three games with a real `SpectateDialog`
  "Watch" entry point) - false by default for the other 46. `GameRegistry.seed()`
  sets it via a small `markSpectatable(gameIds...)` helper after construction, same
  post-construction-setter convention `queueCount` already used, rather than adding a
  required 8th constructor parameter to all 49 call sites for something only 3 of
  them need. Verified with a scratchpad test confirming the spectatable set matches
  exactly `{chess, rock-paper-scissors, battleship}` out of all 49 games. Deliberately
  scoped to just this one flag tonight rather than the full capability set the
  strategy doc describes (offline-capable, min/max players, party-joinable) - `type`
  already mostly captures offline-capability ("Single Player" vs "Multiplayer" vs
  "Single/Multiplayer"), so adding a redundant parallel flag for that wasn't
  justified; the others don't have a concrete consumer yet and building them
  speculatively ahead of one was explicitly flagged as an anti-pattern to avoid in
  the strategy doc's own "Things NOT to build" section.
- **Fixed a real economy bug found during the audit: 14 offline games paid zero coins
  on completion despite already having a reward formula defined.** `EconomyConfig
  .getPracticeReward` has had formulas for Simon Says, Whack-a-Mole, Match Three,
  Maze Chase, Brick Breaker, Flappy Bird, Galaxy Defender, Word Guess, Bubble
  Shooter, Lights Out, Peg Solitaire, Klondike, Yahtzee, and Mancala for a while -
  but `ClientHandler.handleGamePlayed`'s dispatch only ever called it for 6 games
  (Pong/2048/Dino Dash/Tetris/Crossing Road/Aim Trainer), so those 14 games'
  formulas were simply never reached. Fixed by replacing the hand-maintained 6-game
  `OR` chain with a safe default fallback: any `gameId` not otherwise special-cased
  now goes through `awardPracticeScore`, which itself already no-ops safely (via
  `getPracticeReward` returning 0) for any id with no formula - meaning this also
  can't accidentally reward an online game that sends `GAME_PLAYED_REQUEST` purely
  for history tracking (Chess, Racing, etc. all correctly still get 0), and any
  *future* offline game only needs a `getPracticeReward` entry, never a
  `ClientHandler` edit. Verified with a coverage test confirming all 20 intended
  games get a nonzero reward and every online/history-only game still gets exactly
  0. One real gap surfaced by the same audit, left open rather than guessed at:
  Sudoku has no reward formula at all (not a wiring bug, a genuine missing design
  decision) - recorded in `BLOCKED_QUESTIONS.md`.
- **New game: Hill Climb** — 50 games in the catalog now. An original "drive a simple
  vehicle across procedurally rolling hills on a limited fuel tank" implementation
  (`HillClimbGame`/`HillClimbWindow`), picked from the games backlog instead of the
  also-listed Pac-Man concept once the audit noticed Maze Chase is already
  functionally a from-scratch Pac-Man (maze, pellets, chasers, frightened mode) -
  building a second one would have been pure duplication. The first game in this
  codebase actually built on the `engine` package (`Vector2` for the slope/gravity
  math, `GameLoop` for the tick loop) rather than just having it sit unused. Added
  through every layer tonight's earlier refactors made a single-file change each:
  one `GameWindowFactory` entry (no `GameLauncher` edit needed), one `GameRegistry`
  entry (client + server), one `EconomyConfig.getPracticeReward` formula (no
  `ClientHandler` edit needed, since the safe-default fallback from the economy fix
  above already covers any new offline game automatically). Caught and fixed a real
  balance problem during testing rather than shipping it: an early version also
  flipped the car over past a steep-slope-at-speed threshold, but that triggered far
  too readily during ordinary hard-throttle climbing (an unfair "gotcha" death, not
  a reckless-driving penalty) - cut for a solid, fully-tested fuel-only version
  instead of shipping a half-tuned mechanic. Verified with an 11-check logic test
  (terrain bounds, distance/fuel/score behavior, idle vs. throttling, tick() being a
  no-op once over) plus an Xvfb/Swing screenshot harness confirming the terrain,
  car, and HUD render correctly both at rest and mid-drive.
- **Dead connection-error message fixed across 25 game windows.** Every
  practice/find-match screen used `if (!NetworkManager.sendAsync(request)) { ...set an
  error label... }`, but `sendAsync()` is designed to always return `true` - it queues
  offline instead of failing, on purpose, so `sendAsync`-based optimistic UI (like
  chat) never has to check a return value. That made the error branch on all 25 files
  dead code: a player with no server connection just saw "Searching..." spin forever
  with no explanation. Fixed at the source instead of patching each call site
  individually - added `NetworkManager.describeIfNotReady()`, which reads the
  already-existing `ConnectionState` (OFFLINE/CONNECTING/RECONNECTING/ONLINE) and
  returns a player-facing message for the first three and `null` when ONLINE, then
  swapped all 25 files' dead `if (!sent)` checks for a call to it right after
  `sendAsync()`. Verified with a reflection-based state test (all four states return
  the right thing) and an Xvfb/Swing harness that drove real `TicTacToeWindow` and
  `ChessWindow` instances to their searching screen (covering both the
  `searchingLabel` and the one-off `statusLabel` variable-name variants used across
  the 25 files) and confirmed the correct message renders.
- **4 online games' wins never counted toward the generic win-count challenges
  (Win 1 Today / Win 3 Today / Champion).** Same shape of bug as the offline-rewards
  fix above, found the same way: traced every caller of the economy layer rather than
  assuming coverage. `challengeManager.recordWin()` - the only thing that advances
  those challenges - was only ever called from `EconomyManager.awardWin()`. Racing,
  Space Battle, Square Wars, and Trivia Blitz don't have a single
  `EconomyConfig.getWinReward(gameId)`-driven winner the way a 2-player match does
  (placement games, and games where ties split the prize among several winners), so
  they route through `awardRacingPlacement`/`awardSpaceBattlePlacement`/`awardCoins`
  instead - none of which touched `challengeManager` at all. A player who only ever
  played those 4 games could never complete "Win any 1 online match", and since
  `ChallengeManager.recordWin` grants its own coin bonus on completion (on top of the
  match's own reward), this was a real missed-coins bug, not just a progress-bar
  display issue. Fixed by extracting `awardWin`'s post-reward challenge/message logic
  into a shared `recordOnlineWin()` and calling it from `awardRacingPlacement`/
  `awardSpaceBattlePlacement` (only when `place == 1` - that's this race's "win"),
  plus a new `awardMatchWinCoins()` for the tie-splitting games (Square Wars, Trivia
  Blitz) to use instead of the generic `awardCoins`. Verified with a coverage test
  that drives the real `ChallengeManager` for all 4 previously-unreachable game IDs
  and confirms `daily-win-1` now completes, plus a control case proving an unrelated
  game-specific challenge (`tictactoe-win-5`) stays untouched.
- **9 more silent/stale connection-error spots found beyond the 25-file sweep,
  fixed the same way.** The earlier connection-status fix only grepped for the
  `sendAsync`/`if (!sent)` dead-code pattern; a follow-up pass grepping for
  `NetworkManager.send(` (the blocking variant, which genuinely returns `null` on
  failure) and for the literal stale string `"Can't reach the server - is it
  running?"` turned up two more categories of the same underlying problem: (1) two
  more `sendAsync` dead-code spots in `ChatPanel` (send message, send file) that the
  first sweep missed entirely, and (2) 7 spots (`GamesPanel` refresh,
  `LeaderboardPanel`, `FriendsPanel` load + add-friend, `ShopPanel` load + select +
  purchase, `NewGroupDialog`, `LoginPanel`, `ChangeUsernameDialog`,
  `ChangePasswordDialog`) that already showed *some* error on a null `send()`
  response, just the same generic hardcoded string instead of an accurate one, or
  (worse, for `FriendsPanel.loadFriendData()` and `ShopPanel.loadShopItems()`)
  showed nothing at all - a completely blank Friends/Shop page with no explanation
  when offline. All 9 now use `NetworkManager.describeIfNotReady()` the same way the
  25-file fix did. `ChatPanel`'s two spots now also always clear the input (matching
  `sendAsync`'s documented queue-and-optimistically-proceed contract) and, only when
  actually offline, add a reassuring "will send once reconnected" note rather than
  looking like the message/file was silently dropped. Verified: clean compile, plus a
  harness that constructs real `FriendsPanel`/`LeaderboardPanel`/`ShopPanel`
  instances with nothing listening on the configured host:port and confirms each one
  now shows the connection message instead of hanging on "Loading..." or staying
  blank.
- **Found and fixed a real gap in the client/server sync discipline itself:
  `VertexClient/net/ClientHandler.java` and `net/Message.java` had silently
  drifted from their `VertexServer/` counterparts.** Discovered while adding the
  update-integrity check below - diffing the two trees' `ClientHandler.java` turned
  up that tonight's earlier practice-reward fix (the "14 offline games paid zero
  coins" commit) was only ever applied to `VertexServer/`. This matters because
  `VertexClient/games/HostServerDialog.java` spins up a real, full `net.GameServer`
  in-process (the in-app "Host a Server" feature) - anyone hosting from the client
  app rather than a dedicated `VertexServer.jar` was still hitting the old bug.
  `PROGRAM_STRUCTURE.md`'s "shared files" list only names a handful of files as
  required to stay byte-identical; `ClientHandler.java`/`Message.java` weren't on
  it despite needing to be, since the whole `net`/`games`/`economy`/`social`/`admin`
  server stack is compiled into both trees for exactly this hosting feature. Synced
  both files server-to-client (now byte-identical again) and re-added the note to
  `PROGRAM_STRUCTURE.md`'s file-sync section below so this doesn't quietly happen
  again. Verified: both trees compile clean after the sync.
- **Client auto-update integrity check** — `ClientUpdateChecker` staged whatever
  bytes `CLIENT_UPDATE_DOWNLOAD_RESPONSE` handed it with zero verification, so a
  truncated or corrupted transfer would get installed as-is. Added
  `Message.newJarHash` (the server's own jar hash, already computed for the version
  check, now also sent alongside it) and made the client re-hash the download and
  refuse to stage it on a mismatch. Verified against `FileHash.sha256Hex` directly:
  matching bytes hash equal, corrupted bytes hash different. **This is explicitly
  NOT a security fix** - it only catches accidental corruption, since the same
  untrusted server that could serve a malicious jar could just as easily lie about
  the matching hash. The real gap (no code signing, no TLS - a compromised or
  spoofed server can achieve remote code execution via this exact mechanism, flagged
  as the single most severe risk in the platform strategy analysis) is recorded in
  `BLOCKED_QUESTIONS.md` rather than guessed at overnight - it's a real
  key-management/architecture decision, not a mechanical fix.
- **Closed a real Java deserialization vulnerability - both `ClientHandler` (server)
  and `NetworkManager` (client) called `ObjectInputStream.readObject()` directly on
  a raw socket, with zero filtering.** This is the well-known Java deserialization
  RCE bug class (see any major Java deserialization CVE, or the `ysoserial` tool,
  for what an unfiltered `readObject()` on untrusted input enables) - arguably more
  severe than the update-signing gap above, since it needs no MITM positioning and
  no user choosing a malicious server: the server side hits this on a brand-new
  socket's very first message, before login/authentication even happens, so any TCP
  connection to a Vertex server could attempt it, and symmetrically a malicious or
  compromised server could target any client that connects to it. Fixed with a new
  `net.VertexSerializationFilter` (a `java.io.ObjectInputFilter`, JEP 290, standard
  JDK since Java 9 - no new dependency), applied via `setObjectInputFilter()` right
  after constructing both `ObjectInputStream`s. Built as a strict allow-list (only
  `Message`/`MessageType`/`Account`/`Role`/`GameInfo`/`ChallengeProgressInfo`/
  `ShopItemInfo`/`String`/`Enum`/`Object`/`ArrayList` - every concrete class that
  actually flows through the protocol - then a trailing `!*` rejecting everything
  else), deliberately not a deny-list of known-bad gadget classes: an allow-list is
  safe against gadgets nobody has found yet too, a deny-list only ever covers ones
  someone already has. Generous size limits (maxbytes/maxarray/maxdepth/maxrefs) are
  a pure DoS backstop against a peer claiming an absurd size, not a tight bound on
  real traffic - sized well above the largest legitimate payload (a whole Vertex.jar
  on client auto-update). Verified with a round-trip test proving a realistic
  `Message` populated with every field type (account, game list, challenges, shop
  items, file bytes) still deserializes correctly through the filter, plus two
  rejection tests proving a disallowed class sent directly (`HashMap`, `File`) gets
  rejected with `InvalidClassException` instead of silently succeeding. Mirrored
  into `VertexClient/` (this needed the same `PROGRAM_STRUCTURE.md` sync-discipline
  fix above - `ClientHandler.java`'s copy there was, until the next entry, what the
  in-app "Host a Server" feature actually ran).
- **Removed the in-app "Host a Server" feature entirely, per explicit request.**
  Deleted `VertexClient/games/HostServerDialog.java` and the "Start Hosting" button/
  section it was wired to in `SettingsPanel`. Hosting is now exclusively a
  `VertexServer.jar` thing - the ordinary client a player runs has no way to spin up
  a server from inside the app anymore. `VertexClient/` still carries the full
  server-side packages and `ServerMain.java` (needed as the edit-source-of-truth
  copy that gets synced into `VertexServer/`, and because `VertexServer.jar`'s own
  manifest points at that exact class) - that's a developer/build-time thing, not a
  player-facing feature, so it stays. Updated every doc that described "anyone can
  host" as a feature (root `README.md`, `FEATURES.md`, `PROGRAM_STRUCTURE.md`, both
  project `README.md` files) to reflect this. Verified: `VertexClient/` compiles
  clean with `HostServerDialog` gone (1323 classes vs. 1330 before - the removed
  class and its inner classes).
- **New game: Telephone** — 51 games in the catalog now. A Gartic-Phone-style
  draw/guess chain, requested explicitly (twice) rather than picked from the
  backlog. 4-8 players; `N` players means `N` parallel chains and exactly `N`
  rounds - round 0 is everyone writing their chain's opening phrase, every odd
  round is "draw the phrase you were just handed," every even round after 0 is
  "guess the drawing you were just handed" - the standard rotation
  `chain = (player - round) mod N` means every player visits every chain exactly
  once, and chain k's round-0 entry always comes from player k (the chain
  "owner"). Ends with a reveal: every chain replayed start to finish for
  everyone, the actual point of the game. No scoring, no winner - a flat
  participation coin reward via the existing `awardWin` path (same as Zombie
  Survival's precedent for co-op games with no single winner), which also means
  it correctly counts toward the generic win-count challenges via tonight's
  earlier `recordOnlineWin` fix.
  New server classes: `TelephoneMatchManager`/`TelephoneMatch` (same
  `Timer`-auto-advances-regardless-of-answers shape as `TriviaMatch` - one slow
  or disconnected player never stalls anyone else's chain, they just get an
  auto-filled blank entry). New client class: `TelephoneWindow`, including a
  small original `DrawingCanvas` (freehand mouse-drag painting onto a
  `BufferedImage`, same technique as `AvatarEditorDialog`'s existing paint tab,
  not shared code - scoped as its own copy since nothing else needs it yet).
  `ThemedTextArea` gained a `clear()` method (it only had `getValue()` before) -
  a real, small gap found while wiring the text-round screen, matching
  `ThemedTextField`'s existing `clear()`.
  Caught and fixed two real UI bugs during visual verification, not shipped
  blind: (1) the drawing canvas was placed via `BorderLayout.CENTER`, which
  stretches to fill all remaining space regardless of preferred/max size -
  fixed by switching that row to `BoxLayout` (which respects a fixed max size)
  instead. (2) three of the five screens returned a bare, non-opaque `JPanel`
  straight to the card deck instead of wrapping it in a `RoundedPanel` the way
  every other screen (including this same file's own searching/waiting
  screens, and every other game's screens) does - without that wrapper nothing
  ever paints the theme's dark background, so Swing's own default light-grey
  `JPanel` background showed through everywhere. Root-caused by directly
  diffing an working screen (`AmongUsWindow`'s GAME card) against a broken one
  at the component-tree level (a small reflection dump of `isOpaque`/
  `background`/`bounds` for every node) rather than guessing - the actual
  culprit was a missing `RoundedPanel(ThemeColor.BG_APP, 0)` wrapper, present
  on the working screen and absent on the broken ones. Verified with a rotation
  logic test (every one of the N chains gets exactly one contributor per round,
  every player visits every chain exactly once, chain k's opening phrase always
  comes from player k) plus an Xvfb/Swing harness that drives a real
  `TelephoneWindow` through synthetic round-start and reveal-entry pushes and
  confirms every screen renders correctly (including the text-input and guess-
  image-preview screen, which needed its preferred height increased and a
  scroll-pane wrapper added after an early pass clipped the input box and
  submit button off-screen once the guess-image preview was inserted above
  them).
- **First version of the public website** (`website/`, Flask) - the "Planned"
  entry for this is done. Pages: Home/Changelog, Download (the default landing
  page), Credits, Features, and a "How It's Built" overview. The Features and
  How-It's-Built pages render `FEATURES.md`/`HOW_VERTEX_WORKS.md` directly
  (Markdown converted to HTML at request time) instead of duplicating their
  content, so editing those two files - already the convention - keeps the
  site current too. The Download page reads the repo's own `VERSION` file, so
  the version shown can never drift from what the jars are stamped with.
  **The server jar is deliberately not offered for download here** - per
  explicit instruction, only `VertexClient.jar` is downloadable from the site;
  `/downloads/<filename>` serves nothing else regardless of what's requested.
  This matches the same reasoning as removing the in-app "Host a Server"
  client feature above: hosting shouldn't be a casual one-click action,
  whether that's a button in the client or a download link on the marketing
  site - someone who wants to host reads the GitHub repo's setup instructions
  instead. Not deployed anywhere yet (that needs the actual target machine,
  e.g. the Oracle Cloud instance originally mentioned) - see `website/README.md`
  for what's built, what's deliberately not, and deploy notes for whenever
  that happens.
  Verified locally: every route checked with `curl` (200s, and the removed
  server-jar route confirmed 404), content spot-checked (live version number,
  the Telephone changelog highlight, FEATURES.md's current game count all
  render correctly), and real screenshots via headless Chromium at desktop and
  down to 500px (the smallest width this environment's headless Chromium
  build would actually honor - confirmed directly via `window.innerWidth`,
  not a CSS bug; see `website/README.md`'s testing note for the full
  diagnosis and why the CSS itself should still work correctly narrower than
  that on a real device).

## 🔧 In Progress

- **Embedded-games rollout: complete.** All 49 games in the catalog now open inside
  MainMenu's game-host slot instead of their own JFrame - every offline/single-player
  game, both spectate/tournament-capable games (Chess, Rock Paper Scissors,
  Battleship), and all twelve online-multiplayer games (Racing, Among Us, Fight
  Arena, Square Wars, Trivia Blitz, Air Hockey, Snake Arena, Tetris Duel, Fusion
  Grid, Card Rush, Zombie Survival, Space Battle). Any future offline-capable game
  reachable from `OfflineHubWindow` needs the same `setReturnAction`-style treatment
  Snake got, not just the standard MainMenu-only conversion. **Standing check for any
  future game/window**: grep for every external construction site of that game's
  window (not just `GameLauncher.java`) before considering it done - Chess's
  spectate-path bug is exactly the kind of thing that slips through otherwise.
- Also still wanted: a true embedded `CardLayout` "Details" step (Discover -> Details
  -> Mode -> ... -> Return, continuous with the rest of the flow) rather than
  `GameDetailDialog`'s current modal popup (now full-screen-sized, see "Done" above,
  but still a separate `JDialog` on top of the app rather than a step inside it) -
  would mean auditing and rewriting all 3 of its call sites to hand off into
  `MainMenu`'s game-host slot instead of `dialog.setVisible(true)`. Also still wanted:
  a game should be able to have chat "popped out" alongside it while playing (with
  some games, like a Gartic-Phone-style drawing game, needing chat *restricted* rather
  than open, since free chat would let players just say the answer out loud).

- **Reconnection rollout to the other ~29 match types.** Tic-Tac-Toe now proves the
  `ReconnectRegistry`/`ReconnectableMatch` pattern works end-to-end (see "Done" above)
  - extending it to Chess/Connect Four/Reversi/Checkers/Dots and Boxes (the other
  `ai/search`-backed turn-based games, closest in shape to Tic-Tac-Toe) is next,
  followed by the remaining request/response-style games (Battleship, RPS, Trivia
  Blitz, Word Duel, etc.). The genuinely continuous-simulation games (Racing, Space
  Battle, Air Hockey, Fight Arena, Zombie Survival) will need more thought - "resume
  mid-tick" is a different problem than "resume on your turn" - so those are lower
  priority for this pattern until a second concrete game proves that shape out too.
  Client-side, every game window that adopts this needs its own `OPPONENT_DISCONNECTED_
  NOTICE` handling branch (a few lines each, following `TicTacToeWindow`'s as the
  template) since each game's `onPush` dispatch is still hand-written per window, not
  a shared base class.

## 📋 Planned — infrastructure & shared packages

- **A real `ui` empty/loading/error placeholder component.** After fixing the
  FriendsPanel/LeaderboardPanel/ShopPanel silent-failure bugs, all three now hand-roll
  their own near-identical "clear this container and drop in a muted label" logic. Three
  real call sites is enough repetition to justify a shared `ui` primitive (something
  like a `PlaceholderPanel` a caller sets to loading/empty/error state) instead of a
  fourth hand-rolled copy next time - not done now since the current fix isn't broken,
  just slightly duplicated, and a refactor of working code carries real risk for no
  user-facing benefit on its own. Worth doing the next time a panel needs the same
  three states.
- **`economy` package additions** — an `EconomyKernel` any game can call in one line
  to grant coins/XP/unlock cosmetics, instead of reimplementing reward logic per game.
- **`achievements` kernel** — generic trigger-based unlock system, parallel to
  `EconomyKernel`.
- **`save` package** — generic save/load slots for games with persistent state
  (roguelike runs, farming/idle games in the concept backlog need this).
- **`matchmaking` kernel** — shared ELO/queue logic any new competitive game can
  register into.
- **Procedural/emergent character system** — a general engine capability (not tied to
  one game): a pool of name/trait/role combinations that get spawned into a game when
  specific triggers fire (a rival general emerges after you conquer 3 provinces, a
  rebel leader after a revolt, etc.) — free, rule-based, no LLM. Fits Dominion's
  leader/succession system especially well, but meant to be usable by other games too.

## 📋 Planned — social & community

- **Standalone Forums section** — Reddit-style boards (one per game, plus general
  discussion), separate from group chats.
- **Mutual friends** — shown on profiles, computed from the intersection of two
  friends lists.
- **Structured bug reports & suggestions** — `FeedbackDialog`/`GameSuggestionsPanel`
  already exist but are single free-text fields; add real title/description fields,
  plus steps-to-reproduce for bug reports specifically.
- **Slash commands + a free pattern-matching chatbot** — `/help`, `/rules chess`,
  `/theme`, `/challenge @friend`, etc. Deliberately *not* a real LLM chatbot (that
  costs money per message and needs an API key) — free and instant by design. A
  bring-your-own-API-key *option* for real chat later is a separate, explicitly
  opt-in idea, not the default.
- **Discord**: not something built into Vertex itself, but the user is setting up an
  actual Discord server for the project — possible follow-up integration ideas
  (a webhook posting changelog/version updates, a bot) once the specific ask is
  clearer.

## ❌ Not planned — voice chat

Previously listed here as a planned v1 feature (party-scoped PCM audio over a new
channel). The user has since said voice chat is being removed from scope - no audio
code was ever built (this was plan-only), so there's nothing to remove from the
codebase, just this section. Do not re-propose voice chat.

## 🎲 Games backlog (curated 2026-09-25 - see note below)

**Correction to this section's own history:** earlier drafts of this file described
"314 total game concepts... 7 fully-detailed Original Seven... 306 more across 38
categories," attributed to `GAMEHUB_ALL_IDEAS_v4.md`. That number doesn't actually
exist anywhere in this repo - `GAMEHUB_ALL_IDEAS_v4.md`'s real game catalog (its
Section 12) lists about 25 concrete concepts, not 314. Rather than keep repeating an
inflated figure, this section was re-derived from that file's actual contents,
cross-checked against the 50 games already built. Most of the old list turned out
to already be covered - worth knowing, not worth hiding.

### Already covered by an existing game (remove from backlog - nothing to build)
Tic-Tac-Toe, RPS, 2048, Snake, Ping Pong, Dino Dash, Battleship, Crossing Road,
Tetris, match-3 (→ Gem Match), Aim Trainer, Car Racing (→ Racing), Pac-Man (→ Maze
Chase), Zombie Survival, Hill Climb Racing-style (→ Hill Climb), Fighting Arena (→
Fight Arena, which already has 1v1/2v2/3v3/FFA), Among Us, **Paper.io-style
territory game (→ Square Wars - same claim-the-grid mechanic, just a different
name)**, "co-op games" (→ Zombie Survival already is one), "FFA mode" (→ Fight
Arena's Chaos Mode already is one).

### Deliberately not attempted casually (genuinely multi-phase projects, stay flagged)
Mario-style platformer, Pokémon-style creature collector/battler, Terraria-style
sandbox - each realistically a multi-year undertaking even approximated. **Vertex:
Dominion** is its own flagship, categorically bigger than everything else (a
persistent, whole-server nation-building game on a daily tick, not a quick match) -
tracked separately, not folded into this list.

### The real remaining backlog - concrete concepts, not vague entries
- ~~Gartic Phone-style telephone/drawing game~~ — **built, see "Done" above
  (Telephone).** Turned out not to need a restricted chat channel at all - the
  whole draw/guess/reveal loop is structured request/response (drawings and
  guesses submitted as game moves, same pattern as every other game's move
  submission), not free-form chat, so that infrastructure piece this entry
  originally flagged was never actually needed.
- **A Quiplash/Jackbox-style prompt-and-vote game (original concept, working title
  "Caption Chaos")** — the server shows a silly prompt ("The worst thing to say on
  a first date"), everyone privately submits an answer, then everyone votes
  anonymously for their favorite (can't vote for your own); most votes wins the
  round. Cheap to build (no real-time sync, no physics, just request/response like
  Trivia Blitz), and this exact genre (Jackbox's Quiplash, Drawful) is consistently
  one of the most replayed party-game formats that exists - genuinely fun, not
  filler. Telephone's round-rotation/reveal-viewer patterns (a server Timer that
  auto-advances regardless of who's answered, a client reveal stepper) are a
  reasonable template to start from.
- **Casino mini-games** (slots, a roulette-style wheel, a simple blackjack) —
  stakes and payouts computed server-side only, same rule as everything else in the
  economy. Confirmed still worth an `Admin`-level per-server toggle (default off)
  before shipping, since it's virtual-currency wagering and server operators should
  get to decide if that fits their audience - not something to hardcode as
  always-on. Not blocked on a design question, just correctly sequenced after the
  toggle exists.

### New concepts worth adding (researched 2026-09-25, original implementations only)
Looked at what actually makes multiplayer games popular right now (io-game and
party-game genres, not any specific game's content/art/code) to find good, provable
formats - not to copy anything:
- **A vertical "climber" single-player game** (original mechanic, `Doodle Jump`
  genre) — bounce upward off procedurally-placed platforms, camera scrolls up
  forever, moving/breakable/spring platforms add variety, game ends when you fall
  off the bottom of the screen. Distinct from every existing single-player game
  here (nothing else is a vertical endless climber).
- **A number-merge puzzle, `Threes`/`1010!` genre, distinct from 2048** — a small
  grid where you place incoming numbered/colored pieces rather than 2048's
  slide-and-merge-everything mechanic; genuinely different enough from 2048 to be
  its own game, not a reskin.
- Both chosen specifically because the ask was "don't make this only multiplayer" -
  the platform already has ~26 single-player games, so 2 well-chosen new ones
  (each a distinct mechanic, not a reskin of something existing) is a more honest
  amount to add than a long filler list.

Still-deferred engine shapes flagged early on and never built as generic
infrastructure (each existing game solved its own version instead): continuous-
control games (Air Hockey, Fight Arena already work without it), parallel-
simulation games (Racing, Space Battle already work without it), multiplayer
bot-fill (Among Us, Trivia Blitz already work without it) - noted here in case a
*future* game's shape genuinely needs the generic version, not because today's
games are missing anything.

---

*Keep this file updated as things move between sections — that's the whole point of
having it.*
