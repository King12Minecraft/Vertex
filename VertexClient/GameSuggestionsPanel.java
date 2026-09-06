import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * GameSuggestionsPanel
 * --------------------
 * Replaces the old "upload your own game" feature entirely - instead
 * of running arbitrary user-uploaded code (see the removed
 * CustomGameStore's own javadoc on the trust model that required),
 * players just pitch an idea in plain text. Every suggestion is
 * visible to everyone as a shared wishlist, so people can see what's
 * already been suggested before posting a duplicate.
 */
public class GameSuggestionsPanel extends RoundedPanel
{
    private JPanel listPanel;
    private ThemedTextField inputField;

    public GameSuggestionsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);

        refreshInBackground();
    }

    private JPanel createHeader()
    {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        wrap.add(new PageHeader("SUGGEST A GAME"));

        JLabel subtitle = new JLabel("Got an idea for a game you'd like to see added? Post it below - "
            + "everyone can see the list so it's easy to check what's already been suggested.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setBorder(new EmptyBorder(4, 0, 16, 0));
        wrap.add(subtitle);

        return wrap;
    }

    private JPanel createBody()
    {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);

        body.add(createInputRow(), BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        body.add(scroll, BorderLayout.CENTER);

        return body;
    }

    private JPanel createInputRow()
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout(10, 0));
        row.setBorder(new EmptyBorder(12, 12, 12, 12));
        row.setPreferredSize(new Dimension(10, 64));

        inputField = new ThemedTextField("e.g. \"A Connect Four game\" or \"Something like Pictionary, multiplayer\"");
        row.add(inputField, BorderLayout.CENTER);

        final ThemedButton submit = new ThemedButton("Suggest", true);
        submit.setPreferredSize(new Dimension(110, 38));
        submit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { submitSuggestion(); }
        });
        row.add(submit, BorderLayout.EAST);

        return row;
    }

    private void submitSuggestion()
    {
        final String text = inputField.getValue();
        if (text.isEmpty())
        {
            return;
        }

        if (!Session.isLoggedIn())
        {
            GameHubDialog.show(this, "Suggest a Game", "Log in to post a suggestion.");
            return;
        }

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_SUGGESTION_SUBMIT_REQUEST);
                request.setGameSuggestionText(text);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null || !response.isSuccess())
                        {
                            String reason = response != null && response.getErrorText() != null
                                ? response.getErrorText() : "Could not reach the server.";
                            GameHubDialog.show(GameSuggestionsPanel.this, "Suggest a Game", reason);
                            return;
                        }
                        inputField.clear();
                        refreshInBackground();
                    }
                });
            }
        });
        worker.start();
    }

    private void refreshInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_SUGGESTION_LIST_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { renderList(response); }
                });
            }
        });
        worker.start();
    }

    private void renderList(Message response)
    {
        listPanel.removeAll();

        if (response == null || !response.isSuccess() || response.getGameSuggestionEntries() == null
            || response.getGameSuggestionEntries().isEmpty())
        {
            JLabel empty = new JLabel(response == null
                ? "Could not reach the server."
                : "No suggestions yet - be the first to post one!");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
        }
        else
        {
            List<String> entries = response.getGameSuggestionEntries();
            for (int i = 0; i < entries.size(); i++)
            {
                listPanel.add(buildEntryRow(entries.get(i)));
                listPanel.add(javax.swing.Box.createVerticalStrut(8));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private RoundedPanel buildEntryRow(String formattedEntry)
    {
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 56));

        JLabel label = new JLabel("<html><body style='width:600px'>" + escapeHtml(formattedEntry) + "</body></html>");
        label.setFont(UITheme.FONT_BODY);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        row.add(label, BorderLayout.CENTER);

        return row;
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
