package pages;
import net.NetworkManager;
import net.MessageType;
import ui.GameHubDialog;
import account.Session;
import ui.ThemedButton;
import ui.ThemedScrollBarUI;
import theme.ThemeManager;
import theme.UITheme;
import ui.PageHeader;
import theme.ThemeColor;
import net.Message;
import ui.ThemedTextField;
import ui.RoundedPanel;
import ui.ThemedLabel;

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
public class GameSuggestionsPanel extends PageScaffold
{
    private JPanel listPanel;
    private ThemedTextField inputField;

    public GameSuggestionsPanel()
    {
        super("SUGGEST A GAME", "Got an idea for a game? Post it - everyone sees the list, so check what's been suggested first.");

        row(PageScaffold.fullWidth(new SectionCard("YOUR IDEA").withGlow().content(createInputRow())));
        gap(16);

        listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        row(PageScaffold.fullWidth(new SectionCard("SUGGESTIONS FROM EVERYONE").content(listPanel)));

        refreshInBackground();
    }

    private JPanel createInputRow()
    {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);

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
            JLabel empty = new ThemedLabel(response == null
                ? "Could not reach the server."
                : "No suggestions yet - be the first to post one!", ThemeColor.TEXT_MUTED);
            empty.setFont(UITheme.FONT_BODY);
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
        RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        row.setLayout(new BorderLayout());
        row.setBorder(new EmptyBorder(10, 14, 10, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(2000, 64));

        JLabel label = new ThemedLabel("<html><table width='600' cellpadding='0' cellspacing='0'><tr><td>" + escapeHtml(formattedEntry) + "</td></tr></table></html>", ThemeColor.TEXT_PRIMARY);
        label.setFont(UITheme.FONT_BODY);
        row.add(label, BorderLayout.CENTER);

        return row;
    }

    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
