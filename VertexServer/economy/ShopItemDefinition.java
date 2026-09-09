package economy;

/** A single shop catalog entry, server-side. Pulled into its own file (rather than staying a second top-level class alongside EconomyConfig, which worked fine in the old flat single-package layout) since Java requires a public top-level class to live in a file matching its own name once it needs to be visible outside its package - ClientHandler (in the net package) needs this type and its fields. */
public class ShopItemDefinition
{
    public final String id;
    public final String name;
    public final int priceCoins;
    public final String colorHex;
    /** "COLOR", "BADGE", or "FRAME" - which cosmetic slot this item occupies. */
    public final String type;

    ShopItemDefinition(String id, String name, int priceCoins, String colorHex, String type)
    {
        this.id = id;
        this.name = name;
        this.priceCoins = priceCoins;
        this.colorHex = colorHex;
        this.type = type;
    }
}
