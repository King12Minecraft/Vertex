/**
 * Role
 * ----
 * The three platform roles (Section 12). Stored on the Account, never
 * on the username, so it survives username changes. Server-side
 * enforcement (Phase 4/5) is what actually matters for security - this
 * enum alone is not a security boundary.
 */
public enum Role
{
    PLAYER,
    MODERATOR,
    ADMIN
}
