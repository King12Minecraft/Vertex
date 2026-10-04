package net;

import account.Account;
import account.Role;
import dominion.Army;
import dominion.DiplomaticProposal;
import dominion.DiplomaticRelation;
import dominion.DominionSnapshot;
import dominion.Nation;
import dominion.Province;
import dominion.RelationType;
import dominion.Terrain;
import support.Check;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * VertexSerializationFilterTest
 * ------------------------------
 * Graduated from a scratch test into the committed regression suite
 * (ROADMAP.md's Testing Infrastructure entry names this exact test as part
 * of the first, deliberately small batch worth keeping permanently). Proves
 * VertexSerializationFilter.FILTER with a REAL ObjectOutputStream/
 * ObjectInputStream round trip through the actual filter - not just a
 * compile check - both that every allow-listed class a real Message can
 * carry still deserializes correctly, and that the deny-by-default "!*"
 * tail actually rejects something not on the list, proving this is a real
 * allow-list and not silently permissive.
 */
public class VertexSerializationFilterTest
{
    public static void main(String[] args) throws Exception
    {
        Check check = new Check();

        testAllowListedGraphRoundTrips(check);
        testDisallowedClassIsRejected(check);
        testPlainMessageWithNoExtrasRoundTrips(check);

        check.finish();
    }

    /** A Message carrying an Account and a full DominionSnapshot (every dominion.* class the filter allow-lists, nested three levels deep through a List) - the richest real object graph this app's own protocol actually sends. */
    private static void testAllowListedGraphRoundTrips(Check check) throws Exception
    {
        Message original = new Message();
        original.setType(MessageType.DOMINION_STATE_RESPONSE);
        original.setAccount(new Account(7, "tester", "hash", "salt", Role.PLAYER));

        Province province = new Province(1, 0, 0, Terrain.PLAINS);
        province.setOwningNationId(3);
        Nation nation = new Nation(3, 7, "Testland");
        nation.setTreasury(500);
        nation.setHonor(100);
        Army army = new Army(1, 3, 20, 1);
        army.setMarchOrderTargetProvinceId(2);
        DiplomaticRelation relation = new DiplomaticRelation(3, 4, RelationType.NEUTRAL, null);
        DiplomaticProposal proposal = new DiplomaticProposal(1, 3, 4, RelationType.ALLIANCE);

        DominionSnapshot snapshot = new DominionSnapshot(5,
            list(province), list(nation), list(army), list(relation), list(proposal));
        original.setDominionSnapshot(snapshot);

        Message roundTripped = roundTrip(original);

        check.check("Message type survives the round trip", roundTripped.getType() == MessageType.DOMINION_STATE_RESPONSE);
        check.check("Account survives the round trip", roundTripped.getAccount() != null
            && "tester".equals(roundTripped.getAccount().getUsername()));
        check.check("DominionSnapshot survives the round trip", roundTripped.getDominionSnapshot() != null);
        check.check("Snapshot's tick survives", roundTripped.getDominionSnapshot().getCurrentTick() == 5);
        check.check("Snapshot's Province survives", roundTripped.getDominionSnapshot().getProvinces().size() == 1
            && roundTripped.getDominionSnapshot().getProvinces().get(0).getOwningNationId().equals(3));
        check.check("Snapshot's Nation survives", roundTripped.getDominionSnapshot().getNations().get(0).getTreasury() == 500);
        check.check("Snapshot's Army survives", roundTripped.getDominionSnapshot().getArmies().get(0).getTroopCount() == 20);
        check.check("Snapshot's DiplomaticRelation survives", roundTripped.getDominionSnapshot().getRelations().get(0).getType() == RelationType.NEUTRAL);
        check.check("Snapshot's DiplomaticProposal survives", roundTripped.getDominionSnapshot().getMyProposals().get(0).getProposedType() == RelationType.ALLIANCE);
    }

    /** The filter is a strict allow-list (deny-by-default via the trailing "!*") - proves that by actually trying to deserialize something never listed, rather than just trusting the config string reads that way. java.util.HashMap is real, common, and deliberately not on the list (only java.util.ArrayList is). */
    private static void testDisallowedClassIsRejected(Check check) throws Exception
    {
        HashMap<String, String> disallowed = new HashMap<String, String>();
        disallowed.put("key", "value");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(disallowed);
        out.flush();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.setObjectInputFilter(VertexSerializationFilter.FILTER);

        boolean rejected = false;
        try
        {
            in.readObject();
        }
        catch (InvalidClassException expected)
        {
            rejected = true;
        }
        check.check("A class not on the allow-list is rejected by the filter", rejected);
    }

    /** A bare Message with every field left at its default (no Account, no DominionSnapshot, no lists) - the common case for most of this app's real traffic (a plain request/response with a few primitive fields) - should round-trip with no surprises either. */
    private static void testPlainMessageWithNoExtrasRoundTrips(Check check) throws Exception
    {
        Message original = new Message();
        original.setType(MessageType.LOGIN_REQUEST);
        original.setUsername("plainuser");

        Message roundTripped = roundTrip(original);
        check.check("A plain Message with no nested objects round-trips", roundTripped.getType() == MessageType.LOGIN_REQUEST
            && "plainuser".equals(roundTripped.getUsername()));
    }

    private static List<Province> list(Province p) { List<Province> l = new ArrayList<Province>(); l.add(p); return l; }
    private static List<Nation> list(Nation n) { List<Nation> l = new ArrayList<Nation>(); l.add(n); return l; }
    private static List<Army> list(Army a) { List<Army> l = new ArrayList<Army>(); l.add(a); return l; }
    private static List<DiplomaticRelation> list(DiplomaticRelation r) { List<DiplomaticRelation> l = new ArrayList<DiplomaticRelation>(); l.add(r); return l; }
    private static List<DiplomaticProposal> list(DiplomaticProposal p) { List<DiplomaticProposal> l = new ArrayList<DiplomaticProposal>(); l.add(p); return l; }

    private static Message roundTrip(Message original) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(original);
        out.flush();

        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.setObjectInputFilter(VertexSerializationFilter.FILTER);
        return (Message) in.readObject();
    }
}
