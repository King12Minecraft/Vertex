import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupChatManager
{
    public static class Group
    {
        final String groupId;
        String name;
        final String ownerUsername;
        final List<String> memberUsernames = new ArrayList<String>();

        Group(String groupId, String name, String ownerUsername)
        {
            this.groupId = groupId;
            this.name = name;
            this.ownerUsername = ownerUsername;
        }
    }

    private final Map<String, Group> groups = new HashMap<String, Group>();
    private int nextGroupId = 1;
    private final ChatManager chatManager;

    public GroupChatManager(ChatManager chatManager)
    {
        this.chatManager = chatManager;
    }

    public synchronized Group createGroup(String ownerUsername, String requestedName, List<String> requestedMembers)
    {
        String name = (requestedName == null || requestedName.trim().isEmpty()) ? "Unnamed Group" : requestedName.trim();
        Group group = new Group("group-" + (nextGroupId++), name, ownerUsername);
        group.memberUsernames.add(ownerUsername);

        List<String> actuallyAdded = new ArrayList<String>();
        if (requestedMembers != null)
        {
            for (int i = 0; i < requestedMembers.size(); i++)
            {
                String candidate = requestedMembers.get(i);
                if (candidate == null) continue;
                candidate = candidate.trim();
                if (candidate.isEmpty() || candidate.equalsIgnoreCase(ownerUsername)) continue;

                ClientHandler target = chatManager.findByUsername(candidate);
                if (target != null && target.getLoggedInUsername() != null
                    && !group.memberUsernames.contains(target.getLoggedInUsername()))
                {
                    group.memberUsernames.add(target.getLoggedInUsername());
                    actuallyAdded.add(target.getLoggedInUsername());
                }
            }
        }

        groups.put(group.groupId, group);

        for (int i = 0; i < actuallyAdded.size(); i++)
        {
            ClientHandler target = chatManager.findByUsername(actuallyAdded.get(i));
            if (target != null)
            {
                Message notice = new Message();
                notice.setType(MessageType.GROUP_ADDED);
                notice.setGroupId(group.groupId);
                notice.setGroupName(group.name);
                notice.setUsername(ownerUsername);
                target.sendMessage(notice);
            }
        }

        return group;
    }

    public synchronized void sendGroupMessage(String groupId, String senderUsername, String senderColorId, String senderBadgeId,
                                               String text, String fileName, byte[] fileData)
    {
        Group group = groups.get(groupId);
        if (group == null || !group.memberUsernames.contains(senderUsername)) return;

        String trimmedText = ChatManager.trimText(text);
        byte[] validFileData = ChatManager.validateFile(fileData);
        String validFileName = validFileData != null ? fileName : null;

        if (trimmedText.isEmpty() && validFileData == null)
        {
            return;
        }

        Message msg = new Message();
        msg.setType(MessageType.GROUP_MESSAGE);
        msg.setGroupId(groupId);
        msg.setUsername(senderUsername);
        msg.setSenderColorId(senderColorId);
        msg.setSenderBadgeId(senderBadgeId);
        msg.setChatText(trimmedText);
        msg.setFileName(validFileName);
        msg.setFileData(validFileData);

        for (int i = 0; i < group.memberUsernames.size(); i++)
        {
            ClientHandler member = chatManager.findByUsername(group.memberUsernames.get(i));
            if (member != null) member.sendMessage(msg);
        }
    }
}
