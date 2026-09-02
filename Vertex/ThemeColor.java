/**
 * ThemeColor
 * ----------
 * Every color "role" a UI component might need, e.g. BG_PANEL for a card
 * background or TEXT_MUTED for secondary text. Components store which
 * role they want (not a literal Color) and ask ThemeManager to resolve
 * it against the currently active Theme.
 */
public enum ThemeColor
{
    BG_APP,
    BG_SIDEBAR,
    BG_TOPBAR,
    BG_PANEL,
    BG_PANEL_HOVER,
    ACCENT,
    ACCENT_HOVER,
    ACCENT_DIM,
    ACCENT_GRADIENT_START,
    ACCENT_GRADIENT_END,
    SUCCESS,
    TEXT_PRIMARY,
    TEXT_SECONDARY,
    TEXT_MUTED,
    BORDER
}
