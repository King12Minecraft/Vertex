package forum;

import support.Check;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * ForumServiceTest
 * -----------------
 * The forum's server-side rules and storage. The store is a flat file, so the
 * important ones are the persistence/forgery cases (a post body with an embedded
 * newline must reload as ONE post, never as a forged extra record - the bug class
 * this project already fixed once in GameSuggestionStore), plus ordering, the
 * length/locked/full rules, and that damaged files load what they can. Who may
 * post/delete/lock (login, mute, flood, moderator role) lives in ClientHandler and
 * needs real managers, so it isn't covered here. Each test uses its own file name;
 * test.sh runs every test class from a fresh temp directory.
 */
public class ForumServiceTest
{
    private static int fileCounter = 0;

    private static String freshFile()
    {
        return "forum_test_" + (fileCounter++) + ".txt";
    }

    private static ForumService service(String file)
    {
        return new ForumService(new ForumStore(file), Arrays.asList("chess", "dice-duel"));
    }

    private static String repeat(char c, int n)
    {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        testBoards(check);
        testNewThreadAndCleaning(check);
        testBodyRules(check);
        testRepliesAndOrdering(check);
        testLockAndDelete(check);
        testThreadFullAndListCap(check);
        testPersistence(check);
        testForgeryRoundTrip(check);
        testDamagedFile(check);
        testCodec(check);

        check.finish();
    }

    private static void testBoards(Check check)
    {
        ForumService s = service(freshFile());
        check.check("The general board and every game board exist",
            s.isValidBoard("general") && s.isValidBoard("chess") && s.isValidBoard("dice-duel"));
        check.check("A made-up board or null doesn't", !s.isValidBoard("not-a-board") && !s.isValidBoard(null));
        check.check("Posting to a board that doesn't exist is refused",
            !s.newThread("ann", "not-a-board", "Hi", "Hello").ok && s.listThreads("not-a-board") == null);
    }

    private static void testNewThreadAndCleaning(Check check)
    {
        ForumService s = service(freshFile());
        ForumService.Result r = s.newThread("ann", "general", "  Best   opening \n\t move?  ", "I always play e4.");
        ForumThread t = s.getThread(r.id);
        check.check("A new thread is created with a thread id and its opening post",
            r.ok && r.id.startsWith("t") && t != null && t.getPosts().size() == 1 && t.getReplyCount() == 0);
        check.check("The title is forced onto one line with tidy spacing",
            "Best opening move?".equals(t.getTitle()));
        check.check("Author and board are recorded", "ann".equals(t.getAuthor()) && "general".equals(t.getBoardId())
            && "ann".equals(t.getPosts().get(0).getAuthor()) && "I always play e4.".equals(t.getPosts().get(0).getBody()));

        check.check("An empty or whitespace-only title is refused",
            !s.newThread("ann", "general", "", "body").ok && !s.newThread("ann", "general", "  \n\t ", "body").ok);
        check.check("A 100-character title is fine, 101 is refused",
            s.newThread("ann", "general", repeat('a', 100), "body").ok && !s.newThread("ann", "general", repeat('a', 101), "body").ok);
    }

    private static void testBodyRules(Check check)
    {
        ForumService s = service(freshFile());
        check.check("An empty body is refused", !s.newThread("ann", "general", "T", "").ok && !s.newThread("ann", "general", "T", "   ").ok);
        check.check("A 2000-character body is fine, 2001 is refused",
            s.newThread("ann", "general", "T", repeat('b', 2000)).ok && !s.newThread("ann", "general", "T", repeat('b', 2001)).ok);

        ForumService.Result r = s.newThread("ann", "general", "T", "line one\r\nline two\r\n\r\n\r\n\r\nline three\u0007\tend");
        String body = s.getThread(r.id).getPosts().get(0).getBody();
        check.check("Line breaks are kept and normalised, blank-line spam collapses, control characters go, tabs become spaces",
            "line one\nline two\n\nline three end".equals(body));
    }

    private static void testRepliesAndOrdering(Check check)
    {
        ForumService s = service(freshFile());
        String a = s.newThread("ann", "general", "First", "a").id;
        String b = s.newThread("ben", "general", "Second", "b").id;
        String c = s.newThread("cat", "chess", "Chess thread", "c").id;

        List<ForumThread> general = s.listThreads("general");
        check.check("Threads list newest activity first, and only for their own board",
            general.size() == 2 && general.get(0).getId().equals(b) && general.get(1).getId().equals(a)
                && s.listThreads("chess").size() == 1 && s.listThreads("chess").get(0).getId().equals(c));

        ForumService.Result reply = s.reply("dan", a, "  nice one  ");
        ForumThread afterReply = s.getThread(a);
        check.check("A reply is added after the opening post, trimmed, with the replier as author",
            reply.ok && afterReply.getReplyCount() == 1 && "nice one".equals(afterReply.getPosts().get(1).getBody())
                && "dan".equals(afterReply.getPosts().get(1).getAuthor()));
        check.check("Replying bumps a thread back to the top of its board", s.listThreads("general").get(0).getId().equals(a));
        check.check("Replying to a thread that doesn't exist fails", !s.reply("dan", "t999", "hi").ok);
        check.check("A reply obeys the same body rules", !s.reply("dan", a, "").ok && !s.reply("dan", a, repeat('x', 2001)).ok);
    }

    private static void testLockAndDelete(Check check)
    {
        ForumService s = service(freshFile());
        String id = s.newThread("ann", "general", "Topic", "opening").id;
        s.reply("ben", id, "reply one");
        s.reply("cat", id, "reply two");
        String replyId = s.getThread(id).getPosts().get(1).getId();

        check.check("Locking a thread stops replies", s.setLocked(id, true).ok && s.getThread(id).isLocked()
            && !s.reply("dan", id, "too late").ok);
        check.check("Unlocking lets replies through again", s.setLocked(id, false).ok && s.reply("dan", id, "back").ok);
        check.check("Locking a thread that doesn't exist fails", !s.setLocked("t999", true).ok);

        check.check("Deleting one reply removes just that reply",
            s.delete(id, replyId).ok && s.getThread(id).getPosts().size() == 3 && s.getThread(id).getPosts().get(1).getBody().equals("reply two"));
        check.check("Deleting a reply that isn't there fails", !s.delete(id, replyId).ok);
        check.check("Deleting the opening post deletes the whole thread",
            s.delete(id, s.getThread(id).getPosts().get(0).getId()).ok && s.getThread(id) == null && s.listThreads("general").isEmpty());

        String other = s.newThread("ann", "general", "Other", "x").id;
        check.check("Deleting with no post id deletes the thread", s.delete(other, null).ok && s.getThread(other) == null);
        check.check("Deleting a thread that doesn't exist fails", !s.delete("t999", null).ok);
    }

    private static void testThreadFullAndListCap(Check check)
    {
        ForumService s = service(freshFile());
        String id = s.newThread("ann", "general", "Long one", "start").id;
        boolean allOk = true;
        for (int i = 1; i < ForumService.MAX_POSTS_PER_THREAD; i++)
        {
            allOk &= s.reply("ben", id, "r" + i).ok;
        }
        check.check("A thread accepts replies up to its post cap", allOk && s.getThread(id).getPosts().size() == ForumService.MAX_POSTS_PER_THREAD);
        check.check("...and then refuses more", !s.reply("ben", id, "one too many").ok);

        ForumService s2 = service(freshFile());
        for (int i = 0; i < ForumService.MAX_THREADS_LISTED + 5; i++)
        {
            s2.newThread("ann", "chess", "Thread " + i, "b");
        }
        List<ForumThread> listed = s2.listThreads("chess");
        check.check("A board lists at most 100 threads, newest first",
            listed.size() == ForumService.MAX_THREADS_LISTED && "Thread 104".equals(listed.get(0).getTitle()));
    }

    private static void testPersistence(Check check)
    {
        String file = freshFile();
        ForumService s = service(file);
        String a = s.newThread("ann", "general", "Persisted", "first body\nwith two lines").id;
        s.reply("ben", a, "a reply");
        s.setLocked(a, true);
        String b = s.newThread("cat", "chess", "Another", "second").id;

        ForumService reloaded = service(file);
        ForumThread ra = reloaded.getThread(a);
        check.check("Threads, posts, authors and the locked flag survive a reload",
            ra != null && "Persisted".equals(ra.getTitle()) && ra.isLocked() && ra.getPosts().size() == 2
                && "first body\nwith two lines".equals(ra.getPosts().get(0).getBody())
                && "ben".equals(ra.getPosts().get(1).getAuthor()) && reloaded.getThread(b) != null);
        check.check("Board ordering survives a reload", reloaded.listThreads("general").size() == 1 && reloaded.listThreads("chess").size() == 1);

        String c = reloaded.newThread("dan", "general", "After reload", "x").id;
        check.check("New ids after a reload never collide with existing ones", !c.equals(a) && !c.equals(b)
            && reloaded.getThread(a).getTitle().equals("Persisted"));
        check.check("Deletions are persisted too",
            reloaded.delete(c, null).ok && service(file).getThread(c) == null);
    }

    private static void testForgeryRoundTrip(Check check)
    {
        String file = freshFile();
        ForumService s = service(file);
        String evil = "harmless\nT\tt99\tgeneral\tHacked thread\tadmin\t1\t1\t0\nP\tp99\tt99\tadmin\t1\tforged post\n\\n\\t";
        String id = s.newThread("mallory", "general", "Sneaky\nT\tt98\tgeneral\tx\tadmin\t1\t1\t0", evil).id;
        int lines = 0;
        try { lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8).size(); } catch (Exception e) { }

        ForumService reloaded = service(file);
        ForumThread t = reloaded.getThread(id);
        check.check("A post body with embedded record text is written as ONE post (2 file lines: 1 thread + 1 post)", lines == 2);
        String stored = ForumService.cleanBody(evil);
        check.check("...and reloads exactly as the service stored it (forged-looking text kept as plain text, literal backslash sequences intact)",
            t != null && t.getPosts().size() == 1 && stored.equals(t.getPosts().get(0).getBody())
                && stored.contains("Hacked thread") && stored.endsWith("\\n\\t"));
        check.check("No forged thread appears after reload", reloaded.getThread("t99") == null && reloaded.getThread("t98") == null
            && reloaded.listThreads("general").size() == 1);
        check.check("The title's line break/tab were flattened to spaces, not written as record structure",
            t.getTitle().startsWith("Sneaky T t98 general"));
    }

    private static void testDamagedFile(Check check) throws Exception
    {
        String file = freshFile();
        String good = ForumCodec.join("T", "t1", "general", "Good thread", "ann", "100", "200", "0") + "\n"
            + ForumCodec.join("P", "p2", "t1", "ann", "100", "Opening") + "\n"
            + "this is not a record at all\n"
            + ForumCodec.join("T", "t3", "general", "Bad number", "ben", "notanumber", "200", "0") + "\n"
            + ForumCodec.join("T", "t4", "general", "No opening post", "cat", "300", "300", "0") + "\n"
            + ForumCodec.join("P", "p5", "t-missing", "cat", "300", "orphan post") + "\n"
            + "\n"
            + ForumCodec.join("P", "p6", "t1", "dan", "150", "A reply") + "\n";
        Files.write(Paths.get(file), good.getBytes(StandardCharsets.UTF_8));

        ForumService s = service(file);
        ForumThread t = s.getThread("t1");
        check.check("A damaged file loads every good record and skips the bad ones without failing",
            t != null && t.getPosts().size() == 2 && "A reply".equals(t.getPosts().get(1).getBody()));
        check.check("A thread with an unreadable number, or no opening post, is dropped", s.getThread("t3") == null && s.getThread("t4") == null);
        check.check("New ids continue above the highest id in the file", Long.parseLong(s.newThread("ann", "general", "N", "b").id.substring(1)) > 6);
    }

    private static void testCodec(Check check)
    {
        String line = ForumCodec.join("a\tb", "line1\nline2", "back\\slash", "", "cr\rhere");
        check.check("An encoded record is a single physical line", line.indexOf('\n') < 0 && line.indexOf('\r') < 0);
        check.check("It has exactly one raw tab between each of the 5 fields", line.split("\t", -1).length == 5);
        List<String> fields = ForumCodec.split(line);
        check.check("Splitting gives back every field exactly, including tabs, newlines, backslashes and empties",
            fields.size() == 5 && "a\tb".equals(fields.get(0)) && "line1\nline2".equals(fields.get(1))
                && "back\\slash".equals(fields.get(2)) && "".equals(fields.get(3)) && "cr\rhere".equals(fields.get(4)));

        ForumThread thread = new ForumThread("t1", "general", "Ti\ttle", "ann", 5L, 9L, true);
        thread.getPosts().add(new ForumPost("p2", "ann", 5L, "body\nline"));
        thread.getPosts().add(new ForumPost("p3", "ben", 6L, "reply"));
        List<String> summary = ForumCodec.split(ForumCodec.threadSummaryLine(thread));
        check.check("A thread summary line carries id, board, title, author, replies, last activity and locked",
            summary.size() == 7 && "t1".equals(summary.get(0)) && "Ti\ttle".equals(summary.get(2)) && "1".equals(summary.get(4))
                && "9".equals(summary.get(5)) && "1".equals(summary.get(6)));
        List<String> post = ForumCodec.split(ForumCodec.postLine(thread.getPosts().get(0)));
        check.check("A post line carries id, author, time and body", post.size() == 4 && "body\nline".equals(post.get(3)));
    }
}
