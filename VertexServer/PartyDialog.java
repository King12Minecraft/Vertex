import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * PartyDialog
 * -----------
 * Not in a party: create one, or enter a code to join one. In a
 * party: member list (leader marked), an Invite button reusing the
 * Friends list, a Leave button, and Kick buttons for the leader.
 * Party state itself lives wherever it's needed (TopBar keeps a
 * "party of N" indicator) - this dialog is just the control surface.
 */
public class PartyDialog extends JDialog implements NetworkManager.PushListener
{
    private final JPanel contentArea;
    private ThemedTextField codeField;

    private String myCode;
    private List<String> members;
    private String leaderUsername;

    public PartyDialog(Component anchor)
    {
        super((Frame) javax.swing.SwingUtilities.getWindowAncestor(anchor), "Party", false);
        setUndecorated(true);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setPreferredSize(new Dimension(340, 320));
        getRootPane().setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JLabel title = new JLabel("Party");
        title.setFont(UITheme.FONT_NAV_BOLD);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setBorder(new javax.swing.border.EmptyBorder(16, 16, 10, 16));
        root.add(title, BorderLayout.NORTH);

        contentArea = new JPanel();
        contentArea.setOpaque(false);
        contentArea.setLayout(new BoxLayout(contentArea, BoxLayout.Y_AXIS));
        contentArea.setBorder(new javax.swing.border.EmptyBorder(0, 16, 16, 16));
        root.add(contentArea, BorderLayout.CENTER);

        setContentPane(root);

        NetworkManager.addPushListener(this);
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent e) { NetworkManager.removePushListener(PartyDialog.this); }
        });

        renderNoParty();
        pack();
        setLocationRelativeTo(anchor);
    }

    private void renderNoParty()
    {
        members = null;
        contentArea.removeAll();

        JLabel info = new JLabel("You're not in a party.");
        info.setFont(UITheme.FONT_BODY);
        info.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setBorder(new javax.swing.border.EmptyBorder(0, 0, 16, 0));
        contentArea.add(info);

        ThemedButton createButton = new ThemedButton("Create a Party", true);
        createButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        createButton.setMaximumSize(new Dimension(2000, 38));
        createButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Message request = new Message();
                request.setType(MessageType.PARTY_CREATE_REQUEST);
                NetworkManager.sendAsync(request);
            }
        });
        contentArea.add(createButton);
        contentArea.add(Box.createVerticalStrut(16));

        JLabel orLabel = new JLabel("or join with a code:");
        orLabel.setFont(UITheme.FONT_SMALL);
        orLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        orLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        orLabel.setBorder(new javax.swing.border.EmptyBorder(0, 0, 6, 0));
        contentArea.add(orLabel);

        JPanel codeRow = new JPanel(new BorderLayout());
        codeRow.setOpaque(false);
        codeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeRow.setMaximumSize(new Dimension(2000, 44));

        codeField = new ThemedTextField("Party code");
        codeRow.add(codeField, BorderLayout.CENTER);

        ThemedButton joinButton = new ThemedButton("Join", false);
        joinButton.setPreferredSize(new Dimension(80, 36));
        joinButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attemptJoin(); }
        });
        JPanel joinWrap = new JPanel(new BorderLayout());
        joinWrap.setOpaque(false);
        joinWrap.setBorder(new javax.swing.border.EmptyBorder(0, 8, 0, 0));
        joinWrap.add(joinButton, BorderLayout.CENTER);
        codeRow.add(joinWrap, BorderLayout.EAST);

        contentArea.add(codeRow);

        contentArea.revalidate();
        contentArea.repaint();
        pack();
    }

    private void attemptJoin()
    {
        String code = codeField.getValue().trim();
        if (code.isEmpty())
        {
            return;
        }
        Message request = new Message();
        request.setType(MessageType.PARTY_JOIN_BY_CODE_REQUEST);
        request.setPartyCode(code);
        NetworkManager.sendAsync(request);
    }

    private void renderParty()
    {
        contentArea.removeAll();

        boolean isLeader = Session.isLoggedIn() && leaderUsername != null
            && leaderUsername.equalsIgnoreCase(Session.getCurrentAccount().getUsername());

        JPanel codeRow = new JPanel(new java.awt.BorderLayout());
        codeRow.setOpaque(false);
        codeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeRow.setMaximumSize(new java.awt.Dimension(2000, 30));
        codeRow.setBorder(new javax.swing.border.EmptyBorder(0, 0, 4, 0));

        JLabel codeLabel = new JLabel("Code: " + myCode);
        codeLabel.setFont(UITheme.FONT_NAV_BOLD);
        codeLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        codeRow.add(codeLabel, java.awt.BorderLayout.WEST);

        ThemedButton copyButton = new ThemedButton("Copy", false);
        copyButton.setPreferredSize(new java.awt.Dimension(64, 24));
        copyButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(myCode), null);
                copyButton.setText("Copied!");
                javax.swing.Timer resetTimer = new javax.swing.Timer(1500, new ActionListener()
                {
                    public void actionPerformed(ActionEvent e2) { copyButton.setText("Copy"); }
                });
                resetTimer.setRepeats(false);
                resetTimer.start();
            }
        });
        codeRow.add(copyButton, java.awt.BorderLayout.EAST);
        contentArea.add(codeRow);

        JLabel hint = new JLabel("Share this code, or invite a friend directly below.");
        hint.setFont(UITheme.FONT_SMALL);
        hint.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new javax.swing.border.EmptyBorder(0, 0, 14, 0));
        contentArea.add(hint);

        for (int i = 0; i < members.size(); i++)
        {
            final String member = members.get(i);
            boolean memberIsLeader = member.equalsIgnoreCase(leaderUsername);

            RoundedPanel row = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
            row.setLayout(new BorderLayout());
            row.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 12));
            row.setMaximumSize(new Dimension(2000, 40));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel nameLabel = new JLabel((memberIsLeader ? "\u2605 " : "") + member);
            nameLabel.setFont(UITheme.FONT_SMALL);
            nameLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            row.add(nameLabel, BorderLayout.WEST);

            boolean isMe = Session.isLoggedIn() && member.equalsIgnoreCase(Session.getCurrentAccount().getUsername());
            if (isLeader && !isMe)
            {
                ThemedButton kick = new ThemedButton("Kick", false);
                kick.setPreferredSize(new Dimension(60, 26));
                kick.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        Message request = new Message();
                        request.setType(MessageType.PARTY_KICK_REQUEST);
                        request.setToUsername(member);
                        NetworkManager.sendAsync(request);
                    }
                });
                row.add(kick, BorderLayout.EAST);
            }

            contentArea.add(row);
            contentArea.add(Box.createVerticalStrut(4));
        }

        contentArea.add(Box.createVerticalStrut(10));

        ThemedButton invite = new ThemedButton("Invite a Friend", false);
        invite.setAlignmentX(Component.LEFT_ALIGNMENT);
        invite.setMaximumSize(new Dimension(2000, 38));
        invite.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { FriendPickerDialog.showForParty(PartyDialog.this); }
        });
        contentArea.add(invite);
        contentArea.add(Box.createVerticalStrut(8));

        ThemedButton leave = new ThemedButton("Leave Party", false);
        leave.setAlignmentX(Component.LEFT_ALIGNMENT);
        leave.setMaximumSize(new Dimension(2000, 38));
        leave.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Message request = new Message();
                request.setType(MessageType.PARTY_LEAVE_REQUEST);
                NetworkManager.sendAsync(request);
                dispose();
            }
        });
        contentArea.add(leave);

        contentArea.revalidate();
        contentArea.repaint();
        pack();
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isPartyType = type == MessageType.PARTY_UPDATE || type == MessageType.PARTY_JOIN_RESPONSE
            || type == MessageType.PARTY_DISBANDED;
        if (!isPartyType)
        {
            return;
        }

        javax.swing.SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handlePartyMessage(message); }
        });
    }

    private void handlePartyMessage(Message message)
    {
        if (message.getType() == MessageType.PARTY_UPDATE)
        {
            myCode = message.getPartyCode();
            members = message.getPartyMembers();
            leaderUsername = message.getPartyLeader();
            renderParty();
        }
        else if (message.getType() == MessageType.PARTY_JOIN_RESPONSE)
        {
            if (!message.isSuccess())
            {
                GameHubDialog.show(this, "Couldn't Join Party", message.getErrorText());
            }
        }
        else if (message.getType() == MessageType.PARTY_DISBANDED)
        {
            GameHubDialog.show(this, "Party", message.getErrorText() != null ? message.getErrorText() : "Your party has disbanded.");
            renderNoParty();
        }
    }
}
