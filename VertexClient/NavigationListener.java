/**
 * NavigationListener
 * -------------------
 * Implemented by whoever owns the CardLayout (MainMenu) so Sidebar can
 * report which page the player clicked without needing to know anything
 * about how pages are actually switched.
 */
public interface NavigationListener
{
    void onNavigate(String pageKey);
}
