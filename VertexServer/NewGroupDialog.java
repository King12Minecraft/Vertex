import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * NewGroupDialog
 * --------------
 * Prompts for a group name, then shows a checkbox list of everyone
 * currently online to pick members from - no more typing usernames by
 * hand. Only online users can be picked (Section 43's simplification -
 * no offline invite/persistence yet). The creator becomes the group
 * owner automatically.
 */
public class NewGroupDialog
{
    private NewGroupDialog()
    {
        // Static utility class - never instantiated.
    }

    public static void show(Component anchor, final ChatPanel chatPanel)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, true);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        dialog.setContentPane(root);
        dialog.getRootPane().setBorder(new LineBorder(ThemeManager.getColor(ThemeColor.BORDER), 1));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(20, 24, 4, 24));
        body.setPreferredSize(new Dimension(340, 380));

        JLabel title = new JLabel("New Group Chat");
        title.setFont(UITheme.FONT_HEADING.deriveFont(18f));
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(14));

        body.add(smallLabel("Group name"));
        final ThemedTextField nameField = new ThemedTextField("");
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(2000, 42));
        body.add(nameField);
        body.add(Box.createVerticalStrut(14));

        body.add(smallLabel("Members (currently online)"));

        final JPanel checkboxList = new JPanel();
        checkboxList.setOpaque(false);
        checkboxList.setLayout(new BoxLayout(checkboxList, BoxLayout.Y_AXIS));
        checkboxList.setAlignmentX(Component.LEFT_ALIGNMENT);

        final List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();

        JScrollPane scroll = new JScrollPane(checkboxList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(2000, 160));
        body.add(scroll);

        loadOnlineUsers(checkboxList, checkboxes);

        final JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(UITheme.FONT_SMALL);
        errorLabel.setForeground(new Color(240, 100, 100));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
        body.add(errorLabel);

        root.add(body, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(18, 24, 20, 24));

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setPreferredSize(new Dimension(90, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        final ThemedButton create = new ThemedButton("Create", true);
        create.setPreferredSize(new Dimension(100, 38));
        create.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                final String groupName = nameField.getValue();
                if (groupName.length() < 2)
                {
                    errorLabel.setText("Enter a group name.");
                    return;
                }

                final List<String> members = new ArrayList<String>();
                for (int i = 0; i < checkboxes.size(); i++)
                {
                    if (checkboxes.get(i).isSelected())
                    {
                        members.add(checkboxes.get(i).getText());
                    }
                }

                create.setEnabled(false);
                errorLabel.setText("Creating...");

                Thread worker = new Thread(new Runnable()
                {
                    public void run()
                    {
                        Message request = new Message();
                        request.setType(MessageType.GROUP_CREATE_REQUEST);
                        request.setGroupName(groupName);
                        request.setMemberUsernames(members);

                        final Message response = NetworkManager.send(request);

                        SwingUtilities.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                create.setEnabled(true);
                                if (response == null)
                                {
                                    errorLabel.setText("Can't reach the server - is it running?");
                                }
                                else if (response.isSuccess())
                                {
                                    dialog.dispose();
                                    chatPanel.openGroup(response.getGroupId(), response.getGroupName());
                                }
                                else
                                {
                                    errorLabel.setText(response.getErrorText());
                                }
                            }
                        });
                    }
                });
                worker.start();
            }
        });

        buttonRow.add(cancel);
        buttonRow.add(create);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static void loadOnlineUsers(final JPanel checkboxList, final List<JCheckBox> checkboxes)
    {
        JLabel loading = new JLabel("Loading online users...");
        loading.setFont(UITheme.FONT_SMALL);
        loading.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        checkboxList.add(loading);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.ONLINE_USERS_REQUEST);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        checkboxList.removeAll();
                        List<String> users = (response != null && response.isSuccess())
                            ? response.getOnlineUsernames() : null;

                        if (users == null || users.isEmpty())
                        {
                            JLabel empty = new JLabel("No one else is online right now.");
                            empty.setFont(UITheme.FONT_SMALL);
                            empty.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                            checkboxList.add(empty);
                        }
                        else
                        {
                            for (int i = 0; i < users.size(); i++)
                            {
                                JCheckBox box = new JCheckBox(users.get(i));
                                box.setOpaque(false);
                                box.setFont(UITheme.FONT_BODY);
                                box.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                                box.setFocusPainted(false);
                                checkboxes.add(box);
                                checkboxList.add(box);
                            }
                        }
                        checkboxList.revalidate();
                        checkboxList.repaint();
                    }
                });
            }
        });
        worker.start();
    }

    private static JLabel smallLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_SMALL);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }
}
