package pages;

/**
 * HomeTournamentsSection - PLACEHOLDER (design pending; see HomeSectionPanel).
 *
 * Meant to become: tournaments that are open to join or running right now.
 *
 * Data already available:
 *   - TOURNAMENT_LIST_REQUEST / TOURNAMENT_LIST_RESPONSE - the server sends this as a direct reply AND broadcasts it
 *     on every roster change, which is why it is deliberately NOT in NetworkManager.RESPONSE_TYPES (see the note
 *     there): a section should listen for it as a push (net.NetworkManager.PushListener) instead of a blocking send();
 *     pages/TournamentsPanel already does this and renders the entries;
 *   - team tournaments have the parallel TEAM_TOURNAMENT_LIST_RESPONSE.
 * Nothing new is needed on the server for a first version. Clicking through should go to Pages.TOURNAMENTS.
 */
public class HomeTournamentsSection extends HomeSectionPanel
{
    public HomeTournamentsSection()
    {
        super("TOURNAMENTS", "Tournaments you can join will show up here.");
    }
}
