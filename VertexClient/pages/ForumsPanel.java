package pages;

import account.PermissionManager;
import account.Session;
import forum.ForumCodec;
import games.GameInfo;
import games.GameRegistry;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.UITheme;
import ui.GameHubDialog;
import ui.PageHeader;
import ui.PlaceholderPanel;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.ThemedScrollBarUI;
import ui.ThemedTextArea;
import ui.ThemedTextField;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ForumsPanel
 * ------------
 * The Forums sidebar page: a board list on the left (General, then one board per
 * game), and on the right either that board's threads or one thread with its replies.
 * Client-only - everything it shows and every change it asks for goes through the
 * server's forum package (ForumService/ClientHandler), which decides what's allowed;
 * this page only hides buttons that would be refused (posting while logged out, the
 * moderator buttons for everyone else) and shows whatever error the server returns.
 *
 * Reading works for anyone connected; posting needs a login. Moderators/admins get
 * Delete and Lock/Unlock. Data is re-fetched whenever the page is shown, so it isn't
 * stale after time away.
 */
public class ForumsPanel extends RoundedPanel
{
    private static final String GENERAL = "general";
    private static final String CARD_LIST = "LIST";
    private static final String CARD_THREAD = "THREAD";
    private static final int BODY_WIDTH = 560;

    private interface ResponseHandler
    {
        void handle(Message response);
    }

    private final List<String[]> boards = new ArrayList<String[]>();   // {id, display name}
    private final JPanel boardColumn = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);

    // Thread list card
    private final JLabel boardTitle = new ThemedLabel("General", UITheme.FONT_NAV_BOLD, ThemeColor.TEXT_PRIMARY);
    private final JPanel threadRows = new JPanel();
    private final JPanel composer = new JPanel(new BorderLayout());
    private ThemedTextField newTitle;
    private ThemedTextArea newBody;
    private final JLabel composerError = new ThemedLabel(" ", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);

    // Thread card
    private final JLabel threadTitle = new ThemedLabel("", UITheme.FONT_NAV_BOLD, ThemeColor.TEXT_PRIMARY);
    private final JPanel postRows = new JPanel();
    private final JPanel modButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JPanel replyBox = new JPanel(new BorderLayout(0, 8));
    private ThemedTextArea replyArea;
    private final JLabel replyNote = new ThemedLabel(" ", UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
    private ThemedButton lockButton;

    private String currentBoard = GENERAL;
    private String currentThreadId;
    private boolean currentThreadLocked;

    public ForumsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        buildBoardList();

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(new PageHeader("FORUMS"));
        JLabel subtitle = new ThemedLabel("Talk about Vertex and its games - one board per game, plus General.", ThemeColor.TEXT_SECONDARY);
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setBorder(new EmptyBorder(4, 0, 16, 0));
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        JScrollPane boardScroll = new JScrollPane(boardColumn);
        boardScroll.setBorder(BorderFactory.createEmptyBorder());
        boardScroll.setOpaque(false);
        boardScroll.getViewport().setOpaque(false);
        boardScroll.getVerticalScrollBar().setUnitIncrement(16);
        boardScroll.setPreferredSize(new Dimension(236, 100));
        ThemedScrollBarUI.apply(boardScroll);
        body.add(boardScroll, BorderLayout.WEST);

        cardHost.setOpaque(false);
        cardHost.add(buildListCard(), CARD_LIST);
        cardHost.add(buildThreadCard(), CARD_THREAD);
        body.add(cardHost, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        addHierarchyListener(new HierarchyListener()
        {
            public void hierarchyChanged(HierarchyEvent e)
            {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
                {
                    refreshCurrentView();
                }
            }
        });
    }

    // ==================== Boards ====================

    private void buildBoardList()
    {
        boards.add(new String[] { GENERAL, "General" });
        List<GameInfo> games = new ArrayList<GameInfo>(new GameRegistry().getAllGames());
        Collections.sort(games, new Comparator<GameInfo>()
        {
            public int compare(GameInfo a, GameInfo b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        for (int i = 0; i < games.size(); i++)
        {
            boards.add(new String[] { games.get(i).getGameId(), games.get(i).getName() });
        }

        boardColumn.setOpaque(false);
        boardColumn.setLayout(new BoxLayout(boardColumn, BoxLayout.Y_AXIS));
        rebuildBoardButtons();
    }

    private void rebuildBoardButtons()
    {
        boardColumn.removeAll();
        for (int i = 0; i < boards.size(); i++)
        {
            final String id = boards.get(i)[0];
            ThemedButton button = new ThemedButton(boards.get(i)[1], id.equals(currentBoard));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setPreferredSize(new Dimension(212, 34));
            button.setMaximumSize(new Dimension(212, 34));
            button.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { selectBoard(id); }
            });
            boardColumn.add(button);
            boardColumn.add(Box.createVerticalStrut(6));
        }
        boardColumn.revalidate();
        boardColumn.repaint();
    }

    private String boardName(String id)
    {
        for (int i = 0; i < boards.size(); i++)
        {
            if (boards.get(i)[0].equals(id)) return boards.get(i)[1];
        }
        return id;
    }

    private void selectBoard(String id)
    {
        currentBoard = id;
        currentThreadId = null;
        hideComposer();
        rebuildBoardButtons();
        cards.show(cardHost, CARD_LIST);
        loadThreads();
    }

    // ==================== Thread list ====================

    private JPanel buildListCard()
    {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(boardTitle, BorderLayout.CENTER);
        ThemedButton newThread = new ThemedButton("New thread", true);
        newThread.setPreferredSize(new Dimension(130, 34));
        newThread.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { toggleComposer(); }
        });
        titleRow.add(newThread, BorderLayout.EAST);
        top.add(titleRow, BorderLayout.NORTH);

        composer.setOpaque(false);
        composer.setVisible(false);
        top.add(composer, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        threadRows.setOpaque(false);
        threadRows.setLayout(new BoxLayout(threadRows, BoxLayout.Y_AXIS));
        JPanel rowsWrapper = new JPanel(new BorderLayout());
        rowsWrapper.setOpaque(false);
        rowsWrapper.add(threadRows, BorderLayout.NORTH);
        card.add(scroll(rowsWrapper), BorderLayout.CENTER);
        return card;
    }

    private void toggleComposer()
    {
        if (!Session.isLoggedIn())
        {
            GameHubDialog.show(this, "Forums", "Log in to start a thread.");
            return;
        }
        if (composer.isVisible())
        {
            hideComposer();
            return;
        }
        composer.removeAll();
        RoundedPanel box = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(12, 12, 12, 12));

        newTitle = new ThemedTextField("Thread title");
        newTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(newTitle);
        box.add(Box.createVerticalStrut(8));
        newBody = new ThemedTextArea("What do you want to talk about?", 5);
        newBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(newBody);
        box.add(Box.createVerticalStrut(6));
        composerError.setText(" ");
        composerError.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(composerError);
        box.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setPreferredSize(new Dimension(90, 34));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { hideComposer(); }
        });
        ThemedButton post = new ThemedButton("Post", true);
        post.setPreferredSize(new Dimension(90, 34));
        post.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitNewThread(); }
        });
        buttons.add(cancel);
        buttons.add(post);
        box.add(buttons);

        composer.add(box, BorderLayout.CENTER);
        composer.setVisible(true);
        composer.revalidate();
        composer.repaint();
    }

    private void hideComposer()
    {
        composer.setVisible(false);
        composer.removeAll();
        composer.revalidate();
    }

    private void submitNewThread()
    {
        Message request = new Message();
        request.setType(MessageType.FORUM_NEW_THREAD_REQUEST);
        request.setForumBoardId(currentBoard);
        request.setForumTitle(newTitle.getValue());
        request.setChatText(newBody.getValue());
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (response == null || !response.isSuccess())
                {
                    composerError.setText(errorOf(response));
                    return;
                }
                hideComposer();
                openThread(response.getForumThreadId());
            }
        });
    }

    private void loadThreads()
    {
        final String board = currentBoard;
        boardTitle.setText(boardName(board));
        Message request = new Message();
        request.setType(MessageType.FORUM_THREAD_LIST_REQUEST);
        request.setForumBoardId(board);
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (board.equals(currentBoard))
                {
                    renderThreadList(response);
                }
            }
        });
    }

    private void renderThreadList(Message response)
    {
        threadRows.removeAll();
        if (response == null || !response.isSuccess())
        {
            PlaceholderPanel.show(threadRows, errorOf(response));
            return;
        }
        List<String> entries = response.getForumEntries();
        if (entries == null || entries.isEmpty())
        {
            PlaceholderPanel.show(threadRows, "No threads here yet - start the first one!");
            return;
        }
        for (int i = 0; i < entries.size(); i++)
        {
            List<String> f = ForumCodec.split(entries.get(i));
            if (f.size() != 7) continue;
            threadRows.add(buildThreadRow(f));
            threadRows.add(Box.createVerticalStrut(8));
        }
        threadRows.revalidate();
        threadRows.repaint();
    }

    /** f: id, board, title, author, replyCount, lastActivityMs, locked. */
    private JPanel buildThreadRow(List<String> f)
    {
        final String threadId = f.get(0);
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout(0, 4));
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 70));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        boolean locked = "1".equals(f.get(6));
        JLabel title = new ThemedLabel((locked ? "[locked] " : "") + f.get(2), UITheme.FONT_NAV_BOLD, ThemeColor.TEXT_PRIMARY);
        int replies = parseInt(f.get(4));
        JLabel meta = new ThemedLabel("by " + f.get(3) + "  -  " + replies + (replies == 1 ? " reply" : " replies")
            + "  -  " + timeAgo(parseLong(f.get(5))), UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED);
        row.add(title, BorderLayout.NORTH);
        row.add(meta, BorderLayout.CENTER);
        row.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { openThread(threadId); }
        });
        return row;
    }

    // ==================== One thread ====================

    private JPanel buildThreadCard()
    {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        ThemedButton back = new ThemedButton("Back", false);
        back.setPreferredSize(new Dimension(90, 34));
        back.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                currentThreadId = null;
                cards.show(cardHost, CARD_LIST);
                loadThreads();
            }
        });
        top.add(back, BorderLayout.WEST);
        top.add(threadTitle, BorderLayout.CENTER);

        modButtons.setOpaque(false);
        lockButton = new ThemedButton("Lock", false);
        lockButton.setPreferredSize(new Dimension(100, 34));
        lockButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { toggleLock(); }
        });
        ThemedButton deleteThread = new ThemedButton("Delete thread", false);
        deleteThread.setPreferredSize(new Dimension(160, 34));
        deleteThread.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { deletePost(null, true); }
        });
        modButtons.add(lockButton);
        modButtons.add(deleteThread);
        top.add(modButtons, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        postRows.setOpaque(false);
        postRows.setLayout(new BoxLayout(postRows, BoxLayout.Y_AXIS));
        JPanel rowsWrapper = new JPanel(new BorderLayout());
        rowsWrapper.setOpaque(false);
        rowsWrapper.add(postRows, BorderLayout.NORTH);
        card.add(scroll(rowsWrapper), BorderLayout.CENTER);

        replyBox.setOpaque(false);
        card.add(replyBox, BorderLayout.SOUTH);
        return card;
    }

    private void openThread(String threadId)
    {
        currentThreadId = threadId;
        cards.show(cardHost, CARD_THREAD);
        loadThread();
    }

    private void loadThread()
    {
        final String threadId = currentThreadId;
        if (threadId == null) return;
        Message request = new Message();
        request.setType(MessageType.FORUM_THREAD_VIEW_REQUEST);
        request.setForumThreadId(threadId);
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (threadId.equals(currentThreadId))
                {
                    renderThread(response);
                }
            }
        });
    }

    private void renderThread(Message response)
    {
        postRows.removeAll();
        replyBox.removeAll();
        modButtons.setVisible(false);
        if (response == null || !response.isSuccess())
        {
            threadTitle.setText("");
            PlaceholderPanel.show(postRows, errorOf(response));
            return;
        }

        currentThreadLocked = response.isForumLocked();
        threadTitle.setText((currentThreadLocked ? "[locked] " : "") + response.getForumTitle());
        boolean moderator = PermissionManager.isAtLeastModerator(Session.getCurrentAccount());
        modButtons.setVisible(moderator);
        lockButton.setText(currentThreadLocked ? "Unlock" : "Lock");

        List<String> entries = response.getForumEntries();
        for (int i = 0; entries != null && i < entries.size(); i++)
        {
            List<String> f = ForumCodec.split(entries.get(i));
            if (f.size() != 4) continue;
            postRows.add(buildPostRow(f, i > 0 && moderator));
            postRows.add(Box.createVerticalStrut(8));
        }

        if (currentThreadLocked)
        {
            replyNote.setText("This thread is locked - no new replies.");
            replyBox.add(replyNote, BorderLayout.CENTER);
        }
        else if (!Session.isLoggedIn())
        {
            replyNote.setText("Log in to reply.");
            replyBox.add(replyNote, BorderLayout.CENTER);
        }
        else
        {
            replyArea = new ThemedTextArea("Write a reply...", 3);
            replyBox.add(replyArea, BorderLayout.CENTER);
            ThemedButton send = new ThemedButton("Reply", true);
            send.setPreferredSize(new Dimension(90, 34));
            send.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { submitReply(); }
            });
            JPanel sendRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            sendRow.setOpaque(false);
            sendRow.add(send);
            replyBox.add(sendRow, BorderLayout.SOUTH);
        }
        postRows.revalidate();
        postRows.repaint();
        replyBox.revalidate();
        replyBox.repaint();
    }

    /** f: id, author, createdMs, body. */
    private JPanel buildPostRow(List<String> f, boolean deletable)
    {
        final String postId = f.get(0);
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout(0, 6));
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(new ThemedLabel(f.get(1) + "  -  " + timeAgo(parseLong(f.get(2))), UITheme.FONT_SMALL, ThemeColor.TEXT_MUTED), BorderLayout.CENTER);
        if (deletable)
        {
            ThemedButton delete = new ThemedButton("Delete", false);
            delete.setPreferredSize(new Dimension(100, 26));
            delete.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { deletePost(postId, false); }
            });
            head.add(delete, BorderLayout.EAST);
        }
        row.add(head, BorderLayout.NORTH);

        // A table width, not "<body style='width:..'>" - the CSS form is ignored on newer JDKs (see GameDetailPanel).
        JLabel body = new ThemedLabel("<html><table width='" + BODY_WIDTH + "' cellpadding='0' cellspacing='0'><tr><td>"
            + escapeHtml(f.get(3)).replace("\n", "<br>") + "</td></tr></table></html>", UITheme.FONT_BODY, ThemeColor.TEXT_PRIMARY);
        row.add(body, BorderLayout.CENTER);
        return row;
    }

    private void submitReply()
    {
        Message request = new Message();
        request.setType(MessageType.FORUM_REPLY_REQUEST);
        request.setForumThreadId(currentThreadId);
        request.setChatText(replyArea.getValue());
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (response == null || !response.isSuccess())
                {
                    GameHubDialog.show(ForumsPanel.this, "Forums", errorOf(response));
                    return;
                }
                loadThread();
            }
        });
    }

    private void toggleLock()
    {
        Message request = new Message();
        request.setType(MessageType.FORUM_LOCK_REQUEST);
        request.setForumThreadId(currentThreadId);
        request.setForumLocked(!currentThreadLocked);
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (response == null || !response.isSuccess())
                {
                    GameHubDialog.show(ForumsPanel.this, "Forums", errorOf(response));
                    return;
                }
                loadThread();
            }
        });
    }

    private void deletePost(String postId, final boolean wholeThread)
    {
        Message request = new Message();
        request.setType(MessageType.FORUM_DELETE_REQUEST);
        request.setForumThreadId(currentThreadId);
        request.setForumPostId(postId);
        send(request, new ResponseHandler()
        {
            public void handle(Message response)
            {
                if (response == null || !response.isSuccess())
                {
                    GameHubDialog.show(ForumsPanel.this, "Forums", errorOf(response));
                    return;
                }
                if (wholeThread)
                {
                    currentThreadId = null;
                    cards.show(cardHost, CARD_LIST);
                    loadThreads();
                }
                else
                {
                    loadThread();
                }
            }
        });
    }

    // ==================== Shared ====================

    private void refreshCurrentView()
    {
        if (currentThreadId != null) loadThread();
        else loadThreads();
    }

    /** Runs the request off the Swing thread and hands the response (null if the server couldn't be reached) back on it. */
    private void send(final Message request, final ResponseHandler handler)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { handler.handle(response); }
                });
            }
        });
        worker.start();
    }

    private static String errorOf(Message response)
    {
        if (response == null) return "Could not reach the server.";
        return response.getErrorText() != null ? response.getErrorText() : "Something went wrong.";
    }

    private static JScrollPane scroll(JPanel content)
    {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        return scroll;
    }

    private static String timeAgo(long epochMs)
    {
        long d = System.currentTimeMillis() - epochMs;
        if (d < 60000) return "just now";
        if (d < 3600000) return (d / 60000) + "m ago";
        if (d < 86400000) return (d / 3600000) + "h ago";
        return (d / 86400000) + "d ago";
    }

    private static int parseInt(String s)
    {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseLong(String s)
    {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
