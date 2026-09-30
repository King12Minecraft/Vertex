package forum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ForumService
 * -------------
 * The forum's rules, independent of the network: which boards exist, how long a title
 * or post may be, that a locked thread takes no replies, that a full thread takes no
 * more. ClientHandler does the who-is-asking part (logged in, not muted, flood limit,
 * moderator/admin for delete and lock) and then calls this; nothing here trusts what a
 * client claims about content.
 *
 * Boards are the "general" board plus one per game (Vertex's own game ids), fixed at
 * startup - a client can't invent a board. Titles are forced onto one line; bodies keep
 * their line breaks but lose other control characters. Too-long text is rejected with a
 * clear message rather than silently cut, so nobody posts something they didn't see.
 */
public class ForumService
{
    public static final String GENERAL_BOARD = "general";
    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_BODY_LENGTH = 2000;
    public static final int MAX_POSTS_PER_THREAD = 500;
    public static final int MAX_THREADS_LISTED = 100;

    /** Outcome of a change: ok, an error message for the person when not, and the new thread/post id when relevant. */
    public static final class Result
    {
        public final boolean ok;
        public final String message;
        public final String id;

        private Result(boolean ok, String message, String id)
        {
            this.ok = ok;
            this.message = message;
            this.id = id;
        }

        static Result ok(String id) { return new Result(true, null, id); }
        static Result fail(String message) { return new Result(false, message, null); }
    }

    private final ForumStore store;
    private final Set<String> boards = new HashSet<String>();

    public ForumService(ForumStore store, List<String> gameIds)
    {
        this.store = store;
        boards.add(GENERAL_BOARD);
        boards.addAll(gameIds);
    }

    public boolean isValidBoard(String boardId)
    {
        return boardId != null && boards.contains(boardId);
    }

    /** Null if the board doesn't exist. */
    public List<ForumThread> listThreads(String boardId)
    {
        if (!isValidBoard(boardId))
        {
            return null;
        }
        return store.listThreads(boardId, MAX_THREADS_LISTED);
    }

    /** Null if the thread doesn't exist (or was deleted). */
    public ForumThread getThread(String threadId)
    {
        return threadId == null ? null : store.getThread(threadId);
    }

    public Result newThread(String username, String boardId, String rawTitle, String rawBody)
    {
        if (!isValidBoard(boardId))
        {
            return Result.fail("That board doesn't exist.");
        }
        String title = cleanTitle(rawTitle);
        if (title.isEmpty())
        {
            return Result.fail("Give your thread a title.");
        }
        if (title.length() > MAX_TITLE_LENGTH)
        {
            return Result.fail("That title is too long (" + MAX_TITLE_LENGTH + " characters at most).");
        }
        String bodyProblem = checkBody(cleanBody(rawBody));
        if (bodyProblem != null)
        {
            return Result.fail(bodyProblem);
        }
        ForumThread thread = store.createThread(boardId, title, username, cleanBody(rawBody));
        return Result.ok(thread.getId());
    }

    public Result reply(String username, String threadId, String rawBody)
    {
        ForumThread thread = getThread(threadId);
        if (thread == null)
        {
            return Result.fail("That thread no longer exists.");
        }
        if (thread.isLocked())
        {
            return Result.fail("This thread is locked.");
        }
        if (thread.getPosts().size() >= MAX_POSTS_PER_THREAD)
        {
            return Result.fail("This thread is full.");
        }
        String body = cleanBody(rawBody);
        String bodyProblem = checkBody(body);
        if (bodyProblem != null)
        {
            return Result.fail(bodyProblem);
        }
        ForumPost post = store.addReply(threadId, username, body);
        if (post == null)
        {
            return Result.fail("That thread no longer exists.");
        }
        return Result.ok(post.getId());
    }

    /** Deletes a reply, or - if postId is null/empty or is the opening post - the whole thread. The caller has already checked the requester is a moderator/admin. */
    public Result delete(String threadId, String postId)
    {
        ForumThread thread = getThread(threadId);
        if (thread == null)
        {
            return Result.fail("That thread no longer exists.");
        }
        boolean wholeThread = postId == null || postId.isEmpty() || thread.getPosts().get(0).getId().equals(postId);
        boolean removed = wholeThread ? store.deleteThread(threadId) : store.deletePost(threadId, postId);
        return removed ? Result.ok(threadId) : Result.fail("That post no longer exists.");
    }

    public Result setLocked(String threadId, boolean locked)
    {
        return store.setLocked(threadId, locked) ? Result.ok(threadId) : Result.fail("That thread no longer exists.");
    }

    /** Null if fine, else what to tell the person. */
    private static String checkBody(String body)
    {
        if (body.isEmpty())
        {
            return "Write something first.";
        }
        if (body.length() > MAX_BODY_LENGTH)
        {
            return "That post is too long (" + MAX_BODY_LENGTH + " characters at most).";
        }
        return null;
    }

    static String cleanTitle(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean lastWasSpace = false;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            boolean space = Character.isISOControl(c) || Character.isWhitespace(c);
            if (space)
            {
                if (!lastWasSpace) out.append(' ');
                lastWasSpace = true;
            }
            else
            {
                out.append(c);
                lastWasSpace = false;
            }
        }
        return out.toString().trim();
    }

    static String cleanBody(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\n') out.append(c);
            else if (c == '\t') out.append(' ');
            else if (!Character.isISOControl(c)) out.append(c);
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
}
