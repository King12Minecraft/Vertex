package chat;

import account.Session;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedScrollBarUI;
import ui.ThemedTextField;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * MatchChatDock
 * --------------
 * The chat panel docked beside a game while its match is live (client side of the
 * chat package - client-only, like the other *Dialog/panel classes). MainMenu creates
 * one when the server announces a room (MATCH_CHAT_STATE), feeds it MATCH_CHAT_MESSAGE
 * pushes, and drops it when the game host is cleared.
 *
 * It only mirrors the server's decisions: a LOCKED room disables the input and says
 * why, a CLOSED one says the chat ended - but the real enforcement is server-side
 * (MatchChatRoom.post), so a modified client gains nothing by re-enabling the box.
 * Can be collapsed to a thin strip so it never has to cover a game; new messages
 * while collapsed show as a count on the strip.
 */
public class MatchChatDock extends RoundedPanel
{
    private static final int OPEN_WIDTH = 270;
    private static final int COLLAPSED_WIDTH = 84;

    private final String matchId;
    private final JTextArea log;
    private final ThemedTextField input;
    private final ThemedButton send;
    private final JLabel status;
    private final JPanel body;
    private final ThemedButton toggle;
    private final JLabel title;

    private String state = "OPEN";
    private boolean collapsed = false;
    private int unread = 0;

    public MatchChatDock(String matchId)
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        this.matchId = matchId;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(OPEN_WIDTH, 100));

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        title = new JLabel("Match chat");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        header.add(title, BorderLayout.CENTER);
        toggle = new ThemedButton("Hide", false);
        toggle.setPreferredSize(new Dimension(72, 26));
        toggle.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { setCollapsed(!collapsed); }
        });
        header.add(toggle, BorderLayout.EAST);
        header.setBorder(new EmptyBorder(0, 0, 8, 0));
        add(header, BorderLayout.NORTH);

        body = new JPanel(new BorderLayout(0, 8));
        body.setOpaque(false);

        log = new JTextArea();
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(UITheme.FONT_SMALL);
        log.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        log.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        log.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        body.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        status = new JLabel(" ");
        status.setFont(UITheme.FONT_SMALL);
        status.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        bottom.add(status, BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        input = new ThemedTextField("Message...");
        input.setPreferredSize(new Dimension(100, 36));
        input.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendCurrentText(); }
        });
        inputRow.add(input, BorderLayout.CENTER);
        send = new ThemedButton("Send", true);
        send.setPreferredSize(new Dimension(82, 36));
        send.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendCurrentText(); }
        });
        inputRow.add(send, BorderLayout.EAST);
        bottom.add(inputRow, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);

        add(body, BorderLayout.CENTER);
        applyState();
    }

    public String getMatchId()
    {
        return matchId;
    }

    /** "OPEN", "LOCKED" or "CLOSED", as sent in MATCH_CHAT_STATE. */
    public void setState(String newState)
    {
        state = newState;
        applyState();
    }

    public void addMessage(String username, String text)
    {
        String me = Session.getCurrentAccount() != null ? Session.getCurrentAccount().getUsername() : null;
        String who = username != null && username.equals(me) ? "You" : username;
        log.append(who + ": " + text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
        if (collapsed)
        {
            unread++;
            refreshToggle();
        }
    }

    private void sendCurrentText()
    {
        String text = input.getValue();
        if (text.isEmpty() || !"OPEN".equals(state))
        {
            return;
        }
        if (CalcParser.isCalcCommand(text))
        {
            // "/calc ..." is answered here and never sent - only the sender sees it.
            addMessage("Calculator", CalcParser.reply(text) + "   (only you can see this)");
            input.clear();
            return;
        }
        Message request = new Message();
        request.setType(MessageType.MATCH_CHAT_SEND_REQUEST);
        request.setChatText(text);
        NetworkManager.sendAsync(request);
        input.clear();
    }

    private void applyState()
    {
        boolean open = "OPEN".equals(state);
        input.setInputEnabled(open);
        send.setEnabled(open);
        if ("LOCKED".equals(state))
        {
            status.setText("Chat is locked during this game.");
        }
        else if ("CLOSED".equals(state))
        {
            status.setText("Chat has ended.");
        }
        else
        {
            status.setText(" ");
        }
    }

    private void setCollapsed(boolean nowCollapsed)
    {
        collapsed = nowCollapsed;
        if (!collapsed)
        {
            unread = 0;
        }
        body.setVisible(!collapsed);
        title.setVisible(!collapsed);
        setPreferredSize(new Dimension(collapsed ? COLLAPSED_WIDTH : OPEN_WIDTH, 100));
        refreshToggle();
        revalidate();
        repaint();
    }

    private void refreshToggle()
    {
        if (!collapsed)
        {
            toggle.setText("Hide");
        }
        else
        {
            toggle.setText(unread > 0 ? String.valueOf(unread) : "Chat");
        }
        toggle.setPreferredSize(new Dimension(collapsed ? 64 : 72, 26));
    }
}
