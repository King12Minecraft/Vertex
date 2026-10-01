package games;

import account.Account;
import account.ServerAccountStore;
import economy.EconomyManager;
import economy.GameHistoryManager;
import economy.TransactionManager;
import net.Message;
import net.MessageType;
import social.ChatManager;
import support.Check;
import support.FakeClientHandler;

import java.util.List;

/**
 * CaptionChaosMatchTest
 * ---------------------
 * The rules that make Caption Chaos fair, played through the real manager + match with fake connections:
 * matchmaking at 3 players, the prompt going to everyone, answers staying anonymous on the wire until the reveal,
 * phases ending early once everyone has acted, nobody voting for their own answer or twice, the vote counting and
 * 100-points-per-vote scoring, a full three-round match ending with the right winner and a coin award, and the
 * match ending without a win when too many players leave. Timers only matter for the slow paths, so the reveal pause
 * is shortened and the clock-driven phase ends are left to the real Timer (not waited on).
 */
public class CaptionChaosMatchTest
{
    public static void main(String[] args) throws Exception
    {
        Check check = new Check();
        CaptionChaosMatch.resultMs = 50;

        testPlayRoundAndScoring(check);
        testFullMatchWinnerAndAward(check);
        testTooManyLeaveEndsWithoutAWin(check);

        check.finish();
    }

    private static CaptionChaosMatchManager manager(ServerAccountStore accounts)
    {
        return new CaptionChaosMatchManager(new GameHistoryManager(), new ChatManager(),
            new EconomyManager(accounts, new TransactionManager()));
    }

    private static void testPlayRoundAndScoring(Check check)
    {
        ServerAccountStore accounts = new ServerAccountStore();
        CaptionChaosMatchManager manager = manager(accounts);
        FakeClientHandler a = new FakeClientHandler("Ann", 1);
        FakeClientHandler b = new FakeClientHandler("Ben", 2);
        FakeClientHandler c = new FakeClientHandler("Cy", 3);

        manager.findMatch(a);
        manager.findMatch(b);
        check.check("two players are not enough to start", a.lastOfType(MessageType.CAPTIONCHAOS_WRITE_START) == null);
        manager.findMatch(c);
        Message start = a.lastOfType(MessageType.CAPTIONCHAOS_WRITE_START);
        check.check("three players start the match: everyone gets round 1 of 3 and the same prompt",
            start != null && start.getCaptionRound() == 1 && start.getCaptionTotalRounds() == 3
                && start.getCaptionPrompt() != null && !start.getCaptionPrompt().isEmpty()
                && start.getCaptionPrompt().equals(b.lastOfType(MessageType.CAPTIONCHAOS_WRITE_START).getCaptionPrompt())
                && c.lastOfType(MessageType.CAPTIONCHAOS_WRITE_START) != null);
        check.check("match chat starts LOCKED (anonymity)", "LOCKED".equals(a.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState()));

        // submit through the handler wiring the real game uses
        submit(a, "Ann's answer");
        check.check("an answer alone does not end the writing phase", a.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START) == null);
        submit(a, "changed my mind");
        submit(b, "   ");
        check.check("an empty answer is ignored (Ben can still answer)", b.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START) == null);
        submit(b, "Ben's answer");
        submit(c, "Cy's answer");

        Message vote = a.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START);
        check.check("once everyone has answered the voting phase starts at once", vote != null && b.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START) != null);
        List<String> options = vote.getCaptionOptions();
        check.check("three anonymous options, in the same order for everyone",
            options.size() == 3 && options.equals(b.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START).getCaptionOptions())
                && options.equals(c.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START).getCaptionOptions()));
        check.check("the options are the answers only - no author names anywhere in the vote message",
            options.contains("Ann's answer") && options.contains("Ben's answer") && options.contains("Cy's answer")
                && !options.toString().contains("|") && vote.getCaptionResults() == null);
        check.check("a second answer from the same player did not replace the first", options.contains("Ann's answer") && !options.contains("changed my mind"));
        int ownA = vote.getCaptionIndex();
        int ownB = b.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START).getCaptionIndex();
        int ownC = c.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START).getCaptionIndex();
        check.check("each player is told which option is their own", "Ann's answer".equals(options.get(ownA))
            && "Ben's answer".equals(options.get(ownB)) && "Cy's answer".equals(options.get(ownC)));

        voteFor(a, ownA);   // for herself - must be ignored
        voteFor(a, 99);     // not an option - must be ignored
        check.check("voting for your own answer or a non-existent one is ignored", a.lastOfType(MessageType.CAPTIONCHAOS_ROUND_RESULT) == null);
        voteFor(a, ownB);
        voteFor(a, ownC);   // second vote from Ann - ignored
        voteFor(b, ownC);
        check.check("the round waits for the last voter", a.lastOfType(MessageType.CAPTIONCHAOS_ROUND_RESULT) == null);
        voteFor(c, ownB);

        Message result = a.lastOfType(MessageType.CAPTIONCHAOS_ROUND_RESULT);
        check.check("when everyone has voted the round is revealed", result != null && result.getCaptionRound() == 1);
        check.check("Ben (2 votes) is first, with his name now attached", result.getCaptionResults().get(0).equals("Ben|Ben's answer|2"));
        check.check("Cy has 1 vote, Ann none (her second vote was not counted)",
            result.getCaptionResults().get(1).equals("Cy|Cy's answer|1") && result.getCaptionResults().get(2).equals("Ann|Ann's answer|0"));
        check.check("100 points per vote: Ben 200, Cy 100, Ann 0",
            result.getCaptionScores().equals(java.util.Arrays.asList("Ben:200", "Cy:100", "Ann:0")));
    }

    private static void testFullMatchWinnerAndAward(Check check) throws Exception
    {
        ServerAccountStore accounts = new ServerAccountStore();
        Account winnerAccount = accounts.createAccount("CcWinner", "password123", account.Role.PLAYER);
        CaptionChaosMatchManager manager = manager(accounts);
        FakeClientHandler a = new FakeClientHandler("CcWinner", winnerAccount.getAccountId());
        FakeClientHandler b = new FakeClientHandler("CcSecond", 902);
        FakeClientHandler c = new FakeClientHandler("CcThird", 903);
        manager.findMatch(a);
        manager.findMatch(b);
        manager.findMatch(c);
        int before = winnerAccount.getCoins();

        for (int round = 1; round <= 3; round++)
        {
            check.check("round " + round + " begins", waitFor(a, MessageType.CAPTIONCHAOS_WRITE_START, round));
            submit(a, "a" + round);
            submit(b, "b" + round);
            submit(c, "c" + round);
            Message vote = a.lastOfType(MessageType.CAPTIONCHAOS_VOTE_START);
            List<String> options = vote.getCaptionOptions();
            // everyone votes for the winner's answer (a winner votes for someone else)
            int winnerIndex = options.indexOf("a" + round);
            voteFor(b, winnerIndex);
            voteFor(c, winnerIndex);
            voteFor(a, options.indexOf("b" + round));
        }
        check.check("after round 3 the match is over", waitFor(a, MessageType.CAPTIONCHAOS_MATCH_OVER, 0));
        Message over = a.lastOfType(MessageType.CAPTIONCHAOS_MATCH_OVER);
        check.check("the player with the most votes wins, and the final scores are 600/300/0",
            over.getCaptionWinners().equals(java.util.Collections.singletonList("CcWinner")) && !over.isCaptionAborted()
                && over.getCaptionScores().get(0).equals("CcWinner:600") && over.getCaptionScores().get(1).equals("CcSecond:300"));
        check.check("everyone is told the match is over", b.lastOfType(MessageType.CAPTIONCHAOS_MATCH_OVER) != null && c.lastOfType(MessageType.CAPTIONCHAOS_MATCH_OVER) != null);
        // >= because winning also completes the generic "win a match" quests, which pay their own bonus on top
        check.check("the winner got at least the win reward", winnerAccount.getCoins() >= before + economy.EconomyConfig.getWinReward("caption-chaos")
            && economy.EconomyConfig.getWinReward("caption-chaos") > 0);
        check.check("chat unlocked for the post-match gg", "OPEN".equals(a.lastOfType(MessageType.MATCH_CHAT_STATE).getMatchChatState()));
    }

    private static void testTooManyLeaveEndsWithoutAWin(Check check)
    {
        CaptionChaosMatchManager manager = manager(new ServerAccountStore());
        FakeClientHandler a = new FakeClientHandler("Lv-A", 911);
        FakeClientHandler b = new FakeClientHandler("Lv-B", 912);
        FakeClientHandler c = new FakeClientHandler("Lv-C", 913);
        manager.findMatch(a);
        manager.findMatch(b);
        manager.findMatch(c);
        submit(a, "x");
        // two of three leave: fewer than two players remain
        // the handlers call match.handleDisconnect(this) on disconnect; reach it the same way the match was attached
        for (FakeClientHandler h : new FakeClientHandler[] { b, c })
        {
            h.captionChaosForTest().handleDisconnect(h);
        }
        Message over = a.lastOfType(MessageType.CAPTIONCHAOS_MATCH_OVER);
        check.check("with fewer than two players left the match ends, flagged as aborted, and no winner is named",
            over != null && over.isCaptionAborted() && over.getCaptionWinners().isEmpty());
    }

    // ---- helpers: drive the match the way ClientHandler does ----

    private static void submit(FakeClientHandler who, String text)
    {
        who.captionChaosForTest().submitAnswer(who, text);
    }

    private static void voteFor(FakeClientHandler who, int index)
    {
        who.captionChaosForTest().submitVote(who, index);
    }

    /** Waits (briefly) for the nth message of a type - the between-round pause is a real Timer. */
    private static boolean waitFor(FakeClientHandler who, MessageType type, int count) throws InterruptedException
    {
        for (int i = 0; i < 100; i++)
        {
            boolean ok = count == 0 ? who.lastOfType(type) != null : who.countOfType(type) >= count;
            if (ok) return true;
            Thread.sleep(50);
        }
        return false;
    }
}
