package forum;

import account.Role;
import account.ServerAccountStore;
import admin.AdminLog;
import net.ClientHandler;
import net.Message;
import net.MessageType;
import social.ModerationManager;
import support.Check;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * ForumHandlerTest
 * -----------------
 * Drives the REAL ClientHandler forum handlers end to end (real account store,
 * moderation manager, admin log and forum service; everything else null) - this is
 * where the security-relevant rules live: posting needs a login and isn't allowed
 * while muted or flooding, and deleting/locking is moderator/admin only, decided by
 * the server from the account's stored role, never from anything the client claims.
 * ForumServiceTest covers the content rules underneath.
 */
public class ForumHandlerTest
{
    /** A ClientHandler wired with just the four collaborators the forum handlers touch. */
    static class Handler extends ClientHandler
    {
        Handler(ServerAccountStore accounts, ModerationManager moderation, AdminLog adminLog, ForumService forum)
        {
            super(null, accounts, null, null, null, null, null, null, null, moderation,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, adminLog, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, forum);
        }
    }

    private static ServerAccountStore accounts;
    private static ModerationManager moderation;
    private static AdminLog adminLog;
    private static ForumService forum;

    private static Handler handlerFor(String username) throws Exception
    {
        Handler h = new Handler(accounts, moderation, adminLog, forum);
        if (username != null)
        {
            Field f = ClientHandler.class.getDeclaredField("loggedInUsername");
            f.setAccessible(true);
            f.set(h, username);
        }
        return h;
    }

    private static Message send(Handler h, Message request) throws Exception
    {
        Method m = ClientHandler.class.getDeclaredMethod("handle", Message.class);
        m.setAccessible(true);
        return (Message) m.invoke(h, request);
    }

    private static Message newThread(Handler h, String board, String title, String body) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_NEW_THREAD_REQUEST);
        r.setForumBoardId(board);
        r.setForumTitle(title);
        r.setChatText(body);
        return send(h, r);
    }

    private static Message reply(Handler h, String threadId, String body) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_REPLY_REQUEST);
        r.setForumThreadId(threadId);
        r.setChatText(body);
        return send(h, r);
    }

    private static Message delete(Handler h, String threadId, String postId) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_DELETE_REQUEST);
        r.setForumThreadId(threadId);
        r.setForumPostId(postId);
        return send(h, r);
    }

    private static Message lock(Handler h, String threadId, boolean locked) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_LOCK_REQUEST);
        r.setForumThreadId(threadId);
        r.setForumLocked(locked);
        return send(h, r);
    }

    private static Message view(Handler h, String threadId) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_THREAD_VIEW_REQUEST);
        r.setForumThreadId(threadId);
        return send(h, r);
    }

    private static Message list(Handler h, String board) throws Exception
    {
        Message r = new Message();
        r.setType(MessageType.FORUM_THREAD_LIST_REQUEST);
        r.setForumBoardId(board);
        return send(h, r);
    }

    private static boolean logContains(String text)
    {
        List<String> entries = adminLog.getRecent();
        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i).contains(text)) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        accounts = new ServerAccountStore();
        accounts.createAccount("alice", "password123", Role.PLAYER);
        accounts.createAccount("bob", "password123", Role.PLAYER);
        accounts.createAccount("carol", "password123", Role.PLAYER);
        accounts.createAccount("dave", "password123", Role.PLAYER);
        accounts.createAccount("modmo", "password123", Role.MODERATOR);
        accounts.createAccount("boss", "password123", Role.ADMIN);
        moderation = new ModerationManager();
        adminLog = new AdminLog();
        forum = new ForumService(new ForumStore("forum_handler_test.txt"), Arrays.asList("chess"));

        testReadingNeedsNoLogin(check);
        testPostingNeedsLogin(check);
        testMutedCannotPost(check);
        testFloodLimit(check);
        testModeratorOnlyDeleteAndLock(check);

        check.finish();
    }

    private static void testReadingNeedsNoLogin(Check check) throws Exception
    {
        Handler alice = handlerFor("alice");
        String id = newThread(alice, "general", "Hello", "first post").getForumThreadId();
        Handler guest = handlerFor(null);

        Message listed = list(guest, "general");
        check.check("Anyone connected can read a board's thread list, even logged out",
            listed.getType() == MessageType.FORUM_RESPONSE && listed.isSuccess() && listed.getForumEntries().size() == 1);
        Message viewed = view(guest, id);
        check.check("...and open a thread", viewed.isSuccess() && "Hello".equals(viewed.getForumTitle()) && viewed.getForumEntries().size() == 1
            && !viewed.isForumLocked());
        check.check("A board that doesn't exist is an error, not an empty list", !list(guest, "nope").isSuccess());
        check.check("A thread that doesn't exist is an error", !view(guest, "t999").isSuccess());
    }

    private static void testPostingNeedsLogin(Check check) throws Exception
    {
        Handler guest = handlerFor(null);
        Message r = newThread(guest, "general", "Sneaky", "logged out");
        check.check("Starting a thread while logged out is refused, and nothing is stored",
            !r.isSuccess() && r.getErrorText().toLowerCase().contains("log in") && forum.listThreads("general").size() == 1);

        String id = forum.listThreads("general").get(0).getId();
        check.check("Replying while logged out is refused too", !reply(guest, id, "hi").isSuccess() && forum.getThread(id).getPosts().size() == 1);
    }

    private static void testMutedCannotPost(Check check) throws Exception
    {
        moderation.mute("bob", 10);
        Handler bob = handlerFor("bob");
        int before = forum.listThreads("general").size();
        Message r = newThread(bob, "general", "Muted post", "should not appear");
        check.check("A muted player can't start a thread", !r.isSuccess() && r.getErrorText().toLowerCase().contains("muted")
            && forum.listThreads("general").size() == before);
        String id = forum.listThreads("general").get(0).getId();
        check.check("...or reply", !reply(bob, id, "nope").isSuccess() && forum.getThread(id).getPosts().size() == 1);

        moderation.unmute("bob");
        check.check("Once unmuted they can post again", reply(handlerFor("bob"), id, "back").isSuccess());
    }

    private static void testFloodLimit(Check check) throws Exception
    {
        Handler carol = handlerFor("carol");
        String id = forum.listThreads("general").get(0).getId();
        boolean first3 = reply(carol, id, "one").isSuccess() && reply(carol, id, "two").isSuccess() && reply(carol, id, "three").isSuccess();
        Message fourth = reply(carol, id, "four");
        check.check("Three quick posts go through, the fourth is refused as too fast",
            first3 && !fourth.isSuccess() && fourth.getErrorText().toLowerCase().contains("too fast"));
        check.check("The flood limit is per connection - someone else can still post",
            reply(handlerFor("dave"), id, "dave here").isSuccess());
    }

    private static void testModeratorOnlyDeleteAndLock(Check check) throws Exception
    {
        String id = newThread(handlerFor("alice"), "chess", "Openings", "opening post").getForumThreadId();
        reply(handlerFor("dave"), id, "a reply to delete");
        String replyId = forum.getThread(id).getPosts().get(1).getId();

        Handler player = handlerFor("dave");
        check.check("A normal player can't lock a thread", !lock(player, id, true).isSuccess() && !forum.getThread(id).isLocked());
        check.check("A normal player can't delete a reply or a thread",
            !delete(player, id, replyId).isSuccess() && !delete(player, id, null).isSuccess()
                && forum.getThread(id) != null && forum.getThread(id).getPosts().size() == 2);
        check.check("A logged-out connection can't either", !delete(handlerFor(null), id, null).isSuccess() && !lock(handlerFor(null), id, true).isSuccess());
        check.check("A refused attempt leaves no admin-log entry", !logContains("Deleted forum") && !logContains("Locked forum"));

        Handler mod = handlerFor("modmo");
        check.check("A moderator can lock a thread, and it is logged",
            lock(mod, id, true).isSuccess() && forum.getThread(id).isLocked() && logContains("Locked forum thread " + id));
        check.check("A locked thread takes no replies", !reply(handlerFor("alice"), id, "still here?").isSuccess());
        check.check("A moderator can unlock it again, and it is logged",
            lock(mod, id, false).isSuccess() && !forum.getThread(id).isLocked() && logContains("Unlocked forum thread " + id));

        check.check("A moderator can delete a single reply, and it is logged",
            delete(mod, id, replyId).isSuccess() && forum.getThread(id).getPosts().size() == 1 && logContains("Deleted forum post " + replyId));
        check.check("An admin can delete a whole thread, and it is logged with its title and author",
            delete(handlerFor("boss"), id, null).isSuccess() && forum.getThread(id) == null
                && logContains("Deleted forum thread " + id) && logContains("Openings"));
        check.check("Deleting something already gone reports an error", !delete(mod, id, null).isSuccess());
    }
}
