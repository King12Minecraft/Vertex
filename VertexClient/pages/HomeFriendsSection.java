package pages;

/**
 * HomeFriendsSection - PLACEHOLDER (design pending; see HomeSectionPanel).
 *
 * Meant to become: which friends are online right now, with a quick way to invite them or say hi.
 *
 * Data already available:
 *   - FRIEND_LIST_REQUEST -> the friend list with online status (pages/FriendsPanel loads and renders it, including
 *     avatars via economy.AvatarCache and the invite/join buttons - reuse its row rather than rewriting it);
 *   - live presence changes arrive as FRIEND_STATUS_UPDATE pushes (MainMenu already listens to badge the sidebar's
 *     Friends entry) - a section can implement net.NetworkManager.PushListener to update without polling;
 *   - opening a DM: navigate to Pages.CHAT; inviting to a game: see FriendsPanel / games.GameLauncher.
 * Nothing new is needed on the server for a first version.
 */
public class HomeFriendsSection extends HomeSectionPanel
{
    public HomeFriendsSection()
    {
        super("FRIENDS ONLINE", "Friends who are online right now will show up here.");
    }
}
