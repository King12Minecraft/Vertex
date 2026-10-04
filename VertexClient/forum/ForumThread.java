package forum;

import java.util.ArrayList;
import java.util.List;

/**
 * A forum thread: a title on one board plus its posts (index 0 is the opening post,
 * the rest are replies in order). ForumStore owns the live instances and mutates them
 * under its own lock; everything it hands out is a copy (see copy()), so callers can
 * read one freely without synchronizing.
 */
public final class ForumThread
{
    private final String id;
    private final String boardId;
    private final String title;
    private final String author;
    private final long createdMs;
    private long lastActivityMs;
    private boolean locked;
    private final List<ForumPost> posts = new ArrayList<ForumPost>();

    public ForumThread(String id, String boardId, String title, String author, long createdMs, long lastActivityMs, boolean locked)
    {
        this.id = id;
        this.boardId = boardId;
        this.title = title;
        this.author = author;
        this.createdMs = createdMs;
        this.lastActivityMs = lastActivityMs;
        this.locked = locked;
    }

    public String getId() { return id; }
    public String getBoardId() { return boardId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public long getCreatedMs() { return createdMs; }
    public long getLastActivityMs() { return lastActivityMs; }
    public boolean isLocked() { return locked; }
    public List<ForumPost> getPosts() { return posts; }

    /** Replies only - the opening post doesn't count. */
    public int getReplyCount() { return Math.max(0, posts.size() - 1); }

    void setLastActivityMs(long lastActivityMs) { this.lastActivityMs = lastActivityMs; }
    void setLocked(boolean locked) { this.locked = locked; }

    ForumThread copy()
    {
        ForumThread copy = new ForumThread(id, boardId, title, author, createdMs, lastActivityMs, locked);
        copy.posts.addAll(posts);
        return copy;
    }
}
