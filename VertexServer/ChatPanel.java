import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatPanel
 * ---------
 * Private Messages and Group Chats, all in one sidebar-driven view. Every channel type shares the same
 * message-rendering and send logic - only the outgoing MessageType
 * differs. Also supports file attachments (max
 * NetworkConfig.MAX_FILE_SIZE_BYTES, checked before sending) - files
 * are relayed like any other message field and never written to disk
 * server-side; only the recipient's own "save" action writes anything
 * locally.
 *
 * Deliberately NOT built yet (backlog): message history/persistence
 * (a channel's messages, including attachments, only exist for as
 * long as this window has been open this session), moderation
 * (mute/kick/ban), offline message delivery, unread badges on
 * individual sidebar entries (Notification Centre covers "you have
 * something new" instead).
 */
public class ChatPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private static class ChatEntry
    {
        final String sender;
        final String text;
        final String colorId;
        final String badgeId;
        final String role;
        final String fileName;
        final byte[] fileData;
        final long timestamp;

        ChatEntry(String sender, String text, String colorId, String badgeId, String role, String fileName, byte[] fileData)
        {
            this.sender = sender;
            this.text = text;
            this.colorId = colorId;
            this.badgeId = badgeId;
            this.role = role;
            this.fileName = fileName;
            this.fileData = fileData;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, String> channelNames = new LinkedHashMap<String, String>();
    private final Map<String, List<ChatEntry>> channelMessages = new LinkedHashMap<String, List<ChatEntry>>();
    private final Map<String, SidebarButton> channelButtons = new LinkedHashMap<String, SidebarButton>();
    private final java.util.Set<String> unreadChannels = new java.util.HashSet<String>();
    private String currentChannel = null;

    private JPanel sidebarList;
    private JPanel messageListPanel;
    private JScrollPane scrollPane;
    private ThemedTextField field;
    private PageHeader headerLabel;

    public ChatPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.add(createSidebar(), BorderLayout.WEST);
        body.add(createMessageArea(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        add(createInputRow(), BorderLayout.SOUTH);

        rebuildSidebar();
        renderChannel(currentChannel);

        NetworkManager.addPushListener(this);
    }

    private PageHeader createHeader()
    {
        headerLabel = new PageHeader("MESSAGES");

        ThemedButton report = new ThemedButton("Report a Player", false);
        report.setPreferredSize(new Dimension(150, 34));
        report.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { ReportPlayerDialog.show(ChatPanel.this); }
        });
        headerLabel.setRightComponent(report);

        return headerLabel;
    }

    private RoundedPanel createSidebar()
    {
        RoundedPanel wrap = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        wrap.setLayout(new BorderLayout());
        wrap.setPreferredSize(new Dimension(210, 0));
        wrap.setBorder(new EmptyBorder(12, 8, 12, 8));

        sidebarList = new JPanel();
        sidebarList.setOpaque(false);
        sidebarList.setLayout(new BoxLayout(sidebarList, BoxLayout.Y_AXIS));

        JScrollPane sidebarScroll = new JScrollPane(sidebarList);
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
        sidebarScroll.setOpaque(false);
        sidebarScroll.getViewport().setOpaque(false);
        ThemedScrollBarUI.apply(sidebarScroll);
        wrap.add(sidebarScroll, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 6, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(8, 4, 0, 4));

        ThemedButton newDm = new ThemedButton("+ DM", false);
        newDm.setPreferredSize(new Dimension(85, 30));
        newDm.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { NewDirectMessageDialog.show(ChatPanel.this, ChatPanel.this); }
        });

        ThemedButton newGroup = new ThemedButton("+ Group", false);
        newGroup.setPreferredSize(new Dimension(85, 30));
        newGroup.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { NewGroupDialog.show(ChatPanel.this, ChatPanel.this); }
        });

        buttonRow.add(newDm);
        buttonRow.add(newGroup);
        wrap.add(buttonRow, BorderLayout.SOUTH);

        return wrap;
    }

    private RoundedPanel createMessageArea()
    {
        RoundedPanel messageArea = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        messageArea.setLayout(new BorderLayout());
        messageArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        messageListPanel = new JPanel();
        messageListPanel.setOpaque(false);
        messageListPanel.setLayout(new BoxLayout(messageListPanel, BoxLayout.Y_AXIS));

        scrollPane = new JScrollPane(messageListPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scrollPane);
        messageArea.add(scrollPane, BorderLayout.CENTER);

        return messageArea;
    }

    private JPanel createInputRow()
    {
        RoundedPanel pill = new RoundedPanel(ThemeColor.BG_PANEL, 20);
        pill.setLayout(new BorderLayout(8, 0));
        pill.setBorder(new EmptyBorder(6, 10, 6, 10));

        ThemedButton attach = new ThemedButton("+", false);
        attach.setPreferredSize(new Dimension(38, 38));
        attach.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { attachFile(); }
        });
        pill.add(attach, BorderLayout.WEST);

        field = new ThemedTextField("Message...");
        field.setBorder(BorderFactory.createEmptyBorder());
        pill.add(field, BorderLayout.CENTER);

        final ThemedButton send = new ThemedButton("Send", true);
        send.setPreferredSize(new Dimension(80, 38));

        ActionListener sendAction = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { sendCurrentChannelMessage(); }
        };
        send.addActionListener(sendAction);
        field.addActionListener(sendAction);
        pill.add(send, BorderLayout.EAST);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(16, 0, 0, 0));
        row.add(pill, BorderLayout.CENTER);
        return row;
    }

    // ---- Channel management (called by the New DM / New Group dialogs) ----

    /** Opens (creating if needed) a direct-message channel with the given user. */
    public void openDirectMessage(String otherUsername)
    {
        String key = "dm:" + otherUsername.toLowerCase();
        if (!channelNames.containsKey(key))
        {
            channelNames.put(key, "@ " + otherUsername);
            channelMessages.put(key, new ArrayList<ChatEntry>());
            rebuildSidebar();
        }
        switchChannel(key);
    }

    /** Opens a group channel once the server has confirmed its creation. */
    public void openGroup(String groupId, String groupName)
    {
        String key = "group:" + groupId;
        channelNames.put(key, "# " + groupName);
        if (!channelMessages.containsKey(key))
        {
            channelMessages.put(key, new ArrayList<ChatEntry>());
        }
        rebuildSidebar();
        switchChannel(key);
    }

    private void rebuildSidebar()
    {
        sidebarList.removeAll();
        channelButtons.clear();

        for (final String key : channelNames.keySet())
        {
            // Strip the "@ "/"# " prefix from the button's own label - that distinction is
            // now carried by the icon/avatar to its left instead, Discord-style, rather than
            // by a leading character in the text.
            String rawName = channelNames.get(key);
            String displayName = rawName.length() > 2 ? rawName.substring(2) : rawName;

            SidebarButton button = new SidebarButton(displayName);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(2000, 40));
            button.setPreferredSize(new Dimension(150, 40));
            button.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { switchChannel(key); }
            });
            button.setSelected(key.equals(currentChannel));
            channelButtons.put(key, button);

            JPanel wrapper = new JPanel();
            wrapper.setOpaque(false);
            wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            wrapper.setMaximumSize(new Dimension(2000, 40));
            wrapper.setBorder(new EmptyBorder(0, 0, 4, 0));
            wrapper.add(buildChannelIcon(key, displayName));
            wrapper.add(Box.createHorizontalStrut(8));
            wrapper.add(button);

            sidebarList.add(wrapper);
        }

        sidebarList.revalidate();
        sidebarList.repaint();
    }

    /** DMs get the other person's avatar (fetched via AvatarCache, same as message rows); groups get a simple "#" tile - Discord's own text-channel icon convention, since a group here has no single "person" to represent it with a photo. Either gets a small unread dot overlaid when this channel has an unseen message. */
    private JPanel buildChannelIcon(String key, String displayName)
    {
        boolean unread = unreadChannels.contains(key);

        if (key.startsWith("dm:"))
        {
            final ChannelAvatarIcon icon = new ChannelAvatarIcon();
            icon.setUnread(unread);
            AvatarCache.get(displayName, new AvatarCache.Listener()
            {
                public void onLoaded(java.awt.Image image) { icon.setImage(image); }
            });
            return icon;
        }

        final boolean showUnreadDot = unread;
        JPanel tile = new JPanel(new BorderLayout())
        {
            protected void paintComponent(java.awt.Graphics g)
            {
                super.paintComponent(g);
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                UITheme.applyAntialiasing(g2);
                g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                g2.setFont(UITheme.FONT_NAV_BOLD);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String hash = "#";
                g2.drawString(hash, (getWidth() - fm.stringWidth(hash)) / 2, (getHeight() + fm.getAscent()) / 2 - 2);
                if (showUnreadDot)
                {
                    g2.setColor(ThemeManager.getColor(ThemeColor.SUCCESS));
                    g2.fillOval(getWidth() - 10, -2, 10, 10);
                }
                g2.dispose();
            }
        };
        tile.setOpaque(false);
        tile.setPreferredSize(new Dimension(28, 28));
        tile.setMaximumSize(new Dimension(28, 28));
        return tile;
    }

    private static class ChannelAvatarIcon extends JPanel
    {
        private java.awt.Image image;
        private boolean unread;

        ChannelAvatarIcon()
        {
            setPreferredSize(new Dimension(28, 28));
            setMaximumSize(new Dimension(28, 28));
            setOpaque(false);
        }

        void setImage(java.awt.Image image)
        {
            this.image = image;
            repaint();
        }

        void setUnread(boolean unread)
        {
            this.unread = unread;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
            g2.fillOval(0, 0, getWidth(), getHeight());
            if (image != null)
            {
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
                g2.setClip(null);
            }
            if (unread)
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.SUCCESS));
                g2.fillOval(getWidth() - 10, -2, 10, 10);
            }
            g2.dispose();
        }
    }

    private void switchChannel(String key)
    {
        currentChannel = key;
        headerLabel.setTitle(channelNames.get(key).toUpperCase());
        unreadChannels.remove(key);

        for (Map.Entry<String, SidebarButton> entry : channelButtons.entrySet())
        {
            entry.getValue().setSelected(entry.getKey().equals(key));
        }

        rebuildSidebar();
        renderChannel(key);
    }

    // ---- Rendering ----

    /** key is null when there are no DMs or groups yet at all (General Chat was removed - Private Messages and Group Chats are the only channel types now) - shown as a dedicated empty state rather than crashing on a null lookup. */
    private void renderChannel(String key)
    {
        messageListPanel.removeAll();

        if (key == null)
        {
            JLabel empty = new JLabel("No conversations yet - start a DM or a group to begin chatting.");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            messageListPanel.add(empty);
            messageListPanel.revalidate();
            messageListPanel.repaint();
            return;
        }

        List<ChatEntry> history = channelMessages.get(key);
        if (history == null || history.isEmpty())
        {
            JLabel empty = new JLabel("No messages yet - say hello!");
            empty.setFont(UITheme.FONT_BODY);
            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            messageListPanel.add(empty);
        }
        else
        {
            // Discord-style grouping: consecutive messages from the same sender within
            // GROUP_WINDOW_MS of each other share one avatar/username header instead of
            // repeating it for every single message.
            for (int i = 0; i < history.size(); i++)
            {
                ChatEntry entry = history.get(i);
                ChatEntry previous = i > 0 ? history.get(i - 1) : null;
                boolean isContinuation = previous != null
                    && entry.sender != null && entry.sender.equals(previous.sender)
                    && (entry.timestamp - previous.timestamp) < GROUP_WINDOW_MS;
                messageListPanel.add(buildMessageRow(entry, isContinuation));
            }
        }

        messageListPanel.revalidate();
        messageListPanel.repaint();
        scrollToBottom();
    }

    private static final long GROUP_WINDOW_MS = 5 * 60 * 1000;
    private static final int AVATAR_COLUMN_WIDTH = 42;

    private JPanel buildMessageRow(final ChatEntry entry, boolean isContinuation)
    {
        final RoundedPanel row = new RoundedPanel(ThemeColor.BG_PANEL, 6);
        row.setLayout(new BorderLayout(10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(isContinuation ? 1 : 8, 8, 1, 8));
        row.setMaximumSize(new Dimension(2000, 2000));

        // Discord's own "flush rows that highlight on hover" treatment, rather than chat
        // bubbles - bubbles read as a 1:1 DM app (iMessage/WhatsApp); Discord (and this app's
        // own group chats) are closer to a shared room, where flush rows scan faster.
        row.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { row.setBackgroundRole(ThemeColor.BG_SIDEBAR); }
            public void mouseExited(MouseEvent e)  { row.setBackgroundRole(ThemeColor.BG_PANEL); }
        });

        JPanel avatarColumn = new JPanel();
        avatarColumn.setOpaque(false);
        avatarColumn.setPreferredSize(new Dimension(AVATAR_COLUMN_WIDTH, 1));
        if (!isContinuation)
        {
            final AvatarBubble bubble = new AvatarBubble();
            bubble.setBounds(0, 0, 32, 32);
            avatarColumn.setLayout(null);
            bubble.setBounds(2, 2, 32, 32);
            avatarColumn.add(bubble);
            if (entry.sender != null)
            {
                AvatarCache.get(entry.sender, new AvatarCache.Listener()
                {
                    public void onLoaded(java.awt.Image image) { bubble.setImage(image); }
                });
            }
        }
        row.add(avatarColumn, BorderLayout.WEST);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        boolean isMe = entry.sender != null && Session.isLoggedIn()
            && entry.sender.equals(Session.getCurrentAccount().getUsername());

        if (!isContinuation)
        {
            content.add(buildHeaderLine(entry, isMe));
        }

        if (entry.text != null && !entry.text.isEmpty())
        {
            JLabel textLabel = new JLabel("<html><body style='width:340px'>" + escapeHtml(entry.text) + "</body></html>");
            textLabel.setFont(UITheme.FONT_BODY);
            textLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(textLabel);
        }

        if (entry.fileData != null && entry.fileData.length > 0)
        {
            content.add(buildFileChip(entry.fileName, entry.fileData));
        }

        row.add(content, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildHeaderLine(ChatEntry entry, boolean isMe)
    {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 2, 0));

        Color customColor = PlayerColorRegistry.resolve(entry.colorId);
        Color roleColor = "ADMIN".equals(entry.role) ? new Color(230, 90, 90)
            : "MODERATOR".equals(entry.role) ? new Color(90, 170, 230) : null;

        String badgeGlyph = PlayerColorRegistry.resolveBadgeGlyph(entry.badgeId);
        JLabel senderLabel = new JLabel((badgeGlyph != null ? badgeGlyph + " " : "") + entry.sender + (isMe ? " (you)" : ""));
        senderLabel.setFont(UITheme.FONT_NAV_BOLD);
        if (roleColor != null)
        {
            // Admin/moderator color always wins over a purchased cosmetic color - staff
            // visibility in chat shouldn't depend on what someone happened to buy in the shop.
            senderLabel.setForeground(roleColor);
        }
        else if (customColor != null)
        {
            senderLabel.setForeground(customColor);
        }
        else
        {
            senderLabel.setForeground(isMe
                ? ThemeManager.getColor(ThemeColor.ACCENT)
                : ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        }
        header.add(senderLabel);

        JLabel timeLabel = new JLabel(formatTimestamp(entry.timestamp));
        timeLabel.setFont(UITheme.FONT_SMALL.deriveFont(11f));
        timeLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        header.add(timeLabel);

        return header;
    }

    private String formatTimestamp(long millis)
    {
        return new java.text.SimpleDateFormat("h:mm a").format(new java.util.Date(millis));
    }

    /** A small circular avatar - null image just renders as a plain filled circle, matching SettingsPanel's own AvatarBubble (kept as a separate copy here rather than shared, since Swing components can't be reparented across two different container hierarchies anyway). */
    private static class AvatarBubble extends JPanel
    {
        private java.awt.Image image;

        AvatarBubble()
        {
            setPreferredSize(new Dimension(32, 32));
            setOpaque(false);
        }

        void setImage(java.awt.Image image)
        {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
            g2.fillOval(0, 0, getWidth(), getHeight());
            if (image != null)
            {
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
            g2.dispose();
        }
    }

    /** A small clickable chip for a received file attachment - click to save it locally. */
    private JPanel buildFileChip(final String fileName, final byte[] fileData)
    {
        RoundedPanel chip = new RoundedPanel(ThemeColor.BG_SIDEBAR, 8);
        chip.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
        chip.setAlignmentX(Component.LEFT_ALIGNMENT);
        chip.setMaximumSize(new Dimension(360, 40));
        chip.setBorder(new EmptyBorder(2, 10, 2, 10));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String sizeText = formatFileSize(fileData.length);
        JLabel label = new JLabel("\uD83D\uDCCE " + fileName + "  (" + sizeText + ")");
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        chip.add(label);

        chip.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { saveFile(fileName, fileData); }
        });

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(new EmptyBorder(4, 0, 0, 0));
        wrapper.add(chip);
        return wrapper;
    }

    private void saveFile(String fileName, byte[] fileData)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(fileName));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        File target = chooser.getSelectedFile();
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream(target);
            out.write(fileData);
            GameHubDialog.show(this, "Chat", "Saved to " + target.getAbsolutePath());
        }
        catch (IOException e)
        {
            GameHubDialog.show(this, "Chat", "Could not save the file: " + e.getMessage());
        }
        finally
        {
            if (out != null)
            {
                try { out.close(); } catch (IOException ignored) { }
            }
        }
    }

    private String formatFileSize(int bytes)
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

    private void scrollToBottom()
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                JScrollBar bar = scrollPane.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    // ---- Sending ----

    private void sendCurrentChannelMessage()
    {
        String text = field.getValue();
        if (text.isEmpty())
        {
            return;
        }

        Message request = buildOutgoingMessage(text, null, null);
        if (request == null)
        {
            return;
        }

        boolean sent = NetworkManager.sendAsync(request);
        if (sent)
        {
            field.clear();
        }
        else
        {
            GameHubDialog.show(field, "Chat", "Can't reach the server - is it running?");
        }
    }

    /**
     * Opens a file chooser, checks the size limit client-side (the
     * server also checks, but failing fast here avoids a pointless
     * round trip), and sends it as its own message. Never written to
     * disk anywhere on the server - it's relayed exactly like any
     * other message field and only exists on each client's screen for
     * this session.
     */
    private void attachFile()
    {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        File selected = chooser.getSelectedFile();
        if (selected.length() > NetworkConfig.MAX_FILE_SIZE_BYTES)
        {
            GameHubDialog.show(this, "Chat", "That file is too large - the limit is "
                + formatFileSize(NetworkConfig.MAX_FILE_SIZE_BYTES) + ".");
            return;
        }

        byte[] data;
        try
        {
            data = Files.readAllBytes(selected.toPath());
        }
        catch (IOException e)
        {
            GameHubDialog.show(this, "Chat", "Could not read that file: " + e.getMessage());
            return;
        }

        Message request = buildOutgoingMessage("", selected.getName(), data);
        if (request == null)
        {
            return;
        }

        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            GameHubDialog.show(this, "Chat", "Can't reach the server - is it running?");
        }
    }

    /** Shared by both text and file sends - only the payload differs. currentChannel is only ever null when there are no DMs/groups yet, in which case there's nothing to send to. */
    private Message buildOutgoingMessage(String text, String fileName, byte[] fileData)
    {
        if (currentChannel == null)
        {
            return null;
        }

        Message request = new Message();

        if (currentChannel.startsWith("dm:"))
        {
            String otherUsername = channelNames.get(currentChannel).substring(2); // strip "@ "
            request.setType(MessageType.PRIVATE_MESSAGE);
            request.setToUsername(otherUsername);
        }
        else if (currentChannel.startsWith("group:"))
        {
            String groupId = currentChannel.substring("group:".length());
            request.setType(MessageType.GROUP_MESSAGE);
            request.setGroupId(groupId);
        }
        else
        {
            return null;
        }

        request.setChatText(text);
        if (fileName != null)
        {
            request.setFileName(fileName);
            request.setFileData(fileData);
        }
        return request;
    }

    // ---- Receiving ----

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        if (type != MessageType.PRIVATE_MESSAGE && type != MessageType.GROUP_MESSAGE && type != MessageType.GROUP_ADDED)
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleIncoming(message); }
        });
    }

    private void handleIncoming(Message message)
    {
        MessageType type = message.getType();

        if (type == MessageType.PRIVATE_MESSAGE)
        {
            String sender = message.getUsername();
            boolean isMe = Session.isLoggedIn() && sender != null
                && sender.equals(Session.getCurrentAccount().getUsername());
            // If this is the echo of OUR OWN sent message, the "other side" of the
            // conversation is the recipient; otherwise it's whoever sent it to us.
            String otherParty = isMe ? message.getToUsername() : sender;
            String key = "dm:" + otherParty.toLowerCase();

            boolean isNewChannel = !channelNames.containsKey(key);
            if (isNewChannel)
            {
                channelNames.put(key, "@ " + otherParty);
                channelMessages.put(key, new ArrayList<ChatEntry>());
                rebuildSidebar();
            }

            if (!isMe && !key.equals(currentChannel))
            {
                NotificationCenter.add("New message", sender + ": " + notificationPreview(message));
            }

            recordAndMaybeRender(key, message);
        }
        else if (type == MessageType.GROUP_MESSAGE)
        {
            String key = "group:" + message.getGroupId();
            if (!channelMessages.containsKey(key))
            {
                channelMessages.put(key, new ArrayList<ChatEntry>());
            }

            boolean isMe = Session.isLoggedIn() && message.getUsername() != null
                && message.getUsername().equals(Session.getCurrentAccount().getUsername());
            if (!isMe && !key.equals(currentChannel))
            {
                NotificationCenter.add("New group message", message.getUsername() + ": " + notificationPreview(message));
            }

            recordAndMaybeRender(key, message);
        }
        else if (type == MessageType.GROUP_ADDED)
        {
            String key = "group:" + message.getGroupId();
            channelNames.put(key, "# " + message.getGroupName());
            channelMessages.put(key, new ArrayList<ChatEntry>());
            rebuildSidebar();
            NotificationCenter.add("Added to group",
                "You were added to " + message.getGroupName() + " by " + message.getUsername());
        }
    }

    private String notificationPreview(Message message)
    {
        if (message.getFileName() != null)
        {
            return "sent a file: " + message.getFileName();
        }
        return message.getChatText();
    }

    private void recordAndMaybeRender(String key, Message message)
    {
        List<ChatEntry> history = channelMessages.get(key);
        if (history == null)
        {
            history = new ArrayList<ChatEntry>();
            channelMessages.put(key, history);
        }
        history.add(new ChatEntry(message.getUsername(), message.getChatText(), message.getSenderColorId(),
            message.getSenderBadgeId(), message.getSenderRole(), message.getFileName(), message.getFileData()));

        if (key.equals(currentChannel))
        {
            renderChannel(key);
        }
        else
        {
            boolean isMe = Session.isLoggedIn() && message.getUsername() != null
                && message.getUsername().equals(Session.getCurrentAccount().getUsername());
            if (!isMe)
            {
                unreadChannels.add(key);
                rebuildSidebar();
            }
        }
    }

    /** Basic defense against user-supplied text breaking the HTML-rendered JLabel. */
    private String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
