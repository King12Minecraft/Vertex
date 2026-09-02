import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * ShopPanel
 * ---------
 * Real economy: live coin balance and real purchasing (server-
 * validated, never trusts a client-reported balance - Section 33).
 * Challenge/quest tracking moved to its own dedicated QuestsPanel -
 * Shop is purely for spending now. All prices come from the server's
 * EconomyConfig.
 */
public class ShopPanel extends RoundedPanel implements NetworkManager.PushListener
{
    private JPanel itemsGrid;
    private JPanel badgesGrid;
    private List<ShopItemInfo> cachedItems;
    private JLabel balanceLabel;

    public ShopPanel()
    {
        super(ThemeColor.BG_APP, 0);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 32, 24, 32));

        add(new PageHeader("SHOP"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 0, 24, 0));

        content.add(createBalanceRow());
        content.add(Box.createVerticalStrut(24));

        content.add(sectionLabel("USERNAME COLORS"));
        itemsGrid = new JPanel(new GridLayout(0, 4, 18, 18));
        itemsGrid.setOpaque(false);
        itemsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(itemsGrid);
        content.add(Box.createVerticalStrut(24));

        content.add(sectionLabel("BADGES"));
        badgesGrid = new JPanel(new GridLayout(0, 4, 18, 18));
        badgesGrid.setOpaque(false);
        badgesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(badgesGrid);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        NetworkManager.addPushListener(this);

        loadShopItems();
    }

    private JPanel createBalanceRow()
    {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedPanel badge = new RoundedPanel(ThemeColor.BG_SIDEBAR, 18);
        badge.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
        badge.setBorder(new EmptyBorder(9, 16, 9, 16));

        balanceLabel = new JLabel(currentCoinsText());
        balanceLabel.setFont(UITheme.FONT_NAV_BOLD);
        balanceLabel.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
        badge.add(balanceLabel);

        row.add(badge);
        return row;
    }

    private String currentCoinsText()
    {
        return (Session.isLoggedIn() ? Session.getCurrentAccount().getCoins() : 0) + " Coins";
    }

    private JLabel sectionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 14, 0));
        return label;
    }

    private void loadShopItems()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message itemsRequest = new Message();
                itemsRequest.setType(MessageType.SHOP_ITEMS_REQUEST);
                final Message itemsResponse = NetworkManager.send(itemsRequest);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (itemsResponse != null && itemsResponse.isSuccess())
                        {
                            renderShopItems(itemsResponse.getShopItems());
                            PlayerColorRegistry.setItems(itemsResponse.getShopItems());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void renderShopItems(List<ShopItemInfo> items)
    {
        cachedItems = items;
        itemsGrid.removeAll();
        badgesGrid.removeAll();
        if (items != null)
        {
            for (int i = 0; i < items.size(); i++)
            {
                ShopItemInfo item = items.get(i);
                if ("BADGE".equals(item.getType()))
                {
                    badgesGrid.add(buildShopCard(item));
                }
                else
                {
                    itemsGrid.add(buildShopCard(item));
                }
            }
        }
        itemsGrid.revalidate();
        itemsGrid.repaint();
        badgesGrid.revalidate();
        badgesGrid.repaint();
    }

    private boolean isOwned(String itemId)
    {
        return Session.isLoggedIn() && Session.getCurrentAccount().getOwnedItemIds().contains(itemId);
    }

    private JPanel buildShopCard(final ShopItemInfo item)
    {
        final RoundedPanel card = new RoundedPanel(ThemeColor.BG_PANEL, UITheme.RADIUS_PANEL);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setPreferredSize(new Dimension(200, 210));
        card.enableTopAccent();
        card.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) { card.glow().animateIn(); }
            public void mouseExited(MouseEvent e)  { card.glow().animateOut(); }
        });

        JPanel swatch;
        if ("BADGE".equals(item.getType()))
        {
            JLabel glyph = new JLabel(item.getColorHex(), SwingConstants.CENTER);
            glyph.setFont(UITheme.FONT_HEADING.deriveFont(28f));
            glyph.setForeground(ThemeManager.getColor(ThemeColor.ACCENT));
            swatch = new JPanel(new BorderLayout());
            swatch.setOpaque(false);
            swatch.add(glyph, BorderLayout.CENTER);
        }
        else
        {
            swatch = new JPanel();
            try
            {
                swatch.setBackground(Color.decode(item.getColorHex()));
            }
            catch (NumberFormatException e)
            {
                swatch.setBackground(ThemeManager.getColor(ThemeColor.ACCENT));
            }
        }
        swatch.setPreferredSize(new Dimension(50, 50));
        card.add(swatch, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(14, 0, 0, 0));

        JLabel name = new JLabel(item.getName());
        name.setFont(UITheme.FONT_NAV_BOLD);
        name.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel price = new JLabel(item.getPriceCoins() + " coins");
        price.setFont(UITheme.FONT_SMALL);
        price.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        price.setAlignmentX(Component.LEFT_ALIGNMENT);
        price.setBorder(new EmptyBorder(4, 0, 12, 0));

        boolean owned = isOwned(item.getId());
        boolean selected = owned && isSelected(item.getId(), item.getType());

        final ThemedButton buy;
        if (!owned)
        {
            buy = new ThemedButton("Buy", true);
            buy.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { purchaseItem(item, buy); }
            });
        }
        else if (selected)
        {
            buy = new ThemedButton("Selected", false);
            buy.setEnabled(false);
        }
        else
        {
            buy = new ThemedButton("Select", true);
            buy.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { selectItem(item, buy); }
            });
        }
        buy.setAlignmentX(Component.LEFT_ALIGNMENT);
        buy.setMaximumSize(new Dimension(500, 36));
        buy.setPreferredSize(new Dimension(160, 36));

        info.add(name);
        info.add(price);
        info.add(Box.createVerticalGlue());
        info.add(buy);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private boolean isSelected(String itemId, String type)
    {
        if (!Session.isLoggedIn())
        {
            return false;
        }
        if ("BADGE".equals(type))
        {
            return itemId.equals(Session.getCurrentAccount().getEquippedBadgeId());
        }
        return itemId.equals(Session.getCurrentAccount().getPlayerColorName());
    }

    private void selectItem(final ShopItemInfo item, final ThemedButton selectButton)
    {
        selectButton.setEnabled(false);
        final boolean isBadge = "BADGE".equals(item.getType());

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(isBadge ? MessageType.SELECT_BADGE_REQUEST : MessageType.SELECT_COLOR_REQUEST);
                request.setItemId(item.getId());

                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response != null && response.isSuccess())
                        {
                            if (Session.isLoggedIn())
                            {
                                if (isBadge)
                                {
                                    Session.getCurrentAccount().setEquippedBadgeId(item.getId());
                                }
                                else
                                {
                                    Session.getCurrentAccount().setPlayerColorName(item.getId());
                                }
                                Session.notifyListeners();
                            }
                            renderShopItems(cachedItems);
                        }
                        else
                        {
                            selectButton.setEnabled(true);
                            String error = response != null ? response.getErrorText() : "Can't reach the server - is it running?";
                            GameHubDialog.show(selectButton, "Shop", error);
                        }
                    }
                });
            }
        });
        worker.start();
    }

    private void purchaseItem(final ShopItemInfo item, final ThemedButton buyButton)
    {
        buyButton.setEnabled(false);

        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.PURCHASE_REQUEST);
                request.setItemId(item.getId());

                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (response == null)
                        {
                            buyButton.setEnabled(true);
                            GameHubDialog.show(buyButton, "Shop", "Can't reach the server - is it running?");
                        }
                        else if (response.isSuccess())
                        {
                            if (Session.isLoggedIn())
                            {
                                Session.getCurrentAccount().setCoins(response.getCoins());
                                Session.getCurrentAccount().getOwnedItemIds().add(item.getId());
                                Session.notifyListeners();
                            }
                            balanceLabel.setText(currentCoinsText());
                            renderShopItems(cachedItems);
                            GameHubDialog.show(buyButton, "Shop", "You bought " + item.getName() + "!");
                        }
                        else
                        {
                            buyButton.setEnabled(true);
                            GameHubDialog.show(buyButton, "Shop", response.getErrorText());
                        }
                    }
                });
            }
        });
        worker.start();
    }

    @Override
    public void onPush(final Message message)
    {
        if (message.getType() != MessageType.WALLET_UPDATE)
        {
            return;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { balanceLabel.setText(currentCoinsText()); }
        });
    }
}
