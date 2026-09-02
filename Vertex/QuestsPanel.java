import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

/**
 * QuestsPanel
 * -----------
 * A dedicated home for the challenge system (player-facing name:
 * "Quests") - previously tucked inside the Shop page, now its own main
 * tab since it's a big enough part of the game loop to deserve one.
 * Same server data (CHALLENGES_REQUEST/CHALLENGE_UPDATE), just its own
 * page. See Sidebar for the always-visible compact in-progress list.
 */
public class QuestsPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private JPanel questList;

    public QuestsPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        PageHeader header = new PageHeader("QUESTS");
        add(header, BorderLayout.NORTH);

        questList = new JPanel();
        questList.setOpaque(false);
        questList.setLayout(new BoxLayout(questList, BoxLayout.Y_AXIS));
        questList.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(questList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        NetworkManager.addPushListener(this);
        loadQuests();
    }

    private void loadQuests()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.CHALLENGES_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            renderQuests(response.getChallenges());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderQuests(List<ChallengeProgressInfo> quests)
    {
        questList.removeAll();
        if (quests == null || quests.isEmpty())
        {
            JLabel empty = new JLabel("No quests available.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            questList.add(empty);
        }
        else
        {
            for (int i = 0; i < quests.size(); i++)
            {
                questList.add(new QuestRow(quests.get(i), false));
                if (i < quests.size() - 1)
                {
                    questList.add(Box.createVerticalStrut(10));
                }
            }
        }
        questList.revalidate();
        questList.repaint();
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.CHALLENGE_UPDATE)
        {
            return;
        }
        if (message.getChallenges() == null || message.getChallenges().isEmpty())
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { loadQuests(); }
        });
    }
}
