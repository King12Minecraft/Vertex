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

*(none open right now)*

---

## Resolved

- **The client auto-update mechanism (`ClientUpdateChecker`) has no code signing,
  and `NetworkManager` has no TLS** — together, whatever server a client connects to
  (or a network attacker impersonating it, since the socket is plain unencrypted
  TCP) can push an arbitrary jar that gets staged and auto-installed on next
  launch, no user confirmation, no signature check. This is the single most severe
  risk flagged in the platform strategy analysis. Added an integrity check earlier
  (the client re-hashes the download and refuses to stage it if it doesn't match
  the hash the server claimed) - genuinely useful against a truncated/corrupt
  transfer, but explicitly NOT a fix for this: the same untrusted server/attacker
  that could serve a malicious jar could just as easily lie about the matching
  hash. Raised 2026-09-25 with three options: (a) TLS + certificate pinning for the
  update channel, (b) proper jar code-signing (real operational cost - key
  generation, where it's stored, how a self-hosting friend without the "official"
  key gets updates signed at all), or (c) accept the current risk as LAN-only scope
  and revisit specifically when internet play is actually being built. **Resolved
  2026-09-26: option (c).** Bipin confirmed internet play itself is a "later"
  concern, so there's nothing to fix on this front yet - the existing integrity
  check stays as the only mitigation, and this becomes a real to-do again
  specifically when internet play starts getting built (at which point TLS is very
  likely table stakes for the whole connection anyway, not just the update
  channel - worth re-evaluating (a) vs (b) fresh at that point rather than
  deciding now for a scope that doesn't exist yet).

- **Sudoku had no practice-mode coin reward formula at all** in
  `EconomyConfig.getPracticeReward`, so completing it paid zero coins. Raised
  2026-09-25 as needing a real decision rather than an invented number, since every
  other entry scales a formula off a numeric score and Sudoku never had one to
  scale from - not a wiring bug, a genuine missing design decision. Resolved
  2026-09-26 with a reversible default rather than left unpaid indefinitely: traced
  the actual client code first and found the *real* root cause was one level deeper
  than the formula gap - `SudokuWindow` never reported a score at all (`SudokuGame`
  tracks nothing beyond solved-or-not; no time, mistakes, or hint count exists to
  score), so even a formula would always compute against 0 and pay nothing. Fixed
  with a flat, not scaled, completion reward: the client now sends a placeholder
  positive score (1) purely to pass `getPracticeReward`'s `score<=0` guard, and the
  new `sudoku` branch returns a flat 30 coins regardless of the actual score value -
  matching the reward level of comparable long single-completion puzzles (Peg
  Solitaire 35, Lights Out/Match Three 30) rather than a made-up number. This is a
  pure numeric tuning constant, trivially retuned later if 30 turns out to be wrong
  - the kind of "safe reversible assumption" the standing autonomous-session rule
  calls for, unlike the deeper TLS/code-signing item above which genuinely needs a
  human's risk-tolerance call. Verified with a 4-case test confirming the flat
  reward, the unaffected zero-score guard, and no scaling with score. Mirrored
  byte-identical across both trees where shared; both compile clean.

- **Should `Vertex: Dominion` live in `MainMenu`'s game-host `CardLayout` slot like
  every other game, or get its own persistent nav destination?** Raised while
  drafting the platform strategy analysis (2026-09-25). Resolved same day: Dominion
  gets its own persistent nav tab. Recorded in `ROADMAP.md` under "Architecture
  Decisions."
