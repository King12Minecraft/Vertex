import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * PlayerColorRegistry
 * --------------------
 * Client-side cache of the shop's color items (id -> hex), so any
 * component that needs to render a username in someone's purchased
 * color can resolve it without re-fetching the shop every time.
 * Populated from ShopPanel whenever it loads, and eagerly fetched once
 * at MainMenu startup so it's available even if the user never visits
 * the Shop page.
 */
public class PlayerColorRegistry
{
    private static List<ShopItemInfo> items = new ArrayList<ShopItemInfo>();

    private PlayerColorRegistry()
    {
        // Static utility class - never instantiated.
    }

    public static void setItems(List<ShopItemInfo> newItems)
    {
        if (newItems != null)
        {
            items = newItems;
        }
    }

    /** Resolves a purchased color item's ID to its actual Color, or null for "Default"/unknown/not set. */
    public static Color resolve(String colorId)
    {
        if (colorId == null || "Default".equals(colorId))
        {
            return null;
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).getId().equals(colorId))
            {
                try
                {
                    return Color.decode(items.get(i).getColorHex());
                }
                catch (NumberFormatException e)
                {
                    return null;
                }
            }
        }
        return null;
    }

    /** Resolves an equipped badge's ID to its Unicode glyph, or null if none/unknown - badges reuse the same colorHex field as colors, just holding a glyph string instead of a hex code. */
    public static String resolveBadgeGlyph(String badgeId)
    {
        if (badgeId == null || badgeId.isEmpty())
        {
            return null;
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).getId().equals(badgeId) && "BADGE".equals(items.get(i).getType()))
            {
                return items.get(i).getColorHex();
            }
        }
        return null;
    }

    public static void fetchInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.SHOP_ITEMS_REQUEST);
                Message response = NetworkManager.send(request);
                if (response != null && response.isSuccess())
                {
                    setItems(response.getShopItems());
                    // Nothing else would otherwise trigger a re-render once this
                    // loads, since it's fetched async in the background - piggyback
                    // on Session's listener mechanism to refresh anything showing
                    // a colored username (TopBar, etc).
                    javax.swing.SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run() { Session.notifyListeners(); }
                    });
                }
            }
        });
        worker.start();
    }
}
