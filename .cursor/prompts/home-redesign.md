# Prompt for Cursor: design and build the Vertex Home page

Copy everything below the line into Cursor. (Cursor will also pick up `.cursor/rules/` automatically -
`vertex-overview.mdc` and `vertex-ui.mdc` - so the project conventions don't need repeating.)

---

You are working on **Vertex**, a Java/Swing multiplayer game platform. Read `CLAUDE.md` first, then
`.cursor/rules/vertex-ui.mdc`. Everything you touch is client-only (`VertexClient/pages`, `VertexClient/ui`), so
there is nothing to mirror into `VertexServer/`.

## The task

Redesign the **Home page** (`VertexClient/pages/HomePanel.java`) into the app's main landing page. It is the first
thing a player sees after logging in. The structure already exists as **six placeholder sections**, each its own
class extending `pages/HomeSectionPanel`. Your job is to make them look great and work, one at a time.

| Class | What it should become |
|---|---|
| `HomeWelcomeSection` | A greeting with the player's name, today's daily-login reward and login streak, and progress on active quests. |
| `HomeContinueSection` | "Continue playing": the last few games (one-click launch) plus **pinned** games the player chose to keep handy. |
| `HomeQuickPlaySection` | One prominent "Quick play" action that drops the player into a match. |
| `HomeFriendsSection` | Which friends are online right now, with a quick way to invite them or open a DM. |
| `HomeTournamentsSection` | Tournaments that are open to join or running now. |
| `HomeWhatsNewSection` | The newest `CHANGELOG.md` entry with a "see everything" link to the Changelog page. (Already shows the text; needs design and the link.) |

`HomePanel` also still contains the older sections - the scrolling ticker, **Top Players**, **Recently Played** and
**Explore Games**. Decide where each belongs in the new design: fold Recently Played into Continue Playing, keep or
drop the ticker, keep Top Players and Explore Games lower down if they still earn their space. Don't lose
functionality silently - if you remove something, say so.

**Each section's javadoc lists the data that is already available** (message types, existing panels to reuse,
helper classes) and what still needs building. Read it before writing anything - most sections need no server
changes. Where something is missing, the notes say so:

- **Pinned games** have no storage yet. A small per-computer list in `java.util.prefs` (see
  `economy/FpsCounterSetting.java` for the pattern) is enough, plus a pin/unpin control on game cards and on
  `games/GameDetailPanel`.
- **Quick play** needs a rule for what it picks (a game with players waiting? the most-played one?). Reuse the logic
  of `pages/QuickPlayDropdown` (the "Play" control in the top bar) rather than adding a second picker. Propose the
  rule to the user before locking it in.

## Design direction

- It should feel like a modern game launcher's home screen (think Steam/Epic/Discord-style hierarchy): one clear
  focal area, generous spacing, and everything actionable in one click.
- The layout must work from a small window (~1000px wide) up to full screen, and the page must scroll cleanly.
- Use the existing look: `RoundedPanel` cards, `ThemedButton`, `ThemedLabel`, `PageHeader`, `GameCardArt` for game
  art, `StatusPill`/`StatusDot` for small status. **Never hardcode colours** - use `ThemeColor` roles via
  `ThemeManager.getColor(...)`, and repaint on theme change (`ThemeManager.addListener`). Check it in at least two
  of the ~10 themes (e.g. Dark Navy and a light-ish one such as Ice Blue).
- Empty and loading states matter (a brand-new account has no recent games, no friends, no quests): use
  `ui/PlaceholderPanel` or a friendly message, never a blank box or a stack trace.
- Keep the placeholder copy style: short, friendly, no "TODO" text visible to players.

## Rules you must follow

- **Network calls never run on the Swing thread.** `NetworkManager.send(...)` blocks for up to 10 seconds and holds
  a global lock while it waits - so keep the number of requests small, run them on a background thread, and update
  the UI with `SwingUtilities.invokeLater`. `refresh()` on a section is called when Home is built and every 30s.
  Prefer `PushListener` for live data (friends presence, tournaments) over polling.
- Do not add new server message types unless a section genuinely cannot work without one. If you do, read the
  "sync rule" and the `RESPONSE_TYPES` note in `.cursor/rules/vertex-ui.mdc` and `CLAUDE.md` first
  (`net/ResponseTypesTest` will fail the build if a response type is left unregistered).
- HTML in a `JLabel`: wrap text with a `<table width='N'>`, not `<body style='width:N'>` (ignored on this JDK).
- A `ThemedTextField` is disabled with `setInputEnabled(false)`, not `setEnabled(false)`.
- Every player-visible change also gets a line in `CHANGELOG.md`, and `ROADMAP.md`'s "Done" section and
  `PROGRAM_STRUCTURE.md` are updated in the same change (see `CLAUDE.md`).

## How to check your work

1. Compile: `./build.sh` (or `javac -d /tmp/out -encoding UTF-8 $(find VertexClient -name '*.java')`).
2. Run the tests: `./test.sh` - everything must stay green.
3. Look at it. Render `HomePanel` in an undecorated `JFrame` under Xvfb, `printAll` into a `BufferedImage`, save a PNG
   and view it; do it for a wide window, a narrow one, an empty account and a busy account. Scratch harnesses stay out of the repo, and
   always end with `System.exit(0)`.

Work one section at a time, show a screenshot after each, and stop to ask before making a design decision that has
more than one reasonable answer (for example what Quick play picks, or whether to keep the ticker).
