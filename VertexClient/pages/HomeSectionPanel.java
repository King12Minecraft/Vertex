package pages;

/**
 * HomeSectionPanel
 * ----------------
 * The base for one card on the Home page (see HomePanel): a SectionCard whose subclasses - HomeWelcomeSection,
 * HomeContinueSection, HomeQuickPlaySection, HomeFriendsSection, HomeTournamentsSection, HomeWhatsNewSection - each
 * load their own data in refresh(), which HomePanel calls when Home is built and every 30 seconds.
 */
public abstract class HomeSectionPanel extends SectionCard
{
    protected HomeSectionPanel(String title, String placeholderText)
    {
        super(title, placeholderText);
    }
}
