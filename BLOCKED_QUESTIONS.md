# Blocked Questions

Questions that came up during autonomous nightly work sessions (11 PM–5 AM IST) that
genuinely need Bipin's input rather than a reversible assumption. The rule: a blocked
question never stops the rest of the session — it gets recorded here with enough
context to answer quickly later, and work moves to something else independent.

Answered questions move to the bottom under "Resolved," with the decision recorded
(and mirrored into `ROADMAP.md` if it's an architecture decision, or wherever else
it's actually load-bearing) rather than deleted, so the history of *why* stays
visible.

Format per entry: what's blocked, why it needs a real decision (not just a coin flip),
what assumption (if any) was made to keep moving in the meantime, and the date it was
raised.

---

## Open

- **The client auto-update mechanism (`ClientUpdateChecker`) has no code signing,
  and `NetworkManager` has no TLS** — together, whatever server a client connects to
  (or a network attacker impersonating it, since the socket is plain unencrypted
  TCP) can push an arbitrary jar that gets staged and auto-installed on next
  launch, no user confirmation, no signature check. This is the single most severe
  risk flagged in the platform strategy analysis. Added an integrity check tonight
  (the client now re-hashes the download and refuses to stage it if it doesn't
  match the hash the server claimed) - genuinely useful against a truncated/corrupt
  transfer, but explicitly NOT a fix for this: the same untrusted server/attacker
  that could serve a malicious jar could just as easily lie about the matching
  hash. Real exposure today is bounded by "LAN-only for now" (already a documented
  limitation), but this becomes a genuine remote-code-execution path the moment
  internet play ships. Needs a real decision on the fix, not a guess: (a) require
  TLS + certificate pinning specifically for the update channel even before a full
  TLS rollout, (b) proper jar code-signing (a keypair the platform/admin controls,
  public key embedded in the client at build time, server signs whatever jar it
  serves) - this one has real operational cost (key generation, where it's stored,
  how a self-hosting friend without the "official" key would get updates signed at
  all), or (c) accept the current risk as LAN-only scope and revisit specifically
  when internet play is actually being built. Raised 2026-09-25. No code-signing
  assumption made; only the safe, clearly-scoped integrity check above was added.

- **Sudoku has no practice-mode coin reward formula at all** in
  `EconomyConfig.getPracticeReward` — every other offline/single-player game does
  (20 games now have one after tonight's `handleGamePlayed` fix). This isn't the
  wiring bug that fix addressed (those 14 games already had a formula, just weren't
  reached); Sudoku genuinely has no formula defined, so completing it pays zero
  coins by design-or-oversight, unclear which. Needs a real decision (a reward
  value/formula, matching the "min(cap, score-based-scaling)" shape every other
  entry uses) rather than an invented number — picking Sudoku's actual reward
  economics isn't a mechanical fix. Raised 2026-09-25. No assumption made; left
  exactly as found.

---

## Resolved

- **Should `Vertex: Dominion` live in `MainMenu`'s game-host `CardLayout` slot like
  every other game, or get its own persistent nav destination?** Raised while
  drafting the platform strategy analysis (2026-09-25). Resolved same day: Dominion
  gets its own persistent nav tab. Recorded in `ROADMAP.md` under "Architecture
  Decisions."
