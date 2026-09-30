package games;

import mechanics.ReconnectRegistry;
import net.Message;
import net.MessageType;
import support.Check;
import support.FakeClientHandler;

/**
 * CardRushHiddenInfoTest
 * -----------------------
 * A player's hand must never reach the other player's client - a window only shows the opponent's card COUNT,
 * so anything more is a free look at their hand for anyone running a modified client. Every message that carries
 * the state (match found, updates, the waiting notice, the resume result) must show the recipient their own cards
 * and only placeholder zeros, of the right count, for the opponent's.
 */
public class CardRushHiddenInfoTest
{
    /** hand text for the given side (0 = A, 1 = B) out of "piles|handA|handB|stocks". */
    private static String hand(String state, int side) { return state.split("\\|", -1)[1 + side]; }

    private static boolean allZeros(String hand)
    {
        if (hand.isEmpty()) return true;
        for (String card : hand.split(",")) if (!card.equals("0")) return false;
        return true;
    }

    private static boolean hasRealCards(String hand)
    {
        if (hand.isEmpty()) return false;
        for (String card : hand.split(",")) if (card.equals("0")) return false;
        return true;
    }

    public static void main(String[] args)
    {
        Check check = new Check();
        economy.EconomyManager economy = new economy.EconomyManager(new account.ServerAccountStore(), new economy.TransactionManager());
        CardRushMatchManager manager = new CardRushMatchManager(economy, new economy.GameHistoryManager(), new social.ChatManager(), null);
        FakeClientHandler a = new FakeClientHandler("cr-a", 3001);
        FakeClientHandler b = new FakeClientHandler("cr-b", 3002);
        CardRushMatch match = new CardRushMatch("cr-hid", a, b, manager, economy, null);
        match.start();

        String aFound = a.lastOfType(MessageType.CARDRUSH_MATCH_FOUND).getBoardState();
        String bFound = b.lastOfType(MessageType.CARDRUSH_MATCH_FOUND).getBoardState();
        check.check("A sees their own real hand and 5 hidden cards for B", hasRealCards(hand(aFound, 0)) && allZeros(hand(aFound, 1)) && hand(aFound, 1).split(",").length == 5);
        check.check("B sees their own real hand and 5 hidden cards for A", hasRealCards(hand(bFound, 1)) && allZeros(hand(bFound, 0)) && hand(bFound, 0).split(",").length == 5);

        // a play: forced to be legal (a 6 onto a 5) so an update really goes to both
        try
        {
            java.lang.reflect.Field hf = CardRushMatch.class.getDeclaredField("handA");
            hf.setAccessible(true);
            java.lang.reflect.Field pf = CardRushMatch.class.getDeclaredField("centerPile1");
            pf.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Integer> handA = (java.util.List<Integer>) hf.get(match);
            handA.set(0, 60);
            pf.setInt(match, 50);
        }
        catch (Exception e) { throw new RuntimeException(e); }
        match.playCard(a, 60, 1);
        check.check("the play produced an update for both players", a.countOfType(MessageType.CARDRUSH_UPDATE) == 1 && b.countOfType(MessageType.CARDRUSH_UPDATE) == 1);
        for (Message m : a.sent)
        {
            if (m.getType() == MessageType.CARDRUSH_UPDATE)
            {
                check.check("every update to A hides B's hand", allZeros(hand(m.getBoardState(), 1)));
            }
        }
        for (Message m : b.sent)
        {
            if (m.getType() == MessageType.CARDRUSH_UPDATE)
            {
                check.check("every update to B hides A's hand", allZeros(hand(m.getBoardState(), 0)));
            }
        }

        // the waiting notice and the resume result
        match.handleDisconnect(b);
        Message notice = a.lastOfType(MessageType.OPPONENT_DISCONNECTED_NOTICE);
        check.check("the waiting notice to A hides B's hand", notice != null && allZeros(hand(notice.getBoardState(), 1)) && hasRealCards(hand(notice.getBoardState(), 0)));
        FakeClientHandler bAgain = new FakeClientHandler("cr-b", 3002);
        ReconnectRegistry.ReconnectResult r = ReconnectRegistry.shared().tryReconnect(3002, bAgain);
        check.check("the resume result for B shows B's hand and hides A's", r != null && hasRealCards(hand(r.boardState, 1)) && allZeros(hand(r.boardState, 0)));
        Message afterResume = a.lastOfType(MessageType.CARDRUSH_UPDATE);
        check.check("A's refresh on B's return still hides B's hand", afterResume != null && allZeros(hand(afterResume.getBoardState(), 1)));

        check.finish();
        System.exit(0);
    }
}
