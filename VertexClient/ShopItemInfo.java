import java.io.Serializable;

/**
 * ShopItemInfo
 * ------------
 * SHARED (Common) class - identical copy lives in both VertexClient
 * and VertexServer. One purchasable shop item as sent to the client.
 * Whether the player already owns it lives on Account.getOwnedItemIds().
 */
public class ShopItemInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final int priceCoins;
    private final String colorHex;
    /** "COLOR" or "BADGE" - which cosmetic slot this item occupies. */
    private final String type;

    public ShopItemInfo(String id, String name, int priceCoins, String colorHex, String type)
    {
        this.id = id;
        this.name = name;
        this.priceCoins = priceCoins;
        this.colorHex = colorHex;
        this.type = type;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getPriceCoins() { return priceCoins; }
    public String getColorHex() { return colorHex; }
    public String getType() { return type; }
}
