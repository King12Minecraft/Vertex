package pages;
import account.Session;
import dominion.Army;
import dominion.DiplomaticProposal;
import dominion.DiplomaticRelation;
import dominion.Nation;
import dominion.Province;
import dominion.RelationType;
import dominion.Terrain;
import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;
import ui.ThemedLabel;
import ui.ThemedScrollBarUI;
import ui.ThemedTextField;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DominionPanel
 * -------------
 * Build order step 4: the client's persistent nav destination for Vertex:
 * Dominion (see DOMINION_DESIGN.md / ROADMAP.md's Architecture Decisions -
 * its own nav tab, not the MainMenu game-host CardLayout slot every match
 * game uses, since Dominion is a standing thing checked in on repeatedly
 * rather than launched-and-left).
 *
 * V1 first slice: a "found a nation" flow for an account with none yet, and
 * for one that has a nation - a read-only grid map, a dashboard (treasury/
 * Honor/tick/armies), and the four order types build order step 3 already
 * wired up server-side (recruit, march, declare war, propose/respond to a
 * relation). No push-based live updates yet (the server has no
 * broadcast-on-tick mechanism for Dominion, unlike a live match) - a Refresh
 * button re-fetches DOMINION_STATE_REQUEST instead, an honest V1 limitation
 * rather than a silently missing feature.
 *
 * Every request here follows LeaderboardPanel's established shape - a plain
 * blocking NetworkManager.send() on a background thread, the UI update
 * marshaled back via SwingUtilities.invokeLater - since none of these
 * responses are ever pushed unprompted.
 */
public class DominionPanel extends RoundedPanel
{
    /** A small fixed palette for other nations' map colors - deterministic (nationId % length), not randomized, so the same nation always reads the same color across a session. Your own nation always uses the theme's ACCENT color instead, so it's never ambiguous which one is yours. */
    private static final Color[] NATION_PALETTE = new Color[] {
        new Color(0xE0, 0x6C, 0x5C), new Color(0x5C, 0x9E, 0xE0), new Color(0xE0, 0xC4, 0x5C),
        new Color(0x8E, 0x5C, 0xE0), new Color(0x5C, 0xE0, 0x9E), new Color(0xE0, 0x8E, 0x5C)
    };

    private final JLabel statusLabel;
    private final JPanel contentArea;

    private dominion.DominionSnapshot snapshot;
    private Nation myNation;
    private Integer selectedFoundingProvinceId;

    public DominionPanel()
    {
        super(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 16, 0));

        JLabel title = new ThemedLabel("Vertex: Dominion", ThemeColor.TEXT_PRIMARY);
        title.setFont(UITheme.FONT_HEADING);
        header.add(title, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerRight.setOpaque(false);
        statusLabel = new ThemedLabel("Loading...", ThemeColor.TEXT_MUTED);
        statusLabel.setFont(UITheme.FONT_SMALL);
        headerRight.add(statusLabel);
        ThemedButton refreshButton = new ThemedButton("Refresh", false);
        refreshButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { loadState(); }
        });
        headerRight.add(refreshButton);
        header.add(headerRight, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        contentArea = new JPanel();
        contentArea.setOpaque(false);
        contentArea.setLayout(new BoxLayout(contentArea, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        loadState();
    }

    // ---- networking ----

    private void loadState()
    {
        statusLabel.setText("Loading...");
        final Message request = new Message();
        request.setType(MessageType.DOMINION_STATE_REQUEST);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run() { onStateLoaded(response); }
                });
            }
        });
        worker.start();
    }

    private void onStateLoaded(Message response)
    {
        if (response == null || !response.isSuccess() || response.getDominionSnapshot() == null)
        {
            String connectionIssue = NetworkManager.describeIfNotReady();
            statusLabel.setText(connectionIssue != null ? connectionIssue : "Couldn't load Dominion - try Refresh.");
            return;
        }
        snapshot = response.getDominionSnapshot();
        myNation = findMyNation();
        statusLabel.setText("Day " + snapshot.getCurrentTick());
        render();
    }

    /** Runs a fire-and-forget order request on a background thread, then reloads the full state on success (there's no cheaper way to reflect a change today, since responses carry no updated snapshot of their own) or shows the server's error text on failure. */
    private void sendOrder(final Message request, final String successStatus)
    {
        statusLabel.setText("Sending...");
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                final Message response = NetworkManager.send(request);
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null)
                        {
                            String connectionIssue = NetworkManager.describeIfNotReady();
                            statusLabel.setText(connectionIssue != null ? connectionIssue : "No response - try again.");
                            return;
                        }
                        if (!response.isSuccess())
                        {
                            statusLabel.setText(response.getErrorText() != null ? response.getErrorText() : "That didn't work.");
                            return;
                        }
                        statusLabel.setText(successStatus);
                        loadState();
                    }
                });
            }
        });
        worker.start();
    }

    private Nation findMyNation()
    {
        int myAccountId = Session.getCurrentAccount().getAccountId();
        for (Nation nation : snapshot.getNations())
        {
            if (nation.getAccountId() == myAccountId)
            {
                return nation;
            }
        }
        return null;
    }

    private String nationName(int nationId)
    {
        for (Nation nation : snapshot.getNations())
        {
            if (nation.getId() == nationId)
            {
                return nation.getName();
            }
        }
        return "Unknown nation";
    }

    // ---- rendering ----

    private void render()
    {
        contentArea.removeAll();
        if (myNation == null)
        {
            contentArea.add(buildFoundNationView());
        }
        else
        {
            contentArea.add(buildDashboard());
        }
        contentArea.revalidate();
        contentArea.repaint();
    }

    private JComponent buildFoundNationView()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel instructions = new ThemedLabel(
            "You don't have a Nation yet. Pick an unclaimed province on the map below, name your Nation, and found it.",
            ThemeColor.TEXT_SECONDARY);
        instructions.setFont(UITheme.FONT_BODY);
        instructions.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(instructions);
        panel.add(Box.createVerticalStrut(14));

        panel.add(buildMap(true));
        panel.add(Box.createVerticalStrut(14));

        final JLabel selectedLabel = new ThemedLabel(
            selectedFoundingProvinceId == null ? "No province selected." : "Selected province #" + selectedFoundingProvinceId,
            ThemeColor.TEXT_MUTED);
        selectedLabel.setFont(UITheme.FONT_SMALL);
        selectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(selectedLabel);
        panel.add(Box.createVerticalStrut(10));

        JPanel foundRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        foundRow.setOpaque(false);
        foundRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        final ThemedTextField nameField = new ThemedTextField("Nation name");
        nameField.setPreferredSize(new Dimension(220, 42));
        foundRow.add(nameField);

        ThemedButton foundButton = new ThemedButton("Found Nation", true);
        foundButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (selectedFoundingProvinceId == null)
                {
                    statusLabel.setText("Pick a province on the map first.");
                    return;
                }
                String name = nameField.getValue();
                if (name.isEmpty())
                {
                    statusLabel.setText("Enter a Nation name first.");
                    return;
                }
                Message request = new Message();
                request.setType(MessageType.DOMINION_FOUND_NATION_REQUEST);
                request.setDominionNationName(name);
                request.setDominionProvinceId(selectedFoundingProvinceId);
                sendOrder(request, "Nation founded.");
            }
        });
        foundRow.add(foundButton);
        panel.add(foundRow);

        return panel;
    }

    private JComponent buildDashboard()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedPanel summary = new RoundedPanel(ThemeColor.BG_APP, UITheme.RADIUS_BUTTON);
        summary.setLayout(new FlowLayout(FlowLayout.LEFT, 24, 10));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setMaximumSize(new Dimension(2000, 60));
        summary.add(summaryStat(myNation.getName(), "Nation"));
        summary.add(summaryStat(String.valueOf(myNation.getTreasury()), "Treasury"));
        summary.add(summaryStat(String.valueOf(myNation.getHonor()), "Honor"));
        summary.add(summaryStat(String.valueOf(snapshot.getCurrentTick()), "Day"));
        panel.add(summary);
        panel.add(Box.createVerticalStrut(16));

        panel.add(buildMap(false));
        panel.add(Box.createVerticalStrut(16));

        panel.add(buildArmiesSection());
        panel.add(Box.createVerticalStrut(16));

        panel.add(buildOrdersSection());
        panel.add(Box.createVerticalStrut(16));

        panel.add(buildDiplomacySection());

        return panel;
    }

    private JComponent summaryStat(String value, String label)
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel valueLabel = new ThemedLabel(value, ThemeColor.TEXT_PRIMARY);
        valueLabel.setFont(UITheme.FONT_NAV_BOLD);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(valueLabel);

        JLabel captionLabel = new ThemedLabel(label, ThemeColor.TEXT_MUTED);
        captionLabel.setFont(UITheme.FONT_SMALL);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(captionLabel);

        return col;
    }

    /**
     * A read-only grid map: one small cell per Province, colored by owner
     * (your own Nation always the theme's ACCENT color, others from
     * NATION_PALETTE, unclaimed a plain terrain-tinted gray) with a tooltip
     * giving the real detail. In founding mode, clicking an unclaimed cell
     * selects it for the "Found Nation" form above; in dashboard mode cells
     * are informational only - orders below pick provinces/nations from
     * dropdowns rather than by clicking, a deliberate V1 simplification.
     */
    private JComponent buildMap(final boolean foundingMode)
    {
        Map<Long, Province> byCoord = new HashMap<Long, Province>();
        int maxRow = 0, maxCol = 0;
        for (Province province : snapshot.getProvinces())
        {
            byCoord.put(coordKey(province.getRow(), province.getCol()), province);
            maxRow = Math.max(maxRow, province.getRow());
            maxCol = Math.max(maxCol, province.getCol());
        }

        JPanel grid = new JPanel(new GridLayout(maxRow + 1, maxCol + 1, 2, 2));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension((maxCol + 1) * 26, (maxRow + 1) * 26));
        grid.setPreferredSize(new Dimension((maxCol + 1) * 26, (maxRow + 1) * 26));

        for (int row = 0; row <= maxRow; row++)
        {
            for (int col = 0; col <= maxCol; col++)
            {
                final Province province = byCoord.get(coordKey(row, col));
                grid.add(buildMapCell(province, foundingMode));
            }
        }

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.X_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(grid);
        return wrapper;
    }

    private long coordKey(int row, int col)
    {
        return ((long) row << 32) | (col & 0xffffffffL);
    }

    private JComponent buildMapCell(final Province province, boolean foundingMode)
    {
        final JPanel cell = new JPanel();
        cell.setPreferredSize(new Dimension(24, 24));
        if (province == null)
        {
            cell.setOpaque(false);
            return cell;
        }

        cell.setOpaque(true);
        cell.setBackground(mapCellColor(province));
        cell.setToolTipText(mapCellTooltip(province));

        if (foundingMode && province.getOwningNationId() == null)
        {
            cell.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            cell.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    selectedFoundingProvinceId = province.getId();
                    render();
                }
            });
            if (province.getId() == (selectedFoundingProvinceId == null ? -1 : selectedFoundingProvinceId.intValue()))
            {
                cell.setBorder(javax.swing.BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.ACCENT), 2));
            }
        }
        return cell;
    }

    private Color mapCellColor(Province province)
    {
        Integer owner = province.getOwningNationId();
        if (owner == null)
        {
            Terrain terrain = province.getTerrain();
            if (terrain == Terrain.FOREST) return new Color(0x3A, 0x4A, 0x3A);
            if (terrain == Terrain.HILLS) return new Color(0x5A, 0x50, 0x40);
            return new Color(0x45, 0x45, 0x48);
        }
        if (myNation != null && owner.intValue() == myNation.getId())
        {
            return ThemeManager.getColor(ThemeColor.ACCENT);
        }
        return NATION_PALETTE[Math.abs(owner.intValue()) % NATION_PALETTE.length];
    }

    private String mapCellTooltip(Province province)
    {
        String ownerText = province.getOwningNationId() == null ? "unclaimed" : nationName(province.getOwningNationId());
        return "Province #" + province.getId() + " (" + province.getTerrain() + ") - " + ownerText;
    }

    private JComponent buildArmiesSection()
    {
        JPanel section = sectionPanel("Your Armies");

        List<Army> myArmies = new ArrayList<Army>();
        for (Army army : snapshot.getArmies())
        {
            if (army.getNationId() == myNation.getId())
            {
                myArmies.add(army);
            }
        }

        if (myArmies.isEmpty())
        {
            section.add(mutedLine("No armies yet - recruit one below."));
        }
        else
        {
            for (int i = 0; i < myArmies.size(); i++)
            {
                Army army = myArmies.get(i);
                String marchText = army.getMarchOrderTargetProvinceId() == null
                    ? "holding at province #" + army.getLocationProvinceId()
                    : "at province #" + army.getLocationProvinceId() + ", marching to #" + army.getMarchOrderTargetProvinceId();
                section.add(mutedLine("Army #" + army.getId() + " - " + army.getTroopCount() + " troops, " + marchText));
            }
        }
        return section;
    }

    private JComponent buildOrdersSection()
    {
        JPanel section = sectionPanel("Orders");

        List<Province> myProvinces = new ArrayList<Province>();
        for (Province province : snapshot.getProvinces())
        {
            if (province.getOwningNationId() != null && province.getOwningNationId().intValue() == myNation.getId())
            {
                myProvinces.add(province);
            }
        }
        List<Army> myArmies = new ArrayList<Army>();
        for (Army army : snapshot.getArmies())
        {
            if (army.getNationId() == myNation.getId())
            {
                myArmies.add(army);
            }
        }
        List<Nation> otherNations = new ArrayList<Nation>();
        for (Nation nation : snapshot.getNations())
        {
            if (nation.getId() != myNation.getId())
            {
                otherNations.add(nation);
            }
        }

        section.add(buildRecruitRow(myProvinces));
        section.add(Box.createVerticalStrut(8));
        section.add(buildMarchRow(myArmies));
        section.add(Box.createVerticalStrut(8));
        section.add(buildDeclareWarRow(otherNations));
        return section;
    }

    private JComponent buildRecruitRow(List<Province> myProvinces)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new ThemedLabel("Recruit:", ThemeColor.TEXT_SECONDARY));

        if (myProvinces.isEmpty())
        {
            row.add(mutedLine("(no provinces of your own yet)"));
            return row;
        }

        final JComboBox<String> provinceBox = new JComboBox<String>();
        for (int i = 0; i < myProvinces.size(); i++)
        {
            provinceBox.addItem("Province #" + myProvinces.get(i).getId());
        }
        row.add(provinceBox);

        final ThemedTextField troopField = new ThemedTextField("Troops");
        troopField.setPreferredSize(new Dimension(80, 36));
        row.add(troopField);

        ThemedButton button = new ThemedButton("Recruit Army", false);
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Integer troopCount = parsePositiveInt(troopField.getValue());
                if (troopCount == null)
                {
                    statusLabel.setText("Enter a positive troop count.");
                    return;
                }
                Province province = myProvinces.get(provinceBox.getSelectedIndex());
                Message request = new Message();
                request.setType(MessageType.DOMINION_RECRUIT_ARMY_REQUEST);
                request.setDominionProvinceId(province.getId());
                request.setDominionTroopCount(troopCount.intValue());
                sendOrder(request, "Army recruited.");
            }
        });
        row.add(button);
        return row;
    }

    private JComponent buildMarchRow(List<Army> myArmies)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new ThemedLabel("March:", ThemeColor.TEXT_SECONDARY));

        if (myArmies.isEmpty())
        {
            row.add(mutedLine("(no armies to march yet)"));
            return row;
        }

        final JComboBox<String> armyBox = new JComboBox<String>();
        for (int i = 0; i < myArmies.size(); i++)
        {
            armyBox.addItem("Army #" + myArmies.get(i).getId() + " (at #" + myArmies.get(i).getLocationProvinceId() + ")");
        }
        row.add(armyBox);

        final ThemedTextField targetField = new ThemedTextField("Target province #");
        targetField.setPreferredSize(new Dimension(140, 36));
        row.add(targetField);

        ThemedButton button = new ThemedButton("Queue March", false);
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Integer targetProvinceId = parsePositiveInt(targetField.getValue());
                if (targetProvinceId == null)
                {
                    statusLabel.setText("Enter a valid target province id.");
                    return;
                }
                Army army = myArmies.get(armyBox.getSelectedIndex());
                Message request = new Message();
                request.setType(MessageType.DOMINION_QUEUE_MARCH_REQUEST);
                request.setDominionArmyId(army.getId());
                request.setDominionTargetProvinceId(targetProvinceId);
                sendOrder(request, "March queued - resolves next tick.");
            }
        });
        row.add(button);
        return row;
    }

    private JComponent buildDeclareWarRow(List<Nation> otherNations)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new ThemedLabel("Declare war:", ThemeColor.TEXT_SECONDARY));

        if (otherNations.isEmpty())
        {
            row.add(mutedLine("(no other nations yet)"));
            return row;
        }

        final JComboBox<String> nationBox = new JComboBox<String>();
        for (int i = 0; i < otherNations.size(); i++)
        {
            nationBox.addItem(otherNations.get(i).getName());
        }
        row.add(nationBox);

        ThemedButton button = new ThemedButton("Declare War", false);
        button.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Nation target = otherNations.get(nationBox.getSelectedIndex());
                Message request = new Message();
                request.setType(MessageType.DOMINION_DECLARE_WAR_REQUEST);
                request.setDominionTargetNationId(target.getId());
                sendOrder(request, "War declared - effective next tick.");
            }
        });
        row.add(button);
        return row;
    }

    private JComponent buildDiplomacySection()
    {
        JPanel section = sectionPanel("Diplomacy");

        List<Nation> otherNations = new ArrayList<Nation>();
        for (Nation nation : snapshot.getNations())
        {
            if (nation.getId() != myNation.getId())
            {
                otherNations.add(nation);
            }
        }

        if (!otherNations.isEmpty())
        {
            JPanel proposeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            proposeRow.setOpaque(false);
            proposeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            proposeRow.add(new ThemedLabel("Propose:", ThemeColor.TEXT_SECONDARY));

            final JComboBox<String> nationBox = new JComboBox<String>();
            for (int i = 0; i < otherNations.size(); i++)
            {
                nationBox.addItem(otherNations.get(i).getName());
            }
            proposeRow.add(nationBox);

            final JComboBox<String> typeBox = new JComboBox<String>(new String[] { "ALLIANCE", "NON_AGGRESSION" });
            proposeRow.add(typeBox);

            ThemedButton proposeButton = new ThemedButton("Send Proposal", false);
            proposeButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Nation target = otherNations.get(nationBox.getSelectedIndex());
                    Message request = new Message();
                    request.setType(MessageType.DOMINION_PROPOSE_RELATION_REQUEST);
                    request.setDominionTargetNationId(target.getId());
                    request.setDominionRelationType((String) typeBox.getSelectedItem());
                    sendOrder(request, "Proposal sent.");
                }
            });
            proposeRow.add(proposeButton);
            section.add(proposeRow);
            section.add(Box.createVerticalStrut(8));
        }

        List<DiplomaticProposal> myProposals = snapshot.getMyProposals();
        if (myProposals == null || myProposals.isEmpty())
        {
            section.add(mutedLine("No pending proposals."));
        }
        else
        {
            for (int i = 0; i < myProposals.size(); i++)
            {
                section.add(buildProposalRow(myProposals.get(i)));
            }
        }

        section.add(Box.createVerticalStrut(8));
        section.add(buildRelationsSummary());
        return section;
    }

    private JComponent buildProposalRow(final DiplomaticProposal proposal)
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean isIncoming = proposal.getToNationId() == myNation.getId();
        String text = isIncoming
            ? nationName(proposal.getFromNationId()) + " proposes " + proposal.getProposedType()
            : "You proposed " + proposal.getProposedType() + " to " + nationName(proposal.getToNationId()) + " (pending)";
        row.add(new ThemedLabel(text, ThemeColor.TEXT_SECONDARY));

        if (isIncoming)
        {
            ThemedButton acceptButton = new ThemedButton("Accept", true);
            acceptButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { respondToProposal(proposal, true); }
            });
            row.add(acceptButton);

            ThemedButton declineButton = new ThemedButton("Decline", false);
            declineButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { respondToProposal(proposal, false); }
            });
            row.add(declineButton);
        }
        return row;
    }

    private void respondToProposal(DiplomaticProposal proposal, boolean accept)
    {
        Message request = new Message();
        request.setType(MessageType.DOMINION_RESPOND_PROPOSAL_REQUEST);
        request.setDominionProposalId(proposal.getId());
        request.setDominionAccept(accept);
        sendOrder(request, accept ? "Proposal accepted." : "Proposal declined.");
    }

    private JComponent buildRelationsSummary()
    {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<DiplomaticRelation> mine = new ArrayList<DiplomaticRelation>();
        for (DiplomaticRelation relation : snapshot.getRelations())
        {
            if (relation.getNationAId() == myNation.getId() || relation.getNationBId() == myNation.getId())
            {
                mine.add(relation);
            }
        }

        if (mine.isEmpty())
        {
            col.add(mutedLine("No active relations."));
            return col;
        }

        for (int i = 0; i < mine.size(); i++)
        {
            DiplomaticRelation relation = mine.get(i);
            int otherId = relation.getNationAId() == myNation.getId() ? relation.getNationBId() : relation.getNationAId();
            String activeNote = relation.getType() == RelationType.WAR && relation.getEffectiveFromTick() != null
                && relation.getEffectiveFromTick().intValue() > snapshot.getCurrentTick()
                ? " (declared, active day " + relation.getEffectiveFromTick() + ")" : "";
            col.add(mutedLine(nationName(otherId) + ": " + relation.getType() + activeNote));
        }
        return col;
    }

    // ---- small shared builders ----

    private JPanel sectionPanel(String heading)
    {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel headingLabel = new ThemedLabel(heading, ThemeColor.TEXT_PRIMARY);
        headingLabel.setFont(UITheme.FONT_NAV_BOLD);
        headingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headingLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        section.add(headingLabel);
        return section;
    }

    private JLabel mutedLine(String text)
    {
        JLabel label = new ThemedLabel(text, ThemeColor.TEXT_MUTED);
        label.setFont(UITheme.FONT_SMALL);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private Integer parsePositiveInt(String text)
    {
        try
        {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? Integer.valueOf(value) : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
