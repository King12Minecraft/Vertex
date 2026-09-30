package pages;

/**
 * HomeWelcomeSection - PLACEHOLDER (design pending; see HomeSectionPanel).
 *
 * Meant to become: a greeting with the player's name, today's daily-login reward and login streak,
 * and how far along their active quests are.
 *
 * Data already available:
 *   - the name: account.Session.getCurrentAccount().getUsername();
 *   - login streak and today's reward: the LOGIN_RESPONSE's getLoginStreak()/getDailyRewardCoins() (the login
 *     screens show the reward popup once - see LoginPanel.showDailyRewardPopup); the account also carries
 *     getLoginStreak() server-side, but the client Account may not expose it after login;
 *   - quest progress: CHALLENGES_REQUEST -> response.getChallenges() (ChallengeProgressInfo: progress, target,
 *     isCompleted) - the sidebar's quest mini-list and pages/QuestsPanel already load and render these;
 *     pages/QuestRow is the ready-made row.
 * Still to build: a way to know whether today's reward has already been claimed (login is the claim).
 */
public class HomeWelcomeSection extends HomeSectionPanel
{
    public HomeWelcomeSection()
    {
        super("WELCOME BACK", "Your daily reward, login streak and quest progress will show up here.");
    }
}
