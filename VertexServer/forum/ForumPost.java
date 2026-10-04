package forum;

/** One post in a forum thread - the thread's opening post or a reply. Immutable. */
public final class ForumPost
{
    private final String id;
    private final String author;
    private final long createdMs;
    private final String body;

    public ForumPost(String id, String author, long createdMs, String body)
    {
        this.id = id;
        this.author = author;
        this.createdMs = createdMs;
        this.body = body;
    }

    public String getId() { return id; }
    public String getAuthor() { return author; }
    public long getCreatedMs() { return createdMs; }
    public String getBody() { return body; }
}
