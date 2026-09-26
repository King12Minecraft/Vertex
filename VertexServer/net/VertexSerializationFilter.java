package net;

import java.io.ObjectInputFilter;

/**
 * VertexSerializationFilter
 * --------------------------
 * SHARED (Common) - byte-identical in VertexClient/ and VertexServer/.
 *
 * Allow-list applied to every ObjectInputStream this app reads a Message off
 * of a raw socket with (ClientHandler on the server, NetworkManager on the
 * client). Without this, ObjectInputStream.readObject() will happily
 * instantiate ANY class reachable in the object graph a peer sends - the
 * well-known Java deserialization RCE bug class (see every major Java
 * deserialization CVE, and tools like ysoserial, for what an unfiltered
 * readObject() on untrusted input enables). This matters in both directions
 * here: an unauthenticated client connecting to a server (readObject() runs
 * on the very first message, before login), and a client connecting to a
 * malicious or compromised server.
 *
 * Deliberately a strict allow-list (deny-by-default via the trailing "!*"),
 * not a deny-list of known-bad gadget classes - a deny-list only ever covers
 * gadgets someone has already found; an allow-list is safe against ones
 * nobody has found yet too. Every concrete class that legitimately appears
 * anywhere in a Message object graph needs to be listed here explicitly:
 * adding a new custom-type field to Message.java (or a new field to one of
 * the types already listed here) means adding/checking it here too, or that
 * field will start silently failing to deserialize on the receiving end.
 *
 * The size limits (maxbytes/maxarray/etc.) are set well above anything this
 * app legitimately sends (the largest payload is a whole Vertex.jar on
 * client auto-update, plausibly tens of MB) purely as a DoS backstop against
 * a peer claiming an absurd array/stream size to force an OOM, not as a tight
 * bound on real traffic.
 */
public final class VertexSerializationFilter
{
    private VertexSerializationFilter() { }

    public static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
        "net.Message;net.MessageType;"
        + "account.Account;account.Role;"
        + "games.GameInfo;"
        + "economy.ChallengeProgressInfo;economy.ShopItemInfo;"
        + "dominion.DominionSnapshot;dominion.Province;dominion.Nation;dominion.Army;"
        + "dominion.DiplomaticRelation;dominion.Terrain;dominion.RelationType;"
        + "java.lang.String;java.lang.Enum;java.lang.Object;java.lang.Integer;java.lang.Number;"
        + "java.util.ArrayList;"
        + "maxdepth=50;maxarray=100000000;maxrefs=1000000;maxbytes=200000000;"
        + "!*"
    );
}
