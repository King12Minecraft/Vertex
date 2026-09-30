package forum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ForumStore
 * -----------
 * The forum's threads and posts, kept in memory and persisted as a plain text file
 * (gamehub_forums.txt) - the same "genuinely human-readable flat file" choice as
 * FeedbackManager/GameSuggestionStore/AdminLog, sized for a group of friends rather
 * than a public site: every change rewrites the whole file (written to a temp file
 * and moved into place, so a crash mid-write never leaves a half-written store).
 *
 * File format: one record per line, tab-separated, every field escaped by ForumCodec
 * so no field can ever contain a raw tab or line break - a post body can't forge a
 * second record (the newline-forgery bug this project already fixed once in
 * GameSuggestionStore). Records:
 *   T  id  boardId  title  author  createdMs  lastActivityMs  locked(0/1)
 *   P  id  threadId author createdMs body
 * Unparseable lines are skipped on load rather than failing the whole forum.
 *
 * Only structure lives here (ids, ordering, persistence); what's allowed - lengths,
 * valid boards, locked threads - is ForumService's job. Everything returned is a copy.
 */
public class ForumStore
{
    public static final String DEFAULT_FILE = "gamehub_forums.txt";

    private final Path file;
    private final Map<String, ForumThread> threads = new LinkedHashMap<String, ForumThread>();
    private long nextId = 1;
    private long lastStamp = 0;

    public ForumStore()
    {
        this(DEFAULT_FILE);
    }

    public ForumStore(String fileName)
    {
        this.file = Paths.get(fileName);
        load();
    }

    /** Strictly increasing timestamps, so "newest activity first" never ties even when two things happen in the same millisecond. */
    private long stamp()
    {
        long now = Math.max(System.currentTimeMillis(), lastStamp + 1);
        lastStamp = now;
        return now;
    }

    public synchronized ForumThread createThread(String boardId, String title, String author, String body)
    {
        long now = stamp();
        String threadId = "t" + (nextId++);
        ForumThread thread = new ForumThread(threadId, boardId, title, author, now, now, false);
        thread.getPosts().add(new ForumPost("p" + (nextId++), author, now, body));
        threads.put(threadId, thread);
        save();
        return thread.copy();
    }

    /** Null if the thread doesn't exist. Doesn't check the thread is unlocked - that's ForumService's rule. */
    public synchronized ForumPost addReply(String threadId, String author, String body)
    {
        ForumThread thread = threads.get(threadId);
        if (thread == null)
        {
            return null;
        }
        long now = stamp();
        ForumPost post = new ForumPost("p" + (nextId++), author, now, body);
        thread.getPosts().add(post);
        thread.setLastActivityMs(now);
        save();
        return post;
    }

    public synchronized ForumThread getThread(String threadId)
    {
        ForumThread thread = threads.get(threadId);
        return thread == null ? null : thread.copy();
    }

    /** One board's threads, most recently active first, at most limit of them. */
    public synchronized List<ForumThread> listThreads(String boardId, int limit)
    {
        List<ForumThread> matching = new ArrayList<ForumThread>();
        for (ForumThread thread : threads.values())
        {
            if (thread.getBoardId().equals(boardId))
            {
                matching.add(thread);
            }
        }
        Collections.sort(matching, new Comparator<ForumThread>()
        {
            public int compare(ForumThread a, ForumThread b)
            {
                return Long.compare(b.getLastActivityMs(), a.getLastActivityMs());
            }
        });
        List<ForumThread> result = new ArrayList<ForumThread>();
        for (int i = 0; i < matching.size() && i < limit; i++)
        {
            result.add(matching.get(i).copy());
        }
        return result;
    }

    public synchronized boolean setLocked(String threadId, boolean locked)
    {
        ForumThread thread = threads.get(threadId);
        if (thread == null)
        {
            return false;
        }
        thread.setLocked(locked);
        save();
        return true;
    }

    public synchronized boolean deleteThread(String threadId)
    {
        if (threads.remove(threadId) == null)
        {
            return false;
        }
        save();
        return true;
    }

    /** Removes one reply. False if the thread or post doesn't exist, or the post is the thread's opening post (deleting that means deleting the thread). */
    public synchronized boolean deletePost(String threadId, String postId)
    {
        ForumThread thread = threads.get(threadId);
        if (thread == null)
        {
            return false;
        }
        List<ForumPost> posts = thread.getPosts();
        for (int i = 1; i < posts.size(); i++)
        {
            if (posts.get(i).getId().equals(postId))
            {
                posts.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    private void save()
    {
        List<String> lines = new ArrayList<String>();
        for (ForumThread thread : threads.values())
        {
            lines.add(ForumCodec.join("T", thread.getId(), thread.getBoardId(), thread.getTitle(), thread.getAuthor(),
                String.valueOf(thread.getCreatedMs()), String.valueOf(thread.getLastActivityMs()), thread.isLocked() ? "1" : "0"));
            for (ForumPost post : thread.getPosts())
            {
                lines.add(ForumCodec.join("P", post.getId(), thread.getId(), post.getAuthor(),
                    String.valueOf(post.getCreatedMs()), post.getBody()));
            }
        }
        try
        {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            System.err.println("Could not save the forum: " + e.getMessage());
        }
    }

    private void load()
    {
        if (!Files.exists(file))
        {
            return;
        }
        List<String> lines;
        try
        {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            System.err.println("Could not load the forum: " + e.getMessage());
            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            if (lines.get(i).isEmpty())
            {
                continue;
            }
            List<String> f = ForumCodec.split(lines.get(i));
            try
            {
                if ("T".equals(f.get(0)) && f.size() == 8)
                {
                    ForumThread thread = new ForumThread(f.get(1), f.get(2), f.get(3), f.get(4),
                        Long.parseLong(f.get(5)), Long.parseLong(f.get(6)), "1".equals(f.get(7)));
                    threads.put(thread.getId(), thread);
                    noteId(thread.getId());
                    lastStamp = Math.max(lastStamp, Math.max(thread.getCreatedMs(), thread.getLastActivityMs()));
                }
                else if ("P".equals(f.get(0)) && f.size() == 6)
                {
                    ForumThread thread = threads.get(f.get(2));
                    if (thread != null)
                    {
                        ForumPost post = new ForumPost(f.get(1), f.get(3), Long.parseLong(f.get(4)), f.get(5));
                        thread.getPosts().add(post);
                        noteId(post.getId());
                        lastStamp = Math.max(lastStamp, post.getCreatedMs());
                    }
                }
            }
            catch (NumberFormatException e)
            {
                System.err.println("Skipping unreadable forum record on line " + (i + 1));
            }
        }

        // A thread with no opening post can only come from a damaged file - drop it rather than serve a thread nobody can read.
        List<String> empty = new ArrayList<String>();
        for (ForumThread thread : threads.values())
        {
            if (thread.getPosts().isEmpty())
            {
                empty.add(thread.getId());
            }
        }
        for (int i = 0; i < empty.size(); i++)
        {
            threads.remove(empty.get(i));
        }
    }

    /** Keeps nextId above every id already in the file ("t12"/"p13" -> 14). */
    private void noteId(String id)
    {
        try
        {
            nextId = Math.max(nextId, Long.parseLong(id.substring(1)) + 1);
        }
        catch (RuntimeException e)
        {
            // An id that isn't letter+number just doesn't move the counter.
        }
    }
}
