import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * CustomGamesPanel
 * ----------------
 * The "Roblox-style" catalog page - every game a player has uploaded or
 * published from the in-app code editor (CodeEditorWindow), playable
 * by everyone connected to this server. See CustomGameStore's javadoc
 * (server-side) for the trust model this whole feature rests on: no
 * sandboxing, same as running any other program a friend sends you.
 *
 * Two ways to add a game here - "Upload Project" (UploadCustomGameDialog,
 * for something already compiled, e.g. exported from BlueJ) and
 * "Write Code" (CodeEditorWindow, compile-and-publish from a plain text
 * editor right inside Vertex). Both end up going through the exact same
 * CUSTOM_GAME_UPLOAD_REQUEST.
 */
public class CustomGamesPanel extends RoundedPanel
{
    private final JPanel grid;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy");

    public CustomGamesPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(createHeader(), BorderLayout.NORTH);

        grid = new JPanel(new GridLayout(0, 3, 20, 20));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.add(grid);

        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        CustomGameManager.addListener(new Runnable()
        {
            public void run() { rebuildGrid(); }
        });

        rebuildGrid();
        refreshInBackground();
    }

    private JPanel createHeader()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        wrap.add(new PageHeader("CUSTOM GAMES"));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 0, 16, 0));

        ThemedButton uploadButton = new ThemedButton("Upload Project", true);
        uploadButton.setPreferredSize(new Dimension(190, 36));
        uploadButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                UploadCustomGameDialog.show(CustomGamesPanel.this, new Runnable()
                {
                    public void run() { refreshInBackground(); }
                });
            }
        });
        row.add(uploadButton);

        ThemedButton codeButton = new ThemedButton("Write Code", false);
        codeButton.setPreferredSize(new Dimension(150, 36));
        codeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                CodeEditorWindow window = new CodeEditorWindow(new Runnable()
                {
                    public void run() { refreshInBackground(); }
                });
                window.setVisible(true);
            }
        });
        row.add(codeButton);

        ThemedButton refreshButton = new ThemedButton("Refresh", false);
        refreshButton.setPreferredSize(new Dimension(100, 36));
        refreshButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { refreshInBackground(); }
        });
        row.add(refreshButton);

        wrap.add(row);
        return wrap;
    }

    private void refreshInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run() { CustomGameManager.refresh(); }
        });
        worker.start();
    }

    private void rebuildGrid()
    {
        grid.removeAll();

        List<CustomGameInfo> games = CustomGameManager.getCachedGames();
        if (games.isEmpty())
        {
            JLabel empty = new JLabel("No custom games yet - be the first to upload one!");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            grid.add(empty);
        }
        else
        {
            for (int i = 0; i < games.size(); i++)
            {
                grid.add(buildCard(games.get(i)));
            }
        }

        grid.revalidate();
        grid.repaint();
    }

    private JPanel buildCard(final CustomGameInfo game)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(260, 200));
        card.enableTopAccent();

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(game.getName());
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel author = new JLabel("by " + game.getAuthorUsername());
        author.setFont(UITheme.FONT_SMALL);
        author.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        author.setAlignmentX(Component.LEFT_ALIGNMENT);
        author.setBorder(new EmptyBorder(3, 0, 8, 0));

        JLabel uploaded = new JLabel("Uploaded " + dateFormat.format(new Date(game.getUploadedAt())));
        uploaded.setFont(UITheme.FONT_SMALL);
        uploaded.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        uploaded.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel size = new JLabel(formatSize(game.getSizeBytes()));
        size.setFont(UITheme.FONT_SMALL);
        size.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        size.setAlignmentX(Component.LEFT_ALIGNMENT);
        size.setBorder(new EmptyBorder(2, 0, 0, 0));

        info.add(name);
        if (!game.isApproved())
        {
            JLabel pending = new JLabel("Pending Review");
            pending.setFont(UITheme.FONT_SMALL.deriveFont(11f));
            pending.setForeground(new Color(230, 170, 70));
            pending.setAlignmentX(Component.LEFT_ALIGNMENT);
            pending.setBorder(new EmptyBorder(2, 0, 0, 0));
            info.add(pending);
        }
        info.add(author);
        info.add(uploaded);
        info.add(size);
        info.add(Box.createVerticalGlue());

        card.add(info, BorderLayout.CENTER);
        card.add(buildActionRow(game), BorderLayout.SOUTH);

        return card;
    }

    private JPanel buildActionRow(final CustomGameInfo game)
    {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));

        final ThemedButton play = new ThemedButton("Play", true);
        play.setAlignmentX(Component.LEFT_ALIGNMENT);
        play.setPreferredSize(new Dimension(228, 36));
        play.setMaximumSize(new Dimension(500, 36));
        play.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { launchInBackground(play, game); }
        });
        row.add(play);

        String myUsername = Session.isLoggedIn() ? Session.getCurrentAccount().getUsername() : null;
        boolean isAdmin = Session.isLoggedIn() && PermissionManager.isAdmin(Session.getCurrentAccount());

        if (isAdmin && !game.isApproved())
        {
            row.add(Box.createVerticalStrut(6));
            ThemedButton approve = new ThemedButton("Approve", true);
            approve.setAlignmentX(Component.LEFT_ALIGNMENT);
            approve.setPreferredSize(new Dimension(228, 30));
            approve.setMaximumSize(new Dimension(500, 30));
            approve.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { approveGame(game); }
            });
            row.add(approve);
        }

        boolean canDelete = myUsername != null
            && (myUsername.equalsIgnoreCase(game.getAuthorUsername()) || PermissionManager.isAtLeastModerator(Session.getCurrentAccount()));
        if (canDelete)
        {
            row.add(Box.createVerticalStrut(6));
            ThemedButton delete = new ThemedButton("Remove", false);
            delete.setAlignmentX(Component.LEFT_ALIGNMENT);
            delete.setPreferredSize(new Dimension(228, 30));
            delete.setMaximumSize(new Dimension(500, 30));
            delete.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { confirmAndDelete(game); }
            });
            row.add(delete);
        }

        return row;
    }

    private void approveGame(final CustomGameInfo game)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CUSTOM_GAME_APPROVE_REQUEST);
                request.setGameId(game.getGameId());
                NetworkManager.send(request);
                CustomGameManager.refresh();
            }
        });
        worker.start();
    }

    private void confirmAndDelete(final CustomGameInfo game)
    {
        int choice = JOptionPane.showConfirmDialog(this,
            "Remove \"" + game.getName() + "\" for everyone?", "Remove Game", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION)
        {
            return;
        }

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CUSTOM_GAME_DELETE_REQUEST);
                request.setGameId(game.getGameId());
                NetworkManager.send(request);
                CustomGameManager.refresh();
            }
        });
        worker.start();
    }

    private void launchInBackground(final Component anchor, final CustomGameInfo game)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CUSTOM_GAME_DOWNLOAD_REQUEST);
                request.setGameId(game.getGameId());
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null || !response.isSuccess() || response.getFileData() == null)
                        {
                            String reason = response != null && response.getErrorText() != null
                                ? response.getErrorText() : "Could not reach the server.";
                            GameHubDialog.show(anchor, "Launch Error", reason);
                            return;
                        }

                        try
                        {
                            CustomGameLoader.launch(response.getFileData(), response.getCustomGameEntryClass());
                        }
                        catch (Exception ex)
                        {
                            ex.printStackTrace();
                            GameHubDialog.show(anchor, "Launch Error",
                                "Could not launch \"" + game.getName() + "\":\n\n" + ex);
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private String formatSize(long bytes)
    {
        if (bytes < 1024)
        {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024)
        {
            return (bytes / 1024) + " KB";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
